package de.codesourcery.j6502.emulator;

import org.apache.commons.lang.StringUtils;

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

		public final void tick(IECBus bus) {
			cyclesWaited++;
			tickHook(bus);
		}

		public abstract void tickHook(IECBus bus);

		public final void onEnter(IECBus bus) { 
			cyclesWaited = 0;
			onEnterHook(bus);
		} 

		public void onEnterHook(IECBus bus) {
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
		public void tickHook(IECBus bus) 
		{
			if ( bus.getClk() ) {
				waitingStarted = true;
			} else if ( waitingStarted && ! bus.getClk() ) {
				onEdge(bus);
			}
		}

		@Override
		public final void onEnterHook(IECBus bus) {
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
		public void tickHook(IECBus bus) 
		{
			if ( ! bus.getClk() ) {
				waitingStarted = true;
			} else if ( waitingStarted && bus.getClk() ) {
				onEdge(bus);
			}
		}

		@Override
		public final void onEnterHook(IECBus bus) {
			waitingStarted = false;
		}
	}
	
	private final State RECEIVE_ACK_FRAME = new State("RECEIVE_ACK_FRAME") 
	{
		@Override
		public void tickHook(IECBus bus) 
		{
				data = true;
				setState( WAIT_FOR_TALKER_READY );
		}
		
		public void onEnterHook(IECBus bus) 
		{
			bus.takeSnapshot( "FRAME_ACK" );
			data = false;
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
			bitsReceived++;
			if ( bitsReceived != 8 ) {
				setState( RECEIVE_WAIT );
			} else {
				System.out.println("Got byte: "+Integer.toHexString( currentByte ) );
				setState( RECEIVE_ACK_FRAME );
			}
		}
	};	
	
	private final State RECEIVE_WAIT = new WaitForFallingEdge("RECEIVE_WAIT") 
	{
		@Override
		protected void onEdge(IECBus bus) {
			setState(RECEIVE_BIT);
		}
	};

	private final State READY_TO_RECEIVE = new State("READY_TO_RECEIVE") 
	{
		@Override
		public void tickHook(IECBus bus) 
		{
			if ( cyclesWaited >= 50 ) {
				data = true;
				setState( RECEIVE_WAIT );
			}
		}
		
		public void onEnterHook(IECBus bus) 
		{
			data = false;			
			bitsReceived = 0;
			currentByte = 0;
		}
	};	
	
	private final State WAIT_FOR_TALKER_READY = new WaitForRisingEdge("WAIT_FOR_TALKER_READY") 
	{
		public void tickHook(IECBus bus) 
		{
			if ( bus.getATN() ) 
			{
				System.out.println("*** ATN low again, giving up");
				setState( WAIT_FOR_ATN );
				return;
			}
			super.tickHook( bus );
		}
		
		@Override
		protected void onEdge(IECBus bus) {
			setState(READY_TO_RECEIVE);			
		}
	};
	
	private final State WAIT_FOR_ATN = new State("WAIT_FOR_ATN") 
	{
		@Override
		public void tickHook(IECBus bus) 
		{
			if ( ! bus.getATN() ) 
			{
				System.out.println("*** device received ATN ***");
				data = false;
				clk = true;
				setState( WAIT_FOR_TALKER_READY );
			}
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
	public void tick(IECBus bus) 
	{
		if ( nextState != null ) 
		{
			System.out.println("Switching "+state+" -> "+nextState);
			state = nextState;
			state.onEnter(bus);
			nextState = null;
		} else {
			state.tick( bus );
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
		this.state = WAIT_FOR_ATN;
		this.nextState = null;
	}
}