package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.utils.HexDump;
import de.codesourcery.j6502.utils.RingBuffer;

public class IECBus
{
	private final int MAX_CYCLES_TO_KEEP = 200;

	private static final boolean DEBUG_VERBOSE = true;

	protected boolean eoi;
	protected long cycle;

	protected abstract class BusMode 
	{
		public abstract void onEnter(); 
	}

	protected final BusMode BUSMODE_RECEIVE = new BusMode() 
	{
		public void onEnter() 
		{
			previousBusState = busState = WAIT_FOR_ATN;
		}
	};

	protected final BusMode BUSMODE_SEND = new BusMode() 
	{
		public void onEnter() 
		{
			throw new RuntimeException("Sending not implemented");
		}
	};

	private final RingBuffer sendBuffer = new RingBuffer();
	private final RingBuffer eoiBuffer = new RingBuffer();

	private final List<SerialDevice> devices = new ArrayList<>();

	private final List<StateSnapshot> states = new ArrayList<>();

	protected BusMode busMode = BUSMODE_RECEIVE;

	protected int bitsTransmitted = 0;
	protected byte currentByte;

	protected BusState previousBusState;	
	protected BusState busState;
	
	protected Wire wire = new SenderWire();
	
	public static abstract class Wire 
	{
		public boolean atn;
		public boolean clockOut;
		public boolean dataOut;

		public boolean clockIn;
		public boolean dataIn;
		
		public final void reset() {
			atn = false;
			clockOut = false;
			dataOut = false;
			clockIn = false;
			dataIn = false;
		}
		
		public abstract void setOutState(boolean atn,boolean dataOut,boolean clkOut);
		
		public final boolean getATN() {
			return atn;
		}
		
		public abstract boolean getDataOut();
		
		public abstract boolean getClockOut();
		
		public abstract void setDataIn(boolean dataIn);
		
		public abstract boolean getDataIn();
		
		public abstract boolean getClockIn();
	}
	
	protected final class SenderWire extends Wire {

		@Override
		public void setOutState(boolean atn, boolean dataOut, boolean clkOut) {
			this.atn = atn;
			this.clockOut = clkOut;
			this.dataOut = dataOut;
			busStateMayHaveChanged();			
		}

		@Override
		public boolean getDataIn() {
			return dataIn;
		}

		@Override
		public boolean getClockIn() {
			return clockIn;
		}

		@Override
		public boolean getDataOut() {
			return this.dataOut;
		}
		
		@Override
		public void setDataIn(boolean dataIn) {
			this.dataIn = dataIn;
		}

		@Override
		public boolean getClockOut() {
			return this.clockOut;
		}
	}
	
	protected final class ReceiverDriver extends Wire 
	{
		@Override
		public void setOutState(boolean atn, boolean data, boolean clk) 
		{
			// only the CPU may control ATN
			// IECBus.this.atn = atn;
			this.clockIn = clk;
			this.dataIn = data;		
			busStateMayHaveChanged();	
		}

		@Override
		public boolean getDataIn() {
			return dataOut;
		}

		@Override
		public boolean getClockIn() {
			return clockOut;
		}

		@Override
		public boolean getDataOut() {
			return this.dataIn;
		}

		@Override
		public void setDataIn(boolean dataIn) {
			this.dataOut = dataIn;
			busStateMayHaveChanged();	
		}

		@Override
		public boolean getClockOut() {
			return this.clockIn;
		}
	}
	
	public Wire getWire() {
		return wire;
	}
	
