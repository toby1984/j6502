package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.utils.HexDump;
import de.codesourcery.j6502.utils.RingBuffer;

public class IECBus
{
	public static final boolean CAPTURE_BUS_SNAPSHOTS = true;
	
	private final int MAX_CYCLES_TO_KEEP = 200;

	private static final boolean DEBUG_VERBOSE = true;

	protected boolean eoi;
	
	// current cycle count
	protected long cycle;
	
	protected final BusMode CPU_TALKING = new BusMode() 
	{
		public void onEnter() 
		{
			init();
			setBusState( RECV_WAIT_FOR_ATN );
		}
		
		public void onATN() {
			// already covered by state machine
		}
	};

	protected final BusMode DEVICE_TALKING = new BusMode() 
	{
		public void onEnter() 
		{
			init();
			setBusState( SEND_INIT );
		}
		
		public void onATN() 
		{
			// switch to listen mode
			setBusMode( CPU_TALKING );
		}
	};
	
	// states used when writing to the bus
	private BusState SEND_PREPARE_NEXT_BYTE;
	private BusState SEND_BETWEEN_BYTES;
	private BusState SEND_FRAME_HANDSHAKE;		
	private BusState SEND_DATA_VALID;	
	private BusState SEND_SET_DATA_LINE;
	private BusState SEND_FALLING_EDGE1;
	private BusState SEND_RECV_EOI_ACK2;
	private BusState SEND_RECV_EOI_ACK1;
	private BusState SEND_TRANSMISSION_START;	
	private BusState SEND_EOI;
	private BusState SEND_INIT;
	private BusState SEND_PREPARE_TRANSMISSION;

	// states used when reading from the bus
	protected final BusState RECV_ACK_BYTE;	
	protected final BusState RECV_READ_BIT;
	protected final BusState RECV_WAIT_FOR_VALID_DATA;
	protected final BusState RECV_LISTENING;	
	protected final BusState RECV_ACK_ATN;
	protected final BusState RECV_READY_FOR_DATA;
	protected final BusState RECV_WAIT_FOR_ATN;
	protected final BusState RECV_WAIT_FOR_TRANSMISSION;	
	protected final BusState RECV_ACK_EOI;	

	// helper vars used when timing bus cycles
	protected long waitingStartedAtCycle;
	protected long cyclesWaited;	

	// buffer holding data to be sent
	private final RingBuffer sendBuffer = new RingBuffer();
	
	// used to track whether the corresponding byte 
	// in the send buffer is the next-to-last byte
	// before an UNLISTEN or UNTALK byte
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
	 * ATN line is ignored on this one.
	 */
	protected Wire inputWire = new Wire("INPUT",this);	
	
	/** 
	 * Bus lines going out of the computer (CPU -> devices).
	 */
	protected Wire outputWire = new Wire("OUTPUT",this);	
	
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

	protected abstract class BusMode 
	{
		public abstract void onEnter();
		
		public abstract void onATN(); 		
		
		protected final void init() 
		{
			sendBuffer.reset();
			eoiBuffer.reset();
			bitsTransmitted = 0;
			eoi = false;			
		}
	}

