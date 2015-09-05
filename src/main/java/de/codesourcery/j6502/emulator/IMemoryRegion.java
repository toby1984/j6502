package de.codesourcery.j6502.emulator;

public abstract class IMemoryRegion
{
	private final String identifier;
	private final AddressRange addressRange;

	public IMemoryRegion(String identifier,AddressRange range)
	{
		this.identifier = identifier;
		this.addressRange = range;
	}

	public abstract void reset();

	public String getIdentifier() {
		return identifier;
	}

	public AddressRange getAddressRange() {
		return addressRange;
	}

	public void bulkRead(int startingAddress,final byte[] inputBuffer, final int len)
	{
		for ( int i = 0 ; i < len ; i++ , startingAddress++ )
		{
			inputBuffer[i] = (byte) readByte( startingAddress & 0xffff );
		}
	}

	public abstract void bulkWrite(int startingAddress,byte[] data, int datapos, int len);

	public abstract int readByte(int offset);

	public abstract int readWord(int offset);

	public abstract void writeWord(int offset,short value);

	public abstract void writeByte(int offset,byte value);

	public abstract String dump(int offset, int len);

	public abstract boolean isReadsReturnWrites(int offset);

	/**
	 * Special method that first performs a read of the given location
	 * and then immediately writes back the value it just read.
	 *
	 * <p>This method is used to fake a peculiar behavior of the 6510 CPU where
	 * read-modify-write operations like DEC,INC,ASL etc. actually write-back the value
	 * they just read. This in turn gets abused a lot to reset latch registers like $D019 (VIC irq bits).</p>
	 *
	 * @param offset
	 * @return
	 */
	public abstract int readAndWriteByte(int offset);
	
	/**
	 * Reads a byte without triggering side-effects related to
	 * memory-mapped I/O (like clearing IRQs etc.).
	 *  
	 * @param offset
	 * @return
	 */
	public abstract int readByteNoSideEffects(int offset);

	@Override
	public String toString() {
		return addressRange+" - "+identifier;
	}
}
