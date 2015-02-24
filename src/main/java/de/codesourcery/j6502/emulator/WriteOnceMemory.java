package de.codesourcery.j6502.emulator;

public final class WriteOnceMemory extends Memory {

	private boolean writeProtected = false;

	public WriteOnceMemory(String identifier, AddressRange range) {
		super(identifier, range);
	}

	@Override
	public void writeByte(int offset, byte value)
	{
		if ( writeProtected ) {
			throw new UnsupportedOperationException("Can't write to write-protected memory  "+this);
		}
		super.writeByte(offset, value);
	}

	public void writeProtect() {
		this.writeProtected = true;
	}

	@Override
	public void bulkWrite(int startingAddress, byte[] data, int datapos,int len)
	{
		if ( writeProtected ) {
			throw new UnsupportedOperationException("Can't write to write-protected memory  "+this);
		}
		super.bulkWrite(startingAddress, data, datapos, len);
	}
}
