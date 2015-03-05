package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.emulator.Keyboard.Key;

/**
 * I/O area , memory bank 5 ($D000 - $DFFF).
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class IOArea extends Memory
{
	private final VIC vic;
	private final CIA cia1 = new CIA("CIA #1" , AddressRange.range( 0xdc00, 0xdd00 ) ) {

		@Override
		public int readByte(int adr)
		{
			final int offset = ( adr & 0xffff ) % 0x10; // registers are mirrored/repeated every 16 bytes
			if ( offset == CIA1_PRB )
			{
				// rowBit / PRB , columnBit / PRA )
				int pra = super.readByte( CIA1_PRA ); // figure out which keyboard column to read (columns to be read have a 0 bit in here)
				int result2 = 0xff; // keyboard lines are low-active, so 0xff = no key pressed
				for ( int col = 0 ; col < 8 ; col++ ) {
					if ( ( pra & (1<< col ) ) == 0 ) { // got column (bit is low-active)
						int tmp = keyboardColumns[col];
						if ( tmp != 0xff ) // there's a bit set on this column
						{
							result2 &= tmp;
//								System.out.println("Read: keyboard column "+col+" on "+this+" = "+HexDump.toBinaryString( (byte) result2 ));
						}
					}
				}
				return result2;
			}
			return super.readByte( offset );
		}
	};

	private final CIA cia2 = new CIA("CIA #2" , AddressRange.range( 0xdd00, 0xde00 ) ) {
	};

	private final IMemoryRegion mainMemory;

	public IOArea(String identifier, AddressRange range, IMemoryRegion mainMemory)
	{
		super(identifier, range);
		this.mainMemory = mainMemory;
		this.vic = new VIC("VIC", AddressRange.range( 0xd000, 0xd02f));
	}

	private final int[] keyboardColumns = new int[] {0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff}; // 8 keyboard columns, bits are low-active

	public void keyPressed(Key key)
	{
		System.out.println("PRESSED: "+key);
		keyboardColumns[ key.colBitNo ] &= ~(1 << key.rowBitNo); // bits are low-active so clear bit if key is pressed
		if ( key.clearShift() ) {
			keyReleased(Key.KEY_LEFT_SHIFT);
			keyReleased(Key.KEY_RIGHT_SHIFT);
		} else if ( key.fakeLeftShift() ) {
			keyPressed(Key.KEY_LEFT_SHIFT);
		}
	}

	public void keyReleased(Key key)
	{
		System.out.println("RELEASED: "+key);
		keyboardColumns[ key.colBitNo ] |= (1 << key.rowBitNo);	 // bits are low-active so set bit if key is released
		if ( key.fakeLeftShift() ) {
			keyReleased(Key.KEY_LEFT_SHIFT);
		}
	}

	@Override
	public void writeByte(int set, byte value)
	{
		/*
// CIA #1: actually only 16 byte registers but mirrored in range $DC10-$DCFF
// CIA #2: actually only 16 byte registers but mirrored in range $DD10-$DDFF
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
		if ( offset < 0x002f)  // $D000 - $D02F ... VIC
		{
			vic.writeByte( set , value );
			return;
		}
		super.writeByte(offset, value);
	}

	@Override
	public int readByte(int adr)
	{
		// I/O area starts at 0xd000
		// but input to writeByte is already translated by -d000
		final int offset = adr & 0xffff;
		if ( offset >= 0xc00 && offset <= 0xcff) { // CIA #1 $DC00-$DCFF
			return cia1.readByte( offset );
		}
		if ( offset >= 0xd00 && offset <= 0xdff ) { // CIA #2 $DD10-$DDFF
			return cia2.readByte( offset );
		}
		if ( offset < 0x002f ) { // $D000 - $D02F ... VIC
			return vic.readByte( adr );
		}
		return super.readByte(adr);
	}

	@Override
	public void reset() {
		super.reset();

		for ( int i = 0 ; i < keyboardColumns.length ; i++ ) {
			keyboardColumns[i] = 0xff;
		}

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

	public VIC getVIC() {
		return vic;
	}
}