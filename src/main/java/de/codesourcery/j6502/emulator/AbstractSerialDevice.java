package de.codesourcery.j6502.emulator;

import java.util.HashMap;
import java.util.Map;

import de.codesourcery.j6502.utils.RingBuffer;

public class AbstractSerialDevice implements SerialDevice {

	protected  final int primaryAddress;

	protected static final int EOI_DELAY_CYCLES = 100;

	protected  boolean clk=false; // bus == high
	protected  boolean data=false; // bus == high

	protected boolean waitingStarted;	
	protected long cyclesInState = 0;
	protected  State state;
	protected  State nextState;

	protected  int currentBit = 0;
	protected  int currentByte = 0;

	protected boolean deviceSelected = false;

	private Channel activeChannel;

	protected DeviceState deviceState = DeviceState.IDLE;

	protected static enum DeviceState { LISTENING, TALK_REQUESTED , TALKING, IDLE };

	private final Map<Integer,Channel> channels = new HashMap<>();

	protected static final class Channel
	{
		public final int channelNo;
		public boolean isOpen;
		public final RingBuffer receiveBuffer = new RingBuffer();
		public final RingBuffer sendBuffer = new RingBuffer();		

		public Channel(int channelNo)
		{
			this.channelNo = channelNo;
		}

		public void receive(int data)
		{
			receiveBuffer.write( (byte) data );
		}

		public void clear()
		{
			receiveBuffer.reset();
			sendBuffer.reset();
		}

		@Override public String toString() { return "Channel #"+channelNo+" (open: "+isOpen+")"; }
	}

	protected static enum BusCommand
	{
		LISTEN(true),OPEN(true),OPEN_CHANNEL(true),TALK(true),UNTALK(false),UNLISTEN(false),CLOSE(true),UNKNOWN(false);

		public final boolean supportsPayload;

		private BusCommand(boolean supportsPayload) {
			this.supportsPayload = supportsPayload;
		}
	}

	protected abstract class State
	{
		public final String name;

		public State(String name) { this.name = name; }

		public final void tick(IECBus bus, boolean atnLowered) {
			cyclesInState++;
			tickHook(bus, atnLowered);
		}

		public abstract void tickHook(IECBus bus, boolean atnLowered);

		public final void onEnter(IECBus bus, boolean atnLowered) {
			cyclesInState = 0;
			onEnterHook(bus,atnLowered);
		}

		public void onEnterHook(IECBus bus,boolean atnLowered) {
		}

		@Override
		public String toString() { return name; }
	}

	protected abstract class WaitForFallingEdge extends State {

		public WaitForFallingEdge(String name) {
			super(name);
		}

		protected abstract void onEdge(IECBus bus);

		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( bus.getClk() ) {
				waitingStarted = true;
			} else if ( waitingStarted && ! bus.getClk() ) {
				onEdge(bus);
			}
		}

