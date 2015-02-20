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

	public abstract void bulkWrite(short startingAddress,byte[] data, int datapos, int len);

	public abstract byte readByte(short offset);

	public abstract short readWord(short offset);

	public abstract void writeWord(short offset,short value);

	public abstract void writeByte(short offset,byte value);

	public abstract String dump(int offset, int len);

	@Override
	public String toString() {
		return addressRange+" - "+identifier;
	}
}
