package de.codesourcery.j6502.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBoxMenuItem;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.border.TitledBorder;
import javax.swing.table.AbstractTableModel;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.assembler.Assembler;
import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Parser;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.disassembler.Disassembler.Line;
import de.codesourcery.j6502.disassembler.DisassemblerTest;
import de.codesourcery.j6502.emulator.Breakpoint;
import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.CPU.Flag;
import de.codesourcery.j6502.emulator.D64File;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.EmulatorDriver;
import de.codesourcery.j6502.emulator.EmulatorDriver.IBreakpointLister;
import de.codesourcery.j6502.emulator.EmulatorDriver.Mode;
import de.codesourcery.j6502.emulator.EmulatorTest;
import de.codesourcery.j6502.emulator.Floppy;
import de.codesourcery.j6502.emulator.IECBus.StateSnapshot;
import de.codesourcery.j6502.emulator.IMemoryProvider;
import de.codesourcery.j6502.emulator.IMemoryRegion;
import de.codesourcery.j6502.ui.KeyboardInputListener.JoystickPort;
import de.codesourcery.j6502.ui.WindowLocationHelper.IDebuggerView;
import de.codesourcery.j6502.utils.HexDump;

public class Debugger
{
	/**
	 * Time between consecutive UI updates while trying to run emulation at full speed.
	 */
	public static final int UI_REFRESH_MILLIS = 500;

	protected final Emulator emulator = new Emulator();

	protected static final int LINE_HEIGHT = 15;

	protected static final Color FG_COLOR = Color.GREEN;
	protected static final Color BG_COLOR = Color.BLACK;
	protected static final int VERTICAL_LINE_SPACING = 2;
	protected static final Font MONO_FONT = new Font( "Monospaced", Font.PLAIN, 12 );

	protected final WindowLocationHelper locationHelper = new WindowLocationHelper();

	protected final EmulatorDriver driver = new EmulatorDriver( emulator ) {

		private long lastTick;

		@Override
		protected void onStopHook(Throwable t) { SwingUtilities.invokeLater( () -> updateWindows(false) ); }

		@Override
		protected void onStartHook() { SwingUtilities.invokeLater( () -> updateWindows(false) ); }

		@Override
		protected void tick()
		{
			final long now = System.currentTimeMillis();
			long age = now - lastTick;
			if ( age > UI_REFRESH_MILLIS ) // do not post more than 60 events / second to not overload the Swing Event handling queue
			{
				lastTick = now;
				try
				{
					SwingUtilities.invokeAndWait( () -> updateWindows(true) );
				}
				catch (Exception e) {
					e.printStackTrace();
				}
			}
		}
	};

	private final JDesktopPane desktop = new JDesktopPane();

	private final KeyboardInputListener keyboardListener = new KeyboardInputListener(emulator);

	private final List<IDebuggerView> views = new ArrayList<>();

	private final BreakpointModel bpModel = new BreakpointModel();
	private final ButtonToolbar toolbar = new ButtonToolbar();
	private final DisassemblyPanel disassembly = new DisassemblyPanel();
	private final HexDumpPanel hexPanel = new HexDumpPanel();
	private final CPUStatusPanel cpuPanel = new CPUStatusPanel();
	private final BreakpointsWindow breakpointsWindow = new BreakpointsWindow();
	private final ScreenPanel screenPanel = new ScreenPanel();
	private final BlockAllocationPanel bamPanel = new BlockAllocationPanel();
	private final CalculatorPanel calculatorPanel = new CalculatorPanel();
	private final SpriteViewer spriteView = new SpriteViewer(emulator);

	private final AsmPanel asmPanel = new AsmPanel(desktop) {

		@Override
		protected void binaryUploadedToEmulator()
		{
			if ( SwingUtilities.isEventDispatchThread() ) {
				updateWindows(false);
			} else {
				SwingUtilities.invokeLater( () -> updateWindows(false) );
			}
		}
	};

	private BusPanel busPanel;

	private final List<IDebuggerView> panels = new ArrayList<>();

	private IDebuggerView loc;

	public static void main(String[] args)
	{
		SwingUtilities.invokeLater( () -> new Debugger().run() );
	}

