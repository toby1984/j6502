package de.codesourcery.j6502.ui;

import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.apache.commons.io.IOUtils;

import de.codesourcery.j6502.assembler.Assembler;
import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Parser;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.disassembler.Disassembler.Line;
import de.codesourcery.j6502.disassembler.DisassemblerTest;
import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.CPU.Flag;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.EmulatorTest;
import de.codesourcery.j6502.emulator.IECBus.StateSnapshot;
import de.codesourcery.j6502.emulator.IMemoryProvider;
import de.codesourcery.j6502.emulator.IMemoryRegion;
import de.codesourcery.j6502.emulator.Keyboard;
import de.codesourcery.j6502.emulator.Keyboard.Key;
import de.codesourcery.j6502.emulator.Keyboard.KeyLocation;
import de.codesourcery.j6502.emulator.Keyboard.Modifier;
import de.codesourcery.j6502.ui.EmulatorDriver.IBreakpointLister;
import de.codesourcery.j6502.ui.EmulatorDriver.Mode;
import de.codesourcery.j6502.ui.WindowLocationHelper.ILocationAware;
import de.codesourcery.j6502.utils.HexDump;

public class Debugger
{
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
		protected void onStopHook(Throwable t) {
			SwingUtilities.invokeLater( () -> updateWindows() );
		}

		@Override
		protected void onStartHook() {
			SwingUtilities.invokeLater( () -> updateWindows() );
		}

