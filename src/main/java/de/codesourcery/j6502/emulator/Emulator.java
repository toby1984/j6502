package de.codesourcery.j6502.emulator;

import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.disassembler.Disassembler.Line;

public class Emulator
{
    public static final boolean TRACK_TOTAL_CYCLES = true;
    
	protected static final boolean PRINT_CURRENT_INS = false;

	protected static final boolean PRINT_DISASSEMBLY = false;

	protected static final String EMPTY_STRING = "";

	public static long totalCycles;
	
	private final MemorySubsystem memory = new MemorySubsystem();
	private final CPU cpu = new CPU( this.memory );

	private IMemoryProvider memoryProvider;

	private boolean hwBreakpointReached;
	
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
	    hwBreakpointReached = false;
	    
		memory.reset();

		// all 6502 CPUs read their initial PC value from $FFFC
		memory.writeWord( (short) CPU.RESET_VECTOR_LOCATION , (short) 0xfce2 );

		if ( this.memoryProvider != null )
		{
			this.memoryProvider.loadInto( memory );
		}

		// reset CPU, will initialize PC from RESET_VECTOR_LOCATION
		cpu.reset();
		
		totalCycles = 0;
	}

	public void doOneCycle(EmulatorDriver driver)
	{
		/* First (low) half of clock cycle.
		 *
		 * One period of this signal corresponds to one clock cycle consisting
         * of two phases: ph2 is low in the first phase and high in the second
         * phase (hence the name 'ph2' for "phase 2"). The 6510 only accesses
         * the bus in the second (HIGH) clock phase, the VIC normally only in the
         * first (LOW) phase.
		 */

		memory.tick( this , this.cpu , false ); // clock == LOW

		/*
		 * Second (high) half of clock cycle.
		 */
		if ( this.cpu.cycles > 0 ) // wait until current command has 'finished' executing
		{
			this.cpu.cycles--;
		}
		else
		{
			this.cpu.handleInterrupt();
			
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

		memory.tick( this , cpu , true ); // clock == HIGH
		
        if ( cpu.isHardwareBreakpointReached() || hwBreakpointReached ) 
        {
            hwBreakpointReached = false;
            driver.hardwareBreakpointReached();
        }		
        
        if ( TRACK_TOTAL_CYCLES ) {
            totalCycles++;
        }
	}

	public KeyboardBuffer getKeyboardBuffer() {
		return memory.ioArea.keyboardBuffer;
	}

	public IECBus getBus() {
		return memory.ioArea.iecBus;
	}

    public void hardwareBreakpointReached() {
        hwBreakpointReached = true;
    }
}