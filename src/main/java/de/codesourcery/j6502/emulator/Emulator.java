package de.codesourcery.j6502.emulator;

import java.util.function.Consumer;

import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.disassembler.Disassembler.Line;

public class Emulator 
{
	protected static final boolean PRINT_CURRENT_INS = false;

	protected static final boolean PRINT_DISASSEMBLY = false;

	protected static final String EMPTY_STRING = "";

	private final MemorySubsystem memory = new MemorySubsystem();

	private final CPU cpu = new CPU( this.memory );

	private IMemoryProvider memoryProvider;

	private final CPUImpl cpuImpl;

	public Emulator() {
		cpuImpl = new CPUImpl( cpu , memory );
	}

	public void setMemoryProvider(IMemoryProvider provider)
	{
		if (provider==null ) {
			throw new IllegalArgumentException("provider must not be NULL");
		}
		this.memoryProvider = provider;
		if ( this.memoryProvider != null ) {
			this.memoryProvider.loadInto( memory );
		}
	}

	public VIC getVIC() {
		return memory.ioArea.vic;
	}

	public CPU getCPU() {
		return cpu;
	}

	public MemorySubsystem getMemory() {
		return memory;
	}

	public void reset()
	{
		memory.reset();

		// all 6502 CPUs read their initial PC value from $FFFC
		memory.writeWord( (short) CPU.RESET_VECTOR_LOCATION , (short) 0xfce2 );

		if ( this.memoryProvider != null )
		{
			this.memoryProvider.loadInto( memory );
		}

		// reset CPU, will initialize PC from RESET_VECTOR_LOCATION
		cpu.reset();
	}

	public void doOneCycle()
	{
		/* First (low) half of clock cycle.
		 *
		 * One period of this signal corresponds to one clock cycle consisting
         * of two phases: ph2 is low in the first phase and high in the second
         * phase (hence the name 'ph2' for "phase 2"). The 6510 only accesses
         * the bus in the second (HIGH) clock phase, the VIC normally only in the
         * first (LOW) phase.
		 */

		memory.tick( cpu , false ); // clock == LOW

		/*
		 * Second (high) half of clock cycle.
		 */
		if ( cpu.cycles > 0 ) // wait until current command has 'finished' executing
		{
			cpu.cycles--;
		}
		else
		{

			cpu.handleInterrupt();

			if ( PRINT_DISASSEMBLY )
			{
				System.out.println("=====================");
				final Disassembler dis = new Disassembler();
				dis.setAnnotate( true );
				dis.setWriteAddresses( true );
				dis.disassemble( memory , cpu.pc() , 3 , new Consumer<Line>()
				{
					private boolean linePrinted = false;

					@Override
					public void accept(Line line)
					{
						if ( ! linePrinted ) {
							System.out.println( line );
							linePrinted = true;
						}
					}
				});
			}

		    cpuImpl.executeInstruction();
			
			if ( PRINT_DISASSEMBLY ) {
				System.out.println( cpu );
			}
		}

		memory.tick( cpu , true ); // clock == HIGH
	}

	public KeyboardBuffer getKeyboardBuffer() {
		return memory.ioArea.keyboardBuffer;
	}

	public IECBus getBus() {
		return memory.ioArea.iecBus;
	}
}