	public void run() {

		emulator.reset();

		driver.addBreakpointListener( disassembly );
		driver.addBreakpointListener( bpModel );
		driver.start();

		final JInternalFrame toolbarFrame = wrap( "Buttons" , toolbar );
		desktop.add( toolbarFrame );

		final JInternalFrame disassemblyFrame = wrap( "Disassembly" , disassembly );
		desktop.add( disassemblyFrame  );

		final JInternalFrame cpuStatusFrame = wrap( "CPU" , cpuPanel );
		desktop.add( cpuStatusFrame  );

		final JInternalFrame hexPanelFrame = wrap( "Memory" , hexPanel );
		desktop.add( hexPanelFrame  );

		final JInternalFrame breakpointsFrame = wrap( "Breakpoints" , breakpointsWindow );
		desktop.add( breakpointsFrame  );

		final JInternalFrame screenPanelFrame = wrap( "Screen" , screenPanel );
		desktop.add( screenPanelFrame  );

		final JInternalFrame calculatorPanelFrame = wrap( "Calculator" , calculatorPanel );
		desktop.add( calculatorPanelFrame  );

		final JInternalFrame bamPanelFrame = wrap( "BAM" , bamPanel );
		desktop.add( bamPanelFrame  );

		final JInternalFrame spriteViewerFrame = wrap( "Sprite view" , spriteView );
		desktop.add( spriteViewerFrame  );

		final JInternalFrame asmPanelFrame = wrap( AsmPanel.PANEL_TITLE , asmPanel );
		asmPanel.setEmulator( emulator );
		desktop.add( asmPanelFrame  );

		busPanel = new BusPanel("IEC") {

			@Override
			protected List<StateSnapshot> getBusSnapshots()
			{
				synchronized( emulator ) {
					return emulator.getBus().getSnapshots();
				}
			}
		};
		final JInternalFrame busPanelFrame = wrap( "IEC" , busPanel );
		desktop.add( busPanelFrame  );

		final JFrame frame = new JFrame("");

		// register fake IDebuggerView to also track size and location
		// of top-level frame
		loc = new IDebuggerView()
		{
			private boolean isDisplayed;

			private final Map<String,String> configProperties = new HashMap<>();

			@Override
			public Map<String, String> getConfigProperties()
			{
				return configProperties;
			}

			@Override
			public void setConfigProperties(Map<String, String> properties)
			{
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
			public void refresh(Emulator emulator) {
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
		views.add( loc );
		locationHelper.applyLocation( loc );

		// add window listener that saves application state before shutting down
		frame.addWindowListener( new WindowAdapter()
		{
			@Override
			public void windowClosing(WindowEvent e) {

				try {
					onApplicationShutdown();
				} catch(Exception e2) {
					e2.printStackTrace();
				} finally {
					System.exit(0);
				}
			}
		});

		// add menu
		frame.setJMenuBar( createMenu() );

		frame.pack();
		frame.setContentPane( desktop );
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		frame.setVisible(true);

		updateWindows(false);

		final Optional<D64File> current = doWithFloppyAndReturn( floppy -> floppy.getDisk() );
		bamPanel.setDisk( current.orElse( null ) );
	}

	private JMenuBar createMenu()
	{
		final JMenuBar menuBar = new JMenuBar();

		final JMenu menu = new JMenu("File");
		menuBar.add( menu );

		JMenuItem item = new JMenuItem("Insert disk...");
		item.addActionListener( event -> insertDisk() );
		menu.add( item );

		item = new JMenuItem("Eject disk...");
		item.addActionListener( event -> ejectDisk() );
		menu.add( item );

		// add 'Views' menu
		final JMenu views = new JMenu("Views");
		menuBar.add( views );

		panels.sort( (a,b) ->
		{
			final String title1 = ((JInternalFrame) a.getLocationPeer()).getTitle();
			final String title2 = ((JInternalFrame) b.getLocationPeer()).getTitle();
			return title1.compareTo( title2 );
		});
		panels.forEach( loc ->
		{
			final JInternalFrame peer = (JInternalFrame) loc.getLocationPeer();
			final JCheckBoxMenuItem viewItem = new JCheckBoxMenuItem( peer.getTitle() , loc.isDisplayed() );
			viewItem.addActionListener( ev ->
			{
				loc.setDisplayed( viewItem.isSelected() );
				peer.setVisible( viewItem.isSelected() );
				peer.toFront();
			});
			views.add(viewItem );
		});

		// create I/O menu
		final JMenu io = new JMenu("I/O");
		menuBar.add( io );

		final JCheckBoxMenuItem joyPort1 = new JCheckBoxMenuItem( "Joystick port #1" , keyboardListener.getJoystickPort() == JoystickPort.PORT_1);
		final JCheckBoxMenuItem joyPort2 = new JCheckBoxMenuItem( "Joystick port #2" , keyboardListener.getJoystickPort() == JoystickPort.PORT_2);

		io.add( joyPort1 );
		io.add( joyPort2 );

		joyPort1.addActionListener( ev ->
		{
			if ( joyPort1.isSelected() )
			{
				joyPort2.setSelected( false );
				keyboardListener.setJoystickPort( KeyboardInputListener.JoystickPort.PORT_1 );
			}
		});
		joyPort2.addActionListener( ev ->
		{
			if ( joyPort2.isSelected() )
			{
				joyPort1.setSelected( false );
				keyboardListener.setJoystickPort( KeyboardInputListener.JoystickPort.PORT_2 );
			}
		});
		return menuBar;
	}

	private void ejectDisk()
	{
		try
		{
			doWithFloppy( floppy -> floppy.ejectDisk() );
		} catch(Exception e) {
			showError("Failed to eject disk",e);
		}
	}

	private void doWithFloppy(Consumer<Floppy> consumer)
	{
		synchronized(emulator)
		{
			emulator.getMemory().ioArea.iecBus.getDevices()
			.stream().filter( dev -> dev instanceof Floppy).map( dev -> (Floppy) dev).findFirst().ifPresent( consumer );
		}
	}

	private <T> Optional<T> doWithFloppyAndReturn(Function<Floppy,T> consumer)
	{
		synchronized(emulator)
		{
			Optional<Floppy> floppy = emulator.getMemory().ioArea.iecBus.getDevices()
					.stream().filter( dev -> dev instanceof Floppy).map( dev -> (Floppy) dev).findFirst();
			if ( floppy.isPresent() )
			{
				return Optional.ofNullable( consumer.apply( floppy.get() ) );
			}
			return Optional.empty();
		}
	}

	private void insertDisk()
	{
		final String lastFile = loc.getConfigProperties().get("last_d64_file");
		final JFileChooser chooser;
		if (StringUtils.isNotBlank( lastFile ))
		{
			chooser = new JFileChooser(new File( lastFile ) );
		} else {
			chooser = new JFileChooser( new File("/home/tobi/mars_workspace/j6502/src/main/resources/disks"));
		}
		if ( chooser.showOpenDialog( null ) != JFileChooser.APPROVE_OPTION )
		{
			return;
		}
		final File file = chooser.getSelectedFile();
		loc.getConfigProperties().put( "last_d64_file" , file.getAbsolutePath() );

		try
		{
			doWithFloppy( floppy ->
			{
				D64File d64File;
				try {
					d64File = new D64File( file );
				} catch(RuntimeException e) {
					throw e;
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
				floppy.insertDisk( d64File );
				bamPanel.setDisk( d64File );
			});
		} catch (Exception e) {
			showError("Failed to load disk file "+file.getAbsolutePath(),e);
		}
	}

	private void showError(String message,Throwable t)
	{
		final String[] msg = { message };
		if ( t != null ) {
			msg[0] += "\n";
			final ByteArrayOutputStream out = new ByteArrayOutputStream();
			final PrintWriter writer = new PrintWriter( out );
			t.printStackTrace( writer);
			writer.close();
			try {
				msg[0] += out.toString("UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		invokeAndWait( () -> JOptionPane.showConfirmDialog(null, msg[0] , "Error" , JOptionPane.ERROR_MESSAGE ) );
	}

	private void invokeAndWait(Runnable r)
	{
		try
		{
			if ( SwingUtilities.isEventDispatchThread() ) {
				r.run();
			} else {
				SwingUtilities.invokeAndWait( r );
			}
		} catch(Exception e) {
			e.printStackTrace();
		}
	}

	private void onApplicationShutdown()
	{
		try {
			views.forEach( locationHelper::updateConfiguration);
			locationHelper.saveAll();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	private JInternalFrame wrap(String title,JPanel panel)
	{
		setup( panel );

		final JInternalFrame frame = new JInternalFrame( title );

		if ( panel instanceof IDebuggerView)
		{
			final IDebuggerView view = (IDebuggerView) panel;
			panels.add( view );
			view.setLocationPeer( frame );
			views.add( view );
			locationHelper.applyLocation( view );
		}

		frame.setResizable( true );
		frame.getContentPane().add( panel );
		frame.pack();
		if ( panel instanceof IDebuggerView)
		{
			frame.setVisible( ((IDebuggerView) panel).isDisplayed() );
		} else {
			frame.setVisible( true );
		}

		return frame;
	}

	protected final class ButtonToolbar extends JPanel implements WindowLocationHelper.IDebuggerView
	{
		public final JButton singleStepButton = new JButton("Step");
		public final JButton runButton = new JButton("Run");
		public final JButton stopButton = new JButton("Stop");
		public final JButton resetButton = new JButton("Reset");
		public final JButton stepOverButton = new JButton("Step over");
		public final JButton loadButton = new JButton("Load");
		public final JButton refreshUIButton = new JButton("Refresh UI");

		private Component peer;
		private boolean isDisplayed;

		@Override
		public void setLocationPeer(Component frame) {
			this.peer = frame;
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
		public void refresh(Emulator emulator) {
			updateButtons();
			repaint();
		}

		private void prepareTest()
		{
			driver.setMode( Mode.SINGLE_STEP );

			final String source = loadTestProgram();

			final AST ast;
			try {
				ast = new Parser( new Lexer( new Scanner( source ) ) ).parse();
			}
			catch(Exception e)
			{
				DisassemblerTest.maybePrintError( source , e );
				throw e;
			}

			final Assembler a = new Assembler();
			final byte[] binary = a.assemble( ast );
			final int origin = a.getOrigin();

			final IMemoryProvider provider = new IMemoryProvider() {

				@Override
				public void loadInto(IMemoryRegion region) {
					region.bulkWrite( origin , binary , 0 , binary.length );
				}
			};

			synchronized(emulator)
			{
				emulator.reset();
				emulator.setMemoryProvider( provider );
				emulator.getCPU().pc( origin );
			}
			driver.removeAllBreakpoints();
			driver.addBreakpoint( new Breakpoint( (short) 0x45bf , false ) );
			driver.addBreakpoint( new Breakpoint( (short) 0x40cb , false ) );
			hexPanel.setAddress( (short) 0x210 );
			updateWindows(false);
		}

		@Override
		public boolean isRefreshAfterTick() {
			return true;
		}

		private String loadTestProgram()
		{
			InputStream in = EmulatorTest.class.getResourceAsStream( "/test.asm" );
			if ( in == null ) {
				throw new RuntimeException("Failed to load /test.asm from classpath");
			}
			final StringBuilder buffer = new StringBuilder();
			try {
				IOUtils.readLines( in ).forEach( line -> buffer.append( line ).append("\n") );
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			final String source = buffer.toString();
			return source;
		}

		public ButtonToolbar()
		{
			setPreferredSize( new Dimension( 400,50 ) );

			loadButton.addActionListener( event ->
			{
				prepareTest();
			});

			singleStepButton.addActionListener( event ->
			{
				try
				{
					driver.singleStep();
				}
				catch(final Throwable t)
				{
					t.printStackTrace();
				} finally {
					updateWindows(false);
				}
			});

			resetButton.addActionListener( ev ->
			{
				driver.setMode(Mode.SINGLE_STEP);
				synchronized(emulator) {
					emulator.reset();
				}
				updateWindows(false);
			});

			runButton.addActionListener( ev -> driver.setMode(Mode.CONTINOUS) );
			stopButton.addActionListener( event -> driver.setMode(Mode.SINGLE_STEP) );

			stepOverButton.addActionListener( ev -> driver.stepReturn() );
			refreshUIButton.addActionListener( ev ->
			{
				updateWindows( false );
			});

			setLayout( new FlowLayout() );
			add( stopButton );
			add( runButton );
			add( singleStepButton );
			add( stepOverButton );
			add( resetButton );
			add( loadButton );
			add( refreshUIButton );

			updateButtons();
		}

		public void updateButtons()
		{
			final Mode currentMode = driver.getMode();
			singleStepButton.setEnabled( currentMode == Mode.SINGLE_STEP );
			stopButton.setEnabled( currentMode != Mode.SINGLE_STEP );
			runButton.setEnabled( currentMode == Mode.SINGLE_STEP);
			resetButton.setEnabled( currentMode == Mode.SINGLE_STEP);
			stepOverButton.setEnabled( driver.canStepOver() );
		}
	}

	protected void updateWindows(boolean isTick)
	{
		synchronized ( emulator )
		{
			if ( isTick )
			{
				for ( int i = 0, len = views.size() ; i < len ; i++ )
				{
					final IDebuggerView view = views.get(i);
					if ( view.isDisplayed() && view.isRefreshAfterTick() ) {
						view.refresh( emulator );
					}
				}
			}
			else
			{
				for ( int i = 0, len = views.size() ; i < len ; i++ )
				{
					final IDebuggerView view = views.get(i);
					if ( view.isDisplayed()  ) {
						view.refresh( emulator );
					}
				}
			}
		}
		if ( ! isTick )
		{
			SwingUtilities.invokeLater( () ->
			{
				for ( int i = 0, len = views.size() ; i < len ; i++ )
				{
					final IDebuggerView view = views.get(i);
					if ( view.isDisplayed() && view instanceof Component)
					{
						((Component) view).repaint();
					}
				}
			});
		}
	}

	protected final class CalculatorPanel extends JPanel implements IDebuggerView {

		private Component peer;
		private boolean isDisplayed;

		private JTextField input = new JTextField();
		private JTextField hexOutput = new JTextField();
		private JTextField decOutput = new JTextField();
		private JTextField binOutput = new JTextField();

		public CalculatorPanel()
		{
			setLayout( new FlowLayout() );

			input.addActionListener( ev ->
			{
				String text = input.getText();
				Integer inputValue = null;
				if ( StringUtils.isNotBlank( text ) )
				{
					text = text.trim();
					try {
						if ( text.startsWith("$" ) ) {
							inputValue = Integer.parseInt( text.substring(1) , 16 );
						} else if ( text.startsWith("0x" ) ) {
							inputValue = Integer.parseInt( text.substring(2) , 16 );
						} else if ( text.startsWith("0b" ) ) {
							inputValue = Integer.parseInt( text.substring(2) , 2 );
						} else if ( text.startsWith("%" ) ) {
							inputValue = Integer.parseInt( text.substring(1) , 2 );
						} else {
							inputValue = Integer.parseInt( text );
						}
					}
					catch(Exception e)
					{
					}
				}
				if ( inputValue != null )
				{
					hexOutput.setText( Integer.toHexString( inputValue ) );
					decOutput.setText( Integer.toString( inputValue ) );
					binOutput.setText( Integer.toBinaryString( inputValue ) );
				} else {
					hexOutput.setText("No/invalid input");
					decOutput.setText("No/invalid input");
					binOutput.setText("No/invalid input");
				}
			});

			input.setColumns( 16 );
			hexOutput.setColumns( 16 );
			decOutput.setColumns( 16 );
			binOutput.setColumns( 16 );

			setup(input,"To convert");
			setup(hexOutput,"Hexadecimal");
			setup(decOutput,"Decimal");
			setup( binOutput , "Binary" );

			add( input );
			add( hexOutput);
			add( decOutput);
			add( binOutput);
			hexOutput.setEditable( false );
			decOutput.setEditable( false );
			binOutput.setEditable( false );

			setPreferredSize( new Dimension(200,100 ) );
		}

		@Override
		public void refresh(Emulator emulator) {
		}

		@Override
		public boolean isRefreshAfterTick() {
			return false;
		}

		private void setup(JComponent c,String label)
		{
			TitledBorder border = BorderFactory.createTitledBorder( label );
			border.setTitleColor( Color.WHITE );
			c.setBorder( border );
			c.setBackground( Color.BLACK );
			c.setForeground( Color.GREEN );
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
		private volatile boolean isDisplayed;

		public ScreenPanel()
		{
			setFocusable(true);
			setRequestFocusEnabled(true);
			keyboardListener.attach( this );

			// 16 ms = 60hz screen refresh
			final Timer timer = new Timer(16, ev -> repaint() );
			timer.start();
		}

		@Override
		public boolean isRefreshAfterTick() {
			return false;
		}

		@Override
		public void refresh(Emulator emulator) {
			repaint();
		}

		@Override
		public void setLocationPeer(Component frame) {
			this.frame = frame;
		}

		@Override
		public Component getLocationPeer() {
			return this.frame;
		}

		@Override
		public void setDisplayed(boolean yesNo) {
			this.isDisplayed = yesNo;
		}

		@Override
		public boolean isDisplayed() {
			return isDisplayed;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			// no need to synchronized here since all
			// elements of the call-chain (emulator , result of emulator.getVic(), render() method)
			// either only access final variables OR come with their own synchronization (render() method)
			emulator.getVIC().render( (Graphics2D) g , getWidth() , getHeight() );
		}
	}

	// CPU status
	protected final class CPUStatusPanel extends BufferedView implements WindowLocationHelper.IDebuggerView
	{
		private Component peer;
		private boolean isDisplayed;

		// @GuardedBy( lines )
		private final List<String> lines = new ArrayList<>();

		@Override
		public void setLocationPeer(Component frame) {
			this.peer = frame;
		}

		@Override
		protected void initGraphics(Graphics2D g) { setup( g ); }

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

		public CPUStatusPanel()
		{
			setPreferredSize( new Dimension(200,150 ) );
		}

		@Override
		public boolean isRefreshAfterTick() {
			return true;
		}

		@Override
		public void refresh(Emulator emulator)
		{
			lines.clear();

			final CPU cpu = emulator.getCPU();
			lines.add( "PC: "+HexDump.toAdr( cpu.pc() ) + "   Flags: "+ cpu.getFlagsString() );
			lines.add("Cycles: "+cpu.cycles);
			lines.add("Previous PC: "+HexDump.toAdr( cpu.previousPC ) );
			lines.add(" A: "+HexDump.byteToString( (byte) cpu.getAccumulator() ) );
			lines.add(" X: $"+HexDump.byteToString( (byte) cpu.getX()) );
			lines.add(" Y: $"+HexDump.byteToString( (byte) cpu.getY()) );
			lines.add("SP: "+HexDump.toAdr( cpu.sp ) );

			final Graphics2D g = getBackBufferGraphics();
			try
			{
				g.setColor( FG_COLOR );
				int y = 15;
				for ( final String line : lines )
				{
					g.drawString( line , 5 , y );
					y += 15;
				}
			}
			finally {
				swapBuffers();
			}

			repaint();
		}
	}

	public static void setup(JComponent c1,JComponent... other) {
		setup(c1);

		if ( other != null )
		{
			for ( JComponent c : other ) {
				setup(c);
			}
		}
	}

	public static void setup(JComponent c) {
		setMonoSpacedFont( c );
		setColors( c );
	}

	public static void setMonoSpacedFont(JComponent c) {
		c.setFont( MONO_FONT );
	}

	public static void setup(Graphics2D g) {
		g.setFont( MONO_FONT );
		g.setColor( FG_COLOR );
		g.setBackground( BG_COLOR );
	}

	public static void setColors(JComponent c) {
		c.setBackground( BG_COLOR );
		c.setForeground( FG_COLOR );
	}

	protected static final class LineWithBounds
	{
		public final Disassembler.Line line;
		public final Rectangle bounds;

		public LineWithBounds(Disassembler.Line line, Rectangle bounds) {
			this.line = line;
			this.bounds = bounds;
		}

		public boolean isClicked(int x,int y)
		{
			return y >= bounds.y && y <= bounds.y+bounds.height;
		}
	}

	protected final class HexDumpPanel extends BufferedView implements WindowLocationHelper.IDebuggerView
	{
		private final int bytesToDisplay = 25*40;

		private boolean isDisplayed;
		private Component peer;

		private short startAddress = 0;

		private final HexDump hexdump;

		public HexDumpPanel() {
			this.hexdump = new HexDump();
			this.hexdump.setBytesPerLine( 16 );
			this.hexdump.setPrintAddress(true);

			setFocusable(true);
			setRequestFocusEnabled( true );

			addKeyListener( new KeyAdapter()
			{
				@Override
				public void keyReleased(java.awt.event.KeyEvent e)
				{
					if ( e.getKeyCode() == KeyEvent.VK_PAGE_DOWN ) {
						nextPage();
					} else if ( e.getKeyCode() == KeyEvent.VK_PAGE_UP ) {
						previousPage();
					} else if ( e.getKeyCode() == KeyEvent.VK_G ) {
						Short adr = queryAddress();
						if ( adr != null )
						{
							setAddress( adr );
						}
					}
				}
			});
		}

		@Override
		protected void initGraphics(Graphics2D g) { setup( g ); }

		@Override
		public boolean isRefreshAfterTick() {
			return true;
		}

		@Override
		public void refresh(Emulator emulator)
		{
			final Graphics2D g = getBackBufferGraphics();
			try {
				synchronized( emulator )
				{
					final String[] lines = hexdump.dump( startAddress , emulator.getMemory() , startAddress , bytesToDisplay).split("\n");

					int y = 15;

					g.setColor( Color.GREEN );
					for ( String line : lines ) {
						g.drawString( line , 5 , y );
						y += LINE_HEIGHT;
					}
				}
			}
			finally {
				swapBuffers();
			}

			repaint();
		}

		public void setAddress(short adr) {
			this.startAddress = adr;
			refresh( emulator );
		}

		public void nextPage() {
			startAddress = (short) ( (startAddress + bytesToDisplay) & 0xffff);
			refresh( emulator );
		}

		public void previousPage() {
			startAddress = (short) ( (startAddress- bytesToDisplay) & 0xffff);
			refresh( emulator );
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

	protected final class DisassemblyPanel extends BufferedView implements WindowLocationHelper.IDebuggerView , IBreakpointLister
	{
		private final Disassembler dis = new Disassembler().setAnnotate(true);

		protected static final int X_OFFSET = 30;
		protected static final int Y_OFFSET = LINE_HEIGHT;


		private final int bytesToDisassemble = 48;
		private short currentAddress;
		private Short addressToMark = null;

		private final List<LineWithBounds> lines = new ArrayList<>();

		private boolean isDisplayed;
		private Component peer;

		@Override
		protected void initGraphics(Graphics2D g) { setup( g ); }

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

		@Override
		public boolean isRefreshAfterTick() {
			return true;
		}

		@Override
		public void refresh(Emulator emulator)
		{
			synchronized( emulator )
			{
				doRefresh( emulator );
			}
			repaint();
		}

		private void doRefresh(Emulator emulator)
		{
			final Graphics2D g = getBackBufferGraphics();
			try
			{
				final int pc = emulator.getCPU().pc();

				internalSetAddress( (short) pc , (short) pc );

				maybeDisassemble( g );

				for ( int i = 0, y = LINE_HEIGHT ; i < lines.size() ; i++ , y+= LINE_HEIGHT )
				{
					final LineWithBounds line = lines.get(i);

					g.setColor(FG_COLOR);
					g.drawString( line.line.toString() , X_OFFSET , y );
					if ( addressToMark != null && line.line.address == addressToMark )
					{
						g.setColor(Color.RED);
						g.drawRect( line.bounds.x , line.bounds.y , line.bounds.width , line.bounds.height );
					}

					final int lineHeight = line.bounds.height;
					final int circleX = 5;
					final int circleY = line.bounds.y;

					if ( driver.getBreakpoint( line.line.address ) != null ) {
						g.fillArc( circleX , circleY , lineHeight , lineHeight , 0 , 360 );
					} else {
						g.drawArc( circleX , circleY , lineHeight , lineHeight , 0 , 360 );
					}
				}
			} finally {
				swapBuffers();
			}
		}

		public DisassemblyPanel()
		{
			setFocusable( true );
			setRequestFocusEnabled(true);
			requestFocus();

			setPreferredSize( new Dimension(640,480 ) );

			addKeyListener( new KeyAdapter()
			{
				@Override
				public void keyReleased(java.awt.event.KeyEvent e)
				{
					if ( e.getKeyCode() == KeyEvent.VK_DOWN ) {
						lineDown();
					} else if ( e.getKeyCode() == KeyEvent.VK_UP) {
						lineUp();
					} else if ( e.getKeyCode() == KeyEvent.VK_PAGE_DOWN ) {
						pageDown();
					} else if ( e.getKeyCode() == KeyEvent.VK_PAGE_UP ) {
						pageUp();
					} else if ( e.getKeyCode() == KeyEvent.VK_G ) {
						Short adr = queryAddress();
						if ( adr != null )
						{
							setAddress( adr , null );
						}
					}
				}
			});

			addMouseListener( new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if ( e.getButton() == MouseEvent.BUTTON3) {
						final Short adr = getAddressForPoint( e.getX(), e.getY() );
						if ( adr != null )
						{
							final Breakpoint breakpoint = driver.getBreakpoint( adr );
							if ( breakpoint != null ) {
								driver.removeBreakpoint( breakpoint );
							} else {
								driver.addBreakpoint( new Breakpoint( adr , false ) );
							}
						}
					}
				}
			});
		}

		public void pageUp() {
			this.currentAddress = (short) ( this.currentAddress - bytesToDisassemble/2 );
			lines.clear();
			refresh(emulator);
		}

		public void lineUp() {
			this.currentAddress = (short) ( ( this.currentAddress -1 ));
			this.addressToMark = null;
			lines.clear();
			refresh(emulator);
		}

		public void lineDown() {
			this.currentAddress = (short) (  this.currentAddress + 1 );
			this.addressToMark = null;
			lines.clear();
			refresh(emulator);
		}

		public void pageDown() {
			this.currentAddress = (short) ( this.currentAddress + bytesToDisassemble/2 );
			lines.clear();
			refresh(emulator);
		}

		public void setAddress(short adr,Short addressToMark)
		{
			internalSetAddress(adr, addressToMark);
			refresh(emulator);
		}

		private void internalSetAddress(short adr,Short addressToMark)
		{
			this.addressToMark = addressToMark;
			this.currentAddress = adr;
			lines.clear();
		}

		private void disassemble(Graphics g)
		{
			lines.clear();

			/* Because depending on the opcode, each disassembly line may consume up to three bytes, we might not arrive
			 * at the exact location where the PC currently is .. we'll use this flag to re-try disassembling with a
			 * different offset until we succeed
			 */
			boolean alignmentCorrect =false;

			final short pc;
			synchronized( emulator )
			{
				pc = currentAddress;
				short offset = (short) (pc - bytesToDisassemble/2);
				while ( offset != pc && ! alignmentCorrect )
				{
					alignmentCorrect = false;
					lines.clear();
					final Consumer<Line> lineConsumer = new Consumer<Line>()
					{
						private int y = Y_OFFSET;

						@Override
						public void accept(Line line) {
							final LineMetrics lineMetrics = g.getFontMetrics().getLineMetrics( line.toString(),  g );
							final Rectangle2D stringBounds = g.getFontMetrics().getStringBounds( line.toString(),  g );

							final Rectangle bounds = new Rectangle( X_OFFSET , (int) (y - lineMetrics.getAscent() ) , (int) stringBounds.getWidth() , (int) (lineMetrics.getHeight() ) );
							lines.add( new LineWithBounds( line , bounds ) );
							y += LINE_HEIGHT;
						}
					};
					dis.disassemble( emulator.getMemory() , offset, bytesToDisassemble , lineConsumer);
					alignmentCorrect = lines.stream().anyMatch( line -> line.line.address == pc );
					offset++;
				}
			}
		}

		private void maybeDisassemble(Graphics g)
		{
			if ( lines.isEmpty() )
			{
				disassemble( g );
			}
			else if ( addressToMark != null && lines.stream().noneMatch( line -> line.line.address == addressToMark.shortValue() ) ) {
				disassemble( g );
			}
		}

		public Short getAddressForPoint(int x,int y)
		{
			for ( LineWithBounds l : lines ) {
				if ( l.isClicked(x, y ) ) {
					return l.line.address;
				}
			}
			return null;
		}

		@Override
		public void breakpointAdded(Breakpoint bp) {
			SwingUtilities.invokeLater( () -> repaint() );
		}

		@Override
		public void breakpointRemoved(Breakpoint bp) {
			SwingUtilities.invokeLater( () -> repaint() );
		}

		@Override
		public void breakpointReplaced(Breakpoint old, Breakpoint newBp) {
			SwingUtilities.invokeLater( () -> repaint() );
		}
	}

	private Short queryAddress()
	{
		final String s = JOptionPane.showInputDialog(null, "Enter hexadecimal address (0-ffff)", "Enter address" , JOptionPane.PLAIN_MESSAGE );

		if ( s == null ) {
			return null;
		}
		return (short) Integer.valueOf( s ,  16 ).intValue();
	}

	public final class BreakpointsWindow extends JPanel implements IDebuggerView {

		private boolean isDisplayed;
		private Component peer;

		public BreakpointsWindow()
		{
			setColors(this);

			final JTable breakpointTable = new JTable( bpModel );
			setColors( breakpointTable );
			breakpointTable.setFillsViewportHeight( true );

			breakpointTable.getTableHeader().setForeground( FG_COLOR );
			breakpointTable.getTableHeader().setBackground( BG_COLOR );

			setup( breakpointTable );
			final JScrollPane scrollPane = new JScrollPane( breakpointTable );
			setup( scrollPane );

			breakpointTable.setMinimumSize( new Dimension(50,50 ) );
			setLayout( new BorderLayout() );
			add( scrollPane , BorderLayout.CENTER );

			breakpointTable.addKeyListener( new KeyAdapter()
			{
				@Override
				public void keyReleased(KeyEvent event)
				{
					if ( event.getKeyCode() == KeyEvent.VK_DELETE )
					{
						final int[] selectedRows = breakpointTable.getSelectedRows();
						if ( selectedRows != null )
						{
							List<Breakpoint> toRemove = new ArrayList<>();
							for ( int idx : selectedRows ) {
								toRemove.add( bpModel.getRow(idx) );
							}
							toRemove.forEach( driver::removeBreakpoint );
						}
					}
				}
			});
			breakpointTable.addMouseListener( new MouseAdapter()
			{
				@Override
				public void mouseClicked(MouseEvent e)
				{
					if ( e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 ) {
						int row = breakpointTable.rowAtPoint( e.getPoint() );
						if ( row != -1 )
						{
							final Breakpoint breakpoint = bpModel.getRow( row );
							disassembly.setAddress( (short) breakpoint.address , null );
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
		public void refresh(Emulator emulator) {
			repaint();
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

	protected final class BreakpointModel extends AbstractTableModel implements IBreakpointLister
	{
		private volatile boolean breakpointsChanged = true;
		private List<Breakpoint> cachedBreakpoints;

		private List<Breakpoint> getBreakpoints()
		{
			if ( breakpointsChanged || cachedBreakpoints == null )
			{
				synchronized( emulator ) {
					cachedBreakpoints = driver.getBreakpoints();
					cachedBreakpoints.sort( (bp1,bp2 ) -> Integer.compare(bp1.address, bp2.address ));
					breakpointsChanged = false;
				}
			}
			return new ArrayList<>( cachedBreakpoints );
		}

		public Breakpoint getRow(int idx) {
			return getBreakpoints().get(idx);
		}

		@Override
		public String getColumnName(int column) {
			switch(column) {
				case 0: return "Address";
				case 1: return "Required CPU flags";
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
			return 2;
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex)
		{
			return columnIndex == 1;
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex)
		{
			if ( !(aValue instanceof String) ) {
				throw new IllegalArgumentException("No valid processor flags: "+aValue);
			}
			final String flagString = ((String) aValue).trim();
			final Set<CPU.Flag> flags = new HashSet<>();
			for ( char c : flagString.toCharArray() )
			{
				Optional<Flag> flagToSet = Arrays.stream( CPU.Flag.values() ).filter( flag -> flag.symbol == c ).findFirst();
				if ( ! flagToSet.isPresent() ) {
					throw new IllegalArgumentException("No valid processor flags: '"+c+"'");
				}
				flags.add( flagToSet.get() );
			}
			final Breakpoint currentBP = getBreakpoints().get(rowIndex);
			final Breakpoint newBP = new Breakpoint( currentBP.address , false , CPU.Flag.toBitMask( flags ) );
			driver.addBreakpoint( newBP );
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex)
		{
			Breakpoint bp = getBreakpoints().get(rowIndex);
			switch( columnIndex ) {
				case 0:
					return HexDump.toAdr( bp.address );
				case 1:
					if ( bp.checkCPUFlags ) {
						return CPU.Flag.toFlagString( bp.cpuFlagsMask );
					}
					return "<unconditional breakpoint>";
				default:
					throw new RuntimeException("Unreachable code reached");
			}
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
}