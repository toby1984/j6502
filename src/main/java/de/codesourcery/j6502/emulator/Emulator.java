package de.codesourcery.j6502.emulator;

import java.util.function.Consumer;

import de.codesourcery.j6502.assembler.parser.Opcode;
import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.disassembler.Disassembler.Line;
import de.codesourcery.j6502.emulator.OtherCPU.MemoryWrite;
import de.codesourcery.j6502.emulator.exceptions.InvalidOpcodeException;
import de.codesourcery.j6502.utils.HexDump;

public class Emulator
{
	protected static final boolean PRINT_CURRENT_INS = false;

	protected static final boolean PRINT_DISASSEMBLY = false;

	protected boolean failOnBRK = true;

	protected static final String EMPTY_STRING = "";

	private final MemorySubsystem memory = new MemorySubsystem();

	private final CPU cpu = new CPU( this.memory );

	private IMemoryProvider memoryProvider;

	private final CPU clonedCPU = new CPU(this.memory); // TODO: Remove debug code
	
	private final OtherCPU otherCPU;

	public Emulator() {
		otherCPU = new OtherCPU( clonedCPU , memory );
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

			if ( cpu.isInterruptQueued() && ! cpu.isSet(CPU.Flag.IRQ_DISABLE ) )
			{
				cpu.performInterrupt(memory);
			}

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

			final int oldPc = cpu.pc();

			final int opcode = memory.readByte( cpu.pc() );
			clonedCPU.populateFrom( cpu ); // TODO: Remove debug code

			boolean threwException=false;
			try {
			    otherCPU.executeInstruction();
			} catch(Exception e) {
			    System.err.println("otherCPU threw exception: "+e.getMessage()+","+e.getClass().getSimpleName());
			}
			
			doSingleStep();
			
			// TODO: Remove debug code
			
			if ( ! threwException ) 
			{
			    // compare CPU outputs
			    final boolean performCheck = ! ( opcode == 0xad && otherCPU.ea == 0xdc0d );
			    if ( performCheck ) 
			    {
			        if ( ! clonedCPU.matches( cpu ) ) 
			        { // TODO: Remove debug code
			            System.err.println("EXPECTED: ");
			            System.err.println( cpu.toString() );
			            System.err.println("GOT: ");
			            System.err.println( clonedCPU.toString() );
			            //    			    throw new RuntimeException("CPU states don't match, opcode: $"+Integer.toHexString(opcode));
			            System.err.println("CPU states don't match, opcode: $"+Integer.toHexString(opcode));
			        }

			        for ( int i = 0 , len = otherCPU.writes.size() ; i < len ; i++ ) // TODO: Remove debug code 
			        {
			            final MemoryWrite write = otherCPU.writes.get(i);
			            if ( ! write.assertMatches( memory ) ) 
			            {
			                //    			        throw new RuntimeException("Memory location "+HexDump.toAdr( write.address)+" differs! expected: "+write.value+", got "+memory.readByte( write.address )+" , opcode: "+opcode );
			                System.err.println("Memory location "+HexDump.toAdr( write.address)+" differs! expected: "+write.value+", got "+memory.readByte( write.address )+" , opcode: "+opcode );
			            }
			        }
    			}
			}

			if ( PRINT_DISASSEMBLY ) {
				System.out.println( cpu );
			}
			cpu.previousPC = (short) oldPc;
		}

