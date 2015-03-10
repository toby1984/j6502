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

	public boolean DEBUG_VERBOSE = true;

	protected boolean eoi;

	// current cycle count
	protected long cycle;

	protected final String identifier;

	protected final BusMode CPU_TALKING = new BusMode("CPU_TALKING")
	{
		@Override
		public void onEnter()
		{
			init();
			setBusState( RECV_WAIT_FOR_ATN );
		}

		@Override
		public void onATN() {
			// already covered by state machine
		}
	};

	protected final BusMode DEVICE_TALKING = new BusMode("DEVICE_TALKING")
	{
		@Override
		public void onEnter()
		{
			init();
			setBusState( SEND_INIT );
		}

		@Override
		public void onATN()
		{
			// switch to listen mode
			setBusMode( CPU_TALKING );
		}
	};

	// states used when writing to the bus
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

	private BusState SEND_ATN;
	private BusState SEND_WAIT_ATN_ACK;

	// states used when reading from the bus
	protected final BusState RECV_WAIT_BUS_TURN_AROUND;
	protected final BusState RECV_ACK_BUS_TURN_AROUND2;
	protected final BusState RECV_ACK_BUS_TURN_AROUND1;
	protected final BusState RECV_ACK_FRAME;
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

	// used to keep track of whether the current talker wants to
	// switch roles / wants us to talk and be a listener
	protected boolean talkReceived = false;

	// buffer holding data to be sent
	private final RingBuffer sendBuffer = new RingBuffer();

	// used to track whether the corresponding byte
	// in the send buffer is the next-to-last byte
	// before an UNLISTEN or UNTALK byte
	private final RingBuffer eoiBuffer = new RingBuffer();

	private final List<SerialDevice> devices = new ArrayList<>();
	private final List<StateSnapshot> states = new ArrayList<>();

	protected BusMode busMode = CPU_TALKING;

	protected boolean sendATN = false;

	protected int bitsTransmitted = 0;
	protected byte currentByte;

	protected BusState previousBusState;
	protected BusState busState;

	/**
	 * Bus lines going into the computer (devices -> CPU).
	 * ATN line is ignored on this one.
	 */
	protected Wire inputWire;

	/**
	 * Bus lines going out of the computer (CPU -> devices).
	 */
	protected Wire outputWire;

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
		private final String identifier;

		public BusMode(String identifier) {
			this.identifier = identifier;
		}
		public abstract void onEnter();

		public abstract void onATN();

		protected final void init()
		{
			sendATN = false;
			sendBuffer.reset();
			eoiBuffer.reset();
			bitsTransmitted = 0;
			eoi = false;
			talkReceived = false;
		}
		@Override
		public String toString() {
			return identifier;
		}
	}

	protected static class Wire
	{
		protected final String id;
		protected boolean atn;
		protected boolean clock;
		protected boolean data;

		private boolean wireStateChanged;

		public Wire(String id) {
			this.id = id;
		}

		public void reset() {
			atn = false;
			clock = false;
			data = false;
		}

		public void setATN(boolean value)
		{
			if ( CAPTURE_BUS_SNAPSHOTS )
			{
				wireStateChanged |= (this.atn != value );
			}
			this.atn = value;
		}

		public boolean wireStateChanged() {
			boolean result = wireStateChanged;
			wireStateChanged = false;
			return result;
		}

		public void setState(boolean atn, boolean dataOut, boolean clkOut)
		{
			if ( CAPTURE_BUS_SNAPSHOTS )
			{
				wireStateChanged |= ( this.data != dataOut || this.clock != clkOut || this.atn != atn );
			}
			this.atn = atn;
			this.data = dataOut;
			this.clock = clkOut;
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
				wireStateChanged |= (this.data != value);
			}
			this.data = value;
		}

		public void setClock(boolean value)
		{
			if ( CAPTURE_BUS_SNAPSHOTS )
			{
				wireStateChanged |= this.clock != value;
			}
			this.clock = value;
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

	/**
	 * Sets the bus lines going out of the computer (CPU -> devices).
	 */
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

	/**
	 * Sets the bus lines going into the computer (devices -> CPU).
	 * ATN line is ignored on this one.
	 */
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

		states.clear();
		outputWire.reset();

		cycle = 0;
		bitsTransmitted = 0;
		talkReceived = false;
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

	public IECBus(String identifier)
	{
		this.identifier = identifier;

		/* =======================================
		 * === Bus states used while a device  ===
		 * === is transmitting data to the CPU ===
		 * =======================================
		 */

		SEND_BETWEEN_BYTES = new BusState("SEND_BETWEEN_BYTES") {

			@Override
			protected void tickHook()
			{
				if ( cyclesWaited >= 100 )
				{
					if ( bitsTransmitted < 8 ) { // loop until 8 bits transmitted
						setBusState( SEND_PREPARE_TRANSMISSION );
					} else {
						setBusState( SEND_INIT );
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

		SEND_FRAME_HANDSHAKE = new BusState("SEND_FRAME_HANDSHAKE") {

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
				inputWire.setClock( true );
				startWatching = true;
			}
		};

		SEND_DATA_VALID = new BusState("SEND_TRANSMISSION_START") {

			@Override
			protected void tickHook()
			{
				if ( cyclesWaited >= 20 ) { // hold clock line true for 20us
					if ( bitsTransmitted < 8 ) {
						setBusState( SEND_FALLING_EDGE1 );
					} else {
						setBusState( SEND_FRAME_HANDSHAKE );
					}
				}
			}
			@Override
			public void onEnter()
			{
				inputWire.setClock( false ); // rising edge signals listener that the data is now valid
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
				inputWire.setClock( true );
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
//				inputWire.setClock( false ); // pull up clock line
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
				if ( outputWire.getData() == true ) {
					startWatching = true;
				} else if ( startWatching && outputWire.getData() == false ) {
					setBusState( SEND_RECV_EOI_ACK2 );
				}
			}

			@Override
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
			public void onEnter()
			{
				if ( sendATN ) {
					setBusState( SEND_ATN );
				}
				// clock false -> true
				inputWire.setClock( true ); // signal ready to send
				startWatching = false;
			}
		};

		SEND_INIT = new BusState("SEND_INIT") {

			@Override
			protected void tickHook()
			{
				if ( ! sendBuffer.isEmpty() && cyclesWaited > 20 )
				{
					currentByte = sendBuffer.read();
					eoi = ( eoiBuffer.read() != 0);
					System.out.println("Sending byte: "+HexDump.toHex(currentByte)+" [EOI: "+eoi+"]");
					bitsTransmitted = 0;
					setBusState( SEND_PREPARE_TRANSMISSION );
				}
			}

			@Override
			public void onEnter() {
				inputWire.setData( false );
				inputWire.setClock( false );
				startWaiting();
			}
		};

		SEND_WAIT_ATN_ACK = new BusState("SEND_WAIT_ATN_ACK") {

			@Override
			protected void tickHook()
			{
				if ( cyclesWaited > 1000 ) { // waited too long => device not available
					// TODO: Bus error
				}

				if ( outputWire.getData() )
				{
					setBusState( SEND_INIT );
				}
			}

			@Override
			public void onEnter() {
				startWaiting();
			}
		};

		SEND_ATN = new BusState("SEND_ATN") {

			@Override
			protected void tickHook()
			{
				setBusState(SEND_WAIT_ATN_ACK);
			}

			@Override
			public void onEnter() {
				sendATN = false;
				inputWire.setATN(true);
				inputWire.setData( false );
				inputWire.setClock( false );
			}
		};


		/* =======================================
		 * === Bus states used while receiving ===
		 * === data transmitted by the CPU     ===
		 * =======================================
		 */
		RECV_ACK_BUS_TURN_AROUND2 = new BusState("RECV_ACK_BUS_TURN_AROUND2")
		{
			@Override
			protected void tickHook() {
				if ( cyclesWaited >= 160 )
				{
					System.out.println(cycle+"IEC Bus turneded around, CPU ready to listen");
					setBusMode( DEVICE_TALKING );

					// bus turn around happens after a frame has been ack'ed ,
					// to prevent the data receiver from starting to send pre-maturely I
					// delayed the call to byteReceived() up to this point
					// (see RECV_ACK_FRAME)
					byteReceived( currentByte );
				}
			}

			@Override
			public void onEnter() {
				inputWire.setClock( true );
				startWaiting();
			}
		};

		RECV_ACK_BUS_TURN_AROUND1 = new BusState("RECV_ACK_BUS_TURN_AROUND1")
		{
			@Override
			protected void tickHook() {
				if ( cyclesWaited >= 20 )
				{
					setBusState( RECV_ACK_BUS_TURN_AROUND2 );
				}
			}

			@Override
			public void onEnter() {
				inputWire.setData( true );
				startWaiting();
			}
		};

		RECV_WAIT_BUS_TURN_AROUND = new BusState("WAIT_BUS_TURN_AROUND")
		{
			@Override
			protected void tickHook()
			{
				// wait for clock to become FALSE, data to become TRUE within 20...100us
				// this is the indicator that the current talker (=CPU) has now switched to LISTEN mode
				if ( cyclesWaited > 20 ) {
					if ( cyclesWaited > 100 )
					{
						System.out.println("No IEC bus turn-around within 100us");
						setBusState( RECV_LISTENING );
						return;
					}
					if ( outputWire.getClock() && outputWire.getData() == false ) { // talker wants to switch to listening, ack this by lowering clock again for 80us
						setBusState( RECV_ACK_BUS_TURN_AROUND1 );
					}
				}
			}
			@Override
			public void onEnter() {
				startWaiting();
				inputWire.setClock( true );
				inputWire.setData( false );
			}
		};

		RECV_ACK_FRAME = new BusState("ACK_FRAME")
		{
			private boolean startWatching;

			@Override
			protected void tickHook()
			{
				if ( outputWire.getATN() ) {
					startWatching=true;
				}
				else if ( startWatching && ! outputWire.getATN() )
				{
					if ( DEBUG_VERBOSE) {
						System.out.println( cycle+": ATN released after "+cyclesWaited+" cycles ");
					}
					if ( talkReceived )
					{
						// wait for clock to become FALSE, data to become TRUE within 20...100us
						// this is the indicator that the current talker (=CPU) has now switched to LISTEN mode
						setBusState( RECV_WAIT_BUS_TURN_AROUND );
						return;
					}
				}
				if ( cyclesWaited >= 60 ) // hold line for 60us
				{
					byteReceived( currentByte );
					setBusState( RECV_LISTENING );
				}
			}

			@Override
			public void onEnter() {
				inputWire.setData( true );
				startWaiting();
				startWatching = false;
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
					setBusState( RECV_ACK_FRAME );
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
			public void onEnterHook()
			{
				inputWire.setData( true );
			}
		};

		RECV_ACK_ATN = new BusState("ACK_ATN") {

			@Override
			protected void tickHook()
			{
				inputWire.setData( false );
				setBusState(RECV_LISTENING);
			}

			@Override
			public void onEnter()
			{
				inputWire.setData(true);
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

		 // Bus lines going into the computer (devices -> CPU).
		 // ATN line is ignored on this one.
		setInputWire( new Wire("devices->CPU") );

		 // Bus lines going out of the computer (CPU -> devices).
		setOutputWire( new Wire("CPU->devices") );
	}

	@Override
	public String toString() {
		return "[BUS: "+identifier+"] "+outputWire.toString()+" || "+inputWire.toString();
	}

	private void byteReceived(byte data)
	{
		if ( outputWire.getATN() )
		{
			// keep track of whether the sender (CPU)
			// wants us to start talking , needed so that
			// we can properly detect when the CPU is asking for
			// a bus turn-around after an FRAME ACK.
			if ( ( data   & 0xff) == 0x5f ) { // UNTALK
				talkReceived = false;
			}
			if ( ( data   & 0xff) == 0x3f ) { // UNLISTEN
				talkReceived = false;
			}
			if ( ( data & 0b1111_0000) == 0x20 ) { // LISTEN
				talkReceived = false;
			}
			if ( ( data   & 0b1110_0000) == 0x40 ) { // TALK
				talkReceived = true;
			}
		}

		System.out.println("BYTE RECEIVED: "+HexDump.toBinaryString( data )+" ( $"+HexDump.toHex( data ) +" )" );
		for ( SerialDevice device : devices ) {
			device.receive( data , eoi );
		}
	}

	public void send(byte data,boolean eoi,boolean sendATN)
	{
		if ( ! isBusReadyToSend() )
		{
			throw new IllegalStateException("Cannot send data , bus not turned around yet");
//			setBusMode( DEVICE_TALKING ); // also does setBusMode(SEND_INIT)
//			if ( sendATN ) {
//				throw new RuntimeException("Switching CPU TALK -> LISTEN and requesting ATN not implemented yet");
//			}
//			// TODO: Assumption here is that the CPU already asked for an ACK
//			// TODO: that we're now the talker ... how can I guarantee that the
//			// TODO: CPU has asked for acknowledgement ???
//			setBusState( SEND_ACK_TALKING );
		}

		if ( sendATN )
		{
			setBusState( SEND_ATN );
		}
		this.sendBuffer.write( data );
		this.eoiBuffer.write( eoi ? (byte) 0xff : 00 );
	}

	public boolean isBusReadyToSend()
	{
		return this.busMode == DEVICE_TALKING;
	}

	protected void setBusMode(BusMode mode)
	{
		if ( this.busMode != mode ) {
			if ( DEBUG_VERBOSE ) {
				System.out.println("["+this+"] Switching to bus mode "+mode);
			}
			this.busMode = mode;
			this.busMode.onEnter();
		}
	}

	public void tick()
	{
		if ( CAPTURE_BUS_SNAPSHOTS )
		{
			if ( inputWire.wireStateChanged() || outputWire.wireStateChanged() )
			{
				if ( DEBUG_VERBOSE ) {
					final String s1 = StringUtils.rightPad( this.previousBusState.toString(),15);
					final String s2 = StringUtils.rightPad( this.busState.toString(),15);
					System.out.println( cycle+": ["+s1+" -> "+s2+" ] : "+this);
				}
				takeSnapshot();
			}
		}

		if ( previousBusState != busState )
		{
			previousBusState = busState;
			busState.onEnter();
		} else {
			busState.tick();
		}
		devices.forEach( d -> d.tick() );
		cycle++;
	}

	protected void onATN() // called when ATN line changes false -> true
	{
		busMode.onATN();
	}

	public boolean isSendBufferEmpty() {
		return sendBuffer.isEmpty();
	}

	public boolean isSendBufferFull() {
		return sendBuffer.isFull();
	}
}