		@Override
		protected void tick()
		{
			final long now = System.currentTimeMillis();
			long age = now - lastTick;
			if ( age > 250 ) // do not post more than 60 events / second to not overload the Swing Event handling queue
			{
				lastTick = now;
				SwingUtilities.invokeLater( () -> updateWindows() );
			}
		}
	};

	private final List<ILocationAware> locationAware = new ArrayList<>();

	private final BreakpointModel bpModel = new BreakpointModel();
	private final ButtonToolbar toolbar = new ButtonToolbar();
	private final DisassemblyPanel disassembly = new DisassemblyPanel();
	private final HexDumpPanel hexPanel = new HexDumpPanel();
	private final CPUStatusPanel cpuPanel = new CPUStatusPanel();
	private final BreakpointsWindow breakpointsWindow = new BreakpointsWindow();
	private final ScreenPanel screenPanel = new ScreenPanel();
	private final BusPanel busPanel = new BusPanel();

	public static void main(String[] args)
	{
		new Debugger().run();
	}

	public void run() {

		emulator.reset();

		driver.addBreakpointListener( disassembly );
		driver.addBreakpointListener( bpModel );
		driver.start();

		final JDesktopPane desktop = new JDesktopPane();

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

		final JInternalFrame busPanelFrame = wrap( "IEC" , busPanel );
		desktop.add( busPanelFrame  );

		final JFrame frame = new JFrame("");
		frame.addWindowListener( new WindowAdapter() {
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

		final ILocationAware loc = new ILocationAware() {

			@Override
			public void setLocationPeer(Component frame) {
				throw new UnsupportedOperationException("setLocationPeer not implemented yet");
			}

			@Override
			public Component getLocationPeer() {
				return frame;
			}
		};
		locationAware.add( loc );
		locationHelper.applyLocation( loc );

		frame.pack();
		frame.setContentPane( desktop );
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		frame.setVisible(true);

		updateWindows();
	}

	private void onApplicationShutdown()
	{
		try {
			locationAware.forEach( locationHelper::saveLocation);
			locationHelper.saveAll();
		} catch (IOException e1) {
			e1.printStackTrace();
		}
	}

	private JInternalFrame wrap(String title,JPanel panel)
	{
		setup( panel );

		final JInternalFrame frame = new JInternalFrame( title );

		if ( panel instanceof ILocationAware)
		{
			final ILocationAware loc = (ILocationAware) panel;
			loc.setLocationPeer( frame );
			locationAware.add( (ILocationAware) panel );
			locationHelper.applyLocation( loc );
		}

		frame.setResizable( true );
		frame.getContentPane().add( panel );
		frame.pack();
		frame.setVisible( true );

		return frame;
	}

	protected final class ButtonToolbar extends JPanel implements WindowLocationHelper.ILocationAware
	{
		public final JButton singleStepButton = new JButton("Step");
		public final JButton runButton = new JButton("Run");
		public final JButton stopButton = new JButton("Stop");
		public final JButton resetButton = new JButton("Reset");
		public final JButton stepOverButton = new JButton("Step over");
		public final JButton loadButton = new JButton("Load");

		private Component peer;

		@Override
		public void setLocationPeer(Component frame) {
			this.peer = frame;
		}

		@Override
		public Component getLocationPeer() {
			return peer;
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
			updateWindows();
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
					updateWindows();
				}
			});

			resetButton.addActionListener( ev ->
			{
				driver.setMode(Mode.SINGLE_STEP);
				synchronized(emulator) {
					emulator.reset();
				}
				updateWindows();
			});

			runButton.addActionListener( ev -> driver.setMode(Mode.CONTINOUS) );
			stopButton.addActionListener( event -> driver.setMode(Mode.SINGLE_STEP) );

			stepOverButton.addActionListener( ev -> driver.stepReturn() );

			setLayout( new FlowLayout() );
			add( stopButton );
			add( runButton );
			add( singleStepButton );
			add( stepOverButton );
			add( resetButton );
			add( loadButton );

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

	protected void updateWindows()
	{
		toolbar.repaint();
		toolbar.updateButtons();
		int pc;
		synchronized( emulator ) {
			pc = emulator.getCPU().pc();
		}
		disassembly.setAddress( (short) pc , (short) pc );
		screenPanel.repaint();
		cpuPanel.repaint();
		hexPanel.repaint();
		busPanel.repaint();
	}

	protected final class ScreenPanel extends JPanel implements WindowLocationHelper.ILocationAware {

		private Component frame;

		public ScreenPanel()
		{
			setFocusable(true);
			setRequestFocusEnabled(true);
			addKeyListener( new KeyAdapter()
			{
				@Override
				public void keyPressed(java.awt.event.KeyEvent e)
				{
					KeyLocation location = getLocation(e);
					Set<Modifier> modifiers = getModifiers( e );
					Key pressed = Keyboard.keyCodeToKey( e.getKeyCode() , location , modifiers);
					if ( pressed != null ) {
						emulator.keyPressed( pressed );
					}
				}

				private Set<Keyboard.Modifier> getModifiers(KeyEvent e)
				{
					int mask = e.getModifiersEx();
					boolean shiftPressed = false;
					boolean controlPressed = false;
					if ( ( (mask & KeyEvent.SHIFT_DOWN_MASK) != 0 ) ||
						 ( ( mask & KeyEvent.SHIFT_MASK ) != 0 )
					)
					{
						shiftPressed = true;
					}

					if ( ( (mask & KeyEvent.CTRL_DOWN_MASK) != 0 ) ||
							 ( ( mask & KeyEvent.CTRL_MASK ) != 0 )
						)
						{
							controlPressed = true;
						}
					if ( ! controlPressed && ! shiftPressed ) {
						return Collections.emptySet();
					}
					Set<Keyboard.Modifier> result = new HashSet<>();
					if ( controlPressed ) {
						result.add( Keyboard.Modifier.CONTROL );
					}
					if ( shiftPressed ) {
						result.add( Keyboard.Modifier.SHIFT );
					}
					return result;
				}

				private KeyLocation getLocation(KeyEvent e)
				{
					final int keyLocation = e.getKeyLocation();
					if (keyLocation == KeyEvent.KEY_LOCATION_LEFT) {
						return KeyLocation.LEFT;
					}
					if (keyLocation == KeyEvent.KEY_LOCATION_RIGHT) {
						return KeyLocation.RIGHT;
					}
					return KeyLocation.STANDARD;
				}
				@Override
				public void keyReleased(java.awt.event.KeyEvent e)
				{
					Key released = Keyboard.keyCodeToKey( e.getKeyCode() , getLocation(e) , getModifiers(e) );
					if ( released != null ) {
						emulator.keyReleased( released );
					}
				}
			});
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
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);
			emulator.getVIC().render( emulator.getMemory() , (Graphics2D) g , getWidth() , getHeight() );
		}
	}

	// CPU status
	protected final class CPUStatusPanel extends JPanel implements WindowLocationHelper.ILocationAware
	{
		private Component peer;

		@Override
		public void setLocationPeer(Component frame) {
			this.peer = frame;
		}

		@Override
		public Component getLocationPeer() {
			return peer;
		}

		public CPUStatusPanel()
		{
			setPreferredSize( new Dimension(200,150 ) );
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent( g );

			final List<String> lines = new ArrayList<>();
			synchronized( emulator )
			{
				final CPU cpu = emulator.getCPU();
				lines.add( "PC: "+HexDump.toAdr( cpu.pc() ) + "   Flags: "+ cpu.getFlagsString() );
				lines.add("Cycles: "+cpu.cycles);
				lines.add("Previous PC: "+HexDump.toAdr( cpu.previousPC ) );
				lines.add(" A: "+HexDump.byteToString( (byte) cpu.getAccumulator() ) );
				lines.add(" X: $"+HexDump.byteToString( (byte) cpu.getX()) );
				lines.add(" Y: $"+HexDump.byteToString( (byte) cpu.getY()) );
				lines.add("SP: "+HexDump.toAdr( cpu.sp ) );
			}
			g.setColor( FG_COLOR );
			int y = 15;
			for ( final String line : lines )
			{
				g.drawString( line , 5 , y );
				y += 15;
			}
		}
	}

	protected static void setup(JComponent c) {
		setMonoSpacedFont( c );
		setColors( c );
	}

	protected static void setMonoSpacedFont(JComponent c) {
		c.setFont( MONO_FONT );
	}

	protected static void setColors(JComponent c) {
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

	protected final class HexDumpPanel extends JPanel implements WindowLocationHelper.ILocationAware
	{
		private final int bytesToDisplay = 25*40;

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

		public void setAddress(short adr) {
			this.startAddress = adr;
			repaint();
		}

		public void nextPage() {
			startAddress = (short) ( (startAddress + bytesToDisplay) & 0xffff);
			repaint();
		}

		public void previousPage() {
			startAddress = (short) ( (startAddress- bytesToDisplay) & 0xffff);
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);

			final String[] lines;
			synchronized( emulator )
			{
				lines = hexdump.dump( startAddress , emulator.getMemory() , startAddress , bytesToDisplay).split("\n");
			}

			int y = 15;
			g.setColor( Color.GREEN );
			for ( String line : lines ) {
				g.drawString( line , 5 , y );
				y += LINE_HEIGHT;
			}
		}

		@Override
		public void setLocationPeer(Component frame) {
			this.peer = frame;
		}

		@Override
		public Component getLocationPeer() {
			return this.peer;
		}
	}

	protected final class DisassemblyPanel extends JPanel implements WindowLocationHelper.ILocationAware , IBreakpointLister
	{
		private final Disassembler dis = new Disassembler().setAnnotate(true);

		protected static final int X_OFFSET = 30;
		protected static final int Y_OFFSET = LINE_HEIGHT;


		private final int bytesToDisassemble = 48;
		private short currentAddress;
		private Short addressToMark = null;

		private final List<LineWithBounds> lines = new ArrayList<>();

		private Component peer;

		@Override
		public void setLocationPeer(Component frame) {
			this.peer = frame;
		}

		@Override
		public Component getLocationPeer() {
			return peer;
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
			repaint();
		}

		public void lineUp() {
			this.currentAddress = (short) ( ( this.currentAddress -1 ));
			this.addressToMark = null;
			lines.clear();
			repaint();
		}

		public void lineDown() {
			this.currentAddress = (short) (  this.currentAddress + 1 );
			this.addressToMark = null;
			lines.clear();
			repaint();
		}

		public void pageDown() {
			this.currentAddress = (short) ( this.currentAddress + bytesToDisassemble/2 );
			lines.clear();
			repaint();
		}

		public void setAddress(short adr,Short addressToMark) {
			this.addressToMark = addressToMark;
			this.currentAddress = adr;
			lines.clear();
			repaint();
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
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);

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

	public final class BreakpointsWindow extends JPanel implements ILocationAware {

		private Component peer;

		public BreakpointsWindow()
		{
			final JTable breakpointTable = new JTable( bpModel );
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
		public void setLocationPeer(Component frame) {
			this.peer = frame;
		}

		@Override
		public Component getLocationPeer() {
			return peer;
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

	protected static final Stroke DASHED_FAT = new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);
	protected static final Stroke DASHED_SLIM = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);

	protected static abstract class Lane
	{
		private final String title;

		public Lane(String title) {
			this.title = title;
		}

		protected abstract boolean getState(StateSnapshot state);

		public void renderTitle(int x,int y,int laneHeight,Graphics2D g,int maxX)
		{
			LineMetrics metrics = g.getFontMetrics().getLineMetrics( title , g );
			Rectangle2D bounds = g.getFontMetrics().getStringBounds( title , g );

			/*
			 * +---------------------------
			 *
			 *        1  --------------
			 * TITLE
			 *        0  --------------
			 *
			 * +---------------------------
			 */
			int third = laneHeight / 3;

			int cy = y + laneHeight/2;
			int fontCenterY = (int) (cy - bounds.getHeight()/2 + metrics.getAscent());
			g.drawString( title , x , fontCenterY );

			int hiLevelY = y + third;
			int loLevelY = y + 2*third;

			final int offset =  10 + (int) (x+bounds.getWidth());

			metrics = g.getFontMetrics().getLineMetrics( "1" , g );
			bounds = g.getFontMetrics().getStringBounds( "1" , g );
			fontCenterY = (int) (hiLevelY - bounds.getHeight()/2 + metrics.getAscent());
			g.drawString( "1" , offset , (int) (hiLevelY+metrics.getDescent()) );

			metrics = g.getFontMetrics().getLineMetrics( "0" , g );
			bounds = g.getFontMetrics().getStringBounds( "0" , g );
			fontCenterY = (int) (loLevelY - bounds.getHeight()/2 + metrics.getAscent());
			g.drawString( "0" , offset , fontCenterY );

			final Stroke oldStroke = g.getStroke();
			g.setStroke( DASHED_SLIM );
			Color oldColor = g.getColor();
			g.setColor(Color.BLUE);
			g.drawLine( offset+15,hiLevelY , maxX , hiLevelY );
			g.drawLine( offset+15,loLevelY , maxX , loLevelY );

			g.setColor(oldColor);
			g.setStroke(oldStroke);
		}

		public void render(int x,int y,int laneHeight,double cycleWidthInPixels,StateSnapshot previousState,StateSnapshot state,Graphics2D g)
		{
			int third = laneHeight / 3;
			int hiLevelY = y + third;
			int loLevelY = y + 2*third;

			final boolean stateNow = getState( state );
			final boolean stateBefore = previousState == null ? false : getState(previousState);

			final int yNow = stateNow ? hiLevelY : loLevelY;

			final Color oldColor = g.getColor();

			g.setColor(Color.RED);
			g.drawLine(x,yNow,(int) (x+cycleWidthInPixels),yNow);

			if ( previousState != null )
			{
				if (  stateNow != stateBefore  ) {
					final int previousY = stateBefore ? hiLevelY : loLevelY;
					g.drawLine( x , previousY , x , yNow );
				} 
				final int yPrevious = stateBefore ? hiLevelY : loLevelY;
				long cycleDelta = Math.abs( state.cycle - previousState.cycle );
				g.drawLine( (int) (x - cycleDelta*cycleWidthInPixels) , yPrevious , x , yPrevious );
			}
			g.setColor( oldColor );
		}
	}
	
	protected static enum SelectionMode {
		MIN,MAX,NONE;
	}

	protected final class BusPanel extends JPanel implements ILocationAware
	{
		private final int RIGHT_BORDER = 20; // right border in pixels
		private final int RESERVED_WIDTH = 100; // reserved width left of chart in pixels

		private final List<Lane> lanes = new ArrayList<>();

		private Component peer;
		
		private DisplayRange displayRange = null;
		
		private List<StateSnapshot> states = new ArrayList<>();
		private int firstSelectionX;
		private int currentSelectionX=RESERVED_WIDTH;
		private SelectionMode selectionMode = SelectionMode.NONE;

		protected final class DisplayRange 
		{
			public long minCycle;
			public long maxCycle;
			
			public DisplayRange(long minCycle, long maxCycle) {
				if ( minCycle > maxCycle ) {
					throw new IllegalArgumentException("Invalid range: "+minCycle+" > "+maxCycle);
				}
				this.minCycle = minCycle;
				this.maxCycle = maxCycle;
			}
			
			public void zoomOut() {
				long range = (long) ((maxCycle-minCycle)*0.2d);
				if ( (minCycle + range) < getMax() ) {
					minCycle += range;
				}
			}
			
			public void zoomIn() {
				long range = (long) ( (maxCycle-minCycle)*0.2d );
				if ( (minCycle - range) > getMin() ) {
					minCycle -= range;
				} else {
					minCycle = getMin();
				}
			}
			
			private long getMin() {
				return states.stream().mapToLong( s->s.cycle ).min().orElse(0);
			}
			
			private long getMax() {
				return states.stream().mapToLong( s->s.cycle ).max().orElse(0);
			}
			
			public void rollLeft() 
			{
				long range = (long) ((maxCycle - minCycle)*0.15d);
				if ( ( minCycle - range ) < getMin() ) {
					range = minCycle-getMin();
				}
				minCycle -= range;
				maxCycle -= range;
			}
			
			public void rollRight() {
				long range = (long) ( (maxCycle-minCycle)*0.1d );
				if ( maxCycle + range > getMax() ) {
					range = getMax() - maxCycle;
				}
				minCycle += range;
				maxCycle += range;
			}		
		}
		
		public BusPanel()
		{
			lanes.add( new Lane("EOI") { @Override protected boolean getState(StateSnapshot state) { return state.eoi; } });			
			lanes.add( new Lane("ATN") { @Override protected boolean getState(StateSnapshot state) { return state.atn; } });
			lanes.add( new Lane("CLK_IN") { @Override protected boolean getState(StateSnapshot state) { return state.clkIn; } });
			lanes.add( new Lane("DATA_IN") { @Override protected boolean getState(StateSnapshot state) { return state.dataIn; } });
			lanes.add( new Lane("CLOCK_OUT") { @Override protected boolean getState(StateSnapshot state) { return state.clkOut; } });
			lanes.add( new Lane("DATA_OUT") { @Override protected boolean getState(StateSnapshot state) { return state.dataOut; } });
			setMinimumSize( new Dimension(RESERVED_WIDTH+100*3 , 6*30 ) );
			setFocusable(true);
			setRequestFocusEnabled( true );
			addKeyListener( new KeyAdapter() 
			{
				@Override
				public void keyReleased(KeyEvent e) 
				{
					if ( e.getKeyCode() == KeyEvent.VK_ESCAPE ) 
					{
						displayRange = null;
					} else if ( displayRange != null && e.getKeyCode() == KeyEvent.VK_PLUS ) {
						displayRange.zoomOut();
						repaint();						
					} else if ( displayRange != null && e.getKeyCode() == KeyEvent.VK_MINUS) {
						displayRange.zoomIn();
						repaint();
					} else if ( displayRange != null && e.getKeyCode() == KeyEvent.VK_LEFT ) {
						displayRange.rollLeft();
						repaint();
					} else if ( displayRange != null && e.getKeyCode() == KeyEvent.VK_RIGHT ) {
						displayRange.rollRight();
						repaint();
					}
				}
			});
			addMouseListener( new MouseAdapter() 
			{
				@Override
				public void mouseClicked(MouseEvent e) 
				{
					if ( e.getButton() == MouseEvent.BUTTON3 ) {
						if ( selectionMode == SelectionMode.NONE ) {
							return;
						}
						selectionMode = SelectionMode.NONE;
						repaint();
					}
					
					if ( e.getButton() == MouseEvent.BUTTON1 ) 
					{
					 if ( selectionMode == SelectionMode.NONE) {
						selectionMode = SelectionMode.MIN;
					 } else if ( selectionMode == SelectionMode.MIN ) {
						 firstSelectionX = currentSelectionX;
						 selectionMode = SelectionMode.MAX;
						 currentSelectionX = e.getX();
						 repaint();
					 } 
					 else if ( selectionMode == SelectionMode.MAX ) 
					 {
						 selectionMode = SelectionMode.NONE;
						 int first = firstSelectionX-RESERVED_WIDTH;
						 int second = currentSelectionX-RESERVED_WIDTH;
						 int min = Math.min(first,second);
						 int max = Math.max(first,second);
						 double percentageOne = min / (double) ( getWidth() - RESERVED_WIDTH - RIGHT_BORDER); // 0...1
						 double percentageTwo = max / (double) ( getWidth() - RESERVED_WIDTH - RIGHT_BORDER); // 0...1
						 System.out.println("Selected percentage: "+percentageOne+" - "+percentageTwo);
						 // set new view port
						 long minCycle,maxCycle;
						 if ( displayRange == null ) {
							 minCycle = states.stream().mapToLong( s -> s.cycle ).min().orElse( 0 );
							 maxCycle = states.stream().mapToLong( s -> s.cycle ).max().orElse( 0 );
						 } else {
							 minCycle = displayRange.minCycle;
							 maxCycle = displayRange.maxCycle;
						 }
						 long currentRange = maxCycle-minCycle;
						 long newMinCycles = minCycle + (long) (percentageOne*currentRange);
						 long newMaxCycles = minCycle + (long) (percentageTwo*currentRange);
						 if ( displayRange == null ) {
							 displayRange = new DisplayRange(newMinCycles , newMaxCycles );
						 } else {
							 displayRange.minCycle = newMinCycles;
							 displayRange.maxCycle = newMaxCycles;
						 }
						 repaint();
					 }
					}
				}
			});
			addMouseMotionListener( new MouseMotionListener() {
				
				@Override
				public void mouseMoved(MouseEvent e) 
				{
					if ( selectionMode != SelectionMode.NONE ) 
					{
						final int mouseX = e.getX();
						if ( mouseX >= RESERVED_WIDTH && mouseX <= (getWidth()-RIGHT_BORDER ) ) 
						{
							currentSelectionX = mouseX;
						}
						repaint();
					}
				}
				
				@Override
				public void mouseDragged(MouseEvent e) { }
			});
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
		protected void paintComponent(Graphics graphics)
		{
			// clear screen
			final Graphics2D g = (Graphics2D) graphics;
			super.paintComponent(g);
			
			final int w = getWidth();
			final int h = getHeight();
			
			// calculate width of display area 
			final int remainingWidth = w - RESERVED_WIDTH - RIGHT_BORDER;
			
			// get state snapshots from bus
			states = emulator.getBus().getSnapshot();

			// calculate the actual number of cycles that will be displayed
			final long availFirstCycle = states.stream().mapToLong( s -> s.cycle ).min().orElse(0);
			final long availLastCycle  = states.stream().mapToLong( s -> s.cycle ).max().orElse(0);
			
			long firstCycle,lastCycle;
			if ( displayRange == null ) {
				firstCycle = availFirstCycle;
				lastCycle  = availLastCycle;
			} else {
				firstCycle = displayRange.minCycle;
				lastCycle  = displayRange.maxCycle;
			}
			final long cycleDelta = Math.max( lastCycle - firstCycle , 1 );
			
			// calculate width/length in pixels for an individual cycle 
			double cycleWidth = remainingWidth/ (double) cycleDelta;
			
			// trim down the list of bus state objects to those that fall within the 
			// firstCycle/lastCycle range

			final List<StateSnapshot> statesInRange = new ArrayList<>( states.size() );			
			for ( StateSnapshot s : states ) 
			{
				if ( s.cycle >= firstCycle && s.cycle <= lastCycle ) {
					statesInRange.add( s );
				} else if ( s.cycle > lastCycle ) {
					statesInRange.add(s); // add one more state change so we get continous lines to the right if there are more state
					// changes coming
					break;
				}
			}
			
			// render title
			final double timeInSeconds = cycleDelta*1/1000000f;
			g.setColor(Color.RED);
			final DecimalFormat DF = new DecimalFormat("##0.0######");
			final String title = "View: "+DF.format(timeInSeconds)+"s ("+cycleDelta+" cycles ("+statesInRange.size()+" out of "+states.size()+" states) , first: "+firstCycle+" , last: "+lastCycle+")";
			Rectangle2D stringBounds = g.getFontMetrics().getStringBounds( title , g );
			g.drawString( title , (int) ( w/2- stringBounds.getWidth()/2) , 10 );			

			// render lanes plus some free space below the last one
			// so we can output the state names there
			
			final int laneHeight = h/(lanes.size()+1); // +1 lane for state labels

			int y = 0;
			for ( Lane l : lanes ) {
				l.renderTitle( 3 , y , laneHeight , g, w );
				y += laneHeight;
			}

			final int stateLineY = (lanes.size()+1) * laneHeight-26;

			// we'll stagger the state names so they don't overlap
			// too easily
			int stateLineIdx = 0;
			final int[] stateLineOffset = new int[] { stateLineY , stateLineY + 13 , stateLineY + 26 };

			StateSnapshot previousState = null;

			firstCycle = statesInRange.isEmpty() ? 0 : statesInRange.get(0).cycle;
			for ( StateSnapshot currentState : statesInRange )
			{
				long offset = currentState.cycle - firstCycle;
				final int x = (int) (RESERVED_WIDTH + (offset * cycleWidth));
				// mark state changes with vertical, dashed line
				if ( previousState == null || previousState.busState != currentState.busState )
				{
					Color oldColor = g.getColor();
					Stroke oldStroke = g.getStroke();
					g.setColor( Color.GREEN);
					g.drawString( currentState.busState.toString() , x , stateLineOffset[ stateLineIdx++] );
					g.setStroke( DASHED_SLIM );
					g.drawLine( x , getHeight() , x , 0 );
					stateLineIdx = stateLineIdx % stateLineOffset.length;
					g.setColor(oldColor);
					g.setStroke( oldStroke );
				}
				y = 0;
				for ( Lane l : lanes )
				{
					l.render( x , y , laneHeight , cycleWidth, previousState, currentState , g );
					y += laneHeight;
				}
				previousState=currentState;
			}
			
			switch( selectionMode ) 
			{
				case MIN:
					g.setColor(Color.RED);
					g.drawLine( currentSelectionX , 0 , currentSelectionX , getHeight() );
					break;
				case MAX:
					g.setColor(Color.RED);
					g.setXORMode( Color.BLACK );
					int min = Math.min(firstSelectionX,currentSelectionX);
					int max = Math.max(firstSelectionX,currentSelectionX);
					g.fillRect( min , 0 , max-min, getHeight() );
					g.setPaintMode();

					g.drawLine( firstSelectionX , 0 , firstSelectionX , getHeight() );
					g.drawLine( currentSelectionX , 0 , currentSelectionX , getHeight() );
					break;
				default:
			}
		}
	}
}