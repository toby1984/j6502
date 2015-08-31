package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.j6502.emulator.diskdrive.DiskDrive;
import de.codesourcery.j6502.ui.BusStateContainer;

public class IECBus implements Bus
{
	public static final boolean CAPTURE_BUS_SNAPSHOTS = true;

	private final BusStateContainer busStateContainer = new BusStateContainer(this);

	public static final boolean DEBUG_WIRE_LEVEL = false;
	public static final boolean DEBUG_DEVICE_LEVEL_VERBOSE = false;
	public static final boolean DEBUG_DEVICE_LEVEL = true;

	// current cycle count
	protected long cycle;

	protected final String identifier;

	private final SerialDevice cpu;
	private final List<SerialDevice> devices = new ArrayList<>();
	private final SerialDevice[] deviceArray = new SerialDevice[32];

	private boolean atn=true;
	private boolean clkSum=true;
	private boolean dataSum=true;

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
		if ( deviceArray[ device.getPrimaryAddress() ] != null ) {
			throw new IllegalArgumentException("There already is a device registered with primary address #"+device.getPrimaryAddress());
		}
		devices.add( device );
		deviceArray[ device.getPrimaryAddress() ] = device;
	}

	/**
	 *
	 * @param primaryAddress
	 * @return device or <code>NULL</code> if there is no such device
	 */
	public SerialDevice getDevice(int primaryAddress)
	{
		return deviceArray[ primaryAddress ];
	}

	public List<SerialDevice> getDevices() {
		return devices;
	}

	public IECBus(String identifier,SerialDevice cpu)
	{
		this.identifier = identifier;
		this.cpu = cpu;
		addDevice(cpu);

		final DiskDrive diskDrive = new DiskDrive(8);
		addDevice( diskDrive.getHardware() );
	}

	@Override
	public String toString() {
		return "[BUS: "+identifier+"] ATN: "+getATN()+" | CLK: "+clkSum+" | DATA: "+dataSum;
	}

	public void tick()
	{
        /*
         * Method must ONLY be called when ph2 == HIGH
         */

		/*
		 * High = Logical false
		 * Low  = Logical true
		 *
		 * - A line will become LOW ("true") (LOW / PULLED DOWN, or 0V) if one or more devices signal true (LOW);
         * - A line will become HIGH ("false") (HIGH / RELEASED, or 5V) only if all devices signal false (HIGH).
		 */

		boolean sumClk = true;
		boolean sumData = true;

		for (int i = 0, len = devices.size() ; i < len ; i++)
		{
			final SerialDevice dev = devices.get(i);
			dev.tick(this );
			sumData &= dev.getData();
			sumClk &= dev.getClock();
		}

		if ( CAPTURE_BUS_SNAPSHOTS )
		{
			if ( sumClk != clkSum || sumData != dataSum || this.atn != cpu.getATN() )
			{
				this.atn = cpu.getATN();
				this.clkSum = sumClk;
				this.dataSum = sumData;
				busStateContainer.sampleBus();
			}
		} else {
			this.atn = cpu.getATN();
			this.clkSum = sumClk;
			this.dataSum = sumData;
		}
		cycle++;
	}

	public void reset()
	{
		this.clkSum = true;
		this.dataSum = true;
		this.atn = true;

		devices.forEach( device -> device.reset() );
		busStateContainer.reset();
		cycle = 0;
	}

	@Override
	public int getWidth() {
		return 3;
	}

	private static final String[] WIRE_NAMES = { "ATN" , "CLOCK" , "DATA" };

	@Override
	public String[] getWireNames() {
		return WIRE_NAMES;
	}

	@Override
	public int read() {
		return (getData() ? 4 : 0) | (getClk() ? 2 : 0) | (getATN() ? 1 : 0);
	}

	public BusStateContainer getBusStateContainer() {
		return busStateContainer;
	}
}