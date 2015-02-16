package de.codesourcery.j6502.emulator;

public class Memory extends IMemoryRegion
{
	private final byte[] data;

	public Memory(String identifier, AddressRange range) {
		super(identifier, range);
		this.data = new byte[ range.getSizeInBytes() ];
	}

	@Override
	public byte readByte(int offset) {
		return data[offset];
	}

	@Override
	public void writeByte(int offset, byte value) {
		data[offset]=value;
	}

	@Override
	public void reset()
	{
		for ( int i = 0 , len = data.length ; i < len ; i++ ) {
			data[i] = 0;
		}
	}

	@Override
	public void writeWord(int offset, short value) {
		final byte low = (byte) value;
		final byte hi = (byte) (value>>8);
		data[offset] = low;
		data[offset+1] = hi;
	}

	@Override
	public short readWord(int offset)
	{
		final byte low = data[offset];
		final byte hi = data[offset+1];
		return (short) (hi<<8|low);
	}

	@Override
	public void bulkWrite(int startingAddress, byte[] data, int datapos, int len)
	{
		for ( int dstOffset = startingAddress , bytesLeft = len , srcOffset = datapos ; bytesLeft > 0 ; bytesLeft-- ) {
			this.data[ dstOffset++ ] = data[ srcOffset++ ];
		}
	}
}