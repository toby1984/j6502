package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.utils.HexDump;

public class Memory extends IMemoryRegion
{
	private final byte[] data;

	public Memory(String identifier, AddressRange range) {
		super(identifier, range);
		this.data = new byte[ range.getSizeInBytes() ];
	}

	@Override
	public String dump(int offset, int len)
	{
		return HexDump.INSTANCE.dump( (short) (getAddressRange().getStartAddress()+offset),this,offset,len);
	}

	@Override
	public byte readByte(short offset) {
		return data[offset & 0xffff];
	}

	@Override
	public void writeByte(short offset, byte value) {
		data[offset & 0xffff]=value;
	}

	@Override
	public void reset()
	{
		System.out.println("reset(): Clearing "+this);
		new Exception().printStackTrace();
		for ( int i = 0 , len = data.length ; i < len ; i++ ) {
			data[i] = 0;
		}
	}

	@Override
	public void writeWord(short offset, short value) {
		final byte low = (byte) value;
		final byte hi = (byte) (value>>8);

		int realOffset = offset & 0xffff;
		data[ realOffset ] = low;
		realOffset = (realOffset+1) & 0xffff;
		data[ realOffset ] = hi;
	}

	@Override
	public short readWord(short offset)
	{
		int realOffset = offset & 0xffff;
		final byte low = data[realOffset];
		realOffset = (realOffset+1) & 0xffff;
		final byte hi = data[realOffset];
		return (short) (hi<<8|low);
	}

	@Override
	public void bulkWrite(short startingAddress, byte[] data, int datapos, int len)
	{
		final int realOffset = startingAddress & 0xffff;
		final int size = getAddressRange().getSizeInBytes();
		for ( int dstOffset = realOffset , bytesLeft = len , srcOffset = datapos ; bytesLeft > 0 ; bytesLeft-- )
		{
			this.data[ dstOffset ] = data[ srcOffset++ ];
			dstOffset = (dstOffset+1) % size;
		}
	}
}