package de.codesourcery.j6502.emulator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.apache.commons.io.HexDump;

import de.codesourcery.j6502.emulator.EmulationState.EmulationStateEntry;
import de.codesourcery.j6502.emulator.EmulationState.EntryType;
import junit.framework.TestCase;

public class EmulationStateTest extends TestCase
{
    public void testStoreEmpty() throws IOException 
    {
        final EmulationState state = EmulationState.newInstance();
        System.out.println("GOT: "+state);
        roundtrip( state );
    }
    
    public void testStoreWithOneEntry() throws IOException 
    {
        final EmulationState state = EmulationState.newInstance();
        EmulationStateEntry newEntry = new EmulationStateEntry( EntryType.RAM , (byte) 2 );
        state.add( newEntry );
        System.out.println("GOT: "+state);
        roundtrip( state );
    }    
    
    private void roundtrip(EmulationState state) throws IOException {
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        state.write( out );
        HexDump.dump( out.toByteArray() , 0 , System.out , 0 );
        
        final EmulationState read = EmulationState.read( new ByteArrayInputStream( out.toByteArray() ) );
        assertEquals( state , read );
    }
}