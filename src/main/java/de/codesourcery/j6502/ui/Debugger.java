package de.codesourcery.j6502.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.event.KeyAdapter;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JDesktopPane;
import javax.swing.JFrame;
import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import com.sun.glass.events.KeyEvent;

import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.disassembler.Disassembler.Line;
import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.ui.WindowLocationHelper.ILocationAware;
import de.codesourcery.j6502.utils.HexDump;

public class Debugger
{
	private final Emulator emulator = new Emulator();

	protected static final Color FG_COLOR = Color.GREEN;
	protected static final Color BG_COLOR = Color.BLACK;
	protected static final int VERTICAL_LINE_SPACING = 2;
	protected static final Font MONO_FONT = new Font( "Monospaced", Font.PLAIN, 12 );

	protected static enum Mode { SINGLE_STEP , CONTINOUS; }
	
	protected final WindowLocationHelper locationHelper = new WindowLocationHelper();

	protected final EmulatorDriver driver = new EmulatorDriver( emulator ) {

		@Override
		protected void onStopHook(Throwable t) {
			SwingUtilities.invokeLater( () -> updateWindows() );
		}

		protected void onStartHook() {
			SwingUtilities.invokeLater( () -> updateWindows() );
		}

		@Override
		protected void tick() {
			SwingUtilities.invokeLater( () -> updateWindows() );
		}
	};

	private final List<ILocationAware> locationAware = new ArrayList<>();
	
	private final ButtonToolbar toolbar = new ButtonToolbar();
	private final DisassemblyPanel disassembly = new DisassemblyPanel();
	private final CPUStatusPanel cpuPanel = new CPUStatusPanel();

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
		frame.setContentPane( desktop );
		frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);

		frame.setPreferredSize( new Dimension(800,480 ) );
		frame.pack();
		
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
			loc.setInternalFrame( frame );
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
		
		private JInternalFrame peer;
		
		@Override
		public void setInternalFrame(JInternalFrame frame) {
			this.peer = frame;
		}
		
		@Override
		public JInternalFrame getInternalFrame() {
			return peer;
		}
		
		public ButtonToolbar()
		{
			setPreferredSize( new Dimension( 400,50 ) );
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
		short pc;
		synchronized( emulator ) {
			pc = emulator.getCPU().pc;
		}
		disassembly.setAddress( pc );
		cpuPanel.repaint();
	}

	// CPU status
	protected final class CPUStatusPanel extends JPanel implements WindowLocationHelper.ILocationAware
	{
		private JInternalFrame peer;
		
		@Override
		public void setInternalFrame(JInternalFrame frame) {
			this.peer = frame;
		}
		
		@Override
		public JInternalFrame getInternalFrame() {
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
				lines.add( "PC: "+HexDump.toAdr( cpu.pc ) + "   Flags: "+ cpu.getFlagsString() );
				lines.add("Previous PC: "+HexDump.toAdr( cpu.previousPC ) );
				lines.add(" A: "+HexDump.toHex( cpu.accumulator ) );
				lines.add(" X: $"+HexDump.toHex( cpu.x) );
				lines.add(" Y: $"+HexDump.toHex( cpu.y) );
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

	protected final class DisassemblyPanel extends JPanel implements WindowLocationHelper.ILocationAware
	{
		private final Disassembler dis = new Disassembler().setAnnotate(true);
		
		private final int bytesToDisassemble = 32;
		private short currentAddress;

		private boolean mark = false;
		private JInternalFrame peer;
		
		@Override
		public void setInternalFrame(JInternalFrame frame) {
			this.peer = frame;
		}
		
		@Override
		public JInternalFrame getInternalFrame() {
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
				public void keyReleased(java.awt.event.KeyEvent e) 
				{
					if ( e.getKeyCode() == KeyEvent.VK_PAGE_DOWN ) {
						pageDown();
					} else if ( e.getKeyCode() == KeyEvent.VK_PAGE_UP ) {
						pageUp();
					}
				}
			} );
		}
		
		public void pageUp() {
			mark = false;
			this.currentAddress = (short) ( this.currentAddress - bytesToDisassemble/2 );
			repaint();
		}
		
		public void pageDown() {
			mark = false;
			this.currentAddress = (short) ( this.currentAddress + bytesToDisassemble/2 );
			repaint();
		}
		
		public void setAddress(short adr) {
			mark = true; 
			this.currentAddress = adr;
			repaint();
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			super.paintComponent(g);

			final List<Line> lines = new ArrayList<>();

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
					dis.disassemble( emulator.getMemory() , offset, bytesToDisassemble , lines::add );
					alignmentCorrect = lines.stream().anyMatch( line -> line.address == pc );
					offset++;
				}
			}

			final int X_OFFSET = 5;
			for ( int i = 0, y = 0 ; i < lines.size() ; i++ , y+= 15 )
			{
				final Line line = lines.get(i);
				final LineMetrics lineMetrics = g.getFontMetrics().getLineMetrics( line.toString(),  g );
				final Rectangle2D stringBounds = g.getFontMetrics().getStringBounds( line.toString(),  g );

				g.setColor(FG_COLOR);
				g.drawString( line.toString() , X_OFFSET , y );
				if ( mark && line.address == pc )
				{
					g.setColor(Color.RED);
					g.drawRect( X_OFFSET , (int) (y - lineMetrics.getAscent() ) , (int) stringBounds.getWidth() , (int) (lineMetrics.getHeight() ) );
				}
			}
		}
	}
}