		@Override
		public final void onEnterHook(IECBus bus,boolean atnLowered) {
			waitingStarted = false;
		}
	}

	protected abstract class WaitForRisingEdge extends State {

		public WaitForRisingEdge(String name) {
			super(name);
		}

		protected abstract void onEdge(IECBus bus);

		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( ! bus.getClk() ) {
				waitingStarted = true;
			} else if ( waitingStarted && bus.getClk() ) {
				onEdge(bus);
			}
		}

		@Override
		public final void onEnterHook(IECBus bus,boolean atnLowered) {
			waitingStarted = false;
		}
	}

	private final State IDLE = new State("IDLE") {

		@Override
		public void tickHook(IECBus bus, boolean atnLowered) { /* NOP */ }

		@Override
		public void onEnterHook(IECBus bus, boolean atnLowered)
		{
			// TODO: FIXME -- setting both lines to HIGH would be the RightThingToDo(tm) but I obviously
			// TODO:          was sloppy when implementing bus states and doing so breaks things...
//			data = true;
//			clk = true;
			deviceState = DeviceState.IDLE;
		}
	};

	private final State TALK_WAIT_FRAME_ACK = new State("TALK_WAIT_FRAME_ACK")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			// wait for listener to ACK frame
			if ( ! bus.getData() )
			{
				if ( hasDataToSend() )
				{
					setState( TALK_READY_TO_SEND );
				} else {
					// TODO: Maybe need to do bus turn-around here ???
					clk = true;
					setState( IDLE );
				}
			}
		}

		@Override
		public void onEnterHook(IECBus bus, boolean atnLowered) {
			data = true;
			clk = false;
		}
	};

	private final State TALK_RAISE_CLK= new State("TALK_RAISE_CLK")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( cyclesInState >= 40 )
			{
				setState( TALK_RAISE_CLK );
				if ( currentBit == 8 )
				{
					setState( TALK_WAIT_FRAME_ACK );
				} else {
					setState( TALK_LOWER_CLK );
				}
			}
		}

		@Override
		public void onEnterHook(IECBus bus, boolean atnLowered)
		{
			clk = true;
			final int bit = currentByte & 1; // send LSB first
			if ( IECBus.DEBUG_DEVICE_LEVEL_VERBOSE) {
				System.out.println("SEND bit no. "+currentBit+": "+bit);
			}
			data = (bit != 0);
			currentByte = currentByte >> 1;
			currentBit++;
		}
	};

	private final State TALK_LOWER_CLK= new State("TALK_LOWER_CLK")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( cyclesInState >= 40 ) {
				setState( TALK_RAISE_CLK );
			}
		}

		@Override
		public void onEnterHook(IECBus bus, boolean atnLowered)
		{
			clk = false;
			data = true;
		}
	};

	private final State TALK_DELAY_FIRST_BIT = new State("TALK_DELAY_FIRST_BIT")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( cyclesInState >= 40 ) {
				setState(TALK_LOWER_CLK);
			}
		}
	};

	private final State TALK_DELAY_FIRST_BIT_WAIT_EOI2 = new State("TALK_DELAY_FIRST_BIT_WAIT_EOI2")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( bus.getData() )
			{
				setState(TALK_LOWER_CLK);
			}
		}
	};

	private final State TALK_DELAY_FIRST_BIT_WAIT_EOI1 = new State("TALK_DELAY_FIRST_BIT_WAIT_EOI1")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( ! bus.getData() )
			{
				setState(TALK_DELAY_FIRST_BIT_WAIT_EOI2);
			}
		}
	};

	private final State TALK_DELAY_FIRST_BIT_EOI = new State("TALK_DELAY_FIRST_BIT_EOI")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( cyclesInState >= EOI_DELAY_CYCLES )
			{
				setState(TALK_DELAY_FIRST_BIT_WAIT_EOI1);
			}
		}
	};

	private final State TALK_PREPARE_BYTE = new State("TALK_PREPARE_BYTE")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			final boolean isLastByte = ! getNextByte();
			if ( IECBus.DEBUG_DEVICE_LEVEL_VERBOSE ) {
				System.out.println("Sending byte, value 0x"+Integer.toHexString( currentByte & 0xff ) +" [ eoi_required: "+isLastByte+" ]");
			}
			if ( isLastByte ) { // last byte to send, EOI handshake
				setState( TALK_DELAY_FIRST_BIT_EOI );
			} else { // at least one more byte to send, no EOI required
				setState( TALK_DELAY_FIRST_BIT );
			}
		}

		@Override
		public void onEnterHook(IECBus bus, boolean atnLowered)
		{
			currentBit = 0;
		}
	};

	private final State TALK_WAIT_FOR_LISTENER = new State("TALK_WAIT_FOR_LISTENER")
	{
		private boolean waiting;

		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( ! waiting && ! bus.getData() ) { //
				waiting = true;
			} else if ( waiting && bus.getData() ) {
				setState( TALK_PREPARE_BYTE );
			}
		}

		@Override
		public void onEnterHook(IECBus bus, boolean atnLowered) {
			waiting = false;
		}
	};

	private final State TALK_READY_TO_SEND = new State("TALK_READY_TO_SEND") {

		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( cyclesInState >= 100 ) {
				clk = true;
				setState( TALK_WAIT_FOR_LISTENER );
			}
		}
	};

	private final State TURN_L2T_WAIT_CLK = new WaitForRisingEdge("TURN_L2T_WAIT_CLK") // turn-around bus LISTENING -> TALKING
	{
		@Override
		protected void onEdge(IECBus bus)
		{
			data = true;
			clk = false;
			setState( TALK_READY_TO_SEND );
		}
	};

	private final State TURN_L2T_WAIT_ATN = new State("TURN_L2T_WAIT_ATN") // turn-around bus LISTENING -> TALKING
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( bus.getATN() ) // wait for ATN release
			{
				setState( TURN_L2T_WAIT_CLK );
			}
		}
	};

	private final State TURN_T2L_WAIT_CLK_EOF = new State("TURN_T2L_WAIT_CLK_EOF") // turn-around bus LISTENING -> TALKING
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( cyclesInState > 20 ) {
				data = true;
				clk = true;
				setState( IDLE );
			}
		}
	};

	// turn-around bus LISTENING -> TALKING and immediately turn-around bus again to indicate 'File not found' error
	private final State TURN_L2T_WAIT_CLK_EOF = new WaitForRisingEdge("TURN_L2T_WAIT_CLK_EOF") // turn-around bus LISTENING -> TALKING
	{
		@Override
		protected void onEdge(IECBus bus)
		{
			data = false; // indicates 'file not found' error
			clk = false;
			setState( TURN_T2L_WAIT_CLK_EOF );
		}
	};

	// turn-around bus LISTENING -> TALKING and immediately turn-around bus again to indicate 'File not found' error
	private final State TURN_L2T_WAIT_ATN_EOF = new State("TURN_L2T_WAIT_ATN_EOF")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( bus.getATN() ) // wait for ATN release
			{
				setState( TURN_L2T_WAIT_CLK_EOF );
			}
		}
	};

	private final State LISTEN_ACK_FRAME1 = new State("RECEIVE_ACK_FRAME1")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( bus.getData() )
			{
				data = false;
				if ( bus.getATN() ) {
					onDataByteReceived(currentByte);
					setState( LISTEN_WAIT_FOR_TALKER_READY );
				} else {
					setState( commandByteReceived(LISTEN_WAIT_FOR_TALKER_READY) );
				}
			}
		}
	};

	private final State LISTEN_RECEIVE_BIT = new WaitForRisingEdge("RECEIVE_BIT")
	{
		@Override
		protected void onEdge(IECBus bus)
		{
			final int bit = bus.getData() ? 1<<7 : 0;
			currentByte = currentByte >> 1;
			currentByte |= bit;
			if ( IECBus.DEBUG_WIRE_LEVEL ) {
				System.out.println("Got bit "+currentBit+" : "+(bit != 0 ? 1 : 0));
			}
			currentBit++;
			if ( currentBit != 8 )
			{
				setState( LISTEN_WAIT_FOR_TRANSMISSION );
			} else {
				setState( LISTEN_ACK_FRAME1 );
			}
		}
	};

	private final State LISTEN_WAIT2 = new WaitForFallingEdge("RECEIVE_WAIT2")
	{
		@Override
		protected void onEdge(IECBus bus)
		{
			setState(LISTEN_RECEIVE_BIT);
		}
	};

	private final State LISTEN_ACK_EOI = new State("ACK_EOI")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( cyclesInState > 60 )
			{
				if ( IECBus.CAPTURE_BUS_SNAPSHOTS){
					bus.takeSnapshot("EOI_ACKED");
				}
				data = true;
				setState(LISTEN_WAIT2);
			}
		}

		@Override
		public void onEnterHook(IECBus bus,boolean atnLowered) {
			data = false;
		}
	};

	private final State LISTEN_WAIT_FOR_TRANSMISSION = new WaitForFallingEdge("RECEIVE_WAIT")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered) {

			if ( cyclesInState > EOI_DELAY_CYCLES )
			{
				bus.takeSnapshot("EOI_RCV");
				if ( IECBus.DEBUG_WIRE_LEVEL ) {
					System.out.println("****** EOI !!!!");
				}
				setState(LISTEN_ACK_EOI);
			} else {
				super.tickHook(bus, atnLowered);
			}
		}

		@Override
		protected void onEdge(IECBus bus)
		{
			setState(LISTEN_RECEIVE_BIT);
		}
	};

	private final State LISTEN_RDY_TO_RECEIVE = new State("RDY_TO_RECEIVE")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( cyclesInState >= 20 )
			{
				bus.takeSnapshot("READY_TO_RECV");
				data = true;
				setState( LISTEN_WAIT_FOR_TRANSMISSION );
			}
		}

		@Override
		public void onEnterHook(IECBus bus,boolean atnLowered)
		{
			data = false;
			currentBit = 0;
			currentByte = 0;
		}
	};

	private final State LISTEN_WAIT_FOR_TALKER_READY = new WaitForRisingEdge("WAIT_FOR_TALKER")
	{
		@Override
		protected void onEdge(IECBus bus) {
			bus.takeSnapshot("WAITING");
			setState(LISTEN_RDY_TO_RECEIVE);
		}
	};

	private final State ANSWER_ATN = new State("ANSWER_ATN")
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			setState( LISTEN_WAIT_FOR_TALKER_READY );
		}

		@Override
		public void onEnterHook(IECBus bus, boolean atnLowered)
		{
			if ( IECBus.DEBUG_DEVICE_LEVEL_VERBOSE ) {
				System.out.println("*** device responding to ATN (lowered: "+atnLowered+")");
			}
			data = false;
			clk = true;
		}
	};

	public AbstractSerialDevice(int primaryAddress) {
		this.primaryAddress = primaryAddress;
		reset();
	}

	private void setState(State newState) {
		this.nextState = newState;
	}

	@Override
	public final void tick(IECBus bus,boolean atnLowered)
	{
		if ( atnLowered ) {
			nextState = ANSWER_ATN;
		}
		if ( nextState != null )
		{
			if ( IECBus.DEBUG_WIRE_LEVEL ) {
				System.out.println("Switching "+state+" -> "+nextState);
			}
			state = nextState;
			state.onEnter(bus, atnLowered);
			nextState = null;
		} else {
			state.tick( bus, atnLowered );
		}
	}

	@Override
	public final boolean getClock() {
		return clk;
	}

	@Override
	public final boolean getData() {
		return data;
	}

	@Override
	public final boolean getATN() {
		return false;
	}

	@Override
	public final int getPrimaryAddress() {
		return primaryAddress;
	}

	@Override
	public final void reset()
	{
		channels.clear();
		activeChannel = null;
		clk = false;
		data = false;
		this.state = IDLE;
		this.nextState = null;
		deviceSelected = false;
		resetHook();
	}
	
	protected void resetHook() {
		
	}

	protected final State commandByteReceived(State nextState)
	{
		final int payload = currentByte & 0b1111;
		final BusCommand cmdType = parseCommand( currentByte );

		if ( IECBus.DEBUG_DEVICE_LEVEL )
		{
			if ( cmdType.supportsPayload )
			{
				System.out.println("########## COMMAND: 0x"+Integer.toHexString( currentByte )+" => "+cmdType+" #"+payload );
			} else {
				System.out.println("########## COMMAND: 0x"+Integer.toHexString( currentByte )+" => "+cmdType);
			}
		}

		switch( cmdType )
		{
			case LISTEN:
				if ( payload != getPrimaryAddress() )
				{
					deviceSelected = false;
					return IDLE;
				}
				deviceState = DeviceState.LISTENING;
				deviceSelected = true;
				onListen();
				break;
			case TALK:
				if ( payload != getPrimaryAddress() )
				{
					deviceSelected = false;
					return IDLE;
				}
				deviceState = DeviceState.TALK_REQUESTED;
				deviceSelected = true;
				onTalk();
				break;
			case OPEN:
			case OPEN_CHANNEL:
				if ( deviceSelected )
				{
					final boolean bytesAvailable;
					switch( cmdType )
					{
						case OPEN:         bytesAvailable = onOpenData(payload); break;
						case OPEN_CHANNEL: bytesAvailable = onOpenChannel(payload); break;
						default:
							throw new RuntimeException("Unhandled switch/case: "+cmdType);
					}
					if ( this.deviceState == DeviceState.TALK_REQUESTED ) { // turn IEC bus around so that we can talk
						if ( bytesAvailable )
						{
							return TURN_L2T_WAIT_ATN;
						}
						// initiate special turn-around sequence : we're asked to TALK but we've got no bytes to send
						return TURN_L2T_WAIT_ATN_EOF;
					}
				}
				break;
			case CLOSE:
				if ( deviceSelected ) {
					onClose(payload);
				}
				break;
			case UNLISTEN:
				if ( deviceSelected ) {
					onUnlisten();
				}
				deviceSelected = false;
				return IDLE;
			case UNTALK:
				if ( deviceSelected ) {
					onUntalk();
				}
				deviceSelected = false;
				return IDLE;
			default:
				throw new RuntimeException("Unhandled command: "+cmdType);
		}
		return nextState;
	}

	protected void onDataByteReceived(int data)
	{
		activeChannel.receiveBuffer.write( (byte) data );
		if ( IECBus.DEBUG_DEVICE_LEVEL ) {
			System.out.println("########## DATA: 0x"+Integer.toHexString( data )+" written to "+activeChannel);
		}
	}

	protected void onListen() {
	}

	protected void onUnlisten() {
	}

	protected void onTalk() {
	}

	protected void onUntalk() {
	}

	/**
	 *
	 * @param channelNo
	 * @return <code>true</code> if there's at least one byte of data available for transmission (this return value is only ever checked when we're expected to TALK)
	 */
	protected final boolean onOpenChannel(int channelNo)
	{
		activeChannel = getChannel( channelNo );
		activeChannel.isOpen = true;
		if ( deviceState == DeviceState.LISTENING )
		{
			activeChannel.clear();
		}
		return onOpenChannelHook( channelNo );
	}

	/**

	 * @param channelNo
	 * @return <code>true</code> if there's at least one byte of data available for transmission (this return value is only ever checked when we're expected to TALK)
	 */
	protected boolean onOpenChannelHook(int channelNo) {
		return false;
	}

	/**
	 *
	 * @param channelNo
	 * @return <code>true</code> if there's at least one byte of data available for transmission (this return value is only ever checked when we're expected to TALK)
	 */
	protected final boolean onOpenData(int channelNo)
	{
		activeChannel = getChannel( channelNo );
		activeChannel.isOpen = true;
		if ( deviceState == DeviceState.LISTENING )
		{
			activeChannel.clear();
		}
		return onOpenDataHook(channelNo);
	}

	/**
	 *
	 * @param channel
	 * @return <code>true</code> if there's at least one byte of data available for transmission (this return value is only ever checked when we're expected to TALK)
	 */
	protected boolean onOpenDataHook(int channel) {
		return false;
	}

	protected final void onClose(int channelNo)
	{
		Channel channel = getChannel( channelNo );
		channel.clear();
		channel.isOpen = false;
		if ( channel== activeChannel )
		{
			if ( IECBus.DEBUG_DEVICE_LEVEL ) {
				System.out.println("Closed ACTIVE channel #"+channelNo+" on device #"+getPrimaryAddress());
			}
			activeChannel = null;
		} else {
			if ( IECBus.DEBUG_DEVICE_LEVEL ) {
				System.out.println("Closed channel #"+channelNo+" on device #"+getPrimaryAddress());
			}
		}
		onCloseHook( channelNo );
	}

	protected void onCloseHook(int channelNo) {
	}

	private BusCommand parseCommand(int data)
	{
		if ( (data & 0b1111_0000) == 0x60 )
		{
			return BusCommand.OPEN_CHANNEL;
		}
		if (  (data & 0b1111_0000) == 0xf0 )
		{
			return BusCommand.OPEN;
		}
		if ( ( data    & 0b1111_0000) == 0xe0 )
		{
			return BusCommand.CLOSE;
		}
		if ( ( data   & 0xff) == 0x5f )
		{
			return BusCommand.UNTALK;
		}
		if ( ( data   & 0b1110_0000) == 0x40 )
		{
			return BusCommand.TALK;
		}
		if ( ( data & 0b1111_0000) == 0x20 )
		{
			return BusCommand.LISTEN;
		}
		if ( ( data & 0xff) == 0x3f )
		{
			return BusCommand.UNLISTEN;
		}
		return BusCommand.UNKNOWN;
	}

	protected final Channel getChannel(int no)
	{
		Channel result = channels.get( no );
		if ( result == null ) {
			result = new Channel(no);
			channels.put( no , result );
		}
		return result;
	}

	protected boolean hasDataToSend() {
		return false;
	}

	/**
	 * Write next byte to send into <code>currentByte</code>.
	 *
	 * @return true if there are more bytes to send, false if this is the last byte and EOI should be signaled
	 */
	protected boolean getNextByte() {
		return false;
	}

	protected final Channel getActiveChannel() {
		return activeChannel;
	}
	
	public final DeviceState getDeviceState() {
		return deviceState;
	}
	
	public boolean isBusy() 
	{
		return ! deviceState.equals( DeviceState.IDLE );
	}

	/*
	 * ########## COMMAND: 0x28 => LISTEN #8
	 * ########## COMMAND: 0xf0 => OPEN #0
	 * ########## DATA: 0x24
	 * ########## COMMAND: 0x3f => UNLISTEN
	 * ########## COMMAND: 0x48 => TALK #8
	 * ########## COMMAND: 0x60 => OPEN_CHANNEL #0
	 */
}