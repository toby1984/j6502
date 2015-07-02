package de.codesourcery.j6502.emulator;

public class AbstractSerialDevice implements SerialDevice {

	protected  final int primaryAddress;

	protected  boolean clk=false; // bus == high
	protected  boolean data=false; // bus == high

	protected  State state;
	protected  State nextState;	
	
	protected int bitsProcessed = 0;
	protected  int currentByte = 0;
	
	protected static enum CommandType 
	{
		LISTEN(true),OPEN(true),OPEN_CHANNEL(true),TALK(true),UNTALK(false),UNLISTEN(false),CLOSE(true);
		
		public final boolean supportsPayload;
		
		private CommandType(boolean supportsPayload) {
			this.supportsPayload = supportsPayload;
		}
	}
	
	protected static final class Command {
		
		public final CommandType type;
		public final int payload;
		
		public Command(CommandType type,int payload) {
			this.type = type;
			this.payload = payload;
		}
		
		public boolean hasType(CommandType t) {
			return t.equals( this.type );
		}
		
		@Override
		public String toString() 
		{
			if ( type.supportsPayload ) 
			{
				return type+" #"+payload;
			}
			return type.toString();
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
				setState( LISTEN_WAIT_FOR_TALKER_READY );
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
				byteReceived( bus);
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
	public void tick(IECBus bus,boolean atnLowered) 
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
	public boolean getClock() {
		return clk;
	}

	@Override
	public boolean getData() {
		return data;
	}

	@Override
	public boolean getATN() {
		return false;
	}

	@Override
	public int getPrimaryAddress() {
		return primaryAddress;
	}

	@Override
	public void reset() 
	{
		clk = false;
		data = false;
		this.state = IDLE;
		this.nextState = null;
	}
	
	protected final void byteReceived(IECBus bus) 
	{
		if ( bus.getATN() ) {
			processDataByte( currentByte );
		} else {
			processCommandByte( currentByte );
		}
	}
	protected void processDataByte(int payload) {
		System.out.println("########## DATA: 0x"+Integer.toHexString( payload ) );
	}
	
	protected void processCommandByte(int payload) {
		System.out.println("########## COMMAND: 0x"+Integer.toHexString( payload )+" => "+toCommand( payload ) );
	}	
	
	public Command toCommand(int data) 
	{
		final int payload = data & 0b1111;
		if ( (data & 0b1111_0000) == 0x60 ) 
		{
			return new Command(CommandType.OPEN_CHANNEL , payload );
		}
		if (  (data & 0b1111_0000) == 0xf0 ) 
		{
			return new Command(CommandType.OPEN , payload );
		}
		if ( ( data    & 0b1111_0000) == 0xe0 ) 
		{
			return new Command(CommandType.CLOSE , payload );
		}
		if ( ( data   & 0b1110_0000) == 0x40 ) 
		{
			return new Command(CommandType.TALK , payload );
		}
		if ( ( data   & 0xff) == 0x5f ) 
		{
			return new Command(CommandType.UNTALK , 0 );
		}	
		if ( ( data & 0b1111_0000) == 0x20 ) 
		{
			return new Command(CommandType.LISTEN , payload );
		}
		if ( ( data & 0xff) == 0x3f ) 
		{
			return new Command(CommandType.UNLISTEN , payload );
		}		
		return null;
	}
}