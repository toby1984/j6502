package de.codesourcery.j6502.emulator;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.emulator.Keyboard.Key;

/**
 * I/O area , memory bank 5 ($D000 - $DFFF).
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class IOArea extends Memory
{
	public final KeyboardBuffer keyboardBuffer = new KeyboardBuffer();
	
	public final NewVIC vic;
	public final IECBus iecBus;
	
	public final CIA cia1 = new CIA("CIA #1" , AddressRange.range( 0xdc00, 0xdd00 ) ) {

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
			final int offset = ( adr & 0xffff ) % 0x10; // registers are mirrored/repeated every 16 bytes
			if (offset == CIA2_PRA )
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

				super.writeByte( adr , value);

				final boolean atn = (value     & 0b0000_1000) == 0;
				final boolean clkOut = (value  & 0b0001_0000) == 0;
				final boolean dataOut = (value & 0b0010_0000) == 0;
				if ( IECBus.DEBUG_WIRE_LEVEL ) {
					System.out.println("Write to $DD00: to_write: "+toBinaryString( value)+toLogical(", ATN: ",atn)+toLogical(" , clkOut: ",clkOut)+toLogical(", dataOut: ",dataOut));
				}
			}
			else
			{
				if ( offset == CIA2_DDRA && IECBus.DEBUG_WIRE_LEVEL )
				{
					System.out.println("Write to $DD02 DDRA: "+toBinaryString(value));
				}
				super.writeByte(adr, value);
			}
		}

		private String toLogical(String msg,boolean level) {
			return msg+" "+(level?"HIGH":"LOW");
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
				final boolean clockState = iecBus.getClk();
				final boolean dataState = iecBus.getData();

				if ( clockState ) {  // true == BUS HIGH (has to match implementation in CPU SerialDevice#getClock() !!!)
					value |=  0b0100_0000;
				} else {
					value &= ~0b0100_0000;
				}

				if ( dataState ) { // true == BUS HIGH (has to match implementation in CPU SerialDevice#getClock() !!!)
					value |=  0b1000_0000;
				} else {
					value &= ~0b1000_0000;
				}
			}
			return value;
		};
	};

	private final SerialDevice cpuDevice;
	private final IMemoryRegion mainMemory;

	public IOArea(String identifier, AddressRange range, IMemoryRegion mainMemory)
	{
		super(identifier, range);

		cpuDevice = new SerialDevice() {

			@Override
			public void tick(IECBus bus,boolean atnLowered) {
			}

			@Override
			public void reset() {
			}

			@Override
			public int getPrimaryAddress() {
				return 0;
			}

			/*
					Bit 3..5: serial bus Output (0=High/Inactive, 1=Low/Active)

					    Bit 3: ~ATN OUT
					    Bit 4: ~CLOCK OUT
					    Bit 5: ~DATA OUT

					   DATA  ATN
				          |  |
					    0000_0000
					       |
					       CLOCK

					Bit 6..7: serial bus Input (0=Low/Active, 1=High/Inactive)

					    Bit 6: CLOCK IN
					    Bit 7: DATA IN
			 */
			@Override
			public boolean getData()
			{
				// !!! Implementation HAS to match what's used in readByte(int) method !!!
				final int value = cia2.readByte( CIA.CIA2_PRA );
				return ( value & 0b0010_0000) == 0;
			}

			@Override
			public boolean getClock() {
				// !!! Implementation HAS to match what's used in readByte(int) method !!!
				final int value = cia2.readByte( CIA.CIA2_PRA );
				return ( value & 0b0001_0000) == 0;
			}

			@Override
			public boolean getATN() {
				// !!! Implementation HAS to match what's used in readByte(int) method !!!
				final int value = cia2.readByte( CIA.CIA2_PRA );
				return ( value & 0b0000_1000) == 0;
			}
		};
		this.iecBus = new IECBus("default bus" , cpuDevice );
		this.mainMemory = mainMemory;
		this.vic = new NewVIC("VIC", AddressRange.range( 0xd000, 0xd02f) , mainMemory );
	}

	private final int[] keyboardColumns = new int[] {0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff}; // 8 keyboard columns, bits are low-active

	protected void handleKeyPress(Key key)
	{
		keyboardColumns[ key.colBitNo ] &= ~(1 << key.rowBitNo); // bits are low-active so clear bit if key is pressed
		if ( key.clearShift() ) {
			handleKeyRelease(Key.KEY_LEFT_SHIFT);
			handleKeyRelease(Key.KEY_RIGHT_SHIFT);
		} else if ( key.fakeLeftShift() ) {
			handleKeyPress(Key.KEY_LEFT_SHIFT);
		}
	}

	protected void handleKeyRelease(Key key)
	{
		keyboardColumns[ key.colBitNo ] |= (1 << key.rowBitNo);	 // bits are low-active so set bit if key is released
		if ( key.fakeLeftShift() ) {
			handleKeyRelease(Key.KEY_LEFT_SHIFT);
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
	public void reset() 
	{
		super.reset();

		for ( int i = 0 ; i < keyboardColumns.length ; i++ ) {
			keyboardColumns[i] = 0xff;
		}

		keyboardBuffer.reset();
		vic.reset();
		cia1.reset();
		cia2.reset();
		iecBus.reset();
	}

	public void tick(CPU cpu,boolean clockHigh)
	{
		if ( clockHigh ) {
			keyboardBuffer.tick( this );
		}
		cia1.tick( cpu , clockHigh );
		cia2.tick(cpu , clockHigh );
		vic.tick( cpu , clockHigh );
		iecBus.tick(clockHigh);
	}

	protected static String toBinaryString(int value)
	{
		final String string = Integer.toBinaryString( value & 0xff );
		final String result = "%"+StringUtils.repeat("0" , 8-string.length())+string;
		return result.substring(0,5)+"_"+result.substring(5);
	}
}