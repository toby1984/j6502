package de.codesourcery.j6502.emulator;


public abstract class SerialDevice
{
	private final int deviceAddress;

	private IECBus bus;

	public SerialDevice(int deviceAddress)
	{
		this.deviceAddress = deviceAddress;
	}

	protected final IECBus getBus() {
		return bus;
	}

	public final void onAttach(IECBus bus)
	{
		if (bus == null) {
			throw new IllegalArgumentException("bus must not be NULL");
		}
		this.bus = bus;
	}

	public int getPrimaryAddress() {
		return deviceAddress;
	}

	public final void tick() {
		tickHook( this.bus );
	}

	protected abstract void tickHook(IECBus bus);

	public abstract void receive(byte data,boolean eoi);

	public abstract void onATN();

	public abstract void reset();
}