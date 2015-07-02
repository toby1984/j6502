package de.codesourcery.j6502.emulator;

public class AbstractSerialDevice implements SerialDevice {

	private final int primaryAddress;

	private boolean clk=false; // bus == high
	private boolean data=false; // bus == high

	private State state;
	private State nextState;	
	
	private int bitsReceived = 0;
	private int currentByte = 0;
	
	protected abstract class State 
	{
		public final String name;
		protected long cyclesWaited = 0;

		public State(String name) { this.name = name; }

		public final void tick(IECBus bus, boolean atnLowered) {
			cyclesWaited++;
			tickHook(bus, atnLowered);
		}

		public abstract void tickHook(IECBus bus, boolean atnLowered);

		public final void onEnter(IECBus bus, boolean atnLowered) { 
			cyclesWaited = 0;
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
	
	private final State NOP = new State("NOP") {

		@Override
		public void tickHook(IECBus bus, boolean atnLowered) 
		{
		}
	};
	
	private final State RECEIVE_ACK_FRAME2 = new State("RECEIVE_ACK_FRAME2") 
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered) 
		{
		    bus.takeSnapshot( "FRAME_ACK_END" );			
			setState( WAIT_FOR_TALKER_READY );
		}
		
		@Override
		public void onEnterHook(IECBus bus,boolean atnLowered) 
		{
			bus.takeSnapshot( "FRAME_ACK_START" );			
		    data = false;	
		}
	};	
	
	private final State RECEIVE_ACK_FRAME1 = new State("RECEIVE_ACK_FRAME1") 
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered) 
		{
			if ( bus.getData() ) {
				setState( RECEIVE_ACK_FRAME2 );
			}
		}
	};
	
	private final State RECEIVE_BIT = new WaitForRisingEdge("RECEIVE_BIT") 
	{
		@Override
		protected void onEdge(IECBus bus) 
		{
			final int bit = bus.getData() ? 1<<7 : 0;
			currentByte = currentByte >> 1;
			currentByte |= bit;
			System.out.println("Got bit "+bitsReceived+" : "+(bit != 0 ? 1 : 0));
			bitsReceived++;
			if ( bitsReceived != 8 ) {
				setState( RECEIVE_WAIT );
			} else {
				System.out.println("\n=============");
				System.out.println("Got byte: "+Integer.toHexString( currentByte ) );
				System.out.println("\n=============");
				setState( RECEIVE_ACK_FRAME1 );
			}
		}
	};	
	
	private final State RECEIVE_WAIT2 = new WaitForFallingEdge("RECEIVE_WAIT2") 
	{
		@Override
		protected void onEdge(IECBus bus) 
		{
			setState(RECEIVE_BIT);
		}
	};
	
	private final State RECEIVE_ACK_EOI = new State("ACK_EOI") 
	{
		public void tickHook(IECBus bus, boolean atnLowered)
		{
			if ( cyclesWaited > 60 ) 
			{
				if ( IECBus.CAPTURE_BUS_SNAPSHOTS){
					bus.takeSnapshot("EOI_ACKED");
				}
				data = true;
				setState(RECEIVE_WAIT2);
			}
		}
		
		@Override
		public void onEnterHook(IECBus bus,boolean atnLowered) {
			data = false;
		}
	};
	
	private final State RECEIVE_WAIT = new WaitForFallingEdge("RECEIVE_WAIT") 
	{
		public void tickHook(IECBus bus, boolean atnLowered) {
		
			if ( cyclesWaited > 100 ) 
			{
				if ( IECBus.CAPTURE_BUS_SNAPSHOTS){
					bus.takeSnapshot("EOI_RCV");
				}
				System.out.println("****** EOI !!!!");
				setState(RECEIVE_ACK_EOI);
			} else {
				super.tickHook(bus, atnLowered);
			}
		}
		
		@Override
		protected void onEdge(IECBus bus) 
		{
			setState(RECEIVE_BIT);
		}
	};

	private final State READY_TO_RECEIVE = new State("READY_TO_RECEIVE") 
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered) 
		{
			if ( cyclesWaited >= 20 ) 
			{
				bus.takeSnapshot("READY_TO_RECV");
				data = true;
				setState( RECEIVE_WAIT );
			}
		}
		
		@Override
		public void onEnterHook(IECBus bus,boolean atnLowered) 
		{
			data = false;			
			bitsReceived = 0;
			currentByte = 0;
		}
	};	
	
	private final State WAIT_FOR_TALKER_READY = new WaitForRisingEdge("WAIT_FOR_TALKER_READY") 
	{
		@Override
		protected void onEdge(IECBus bus) {
			bus.takeSnapshot("WAITING");
			setState(READY_TO_RECEIVE);			
		}
	};
	
	private final State ANSWER_ATN = new State("ANSWER_ATN") 
	{
		@Override
		public void tickHook(IECBus bus, boolean atnLowered) 
		{
			setState( WAIT_FOR_TALKER_READY );
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
	public void reset() {
		clk = false;
		data = false;
		this.state = NOP;
		this.nextState = null;
	}
}