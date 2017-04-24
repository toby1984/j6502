package de.codesourcery.j6502.emulator;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.emulator.Keyboard.Key;
import de.codesourcery.j6502.emulator.tapedrive.TapeDrive;

/**
 * I/O area , memory bank 5 ($D000 - $DFFF).
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class IOArea extends SlowMemory
{
	public final KeyboardBuffer keyboardBuffer = new KeyboardBuffer();

	private final IMemoryRegion colorRAMBank; // RAM that covers $D000 - $DFFF

	public final TapeDrive tapeDrive;
	public final VIC vic;
	public final IECBus iecBus;

	public static enum JoyDirection
	{
		N, NE, E, SE, S, SW, W , NW, CENTER;
	}

	/* Joystick masks:
	 *
	 * Bit 0 => left (low-active , 0 = on)
	 * Bit 1 => right (low-active!)
	 * Bit 2 => up (low-active!)
	 * Bit 3 => down (low-active!)
	 * Bit 4 => fire (low-active!)
	 */
	protected int joy1Mask = 0xff;
	protected int joy2Mask = 0xff;

	public final CIA cia1 = new CIA("CIA #1" , AddressRange.range( 0xdc00, 0xdd00 ) ) {

	    // only CIA #1 needs to handle input from tape drive
	    protected final void handleCassette(CPU cpu) 
	    {
	        tapeDrive.tick();
	        doHandleCassette( cpu );
	    }
	    
	    protected final boolean getTapeSignal() 
	    {
	        return tapeDrive.currentSignal();
	    }
	    
	    @Override
	    public void writeByte(int address, byte value) 
	    {
	        // do NOT move this to the CIA (superclass) class as this method is also calling super.readByte() 
	        // with different addresses for internal purposes and this would trigger 
	        // memory breakpoints without the client code actually accessing the memory location  
	        getBreakpointsContainer().write( address );	        
	        super.writeByte( address , value);
	    }
	    
		@Override
		public int readByte(int adr)
		{
		    // do NOT move this to the CIA (superclass) class as this method is also calling super.readByte() 
		    // with different addresses for internal purposes and this would trigger 
		    // memory breakpoints without the client code actually accessing the memory location  		    
	        breakpointsContainer.read( adr );
	        
			/*
$DC00
PRA 	Data Port A 	Monitoring/control of the 8 data lines of Port A

        Read/Write: Bit 0..7 keyboard matrix columns
        Read: Joystick Port 2: Bit 0: up, Bit 1: down, Bit 2: left, Bit 3: right, Bit 4: fire ( all bits 0 = active )
        Read: Lightpen: Bit 4 (as fire button), connected also with "/LP" (Pin 9) of the VIC
        Read: Paddles: Bit 2..3 Fire buttons, Bit 6..7 Switch control port 1 (%01=Paddles A) or 2 (%10=Paddles B)
----
$DC01
PRB 	Data Port B 	Monitoring/control of the 8 data lines of Port B. The lines are used for multiple purposes:

        Read/Write: Bit 0..7 keyboard matrix rows
        Read: Joystick Port 1: Bit 0: up, Bit 1: down, Bit 2: left, Bit 3: right, Bit 4: fire ( all bits 0 = active )
        Read: Bit 6: Timer A: Toggle/Impulse output (see register 14 bit 2)
        Read: Bit 7: Timer B: Toggle/Impulse output (see register 15 bit 2)
			 */
			final int offset = ( adr & 0xffff ) % 0x10; // registers are mirrored/repeated every 16 bytes
			if ( offset == CIA_PRA )
			{
				final int pra = super.readByte( CIA_PRA );
				return pra & joy2Mask;
			}
			if ( offset == CIA_PRB )
			{
				// rowBit / PRB , columnBit / PRA )
				int pra = super.readByte( CIA_PRA ); // figure out which keyboard column to read (columns to be read have a 0 bit in here)
				int result2 = 0xff; // keyboard lines are low-active, so 1 = key NOT pressed , 0 = key pressed
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
				result2 &= joy1Mask;
//				if ( result2 != 0xff ) {
//				    System.out.println("CIA #1 - PRA: %"+StringUtils.leftPad( Integer.toBinaryString( pra ) , 8 , '0') + " , result: %"+StringUtils.leftPad( Integer.toBinaryString( result2 ) , 8 , '0' ) +" ( $"+Integer.toHexString( result2 )+", joyMask: "+joy1Mask+")" );
//				}
				return result2;
			}
			return super.readByte( offset );
		}
	};

	public void setJoystick1(JoyDirection direction,boolean fire)
	{
		System.out.println("Joystick #2: "+direction+",fire: "+fire);
		joy1Mask = calcJoystickMask(direction,fire);
	}

	public void setJoystick2(JoyDirection direction,boolean fire)
	{
		System.out.println("Joystick #2: "+direction+",fire: "+fire);
		joy2Mask = calcJoystickMask(direction,fire);
	}

	private int calcJoystickMask(JoyDirection direction,boolean fire)
	{
		/* All bits: 0 = active, 1 = inactive
         * Bit 0: up,
         * Bit 1: down,
         * Bit 2: left,
         * Bit 3: right,
         * Bit 4: fire
		 */

		int mask = 0xff;
		switch( direction )
		{
			case CENTER:
				break;
			case E:
				mask &= ~(1<<3); // right
				break;
			case N:
				mask &= ~(1<<0); // up
				break;
			case NE:
				mask &= ~(1<<0); // up
				mask &= ~(1<<3); // right
				break;
			case NW:
				mask &= ~(1<<0); // up
				mask &= ~(1<<2); // left
				break;
			case S:
				mask &= ~(1<<1); // down
				break;
			case SE:
				mask &= ~(1<<1); // down
				mask &= ~(1<<3); // right
				break;
			case SW:
				mask &= ~(1<<1); // down
				mask &= ~(1<<2); // left
				break;
			case W:
				mask &= ~(1<<2); // left
				break;
			default:
				throw new IllegalArgumentException("Unhandled joystick direction: "+direction);
		}
		if (fire) {
			mask &= ~(1<<4);
		}
		return mask;
	}

	public final CIA cia2 = new CIA("CIA #2" , AddressRange.range( 0xdd00, 0xde00 ) )
	{
		@Override
		public void writeByte(int adr, byte value)
		{
            // do NOT move this to the CIA (superclass) class as this method is also calling super.readByte() 
            // with different addresses for internal purposes and this would trigger 
            // memory breakpoints without the client code actually accessing the memory location  
            getBreakpointsContainer().write( adr );   
            
			final int offset = ( adr & 0xffff ) % 0x10; // registers are mirrored/repeated every 16 bytes
			if (offset == CIA_PRA )
			{
				/*
				 Bit 0..1: Select the position of the VIC-memory

				     %00, Bank 0: $C000-$FFFF, 49152-65535
				     %01, Bank 1: $8000-$BFFF, 32768-49151
				     %10, Bank 2: $4000-$7FFF, 16384-32767
				     %11, Bank 3: $0000-$3FFF, 0-16383 (standard)

				Bit 2: RS-232: TXD Output, userport: Data PA 2 (pin M)
				Bit 3..5: serial bus Output (0=High/Inactive, 1=Low/Active)

				    Bit 3: ATN OUT
				    Bit 4: CLOCK OUT
				    Bit 5: DATA OUT

				Bit 6..7: serial bus Input (0=Low/Active, 1=High/Inactive)

				    Bit 6: CLOCK IN
				    Bit 7: DATA IN
				 */

				final int oldValue = super.readByte( adr );
				super.writeByte( adr , value);

				if ( (oldValue & 0b11) != (value & 0b11 ) ) // VIC bank was changed, recalculate RAM offsets
				{
					vic.setCurrentBankNo( value & 0b11 );
				}

				if ( IECBus.DEBUG_WIRE_LEVEL )
				{
					final boolean atn = (value     & 0b0000_1000) == 0;
					final boolean clkOut = (value  & 0b0001_0000) == 0;
					final boolean dataOut = (value & 0b0010_0000) == 0;
					System.out.println("Write to $DD00: to_write: "+toBinaryString( value)+toLogical(", ATN: ",atn)+toLogical(" , clkOut: ",clkOut)+toLogical(", dataOut: ",dataOut));
				}
			}
			else
			{
				if ( offset == CIA_DDRA && IECBus.DEBUG_WIRE_LEVEL )
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
            // do NOT move this to the CIA (superclass) class as this method is also calling super.readByte() 
            // with different addresses for internal purposes and this would trigger 
            // memory breakpoints without the client code actually accessing the memory location  		    
	        breakpointsContainer.read( adr );
	        
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

	/**
	 *
	 * @param identifier
	 * @param range
	 * @param mainMemory
	 * @param colorRAMBank Writes/reads of color RAM @ 0d800-0dc00 will be redirected here so
	 * that contents of color RAM are identical no matter whether the I/O area has been swapped for RAM or not
	 */
	public IOArea(String identifier, AddressRange range, MemorySubsystem mainMemory,IMemoryRegion colorRAMBank, TapeDrive tapeDrive)
	{
		super(identifier, MemoryType.IOAREA ,range);

		this.tapeDrive = tapeDrive;
		this.colorRAMBank = colorRAMBank;

		cpuDevice = new SerialDevice() {

			@Override
			public void tick(Emulator emulator,IECBus bus) {
			}

			@Override
			public void reset() {
			}

			@Override
			public boolean isDataTransferActive() {
				return false;
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
				final int value = cia2.readByte( CIA.CIA_PRA );
				return ( value & 0b0010_0000) == 0;
			}

			@Override
			public boolean getClock() {
				final int value = cia2.readByte( CIA.CIA_PRA );
				return ( value & 0b0001_0000) == 0;
			}

			@Override
			public boolean getATN() {
				final int value = cia2.readByte( CIA.CIA_PRA );
				return ( value & 0b0000_1000) == 0;
			}
		};
		this.iecBus = new IECBus("default bus" , cpuDevice );
		this.vic = new VIC("VIC", AddressRange.range( 0xd000, 0xd02f) , mainMemory );
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
	public void writeByte(int address, byte value)
	{
		/*
// CIA #1: actually only 16 byte registers but mirrored in range $DC10-$DCFF
// CIA #2: actually only 16 byte registers but mirrored in range $DD10-$DDFF
		 */
		final int trimmedAddress = address & 0xffff;

		// I/O area starts at 0xd000
		// but input to writeByte is already translated by -d000

		if ( trimmedAddress >= 0x800 && trimmedAddress < 0x800+1024) {
		    // intentionally NOT trimmedAddress - 0x800 as colorRAMBank is actually the whole bank #5 (D000-DFFF) and not just the 1 KB block starting at $D800
			colorRAMBank.writeByte( trimmedAddress , value );
			return;
		}
		if ( trimmedAddress >= 0xc00 && trimmedAddress <= 0xcff) { // $DC00-$DCFF
			cia1.writeByte( address - 0xc00 , value );
			return;
		}
		if ( trimmedAddress >= 0xd00 && trimmedAddress <= 0xdff ) { // $DD10-$DDFF
			cia2.writeByte( address - 0xd00 , value );
			return;
		}
		if ( trimmedAddress < 0x002f)  // $D000 - $D02F ... VIC
		{
			vic.writeByte( address , value );
			return;
		}
		super.writeByte(trimmedAddress, value);
	}

	@Override
	public int readByte(int adr)
	{
		// this I/O area starts at 0xd000
		// but input to writeByte is already translated by -d000
		final int offset = adr & 0xffff;
		if ( offset >= 0x800 && offset < 0x800+1024) {
			return colorRAMBank.readByte( offset ); // intentionally NOT offset - 0x800 as colorRAMBank is actually the whole bank #5 (D000-DFFF) and not just the 1 KB block starting at $D800
		}
		if ( offset >= 0xc00 && offset <= 0xcff) { // CIA #1 $DC00-$DCFF
			return cia1.readByte( offset - 0xc00 );
		}
		if ( offset >= 0xd00 && offset <= 0xdff ) { // CIA #2 $DD10-$DDFF
			return cia2.readByte( offset - 0xd00 );
		}
		if ( offset < 0x002f ) { // $D000 - $D02F ... VIC
			return vic.readByte( offset );
		}
		return super.readByte(offset);
	}
	
	@Override
	public void reset()
	{
		super.reset();

		for ( int i = 0 ; i < keyboardColumns.length ; i++ ) {
			keyboardColumns[i] = 0xff;
		}

		tapeDrive.reset();
		
		joy1Mask = 0xff;
		joy2Mask = 0xff;

		keyboardBuffer.reset();
		vic.reset();
		cia1.reset();
		cia2.reset();
		iecBus.reset();
	}

	public void tick(Emulator emulator,CPU cpu,boolean clockHigh)
	{
		if ( clockHigh ) {
			keyboardBuffer.tick( this );
	        cia1.tick( cpu );
	        cia2.tick( cpu );
	        iecBus.tick(emulator);
		}

		vic.tick( cpu , clockHigh );

	}

	protected static String toBinaryString(int value)
	{
		final String string = Integer.toBinaryString( value & 0xff );
		final String result = "%"+StringUtils.repeat("0" , 8-string.length())+string;
		return result.substring(0,5)+"_"+result.substring(5);
	}

	@Override
	public boolean isReadsReturnWrites(int offset) {
	    return false;
	}
}