	public void reset() 
	{
		busMode = BUSMODE_RECEIVE;
		busMode.onEnter();

		eoiBuffer.reset();
		sendBuffer.reset();

		devices.clear();

		devices.add( new Floppy(8) );

		bitsTransmitted = 0;
		states.clear();
		cycle = 0;
		wire.reset();
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
			states.add( new StateSnapshot( 
					wire.getATN() , 
					wire.getClockOut() , 
					wire.getDataOut() , 
					wire.getClockIn() , 
					wire.getDataIn() , busState , cycle , eoi ) );
			if ( states.size() > MAX_CYCLES_TO_KEEP ) {
				states.remove(0);
			}
		}
	}

	public List<StateSnapshot> getSnapshots()
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

	// waits for rising clock edge
	protected abstract class WaitForRisingEdge extends BusState {

		private boolean startWatching = false;

		public WaitForRisingEdge(String name) {
			super(name);
		}

		@Override
		protected final void tickHook() 
		{
			if ( wire.getClockOut() == false ) {
				startWatching = true;
			} else if ( startWatching && wire.getClockOut() == true ) {
				onRisingEdge();
			}
		}

		protected abstract void onRisingEdge();			

		@Override
		public final void onEnter() {
			startWatching = false;
			onEnterHook();
		}

		public void onEnterHook() {
		}
	}

	// waits for falling clock edge
	protected abstract class WaitForFallingEdge extends BusState {

		private boolean startWatching = false;

		public WaitForFallingEdge(String name) {
			super(name);
		}

		@Override
		protected void tickHook() {
			if ( wire.getClockOut() == true ) {
				startWatching = true;
				tickHook2();
			} else if ( startWatching && wire.getClockOut() == false ) {
				onFallingEdge();
			} else {
				tickHook2();
			}
		}

		protected void tickHook2() {
		}

		protected abstract void onFallingEdge();			

		@Override
		public final void onEnter() {
			startWatching = false;
			onEnterHook();
		}

		protected void onEnterHook() {
		}
	}	

	// states used when reading the bus
	protected final BusState ACK_BYTE;	
	protected final BusState READ_BIT;
	protected final BusState WAIT_FOR_VALID_DATA;
	protected final BusState LISTENING;	
	protected final BusState ACK_ATN;
	protected final BusState READY_FOR_DATA;
	protected final BusState WAIT_FOR_ATN;
	protected final BusState WAIT_FOR_TRANSMISSION;	
	protected final BusState ACK_EOI;	

	// helper vars used when timing bus cycles
	protected long waitingStartedAtCycle;
	protected long cyclesWaited;

	public IECBus()
	{
		ACK_BYTE = new BusState("ACK")
		{
			@Override
			protected void tickHook() {
				if ( cyclesWaited >= 60 ) // hold line for 60us
				{
					wire.setDataIn( false ); 
					setBusState( LISTENING );
				}	
			}

			@Override
			public void onEnter() {
				wire.setDataIn( true );
				startWaiting();
			}
		};

		// reads on bit on the rising (0->1) edge of the clock signal
		READ_BIT = new WaitForRisingEdge("BIT")
		{
			@Override
			protected void onRisingEdge() 
			{
				currentByte = (byte) (currentByte >>> 1);
				final boolean dataOut = wire.getDataOut();
				if ( DEBUG_VERBOSE ) 
				{
					System.out.println("GOT BIT no "+bitsTransmitted+": "+(dataOut ? "1" : "0" ) );
				}
				if ( dataOut ) {
					currentByte |= 1<<7; // bit was 1
				} else {
					currentByte &= ~(1<<7); // bit was 0
				}
				bitsTransmitted++;
				if ( bitsTransmitted == 8 ) {
					byteReceived( currentByte );
					setBusState( ACK_BYTE );
				} else {
					setBusState( WAIT_FOR_VALID_DATA );
				}
			}
		};

		// wait for falling edge of the clock signal
		WAIT_FOR_VALID_DATA = new WaitForFallingEdge("WAIT_VALID")
		{
			@Override
			protected void onFallingEdge() {
				setBusState( READ_BIT );
			}		
		};

		// acknowdlege EOI
		ACK_EOI = new BusState("ACK_EOI")
		{
			@Override
			protected void tickHook() {
				if ( cyclesWaited > 60 ) { // pull dataIn to low for 32 cycles
					bitsTransmitted = 0;
					wire.setDataIn(false);
					setBusState( READ_BIT );
				} 
			}

			@Override
			public void onEnter() {
				wire.setDataIn( true );
				startWaiting();
			}
		};

		// wait for talker to start sending, if this takes longer than
		// 200us this is the last byte and we need to acknowledge this
		// to the talker
		WAIT_FOR_TRANSMISSION = new WaitForFallingEdge("WAIT_FOR_TRANSMISSION")
		{
			@Override
			protected void onFallingEdge() 
			{
				if ( DEBUG_VERBOSE ) {
					System.out.println(cycle+": Talker starting to send after "+cyclesWaited+" cycles.");
				}									
				bitsTransmitted = 0;
				setBusState( READ_BIT );
			}

			@Override
			protected void tickHook2() 
			{
				if ( cyclesWaited > 200 ) 
				{
					if ( DEBUG_VERBOSE ) {
						System.out.println(cycle+": Waited "+cyclesWaited+" cycles => EOI");
					}					
					eoi = true;
					setBusState( ACK_EOI );
				}
			}

			@Override
			public void onEnterHook() {
				eoi = false;
				if ( DEBUG_VERBOSE ) {
					System.out.println(cycle+": Starting to wait at");
				}
				startWaiting();
			}
		};	

		READY_FOR_DATA = new BusState("READY")
		{
			@Override
			public void tickHook() {
				setBusState( WAIT_FOR_TRANSMISSION );
			}

			@Override
			public void onEnter() {
				wire.setDataIn(false); // tell talker we're ready for ata by releasing the data line then wait for rising clock
			}
		};		

		LISTENING = new WaitForRisingEdge("LISTENING")// wait for clock going false -> true
		{
			@Override
			protected void onRisingEdge() {
				setBusState( READY_FOR_DATA );				
			}
			@Override
			public void onEnterHook() {
				wire.setDataIn( true );
			}
		};		

		ACK_ATN = new BusState("ACK_ATN") {

			@Override
			protected void tickHook() 
			{
				if ( cyclesWaited > 20 ) { // acknowledge by holding DataIn == true for 20us
					wire.setDataIn( false );
					setBusState(LISTENING);
				}
			}

			@Override
			public void onEnter() 
			{
				wire.setDataIn(true);
				startWaiting();
			}
		};		

		WAIT_FOR_ATN = new BusState("WAIT_ATN")
		{
			@Override
			public void tickHook() 
			{
				if ( wire.getATN() ) {
					setBusState( ACK_ATN );
				} 
			}
		};		
		this.previousBusState = this.busState = WAIT_FOR_ATN;
	}

	@Override
	public String toString() {
		return "ATN: "+
	            (wire.getATN() ? "1" : "0" )+" , CLK_OUT: "+
				(wire.getClockOut() ? "1":"0") +" , DATA_OUT: "+
				(wire.getDataOut() ? "1" : "0") +"  ||   CLK_IN: "+
				(wire.getClockIn() ?"1":"0")+", DATA_IN: "+
				(wire.getDataIn() ?"1":"0");
	}

	private void byteReceived(byte data) 
	{
		System.out.println("BYTE RECEIVED: "+HexDump.toBinaryString( data )+" ( $"+HexDump.toHex( data ) +" )" );		
		for ( SerialDevice device : devices ) {
			device.receive( data , this );
		}
	}

	protected void busStateMayHaveChanged() 
	{
		if ( DEBUG_VERBOSE ) {
			final String s1 = StringUtils.rightPad( this.previousBusState.toString(),15);		
			final String s2 = StringUtils.rightPad( this.busState.toString(),15);
			System.out.println( cycle+": ["+s1+" -> "+s2+" ] : "+this);
		}
		takeSnapshot();
	}

	public void send(byte data,boolean eoi) 
	{
		this.sendBuffer.write( data );
		this.eoiBuffer.write( eoi ? (byte) 0xff : 00 ); 
		if ( this.busMode == BUSMODE_RECEIVE ) 
		{
			this.busMode = BUSMODE_SEND;
			this.busMode.onEnter();
		}
	}

	public void switchToReceive() 
	{
		if ( this.busMode == BUSMODE_SEND ) 
		{
			this.busMode = BUSMODE_RECEIVE;
			this.busMode.onEnter();
		}
	}

	public void tick() 
	{
		if ( previousBusState != busState ) 
		{
			previousBusState = busState;
			busState.onEnter();
		}
		busState.tick();
		cycle++;		
	}
}