package de.codesourcery.j6502.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.HeadlessException;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.AbstractButton;
import javax.swing.BorderFactory;
import javax.swing.Icon;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.JToggleButton;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import javax.swing.filechooser.FileFilter;
import javax.swing.table.AbstractTableModel;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import de.codesourcery.hex2raw.IntelHex;
import de.codesourcery.j6502.Constants;
import de.codesourcery.j6502.assembler.Assembler;
import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Parser;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.disassembler.DisassemblerTest;
import de.codesourcery.j6502.emulator.AddressRange;
import de.codesourcery.j6502.emulator.Breakpoint;
import de.codesourcery.j6502.emulator.BreakpointsController;
import de.codesourcery.j6502.emulator.BreakpointsController.IBreakpointLister;
import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.D64File;
import de.codesourcery.j6502.emulator.EmulationStateManager;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.EmulatorDriver;
import de.codesourcery.j6502.emulator.EmulatorDriver.CallbackWithResult;
import de.codesourcery.j6502.emulator.EmulatorDriver.IEmulationListener;
import de.codesourcery.j6502.emulator.EmulatorDriver.Mode;
import de.codesourcery.j6502.emulator.EmulatorTest;
import de.codesourcery.j6502.emulator.G64File;
import de.codesourcery.j6502.emulator.IMemoryProvider;
import de.codesourcery.j6502.emulator.IMemoryRegion;
import de.codesourcery.j6502.emulator.SerialDevice;
import de.codesourcery.j6502.emulator.VIC;
import de.codesourcery.j6502.emulator.diskdrive.DiskHardware;
import de.codesourcery.j6502.emulator.tapedrive.TapeFile;
import de.codesourcery.j6502.ui.KeyboardInputListener.JoystickPort;
import de.codesourcery.j6502.ui.WindowLocationHelper.IDebuggerView;
import de.codesourcery.j6502.ui.WindowLocationHelper.ViewConfiguration;
import de.codesourcery.j6502.utils.HexDump;
import de.codesourcery.j6502.utils.Misc;

public class Debugger 
{
    protected static final int LINE_HEIGHT = 15;

    protected static final Color FG_COLOR = Color.GREEN;
    protected static final Color BG_COLOR = Color.BLACK;
    protected static final int VERTICAL_LINE_SPACING = 2;
    protected static final Font MONO_FONT = new Font("Monospaced", Font.PLAIN, 12);

    protected static final String CONFIG_KEY_LAST_STATE_FILE = "last.state.file";
    protected static final String CONFIG_KEY_LAST_MEMORY_FILE = "last.memory.file";
    
    protected static enum DebugTarget {
        COMPUTER, FLOPPY_8;
    }

    protected final Emulator emulator = new Emulator();

    private final BreakpointsController c64BreakpointsController = new BreakpointsController(emulator.getCPU(),emulator.getMemory());
    private final AtomicReference<BreakpointsController> breakpointsController = new AtomicReference<>(c64BreakpointsController);    
    private BreakpointsController floppyBreakpointsController;

    private final AtomicReference<CPU> debugCPU = new AtomicReference<>(emulator.getCPU());
    private final AtomicReference<IMemoryRegion> debugMemory = new AtomicReference<>(emulator.getMemory());    
    private DebugTarget debugTarget = DebugTarget.COMPUTER;

    public final EmulatorDriver driver = new EmulatorDriver(emulator) {

        private long lastTick;

        @Override
        protected void tick() {
            final long now = System.currentTimeMillis();
            long age = now - lastTick;
            if (age > Constants.DEBUGGER_UI_REFRESH_MILLIS) // do not post more
                // than 60 events /
                // second to not
                // overload the
                // Swing Event
                // handling queue
            {
                lastTick = now;
                try 
                {
                    SwingUtilities.invokeLater(() -> updateWindows(true));
                } 
                catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }

        @Override
        protected BreakpointsController getBreakPointsController() {
            return Debugger.this.getBreakPointsController();
        }
    };

    protected final EmulationStateManager emulationStateManager = new EmulationStateManager(driver);

    protected static final class ViewMetrics {
        public final String viewIdentifier;
        private double totalMs = 0;
        private long tickCount = 0;

        public ViewMetrics(IDebuggerView view) {
            this.viewIdentifier = view.getIdentifier();
        }

        public void update(long deltaNanos) {
            tickCount = 1;
            totalMs = (deltaNanos / 1000000f);
        }

        public double getAverageTimeMs() {
            return totalMs / tickCount;
        }
    }

    private long viewPerformanceTicks;
    private final IdentityHashMap<IDebuggerView, ViewMetrics> viewPerformanceMetrics = new IdentityHashMap<>();

    protected final WindowLocationHelper locationHelper = new WindowLocationHelper();

    private final JDesktopPane desktop = new JDesktopPane();

    private final KeyboardInputListener keyboardListener = new KeyboardInputListener(driver);

    private final FloppyJobQueuePanel floppyJobQueue = new FloppyJobQueuePanel(driver);

    private final List<IDebuggerView> views = new ArrayList<>();

    private final FloppyInfoPanel floppyInfoPanel = new FloppyInfoPanel(driver);
    private final BreakpointModel bpModel = new BreakpointModel();
    private final ButtonToolbar toolbar = new ButtonToolbar();
    public final DisassemblyPanel disassembly = new DisassemblyPanel(this) {

        @Override
        public BreakpointsController getBreakpointsController() {
            return Debugger.this.getBreakPointsController();
        }
    };
    private final MemoryBreakpointsPanel memoryBreakpoints = new MemoryBreakpointsPanel(driver);

    private final HexDumpPanel hexPanel = new HexDumpPanel();

    private final CPUStatusPanel cpuPanel = new CPUStatusPanel(this) {
        @Override
        protected CPU getCPU() {
            return Debugger.this.getCPU();
        }
    };

    private final BreakpointsWindow breakpointsWindow = new BreakpointsWindow();
    private final ScreenPanel screenPanel = new ScreenPanel();
    private final BlockAllocationPanel bamPanel = new BlockAllocationPanel();
    private final CalculatorPanel calculatorPanel = new CalculatorPanel();
    private final CommentedCodeViewer codeViewer = new CommentedCodeViewer(driver);

    private final AsmPanel asmPanel = new AsmPanel(desktop, driver) {
        @Override
        protected void binaryUploadedToEmulator() {
            if (SwingUtilities.isEventDispatchThread()) {
                updateWindows(false);
            } else {
                SwingUtilities.invokeLater(() -> updateWindows(false));
            }
        }
    };

    private BusAnalyzer busPanel;

    private final List<IDebuggerView> panels = new ArrayList<>();

    private IDebuggerView loc;

    public Debugger() {
        driver.addEmulationListener(new IEmulationListener() {
            @Override
            public void emulationStopped(Throwable t, boolean stoppedOnBreakpoint) {
                System.out.println("Emulation stopped (on breakpoint: " + stoppedOnBreakpoint + ")");
                SwingUtilities.invokeLater(() -> 
                {
                    if (stoppedOnBreakpoint) {
                        disassembly.setTrackPC(true);
                    }                    
                    updateWindows(false);
                });
            }

            @Override
            public void emulationStarted() {
                SwingUtilities.invokeLater(() -> updateWindows(false));
            }
        });
        driver.addEmulationListener(cpuPanel);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> new Debugger().run());
    }

