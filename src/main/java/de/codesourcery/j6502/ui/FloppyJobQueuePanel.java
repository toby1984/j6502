package de.codesourcery.j6502.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReferenceArray;

import javax.swing.JPanel;

import de.codesourcery.j6502.emulator.EmulatorDriver;
import de.codesourcery.j6502.emulator.SerialDevice;
import de.codesourcery.j6502.emulator.diskdrive.DiskDrive.JobQueue;
import de.codesourcery.j6502.emulator.diskdrive.DiskHardware;
import de.codesourcery.j6502.ui.WindowLocationHelper.IDebuggerView;

public final class FloppyJobQueuePanel extends JPanel implements IDebuggerView {

    private final EmulatorDriver driver;
    private Component peer;
    private boolean isDisplayed;
    
    private volatile Optional<DiskHardware> drive = Optional.empty();
    private final AtomicReferenceArray<JobQueue> queueEntries = new AtomicReferenceArray<JobQueue>( 6 );
    
    public FloppyJobQueuePanel(EmulatorDriver driver) 
    {
        this.driver = driver;
        setPreferredSize(new Dimension(200,200 ) );
        for ( int i = 0 ; i < queueEntries.length() ; i++ ) {
            queueEntries.set( i,new JobQueue(i));
        }
    }
    
    @Override
    public String getIdentifier() {
        return "Floppy Job Queue view";
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
    public void refresh() 
    {
        driver.invokeAndWait( emulator -> 
        {
            final SerialDevice device = emulator.getBus().getDevice( 8 );
            if ( device instanceof DiskHardware) 
            {
                drive = Optional.of( (DiskHardware) device );
                final JobQueue[] src = drive.get().getDiskDrive().getQueueEntries();
                for ( int i = 0 ; i < 6 ; i++ ) 
                {
                    src[i].copyTo( queueEntries.get(i) );
                }
            } else {
                drive = Optional.empty();
            }
        });
    }

    @Override
    public boolean isRefreshAfterTick() {
        return true;
    }
    
    @Override
    protected void paintComponent(Graphics g) 
    {
        super.paintComponent(g);
        g.setColor(Color.GREEN);
        final Optional<DiskHardware> drive = this.drive;
        if ( drive.isPresent() ) 
        {
            int y = 15;
            for ( int i = 0 ; i <queueEntries.length() ; i++ , y+= 15 ) 
            {
                g.drawString( queueEntries.get(i).toString() , 15 , y );
            }
        } else {
            g.drawString("<no drive>", 15,15 );
        }
    }
}