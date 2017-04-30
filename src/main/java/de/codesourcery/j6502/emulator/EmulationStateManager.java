package de.codesourcery.j6502.emulator;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.emulator.EmulationState.EmulationStateEntry;
import de.codesourcery.j6502.emulator.EmulationState.EntryType;
import de.codesourcery.j6502.emulator.EmulatorDriver.Mode;

public class EmulationStateManager
{
    private final EmulatorDriver driver;

    public EmulationStateManager(EmulatorDriver driver) {
        Validate.notNull(driver, "driver must not be NULL");
        this.driver = driver;
    }

    public void restoreEmulationState(InputStream in ) throws IOException {

        driver.invokeAndWait(emulator -> 
        {
            final EmulationState state = EmulationState.read( in );
            emulator.reset();

            // restore C64 memory
            emulator.getMemory().restoreState( state );

            // restore C64 CPU state
            final EmulationStateEntry entry = state.getEntry( EntryType.C64_CPU );
            emulator.cpu.restoreState( entry.getPayload() );

            // FIXME: restore floppy state as well
        });
    }

    public void saveEmulationState(OutputStream out) throws IOException 
    {
        driver.invokeAndWait(emulator -> 
        {
            final EmulationState state = EmulationState.newInstance();
            // save C64 memory
            emulator.getMemory().saveState( state );

            // save C64 CPU state
            final EmulationStateEntry entry = new EmulationStateEntry( EntryType.C64_CPU , (byte) 1 );
            entry.setPayload( emulator.cpu.getState() );
            state.add( entry );

            // FIXME: save floppy state            
            state.write( out );
            System.out.println("=============\nState\n==========="+state);
        });
    }
}