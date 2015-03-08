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

	protected final BusMode CPU_TALKING = new BusMode() 
	{
		public void onEnter() 
		{
			previousBusState = busState = WAIT_FOR_ATN;
		}
	};

	protected final BusMode DEVICE_TALKING = new BusMode() 
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

	protected BusMode busMode = CPU_TALKING;

	protected int bitsTransmitted = 0;
	protected byte currentByte;

	protected BusState previousBusState;	
	protected BusState busState;
	
	/** 
	 * Bus lines going into the computer (devices -> CPU).
	 */
	protected Wire inputWire = new Wire("INPUT",this);	
	
	/** 
	 * Bus lines going out of the computer (CPU -> devices).
	 */
	protected Wire outputWire = new Wire("OUTPUT",this);
	
	public static final class Wire
	{
		private final String id;
		private boolean atn;
		private boolean clock;
		private boolean data;
		private IECBus bus;
		
		public Wire(String id,IECBus bus) {
			this.id = id;
			this.bus = bus;
		}
		
		public final void reset() {
			atn = false;
			clock = false;
			data = false;
		}

		public void setState(boolean atn, boolean dataOut, boolean clkOut) 
		{
			if ( this.data != dataOut || this.clock != clkOut || this.atn != atn ) {
				this.atn = atn;
				this.data = dataOut;
				this.clock = clkOut;
				bus.busStateHasChanged();
			}
		}
		
		public void setBus(IECBus bus) {
			if (bus == null) {
				throw new IllegalArgumentException("bus must not be NULL");
			}
			this.bus = bus;
		}

		public boolean getATN() {
			return atn;
		}

		public boolean getData() {
			return data;
		}

		public boolean getClock() {
			return clock;
		}

		public void setData(boolean value) {
			if ( this.data != value ) {
				this.data = value;
				bus.busStateHasChanged();
			}
		}

		public void setClock(boolean value) 
		{
			if ( this.clock != value ) {
				this.clock = value;
				bus.busStateHasChanged();
			}
		}
		
		@Override
		public String toString() {
			return id+" => ATN: "+
		            (getATN() ? "1" : "0" )+" , CLK: "+
					(getClock() ? "1":"0") +" , DATA: "+
					(getData() ? "1" : "0");
		}
	}
	
	/**
	 * Returns the bus lines going from the CPU to the devices.
	 * @return
	 */
	public Wire getOutputWire() {
		return outputWire;
	}
	
	public void setOutputWire(Wire outputWire) {
		this.outputWire = outputWire;
	}
	
	/**
	 * Returns the bus lines going from devices to the CPU.
	 * 
	 * @return
	 */
	public Wire getInputWire() {
		return inputWire;
	}
	
	public void setInputWire(Wire inputWire) {
		this.inputWire = inputWire;
	}
	
	public void reset() 
	{
		busMode = CPU_TALKING;
		busMode.onEnter();

		eoiBuffer.reset();
		sendBuffer.reset();

		devices.clear();

		devices.add( new Floppy(8) );

		bitsTransmitted = 0;
		states.clear();
		cycle = 0;
		outputWire.reset();
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

		public StateSnapshot(Wire outputWire,Wire inputWire,BusState busState,long cycle,boolean eoi)
		{
			this.cycle = cycle;
			this.eoi = eoi;
			this.atn = outputWire.getATN();
			this.clkOut = outputWire.getClock();
			this.dataOut = outputWire.getData();
			this.clkIn = inputWire.getClock();
			this.dataIn = inputWire.getData();
			this.busState = busState;
		}
	}

	private void takeSnapshot()
	{
		synchronized(states) {
			states.add( new StateSnapshot( outputWire, inputWire , busState , cycle , eoi ) );
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
			if ( outputWire.getClock() == false ) {
				startWatching = true;
			} else if ( startWatching && outputWire.getClock() == true ) {
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
			if ( outputWire.getClock() == true ) {
				startWatching = true;
				tickHook2();
			} else if ( startWatching && outputWire.getClock() == false ) {
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
					inputWire.setData( false ); 
					setBusState( LISTENING );
				}	
			}

			@Override
			public void onEnter() {
				inputWire.setData( true );
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
				final boolean dataOut = outputWire.getData();
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
					inputWire.setData(false);
					setBusState( READ_BIT );
				} 
			}

			@Override
			public void onEnter() {
				inputWire.setData( true );
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
				inputWire.setData(false); // tell talker we're ready for ata by releasing the data line then wait for rising clock
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
				inputWire.setData( true );
			}
		};		

		ACK_ATN = new BusState("ACK_ATN") {

			@Override
			protected void tickHook() 
			{
				if ( cyclesWaited > 20 ) { // acknowledge by holding DataIn == true for 20us
					inputWire.setData( false );
					setBusState(LISTENING);
				}
			}

			@Override
			public void onEnter() 
			{
				inputWire.setData(true);
				startWaiting();
			}
		};		

		WAIT_FOR_ATN = new BusState("WAIT_ATN")
		{
			@Override
			public void tickHook() 
			{
				if ( outputWire.getATN() ) {
					setBusState( ACK_ATN );
				} 
			}
		};		
		this.previousBusState = this.busState = WAIT_FOR_ATN;
	}

	@Override
	public String toString() {
		return outputWire.toString()+" || "+inputWire.toString();
	}

	private void byteReceived(byte data) 
	{
		System.out.println("BYTE RECEIVED: "+HexDump.toBinaryString( data )+" ( $"+HexDump.toHex( data ) +" )" );		
		for ( SerialDevice device : devices ) {
			device.receive( data , this );
		}
	}

	protected void busStateHasChanged() 
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
		if ( this.busMode == CPU_TALKING ) 
		{
			this.busMode = DEVICE_TALKING;
			this.busMode.onEnter();
		}
	}

	public void switchToReceive() 
	{
		if ( this.busMode == DEVICE_TALKING ) 
		{
			this.busMode = CPU_TALKING;
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