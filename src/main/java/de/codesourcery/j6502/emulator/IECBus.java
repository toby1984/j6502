package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.utils.HexDump;

public class IECBus
{
	private final int MAX_CYCLES_TO_KEEP = 120;
	
	private static final boolean DEBUG_VERBOSE = true;

	protected boolean atn;
	protected boolean clockOut;
	protected boolean dataOut;

	protected boolean clockIn;
	protected boolean dataIn;
	
	protected boolean eoi;
	protected long cycle;

	private final List<StateSnapshot> states = new ArrayList<>();
	
	protected int bitsReceived = 0;
	protected byte data;
	
	private BusState previousBusState;	
	private BusState busState;
	
	public void reset() 
	{
		this.previousBusState = this.busState = WAIT_FOR_ATN;
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

		public final void tick() 
		{
			long delta = Math.abs( cycle - waitingStartedAtCycle );
			waitingStartedAtCycle = cycle;
			cyclesWaited += delta;
			tickHook();
		}
		
		protected abstract void tickHook();
		
		protected final void startWaiting() {
			cyclesWaited = 0;
			waitingStartedAtCycle = cycle;			
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
		clockOut = (b & 1 << 4) == 0;
		dataOut = (b & 1<<5) == 0;
		
		if ( DEBUG_VERBOSE ) {
			final String s1 = StringUtils.rightPad( this.previousBusState.toString(),15);		
			final String s2 = StringUtils.rightPad( this.busState.toString(),15);
			System.out.println( cycle+": ["+s1+" -> "+s2+" ] : "+this);
		}
		takeSnapshot();
	}
	
	private void setBusState(BusState newState) 
	{
		BusState oldState = this.busState;
		if ( newState != oldState ) 
		{
			previousBusState = this.busState;
			this.busState = newState;
			if ( DEBUG_VERBOSE ) {
				final String s1 = StringUtils.rightPad( this.previousBusState.toString(),15);		
				final String s2 = StringUtils.rightPad( this.busState.toString(),15);
				System.out.println( cycle+": ["+s1+" -> "+s2+" ] : "+this);
			}			
			takeSnapshot();			
		}
	}
	
	protected final BusState ACK_BYTE;	
	protected final BusState RECEIVE_BIT1;
	protected final BusState RECEIVE_BIT0;
	protected final BusState PREPARE_FOR_BYTE;
	protected final BusState LISTENING2;	
	protected final BusState LISTENING1;
	protected final BusState ACK_ATN;
	protected final BusState READY_FOR_DATA;
	protected final BusState WAIT_FOR_ATN;
	protected final BusState WAIT_FOR_TRANSMISSION;	
	protected final BusState ACK_EOI;	

	protected long waitingStartedAtCycle;
	protected long cyclesWaited;
	
	public IECBus()
	{
		ACK_BYTE = new BusState("ACK_BYTE")
		{
			@Override
			protected void tickHook() {
				if ( cyclesWaited >= 60 ) 
				{
					dataIn = true; 
					setBusState( LISTENING2 );
				}	
			}
			
			@Override
			public void onEnter() {
				dataIn = false;
				startWaiting();
			}
		};
		
		RECEIVE_BIT1 = new BusState("RECEIVE_BIT1")
		{
			@Override
			protected void tickHook() {
				if ( ! clockOut ) { // wait for falling clock
					setBusState( RECEIVE_BIT0 );
				}
			}
			
			@Override
			public void onEnter() 
			{
				data = (byte) (data >>> 1);
				if ( DEBUG_VERBOSE ) {
					System.out.println("GOT BIT: "+(dataOut ? "1" : "0" ) );
				}
				if ( dataOut ) {
					data |= 1<<7; // bit was 1
				} else {
					data &= ~(1<<7); // bit was 0
				}
				bitsReceived++;
				if ( bitsReceived == 8 ) {
					byteReceived( data );
					setBusState( ACK_BYTE );
				}
			}
		};
		
		RECEIVE_BIT0 = new BusState("RECEIVE_BIT0")
		{
			@Override
			public void tickHook() 
			{
				if ( clockOut ) { // wait for rising clock
					setBusState( RECEIVE_BIT1 );
				}
			}		
		};
		
		PREPARE_FOR_BYTE = new BusState("PREPARE_FOR_BYTE")
		{
			@Override
			public void tickHook() 
			{
				if ( ! clockOut ) {
					setBusState( RECEIVE_BIT0 );
				}
			}
			
			public void onEnter() {
				bitsReceived = 0;				
				dataIn = false;
			}
		};
		
		ACK_EOI = new BusState("ACK_EOI")
		{
			@Override
			protected void tickHook() {
				if ( cyclesWaited > 500 ) {
					setBusState( PREPARE_FOR_BYTE );
				}				
			}
			
			@Override
			public void onEnter() {
				// pull dataIn to low for 32 cycles
				dataIn = true;
				startWaiting();
			}
		};
		
		WAIT_FOR_TRANSMISSION = new BusState("WAIT_FOR_TRANSMISSION")
		{
			@Override
			public void tickHook() 
			{
				if ( clockOut ) // wait for clkin == low
				{
					if ( DEBUG_VERBOSE ) {
						System.out.println(cycle+": Talker starting to send after "+cyclesWaited+" cycles.");
					}				
					if ( cyclesWaited < 200 ) 
					{
						if ( DEBUG_VERBOSE ) {
							System.out.println(cycle+": Waited "+cyclesWaited+" cycles => EOI");
						}					
						eoi = true;
						setBusState( ACK_EOI );
					} else {					
						setBusState( PREPARE_FOR_BYTE );
					}
				}
			}
			
			@Override
			public void onEnter() {
				dataIn = true; // signal "ready for data"
				eoi = false;
				if ( DEBUG_VERBOSE ) {
					System.out.println(cycle+": Starting to wait at");
				}
				startWaiting();
			}
		};	
		
		READY_FOR_DATA = new BusState("READY_FOR_DATA")
		{
			@Override
			public void tickHook() {
				setBusState( WAIT_FOR_TRANSMISSION );
			}
			
			@Override
			public void onEnter() {
				dataIn = false; 
			}
		};		
		
		LISTENING2 = new BusState("LISTENING_2")
		{
			@Override
			public void tickHook() 
			{
				if ( ! clockOut ) // wait for clockOut == false
				{
					setBusState( READY_FOR_DATA );
				}
			}
		};		
		
		ACK_ATN = new BusState("ACK_ATN") {

			@Override
			protected void tickHook() {
				setBusState(LISTENING2);
			}
			
			@Override
			public void onEnter() {
				dataIn = true;
			}
		};		

		LISTENING1 = new BusState("LISTENING_1")
		{
			@Override
			public void tickHook() 
			{
				if ( ! clockOut ) // wait for clock out true -> false
				{
					setBusState( LISTENING2 );
				}
			}
		};
		
		WAIT_FOR_ATN = new BusState("WAIT_FOR_ATN")
		{
			@Override
			public void tickHook() 
			{
				if ( atn ) {
					setBusState( ACK_ATN );
				} 
			}
		};		
		
		this.previousBusState = this.busState = WAIT_FOR_ATN;
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
		if ( previousBusState != busState ) 
		{
			previousBusState = busState;
			busState.onEnter();
		}
		busState.tick();
		cycle++;		
	}
}