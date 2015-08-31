package de.codesourcery.j6502.ui;

import java.util.Arrays;

import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.emulator.Bus;

public class BusStateContainer
{
	public static final int MAX_STATES = 256;

	private int oldestStatePtr;
	private int latestStatePtr;

	private long cycles;

	private int stateCount;

	private final long[] timestamps;
	private final int[] wireStates;

	private final Bus bus;

	public BusStateContainer(Bus bus)
	{
		Validate.notNull(bus, "bus must not be NULL");
		this.bus = bus;
		timestamps = new long[ MAX_STATES ];
		wireStates = new int[ MAX_STATES ];
	}

	public synchronized void reset()
	{
		cycles = 0;
		stateCount = 0;
		oldestStatePtr = latestStatePtr = 0;
		Arrays.fill( timestamps,0);
		Arrays.fill( wireStates,0);
	}

	public synchronized int firstPtr() {
		return oldestStatePtr;
	}

	public synchronized int lastPtr()
	{
		return latestStatePtr;
	}

	public synchronized int sampleCount()
	{
		return stateCount;
	}

	public Bus getBus() {
		return bus;
	}

	public synchronized int state(int index)
	{
		return wireStates[ index % MAX_STATES ];
	}

	public synchronized long timestamp(int index)
	{
		return timestamps[ index % MAX_STATES ];
	}

	public synchronized void sampleBus()
	{
		final int newValue = bus.read();

		if ( stateCount != 0 && wireStates[ (latestStatePtr-1) % MAX_STATES ] == newValue )
		{
			return; // wire state did not change
		}

		timestamps[latestStatePtr] = cycles;
		wireStates[latestStatePtr] = newValue;

		latestStatePtr = (latestStatePtr+1) % MAX_STATES;
		if ( stateCount < MAX_STATES )
		{
			stateCount++;
		} else {
			oldestStatePtr = (oldestStatePtr+1) % MAX_STATES;
		}
		cycles++;
	}

}