	protected static final class Wire
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
			if ( CAPTURE_BUS_SNAPSHOTS ) 
			{
				if ( this.data != dataOut || this.clock != clkOut || this.atn != atn ) 
				{
					if ( atn && this.atn == false ) {
						bus.onATN();
					}
					this.atn = atn;
					this.data = dataOut;
					this.clock = clkOut;
					bus.busStateHasChanged();
				}
			} 
			else 
			{
				if ( atn && this.atn == false ) {
					bus.onATN();
				}				
				this.atn = atn;
				this.data = dataOut;
				this.clock = clkOut;
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

		public void setData(boolean value) 
		{
			if ( CAPTURE_BUS_SNAPSHOTS ) 
			{
				if ( this.data != value ) {
					this.data = value;
					bus.busStateHasChanged();
				}
			} else {
				this.data = value;
			}
		}

		public void setClock(boolean value) 
		{
			if ( CAPTURE_BUS_SNAPSHOTS ) 
			{
				if ( this.clock != value ) 
				{
					this.clock = value;
					bus.busStateHasChanged();
				}
			} else {
				this.clock = value;
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
	
	public void addDevice(SerialDevice device) {
		
		if ( devices.stream().anyMatch( d -> d.getPrimaryAddress() == device.getPrimaryAddress() ) ) {
			throw new IllegalArgumentException("A device with ID "+device.getPrimaryAddress()+" has already been registered");
		}
		devices.add( device );
		device.onAttach( this );
	}
	
	public Optional<SerialDevice> getDevice(int primaryAddress) 
	{
		return devices.stream().filter( d -> d.getPrimaryAddress() == primaryAddress ).findFirst();
	}
	
	public void reset() 
	{
		setBusMode( CPU_TALKING );

		eoiBuffer.reset();
		sendBuffer.reset();

		devices.forEach( device -> device.reset() );

		bitsTransmitted = 0;
		states.clear();
		cycle = 0;
		outputWire.reset();
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
	
	public IECBus()
	{
		/* =======================================
		 * === Bus states used while a device  ===
		 * === is transmitting data to the CPU ===
		 * =======================================
		 */
		
		SEND_PREPARE_NEXT_BYTE = new BusState("SEND_PREPARE_NEXT_BYTE") {

			@Override
			protected void tickHook() 
			{
				if ( ! sendBuffer.isEmpty() ) 
				{
					currentByte = sendBuffer.read();
					eoi = ( eoiBuffer.read() != 0);
					bitsTransmitted = 0;
					setBusState( SEND_PREPARE_TRANSMISSION );
				}
			}
		};
		
		SEND_BETWEEN_BYTES = new BusState("SEND_BETWEEN_BYTES") {

			@Override
			protected void tickHook() 
			{
				if ( cyclesWaited >= 100 ) 
				{
					if ( bitsTransmitted < 8 ) { // loop until 8 bits transmitted
						setBusState( SEND_PREPARE_TRANSMISSION );
					} else {
						setBusState( SEND_PREPARE_NEXT_BYTE );
					}
				}
			}
			
			@Override
			public void onEnter() 
			{
				inputWire.setData( false );
				startWaiting();
			}
		};
			
		SEND_FRAME_HANDSHAKE = new BusState("SEND_TRANSMISSION_START") {

			private boolean startWatching = false; 
			@Override
			protected void tickHook() 
			{
				if ( outputWire.getData() == false ) {
					startWatching = true;
				} else if ( startWatching && outputWire.getData() == true ) {
					setBusState( SEND_BETWEEN_BYTES );
				}
			}
			
			@Override
			public void onEnter() 
			{
				inputWire.setClock( false );
				startWatching = true;
			}
		};
		
		SEND_DATA_VALID = new BusState("SEND_TRANSMISSION_START") {

			@Override
			protected void tickHook() 
			{
				if ( cyclesWaited >= 20 ) { // hold clock line true for 20us
					setBusState( SEND_FRAME_HANDSHAKE );
				}
			}
			@Override
			public void onEnter() 
			{
				inputWire.setClock( true ); // rising edge signals listener that the data is now valid
				startWaiting();
			}
		};
		SEND_SET_DATA_LINE = new BusState("SEND_TRANSMISSION_START") {

			@Override
			protected void tickHook() 
			{
				if ( cyclesWaited >= 10 ) // wait 10us before raising clock (so total time clock==false is 20us)  
				{
					setBusState( SEND_DATA_VALID );
				}
			}
			
			@Override
			public void onEnter() 
			{
				// put bit on data line
				inputWire.setData( (currentByte & 1 ) != 0 );
				currentByte = (byte) (currentByte >> 1);	
				bitsTransmitted++;
				startWaiting();
			}
		};
		
		SEND_FALLING_EDGE1 = new BusState("SEND_FALLING_EDGE1") {

			@Override
			protected void tickHook() 
			{
				if ( cyclesWaited >= 10 ) { // wait 10us before putting bit on data line 
					setBusState( SEND_SET_DATA_LINE );
				}
			}
			@Override
			public void onEnter() {
				inputWire.setClock( false );
				startWaiting();
			}
		};
		
		SEND_TRANSMISSION_START = new BusState("SEND_TRANSMISSION_START") {

			@Override
			protected void tickHook() {
				if ( cyclesWaited >= 40 ) { // wait 40us before starting to actually send
					setBusState(SEND_FALLING_EDGE1);
				}
			}
			
			@Override
			public void onEnter() {
				inputWire.setClock( true ); // pull up clock line
				startWaiting();
			}
		};
		
		SEND_RECV_EOI_ACK2 = new BusState("SEND_RECV_EOI_ACK2") 
		{
			@Override
			protected void tickHook() { // wait for falling edge of data line
				if ( outputWire.getData() == false ) {
					setBusState( SEND_TRANSMISSION_START );
				}
			}
		};		
		
		SEND_RECV_EOI_ACK1 = new BusState("SEND_RECV_EOI_ACK1") {

			private boolean startWatching;
			
			@Override
			protected void tickHook() { // wait for rising edge of data line
				if ( outputWire.getData() == false ) {
					startWatching = true;
				} else if ( startWatching && outputWire.getData() == true ) {
					setBusState( SEND_RECV_EOI_ACK2 );
				}
			}
			
			public void onEnter() {
				startWatching = false;
			}
		};
		
		SEND_EOI = new BusState("SEND_EOI") 
		{
			@Override
			protected void tickHook() 
			{
				if ( cyclesWaited >= 250 ) { // wait more than 200us and then make sure the receiver acknowledged the EOI
					setBusState( SEND_RECV_EOI_ACK1 );
				}
			}
			
			@Override
			public void onEnter() {
				eoi = false;
				startWaiting();
			}
		};

		
		SEND_PREPARE_TRANSMISSION = new BusState("SEND_READY") {

			private boolean startWatching;
			
			@Override
			protected void tickHook() 
			{
				if ( outputWire.getData() ) {
					startWatching = true;
				} else if ( startWatching && ! outputWire.getData() ) { // falling edge detected, listener ready to receive
					if ( eoi ) {
						setBusState( SEND_EOI );
					} else {
						setBusState( SEND_TRANSMISSION_START );
					}
				}
			}
			
			@Override
			public void onEnter() {
				inputWire.setClock( false ); // signal ready to send
				startWatching = false;
			}
		};
		
		SEND_INIT = new BusState("SEND_INIT") {

			@Override
			protected void tickHook() {
				if ( cyclesWaited > 20 ) {
					setBusState(SEND_PREPARE_TRANSMISSION);
				}
			}
			
			@Override
			public void onEnter() {
				inputWire.setData( true );
				inputWire.setClock( true );
				startWaiting();
			}
		};		
		
		
		/* =======================================
		 * === Bus states used while receiving ===
		 * === data transmitted by the CPU     ===
		 * =======================================
		 */
		RECV_ACK_BYTE = new BusState("ACK")
		{
			@Override
			protected void tickHook() {
				if ( cyclesWaited >= 60 ) // hold line for 60us
				{
					inputWire.setData( false ); 
					setBusState( RECV_LISTENING );
				}	
			}

			@Override
			public void onEnter() {
				inputWire.setData( true );
				startWaiting();
			}
		};

		// reads on bit on the rising (0->1) edge of the clock signal
		RECV_READ_BIT = new WaitForRisingEdge("BIT")
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
					setBusState( RECV_ACK_BYTE );
				} else {
					setBusState( RECV_WAIT_FOR_VALID_DATA );
				}
			}
		};

		// wait for falling edge of the clock signal
		RECV_WAIT_FOR_VALID_DATA = new WaitForFallingEdge("WAIT_VALID")
		{
			@Override
			protected void onFallingEdge() {
				setBusState( RECV_READ_BIT );
			}		
		};

		// acknowdlege EOI
		RECV_ACK_EOI = new BusState("ACK_EOI")
		{
			@Override
			protected void tickHook() {
				if ( cyclesWaited > 60 ) { // pull dataIn to low for 32 cycles
					bitsTransmitted = 0;
					inputWire.setData(false);
					setBusState( RECV_READ_BIT );
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
		RECV_WAIT_FOR_TRANSMISSION = new WaitForFallingEdge("WAIT_FOR_TRANSMISSION")
		{
			@Override
			protected void onFallingEdge() 
			{
				if ( DEBUG_VERBOSE ) {
					System.out.println(cycle+": Talker starting to send after "+cyclesWaited+" cycles.");
				}									
				bitsTransmitted = 0;
				setBusState( RECV_READ_BIT );
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
					setBusState( RECV_ACK_EOI );
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

		RECV_READY_FOR_DATA = new BusState("READY")
		{
			@Override
			public void tickHook() {
				setBusState( RECV_WAIT_FOR_TRANSMISSION );
			}

			@Override
			public void onEnter() {
				inputWire.setData(false); // tell talker we're ready for ata by releasing the data line then wait for rising clock
			}
		};		

		RECV_LISTENING = new WaitForRisingEdge("LISTENING")// wait for clock going false -> true
		{
			@Override
			protected void onRisingEdge() {
				setBusState( RECV_READY_FOR_DATA );				
			}
			@Override
			public void onEnterHook() {
				inputWire.setData( true );
			}
		};		

		RECV_ACK_ATN = new BusState("ACK_ATN") {

			@Override
			protected void tickHook() 
			{
				if ( cyclesWaited > 20 ) { // acknowledge by holding DataIn == true for 20us
					inputWire.setData( false );
					setBusState(RECV_LISTENING);
				}
			}

			@Override
			public void onEnter() 
			{
				inputWire.setData(true);
				startWaiting();
			}
		};		

		RECV_WAIT_FOR_ATN = new BusState("WAIT_ATN")
		{
			@Override
			public void tickHook() 
			{
				if ( outputWire.getATN() ) {
					setBusState( RECV_ACK_ATN );
				} 
			}
		};		
		this.previousBusState = this.busState = RECV_WAIT_FOR_ATN;
	}

	@Override
	public String toString() {
		return outputWire.toString()+" || "+inputWire.toString();
	}

	private void byteReceived(byte data) 
	{
		System.out.println("BYTE RECEIVED: "+HexDump.toBinaryString( data )+" ( $"+HexDump.toHex( data ) +" )" );		
		for ( SerialDevice device : devices ) {
			device.receive( data );
		}
	}

	protected void busStateHasChanged() 
	{
		if ( DEBUG_VERBOSE ) {
			final String s1 = StringUtils.rightPad( this.previousBusState.toString(),15);		
			final String s2 = StringUtils.rightPad( this.busState.toString(),15);
			System.out.println( cycle+": ["+s1+" -> "+s2+" ] : "+this);
		}
		if( CAPTURE_BUS_SNAPSHOTS ) {
			takeSnapshot();
		}
	}

	public void send(byte data,boolean eoi) 
	{
		if ( this.busMode == CPU_TALKING ) 
		{
			setBusMode( DEVICE_TALKING );
		}
		// write to buffer AFTER switching bus mode since
		// bus mode will clear all buffers
		this.sendBuffer.write( data );
		this.eoiBuffer.write( eoi ? (byte) 0xff : 00 ); 
	}

	protected void setBusMode(BusMode mode) 
	{
		if ( this.busMode != mode ) {
			this.busMode = mode;
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
		devices.forEach( d -> d.tick() );
		cycle++;		
	}
	
	protected void onATN() // called when ATN line changes false -> true 
	{
		busMode.onATN();
	}
}