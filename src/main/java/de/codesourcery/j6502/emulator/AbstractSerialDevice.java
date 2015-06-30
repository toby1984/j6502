package de.codesourcery.j6502.emulator;

public class AbstractSerialDevice implements SerialDevice {

	private final int primaryAddress;
	
	private boolean clk;
	private boolean data;
	
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
		
		public void onEnter(IECBus bus) { 
			cyclesWaited = 0;
		} 
		
		@Override
		public String toString() { return name; }
	}
	
	private State state;
	private State nextState;	
	
	private final State RECEIVE = new State("RECEIVE") 
	{

		@Override
		public void tickHook(IECBus bus) 
		{
		}
		
	};		
	
	private final State READY_FOR_DATA = new State("READY_FOR_DATA") 
	{

		@Override
		public void tickHook(IECBus bus) 
		{
			if ( cyclesWaited >= 20 ) 
			{
				data = true;
				setState( RECEIVE );
			}
		}
	};	
	
	private final State WAIT_FOR_CLOCK = new State("WAIT_FOR_CLOCK") 
	{

		@Override
		public void tickHook(IECBus bus) 
		{
			if ( ! bus.getClk() ) 
			{
				data = false;
				setState( READY_FOR_DATA );
			}
		}
	};	
	
	private final State WAIT_FOR_ATN = new State("WAIT_FOR_ATN") 
	{

		@Override
		public void tickHook(IECBus bus) 
		{
			if ( bus.getATN() ) 
			{
				System.out.println("*** device received ATN ***");
				data = true;
				setState( WAIT_FOR_CLOCK );
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