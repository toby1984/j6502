package de.codesourcery.j6502.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Graphics;

import javax.swing.JPanel;

import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.SerialDevice;
import de.codesourcery.j6502.emulator.diskdrive.DiskHardware;
import de.codesourcery.j6502.emulator.diskdrive.DiskHardware.ReadMode;
import de.codesourcery.j6502.ui.WindowLocationHelper.IDebuggerView;

public class FloppyInfoPanel extends JPanel implements IDebuggerView {

    private boolean isDisplayed = false;
    private Component peer;
    
    private volatile boolean gotDrive = false;
    private volatile boolean motor = false;
    private volatile boolean driveLED = false;
    private volatile boolean readMode = false;
    private volatile float headPosition;;
    
    public FloppyInfoPanel() {
        Debugger.setup( this );
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
    public boolean isDisplayed() {
        return isDisplayed;
    }

    @Override
    public void setDisplayed(boolean yesNo) {
        this.isDisplayed = yesNo;
    }
    
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        
        g.setColor( Color.GREEN );
        
        final String msg;
        if ( gotDrive ) {
            msg = "LED: "+driveLED+" | motor: "+motor+" | mode: "+(readMode?"READ":"WRITE")+" | head: "+headPosition;
        } else {
            msg = "<no drive available>";
        }
        g.drawString( msg  , 10 ,15 );
    }

    @Override
    public void refresh(Emulator emulator) {

        synchronized( emulator ) 
        {
            final SerialDevice device = emulator.getBus().getDevice( 8 );
            if ( device instanceof DiskHardware ) 
            {
                this.gotDrive = true;
                this.driveLED = ((DiskHardware) device).getDriveLED();
                this.headPosition = ((DiskHardware) device).getHeadPosition();
                this.motor = ((DiskHardware) device).getMotorStatus();
                this.readMode = ((DiskHardware) device).getDriveMode() instanceof ReadMode;
            } else {
                this.gotDrive = false;
            }
        }
        repaint();
    }
    
    @Override
    public boolean isRefreshAfterTick() {
        return true;
    }
}