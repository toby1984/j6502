package de.codesourcery.j6502.emulator;


public class Emulator implements IEmulator {

	private final MemorySubsystem memory = new MemorySubsystem();
	private final CPU cpu = new CPU( this.memory );

	private IMemoryProvider memoryProvider;

	@Override
	public void setMemoryProvider(IMemoryProvider provider)
	{
		if (provider==null ) {
			throw new IllegalArgumentException("provider must not be NULL");
		}
		this.memoryProvider = provider;
		reset();
	}

	public CPU getCPU() {
		return cpu;
	}

	public IMemoryRegion getMemory() {
		return memory;
	}

	@Override
	public void reset()
	{
		memory.reset();

		// all 6502 CPUs read their initial PC value from $FFFC
		memory.writeWord( CPU.RESET_VECTOR_LOCATION , (short) 0xfce2 );

		if ( this.memoryProvider != null )
		{
			this.memoryProvider.loadInto( memory );
		}

		// reset CPU, will initialize PC from RESET_VECTOR_LOCATION
		cpu.reset();
	}

	@Override
	public void singleStep() {
		cpu.singleStep();
	}

	@Override
	public void start() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("start not implemented yet");
	}

	@Override
	public void stop() {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("stop not implemented yet");
	}
}