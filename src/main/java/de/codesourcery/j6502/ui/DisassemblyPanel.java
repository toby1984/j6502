package de.codesourcery.j6502.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.SwingUtilities;

import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.disassembler.Disassembler.Line;
import de.codesourcery.j6502.emulator.Breakpoint;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.EmulatorDriver;
import de.codesourcery.j6502.emulator.EmulatorDriver.IBreakpointLister;
import de.codesourcery.j6502.ui.Debugger.LineWithBounds;
import de.codesourcery.j6502.utils.HexDump;

public final class DisassemblyPanel extends BufferedView implements WindowLocationHelper.IDebuggerView , IBreakpointLister
{
    private final Disassembler dis = new Disassembler().setAnnotate(true);

    protected final Short TRACK_CURRENT_PC = Short.valueOf( (short) 0xdead ); // dummy value, any will do since doRefresh() just checks for != NULL

    protected static final int X_OFFSET = 30;
    protected static final int Y_OFFSET = Debugger.LINE_HEIGHT;

    private final Emulator emulator;
    private final EmulatorDriver driver;
    
    private final int bytesToDisassemble = 48;

    private short currentAddress;
    private Short addressToMark = TRACK_CURRENT_PC;

    private final List<LineWithBounds> lines = new ArrayList<>();

    private boolean isDisplayed;
    private Component peer;

    @Override
    protected void initGraphics(Graphics2D g) { Debugger.setup( g ); }

    public void setTrackPC(boolean trackPC )
    {
        if ( trackPC ) {
            addressToMark = TRACK_CURRENT_PC;
        }
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
            final int pc;
            Short adrToMark = addressToMark;
            if ( adrToMark != null )
            {
                pc = emulator.getCPU().pc();
                adrToMark = (short) pc;
                internalSetAddress( (short) pc , TRACK_CURRENT_PC );
            } else {
                pc = currentAddress & 0xffff;
            }

            maybeDisassemble( g , adrToMark );

            for ( int i = 0, y = Debugger.LINE_HEIGHT ; i < lines.size() ; i++ , y+= Debugger.LINE_HEIGHT )
            {
                final LineWithBounds line = lines.get(i);

                g.setColor( Debugger.FG_COLOR );
                g.drawString( line.line.toString() , X_OFFSET , y );
                if ( adrToMark != null && line.line.address == adrToMark.shortValue() )
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

    public DisassemblyPanel(Emulator emulator,EmulatorDriver driver)
    {
        this.emulator = emulator;
        this.driver = driver;
        
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
                    Short adr = Debugger.queryAddress();
                    if ( adr != null )
                    {
                        setAddress( adr , null );
                    } else {
                        setAddress( (short) 0 , TRACK_CURRENT_PC );
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
                            driver.addBreakpoint( new Breakpoint( adr , false , true ) );
                        }
                        refresh( emulator );
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
        System.out.println("Disassembling starts @ "+HexDump.toAdr( adr ) );
        internalSetAddress(adr, addressToMark);

        synchronized( emulator )
        {
            doRefresh( emulator );
        }
        repaint();
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
                        y += Debugger.LINE_HEIGHT;
                    }
                };
                dis.disassemble( emulator.getMemory() , offset, bytesToDisassemble , lineConsumer);
                alignmentCorrect = lines.stream().anyMatch( line -> line.line.address == pc );
                offset++;
            }
        }
    }

    private void maybeDisassemble(Graphics g,Short addressToMark)
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