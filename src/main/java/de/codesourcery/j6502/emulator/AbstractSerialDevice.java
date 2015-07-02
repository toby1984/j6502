package de.codesourcery.j6502.emulator;

public class AbstractSerialDevice implements SerialDevice {

	protected  final int primaryAddress;

	protected  boolean clk=false; // bus == high
	protected  boolean data=false; // bus == high

	protected  State state;
	protected  State nextState;	
	
	protected int bitsProcessed = 0;
	protected  int currentByte = 0;
	
	protected boolean deviceAddressed = false;
	
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
		protected long cyclesInState = 0;

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

		boolean waitingStarted;

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

		boolean waitingStarted;

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
		public void tickHook(IECBus bus, boolean atnLowered) 
		{
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
					dataByteReceived();
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
			System.out.println("Got bit "+bitsProcessed+" : "+(bit != 0 ? 1 : 0));
			bitsProcessed++;
			if ( bitsProcessed != 8 ) 
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
		public void tickHook(IECBus bus, boolean atnLowered) {
		
			if ( cyclesInState > 100 ) 
			{
				bus.takeSnapshot("EOI_RCV");
				System.out.println("****** EOI !!!!");
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
			bitsProcessed = 0;
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
			System.out.println("*** device received ATN *** lowered: "+atnLowered);
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
			System.out.println("Switching "+state+" -> "+nextState);
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
	public void reset() 
	{
		clk = false;
		data = false;
		this.state = IDLE;
		this.nextState = null;
		deviceAddressed = false;
	}
	
	protected void dataByteReceived() {
		System.out.println("########## DATA: 0x"+Integer.toHexString( currentByte) );
	}
	
	protected final State commandByteReceived(State nextState) 
	{
		final int payload = currentByte & 0b1111;
		final BusCommand cmdType = parseCommand( currentByte );
		if ( cmdType.supportsPayload ) {
			System.out.println("########## COMMAND: 0x"+Integer.toHexString( currentByte )+" => "+cmdType+" #"+payload );
		} else {
			System.out.println("########## COMMAND: 0x"+Integer.toHexString( currentByte )+" => "+cmdType);
		}
		switch( cmdType ) 
		{
			case LISTEN:
				if ( payload != getPrimaryAddress() ) 
				{
					deviceAddressed = false;
					return IDLE;
				}
				deviceAddressed = true;
				onListen();
				break;
			case TALK:
				if ( payload != getPrimaryAddress() ) {
					deviceAddressed = false;
					return IDLE;
				} 
				deviceAddressed = true;
				onTalk();
				break;				
			case OPEN:
				if ( deviceAddressed ) {
					onOpenData(payload);
				}
				break;
			case OPEN_CHANNEL:
				if ( deviceAddressed ) {
					onOpenChannel(payload);
				}
				break;
			case CLOSE:
				if ( deviceAddressed ) {
					onClose(payload);
				}
				break;				
			case UNLISTEN:
				if ( deviceAddressed ) {
					onUnlisten();
				}
				deviceAddressed = false;
				return IDLE;
			case UNTALK:
				if ( deviceAddressed ) {
					onUntalk();
				}
				deviceAddressed = false;
				return IDLE;
			default:
				throw new RuntimeException("Unhandled command: "+cmdType);
		}
		return nextState;
	}	
	
	protected void onListen() {
	}
	
	protected void onUnlisten() {
	}
	
	protected void onTalk() {
	}
	
	protected void onUntalk() {
	}
	
	protected void onOpenChannel(int payload) {
		
	}
	
	protected void onOpenData(int payload) {
		
	}
	
	protected void onClose(int payload) {
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
		if ( ( data   & 0b1110_0000) == 0x40 ) 
		{
			return BusCommand.TALK;
		}
		if ( ( data   & 0xff) == 0x5f ) 
		{
			return BusCommand.UNTALK;
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
}