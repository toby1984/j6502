package de.codesourcery.j6502.utils;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;

import junit.framework.TestCase;

public class BitStreamTest extends TestCase 
{
    public void testSimpleRead() 
    {
        final byte[] expected = new byte[] { 1 , 2 ,3 , 4 , 5 , 6 ,7 , 8 };
        final byte[] actual = new byte[ expected.length ];
        
        final BitStream stream = new BitStream( expected );
        for ( int i = 0 ; i < expected.length ; i++ ) 
        {
            byte value = (byte) (stream.readByte() & 0xff);
            actual[i] = value;
        }
        assertTrue( stream.hasWrapped() );
        Assert.assertArrayEquals( expected , actual );
    }
    
    public void testRewind() 
    {
        final byte[] expected = new byte[] { 1 , 2 ,3 , 4 , 5 , 6 ,7 , 8 };
        final List<Byte> actual = new ArrayList<>(); 
        
        boolean rewind = false;
        final BitStream stream = new BitStream( expected );
        while ( ! stream.hasWrapped() )
        {
            byte value = (byte) (stream.readByte() & 0xff);
            actual.add( Byte.valueOf( value ) );
            if ( value == 4 && ! rewind ) {
                stream.rewind( 8 );
                rewind = true;
            }
        }
        assertTrue( stream.hasWrapped() );
        Assert.assertArrayEquals( new byte[] { 1 , 2 ,3 , 4 , 4 , 5 , 6 ,7 , 8 } , toByteArray( actual ) );
    }    
    
    public void testMark() 
    {
        final byte[] expected = new byte[] { 1 , 2 ,3 , 4 , 5 , 6 ,7 , 8 };
        final List<Byte> actual = new ArrayList<>(); 
        
        boolean markSet = false;
        final BitStream stream = new BitStream( expected );
        while ( ! stream.hasWrapped() )
        {
            byte value = (byte) (stream.readByte() & 0xff);
            actual.add( Byte.valueOf( value ) );
            if ( value == 4 && ! markSet ) {
                stream.mark();
                markSet = true;
            }
        }
        
        assertTrue( stream.hasWrapped() );
        Assert.assertArrayEquals( new byte[] { 1 , 2 ,3 , 4 , 5 , 6 ,7 , 8 , 1 , 2 , 3 , 4 } , toByteArray( actual ) );
    }   
    
    public void testRewindOverMark() 
    {
        final byte[] expected = new byte[] { 1 , 2 ,3 , 4 };
        
        final BitStream stream = new BitStream( expected );
        
        assertEquals( 1 , stream.readByte() );
        assertEquals( 2 , stream.readByte() );
        stream.mark();
        assertFalse( stream.hasWrapped() );
        stream.rewind(8);
        assertFalse( stream.hasWrapped() );
        assertEquals( 2 , stream.readByte() );
        assertTrue( stream.hasWrapped() );
        
        assertEquals( 3 , stream.readByte() );
        assertEquals( 4 , stream.readByte() );
        assertTrue( stream.hasWrapped() );
    }  
    
    public void testRewindAtStart() 
    {
        final byte[] expected = new byte[] { 1 , 2 , 3 };
        
        final BitStream stream = new BitStream( expected );
        
        stream.rewind(16);
        assertFalse( stream.hasWrapped() );
        assertEquals( 2 , stream.readByte() );
        assertEquals( 3 , stream.readByte() );
        
        assertTrue( stream.hasWrapped() );
        
        assertEquals( 1 , stream.readByte() );
    }    
    
    public void testRewindAcrossStart() 
    {
        final byte[] expected = new byte[] { 1 , 2 , 3 };
        
        final BitStream stream = new BitStream( expected );
        
        assertEquals( 1 , stream.readByte() );
        stream.rewind(16);
        assertFalse( stream.hasWrapped() );
        assertEquals( 3 , stream.readByte() );
        assertEquals( 1 , stream.readByte() );
        assertTrue( stream.hasWrapped() );
        assertEquals( 2 , stream.readByte() );
    }     
    
    private static byte[] toByteArray(List<Byte> data) 
    {
       final byte[] result = new byte[ data.size() ];
       for ( int i = 0 ; i < data.size() ; i++ ) 
       {
           result[i] = data.get(i).byteValue();
       }
       return result;
    }
}
