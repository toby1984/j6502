package de.codesourcery.j6502.utils;

import junit.framework.TestCase;

public class BitOutputStreamTest extends TestCase {

    public void testWriteLessThanOneByte() {
        final BitOutputStream out = new BitOutputStream(1);
        out.writeBit( 1 );
        out.writeBit( 0 );
        out.writeBit( 1 );
        out.writeBit( 1 );
        out.writeBit( 0 );
        assertEquals( 5 , out.getBitsWritten() );
        final byte[] data = out.toByteArray();
        assertEquals(1,data.length );
        assertEquals( 0b10110000 , data[0] & 0xff );
    }
    
    public void testWriteOneByte() {
        final BitOutputStream out = new BitOutputStream(1);
        out.writeBit( 1 );
        out.writeBit( 0 );
        out.writeBit( 1 );
        out.writeBit( 1 );
        out.writeBit( 0 );
        out.writeBit( 1);
        out.writeBit( 1 );
        out.writeBit( 1 );
        
        assertEquals( 8 , out.getBitsWritten() );
        final byte[] data = out.toByteArray();
        assertEquals(1,data.length );
        assertEquals( 0b10110111 , data[0] & 0xff );
    }   
    
    public void testWriteNineBits() 
    {
        final BitOutputStream out = new BitOutputStream(1);
        out.writeBit( 1 );
        out.writeBit( 0 );
        out.writeBit( 1 );
        out.writeBit( 1 );
        out.writeBit( 0 );
        out.writeBit( 1);
        out.writeBit( 1 );
        out.writeBit( 1 );
        out.writeBit( 0 );
        
        assertEquals( 9 , out.getBitsWritten() );
        final byte[] data = out.toByteArray();
        assertEquals(2,data.length );
        assertEquals( 0b10110111 , data[0] & 0xff );
        assertEquals( 0b00000000 , data[1] & 0xff );
    }      
    
    public void testBulkWriteOneByte() {
        final BitOutputStream out = new BitOutputStream(1);
        out.writeByte( 0b1100_1010 );
        
        assertEquals( 8 , out.getBitsWritten() );
        final byte[] data = out.toByteArray();
        assertEquals(1,data.length );
        assertEquals( 0b1100_1010 , data[0] & 0xff );
    }    
    
    public void testWriteOneWord() {
        final BitOutputStream out = new BitOutputStream(1);
        out.writeWord( 0x1234 );
        
        assertEquals( 16 , out.getBitsWritten() );
        final byte[] data = out.toByteArray();
        assertEquals(2,data.length );
        assertEquals( 0x34, data[0] & 0xff );
        assertEquals( 0x12, data[1] & 0xff );
    }    
    
    public void testWriteOneDWord() {
        final BitOutputStream out = new BitOutputStream(1);
        out.writeDWord( 0x12345678 );
        
        assertEquals( 32 , out.getBitsWritten() );
        final byte[] data = out.toByteArray();
        assertEquals(4,data.length );
        assertEquals( 0x78, data[0] & 0xff );
        assertEquals( 0x56, data[1] & 0xff );
        assertEquals( 0x34, data[2] & 0xff );
        assertEquals( 0x12, data[3] & 0xff );
    }    
}
