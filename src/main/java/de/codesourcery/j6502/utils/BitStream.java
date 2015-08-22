package de.codesourcery.j6502.utils;

import java.util.NoSuchElementException;

public final class BitStream 
{
    private final byte[] data;
    
    private int startByteOffset;
    private int bitsAvailable;
    
    private int currentBit;
    
    private int wrapCounter;
    private int mark;
    
    public BitStream(byte[] data) {
        this.data = data;
        setStartingOffset( 0 , data.length * 8 );
    }
    
    @Override
    public String toString() 
    {
        final int offset = startByteOffset + currentBit/8;
        final int bitInByte = 7 - ( currentBit % 8 );
        return "BitStream [ startByteOffset: "+startByteOffset+" , currentBit: "+currentBit+" ( "+offset+" , bit "+bitInByte+") , bitsAvailable: "+bitsAvailable+", wrapCount: "+wrapCounter+" ]";
    }
    
    public void mark() 
    {
        mark = currentBit;
        wrapCounter = 0;
    }
    
    public boolean hasWrapped() {
        return wrapCounter > 0;
    }
    
    public void setStartingOffset(int byteOffset,int bitsAvailable) 
    {
        if ( bitsAvailable == 0 ) {
            throw new RuntimeException("At least one bit needs to be available");
        }
        this.startByteOffset = byteOffset;
        this.bitsAvailable = bitsAvailable;
        this.currentBit = 0;
        mark();
    }
    
    public int readByte() {
        int value = readBit();
        for ( int i = 0 ; i < 7 ; i++ ) {
            value <<= 1;
            value |= readBit();
        }
        return value;
    }
    
    public void rewind(int bits)
    {
        for ( int i = 0 ; i < bits ; i++ ) 
        {
            if ( currentBit == mark && wrapCounter > 0 ) {
                wrapCounter--;
            }            
            currentBit --;
            if ( currentBit < 0 ) 
            {
                currentBit = bitsAvailable - 1;
            }
        }
    }
    
    public int readBit() throws NoSuchElementException
    {
        final int byteOffset = currentBit/8;
        final int currentMask = 1 << ( 7 - ( currentBit % 8 ) );
        final int result = (data[ startByteOffset + byteOffset] & currentMask) == 0 ? 0 : 1;
        currentBit++;
        if ( currentBit == bitsAvailable ) 
        {
            this.currentBit = 0;            
        }
        if ( currentBit == mark ) {
            wrapCounter++;
        }        
        return result;
    }
}