package de.codesourcery.j6502.emulator;

public final class WriteOnceMemory extends Memory {

	private boolean writeProtected = false;

	public WriteOnceMemory(String identifier, AddressRange range) {
		super(identifier, MemoryType.ROM, range);
	}

	@Override
	public void writeByte(int offset, byte value)
	{
		if ( ! writeProtected ) {
		    super.writeByte(offset, value);
		}
	}

	@Override
	public void reset()
	{
		if ( ! writeProtected ) {
			super.reset();
		}
	}

	public void writeProtect() {
		this.writeProtected = true;
	}

	public boolean isWriteProtected() {
        return writeProtected;
    }
}
