package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.utils.HexDump;

/**
 * Memory that implements all operations in terms of {@link #readByte(int)} and {@link #writeByte(int, byte)}
 * instead of directly accessing the internal array.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class SlowMemory extends IMemoryRegion
{
	private final byte[] data;

	public SlowMemory(String identifier, MemoryType type,AddressRange range) {
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
			writeByte(i,(byte) 0);
		}
	}

    @Override
	public boolean isReadsReturnWrites(int offset) {
        return true;
    }

	@Override
	public int readByte(int offset) {
		return data[offset & 0xffff] & 0xff;
	}
	
    @Override
    public int readByteNoSideEffects(int offset) {
        return data[offset & 0xffff] & 0xff;
    }	
    
	@Override
	public void writeByte(int offset, byte value) {
		data[offset & 0xffff]=value;
	}

	@Override
	public final void writeWord(int offset, short value) {
		final byte low = (byte) value;
		final byte hi = (byte) (value>>8);

		int realOffset = offset & 0xffff;
		writeByte( realOffset, low );
		realOffset = (realOffset+1) & 0xffff;
		writeByte( realOffset , hi );
	}

	@Override
	public final int readWord(int offset)
	{
		int realOffset = offset & 0xffff;
		final byte low = (byte) readByte( realOffset );
		realOffset = (realOffset+1) & 0xffff;
		final byte hi = (byte) readByte( realOffset );
		return (hi<<8|low) & 0xffff;
	}

	@Override
	public final void bulkWrite(int startingAddress, byte[] data, int datapos, int len)
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