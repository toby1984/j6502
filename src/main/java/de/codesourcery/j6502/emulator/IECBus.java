package de.codesourcery.j6502.emulator;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.utils.HexDump;

public class IECBus 
{
	protected boolean atn;
	protected boolean clockOut;
	protected boolean dataOut;

	protected boolean clockIn;
	protected boolean dataIn=true;

	private abstract class BusState 
	{
		private final String name;
		
		public BusState(String name) { this.name=name; }
		
		public abstract BusState afterWrite();

		public void tick() {
		}
		
		public void onEnter() {
		}
		
		@Override public String toString() { return name; }
	}

	protected int bitsReceived = 0;
	protected byte data;

	private final BusState WAIT_TO_ACK;	
	private final BusState ACKNOWLEDGE;
	private final BusState UNDEFINED;	
	private final BusState RECEIVING2;
	private final BusState RECEIVING1;
	private final BusState LISTENING;

	private BusState busState;

	public void writeBus(byte b) 
	{
		// all lines are low-active, 0 = LOGICAL TRUE , 1 = LOGICAL FALSE
		// but I invert the sense of matching here since
		// it's easier to follow the program code along...
		atn = (b & 1<<3) == 0;
		clockOut = (b & 1 << 4) == 0;
		dataOut = (b & 1<<5) == 0;
		final String s1 = StringUtils.rightPad( this.busState.toString(),15);
		BusState newState = busState.afterWrite();
		if ( newState != busState ) {
			newState.onEnter();
		}
		final String s2 = StringUtils.rightPad( newState.toString(),15);
		System.out.println( "["+s1+" -> "+s2+" ] : "+this);			
		busState = newState;
	}
	
	public IECBus() 
	{
		UNDEFINED = new BusState("UNDEFINED") 
		{ 
			@Override
			public BusState afterWrite() {
				if ( atn && clockOut ) {
					dataIn = true;
					return LISTENING;
				}
				return this;
			}
		};
		
		ACKNOWLEDGE = new BusState("ACK") {
			@Override
			public BusState afterWrite() 
			{
				return LISTENING;
			}
			
			@Override
			public void onEnter() {
				dataIn = true;
			}
		};
		
		WAIT_TO_ACK = new BusState("WAIT_TO_ACK") {
			@Override
			public BusState afterWrite() 
			{
				if ( clockOut && ! dataOut ) {
					return ACKNOWLEDGE;
				}
				return this;
			}
		};
		
		RECEIVING2 = new BusState("RECEIVING_2")  
		{
			@Override
			public BusState afterWrite() 
			{
				if ( clockOut == true ) 
				{
					return RECEIVING1;
				}
				return this;
			}
			
			public void onEnter() { };
		};	

		RECEIVING1 = new BusState("RECEIVING_1")  
		{
			@Override
			public BusState afterWrite() 
			{
				if ( clockOut == false ) 
				{
					// data is received with the LSB first
					data = (byte) (data >> 1);
					System.out.println("BIT "+bitsReceived+": "+dataOut);
					data &= dataOut ? 1<<7 : 0; // logical 1 = 
					bitsReceived++;
					if ( bitsReceived == 8 ) {
						byteReceived(data);
						data = 0;
						bitsReceived = 0;
						return WAIT_TO_ACK;
					}
					return RECEIVING2;
				}
				return this;
			}
			
			public void onEnter() { };
		};
		
		LISTENING = new BusState("LISTENING") 
		{
			@Override
			public BusState afterWrite() {
				if ( ! atn && clockOut ) {
					dataIn = false;
					return RECEIVING1;
				}
				return this;
			}
		};		
		this.busState = UNDEFINED;
	}

	@Override
	public String toString() {
		return "ATN: "+atn+" , CLK_OUT: "+clockOut+" , DATA_OUT: "+dataOut+"  ||   CLK_IN: "+clockIn+", DATA_IN: "+dataIn;
	}
	
	private void byteReceived(byte data) {
		System.out.println("BYTE RECEIVED: "+HexDump.toBinaryString( data ) );
	}

	public byte readBus() 
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
		return (byte) ( (clockIn ? 0 : 1<<6 ) | ( dataIn ? 0 : 1<<7 ) );
	}

	public void tick() {
	}
}
