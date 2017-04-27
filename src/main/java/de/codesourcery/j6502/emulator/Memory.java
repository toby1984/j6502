package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.Constants;
import de.codesourcery.j6502.utils.HexDump;

/**
 * Memory that implements all operations in terms of {@link #readByte(int)} and {@link #writeByte(int, byte)}
 * instead of directly accessing the internal array.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Memory extends IMemoryRegion
{
    private final byte[] data;

    public Memory(String identifier, MemoryType type,AddressRange range) {
        super(identifier, type , range);
        this.data = new byte[ range.getSizeInBytes() ];
    }

    @Override
    public String dump(int offset, int len)
    {
        return HexDump.INSTANCE.dump( (short) (getAddressRange().getStartAddress()+offset),this,offset,len);
    }
    
    @Override
    public void reset()
    {
        for ( int i = 0 , len = data.length ; i < len ; i++ )
        {
            writeByteNoSideEffects(i,(byte) 0);
        }
    }

    @Override
    public int readByte(int offset) {
        if ( Constants.MEMORY_SUPPORT_BREAKPOINTS ) {
            getBreakpointsContainer().read( offset );
        }
        return data[offset & 0xffff] & 0xff;
    }

    @Override
    public int readByteNoSideEffects(int offset) {
        return data[offset & 0xffff] & 0xff;
    }	

    @Override
    public void writeByte(int offset, byte value) {
        if ( Constants.MEMORY_SUPPORT_BREAKPOINTS ) {
            getBreakpointsContainer().write( offset );
        }
        data[offset & 0xffff]=value;
    }
    
    @Override
    public void writeByteNoSideEffects(int offset, byte value) {
        data[offset & 0xffff]=value;
    }

    @Override
    public final void writeWord(int offset, short value) {
        final byte low = (byte) value;
        final byte hi = (byte) (value>>8);

        final int realOffsetLo = offset & 0xffff;
        final int realOffsetHi = (realOffsetLo+1) & 0xffff;
        writeByte( realOffsetLo, low );
        writeByte( realOffsetHi , hi );
    }

    @Override
    public final int readWord(int offset)
    {
        final int realOffsetLo = offset & 0xffff;
        final int realOffsetHi = (realOffsetLo+1) & 0xffff;
        
        final int low = readByte( realOffsetLo );
        final int hi = readByte( realOffsetHi );
        return (hi<<8|low) & 0xffff;
    }

    @Override
    public void bulkWrite(int startingAddress, byte[] data, int datapos, int len)
    {
        final int realOffset = startingAddress & 0xffff;
        final int size = getAddressRange().getSizeInBytes();
        for ( int dstOffset = realOffset , bytesLeft = len , srcOffset = datapos ; bytesLeft > 0 ; bytesLeft-- )
        {
            writeByte( dstOffset , data[ srcOffset++ ] );
            dstOffset = (dstOffset+1) % size;
        }
    }
}