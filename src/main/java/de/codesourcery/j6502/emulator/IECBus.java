package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class IECBus
{
	public static final boolean CAPTURE_BUS_SNAPSHOTS = true;

	private final int MAX_CYCLES_TO_KEEP = 200;

	public boolean DEBUG_VERBOSE = true;

	// current cycle count
	protected long cycle;

	protected final String identifier;

	private final SerialDevice cpu;
	private final List<SerialDevice> devices = new ArrayList<>();
	private final List<StateSnapshot> states = new ArrayList<>();
	
	private boolean atn;
	private boolean clkSum;
	private boolean dataSum;

	public static final class StateSnapshot
	{
		public final boolean atn;
		public final boolean clk;
		public final boolean data;

		public final long cycle;

		public final String busState;

		public StateSnapshot(boolean atn,boolean clk,boolean data,long cycle)
		{
			this.cycle = cycle;
			this.atn = atn;
			this.clk = clk;
			this.data = data;
			this.busState = "unknown";
		}
		
		@Override
		public String toString() {
			return cycle+" - ATN: "+toLevel(atn)+","+"CLK: "+toLevel(clk)+","+"DATA: "+toLevel(data);
		}
		
		private static String toLevel(boolean value) {
			return value ? "HIGH" : "LOW";
		}
	}
	
	public boolean getATN() {
		return atn;
	}
	
	public boolean getData() {
		return dataSum;
	}
	
	public boolean getClk() {
		return clkSum;
	}

	public void addDevice(SerialDevice device) 
	{
		devices.add( device );
	}

	public Optional<SerialDevice> getDevice(int primaryAddress)
	{
		return devices.stream().filter( d -> d.getPrimaryAddress() == primaryAddress ).findFirst();
	}

	public void reset()
	{
		this.clkSum = false;
		this.dataSum = false;

		devices.forEach( device -> device.reset() );
		states.clear();
		cycle = 0;
	}

	private void takeSnapshot()
	{
		synchronized(states) 
		{
			final StateSnapshot snapshot = new StateSnapshot( atn , clkSum , dataSum , cycle );
			System.out.println("SNAPSHOT: "+snapshot);
			states.add( snapshot );
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

	public IECBus(String identifier,SerialDevice cpu)
	{
		this.identifier = identifier;
		this.cpu = cpu;
		addDevice(cpu);
		addDevice( new AbstractSerialDevice( 8 ) );
	}

	@Override
	public String toString() {
		return "[BUS: "+identifier+"] ATN: "+getATN()+" | CLK: "+clkSum+" | DATA: "+dataSum;
	}

	public void tick()
	{
		/*
		 * High = Logical false
		 * Low  = Logical true
		 * 
		 * - A line will become LOW ("true") (LOW / PULLED DOWN, or 0V) if one or more devices signal true (LOW);
         * - A line will become HIGH ("false") (HIGH / RELEASED, or 5V) only if all devices signal false (HIGH).
		 */
		boolean sumClk = false;
		boolean sumData = false;
		
		for (int i = 0; i < devices.size(); i++) 
		{
			final SerialDevice dev = devices.get(i);
			dev.tick(this);
			sumClk |= dev.getClock();
			sumData |= dev.getData();
		}
		if ( CAPTURE_BUS_SNAPSHOTS ) 
		{ 
			if ( sumClk != clkSum || sumData != dataSum || this.atn != cpu.getATN() ) 
			{
				this.clkSum = sumClk;
				this.dataSum = sumData;			
				this.atn = cpu.getATN();
				takeSnapshot();
			}
		} else {
			this.atn = cpu.getATN();
			this.clkSum = sumClk;
			this.dataSum = sumData;
		}
		cycle++;
	}
}