    public void run() 
    {
        emulator.reset();

        final JInternalFrame toolbarFrame = wrap("Buttons", toolbar);
        desktop.add(toolbarFrame);

        final JInternalFrame disassemblyFrame = wrap("Disassembly", disassembly);
        desktop.add(disassemblyFrame);

        final JInternalFrame floppyInfoFrame = wrap("Floppy status", floppyInfoPanel);
        desktop.add(floppyInfoFrame);

        final JInternalFrame floppyJobQueueFrame = wrap("Floppy Job Queue", floppyJobQueue);
        desktop.add(floppyJobQueueFrame);

        final JInternalFrame cpuStatusFrame = wrap("CPU", cpuPanel);
        desktop.add(cpuStatusFrame);

        final JInternalFrame hexPanelFrame = wrap("Memory", hexPanel);
        desktop.add(hexPanelFrame);

        final JInternalFrame breakpointsFrame = wrap("Breakpoints", breakpointsWindow);
        desktop.add(breakpointsFrame);

        final JInternalFrame screenPanelFrame = wrap("Screen", screenPanel);
        desktop.add(screenPanelFrame);

        final JInternalFrame codeViewerFrame = wrap("Code viewer", codeViewer);
        desktop.add(codeViewerFrame);

        final JInternalFrame calculatorPanelFrame = wrap("Calculator", calculatorPanel);
        desktop.add(calculatorPanelFrame);

        final JInternalFrame bamPanelFrame = wrap("BAM", bamPanel);
        desktop.add(bamPanelFrame);

        final JInternalFrame memoryBreakpointsFrame = wrap("Memory Breakpoints", memoryBreakpoints);
        desktop.add(memoryBreakpointsFrame);

        final JInternalFrame asmPanelFrame = wrap(AsmPanel.PANEL_TITLE, asmPanel);
        desktop.add(asmPanelFrame);

        busPanel = new BusAnalyzer();
        busPanel.setBusStateContainer(emulator.getBus().getBusStateContainer());
        final JInternalFrame busPanelFrame = wrap("IEC", busPanel);
        desktop.add(busPanelFrame);

        final JFrame frame = new JFrame("");

        // register fake IDebuggerView to also track size and location
        // of top-level frame
        loc = new IDebuggerView() {
            private boolean isDisplayed;

            private final Map<String, String> configProperties = new HashMap<>();

            @Override
            public Map<String, String> getConfigProperties() {
                return configProperties;
            }

            @Override
            public String getIdentifier() {
                return "Fake top-level frame view";
            }

            @Override
            public void setConfigProperties(Map<String, String> properties) {
                this.configProperties.putAll(properties);
            }

            @Override
            public void setLocationPeer(Component frame) {
                throw new UnsupportedOperationException("setLocationPeer not supported");
            }

            @Override
            public Component getLocationPeer() {
                return frame;
            }

            @Override
            public boolean isDisplayed() {
                return isDisplayed;
            }

            @Override
            public void refresh() {
            }

            @Override
            public void setDisplayed(boolean yesNo) {
                this.isDisplayed = yesNo;
            }

            @Override
            public boolean isRefreshAfterTick() {
                return false;
            }
        };
        views.add(loc);
        locationHelper.applyLocation(loc);

        // add window listener that saves application state before shutting down
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {

                try {
                    onApplicationShutdown();
                } catch (Exception e2) {
                    e2.printStackTrace();
                } finally {
                    System.exit(0);
                }
            }
        });

        // add menu
        frame.setJMenuBar(createMenu());

        frame.pack();
        frame.setContentPane(desktop);
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

        frame.setVisible(true);

        updateWindows(false);

        final Optional<G64File> current = doWithFloppyAndReturn(floppy -> floppy.getDisk());
        if (current.isPresent()) {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try {
                current.get().toD64(out);
                bamPanel.setDisk(new D64File(new ByteArrayInputStream(out.toByteArray()), current.get().getSource()));
            } catch (Exception e1) {
                e1.printStackTrace();
            }
        } else {
            bamPanel.setDisk(null);
        }
    }

    public static enum DialogMode {
        RESTORE, SAVE
    };

    private class SaveRestoreMemoryDialog extends JDialog {

        private final DialogMode mode;

        private final JTextField selectedFile = new JTextField("/home/tobi/tmp/screen.bin");

        public int startAddress = 0x0;
        public int endAddress = 0xffff;
        public int size = 0xffff;

        private boolean validationEnabled = true;

        private void doNoValidation(Runnable r) {
            validationEnabled = false;
            try {
                r.run();
            } finally {
                validationEnabled = true;
            }
        }

        private ActionListener wrap(ActionListener l) {
            return new ActionListener() {

                @Override
                public void actionPerformed(ActionEvent e) {
                    if (validationEnabled) {
                        l.actionPerformed(e);
                    }
                }
            };
        }

        public SaveRestoreMemoryDialog(DialogMode mode) 
        {
            super((Frame) null, mode == DialogMode.RESTORE ? "Restore memory" : "Save memory");
            setModal(true);
            this.mode = mode;

            final Optional<File> last = getFile( CONFIG_KEY_LAST_MEMORY_FILE );
            last.map( File::getAbsolutePath ).ifPresent( selectedFile::setText );
            
            final JTextField startAddressField = new JTextField();
            final JTextField endAddressField = new JTextField();
            final JTextField sizeField = new JTextField();

            startAddressField.setText(Misc.to16BitHex(startAddress));
            endAddressField.setText(Misc.to16BitHex(endAddress));
            startAddressField.setText(Misc.to16BitHex(startAddress));
            sizeField.setText(Integer.toString(size));

            startAddressField.setColumns(6);
            startAddressField.addActionListener(wrap(ev -> {
                final String value = startAddressField.getText();
                if (Misc.isValidHexAddress(value)) {
                    startAddress = Misc.parseHexAddress(value);
                    if (startAddress > endAddress) {
                        int end = Math.min(startAddress + size, 65535);
                        endAddress = end;
                        doNoValidation(() -> endAddressField.setText(Misc.to16BitHex(end)));
                    }
                } else {
                    error("Invalid address: '" + value + "'");
                }
            }));

            endAddressField.setColumns(6);
            endAddressField.addActionListener(wrap(ev -> {
                final String value = endAddressField.getText();
                if (Misc.isValidHexAddress(value)) {
                    endAddress = Misc.parseHexAddress(value);

                    if (endAddress >= startAddress) {
                        size = endAddress - startAddress;
                        doNoValidation(() -> sizeField.setText(Integer.toString(size)));
                    } else {
                        int min = Math.max(0, size);
                        startAddress = min;
                        size = endAddress - startAddress;
                        doNoValidation(() -> {
                            startAddressField.setText(Misc.to16BitHex(min));
                            sizeField.setText(Integer.toString(size));
                        });
                    }
                } else {
                    error("Invalid address: '" + value + "'");
                }
            }));

            sizeField.setEnabled(mode == DialogMode.SAVE);
            endAddressField.setEnabled(mode == DialogMode.SAVE);
            sizeField.setColumns(6);
            sizeField.addActionListener(wrap(ev -> {
                final String value = sizeField.getText();
                try {
                    int v = Integer.parseInt(value);
                    if (v > 0 && v <= 65536) {
                        size = v;
                        endAddress = Math.max(startAddress + size - 1, 65535);
                        System.out.println("End address: " + startAddress + " + " + size + " = " + endAddress);
                        doNoValidation(() -> endAddressField.setText(Misc.to16BitHex(endAddress)));
                    }
                } catch (Exception e) {
                    error("Invalid size: '" + value + "'");
                }
            }));

            selectedFile.addActionListener(ev -> {
                final String f = selectedFile.getText();
                if (f != null && f.trim().length() > 0) {
                    final File tmp = new File(f);
                    if (mode == DialogMode.RESTORE && !(tmp.isFile() && tmp.length() > 0)) {
                        error("Not a regular file or file is empty: " + tmp.getAbsolutePath());
                    }
                }
            });
            selectedFile.setColumns(20);
            final JButton selectFileButton = new JButton("Select file...");
            selectFileButton.addActionListener(ev -> 
            {
                final JFileChooser chooser = createFileChooser( getFile( CONFIG_KEY_LAST_MEMORY_FILE ) );
                chooser.setFileFilter(new FileFilter() {

                    @Override
                    public boolean accept(File f) {
                        if (mode == DialogMode.RESTORE) {
                            if (f.isFile() && f.length() <= 0) {
                                return false;
                            }
                        }
                        if (f.isFile() && ! (f.getName().toLowerCase().endsWith(".bin") | f.getName().toLowerCase().endsWith(".hex"))) {
                            return false;
                        }
                        return f.isDirectory() || f.isFile();
                    }

                    @Override
                    public String getDescription() {
                        return ".bin,.hex";
                    }

                });
                final int result;
                if (mode == DialogMode.RESTORE) {
                    result = chooser.showOpenDialog(this);
                } else {
                    result = chooser.showSaveDialog(this);
                }
                if (result == JFileChooser.APPROVE_OPTION) 
                {
                    File tmp = chooser.getSelectedFile();
                    if (!isValidFile(tmp.getAbsolutePath())) {
                        error("File " + tmp.getAbsolutePath() + " is either no regular file or empty");
                        return;
                    }
                    sizeField.setText(Long.toString(tmp.length()));
                    selectedFile.setText(tmp.getAbsolutePath());
                }
            });

            final JButton cancelButton = new JButton("Cancel");
            cancelButton.addActionListener(ev -> {
                dispose();
            });
            final JButton submitButton = new JButton(mode == DialogMode.SAVE ? "Save" : "Restore");
            submitButton.addActionListener(ev -> {
                try {
                    onClick();
                    rememberFile(CONFIG_KEY_LAST_MEMORY_FILE, new File( selectedFile.getText() ) );
                    dispose();
                } catch (Exception e) {
                    e.printStackTrace();
                    error((mode == DialogMode.RESTORE ? "Loading" : "Saving") + " to file " + selectedFile.getText()
                    + " failed: " + e.getMessage());
                }
            });

            setLayout(new GridBagLayout());

            // first row
            GridBagConstraints cnstrs = cnstrs(0, 0, 1, 1);
            getContentPane().add(new JLabel("File:"), cnstrs);
            cnstrs = cnstrs(1, 0, 1, 1);
            cnstrs.fill = GridBagConstraints.HORIZONTAL;
            cnstrs.weightx=0.7;
            getContentPane().add(selectedFile, cnstrs);
            cnstrs = cnstrs(2, 0, 1, 1);
            cnstrs.fill = GridBagConstraints.HORIZONTAL;            
            cnstrs.weightx=0.3;
            getContentPane().add(selectFileButton, cnstrs);
            // second row
            cnstrs = cnstrs(0, 1, 1, 1);
            getContentPane().add(new JLabel("Start address:"), cnstrs);
            cnstrs = cnstrs(1, 1, 2, 1);
            cnstrs.fill = GridBagConstraints.HORIZONTAL;
            getContentPane().add(startAddressField, cnstrs);
            // third row
            cnstrs = cnstrs(0, 2, 1, 1);
            getContentPane().add(new JLabel("End address:"), cnstrs);
            cnstrs = cnstrs(1, 2, 2, 1);
            cnstrs.fill = GridBagConstraints.HORIZONTAL;
            getContentPane().add(endAddressField, cnstrs);
            // fourth row
            cnstrs = cnstrs(0, 3, 1, 1);
            getContentPane().add(new JLabel("Size:"), cnstrs);
            cnstrs = cnstrs(1, 3, 2, 1);
            cnstrs.fill = GridBagConstraints.HORIZONTAL;
            getContentPane().add(sizeField, cnstrs);
            // fifth row
            final JPanel buttonPanel = new JPanel();
            buttonPanel.add(cancelButton);
            buttonPanel.add(submitButton);
            buttonPanel.setLayout(new FlowLayout());
            cnstrs = cnstrs(0, 4, 3, 1);
            cnstrs.fill = GridBagConstraints.HORIZONTAL;
            getContentPane().add(buttonPanel, cnstrs);

            setMinimumSize(new Dimension(420, 200));
            pack();
        }

        private boolean isValidFile(String s) {
            if (s == null || s.trim().length() == 0) {
                return false;
            }
            final File f = new File(s);
            if (mode == DialogMode.RESTORE) {
                if (!f.isFile() || f.length() <= 0) {
                    return false;
                }
            }
            return true;
        }

        public void onClick() throws FileNotFoundException, IOException {

            if (!isValidFile(selectedFile.getText())) {
                error("Invalid file: " + selectedFile.getText());
                return;
            }

            System.out.println("Address range: " + startAddress + " - " + endAddress + " , " + size + " bytes");

            final File file = new File(selectedFile.getText().trim());
            if (mode == DialogMode.RESTORE) 
            {
                driver.setMode(Mode.SINGLE_STEP);
                driver.invokeAndWait(emulator -> 
                {
                    final int[] loaded = { 0 };
                    if ( file.getName().toLowerCase().endsWith(".hex" ) ) 
                    {
                        final IntelHex conv = new IntelHex();

                        try ( FileInputStream in = new FileInputStream(file) ) 
                        {
                            conv.parseHex( in , line -> 
                            {
                                System.out.println( line.len+" bytes @ "+Misc.to16BitHex( line.loadOffset ));
                                emulator.getMemory().restoreRAM( new ByteArrayInputStream( line.data ), line.loadOffset );
                                loaded[0] += line.len;
                            });
                        }                        
                    } else {
                        loaded[0] += emulator.getMemory().restoreRAM(new FileInputStream(file), startAddress);
                    }
                    SwingUtilities.invokeLater(() -> 
                    {
                        timedInfo("Restored " + loaded[0] + " bytes from " + file.getAbsolutePath());
                        updateWindows( false );
                    });
                });
            } else {
                driver.setMode(Mode.SINGLE_STEP);
                driver.invokeAndWait(emulator -> {
                    int saved = emulator.getMemory().saveRAM(AddressRange.range(startAddress, startAddress + size - 1),
                            new FileOutputStream(file));
                    SwingUtilities.invokeLater(() -> timedInfo("Saved " + saved + " bytes to " + file.getAbsolutePath()));
                });
            }
        }

        private GridBagConstraints cnstrs(int x, int y, int width, int height) {
            final GridBagConstraints result = new GridBagConstraints();
            result.fill = GridBagConstraints.NONE;
            result.gridx = x;
            result.gridy = y;
            result.gridwidth = width;
            result.gridheight = height;
            result.weightx = 0;
            result.weighty = 0;
            return result;
        }
    }

    private void saveMemoryRegion() {
        new SaveRestoreMemoryDialog(DialogMode.SAVE).setVisible(true);
    }

    private void loadMemoryRegion() {
        new SaveRestoreMemoryDialog(DialogMode.RESTORE).setVisible(true);
    }

    private void saveEmulatorState() throws IOException 
    {
        final JFileChooser chooser = createFileChooser( getFile(CONFIG_KEY_LAST_STATE_FILE) );
        if (chooser.showSaveDialog(null) == JFileChooser.APPROVE_OPTION) 
        {
            final File file = chooser.getSelectedFile();
            try (FileOutputStream out = new FileOutputStream(file)) {
                emulationStateManager.saveEmulationState(out);
                info("Saved state to " + file.getAbsolutePath());
                rememberFile(CONFIG_KEY_LAST_STATE_FILE , file );
            }
        }
    }
    
    private JFileChooser createFileChooser(Optional<File> previousFile) {
        final JFileChooser result = new JFileChooser();
        previousFile.ifPresent( result::setSelectedFile );
        return result;
    }

    private void restoreEmulatorState() throws IOException 
    {
        final JFileChooser chooser = createFileChooser( getFile(CONFIG_KEY_LAST_STATE_FILE) );
        if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) 
        {
            final File file = chooser.getSelectedFile();
            try (FileInputStream in = new FileInputStream(file)) 
            {
                emulationStateManager.restoreEmulationState(in);
                info("Restored state from " + chooser.getSelectedFile().getAbsolutePath());
                rememberFile(CONFIG_KEY_LAST_STATE_FILE,file);
            }
        }
    }
    
    private Optional<File> getFile(String key) 
    {
        final String file = loc.getConfigProperties().get( key );
        return StringUtils.isBlank( file ) ? Optional.empty() : Optional.of( new File(file) );
    }
    
    private void rememberFile(String key,File file) 
    {
        if ( file != null ) {
            loc.getConfigProperties().put(key,file.getAbsolutePath());
        }
    }

    private JMenuBar createMenu() {
        final JMenuBar menuBar = new JMenuBar();

        final JMenu menu = new JMenu("File");
        menuBar.add(menu);

        // memory save/restore
        JMenuItem item = new JMenuItem("Save memory region...");
        item.addActionListener(event -> saveMemoryRegion());
        menu.add(item);

        item = new JMenuItem("Restore memory region...");
        item.addActionListener(event -> loadMemoryRegion());
        menu.add(item);

        menu.addSeparator();

        // state
        item = new JMenuItem("Save emulator state...");
        item.addActionListener(event -> {
            try {
                saveEmulatorState();
            } catch (IOException e) {
                error("Saving state failed: " + e.getMessage());
            }
        });
        menu.add(item);

        item = new JMenuItem("Restore emulator state...");
        item.addActionListener(event -> {
            try {
                restoreEmulatorState();
            } catch (IOException e) {
                error("Restoring state failed: " + e.getMessage());
            }
        });
        menu.add(item);

        menu.addSeparator();

        // disk handling
        item = new JMenuItem("Insert disk...");
        item.addActionListener(event -> insertDisk());
        menu.add(item);

        item = new JMenuItem("Eject disk...");
        item.addActionListener(event -> ejectDisk());
        menu.add(item);

        menu.addSeparator();

        // tape handling
        menu.addSeparator();
        item = new JMenuItem("Insert tape...");
        item.addActionListener(event -> {
            try {
                insertTape();
            } catch (Exception e) {
                showError("Failed to load .t64 file", e);
            }
        });
        menu.add(item);

        item = new JMenuItem("Eject tape...");
        item.addActionListener(event -> ejectTape());
        menu.add(item);

        // add 'Views' menu
        final JMenu views = new JMenu("Views");
        menuBar.add(views);

        setDebugTarget(DebugTarget.COMPUTER);

        JCheckBoxMenuItem debugTargetSelector = new JCheckBoxMenuItem("Debug Floppy #8", false);
        debugTargetSelector.setSelected(debugTarget == DebugTarget.FLOPPY_8);
        debugTargetSelector.addActionListener(ev -> {
            setDebugTarget(debugTargetSelector.isSelected() ? DebugTarget.FLOPPY_8 : DebugTarget.COMPUTER);
        });

        views.add(debugTargetSelector);

        panels.sort((a, b) -> {
            final String title1 = ((JInternalFrame) a.getLocationPeer()).getTitle();
            final String title2 = ((JInternalFrame) b.getLocationPeer()).getTitle();
            return title1.compareTo(title2);
        });
        panels.forEach(loc -> {
            final JInternalFrame peer = (JInternalFrame) loc.getLocationPeer();
            final JCheckBoxMenuItem viewItem = new JCheckBoxMenuItem(peer.getTitle(), loc.isDisplayed());
            viewItem.addActionListener(ev -> {
                loc.setDisplayed(viewItem.isSelected());
                peer.setVisible(viewItem.isSelected());
                peer.toFront();
            });
            views.add(viewItem);
        });

        // create I/O menu
        final JMenu io = new JMenu("I/O");
        menuBar.add(io);

        final JCheckBoxMenuItem joyPort1 = new JCheckBoxMenuItem("Joystick port #1",
                keyboardListener.getJoystickPort() == JoystickPort.PORT_1);
        final JCheckBoxMenuItem joyPort2 = new JCheckBoxMenuItem("Joystick port #2",
                keyboardListener.getJoystickPort() == JoystickPort.PORT_2);

        io.add(joyPort1);
        io.add(joyPort2);

        joyPort1.addActionListener(ev -> {
            if (joyPort1.isSelected()) {
                joyPort2.setSelected(false);
                keyboardListener.setJoystickPort(KeyboardInputListener.JoystickPort.PORT_1);
            }
        });
        joyPort2.addActionListener(ev -> {
            if (joyPort2.isSelected()) {
                joyPort1.setSelected(false);
                keyboardListener.setJoystickPort(KeyboardInputListener.JoystickPort.PORT_2);
            }
        });
        return menuBar;
    }

    private void setDebugTarget(DebugTarget target) {
        this.debugTarget = target;

        final boolean debugC64;
        DiskHardware floppy = null;

        switch (target) {
            case FLOPPY_8:
                final SerialDevice device = emulator.getBus().getDevice(8);
                if (device instanceof DiskHardware) {
                    floppy = (DiskHardware) device;
                    debugC64 = false;
                } else {
                    debugC64 = true;
                }
                break;
            case COMPUTER:
                debugC64 = true;
                break;
            default:
                throw new RuntimeException("Unhandled switch/case: " + debugTarget);
        }

        c64BreakpointsController.removeBreakpointListener(disassembly);
        c64BreakpointsController.removeBreakpointListener(bpModel);

        if (floppyBreakpointsController != null) {
            floppyBreakpointsController.removeBreakpointListener(disassembly);
            floppyBreakpointsController.removeBreakpointListener(bpModel);
        }

        if (debugC64 || floppy == null) {
            debugCPU.set(emulator.getCPU());
            debugMemory.set(emulator.getMemory());
            breakpointsController.set(c64BreakpointsController);
        } else {
            debugCPU.set(floppy.getCPU());
            debugMemory.set(floppy.getMemory());

            if (floppyBreakpointsController == null) {
                floppyBreakpointsController = new BreakpointsController(debugCPU.get(), debugMemory.get());
            }
            breakpointsController.set(floppyBreakpointsController);
        }

        breakpointsController.get().addBreakpointListener(disassembly);
        breakpointsController.get().addBreakpointListener(bpModel);
        disassembly.allBreakpointsChanged();
        bpModel.allBreakpointsChanged();
    }

    private void ejectDisk() {
        try {
            doWithFloppy(floppy -> floppy.ejectDisk());
        } catch (Exception e) {
            showError("Failed to eject disk", e);
        }
    }

    private void doWithFloppy(Consumer<DiskHardware> consumer) {
        driver.invokeAndWait(emulator -> {
            emulator.getMemory().ioArea.iecBus.getDevices().stream().filter(dev -> dev instanceof DiskHardware)
            .map(dev -> (DiskHardware) dev).findFirst().ifPresent(consumer);
        });
    }

    private <T> Optional<T> doWithFloppyAndReturn(Function<DiskHardware, Optional<T>> consumer) {
        final CallbackWithResult<Optional<T>> cb = new CallbackWithResult<Optional<T>>(emulator -> {
            final Optional<DiskHardware> floppy = emulator.getMemory().ioArea.iecBus.getDevices().stream()
                    .filter(dev -> dev instanceof DiskHardware).map(dev -> (DiskHardware) dev).findFirst();
            if (floppy.isPresent()) {
                return consumer.apply(floppy.get());
            }
            return Optional.empty();
        });
        driver.invokeAndWait(cb);
        return cb.getResult();
    }

    private void insertTape() throws IOException {
        final String lastFile = loc.getConfigProperties().get("last_t64_file");
        final JFileChooser chooser;
        if (StringUtils.isNotBlank(lastFile)) {
            chooser = new JFileChooser(new File(lastFile));
        } else {
            chooser = new JFileChooser(new File("/home/tgierke/mars_workspace/j6502/tapes"));
        }

        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || (f.isFile()
                        && (f.getName().toLowerCase().endsWith(".t64") || f.getName().toLowerCase().endsWith(".tap")));
            }

            @Override
            public String getDescription() {
                return ".t64 / .tap";
            }
        });
        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }

        final File file = chooser.getSelectedFile();

        loc.getConfigProperties().put("last_t64_file", file.getAbsolutePath());
        driver.invokeAndWait(emulator -> {
            emulator.tapeDrive.insert(TapeFile.load(file));
        });
    }

    private void ejectTape() {
        driver.invokeAndWait(emulator -> {
            emulator.tapeDrive.eject();
        });
    }

    private void insertDisk() {
        final String lastFile = loc.getConfigProperties().get("last_d64_file");
        final JFileChooser chooser;
        if (StringUtils.isNotBlank(lastFile)) {
            chooser = new JFileChooser(new File(lastFile));
        } else {
            chooser = new JFileChooser(new File("/home/tobi/mars_workspace/j6502/src/main/resources/disks"));
        }

        chooser.setFileFilter(new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || (f.isFile()
                        && (f.getName().toLowerCase().endsWith(".g64") || f.getName().toLowerCase().endsWith(".d64")));
            }

            @Override
            public String getDescription() {
                return ".g64 / .d64";
            }
        });

        if (chooser.showOpenDialog(null) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        final File file = chooser.getSelectedFile();

        // TODO: Implement file-type (.d64 / .g64) detection based on file
        // content and not just the suffix...
        final boolean isD64File = file.getName().toLowerCase().endsWith(".d64");

        loc.getConfigProperties().put("last_d64_file", file.getAbsolutePath());

        try {
            doWithFloppy(floppy -> {
                final G64File g64File;
                final D64File d64File;
                try {
                    final ByteArrayOutputStream out = new ByteArrayOutputStream();
                    if (isD64File) {
                        d64File = new D64File(file);
                        G64File.toG64(d64File, out);
                        g64File = new G64File(new ByteArrayInputStream(out.toByteArray()), file.getAbsolutePath());
                    } else {
                        g64File = new G64File(new FileInputStream(file), file.getAbsolutePath());
                        g64File.toD64(out);
                        d64File = new D64File(new ByteArrayInputStream(out.toByteArray()), file.getAbsolutePath());
                    }
                } catch (Exception e) {
                    if (e instanceof RuntimeException) {
                        throw (RuntimeException) e;
                    }
                    throw new RuntimeException(e);
                }

                floppy.loadDisk(g64File);
                SwingUtilities.invokeLater(() -> bamPanel.setDisk(d64File));
            });
        } catch (Exception e) {
            showError("Failed to load disk file " + file.getAbsolutePath(), e);
        }
    }

    private void showError(String message, Throwable t) {
        final String[] msg = { message };
        if (t != null) {
            msg[0] += "\n";
            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            final PrintWriter writer = new PrintWriter(out);
            t.printStackTrace(writer);
            writer.close();
            try {
                msg[0] += out.toString("UTF-8");
            } catch (UnsupportedEncodingException e) {
                e.printStackTrace();
            }
        }
        invokeAndWait(() -> JOptionPane.showConfirmDialog(null, msg[0], "Error", JOptionPane.ERROR_MESSAGE));
    }

    private void invokeAndWait(Runnable r) {
        try {
            if (SwingUtilities.isEventDispatchThread()) {
                r.run();
            } else {
                SwingUtilities.invokeAndWait(r);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void onApplicationShutdown() {
        try {
            views.forEach(locationHelper::updateConfiguration);
            locationHelper.saveAll();
        } catch (IOException e1) {
            e1.printStackTrace();
        }
    }

    private JInternalFrame wrap(String title, JPanel panel) {
        setup(panel);

        final JInternalFrame frame = new JInternalFrame(title);

        if (panel instanceof IDebuggerView) {
            final IDebuggerView view = (IDebuggerView) panel;
            panels.add(view);
            view.setLocationPeer(frame);
            views.add(view);
            locationHelper.applyLocation(view);
        }

        frame.setResizable(true);
        frame.getContentPane().add(panel);
        frame.pack();
        if (panel instanceof IDebuggerView) {
            frame.setVisible(((IDebuggerView) panel).isDisplayed());
        } else {
            frame.setVisible(true);
        }

        return frame;
    }

    protected final class ButtonToolbar extends JPanel implements WindowLocationHelper.IDebuggerView {
        public final JButton singleStepButton = new JButton("Step");
        public final JButton toggleDisplayToolbar = new JButton("Toggle display");
        public final JButton runButton = new JButton("Run");
        public final JButton stopButton = new JButton("Stop");
        public final JButton breakOnIRQButton = new JButton("Break on IRQ");
        public final JButton resetButton = new JButton("Reset");
        public final JButton stepOverButton = new JButton("Step over");
        public final JButton loadButton = new JButton("Load");
        public final JButton toggleSpeedButton = new JButton("Toggle speed");
        public final JToggleButton tapePlay = new JToggleButton("Tape: Play", false);
        public final JButton saveTape = new JButton("Save tape");
        public final JButton refreshUIButton = new JButton("Refresh UI");
        public final JButton printBacktraceButton = new JButton("Show backtrace");

        private final KeyAdapter keyListener = new KeyAdapter() {
            public void keyReleased(KeyEvent e) {
                if (e.getKeyCode() == KeyEvent.VK_F6) {
                    singleStepButton.doClick();
                }
            }
        };

        private final Map<String, String> config = new HashMap<>();

        private Component peer;
        private boolean isDisplayed;

        @Override
        public void setLocationPeer(Component frame) {
            this.peer = frame;
        }

        @Override
        public String getIdentifier() {
            return "Button toolbar view";
        }

        @Override
        public Component getLocationPeer() {
            return peer;
        }

        @Override
        public boolean isDisplayed() {
            return isDisplayed;
        }

        @Override
        public void setDisplayed(boolean yesNo) {
            this.isDisplayed = yesNo;
        }

        @Override
        public void setConfigProperties(Map<String, String> properties) {
            this.config.clear();
            this.config.putAll(properties);
        }

        @Override
        public Map<String, String> getConfigProperties() {
            return this.config;
        }

        @Override
        public void refresh() {
            updateButtons();
            repaint();
        }

        private void prepareTest() {
            driver.setMode(Mode.SINGLE_STEP);

            final String source = loadTestProgram();

            final AST ast;
            try {
                ast = new Parser(new Lexer(new Scanner(source))).parse();
            } catch (Exception e) {
                DisassemblerTest.maybePrintError(source, e);
                throw e;
            }

            final Assembler a = new Assembler();
            final byte[] binary = a.assemble(ast);
            final int origin = a.getOrigin();

            final IMemoryProvider provider = new IMemoryProvider() {

                @Override
                public void loadInto(IMemoryRegion region) {
                    region.bulkWrite(origin, binary, 0, binary.length);
                }
            };

            driver.invokeAndWait(emulator -> {
                emulator.reset();
                emulator.setMemoryProvider(provider);
                emulator.getCPU().pc(origin);
            });

            final BreakpointsController bpController = getBreakPointsController();
            bpController.removeAllBreakpoints();
            bpController.addBreakpoint(Breakpoint.unconditionalBreakpoint((short) 0x45bf));
            bpController.addBreakpoint(Breakpoint.unconditionalBreakpoint((short) 0x40cb));
            hexPanel.setAddress((short) 0x210);
            updateWindows(false);
        }

        @Override
        public boolean isRefreshAfterTick() {
            return true;
        }

        private String loadTestProgram() {
            InputStream in = EmulatorTest.class.getResourceAsStream("/test.asm");
            if (in == null) {
                throw new RuntimeException("Failed to load /test.asm from classpath");
            }
            final StringBuilder buffer = new StringBuilder();
            try {
                IOUtils.readLines(in).forEach(line -> buffer.append(line).append("\n"));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            final String source = buffer.toString();
            return source;
        }

        public ButtonToolbar() {
            setPreferredSize(new Dimension(400, 50));

            setFocusable(true);
            addKeyListener(keyListener);

            loadButton.addActionListener(event -> {
                prepareTest();
            });

            toggleDisplayToolbar.addActionListener(event -> {
                driver.invokeLater(emulator -> {
                    emulator.getVIC().setDisplayEnabled(!emulator.getVIC().isDisplayEnabled());
                });
            });

            singleStepButton.addActionListener(event -> {
                try {
                    driver.singleStep(getCPU());
                } catch (final Throwable t) {
                    t.printStackTrace();
                } finally {
                    updateWindows(false);
                }
            });

            resetButton.addActionListener(ev -> {
                driver.setMode(Mode.SINGLE_STEP);
                driver.invokeAndWait(emulator -> {
                    emulator.reset();
                });
                updateWindows(false);
            });

            runButton.addActionListener(ev -> driver.setMode(Mode.CONTINOUS));
            stopButton.addActionListener(event -> driver.setMode(Mode.SINGLE_STEP));

            stepOverButton.addActionListener(ev -> {
                getBreakPointsController().stepReturn(getMemory(), getCPU());
                driver.setMode(Mode.CONTINOUS);
            });

            final boolean[] trueSpeed = { true };
            driver.setTrueSpeed();

            toggleSpeedButton.addActionListener(ev -> {
                if (trueSpeed[0]) {
                    driver.setMaxSpeed();
                } else {
                    driver.setTrueSpeed();
                }
                trueSpeed[0] = !trueSpeed[0];
            });

            printBacktraceButton.addActionListener( ev -> 
            {
                driver.invokeAndWait( emulator -> 
                {
                    getCPU().printBacktrace( System.out );
                });

            });

            tapePlay.addActionListener(ev -> {
                driver.invokeAndWait(emulator -> {
                    emulator.tapeDrive.setKeyPressed(tapePlay.isSelected());
                });
            });

            saveTape.addActionListener(ev -> {
                saveTape();
            });

            refreshUIButton.addActionListener(ev -> {
                updateWindows(false);
            });
            breakOnIRQButton.addActionListener(ev -> {
                getCPU().setBreakOnInterrupt();
                breakOnIRQButton.setEnabled(false);
            });

            setLayout(new FlowLayout());

            final List<AbstractButton> allButtons = Arrays.asList(stopButton, runButton, singleStepButton,
                    stepOverButton, resetButton, breakOnIRQButton, loadButton, toggleSpeedButton, saveTape, tapePlay,
                    refreshUIButton, toggleDisplayToolbar,printBacktraceButton);
            for (AbstractButton button : allButtons) {
                button.setFocusable(false);
                add(button);
            }

            updateButtons();
        }

        public void updateButtons() {
            final Mode currentMode = driver.getMode();
            singleStepButton.setEnabled(currentMode == Mode.SINGLE_STEP);
            stopButton.setEnabled(currentMode != Mode.SINGLE_STEP);
            runButton.setEnabled(currentMode == Mode.SINGLE_STEP);
            resetButton.setEnabled(currentMode == Mode.SINGLE_STEP);
            stepOverButton.setEnabled(getBreakPointsController().canStepOver(getMemory(), getCPU()));
            breakOnIRQButton.setEnabled(!getCPU().isBreakOnIRQ());
        }

        protected void saveTape() {

            final File lastSavedFile;
            if (config.getOrDefault("tape_recording", null) != null) {
                lastSavedFile = new File(config.get("tape_recording"));
            } else {
                lastSavedFile = null;
            }
            final JFileChooser chooser = new JFileChooser();
            if (lastSavedFile != null) {
                chooser.setSelectedFile(lastSavedFile);
            }
            if (chooser.showSaveDialog(null) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            final File file = chooser.getSelectedFile();
            config.put("tape_recording", file.getAbsolutePath());

            final CallbackWithResult<int[]> callback = new CallbackWithResult<int[]>(new Function<Emulator, int[]>() {

                @Override
                public int[] apply(Emulator emulator) {
                    return emulator.tapeDrive.getRecording();
                }

            });
            driver.invokeAndWait(callback);
            final int[] data = callback.getResult();

            // write tap file
            /*
             * Bytes: $0000-000B: File signature "C64-TAPE-RAW" 000C: TAP
             * version (see below for description) $00 - Original layout 01 -
             * Updated 000D-000F: Future expansion 0010-0013: File data size
             * (not including this header, in LOW/HIGH format) i.e. This image
             * is $00082151 bytes long. 0014-xxxx: File data
             */
            final ByteArrayOutputStream out = new ByteArrayOutputStream();

            try (FileOutputStream fileOut = new FileOutputStream(file)) {
                // write header
                final char[] headerChars = "C64-TAPE-RAW".toCharArray();
                final byte[] HEADER = new byte[headerChars.length];
                for (int i = 0; i < headerChars.length; i++) {
                    HEADER[i] = (byte) headerChars[i];
                }
                out.write(HEADER);

                // write file format version
                out.write((byte) 0x01);

                // write expansion bytes
                out.write(new byte[] { 0, 0, 0 });

                // write file size
                final int sizeLo = data.length & 0xff;
                final int sizeHi = (data.length >>> 8) & 0xff;
                out.write(sizeLo);
                out.write(sizeHi);

                // write data
                for (int cycles : data) {
                    /*
                     * (8 * data byte) pulse length (in seconds) =
                     * ---------------- clock cycles
                     * 
                     * Therefore, a data value of $2F (47 in decimal) would be:
                     * 
                     * (47 * 8) / 985248 = 0.00038975 seconds. = 0.00038163
                     * seconds.
                     * 
                     *
                     * clock_cyles --------------- = data byte 8
                     * 
                     */
                    final int dataByte = Math.round(cycles / 8f);
                    if (dataByte < 255) {
                        System.out.println(cycles + " cycles => $" + Integer.toHexString(dataByte));
                        out.write((byte) dataByte);
                    } else {
                        /*
                         * Overflow.
                         * 
                         * Data value of 00 is followed by three bytes,
                         * representing a 24 bit value of C64 _CYCLES_ (NOT
                         * cycles/8). The order is as follow: 00 <bit0-7>
                         * <bit8-15> <bit16-24>.
                         */
                        System.err.println("Overflow detected, tape pulse lasted " + cycles + " CPU cycles");
                        out.write(0);
                        out.write((byte) (cycles & 0xff));
                        out.write((byte) ((cycles >>> 8) & 0xff));
                        out.write((byte) ((cycles >>> 16) & 0xff));
                    }
                }

                // write everything to the file
                fileOut.write(out.toByteArray());
                System.out.println("Success - TAP64 written to " + file.getAbsolutePath());
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public void repaint() {
        updateWindows(false);
    }

    protected void updateWindows(boolean isTick) 
    {
        synchronized (emulator) 
        {
            for (int i = views.size() - 1; i >= 0; i--) {
                final IDebuggerView view = views.get(i);
                if (view.isDisplayed() && ( ! isTick || view.isRefreshAfterTick() ) ) {
                    if (Constants.DEBUGGER_DEBUG_VIEW_PERFORMANCE) {
                        benchmark(view);
                    } else {
                        view.refresh();
                        if (view instanceof Component) {
                            ((Component) view).repaint();
                        }                        
                    }
                }
            }

            if (Constants.DEBUGGER_DEBUG_VIEW_PERFORMANCE && isTick) {
                viewPerformanceTicks++;
                if ((viewPerformanceTicks % 3) == 0) {
                    final DecimalFormat DF = new DecimalFormat("######0.0##");
                    final StringBuilder buffer = new StringBuilder("\n");
                    for (ViewMetrics metrics : viewPerformanceMetrics.values()) {

                        buffer.append(Thread.currentThread().getName()).append(":").append(metrics.viewIdentifier)
                        .append(": ").append(DF.format(metrics.getAverageTimeMs())).append(" ms\n");
                    }
                    ;
                    System.out.print(buffer);
                    System.out.flush();
                }
            }
        }
        //        if ( ! isTick ) 
        //        {
        //            for (int i = 0, len = views.size(); i < len; i++) {
        //                final IDebuggerView view = views.get(i);
        //                if (view.isDisplayed() && view instanceof Component) {
        //                    ((Component) view).repaint();
        //                }
        //            }
        //        }
    }

    private void benchmark(final IDebuggerView view) {
        final long now = System.nanoTime();
        view.refresh();
        if ( view instanceof Component) {
            ((Component) view).repaint();
        }
        final long delta = System.nanoTime() - now;

        ViewMetrics metrics = viewPerformanceMetrics.get(view);
        if (metrics == null) {
            metrics = new ViewMetrics(view);
            viewPerformanceMetrics.put(view, metrics);
        }
        metrics.update(delta);
    }

    protected final class CalculatorPanel extends JPanel implements IDebuggerView {

        private Component peer;
        private boolean isDisplayed;

        private JTextField input = new JTextField();
        private JTextField hexOutput = new JTextField();
        private JTextField decOutput = new JTextField();
        private JTextField binOutput = new JTextField();

        public CalculatorPanel() {
            setLayout(new FlowLayout());

            input.addActionListener(ev -> {
                String text = input.getText();
                Integer inputValue = null;
                if (StringUtils.isNotBlank(text)) {
                    text = text.trim();
                    try {
                        if (text.startsWith("$")) {
                            inputValue = Integer.parseInt(text.substring(1), 16);
                        } else if (text.startsWith("0x")) {
                            inputValue = Integer.parseInt(text.substring(2), 16);
                        } else if (text.startsWith("0b")) {
                            inputValue = Integer.parseInt(text.substring(2), 2);
                        } else if (text.startsWith("%")) {
                            inputValue = Integer.parseInt(text.substring(1), 2);
                        } else {
                            inputValue = Integer.parseInt(text);
                        }
                    } catch (Exception e) {
                    }
                }
                if (inputValue != null) {
                    hexOutput.setText(Integer.toHexString(inputValue));
                    decOutput.setText(Integer.toString(inputValue));
                    binOutput.setText(Integer.toBinaryString(inputValue));
                } else {
                    hexOutput.setText("No/invalid input");
                    decOutput.setText("No/invalid input");
                    binOutput.setText("No/invalid input");
                }
            });

            input.setColumns(16);
            hexOutput.setColumns(16);
            decOutput.setColumns(16);
            binOutput.setColumns(16);

            setup(input, "To convert");
            setup(hexOutput, "Hexadecimal");
            setup(decOutput, "Decimal");
            setup(binOutput, "Binary");

            add(input);
            add(hexOutput);
            add(decOutput);
            add(binOutput);
            hexOutput.setEditable(false);
            decOutput.setEditable(false);
            binOutput.setEditable(false);

            setPreferredSize(new Dimension(200, 100));
        }

        @Override
        public String getIdentifier() {
            return "Calculator view";
        }

        @Override
        public void refresh() {
        }

        @Override
        public boolean isRefreshAfterTick() {
            return false;
        }

        private void setup(JComponent c, String label) {
            TitledBorder border = BorderFactory.createTitledBorder(label);
            border.setTitleColor(Color.WHITE);
            c.setBorder(border);
            c.setBackground(Color.BLACK);
            c.setForeground(Color.GREEN);
        }

        @Override
        public void setLocationPeer(Component frame) {
            this.peer = frame;
        }

        @Override
        public Component getLocationPeer() {
            return peer;
        }

        @Override
        public void setDisplayed(boolean yesNo) {
            this.isDisplayed = yesNo;
        }

        @Override
        public boolean isDisplayed() {
            return isDisplayed;
        }
    }

    protected final class ScreenPanel extends JPanel implements WindowLocationHelper.IDebuggerView {

        private Component frame;
        private final AtomicBoolean isDisplayed = new AtomicBoolean();

        public ScreenPanel() {
            setFocusable(true);
            setRequestFocusEnabled(true);
            keyboardListener.attach(this);

            setPreferredSize(new Dimension(VIC.DISPLAY_AREA_WIDTH, VIC.DISPLAY_AREA_HEIGHT));

            // 16 ms = 60hz screen refresh
            final Timer timer = new Timer(16, ev -> repaint());
            timer.start();
        }

        @Override
        public boolean isRefreshAfterTick() {
            return false;
        }

        @Override
        public void refresh() {
            repaint();
        }

        @Override
        public void setLocationPeer(Component frame) {
            this.frame = frame;
            frame.setPreferredSize(new Dimension(VIC.DISPLAY_AREA_WIDTH, VIC.DISPLAY_AREA_HEIGHT));
            frame.setSize(new Dimension(VIC.DISPLAY_AREA_WIDTH, VIC.DISPLAY_AREA_HEIGHT));
        }

        @Override
        public Component getLocationPeer() {
            return this.frame;
        }

        @Override
        public String getIdentifier() {
            return "Screen view";
        }

        @Override
        public void setDisplayed(boolean yesNo) {
            this.isDisplayed.set(yesNo);
        }

        @Override
        public boolean isDisplayed() {
            return isDisplayed.get();
        }

        @Override
        protected void paintComponent(Graphics g) {
            // no need to synchronized here since all
            // elements of the call-chain (emulator , result of
            // emulator.getVic(), render() method)
            // either only access final variables OR come with their own
            // synchronization (render() method)
            emulator.getVIC().render((Graphics2D) g, getWidth(), getHeight());
        }
    }

    public static void setup(JComponent c1, JComponent... other) {
        setup(c1);

        if (other != null) {
            for (JComponent c : other) {
                setup(c);
            }
        }
    }

    public static void setup(JComponent c) {
        setMonoSpacedFont(c);
        setColors(c);
    }

    public static void setMonoSpacedFont(JComponent c) {
        c.setFont(MONO_FONT);
    }

    public static void setup(Graphics2D g) {
        g.setFont(MONO_FONT);
        g.setColor(FG_COLOR);
        g.setBackground(BG_COLOR);
    }

    public static void setColors(JComponent c) {
        c.setBackground(BG_COLOR);
        c.setForeground(FG_COLOR);
    }

    protected static final class LineWithBounds {
        public final Disassembler.Line line;
        public final Rectangle bounds;

        public LineWithBounds(Disassembler.Line line, Rectangle bounds) {
            this.line = line;
            this.bounds = bounds;
        }

        public boolean isClicked(int x, int y) {
            return y >= bounds.y && y <= bounds.y + bounds.height;
        }
    }

    protected final class HexDumpPanel extends BufferedView implements WindowLocationHelper.IDebuggerView {
        private final int bytesToDisplay = 25 * 40;

        private boolean isDisplayed;
        private Component peer;

        private short startAddress = 0;

        private final HexDump hexdump;

        public HexDumpPanel() {
            this.hexdump = new HexDump();
            this.hexdump.setBytesPerLine(16);
            this.hexdump.setPrintAddress(true);

            setFocusable(true);
            setRequestFocusEnabled(true);

            addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(java.awt.event.KeyEvent e) {
                    if (e.getKeyCode() == KeyEvent.VK_PAGE_DOWN) {
                        nextPage();
                    } else if (e.getKeyCode() == KeyEvent.VK_PAGE_UP) {
                        previousPage();
                    } else if (e.getKeyCode() == KeyEvent.VK_G) {
                        Short adr = queryAddress();
                        if (adr != null) {
                            setAddress(adr);
                        }
                    }
                }
            });
        }

        @Override
        protected void initGraphics(Graphics2D g) {
            setup(g);
        }

        @Override
        public boolean isRefreshAfterTick() {
            return true;
        }

        @Override
        public void refresh() {
            final Graphics2D g = getBackBufferGraphics();
            try {
                final IMemoryRegion memory = getMemory();
                final String[] lines = hexdump.dump(startAddress, memory, startAddress, bytesToDisplay).split("\n");

                int y = 15;

                g.setColor(Color.GREEN);
                for (String line : lines) {
                    g.drawString(line, 5, y);
                    y += LINE_HEIGHT;
                }
            } finally {
                swapBuffers();
            }

            repaint();
        }

        public void setAddress(short adr) {
            this.startAddress = adr;
            refresh();
        }

        public void nextPage() {
            startAddress = (short) ((startAddress + bytesToDisplay) & 0xffff);
            refresh();
        }

        public void previousPage() {
            startAddress = (short) ((startAddress - bytesToDisplay) & 0xffff);
            refresh();
        }

        @Override
        public String getIdentifier() {
            return "Memory dump view";
        }

        @Override
        public void setLocationPeer(Component frame) {
            this.peer = frame;
        }

        @Override
        public Component getLocationPeer() {
            return this.peer;
        }

        @Override
        public void setDisplayed(boolean yesNo) {
            this.isDisplayed = yesNo;
        }

        @Override
        public boolean isDisplayed() {
            return isDisplayed;
        }
    }

    public static Short queryAddress() {
        final String s = JOptionPane.showInputDialog(null, "Enter hexadecimal address (0-ffff)", "Enter address",
                JOptionPane.PLAIN_MESSAGE);

        if (s == null) {
            return null;
        }
        return (short) Integer.valueOf(s, 16).intValue();
    }

    public final class BreakpointsWindow extends JPanel implements IDebuggerView {

        private boolean isDisplayed;
        private Component peer;

        public BreakpointsWindow() {
            setColors(this);

            final JTable breakpointTable = new JTable(bpModel);
            setColors(breakpointTable);
            breakpointTable.setFillsViewportHeight(true);

            breakpointTable.getTableHeader().setForeground(FG_COLOR);
            breakpointTable.getTableHeader().setBackground(BG_COLOR);

            setup(breakpointTable);
            final JScrollPane scrollPane = new JScrollPane(breakpointTable);
            setup(scrollPane);

            breakpointTable.setMinimumSize(new Dimension(50, 50));
            setLayout(new BorderLayout());
            add(scrollPane, BorderLayout.CENTER);

            breakpointTable.addKeyListener(new KeyAdapter() {
                @Override
                public void keyReleased(KeyEvent event) {
                    if (event.getKeyCode() == KeyEvent.VK_DELETE) {
                        final int[] selectedRows = breakpointTable.getSelectedRows();
                        if (selectedRows != null) {
                            List<Breakpoint> toRemove = new ArrayList<>();
                            for (int idx : selectedRows) {
                                toRemove.add(bpModel.getRow(idx));
                            }
                            toRemove.forEach(bp -> getBreakPointsController().removeBreakpoint(bp));
                        }
                    } else if (event.getKeyCode() == KeyEvent.VK_INSERT) {
                        final String sAddress = JOptionPane.showInputDialog("Enter breakpoint address", "$450c");
                        final Integer address = Misc.parseHexAddress(sAddress);
                        if (address != null) {
                            getBreakPointsController().addBreakpoint(Breakpoint.unconditionalBreakpoint(address));
                        }
                    }
                }
            });
            breakpointTable.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1) {
                        int row = breakpointTable.rowAtPoint(e.getPoint());
                        if (row != -1) {
                            final Breakpoint breakpoint = bpModel.getRow(row);
                            disassembly.setAddress((short) breakpoint.address, null);
                        }
                    }
                }
            });
        }

        @Override
        public boolean isRefreshAfterTick() {
            return false;
        }

        @Override
        public void refresh() {
            repaint();
        }

        @Override
        public String getIdentifier() {
            return "Breakpoints view";
        }

        @Override
        public void setLocationPeer(Component frame) {
            this.peer = frame;
        }

        @Override
        public Component getLocationPeer() {
            return peer;
        }

        @Override
        public void setDisplayed(boolean yesNo) {
            this.isDisplayed = yesNo;
        }

        @Override
        public boolean isDisplayed() {
            return isDisplayed;
        }
    }

    protected final class BreakpointModel extends AbstractTableModel implements IBreakpointLister {
        private volatile boolean breakpointsChanged = true;
        private volatile List<Breakpoint> cachedBreakpoints;

        private List<Breakpoint> getBreakpoints() {
            if (breakpointsChanged || cachedBreakpoints == null) {
                driver.invokeAndWait(emulator -> {
                    cachedBreakpoints = getBreakPointsController().getBreakpoints();
                    cachedBreakpoints.sort((bp1, bp2) -> Integer.compare(bp1.address, bp2.address));
                    breakpointsChanged = false;
                });
            }
            return new ArrayList<>(cachedBreakpoints);
        }

        public Breakpoint getRow(int idx) {
            return getBreakpoints().get(idx);
        }

        @Override
        public String getColumnName(int column) {
            switch (column) {
                case 0:
                    return "Address";
                case 1:
                    return "Enabled";
                case 2:
                    return "Condition";
                default:
                    return "unknown";
            }
        }

        @Override
        public int getRowCount() {
            return getBreakpoints().size();
        }

        @Override
        public int getColumnCount() {
            return 3;
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch (columnIndex) {
                case 1:
                    return Boolean.class;
                case 0:
                case 2:
                    return String.class;
                default:
                    throw new RuntimeException("Unhandled index " + columnIndex);
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) {
            return columnIndex == 1 || columnIndex == 2;
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) {
            final Breakpoint currentBP = getBreakpoints().get(rowIndex);
            final Breakpoint newBP;
            if (columnIndex == 1) {
                newBP = currentBP.withEnabled((Boolean) aValue);
            } else {
                if (!(aValue instanceof String) || !Breakpoint.isValidExpression((String) aValue)) {
                    throw new IllegalArgumentException("Invalid breakpoint condition: " + aValue);
                }

                newBP = Breakpoint.toBreakpoint(currentBP.address, false, (String) aValue, true);
            }
            getBreakPointsController().addBreakpoint(newBP);
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) {
            Breakpoint bp = getBreakpoints().get(rowIndex);
            switch (columnIndex) {
                case 0:
                    return HexDump.toAdr(bp.address);
                case 1:
                    return bp.isEnabled;
                case 2:
                    return bp.pattern == null ? "<unconditional breakpoint>" : bp.pattern;
                default:
                    throw new RuntimeException("Unreachable code reached");
            }
        }

        @Override
        public void allBreakpointsChanged() {
            breakpointsChanged = true;
            fireTableDataChanged();
        }

        @Override
        public void breakpointAdded(Breakpoint bp) {
            breakpointsChanged = true;
            fireTableDataChanged();
        }

        @Override
        public void breakpointRemoved(Breakpoint bp) {
            breakpointsChanged = true;
            fireTableDataChanged();
        }

        @Override
        public void breakpointReplaced(Breakpoint old, Breakpoint newBp) {
            breakpointsChanged = true;
            fireTableDataChanged();
        }
    };

    protected final CPU getCPU() {
        return debugCPU.get();
    }

    protected final IMemoryRegion getMemory() {
        return debugMemory.get();
    }

    protected final BreakpointsController getBreakPointsController() {
        return breakpointsController.get();
    }

    protected static void error(String msg) {
        JOptionPane.showMessageDialog(null, msg, "Error", JOptionPane.ERROR_MESSAGE);
    }

    public static void info(String msg) {
        JOptionPane.showMessageDialog(null, msg, "Info", JOptionPane.INFORMATION_MESSAGE);
    }
    
    public static void timedInfo(String msg) 
    {
            final JOptionPane pane = new JOptionPane(msg, JOptionPane.INFORMATION_MESSAGE,JOptionPane.DEFAULT_OPTION);
            final JDialog dialog = pane.createDialog(null,"Info");
            dialog.setModal( false );
            dialog.setVisible(true);

            final Timer[] timer = {null};
            timer[0] = new Timer(1000 , ev -> 
            {
                timer[0].stop();
                dialog.dispose();
            });
            timer[0].start();
    }
}