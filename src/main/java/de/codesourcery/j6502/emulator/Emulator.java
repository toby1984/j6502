package de.codesourcery.j6502.emulator;

import java.util.function.Consumer;

import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.disassembler.Disassembler.Line;
import de.codesourcery.j6502.emulator.tapedrive.TapeDrive;

public class Emulator
{
    public static final boolean TRACK_TOTAL_CYCLES = false;
    
	protected static final boolean PRINT_CURRENT_INS = false;

	protected static final boolean PRINT_DISASSEMBLY = false;

	protected static final String EMPTY_STRING = "";

	public static long totalCycles;
	
	public final TapeDrive tapeDrive = new TapeDrive();
	
	private final MemorySubsystem memory = new MemorySubsystem(tapeDrive);
	public final CPU cpu = new CPU( this.memory );

	private IMemoryProvider memoryProvider;

	private boolean externalHwBreakpointReached;
	
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
	
	public CIA getCIA1() {
	    return memory.ioArea.cia1;
	}
	
    public CIA getCIA2() {
        return memory.ioArea.cia2;
    }	
	
	public CPU getCPU() {
		return cpu;
	}

	public MemorySubsystem getMemory() {
		return memory;
	}

	public void reset()
	{
	    externalHwBreakpointReached = false;
	    
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
        boolean internalHwBreakpointReached = false;
        if ( --this.cpu.cycles == 0 ) // wait until current command has 'finished' executing
        {
            internalHwBreakpointReached = cpu.isBreakpointReached(); 
            
            if ( this.cpu.handleInterrupt() ) {
                this.cpu.cycles+=7; // delay executing first IRQ routine instruction by 7 clock cycles , that's how long the 6510 takes to jump to an IRQ
            } 
            else
            {
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
        }

        memory.tick( this , cpu , true ); // clock == HIGH
        
        if ( internalHwBreakpointReached || externalHwBreakpointReached ) 
        {
            externalHwBreakpointReached = false;
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

    public void setExternalHwBreakpointReached() {
        externalHwBreakpointReached = true;
    }
}