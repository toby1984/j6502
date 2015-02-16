package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class MemorySubsystem extends IMemoryRegion {

	public static final String RAM_IDENTIFIER = "RAM";

	private final List<IMemoryRegion> regions = new ArrayList<>();

	private final IMemoryRegion[] memoryMap = new IMemoryRegion[65536];

	public MemorySubsystem() {
		super("main memory" , new AddressRange(0,65536 ) );
		reset();
	}

	@Override
	public void reset()
	{
		regions.clear();
		mapRegion( new Memory( RAM_IDENTIFIER ,new AddressRange(0,65536 ) ) );
	}

	public void mapRegion(IMemoryRegion mem)
	{
		regions.add( mem );
		for ( int adr = mem.getAddressRange().getStartAddress() , len = mem.getAddressRange().getSizeInBytes() ; len > 0 ; len-- ) {
			memoryMap[adr++]=mem;
		}
	}

	@Override
	public byte readByte(int offset)
	{
		final IMemoryRegion region = memoryMap[offset];
		final int realOffset = offset - region.getAddressRange().getStartAddress();
		return region.readByte( realOffset );
	}

	@Override
	public void writeWord(int offset,short value)
	{
		final byte low = (byte) value;
		final byte hi = (byte) (value>>8);

		IMemoryRegion region = memoryMap[offset];
		int realOffset = offset - region.getAddressRange().getStartAddress();
		region.writeByte( realOffset , low );

		region = memoryMap[offset+1];
		realOffset = offset - region.getAddressRange().getStartAddress();
		region.writeByte( realOffset , hi );
	}

	@Override
	public short readWord(int offset)
	{
		final byte low = readByte(offset);
		final byte hi = readByte(offset+1);
		return (short) (hi<<8|low);
	}

	@Override
	public void writeByte(int offset, byte value)
	{
		final IMemoryRegion region = memoryMap[offset];
		final int realOffset = offset - region.getAddressRange().getStartAddress();
		region.writeByte( realOffset , value );
	}

	@Override
	public void bulkWrite(int startingAddress, byte[] data, int datapos, int len)
	{
		final AddressRange range = new AddressRange( startingAddress , len );
		for ( final IMemoryRegion r : regions )
		{
			if ( r.getAddressRange().contains( range ) )
			{
				// fast path, write does not cross region boundaries
				final int realOffset = startingAddress - r.getAddressRange().getStartAddress();
				r.bulkWrite( realOffset , data , datapos ,len );
				return;
			}
		}

		// slow path, memory write across region boundaries
		// (TODO: Speed of this could be improved greatly by segmenting the writes according to the configured memory regions instead of doing byte-wise copying)
		for ( int dstAdr = startingAddress , bytesLeft = len , src = datapos ; bytesLeft > 0 ; bytesLeft-- ) {
			writeByte( dstAdr++ , data[ src++ ] );
		}
	}

	@Override
	public String toString()
	{
		final StringBuilder buffer = new StringBuilder("=== Memory layout ===\n");
		for (final Iterator<IMemoryRegion> it = regions.iterator(); it.hasNext();) {
			final IMemoryRegion r = it.next();
			buffer.append( r );
			if ( it.hasNext() ) {
				buffer.append("\n");
			}
		}
		return buffer.toString();
	}
}