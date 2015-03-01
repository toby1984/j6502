package de.codesourcery.j6502.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import org.apache.commons.io.IOUtils;

import com.sun.glass.events.KeyEvent;

import de.codesourcery.j6502.assembler.Assembler;
import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Parser;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.disassembler.Disassembler.Line;
import de.codesourcery.j6502.disassembler.DisassemblerTest;
import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.EmulatorTest;
import de.codesourcery.j6502.emulator.IMemoryProvider;
import de.codesourcery.j6502.emulator.IMemoryRegion;
import de.codesourcery.j6502.emulator.Keyboard;
import de.codesourcery.j6502.emulator.Keyboard.Key;
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
			if ( age > 1000 )
			{
				lastTick = now;
				SwingUtilities.invokeLater( () -> updateWindows() );
			}
		}
	};

	private final List<ILocationAware> locationAware = new ArrayList<>();

	private final ButtonToolbar toolbar = new ButtonToolbar();
	private final DisassemblyPanel disassembly = new DisassemblyPanel();
	private final HexDumpPanel hexPanel = new HexDumpPanel();
	private final CPUStatusPanel cpuPanel = new CPUStatusPanel();
	private final ScreenPanel screenPanel = new ScreenPanel();

	public static void main(String[] args)
	{
		new Debugger().run();
	}

	public void run() {

		emulator.reset();

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
		
		final JInternalFrame screenPanelFrame = wrap( "Screen" , screenPanel );
		desktop.add( screenPanelFrame  );		
		

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
			emulator.reset();

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
			emulator.setMemoryProvider( provider );
			emulator.getCPU().pc( origin );
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
				synchronized(emulator) {
					prepareTest();
				}
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
					Key pressed = Keyboard.keyCodeToKey( e.getKeyCode() );
					if ( pressed != null ) {
						emulator.keyPressed( pressed );
					}
				}
				
				@Override
				public void keyReleased(java.awt.event.KeyEvent e) 
				{
					Key released = Keyboard.keyCodeToKey( e.getKeyCode() );
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
			emulator.getVIC().render( (Graphics2D) g , getWidth() , getHeight() );
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

	protected final class DisassemblyPanel extends JPanel implements WindowLocationHelper.ILocationAware
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
							repaint();
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
	}

	private Short queryAddress()
	{
		final String s = JOptionPane.showInputDialog(null, "Enter hexadecimal address (0-ffff)", "Enter address" , JOptionPane.PLAIN_MESSAGE );

		if ( s == null ) {
			return null;
		}
		return (short) Integer.valueOf( s ,  16 ).intValue();
	}
}