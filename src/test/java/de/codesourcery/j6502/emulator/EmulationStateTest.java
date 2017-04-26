package de.codesourcery.j6502.emulator;

import static org.junit.Assert.assertArrayEquals;

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
    
    public void testStoreIntArray() throws IOException 
    {
        final int[] test = {1,2,3,4,5};
        final int[] test2 = new int[test.length];
        
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        EmulationState.writeIntArray(test , out );
        
        final byte[] data = out.toByteArray();
        HexDump.dump(data , 0 , System.out , 0 );
        
        final ByteArrayInputStream byteIn = new ByteArrayInputStream(data);
        EmulationState.populateIntArray(test2 , byteIn );
        assertArrayEquals( test, test2 );
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