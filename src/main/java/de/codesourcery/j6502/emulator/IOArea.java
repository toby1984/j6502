package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.emulator.Keyboard.Key;

/**
 * I/O area , memory bank 5 ($D000 - $DFFF).
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class IOArea extends Memory
{
	public static final boolean VIC_PAL_MODE = true;

	private final VIC vic;
	private final CIA cia1 = new CIA("CIA #1" , AddressRange.range( 0xdc00, 0xdd00 ) ); // actually only 16 byte registers but mirrored in range $DC10-$DCFF
	private final CIA cia2 = new CIA("CIA #2" , AddressRange.range( 0xdd00, 0xde00 ) ); // actually only 16 byte registers but mirrored in range $DD10-$DDFF

	private final IMemoryRegion mainMemory;

	public IOArea(String identifier, AddressRange range, IMemoryRegion mainMemory)
	{
		super(identifier, range);
		this.mainMemory = mainMemory;
		this.vic = new VIC(mainMemory);
	}

	@Override
	public void writeByte(int set, byte value)
	{
		/*
// actually only 16 byte registers but mirrored in range $DC10-$DCFF
// actually only 16 byte registers but mirrored in range $DD10-$DDFF
		 */
		final int offset = set & 0xffff;

		// I/O area starts at 0xd000
		// but input to writeByte is already translated by -d000

		if ( offset >= 0xc00 && offset <= 0xcff) { // $DC00-$DCFF
			cia1.writeByte( set , value );
			return;
		}
		if ( offset >= 0xd00 && offset <= 0xdff ) { // $DD10-$DDFF
			cia2.writeByte( set , value );
			return;
		}

		if ( offset == VIC.VIC_SCANLINE )
		{
			// current scan line, lo-byte
			vic.irqOnRaster = (short) (vic.irqOnRaster | (value & 0xff) );
		}
		else if ( offset == VIC.VIC_CNTRL1 )
		{
			// current scan line, hi-byte
			if ( ( value & 0b1000_0000 ) != 0 ) {
				vic.irqOnRaster = (short) ( 0b0100 | (vic.irqOnRaster & 0xff) ); // set hi-bit
			} else {
				vic.irqOnRaster = (short) (vic.irqOnRaster & 0xff); // clear hi-bit
			}
		}
		super.writeByte(offset, value);
	}

	@Override
	public int readByte(int adr)
	{
		// I/O area starts at 0xd000
		// but input to writeByte is already translated by -d000
		final int offset = adr & 0xffff;
		if ( offset >= 0xc00 && offset <= 0xcff) { // $DC00-$DCFF
			return cia1.readByte( offset );
		}
		if ( offset >= 0xd00 && offset <= 0xdff ) { // $DD10-$DDFF
			return cia2.readByte( offset );
		}
		return super.readByte(adr);
	}

	@Override
	public void reset() {
		super.reset();
		vic.reset();
		cia1.reset();
		cia2.reset();
	}

	public void tick(CPU cpu)
	{
		cia1.tick( cpu );
		cia2.tick(cpu );
		vic.tick( cpu );
	}

	public CIA getCIA1() {
		return cia1;
	}
}