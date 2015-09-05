package de.codesourcery.j6502.ui;

import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.GridLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JDialog;
import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.SwingConstants;

import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.EmulatorDriver.IEmulationListener;
import de.codesourcery.j6502.utils.HexDump;

// CPU status
public abstract class CPUStatusPanel extends BufferedView implements WindowLocationHelper.IDebuggerView , IEmulationListener
{
    private Component peer;
    private boolean isDisplayed;

    // @GuardedBy( lines )
    private final List<String> lines = new ArrayList<>();
    
    private volatile boolean emulationRunning;

    public CPUStatusPanel()
    {
        setRequestFocusEnabled( true );
        setFocusable( true );
        
        setPreferredSize( new Dimension(200,150 ) );
        
        addKeyListener( new KeyAdapter() {
            
            @Override
            public void keyTyped(KeyEvent e) 
            {
                if ( emulationRunning ) {
                    return;
                }
                switch( e.getKeyChar() ) 
                {
                    case 'p':
                        readValue("Change PC" , "$"+Integer.toHexString( getCPU().pc() ) , value -> getCPU().pc( value ) ); 
                        break;
                    case 'a':
                        readValue("Change accumulator" , "$"+Integer.toHexString( getCPU().getAccumulator() ) , value -> getCPU().setAccumulator( value ) );
                        break;
                    case 'x':
                        readValue("Change X" , "$"+Integer.toHexString( getCPU().getX() ) , value -> getCPU().setX( value ) );
                        break;
                    case 'y':
                        readValue("Change Y" , "$"+Integer.toHexString( getCPU().getY() ) , value -> getCPU().setY( value ) );
                        break;       
                    case 'f':
                        final CPUFlagDialog dialog = new CPUFlagDialog();
                        dialog.pack();
                        dialog.setLocationRelativeTo( null );
                        dialog.setVisible( true );
                        break;
                }
            }
        });
    }
    
    protected final class CPUFlagDialog extends JDialog {
        
        public CPUFlagDialog() 
        {
            super((Dialog) null , "Change CPU flags",true);
            
            setPreferredSize( new Dimension(200,200 ) );
            setDefaultCloseOperation( JFrame.DISPOSE_ON_CLOSE );
            
            final int flagCount = CPU.Flag.values().length;
            getContentPane().setLayout( new GridLayout( flagCount+1 , 1 ) );
            final List<JCheckBox> checkboxes = new ArrayList<>();
            for ( CPU.Flag flag : CPU.Flag.values() ) 
            {
                final JCheckBox cb = new JCheckBox( flag.name+":" , getCPU().isSet( flag ) );
                cb.setHorizontalAlignment( SwingConstants.LEFT );
//                cb.setHorizontalTextPosition( SwingConstants.LEFT );
                checkboxes.add( cb );
                getContentPane().add( cb );
            }
            
            final JButton apply = new JButton("Apply");
            apply.addActionListener( ev -> 
            {
                final Iterator<JCheckBox> box = checkboxes.iterator();
                for ( CPU.Flag flag : CPU.Flag.values() ) 
                {
                    getCPU().setFlag( flag , box.next().isSelected() );
                }
                setVisible(false);
                dispose();
            });
            getContentPane().add( apply );
        }
    }
    
    protected abstract CPU getCPU();
    
    private void readValue(String message,String initialValue, Consumer<Integer> consumer) {
        
        String value = JOptionPane.showInputDialog( message , initialValue );
        if ( value != null ) 
        {
            value = value.trim();
            if ( value.trim().length() == 0 ) {
                return;
            }
            final int iValue;
            try 
            {
                if ( value.startsWith("$" ) ) {
                    iValue = Integer.parseInt( value.substring(1).toLowerCase() , 16 );
                } else {
                    iValue = Integer.parseInt( value );
                }
            } catch(NumberFormatException e) {
                e.printStackTrace();
                return;
            }
            consumer.accept( iValue );
        }
    }
    
    @Override
    public void setLocationPeer(Component frame) {
        this.peer = frame;
    }

    @Override
    protected void initGraphics(Graphics2D g) { Debugger.setup( g ); }

    @Override
    public void emulationStarted() 
    {
        emulationRunning = true;        
    }
    
    @Override
    public void emulationStopped(Throwable t, boolean stoppedOnBreakpoint) {
        emulationRunning = false;        
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