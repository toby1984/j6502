package de.codesourcery.j6502.emulator;

public final class AddressRange {

	private final int startAddress;
	private final int endAddress;
	private final int sizeInBytes;

	public AddressRange(int startAddress, int sizeInBytes)
	{
		if ( startAddress < 0 || startAddress > 65535 ) {
			throw new IllegalArgumentException("Address out-of-range: "+startAddress);
		}
		if ( sizeInBytes  <  0 || sizeInBytes > 65536 ) {
			throw new IllegalArgumentException("Size out-of-range: "+sizeInBytes);
		}
		this.startAddress = startAddress;
		this.sizeInBytes = sizeInBytes;
		this.endAddress = startAddress+sizeInBytes;

		if ( endAddress > 65536 ) {
			throw new IllegalArgumentException("End address out-of-range: "+(startAddress+sizeInBytes));
		}
	}

	public static AddressRange range(int start,int end) {
		if ( end < start ) {
			throw new IllegalArgumentException("end < start ?");
		}
		if ( start < 0 || start > 65535 ) {
			throw new IllegalArgumentException("start out-of-range: "+start);
		}
		if ( end < 0 || end > 65535 ) {
			throw new IllegalArgumentException("end out-of-range: "+end);
		}
		return new AddressRange( start , (end - start)+1 );
	}

	public boolean contains(AddressRange other)
	{
		return this.startAddress <= other.startAddress && other.endAddress <= this.endAddress;
	}

	@Override
	public int hashCode() {
		final int result = 31  + sizeInBytes;
		return 31 * result + startAddress;
	}

	@Override
	public boolean equals(Object obj)
	{
		if (this instanceof AddressRange) {
			final AddressRange  other = (AddressRange) obj;
			return this.startAddress == other.startAddress && this.sizeInBytes == other.sizeInBytes;
		}
		return false;
	}

	public int getStartAddress() {
		return startAddress;
	}

	public int getSizeInBytes() {
		return sizeInBytes;
	}

	public int getEndAddress() {
		return endAddress;
	}

	@Override
	public String toString()
	{
		return "$"+Integer.toString( startAddress, 16)+" - "+ "$"+Integer.toString( endAddress , 16 )+" ("+sizeInBytes+" bytes)";
	}
}