		memory.tick( cpu , true ); // clock == HIGH
	}

	private void doSingleStep()
	{
		int op = memory.readByte( cpu.pc() );

		if ( PRINT_CURRENT_INS ) {
			System.out.println( "**************** Executing $"+HexDump.byteToString((byte) op)+" @ "+HexDump.toAdr( cpu.pc() ));
		}

		// mixed bag of opcodes...
		switch( op )
		{
			case 0x00:
				if ( failOnBRK ) {
					unknownOpcode( op );
				}
				Opcode.BRK.execute(op,cpu ,memory,this);
				return;
			case 0x02: // "illegal opcode"
			case 0x12: // "illegal opcode"
			case 0x22: // "illegal opcode"
			case 0x32: // "illegal opcode"
			case 0x42: // "illegal opcode"
			case 0x52: // "illegal opcode"
			case 0x62: // "illegal opcode"
			case 0x72: // "illegal opcode"
			case 0x92: // "illegal opcode"
			case 0xB2: // "illegal opcode"
			case 0xD2: // "illegal opcode"
			case 0xF2: // "illegal opcode"
				Opcode.HLT.execute(op,cpu ,memory,this);
				return;
            case 0x0C: // "illegal opcode"
            case 0x1C: // "illegal opcode"
            case 0x3C: // "illegal opcode"
            case 0x5C: // "illegal opcode"
            case 0x7C: // "illegal opcode"
            case 0xDC: // "illegal opcode"
            case 0xFC: // "illegal opcode"
                Opcode.SKW.execute(op,cpu ,memory,this);
                return;
			case 0x20: Opcode.JSR.execute(op,cpu ,memory,this); return;
			case 0x40: Opcode.RTI.execute(op,cpu ,memory,this); return;
			case 0x60: Opcode.RTS.execute(op,cpu ,memory,this); return;
			case 0x08: Opcode.PHP.execute(op,cpu ,memory,this); return;
			case 0x28: Opcode.PLP.execute(op,cpu ,memory,this); return;
			case 0x48: Opcode.PHA.execute(op,cpu ,memory,this); return;
			case 0x68: Opcode.PLA.execute(op,cpu ,memory,this); return;
			case 0x88: Opcode.DEY.execute(op,cpu ,memory,this); return;
			case 0xa8: Opcode.TAY.execute(op,cpu ,memory,this); return;
			case 0xc8: Opcode.INY.execute(op,cpu ,memory,this); return;
			case 0xe8: Opcode.INX.execute(op,cpu ,memory,this); return;
			case 0x18: Opcode.CLC.execute(op,cpu ,memory,this); return;
			case 0x38: Opcode.SEC.execute(op,cpu ,memory,this); return;
			case 0x58: Opcode.CLI.execute(op,cpu ,memory,this); return;
			case 0x78: Opcode.SEI.execute(op,cpu ,memory,this); return;
			case 0x8f: // "illegal opcode"
			case 0x87: // "illegal opcode"
			case 0x97: // "illegal opcode"
			case 0x83: // "illegal opcode"
				Opcode.AXS.execute(op,cpu ,memory,this);
				return;
			case 0x98: Opcode.TYA.execute(op,cpu ,memory,this); return;
			case 0xb8: Opcode.CLV.execute(op,cpu ,memory,this); return;
			case 0xd8: Opcode.CLD.execute(op,cpu ,memory,this); return;
			case 0xf8: Opcode.SED.execute(op,cpu ,memory,this); return;
			case 0x8a: Opcode.TXA.execute(op,cpu ,memory,this); return;
			case 0x9a: Opcode.TXS.execute(op,cpu ,memory,this); return;
			case 0xaa: Opcode.TAX.execute(op,cpu ,memory,this); return;
			case 0xba: Opcode.TSX.execute(op,cpu ,memory,this); return;
			case 0xca: Opcode.DEX.execute(op,cpu ,memory,this); return;
			case 0xea: Opcode.NOP.execute(op,cpu ,memory,this); return;
			// bail out early on some illegal opcodes that
			// would otherwise be wrongly classified as being valid instructions
			// because they match some of the generic patterns I check for (mostly JMP instruction patterns)
			case 0x64:
			case 0x74:
				unknownOpcode( op );
				return;
			default:
				// $$FALL-THROUGH$$
		}

		// branch instructions
		switch( op ) {
			case 0x10: Opcode.BPL.execute(op,cpu ,memory,this); return;
			case 0x30: Opcode.BMI.execute(op,cpu ,memory,this); return;
			case 0x50: Opcode.BVC.execute(op,cpu ,memory,this); return;
			case 0x70: Opcode.BVS.execute(op,cpu ,memory,this); return;
			case 0x90: Opcode.BCC.execute(op,cpu ,memory,this); return;
			case 0xB0: Opcode.BCS.execute(op,cpu ,memory,this); return;
			case 0xD0: Opcode.BNE.execute(op,cpu ,memory,this); return;
			case 0xF0: Opcode.BEQ.execute(op,cpu ,memory,this); return;
			default:
				//$$FALL-THROUGH$$
		}

		final int cc = (op & 0b11);
		if ( cc == 0b01 )
		{
			executeGeneric1( op );
			return;
		}
		if ( cc == 0b10 ) {
			executeGeneric2( op );
			return;
		}
		if ( cc == 0b00 ) {
			executeGeneric3( op );
			return;
		}
		unknownOpcode( op );
	}

	private int unknownOpcode( int opcode)
	{
		throw new InvalidOpcodeException("Unknown opcode: $"+HexDump.byteToString((byte) (opcode & 0xff)) , cpu.pc() , (byte) opcode );
	}

	private void executeGeneric1(int op)
	{
		final int instruction = (op & 0b11100000) >> 5;
		switch ( instruction )
		{
			case 0b000: Opcode.ORA.execute(op,cpu ,memory,this); break;
			case 0b001: Opcode.AND.execute(op,cpu ,memory,this); break;
			case 0b010: Opcode.EOR.execute(op,cpu ,memory,this); break;
			case 0b011: Opcode.ADC.execute(op,cpu ,memory,this); break;
			case 0b100: Opcode.STA.execute(op,cpu ,memory,this); break;
			case 0b101: Opcode.LDA.execute(op,cpu ,memory,this); break;
			case 0b110: Opcode.CMP.execute(op,cpu ,memory,this); break;
			case 0b111: Opcode.SBC.execute(op,cpu ,memory,this); break;
			default:
				unknownOpcode( op ); // never returns
		}
	}

	private void executeGeneric2(int op)
	{
		final int instruction = (op & 0b11100000) >> 5;
		switch ( instruction )
		{
			case 0b000: Opcode.ASL.execute(op,cpu ,memory,this); break;
			case 0b001: Opcode.ROL.execute(op,cpu ,memory,this); break;
			case 0b010: Opcode.LSR.execute(op,cpu ,memory,this); break;
			case 0b011: Opcode.ROR.execute(op,cpu ,memory,this); break;
			case 0b100: Opcode.STX.execute(op,cpu ,memory,this); break;
			case 0b101: Opcode.LDX.execute(op,cpu ,memory,this); break;
			case 0b110: Opcode.DEC.execute(op,cpu ,memory,this); break;
			case 0b111: Opcode.INC.execute(op,cpu ,memory,this); break;
			default:
				unknownOpcode( op ); // never returns
		}
	}

	private void executeGeneric3(int op)
	{
		final int instruction = (op & 0b11100000) >> 5;
		switch ( instruction )
		{
			case 0b001: Opcode.BIT.execute(op,cpu ,memory,this); break;
			case 0b010: Opcode.JMP.execute(op,cpu ,memory,this); break;
			case 0b011: Opcode.JMP.execute(op,cpu ,memory,this); break;
			case 0b100: Opcode.STY.execute(op,cpu ,memory,this); break;
			case 0b101: Opcode.LDY.execute(op,cpu ,memory,this); break;
			case 0b110: Opcode.CPY.execute(op,cpu ,memory,this); break;
			case 0b111: Opcode.CPX.execute(op,cpu ,memory,this); break;
			default:
				unknownOpcode( op ); // never returns
		}
	}

	public KeyboardBuffer getKeyboardBuffer() {
		return memory.ioArea.keyboardBuffer;
	}

	public IECBus getBus() {
		return memory.ioArea.iecBus;
	}
}