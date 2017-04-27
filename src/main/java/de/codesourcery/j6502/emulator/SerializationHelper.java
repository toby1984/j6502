package de.codesourcery.j6502.emulator;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.codesourcery.j6502.utils.Misc;

public class SerializationHelper 
{
    protected static enum Tag 
    {
        BYTE      (0b10000000),
        INT       (0b10000001),
        LONG      (0b10000010),
        SHORT     (0b10000011),
        BOOLEAN   (0b10000100),
        INT_ARRAY (0b10000101),
        BYTE_ARRAY(0b10000110);
        
        private int identifier;
        private Tag(int identifier) {
            this.identifier = identifier;
        }
        public void write(OutputStream out) throws IOException 
        {
            out.write( identifier );
        }
        
        public void read(InputStream in) throws IOException 
        {
            final int value = in.read();
            if ( value == -1 ) {
                throw new EOFException("Premature end of file, expected tag for "+this);
            }
            if ( value != identifier) {
                Tag actual = read( value );
                if ( actual == null ) {
                    throw new IOException("Expected tag "+this+" but got unrecognized tag identifier "+Misc.to8BitHex( value ) );
                }
                throw new IOException("Expected tag "+this+" but got "+actual);
            }
        }
        
        public static Tag read(int value) 
        {
            switch( value ) 
            {
                case 0b10000000: return BYTE;
                case 0b10000001: return INT;
                case 0b10000010: return LONG;
                case 0b10000011: return SHORT;
                case 0b10000100: return BOOLEAN;
                case 0b10000101: return INT_ARRAY;
                case 0b10000110: return BYTE_ARRAY; 
                default:
                    return null;
            }
        }
    }
    
    public static void writeShort(int value, OutputStream out) throws IOException 
    {
        Tag.SHORT.write( out );
        writeShortNoTag(value,out);
    }
    
    static void writeShortNoTag(int value, OutputStream out) throws IOException {
        out.write( (value >>  8) & 0xff );
        out.write( (value      ) & 0xff );
    }  
    
    public static short readShort(InputStream in) throws IOException {
        Tag.SHORT.read(in);
        return readShortNoTag(in);
    }
    
    static short readShortNoTag(InputStream in) throws IOException {
        final int hi = readByteNoTag( in ) & 0xff;
        final int lo = readByteNoTag( in ) & 0xff;
        return (short) (hi << 8 | lo);
    }     
    
    public static void writeBoolean(boolean value,OutputStream out) throws IOException 
    {
        Tag.BOOLEAN.write(out);
        writeBooleanNoTag(value,out);
    }
    
    static void writeBooleanNoTag(boolean value,OutputStream out) throws IOException 
    {
        out.write( value ? 0xff : 0 );
    }    
    
    public static boolean readBoolean(InputStream in) throws IOException 
    {
        Tag.BOOLEAN.read(in);
        return readBooleanNoTag(in);
    }    
    
    static boolean readBooleanNoTag(InputStream in) throws IOException 
    {
        return readByteNoTag( in ) != 0;
    }  
    
    public static void writeByte(int value,OutputStream out) throws IOException 
    {
        Tag.BYTE.write(out);
        writeByteNoTag(value,out);
    }
    
    static void writeByteNoTag(int value,OutputStream out) throws IOException 
    {
        out.write( value );
    }
    
    public static int readByte(InputStream in) throws IOException {
        Tag.BYTE.read(in);
        return readByteNoTag(in);
    }
    
    static int readByteNoTag(InputStream in) throws IOException {
        final int value = in.read();
        if ( value == -1 ) {
            throw new EOFException("Premature end of file");
        }
        return value & 0xff;
    }
    
    public static void writeLong(long value, OutputStream out) throws IOException {
        Tag.LONG.write(out);
        writeLongNoTag(value, out);
    }
    
    static void writeLongNoTag(long value, OutputStream out) throws IOException 
    {
        writeIntNoTag( (int) (( value >> 32) & 0xffffffff) , out );
        writeIntNoTag( (int) (value & 0xffffffff) , out );
    }
    
    public static long readLong(InputStream in) throws IOException {
        Tag.LONG.read(in);
        return readLongNoTag(in);
    }
    
    static long readLongNoTag(InputStream in) throws IOException 
    {
        final long hi = readIntNoTag( in ) & 0xffffffff;
        final long lo = readIntNoTag( in ) & 0xffffffff;
        return hi << 32 | lo;
    }    
    
    public static void writeIntArray(int[] array, OutputStream out) throws IOException {
        Tag.INT_ARRAY.write(out);
        writeIntArrayNoTag(array, out);
    }
    
    static void writeIntArrayNoTag(int[] array, OutputStream out) throws IOException {
        writeIntNoTag( array.length , out );
        for (  int v : array ) {
            writeIntNoTag( v , out );
        }
    }
    
    public static int[] readIntArray(InputStream in) throws IOException {
        Tag.INT_ARRAY.read(in);
        return readIntArrayNoTag(in);
    }
    
    static int[] readIntArrayNoTag(InputStream in) throws IOException {
        final int len = readIntNoTag( in );
        final int[] result = new int[len];
        for ( int i = 0 ; i < len ; i++ ) {
            result[i] = readByteNoTag(in);
        }
        return result;
    }
    
    public static void populateIntArray(int[] arrayToFill, InputStream in) throws IOException {
        Tag.INT_ARRAY.read(in);
        populateIntArrayNoTag(arrayToFill, in);
    }
    
    static void populateIntArrayNoTag(int[] arrayToFill, InputStream in) throws IOException {
        final int len = readIntNoTag( in );
        if ( len != arrayToFill.length ) {
            throw new IllegalArgumentException("Input array has size "+arrayToFill.length+" but de-serialized array has size "+len);
        }
        for ( int i = 0 ; i < len ; i++ ) {
            arrayToFill[i] = readIntNoTag(in);
        }
    }
    
    public static void writeInt(int value, OutputStream out) throws IOException {
        Tag.INT.write(out);
        writeIntNoTag(value,out);
    }
    
    static void writeIntNoTag(int value, OutputStream out) throws IOException 
    {
        writeShortNoTag( (short) ((value >> 16) & 0xffff), out );
        writeShortNoTag( (short) (value & 0xffff), out );
    }
    
    public static int readInt(InputStream in) throws IOException {
        Tag.INT.read(in);
        return readIntNoTag(in);
    }
    
    static int readIntNoTag(InputStream in) throws IOException 
    {
        final int hi = readShortNoTag( in ) & 0xffff; 
        final int lo = readShortNoTag( in ) & 0xffff;
        return hi << 16 | lo;
    }
}
