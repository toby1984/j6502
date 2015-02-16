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

	public abstract void bulkWrite(int startingAddress,byte[] data, int datapos, int len);

	public abstract byte readByte(int offset);

	public abstract short readWord(int offset);

	public abstract void writeWord(int offset,short value);

	public abstract void writeByte(int offset,byte value);

	@Override
	public String toString() {
		return addressRange+" - "+identifier;
	}
}
