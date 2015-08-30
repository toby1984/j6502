package de.codesourcery.j6502.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.ArrayList;
import java.util.List;

import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.utils.HexDump;

// CPU status
public abstract class CPUStatusPanel extends BufferedView implements WindowLocationHelper.IDebuggerView
{
    private Component peer;
    private boolean isDisplayed;

    // @GuardedBy( lines )
    private final List<String> lines = new ArrayList<>();

    public CPUStatusPanel()
    {
        setPreferredSize( new Dimension(200,150 ) );
    }
    
    protected abstract CPU getCPU();
    
    @Override
    public void setLocationPeer(Component frame) {
        this.peer = frame;
    }

    @Override
    protected void initGraphics(Graphics2D g) { Debugger.setup( g ); }

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
        final CPU cpu = getCPU();
        lines.clear();
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
            g.setColor( Debugger.FG_COLOR );
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