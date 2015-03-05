package de.codesourcery.j6502.emulator;

import org.apache.commons.lang.StringUtils;

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

	protected byte data;
	
	private final BusState UNDEFINED;	
	private final BusState RECEIVING2;
	private final BusState RECEIVING1;
	private final BusState LISTENING;

	private BusState busState;

	public void writeBus(byte b) 
	{
		atn = (b & 1<<3) != 0;
		clockOut = (b & 1 << 4) != 0;
		dataOut = (b & 1<<5) != 0;
		System.out.println("--------");
		System.out.println("["+StringUtils.rightPad( this.busState.toString(),15)+"] : "+this);
		BusState newState = busState.afterWrite();
		if ( newState != busState ) {
			newState.onEnter();
		}
		busState = newState;
		System.out.println("["+StringUtils.rightPad( this.busState.toString(),15)+"] : "+this);
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
			
			public void onEnter() { data = 0; };
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
					data |= dataOut ? 1<<7 : 0;
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
		return "ATN: "+atn+" , CLK_OUT: "+clockOut+" , DATA_OUT: "+dataOut+", CLK_IN: "+clockIn+", DATA_IN: "+dataIn;
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
		return (byte) ( (clockIn ? 1<<6 : 0 ) | ( dataIn ? 1<<7 : 0) );
	}

	public void tick() {

	}
}
