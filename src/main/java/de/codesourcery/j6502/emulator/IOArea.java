package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.emulator.IECBus.Wire;
import de.codesourcery.j6502.emulator.Keyboard.Key;
import de.codesourcery.j6502.utils.HexDump;

/**
 * I/O area , memory bank 5 ($D000 - $DFFF).
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class IOArea extends Memory
{
	private final VIC vic;
	private final IECBus iecBus;

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

	private final CIA cia2 = new CIA("CIA #2" , AddressRange.range( 0xdd00, 0xde00 ) )
	{
		@Override
		public void writeByte(int adr, byte value)
		{
			super.writeByte(adr, value);
			final int offset = ( adr & 0xffff ) % 0x10; // registers are mirrored/repeated every 16 bytes
			if (offset == CIA1_PRA )
			{
				/*
				 Bit 0..1: Select the position of the VIC-memory

				     %00, 0: Bank 3: $C000-$FFFF, 49152-65535
				     %01, 1: Bank 2: $8000-$BFFF, 32768-49151
				     %10, 2: Bank 1: $4000-$7FFF, 16384-32767
				     %11, 3: Bank 0: $0000-$3FFF, 0-16383 (standard)

				Bit 2: RS-232: TXD Output, userport: Data PA 2 (pin M)
				Bit 3..5: serial bus Output (0=High/Inactive, 1=Low/Active)

				    Bit 3: ATN OUT
				    Bit 4: CLOCK OUT
				    Bit 5: DATA OUT

				Bit 6..7: serial bus Input (0=Low/Active, 1=High/Inactive)

				    Bit 6: CLOCK IN
				    Bit 7: DATA IN
				 */
				
				// all lines are low-active, 0 = LOGICAL TRUE , 1 = LOGICAL FALSE
				final Wire outWire = iecBus.getOutputWire();
				final Wire inWire = iecBus.getInputWire();
				
				final boolean atn = outWire.getATN();
				final boolean clockOut = outWire.getClock();
				final boolean dataOut = outWire.getData();
				
				final boolean clockIn = inWire.getClock();
				final boolean dataIn= inWire.getData();				
				
				final int mask = super.readByte( CIA1_DDRA );
//				System.out.println("Write to $DD00 with mask: "+HexDump.toBinaryString( (byte) mask ) );
				int currentValue = (atn ? 1<<3 : 0 ) | ( clockOut ? 0 : 1<<4) | (dataOut ? 0 : 1<<5) |
						                               ( clockIn  ? 0 : 1<<6) | (dataIn  ? 0 : 1<<7);
				currentValue = currentValue & ~mask;

				int newValue = (value & 0xff) & mask;
				newValue = newValue | currentValue;
				
				boolean newAtn = (newValue & 1<<3) != 0;
				boolean newClockOut = (newValue & 1 << 4) == 0;
				boolean newDataOut = (newValue & 1<<5) == 0;
				
				boolean newClockIn = (newValue & 1 << 6) == 0;
				boolean newDataIn = (newValue & 1<<7) == 0;
				
				outWire.setState( newAtn , newDataOut , newClockOut );
				inWire.setState( false , newDataIn , newClockIn );
			}
		}
		
		@Override
		public int readByte(int adr)
		{
			int value = super.readByte(adr);
			final int offset = ( adr & 0xffff ) % 0x10; // registers are mirrored/repeated every 16 bytes
			if ( offset == 0 ) {
				/*
				 Bit 0..1: Select the position of the VIC-memory

				     %00, 0: Bank 3: $C000-$FFFF, 49152-65535
				     %01, 1: Bank 2: $8000-$BFFF, 32768-49151
				     %10, 2: Bank 1: $4000-$7FFF, 16384-32767
				     %11, 3: Bank 0: $0000-$3FFF, 0-16383 (standard)

				Bit 2: RS-232: TXD Output, userport: Data PA 2 (pin M)
				Bit 3..5: serial bus Output (0=High/Inactive, 1=Low/Active)

				    Bit 3: ATN OUT
				    Bit 4: CLOCK OUT
				    Bit 5: DATA OUT

				Bit 6..7: serial bus Input (0=Low/Active, 1=High/Inactive)

				    Bit 6: CLOCK IN
				    Bit 7: DATA IN
				 */
				final Wire outWire = iecBus.getOutputWire();
				final boolean atn = outWire.getATN();
				final boolean clockOut = outWire.getClock(); 
				final boolean dataOut = outWire.getData();
				
				final Wire wire = iecBus.getInputWire();
				final boolean clockIn = wire.getClock();
				final boolean dataIn = wire.getData();
				
				final int busRegister = ( dataIn ? 0 : 1<<7 ) | (clockIn ? 0 : 1<<6 ) |
						                ( atn ? 1<<3 : 0 ) | ( clockOut ? 0 : 1<<4)   | (dataOut ? 0 : 1<<5);
				
				value = (value & 0b0000_0111 ) | busRegister; // merge with bits
			}
			return value;
		};
	};

	private final IMemoryRegion mainMemory;

	public IOArea(String identifier, AddressRange range, IMemoryRegion mainMemory)
	{
		super(identifier, range);
		
		this.iecBus = new IECBus("default bus");
		iecBus.addDevice( new Floppy(8) );
		this.mainMemory = mainMemory;
		this.vic = new VIC("VIC", AddressRange.range( 0xd000, 0xd02f));
	}

	private final int[] keyboardColumns = new int[] {0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff}; // 8 keyboard columns, bits are low-active

	public void keyPressed(Key key)
	{
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
		iecBus.reset();
	}

	public void tick(CPU cpu)
	{
		cia1.tick( cpu );
		cia2.tick(cpu );
		vic.tick( cpu );
		iecBus.tick();
	}

	public CIA getCIA1() {
		return cia1;
	}

	public VIC getVIC() {
		return vic;
	}

	public IECBus getIECBus() {
		return iecBus;
	}
}