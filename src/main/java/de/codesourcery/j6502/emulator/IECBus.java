package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.utils.HexDump;

public class IECBus
{
	private final int MAX_CYCLES_TO_KEEP = 120;
	
	private static final boolean DEBUG_VERBOSE = false;

	protected boolean atn;
	protected boolean clockOut;
	protected boolean dataOut;

	protected boolean clockIn;
	protected boolean dataIn;
	
	protected long cycle;

	private final List<StateSnapshot> states = new ArrayList<>();
	
	protected int bitsReceived = 0;
	protected byte data;
	
	// EOI handling
	protected long lastReadyForDataAtCycle = 0;
	protected boolean eoi;

	private final BusState ACKNOWLEDGE;
	private final BusState UNDEFINED;
	private final BusState RECEIVING2;
	private final BusState WAIT_FOR_FIRST_BIT;
	private final BusState RECEIVING1;
	private final BusState LISTENING;

	private BusState busState;
	
	public void reset() {
		busState = UNDEFINED;
		eoi = false;
		bitsReceived = 0;
		states.clear();
		cycle = 0;
		atn = false;
		clockOut = false;
		dataOut = false;
		clockIn = false;
		dataIn = false;
	}

	public static final class StateSnapshot
	{
		public final boolean eoi;
		
		public final boolean atn;
		public final boolean clkOut;
		public final boolean dataOut;

		public final boolean clkIn;
		public final boolean dataIn;
		
		public final long cycle;

		public final BusState busState;

		public StateSnapshot(boolean atn, boolean clkOut, boolean dataOut, boolean clkIn, boolean dataIn,BusState busState,long cycle,boolean eoi)
		{
			this.cycle = cycle;
			this.eoi = eoi;
			this.atn = atn;
			this.clkOut = clkOut;
			this.dataOut = dataOut;
			this.clkIn = clkIn;
			this.dataIn = dataIn;
			this.busState = busState;
		}
	}

	private void takeSnapshot()
	{
		synchronized(states) {
			states.add( new StateSnapshot( atn , clockOut , dataOut, clockIn , dataIn , busState , cycle , eoi ) );
			if ( states.size() > MAX_CYCLES_TO_KEEP ) {
				states.remove(0);
			}
		}
	}

	public List<StateSnapshot> getSnapshot()
	{
		synchronized(states) {
			final List<StateSnapshot> result = new ArrayList<>( this.states.size() );
			if ( ! this.states.isEmpty() ) 
			{
				long lastCycle = states.get(states.size()-1).cycle;
				long cyclesToCover = 2*1000000; // 2 seconds = 2 mio. cycles
				for ( int i = states.size()-1 ; i >= 0 ; i-- ) {
					if ( lastCycle - states.get(i).cycle > cyclesToCover ) {
						break;
					}
					result.add(0,states.get(i) );
				}
				return result;
			}
			return result;
		}
	}

	public abstract class BusState
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

	public void writeBus(byte b)
	{
		// all lines are low-active, 0 = LOGICAL TRUE , 1 = LOGICAL FALSE
		// but I invert the sense of matching here since
		// it's easier to follow the program code along...
		atn = (b & 1<<3) != 0;
		clockOut = (b & 1 << 4) != 0;
		dataOut = (b & 1<<5) == 0;
		
		BusState newState = busState.afterWrite();
		if ( newState != busState ) {
			newState.onEnter();
		}
		
		if ( DEBUG_VERBOSE ) {
			final String s1 = StringUtils.rightPad( this.busState.toString(),15);		
			final String s2 = StringUtils.rightPad( newState.toString(),15);
			System.out.println( "["+s1+" -> "+s2+" ] : "+this);
		}
		busState = newState;
		takeSnapshot();
	}

	public IECBus()
	{
		UNDEFINED = new BusState("UNDEFINED")
		{
			@Override
			public BusState afterWrite() {
				if ( atn ) {
					dataIn = true;
					eoi = false;
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

		RECEIVING2 = new BusState("BIT")
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

			@Override
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
					data = (byte) (data >>> 1);
					if ( DEBUG_VERBOSE ) {
						System.out.println("BIT "+bitsReceived+": "+dataOut);
					}
					if ( dataOut ) {
						data |= 1<<7;
					} else {
						data &= 0b01111111;
					}

					bitsReceived++;
					if ( bitsReceived == 8 ) 
					{
						byteReceived(data);
						bitsReceived = 0;
						return ACKNOWLEDGE;
					}
					return RECEIVING2;
				}
				return this;
			}

			@Override
			public void onEnter() { };
		};

		WAIT_FOR_FIRST_BIT = new BusState("WAIT") {

			@Override
			public BusState afterWrite() 
			{
				if ( clockOut ) 
				{
					System.out.println("WAIT_FOR_FIRST_BIT : "+(cycle-lastReadyForDataAtCycle)+" cycles waited");
					eoi = (cycle - lastReadyForDataAtCycle ) > 200;
					return RECEIVING1;
				}
				return this;
			}
			
		};
		LISTENING = new BusState("LISTENING")
		{
			@Override
			public BusState afterWrite() {
				if ( ! clockOut ) // talker is ready to sent
				{
					dataIn = false; // signal "ready for data"
					lastReadyForDataAtCycle = cycle;
					return WAIT_FOR_FIRST_BIT;
				}
				return this;
			}
		};
		this.busState = UNDEFINED;
	}

	@Override
	public String toString() {
		return "ATN: "+( atn ? "1" : "0" )+" , CLK_OUT: "+( clockOut ? "1":"0") +" , DATA_OUT: "+(dataOut ? "1" : "0") +"  ||   CLK_IN: "+(clockIn?"1":"0")+", DATA_IN: "+(dataIn?"1":"0");
	}

	private void byteReceived(byte data) {
		System.out.println("BYTE RECEIVED: "+HexDump.toBinaryString( data )+" ( $"+HexDump.toHex( data ) +" )" );
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
		cycle++;
	}
}