package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.Constants;
import de.codesourcery.j6502.utils.HexDump;

public class Memory extends IMemoryRegion
{
	private final byte[] data;

	public Memory(String identifier, MemoryType type, AddressRange range) {
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
			data[i] = 0;
		}
	}

	@Override
	public boolean isReadsReturnWrites(int offset) {
	    return true;
	}

	@Override
	public int readByte(int offset) {
	    if ( Constants.MEMORY_SUPPORT_BREAKPOINTS ) {
	        getBreakpointsContainer().read( offset );
	    }
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
	public void writeWord(int offset, short value)
	{
		final byte low = (byte) value;
		final byte hi = (byte) (value>>8);

		final int realOffsetLo = offset & 0xffff;
		final int realOffsetHi = (realOffsetLo+1) & 0xffff;
		data[ realOffsetLo ] = low;
		data[ realOffsetHi ] = hi;
        if ( Constants.MEMORY_SUPPORT_BREAKPOINTS ) {
            getBreakpointsContainer().write( realOffsetLo );
            getBreakpointsContainer().write( realOffsetHi );
        }
	}

	@Override
	public int readWord(int offset)
	{
		int realOffset = offset & 0xffff;
		final int low = data[realOffset] & 0xff;
		realOffset = (realOffset+1) & 0xffff;
		final int hi = data[realOffset] & 0xff;
		return (hi<<8|low);
	}

	@Override
	public void bulkWrite(int startingAddress, byte[] data, int datapos, int len)
	{
		final int realOffset = startingAddress & 0xffff;
		final int size = getAddressRange().getSizeInBytes();
		for ( int dstOffset = realOffset , bytesLeft = len , srcOffset = datapos ; bytesLeft > 0 ; bytesLeft-- )
		{
			this.data[ dstOffset ] = data[ srcOffset++ ];
			dstOffset = (dstOffset+1) % size;
		}
	}

    @Override
    public int readByteNoSideEffects(int offset) 
    {
        if ( Constants.MEMORY_SUPPORT_BREAKPOINTS ) {
            getBreakpointsContainer().read( offset );
        }
                
        return data[offset & 0xffff] & 0xff;
    }
}