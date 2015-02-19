package de.codesourcery.j6502.assembler.parser;

import de.codesourcery.j6502.assembler.AddressingMode;
import de.codesourcery.j6502.assembler.ICompilationContext;
import de.codesourcery.j6502.assembler.exceptions.BranchTargetOutOfRangeException;
import de.codesourcery.j6502.assembler.exceptions.InvalidAddressingModeException;
import de.codesourcery.j6502.assembler.parser.ast.IASTNode;
import de.codesourcery.j6502.assembler.parser.ast.IValueNode;
import de.codesourcery.j6502.assembler.parser.ast.IndirectOperand;
import de.codesourcery.j6502.assembler.parser.ast.InstructionNode;
import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.IEmulator;
import de.codesourcery.j6502.emulator.IMemoryRegion;
import de.codesourcery.j6502.emulator.exceptions.InvalidOpcodeException;
import de.codesourcery.j6502.utils.HexDump;

public enum Opcode
{
	// generic #1
	LDA("LDA")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b101 ); }
		@Override
		public void execute(int opcode,CPU cpu, IMemoryRegion memory, IEmulator emulator)
		{
			switch( opcode ) {
				//                  MODE        SYNTAX       LEN TIM
				case 0xA9:    // Immediate     LDA #$44       2   2
					cpu.accumulator = readImmediateValue(cpu, memory);
					cpu.cycles += 2;
					break;
				case 0xA5:    // Zero Page     LDA $44        2   3
					cpu.accumulator = readZeroPageValue(cpu,memory);
					cpu.cycles += 3;
					break;
				case 0xB5:    // Zero Page,X   LDA $44,X      2   4
					cpu.accumulator = readAbsoluteZeroPageXValue(cpu,memory);
					cpu.cycles += 4;
					break;
				case 0xAD: // Absolute      LDA $4400      3   4
					cpu.accumulator = readAbsoluteValue(cpu,memory);
					cpu.cycles += 4;
					break;
				case 0xBD: // Absolute,X    LDA $4400,X    3   4+
					cpu.accumulator = readAbsoluteXValue(cpu, memory,4);
					break;
				case 0xB9: // Absolute,Y    LDA $4400,Y    3   4+
					cpu.accumulator = readAbsoluteYValue(cpu,memory,4);
					break;
				case 0xA1: // Indexed Indirect,X    LDA ($44,X)    2   6
					cpu.accumulator = readIndexedIndirectX(cpu, memory);
					cpu.cycles += 6;
					break;
				case 0xB1: // Indirect Indexed,Y    LDA ($44),Y    2   5+
					cpu.accumulator = readIndirectIndexedY(cpu, memory , 5 );
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
			updateZeroSignedFromAccumulator(cpu);
		}
	},
	STA("STA")
	{
		@Override
		public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			if ( ins.getAddressingMode() == AddressingMode.IMMEDIATE ) // STA #$xx makes no sense
			{
				throw new InvalidAddressingModeException( ins );
			}
			assembleGeneric1(ins,writer, 0b100 );
		}

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator)
		{
			switch( opcode )
			{
				case 0x85: // Zero Page     STA $44       $85  2   3
					writeZeroPage( cpu.accumulator , cpu , memory );
					cpu.cycles += 3;
					break;
				case 0x95: // Zero Page,X   STA $44,X     $95  2   4
					writeAbsoluteZeroPageXValue( cpu.accumulator , cpu, memory );
					cpu.cycles += 4;
					break;
				case 0x8D: // Absolute      STA $4400     $8D  3   4
					writeAbsoluteValue( cpu.accumulator , cpu , memory );
					cpu.cycles += 4;
					break;
				case 0x9D: // Absolute,X    STA $4400,X   $9D  3   5
					writeAbsoluteXValue( cpu.accumulator , cpu , memory );
					cpu.cycles += 5;
					break;
				case 0x99: // Absolute,Y    STA $4400,Y   $99  3   5
					writeAbsoluteYValue( cpu.accumulator , cpu , memory );
					cpu.cycles += 5;
					break;
				case 0x81: // Indexed Indirect,X    STA ($44,X)   $81  2   6
					writeIndexedIndirectX( cpu.accumulator , cpu , memory );
					cpu.cycles += 6;
					break;
				case 0x91: // Indirect,Y    STA ($44),Y   $91  2   6
					writeIndirectIndexedY( cpu.accumulator , cpu , memory );
					cpu.cycles += 6;
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
		}
	},
	ORA("ORA")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b000 ); }
	},
	AND("AND")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b001 ); }
	},
	EOR("EOR")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b010 ); }
	},
	ADC("ADC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b011 ); }
	},
	CMP("CMP")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b110 ); }
	},
	SBC("SBC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b111 ); }
	},
	// generic #2
	ASL("ASL")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			if ( ins.getAddressingMode() == AddressingMode.IMMEDIATE ) // ASL #$xx makes no sense
			{
				throw new InvalidAddressingModeException( ins );
			}
			assembleGeneric2(ins,writer, 0b000 );
		}
	},
	ROL("ROL")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			if ( ins.getAddressingMode() == AddressingMode.IMMEDIATE ) // ROL #$xx makes no sense
			{
				throw new InvalidAddressingModeException( ins );
			}
			assembleGeneric2(ins,writer, 0b001 );
		}
	},
	LSR("LSR")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			if ( ins.getAddressingMode() == AddressingMode.IMMEDIATE ) // LSR #$xx makes no sense
			{
				throw new InvalidAddressingModeException( ins );
			}
			assembleGeneric2(ins,writer, 0b010 );
		}
	},
	ROR("ROR")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			if ( ins.getAddressingMode() == AddressingMode.IMMEDIATE ) // ROR #$xx makes no sense
			{
				throw new InvalidAddressingModeException( ins );
			}
			assembleGeneric2(ins,writer, 0b011 );
		}
	},
	STX("STX")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case ABSOLUTE:
				case ZERO_PAGE:
				case ZERO_PAGE_Y:
					assembleGeneric2(ins,writer, 0b100 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}
		}

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)
		{
			/*
STX (STore X register)

Affects Flags: none

MODE           SYNTAX       HEX LEN TIM
Zero Page     STX $44       $86  2   3
Zero Page,Y   STX $44,Y     $96  2   4
Absolute      STX $4400     $8E  3   4
			 */
			switch(opcode)
			{
				case 0x86: // Zero Page     STX $44       $86  2   3
					writeZeroPage( cpu.x, cpu , memory );
					cpu.cycles += 3;
					break;
				case 0x96: // Zero Page,Y   STX $44,Y     $95  2   4
					writeAbsoluteZeroPageYValue( cpu.x , cpu, memory );
					cpu.cycles += 4;
					break;
				case 0x8e: // Absolute      STA $4400     $8D  3   4
					writeAbsoluteValue( cpu.x , cpu , memory );
					cpu.cycles += 4;
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
		}
	},
	LDX("LDX")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case IMPLIED:
				case ZERO_PAGE_X:
				case ABSOLUTE_INDEXED_X:
				case INDEXED_INDIRECT_X:
				case INDIRECT_INDEXED_Y:
					throw new InvalidAddressingModeException( ins );
				default:
					// $$FALL-THROUGH$$
			}
			assembleGeneric2(ins,writer, 0b101 );
		}

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)
		{
			/*
MODE           SYNTAX       HEX LEN TIM
Immediate     LDX #$44      $A2  2   2
Zero Page     LDX $44       $A6  2   3
Zero Page,Y   LDX $44,Y     $B6  2   4
Absolute      LDX $4400     $AE  3   4
Absolute,Y    LDX $4400,Y   $BE  3   4+
			 */

			switch( opcode ) {
				//                  MODE        SYNTAX       LEN TIM
				case 0xA2:    // Immediate     LDX #$44       2   2
					cpu.x = (byte) readImmediateValue(cpu, memory);
					cpu.cycles += 2;
					break;
				case 0xA6:    // Zero Page     LDX $44        2   3
					cpu.x = (byte) readZeroPageValue(cpu,memory);
					cpu.cycles += 3;
					break;
				case 0xB6:    // Zero Page,Y   LDX $44,Y      2   4
					cpu.x = (byte) readAbsoluteZeroPageYValue(cpu,memory);
					cpu.cycles += 4;
					break;
				case 0xAE: // Absolute      LDX $4400      3   4
					cpu.x = (byte) readAbsoluteValue(cpu,memory);
					cpu.cycles += 4;
					break;
				case 0xBE: // Absolute,Y    LDX $4400,Y    3   4+
					cpu.x = (byte) readAbsoluteYValue(cpu,memory,4);
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
			updateZeroSignedFromX(cpu);
		}
	},
	DEC("DEC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case ABSOLUTE:
				case ABSOLUTE_INDEXED_X:
				case ZERO_PAGE:
				case ZERO_PAGE_X:
					assembleGeneric2(ins,writer, 0b110 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}
		}
	},
	INC("INC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case ABSOLUTE:
				case ABSOLUTE_INDEXED_X:
				case ZERO_PAGE:
				case ZERO_PAGE_X:
					assembleGeneric2(ins,writer, 0b111 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}
		}
	},
	// generic #3
	BIT("BIT")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case ABSOLUTE:
				case ZERO_PAGE:
					assembleGeneric3(ins,writer, 0b001 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}
		}
	},
	JMP("JMP")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			/*
			 * Indirect      JMP ($5597)   $6C  3   5
			 * Absolute      JMP $5597     $4C  3   3
			 */
			if ( ins.hasChildren() )
			{
				final IASTNode child0 = ins.child(0);
				final IASTNode child1 = ins.getChildCount() > 1 ? ins.child(1) : null;
				if ( child0 != null && child0 instanceof IndirectOperand && child1 == null ) { // JMP ($xxxx)
					writer.writeByte( (byte) 0x6c );
					writer.writeWord( child0.child(0) );
					return;
				}
				switch ( ins.getAddressingMode() )
				{
					case ABSOLUTE:
					case ZERO_PAGE:
						writer.writeByte( (byte) 0x4c );
						writer.writeWord( child0.child(0) );
						return;
					default:
						// $$FALL-THROUGH$$
				}
			}
			throw new InvalidAddressingModeException( ins );
		}

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)
		{
			/*
			 * Indirect      JMP ($5597)   $6C  3   5
			 * Absolute      JMP $5597     $4C  3   3
			 */
			switch( opcode & 0xff ) {
				case 0x6c:
					cpu.pc++;
					cpu.pc = memory.readWord( memory.readWord( cpu.pc ) );
					cpu.cycles += 5;
					break;
				case 0x4c:
					cpu.pc++;
					cpu.pc = memory.readWord( cpu.pc );
					cpu.cycles += 3;
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
		}
	},
	STY("STY")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case ZERO_PAGE:
				case ABSOLUTE:
				case ZERO_PAGE_X:
					assembleGeneric3(ins,writer, 0b100 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}
		}

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)
		{
/*
MODE           SYNTAX       HEX LEN TIM
Zero Page     STY $44       $84  2   3
Zero Page,X   STY $44,X     $94  2   4
Absolute      STY $4400     $8C  3   4
 */
			switch(opcode)
			{
				case 0x84:
					writeZeroPage( cpu.y, cpu , memory );
					cpu.cycles += 3;
					break;
				case 0x94:
					writeAbsoluteZeroPageXValue( cpu.y , cpu, memory );
					cpu.cycles += 4;
					break;
				case 0x8c:
					writeAbsoluteValue( cpu.y , cpu , memory );
					cpu.cycles += 4;
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
		}
	},
	LDY("LDY")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case IMMEDIATE:
				case ZERO_PAGE:
				case ZERO_PAGE_X:
				case ABSOLUTE:
				case ABSOLUTE_INDEXED_X:
					assembleGeneric3(ins,writer, 0b101 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}
		}

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)
		{
			/*
 Affects Flags: S Z

MODE           SYNTAX       HEX LEN TIM
Immediate     LDY #$44      $A0  2   2
Zero Page     LDY $44       $A4  2   3
Zero Page,X   LDY $44,X     $B4  2   4
Absolute      LDY $4400     $AC  3   4
Absolute,X    LDY $4400,X   $BC  3   4+

+ add 1 cycle if page boundary crossed

			 */

			switch( opcode ) {
				//                  MODE        SYNTAX       LEN TIM
				case 0xA0:    // Immediate     LDX #$44       2   2
					cpu.y = (byte) readImmediateValue(cpu, memory);
					cpu.cycles += 2;
					break;
				case 0xA4:    // Zero Page     LDX $44        2   3
					cpu.y = (byte) readZeroPageValue(cpu,memory);
					cpu.cycles += 3;
					break;
				case 0xB4:    // Zero Page,Y   LDX $44,X      2   4
					cpu.y = (byte) readAbsoluteZeroPageXValue(cpu,memory);
					cpu.cycles += 4;
					break;
				case 0xAC: // Absolute      LDX $4400      3   4
					cpu.y = (byte) readAbsoluteValue(cpu,memory);
					cpu.cycles += 4;
					break;
				case 0xBC: // Absolute,Y    LDY $4400,X    3   4+
					cpu.y = (byte) readAbsoluteXValue(cpu,memory,4);
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
			updateZeroSignedFromY(cpu);
		}
	},
	CPY("CPY")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case IMMEDIATE:
				case ZERO_PAGE:
				case ABSOLUTE:
					assembleGeneric3(ins,writer, 0b110 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}

		}
	},
	CPX("CPX")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case IMMEDIATE:
				case ZERO_PAGE:
				case ABSOLUTE:
					assembleGeneric3(ins,writer, 0b111 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}

		}
	},
	/* Conditional branches.
	 *
	 * The conditional branch instructions all have the form xxy10000
	 * with xx being the flag under test and y being the flag's expected value (either set or not set).
	 *
	 * xx	flag
	 * 00	negative
	 * 01	overflow
	 * 10	carry
	 * 11	zero
	 *
	 * BPL	BMI	BVC	BVS	BCC	BCS BNE	BEQ
	 * 10	30	50	70	90	B0 	D0	F0
	 */
	BPL("BPL")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0x10 ); }
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleBranch( cpu , memory ); }
	},
	BMI("BMI")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0x30 ); }
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleBranch( cpu , memory ); }
	},
	BVC("BVC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0x50 ); }
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleBranch( cpu , memory ); }
	},
	BVS("BVS")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0x70 ); }
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleBranch( cpu , memory ); }
	},
	BCC("BCC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0x90 ); }
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleBranch( cpu , memory ); }
	},
	BCS("BCS")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0xb0 ); }
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleBranch( cpu , memory ); }
	},
	BNE("BNE")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0xd0 ); }
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleBranch( cpu , memory ); }
	},
	BEQ("BEQ")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0xf0 ); }
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleBranch( cpu , memory ); }
	},
	BRK("BRK") {

		@Override
		public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			if ( ins.getAddressingMode() != AddressingMode.IMPLIED ) {
				throw new InvalidAddressingModeException( ins );
			}
			writer.writeByte( (byte) 0x00 );
		}
	},
	JSR("JSR") {

		@Override
		public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() ) {
				case ABSOLUTE:
				case ZERO_PAGE:
					writer.writeByte( (byte) 0x20 );
					writer.writeWord( getWordValue( ins.child(0).child(0) ) );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}
		}

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)
		{
/*
MODE           SYNTAX       HEX LEN TIM
Absolute      JSR $5597     $20  3   6

JSR pushes the address-1 of the next operation on to the stack before transferring program control to the following address.
Subroutines are normally terminated by a RTS op code.
 */
			if ( (opcode & 0xff ) != 0x20 ) {
				throw new RuntimeException("Unreachable code reached");
 			}
			cpu.pc++;
			final short jumpTarget = memory.readWord( cpu.pc );
			short adr = cpu.pc;
			adr++; // +1 because address-1 needs to be pushed
			push( adr , cpu , memory );
			cpu.pc = jumpTarget;
			cpu.cycles += 6;
		}
	},
	RTI("RTI" , (byte) 0x40 ),
	RTS("RTS" , (byte) 0x60 ) {

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)
		{
			/*
			 * MODE           SYNTAX       HEX LEN TIM
             * Implied       RTS           $60  1   6
             *
			 * RTS pulls the top two bytes off the stack (low byte first) and transfers program control to that address+1.
			 * It is used, as expected, to exit a subroutine invoked via JSR which pushed the address-1.
			 */
			if ( (opcode & 0xff) != 0x60 ) {
				throw new RuntimeException("Unreachable code reached");
			}
			final byte lo = pop(cpu, memory);
			final byte hi = pop(cpu, memory);
			short adr = (short) (hi<<8 | lo);
			adr++;
			cpu.pc = adr;
			cpu.cycles += 6;
		}
	},
	PHP("PHP",(byte) 0x08) {
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)  { handleStackInstruction(cpu, memory); }
	},
	PLP("PLP",(byte) 0x28) {
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)  { handleStackInstruction(cpu, memory); }
	},
	PHA("PHA",(byte) 0x48) {
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)  { handleStackInstruction(cpu, memory); }
	},
	PLA("PLA",(byte) 0x68) {
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)  { handleStackInstruction(cpu, memory); }
	},
	DEY("DEY",(byte) 0x88) {
		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleRegisterInstructions(cpu,memory); }
	},
	TAY("TAY",(byte) 0xa8) {
		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleRegisterInstructions(cpu,memory); }
	},
	INY("INY",(byte) 0xc8) {
		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleRegisterInstructions(cpu,memory); }
	},
	INX("INX",(byte) 0xe8) {
		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleRegisterInstructions(cpu,memory); }
	},
	CLC("CLC",(byte) 0x18) {
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleProcessorStatus(cpu, memory); }
	},
	SEC("SEC",(byte) 0x38) {
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleProcessorStatus(cpu, memory); }
	},
	CLI("CLI",(byte) 0x58) {
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleProcessorStatus(cpu, memory); }
	},
	SEI("SEI",(byte) 0x78) {
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleProcessorStatus(cpu, memory); }
	},
	TYA("TYA",(byte) 0x98) {
		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleRegisterInstructions(cpu,memory); }
	},
	CLV("CLV",(byte) 0xb8) {
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleProcessorStatus(cpu, memory); }
	},
	CLD("CLD",(byte) 0xd8) {
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleProcessorStatus(cpu, memory); }
	},
	SED("SED",(byte) 0xf8) {
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleProcessorStatus(cpu, memory); }
	},
	TXA("TXA",(byte) 0x8a) {
		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleRegisterInstructions(cpu,memory); }
	},
	TXS("TXS",(byte) 0x9a) {
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)  { handleStackInstruction(cpu, memory); }
	},
	TAX("TAX",(byte) 0xaa) {
		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleRegisterInstructions(cpu,memory); }
	},
	TSX("TSX",(byte) 0xba) {
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)  { handleStackInstruction(cpu, memory); }
	},
	DEX("DEX",(byte) 0xca) {
		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator) { handleRegisterInstructions(cpu,memory); }
	},
	NOP("NOP",(byte) 0xea )
	{
		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory,IEmulator emulator)
		{
			cpu.pc++;
			cpu.cycles+=2;
		}
	};

	private final String mnemonic;
	private final byte opcode;

	private Opcode(String mnemonic) {
		this.mnemonic = mnemonic.toUpperCase();
		this.opcode = 0;
	}

	private Opcode(String mnemonic,byte opcode) {
		this.mnemonic = mnemonic.toUpperCase();
		this.opcode = opcode;
	}

	private static void assembleConditionalBranch(InstructionNode ins,ICompilationContext writer,byte opcode)
	{
		if ( ins.getAddressingMode() != AddressingMode.ABSOLUTE && ins.getAddressingMode() != AddressingMode.ZERO_PAGE) // ASL #$xx makes no sense
		{
			throw new InvalidAddressingModeException( ins );
		}
		final byte relOffset = getRelativeOffset( ins , writer );
		writer.writeByte( opcode  );
		writer.writeByte( relOffset );
	}

	private static byte getRelativeOffset(InstructionNode ins, ICompilationContext writer)
	{
		final IValueNode lit = (IValueNode) ins.child(0).child(0);
		if ( ! lit.isValueAvailable() ) {
			return 0;
		}

		final int relOffset = lit.getWordValue() - writer.getCurrentAddress()-2; // -2 because branching is done relative to the END of the branch instruction

		if( relOffset > 255 || relOffset < -128 ) {
			throw new BranchTargetOutOfRangeException(ins, relOffset);
		}
		return (byte) relOffset;
	}

	public String getMnemonic() {
		return mnemonic;
	}

	public static Opcode getOpcode(String s)
	{
		final String up = s.toUpperCase();
		for ( final Opcode op : values() ) {
			if ( op.mnemonic.equals( up ) ) {
				return op;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return mnemonic;
	}

	public static boolean isZeroPage(int value) {
		value = value & 0xffff;
		return value <= 255;
	}

	private static void assembleGeneric1(InstructionNode ins, ICompilationContext writer,int aaa)
	{
		final int cc = 0b01;
		/*
		 * pattern:  aaabbbcc
		 *
		 * where cc = 01
		 *
		 * and bbb:
		 *
		 * bbb	addressing mode
		 * 000	(zero page,X)
		 * 001	zero page
		 * 010	#immediate
		 * 011	absolute
		 * 100	(zero page),Y
		 * 101	zero page,X
		 * 110	absolute,Y
		 * 111	absolute,X
		 */
		final IASTNode child0 = ins.child(0);
		int opCode=0;
		switch( ins.getAddressingMode() )
		{
			case ABSOLUTE:
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute      LDA $4400     $AD  3   4
				opCode = (aaa << 5) | ( 0b011 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400
				writer.writeWord( child0.child(0) );
				break;
			case ABSOLUTE_INDEXED_X:
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute,X    LDA $4400,X   $BD  3   4+
				opCode = (aaa << 5) | ( 0b111 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400,X
				writer.writeWord( child0.child(0) );
				break;
			case ABSOLUTE_INDEXED_Y:
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute,Y      LDA $4400,Y   $B9  3   4+
				opCode = (aaa << 5) | ( 0b110 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400,Y
				writer.writeWord( child0.child(0) );
				break;
			case ZERO_PAGE_Y:
			case ZERO_PAGE_X:
				// MODE           SYNTAX         HEX LEN TIM
				// Zero Page,X   LDA $44,X     $B5  2   4
				opCode = (aaa << 5) | ( 0b101 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $44,X
				writer.writeByte( child0.child(0) );
				break;
			case IMMEDIATE:
				// MODE           SYNTAX         HEX LEN TIM
				// # Immediate     LDA #$44      $A9  2   2
				opCode = (aaa << 5) | ( 0b010 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA #$44
				writer.writeByte( child0.child(0) );
				break;
			case INDEXED_INDIRECT_X:
				// MODE           SYNTAX         HEX LEN TIM
				// Indirect,X      LDA ($44,X)   $A1  2   6
				opCode = (aaa << 5) | ( 0b000 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA ($44,X)
				writer.writeByte( child0.child(0) );
				break;
			case INDIRECT_INDEXED_Y:
				// MODE           SYNTAX         HEX LEN TIM
				// Indirect,Y      LDA ($44),Y   $B1  2   5+
				opCode = (aaa << 5) | ( 0b100 << 2) | cc;
				writer.writeByte( (byte) opCode );
				writer.writeByte( child0.child(0) ); // LDA ($44),Y
				break;
			case ZERO_PAGE:
				// MODE           SYNTAX         HEX LEN TIM
				// Zero Page     LDA $44       $A5  2   3		#
				opCode = (aaa << 5) | ( 0b001 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $44
				writer.writeByte( child0.child(0) );
				break;
			default:
				throw new InvalidAddressingModeException( ins );
		}
	}

	private static void assembleGeneric2(InstructionNode ins, ICompilationContext writer,int aaa)
	{
		final int cc = 0b10;
		/*
		 * pattern:  aaabbbcc
		 *
		 * where cc = 10
		 *
		 * and bbb:
		 *
		 * bbb	addressing mode
		 * 000	#immediate     OK
		 * 001	zero page      OK
		 * 010	accumulator    OK
		 * 011	absolute       OK
		 * 101	zero page,X    OK
		 * 111	absolute,X     OK
		 */
		int opCode=0;
		if ( ins.getAddressingMode() == AddressingMode.IMPLIED ) {
			// MODE           SYNTAX         HEX LEN TIM
			// Absolute      ASL             $AD  3   4
			opCode = (aaa << 5) | ( 0b010 << 2) | cc;
			writer.writeByte( (byte) opCode ); // LDA $4400
			return;
		}

		final IASTNode child0 = ins.child(0);
		switch( ins.getAddressingMode() )
		{
			case IMMEDIATE: // OK
				// MODE           SYNTAX         HEX LEN TIM
				// # Immediate     LDA #$44      $A9  2   2
				opCode = (aaa << 5) | ( 0b000 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA #$44
				writer.writeByte( child0.child(0) );
				break;
			case ZERO_PAGE: // OK
				// MODE           SYNTAX         HEX LEN TIM
				// Zero Page     LDA $44       $A5  2   3		#
				opCode = (aaa << 5) | ( 0b001 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $44
				writer.writeByte( child0.child(0) );
				break;
			case ABSOLUTE: // OK
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute      LDA $4400     $AD  3   4
				opCode = (aaa << 5) | ( 0b011 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400
				writer.writeWord( child0.child(0) );
				break;
			case ZERO_PAGE_Y:
				if ( ins.opcode != Opcode.LDX && ins.opcode != Opcode.STX ) {
					throw new InvalidAddressingModeException( ins );
				}
			case ZERO_PAGE_X: // OK
				// MODE           SYNTAX         HEX LEN TIM
				// Zero Page,X   LDA $44,X     $B5  2   4
				opCode = (aaa << 5) | ( 0b101 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $44,X
				writer.writeByte( child0.child(0) );
				break;
			case ABSOLUTE_INDEXED_Y: // OK
				if ( ins.opcode != Opcode.LDX && ins.opcode != Opcode.STX ) {
					throw new InvalidAddressingModeException( ins );
				}
			case ABSOLUTE_INDEXED_X: // OK
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute,X    LDA $4400,X   $BD  3   4+
				opCode = (aaa << 5) | ( 0b111 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400,X
				writer.writeWord( child0.child(0) );
				break;
			default:
				throw new InvalidAddressingModeException( ins );
		}
	}

	private static void assembleGeneric3(InstructionNode ins, ICompilationContext writer,int aaa)
	{
		final int cc = 0b00;
		/*
		 * pattern:  aaabbbcc
		 *
		 * where cc = 00
		 *
		 * and bbb:
		 *
		 * bbb	addressing mode
		 * 000	#immediate
		 * 001	zero page
		 * 011	absolute
		 * 101	zero page,X
		 * 111	absolute,X
		 */
		final IASTNode child0 = ins.child(0);
		int opCode=0;
		switch( ins.getAddressingMode() )
		{
			case IMMEDIATE:
				// MODE           SYNTAX         HEX LEN TIM
				// # Immediate     LDA #$44      $A9  2   2
				opCode = (aaa << 5) | ( 0b000 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA #$44
				writer.writeByte( child0.child(0) );
				break;
			case ZERO_PAGE:
				// MODE           SYNTAX         HEX LEN TIM
				// Zero Page     LDA $44       $A5  2   3		#
				opCode = (aaa << 5) | ( 0b001 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $44
				writer.writeByte( child0.child(0) );
				break;
			case ABSOLUTE:
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute      LDA $4400     $AD  3   4
				opCode = (aaa << 5) | ( 0b011 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400
				writer.writeWord( child0.child(0) );
				break;
			case ZERO_PAGE_X:
				// MODE           SYNTAX         HEX LEN TIM
				// Zero Page,X   LDA $44,X     $B5  2   4
				opCode = (aaa << 5) | ( 0b101 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $44,X
				writer.writeByte( child0.child(0) );
				break;
			case ABSOLUTE_INDEXED_X:
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute,X    LDA $4400,X   $BD  3   4+
				opCode = (aaa << 5) | ( 0b111 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400,X
				writer.writeWord( child0.child(0) );
				break;
			default:
				throw new InvalidAddressingModeException( ins );
		}
	}

	public static byte getByteValue(IASTNode node)
	{
		final IValueNode lit = (IValueNode) node;
		if ( ! lit.isValueAvailable() ) {
			return (byte) 0xff;
		}
		return lit.getByteValue();
	}

	public static short getWordValue(IASTNode node)
	{
		final IValueNode lit = (IValueNode) node;
		if ( ! lit.isValueAvailable() ) {
			return (short) 0xffff;
		}
		return lit.getWordValue();
	}

	private static void updateZeroSignedFromAccumulator(CPU cpu) {
		updateZeroSigned(cpu.accumulator , cpu );
	}

	private static void updateZeroSignedFromX(CPU cpu) {
		updateZeroSigned(cpu.x , cpu );
	}

	private static void updateZeroSignedFromY(CPU cpu) {
		updateZeroSigned(cpu.y , cpu );
	}

	private static void updateZeroSigned(short v,CPU cpu)
	{
		final int value = (v& 0xff);

		cpu.setFlag(CPU.Flag.ZERO , value == 0 );
		cpu.setFlag(CPU.Flag.NEGATIVE , (value & 0b10000000) != 0 );
	}

	public void assemble(InstructionNode ins, ICompilationContext writer)
	{
		if ( ins.getAddressingMode() != AddressingMode.IMPLIED ) {
			throw new InvalidAddressingModeException( ins );
		}
		writer.writeByte( opcode );
	}

	public void execute(int opcode, CPU cpu , IMemoryRegion memory, IEmulator emulator)
	{
		throw new InvalidOpcodeException( "Opcode $"+HexDump.toHex((byte) opcode)+" not implemented yet @ "+HexDump.toAdr( cpu.pc ) , cpu.pc , (byte) opcode);
	}

	private static boolean isAcrossPageBoundary(int adr1,int adr2) {
		int a1 = adr1;
		int a2 = adr2;
		a1 &= 0xffff;
		a2 &= 0xffff;
		return a1/256 != a2 / 256;
	}

	/* ================
	 * Addressing modes
	 * ================ */

	// LDA ( $12 ) , Y
	private static short readIndirectIndexedY(CPU cpu, IMemoryRegion memory,int cycles)
	{
		cpu.pc ++;
		final short adr1111 = memory.readByte( cpu.pc ); // zp offset
		cpu.pc++;
		final short adr2222 = memory.readWord( adr1111 );
		final short adr3333 = (short) (adr2222 + cpu.y);
		cpu.cycles += isAcrossPageBoundary( adr2222, adr3333 ) ? cycles+1 : cycles;
		return memory.readByte( adr3333 );
	}

	// LDA ( $12 , X )
	private static short readIndexedIndirectX(CPU cpu, IMemoryRegion memory) {
		cpu.pc ++;
		final short adr111 = memory.readByte( cpu.pc ); // zp offset
		cpu.pc++;
		final short adr222 = (short) (adr111 + cpu.x); // zp + offset
		final short adr333 = memory.readWord( adr222 );
		return memory.readByte( adr333 );
	}

	// LDA $1234 , Y
	private static short readAbsoluteYValue(CPU cpu, IMemoryRegion memory,int cycles)
	{
		cpu.pc++;
		final short adr11 = memory.readWord( cpu.pc );
		cpu.pc += 2;
		final short adr22 = (short) (adr11 + cpu.y);
		cpu.cycles += isAcrossPageBoundary( adr11 , adr22 ) ? cycles+1 : cycles;
		return memory.readByte( adr22  ); // accu:= mem[ zp_adr + x ]
	}

	// LDA $1234 , X
	private static short readAbsoluteXValue(CPU cpu, IMemoryRegion memory,int cycles) {
		cpu.pc++;
		final short adr1 = memory.readWord( cpu.pc );
		cpu.pc += 2;
		final short adr2 = (short) (adr1 + cpu.x);
		cpu.cycles += isAcrossPageBoundary( adr1 , adr2 ) ? cycles+1 : cycles;
		return memory.readByte( adr2  ); // accu:= mem[ zp_adr + x ]
	}

	// LDA #$12
	private static short readImmediateValue(CPU cpu, IMemoryRegion memory) {
		cpu.pc++; // skip opcode
		return memory.readByte( cpu.pc++ ); // accu := mem[ cpu.pc + 1 ]
	}

	// LDA $12
	private static short readZeroPageValue(CPU cpu,IMemoryRegion memory)
	{
		cpu.pc++; // skip opcode
		return memory.readByte( memory.readByte( cpu.pc++ ) ); // accu := mem[ zp_adr ]
	}

	// LDA $12 , X
	private static short readAbsoluteZeroPageXValue(CPU cpu,IMemoryRegion memory)
	{
		cpu.pc++; // skip opcode
		final byte adr = (byte) (memory.readByte( cpu.pc++ ) + cpu.x);
		return memory.readByte( adr  ); // accu:= mem[ zp_adr + x ]
	}

	private static short readAbsoluteZeroPageYValue(CPU cpu,IMemoryRegion memory)
	{
		cpu.pc++; // skip opcode
		final byte adr = (byte) (memory.readByte( cpu.pc++ ) + cpu.y);
		return memory.readByte( adr  ); // accu:= mem[ zp_adr + x ]
	}

	// LDA $1234
	private static short readAbsoluteValue(CPU cpu,IMemoryRegion memory) {
		cpu.pc++; // skip opcode
		final short result = memory.readByte( memory.readWord( cpu.pc )  ); // accu:= mem[ adr  ]
		cpu.pc += 2;
		return result;
	}

	// == store ==

	private static void writeIndirectIndexedY(short accumulator, CPU cpu, IMemoryRegion memory) {
		cpu.pc ++;
		final short adr1111 = memory.readByte( cpu.pc ); // zp offset
		cpu.pc++;
		final short adr2222 = memory.readWord( adr1111 );
		final short adr3333 = (short) (adr2222 + cpu.y);
		memory.writeByte( adr3333 ,(byte) accumulator );
	}

	private static void writeIndexedIndirectX(short value, CPU cpu, IMemoryRegion memory)
	{
		cpu.pc ++;
		final short adr111 = memory.readByte( cpu.pc ); // zp offset
		cpu.pc++;
		final short adr222 = (short) (adr111 + cpu.x); // zp + offset
		final short adr333 = memory.readWord( adr222 );
		memory.writeByte( adr333 , (byte) value );
	}

	private static void writeAbsoluteYValue(short value , CPU cpu, IMemoryRegion memory) {
		cpu.pc++;
		final short adr11 = memory.readWord( cpu.pc );
		cpu.pc += 2;
		final short adr22 = (short) (adr11 + cpu.y);
		memory.writeByte( adr22  , (byte) value ); // accu:= mem[ zp_adr + x ]
	}

	private static void writeAbsoluteXValue(short value, CPU cpu, IMemoryRegion memory) {
		cpu.pc++;
		final short adr1 = memory.readWord( cpu.pc );
		cpu.pc += 2;
		final short adr2 = (short) (adr1 + cpu.x);
		memory.writeByte( adr2  , (byte) value ); // accu:= mem[ zp_adr + x ]
	}

	private static void writeAbsoluteValue(short value, CPU cpu, IMemoryRegion memory)
	{
		cpu.pc++; // skip opcode
		memory.writeByte( memory.readWord( cpu.pc ) , (byte) value ); // accu:= mem[ adr  ]
		cpu.pc += 2;
	}

	private static void writeAbsoluteZeroPageXValue(short value, CPU cpu, IMemoryRegion memory) {
		cpu.pc++;
		final byte adr = (byte) (memory.readByte( cpu.pc++ ) + cpu.x);
		memory.writeByte( adr , (byte) value ); // accu:= mem[ zp_adr + x ]
	}

	private static void writeAbsoluteZeroPageYValue(short value, CPU cpu, IMemoryRegion memory) {
		cpu.pc++;
		final byte adr = (byte) (memory.readByte( cpu.pc++ ) + cpu.y);
		memory.writeByte( adr , (byte) value ); // accu:= mem[ zp_adr + x ]
	}

	private static void writeZeroPage(short value,CPU cpu,IMemoryRegion memory)
	{
		cpu.pc++;
		final short adr = memory.readByte( cpu.pc++ );
		memory.writeByte( adr , (byte) value );
	}

	private static void handleStackInstruction(CPU cpu,IMemoryRegion memory)
	{
		final int opcode = memory.readByte(cpu.pc);
		/*
TSX (Transfer Stack pointer to X) is one of the Register transfer operations in 6502 instruction-set.
    TSX operation transfers the content of the Stack Pointer to Index Register X and sets the zero and negative flags as appropriate.
		 */
		switch( opcode & 0xff )
		{
			case 0x9A: // TXS (Transfer X to Stack ptr)   $9A  2
				cpu.pc++;
				cpu.sp = cpu.x;
				cpu.sp &= 0xff;
				cpu.cycles += 2;
				break;
			case 0xBA: // TSX (Transfer Stack ptr to X)   $BA  2
				cpu.pc++;
				cpu.x = (byte) cpu.sp;
				cpu.x &= 0xff;
				cpu.cycles += 2;
				updateZeroSignedFromX(cpu);
				break;
			case 0x48: // PHA (PusH Accumulator)          $48  3
				push( cpu.accumulator , cpu , memory );
				cpu.pc++;
				cpu.cycles += 3;
				break;
			case 0x68: // PLA (PuLl Accumulator)          $68  4
				cpu.accumulator = pop( cpu , memory );
				updateZeroSignedFromAccumulator(cpu);
				cpu.pc++;
				cpu.cycles += 4;
				break;
			case 0x08: // PHP (PusH Processor status)     $08  3
				push( cpu.flags , cpu , memory );
				cpu.pc++;
				cpu.cycles += 3;
				break;
			case 0x28: // PLP (PuLl Processor status)     $28  4
				cpu.flags = pop( cpu , memory );
				cpu.pc++;
				cpu.cycles += 4;
				break;
			default:
				throw new RuntimeException("Unreachable code reached");
		}
	}

	private static void handleProcessorStatus(CPU cpu,IMemoryRegion memory) {
/*
 *  These instructions are implied mode, have a length of one byte and require two machine cycles.
 */
		final int op = memory.readByte( cpu.pc );
		switch( op & 0xff )
		{
			case 0x18: // CLC (CLear Carry)              $18
				cpu.clearFlag( CPU.Flag.CARRY );
				break;
			case 0x38: // SEC (SEt Carry)                $38
				cpu.setFlag( CPU.Flag.CARRY );
				break;
			case 0x58: // CLI (CLear Interrupt)          $58
				cpu.clearFlag( CPU.Flag.IRQ_DISABLE );
				break;
			case 0x78: // SEI (SEt Interrupt)            $78
				cpu.setFlag( CPU.Flag.IRQ_DISABLE );
				break;
			case 0xB8: // CLV (CLear oVerflow)           $B8
				cpu.clearFlag( CPU.Flag.OVERFLOW);
				break;
			case 0xD8: // CLD (CLear Decimal)            $D8
				cpu.clearFlag( CPU.Flag.DECIMAL_MODE);
				break;
			case 0xF8: // SED (SEt Decimal)              $F8
				cpu.setFlag( CPU.Flag.DECIMAL_MODE);
				break;
			default:
				throw new RuntimeException("Unreachable code reached");
		}
		cpu.pc++;
		cpu.cycles += 2;
	}

	private static void handleBranch(CPU cpu,IMemoryRegion memory) {
		/*
		 * When the 6502 is ready for the next instruction it increments the program counter before fetching the instruction.
		 * Once it has the op code, it increments the program counter by the length of the operand, if any.
		 * This must be accounted for when calculating branches or when pushing bytes to
		 * create a false return address (i.e. jump table addresses are made up of addresses-1 when it
		 * is intended to use an RTS rather than a JMP).
		 *
		 * A branch not taken requires two machine cycles. Add one if the branch is taken and
		 * add one more if the branch crosses a page boundary.
		 *
		 * The program counter is loaded least signifigant byte first. Therefore the most signifigant byte must be pushed
		 * first when creating a false return address.
		 *
		 * When calculating branches a forward branch of 6 skips the following 6 bytes so,
		 * effectively the program counter points to the address that is 8 bytes beyond the address of
		 *  the branch opcode; and a backward branch of $FA (256-6) goes to an address 7 bytes before the branch instruction.
		 */
		final int adr1 = cpu.pc;
		int op = memory.readByte( cpu.pc++ );
		op = op & 0xff;
		final int offset = memory.readByte( cpu.pc++ );
		final boolean takeBranch;
		switch( op )
		{
			case 0x10: takeBranch = cpu.isCleared(CPU.Flag.NEGATIVE); break;// BPL (Branch on PLus)
			case 0x30: takeBranch = cpu.isSet(CPU.Flag.NEGATIVE); break;// BMI (Branch on MInus)
			case 0x50: takeBranch = cpu.isCleared(CPU.Flag.OVERFLOW); break;// BVC (Branch on oVerflow Clear)
			case 0x70: takeBranch = cpu.isSet(CPU.Flag.OVERFLOW); break;// BVS (Branch on oVerflow Set)
			case 0x90: takeBranch = cpu.isCleared(CPU.Flag.CARRY); break;// BCC (Branch on Carry Clear)
			case 0xB0: takeBranch = cpu.isSet(CPU.Flag.CARRY); break;// BCS (Branch on Carry Set)
			case 0xD0: takeBranch = cpu.isCleared(CPU.Flag.ZERO); break;// BNE (Branch on Not Equal)
			case 0xF0: takeBranch = cpu.isSet(CPU.Flag.ZERO); break;// BEQ (Branch on EQual)
			default:
				throw new RuntimeException("Unreachable code reached");
		}
		if ( takeBranch )
		{
			final int adr2 = adr1 + offset;
			cpu.pc += (short) offset;
			cpu.cycles += (3 + (isAcrossPageBoundary( adr1 , adr2 ) ? 1 : 0) );
		} else {
			cpu.cycles += 2;
		}
	}

	private static void handleRegisterInstructions(CPU cpu,IMemoryRegion memory)
	{
		final int op = memory.readByte( cpu.pc );

		// These instructions are implied mode, have a length of one byte and require two machine cycles.

		switch( (op & 0xff ) )
		{
			case 0xAA: // TAX (Transfer A to X)    $AA
				cpu.x = (byte) cpu.accumulator;
				cpu.setFlag( CPU.Flag.ZERO , cpu.x == 0 );
				cpu.setFlag( CPU.Flag.NEGATIVE, (cpu.x & 0b10000000) != 0 );
				break;
			case 0x8A: // TXA (Transfer X to A)    $8A
				cpu.accumulator = cpu.x;
				cpu.setFlag( CPU.Flag.ZERO , cpu.accumulator == 0 );
				cpu.setFlag( CPU.Flag.NEGATIVE, (cpu.accumulator & 0b10000000) != 0 );
				break;
			case 0xCA: // DEX (DEcrement X)        $CA
				// Subtracts one from Register X and setting the zero and negative flag accordingly
				cpu.x--;
				cpu.setFlag( CPU.Flag.ZERO , cpu.x == 0 );
				cpu.setFlag( CPU.Flag.NEGATIVE, (cpu.x & 0b10000000) != 0 );
				break;
			case 0xE8: // INX (INcrement X)        $E8
				cpu.x++;
				cpu.setFlag( CPU.Flag.ZERO , cpu.x == 0 );
				cpu.setFlag( CPU.Flag.NEGATIVE, (cpu.x & 0b10000000) != 0 );
				break;
			case 0xA8: // TAY (Transfer A to Y)    $A8
				cpu.y = (byte) cpu.accumulator;
				cpu.setFlag( CPU.Flag.ZERO , cpu.y == 0 );
				cpu.setFlag( CPU.Flag.NEGATIVE, (cpu.y & 0b10000000) != 0 );
				break;
			case 0x98: // TYA (Transfer Y to A)    $98
				cpu.accumulator = cpu.y;
				cpu.setFlag( CPU.Flag.ZERO , cpu.accumulator == 0 );
				cpu.setFlag( CPU.Flag.NEGATIVE, (cpu.accumulator & 0b10000000) != 0 );
				break;
			case 0x88: // DEY (DEcrement Y)        $88
				cpu.y--;
				cpu.setFlag( CPU.Flag.ZERO , cpu.y == 0 );
				cpu.setFlag( CPU.Flag.NEGATIVE, (cpu.y & 0b10000000) != 0 );
				break;
			case 0xC8: // INY (INcrement Y)        $C8
				cpu.y++;
				cpu.setFlag( CPU.Flag.ZERO , cpu.y == 0 );
				cpu.setFlag( CPU.Flag.NEGATIVE, (cpu.y & 0b10000000) != 0 );
				break;
			default:
				throw new RuntimeException("Unreachable code reached");
		}
		cpu.pc++;
		cpu.cycles += 2;
	}

	private static void push(short value,CPU cpu,IMemoryRegion region)
	{
		final byte hi = (byte) ((value >> 8) & 0xff);
		final byte lo = (byte) (value & 0xff);
		push( hi , cpu , region );
		push( lo , cpu , region );
	}

	private static void push(byte value,CPU cpu,IMemoryRegion region)
	{
		cpu.sp--;
		region.writeByte( cpu.sp , value );
	}

	private static byte pop(CPU cpu,IMemoryRegion region) {
		return region.readByte( cpu.sp++ );
	}
}