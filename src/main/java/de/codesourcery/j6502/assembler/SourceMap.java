package de.codesourcery.j6502.assembler;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import de.codesourcery.j6502.emulator.AddressRange;

public class SourceMap
{
	private final List<RangeWithLine> ranges = new ArrayList<>();

	protected static final class RangeWithLine
	{
		public final AddressRange range;
		public final int lineNo;

		public RangeWithLine(AddressRange range, int lineNo) {
			this.range = range;
			this.lineNo = lineNo;
		}

		@Override
		public String toString() {
			return "Line no. "+lineNo+" is at "+range;
		}
	}

	public void clear() {
		ranges.clear();
	}

	public void addAddressRange(short start,int len, int lineNo)
	{
		ranges.add( new RangeWithLine( new AddressRange(start,len) , lineNo ) );
	}

	public Optional<Integer> getLineNumberForAddress(int adr)
	{
		for ( RangeWithLine r : ranges ) {
			if ( r.range.contains( adr ) ) {
				return Optional.of( r.lineNo );
			}
		}
		return Optional.empty();
	}
}
