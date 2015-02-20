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
import de.codesourcery.j6502.emulator.CPU.Flag;
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

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator)
		{
			byte value;
			switch(opcode) {
				case 0x09: // Immediate     ORA #$44      $29  2   2
					value = readImmediateValue( cpu , memory );
					cpu.cycles += 2;
					break;
				case 0x05: // Zero Page     ORA $44       $25  2   2
					value = readZeroPageValue(cpu, memory);
					cpu.cycles += 2;
					break;
				case 0x15: // Zero Page,X   ORA $44,X     $35  2   3
					value = readAbsoluteZeroPageXValue(cpu, memory); // memory.readByte( (short) (cpu.pc+1) );
					cpu.cycles += 3;
					break;
				case 0x0D: // Absolute      ORA $4400     $2D  3   4
					value = readAbsoluteValue(cpu, memory);
					cpu.cycles += 4;
					break;
				case 0x1D: // Absolute,X    ORA $4400,X   $3D  3   4+
					value = readAbsoluteXValue( cpu , memory , 4 );
					break;
				case 0x19: // Absolute,Y    ORA $4400,Y   $39  3   4+
					value = readAbsoluteYValue( cpu , memory , 4 );
					break;
				case 0x01: // Indirect,X    ORA ($44,X)   $21  2   6
					value = readIndexedIndirectX(cpu,memory);
					cpu.cycles += 6;
					break;
				case 0x11: // Indirect,Y    ORA ($44),Y   $31  2   5+
					value = readIndirectIndexedY( cpu , memory , 5 );
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}

			final byte result = (byte) (cpu.accumulator | value);
			cpu.accumulator = result;

			updateZeroSigned( result , cpu );
		}
	},
	AND("AND")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b001 ); }

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator) {

			byte value;
			switch(opcode) {
				case 0x29: // Immediate     AND #$44      $29  2   2
					value = readImmediateValue( cpu , memory );
					cpu.cycles += 2;
					break;
				case 0x25: // Zero Page     AND $44       $25  2   2
					value = readZeroPageValue(cpu, memory);
					cpu.cycles += 2;
					break;
				case 0x35: // Zero Page,X   AND $44,X     $35  2   3
					value = readAbsoluteZeroPageXValue(cpu, memory); // memory.readByte( (short) (cpu.pc+1) );
					cpu.cycles += 3;
					break;
				case 0x2D: // Absolute      AND $4400     $2D  3   4
					value = readAbsoluteValue(cpu, memory);
					cpu.cycles += 4;
					break;
				case 0x3D: // Absolute,X    AND $4400,X   $3D  3   4+
					value = readAbsoluteXValue( cpu , memory , 4 );
					break;
				case 0x39: // Absolute,Y    AND $4400,Y   $39  3   4+
					value = readAbsoluteYValue( cpu , memory , 4 );
					break;
				case 0x21: // Indirect,X    AND ($44,X)   $21  2   6
					value = readIndexedIndirectX(cpu,memory);
					cpu.cycles += 6;
					break;
				case 0x31: // Indirect,Y    AND ($44),Y   $31  2   5+
					value = readIndirectIndexedY( cpu , memory , 5 );
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}

			final byte result = (byte) (cpu.accumulator & value);
			cpu.accumulator = result;

			updateZeroSigned( result , cpu );
		}
	},
	EOR("EOR")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b010 ); }
		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator)
		{
			/*
 Affects Flags: S Z

MODE           SYNTAX       HEX LEN TIM
Immediate     EOR #$44      $49  2   2
Zero Page     EOR $44       $45  2   3
Zero Page,X   EOR $44,X     $55  2   4
Absolute      EOR $4400     $4D  3   4
Absolute,X    EOR $4400,X   $5D  3   4+
Absolute,Y    EOR $4400,Y   $59  3   4+
Indirect,X    EOR ($44,X)   $41  2   6
Indirect,Y    EOR ($44),Y   $51  2   5+

+ add 1 cycle if page boundary crossed
			 */
			byte value;
			switch(opcode) {
				case 0x49: // Immediate     AND #$44      $29  2   2
					value = readImmediateValue( cpu , memory );
					cpu.cycles += 2;
					break;
				case 0x45: // Zero Page     AND $44       $25  2   2
					value = readZeroPageValue(cpu, memory);
					cpu.cycles += 3;
					break;
				case 0x55: // Zero Page,X   AND $44,X     $35  2   3
					value = readAbsoluteZeroPageXValue(cpu, memory); // memory.readByte( (short) (cpu.pc+1) );
					cpu.cycles += 4;
					break;
				case 0x4D: // Absolute      AND $4400     $2D  3   4
					value = readAbsoluteValue(cpu, memory);
					cpu.cycles += 4;
					break;
				case 0x5D: // Absolute,X    AND $4400,X   $3D  3   4+
					value = readAbsoluteXValue( cpu , memory , 4 );
					break;
				case 0x59: // Absolute,Y    AND $4400,Y   $39  3   4+
					value = readAbsoluteYValue( cpu , memory , 4 );
					break;
				case 0x41: // Indirect,X    AND ($44,X)   $21  2   6
					value = readIndexedIndirectX(cpu,memory);
					cpu.cycles += 6;
					break;
				case 0x51: // Indirect,Y    AND ($44),Y   $31  2   5+
					value = readIndirectIndexedY( cpu , memory , 5 );
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}

			final byte result = (byte) (cpu.accumulator ^ value);
			cpu.accumulator = result;

			updateZeroSigned( result , cpu );
		}
	},
	ADC("ADC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b011 ); }

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator) {
			/*
 Affects Flags: S V Z C

+ add 1 cycle if page boundary crossed

ADC results are dependant on the setting of the decimal flag. In decimal mode, addition is carried out on the assumption that the values involved are packed BCD (Binary Coded Decimal).

There is no way to add without carry. Return To Index
			 */
			int b;
			switch( opcode )
			{
				case 0x69: // Immediate     ADC #$44      $69  2   2
					b = readImmediateValue(cpu,memory);
					cpu.cycles += 3;
					break;
				case 0x65: // Zero Page     ADC $44       $65  2   3
					b = readZeroPageValue( cpu , memory );
					cpu.cycles += 3;
					break;
				case 0x75: // Zero Page,X   ADC $44,X     $75  2   4
					b = readAbsoluteZeroPageXValue( cpu , memory );
					cpu.cycles +=4;
					break;
				case 0x6D: // Absolute      ADC $4400     $6D  3   4
					b = readAbsoluteValue( cpu , memory );
					cpu.cycles +=4;
					break;
				case 0x7D: // Absolute,X    ADC $4400,X   $7D  3   4+
					b = readAbsoluteXValue( cpu , memory , 4 );
					break;
				case 0x79: // Absolute,Y    ADC $4400,Y   $79  3   4+
					b = readAbsoluteYValue( cpu , memory , 4 );
					break;
				case 0x61: // Indirect,X    ADC ($44,X)   $61  2   6
					b = readIndexedIndirectX( cpu , memory );
					cpu.cycles += 6;
					break;
				case 0x71: // Indirect,Y    ADC ($44),Y   $71  2   5+
					b = readIndirectIndexedY( cpu , memory , 5 );
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
			b &= 0xff;

			/*
  t = A + M + P.C
  P.V = (A.7!=t.7) ? 1:0
  P.N = A.7
  P.Z = (t==0) ? 1:0
  IF (P.D)
    t = bcd(A) + bcd(M) + P.C
    P.C = (t>99) ? 1:0
  ELSE
    P.C = (t>255) ? 1:0
  A = t & 0xFF
			 */
			final int a = (cpu.accumulator & 0xff );
			final int result = a + b + ( cpu.isSet(Flag.CARRY) ? 1 : 0 );

			cpu.setFlag(CPU.Flag.OVERFLOW , ( a & 0b1000_0000) != ( result & 0b1000_0000) );
			cpu.setFlag( CPU.Flag.NEGATIVE , ( result & 0b1000_0000) != 0 );
			cpu.setFlag( CPU.Flag.ZERO , ( result & 0xff) == 0 );
			// FIXME: Handle BCD mode
			if ( cpu.isSet( Flag.DECIMAL_MODE ) ) {
				throw new RuntimeException("ADC with BCD currently not implemented");
			}
			cpu.setFlag( Flag.CARRY , result >255 );
			cpu.accumulator = (byte) result;
		}
	},
	CMP("CMP")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b110 ); }

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator) {
			/*
 Affects Flags: S Z C

Logic:
  t = A - M
  P.N = t.7
  P.C = (A>=M) ? 1:0
  P.Z = (t==0) ? 1:0

MODE           SYNTAX       HEX LEN TIM

+ add 1 cycle if page boundary crossed

Compare sets flags as if a subtraction had been carried out.
If the value in the accumulator is equal or greater than the compared value, the Carry will be set.
The equal (Z) and sign (S) flags will be set based on equality or
lack thereof and the sign (i.e. A>=$80) of the accumulator.
			 */

			int b;
			switch ( opcode )
			{
				case 0xC9: // Immediate     CMP #$44      $C9  2   2
					b = readImmediateValue( cpu , memory );
					cpu.cycles += 4;
					break;
				case 0xC5: // Zero Page     CMP $44       $C5  2   3
					b = readZeroPageValue(cpu , memory );
					cpu.cycles += 4;
					break;
				case 0xD5: // Zero Page,X   CMP $44,X     $D5  2   4
					b = readAbsoluteZeroPageXValue(cpu, memory );
					cpu.cycles += 4;
					break;
				case 0xCD: // Absolute      CMP $4400     $CD  3   4
					b = readAbsoluteValue( cpu , memory );
					cpu.cycles += 4;
					break;
				case 0xDD: // Absolute,X    CMP $4400,X   $DD  3   4+
					b = readAbsoluteXValue(cpu,memory,4);
					break;
				case 0xD9: // Absolute,Y    CMP $4400,Y   $D9  3   4+
					b = readAbsoluteYValue(cpu,memory,4);
					break;
				case 0xC1: // Indirect,X    CMP ($44,X)   $C1  2   6
					b = readIndexedIndirectX( cpu , memory );
					cpu.cycles += 6;
					break;
				case 0xD1: // Indirect,Y    CMP ($44),Y   $D1  2   5+
					b = readIndirectIndexedY(cpu , memory , 5 );
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
			final int a = (cpu.accumulator & 0xff);
			b &= 0xff;

			cpu.setFlag( Flag.CARRY , a >= b );
			cpu.setFlag( Flag.ZERO , a == b );
			cpu.setFlag( Flag.NEGATIVE , ( (a-b) & 0xffffff00 ) != 0 );
		}
	},
	SBC("SBC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b111 ); }

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator) {
			/*
MODE           SYNTAX       HEX LEN TIM
Immediate     SBC #$44      $E9  2   2
Zero Page     SBC $44       $E5  2   3
Zero Page,X   SBC $44,X     $F5  2   4
Absolute      SBC $4400     $ED  3   4
Absolute,X    SBC $4400,X   $FD  3   4+
Absolute,Y    SBC $4400,Y   $F9  3   4+
Indirect,X    SBC ($44,X)   $E1  2   6
Indirect,Y    SBC ($44),Y   $F1  2   5+

SBC results are dependant on the setting of the decimal flag.
In decimal mode, subtraction is carried out on the assumption that the values involved are packed BCD (Binary Coded Decimal).
There is no way to subtract without the carry which works as an inverse borrow. i.e, to subtract you set the carry before the
operation. If the carry is cleared by the operation, it indicates a borrow occurred.
			 */
			int b;
			switch( opcode )
			{
				case 0xE9: // Immediate     ADC #$44      $69  2   2
					b = readImmediateValue(cpu,memory);
					cpu.cycles += 3;
					break;
				case 0xE5: // Zero Page     ADC $44       $65  2   3
					b = readZeroPageValue( cpu , memory );
					cpu.cycles += 3;
					break;
				case 0xF5: // Zero Page,X   ADC $44,X     $75  2   4
					b = readAbsoluteZeroPageXValue( cpu , memory );
					cpu.cycles +=4;
					break;
				case 0xED: // Absolute      ADC $4400     $6D  3   4
					b = readAbsoluteValue( cpu , memory );
					cpu.cycles +=4;
					break;
				case 0xFD: // Absolute,X    ADC $4400,X   $7D  3   4+
					b = readAbsoluteXValue( cpu , memory , 4 );
					break;
				case 0xF9: // Absolute,Y    ADC $4400,Y   $79  3   4+
					b = readAbsoluteYValue( cpu , memory , 4 );
					break;
				case 0xE1: // Indirect,X    ADC ($44,X)   $61  2   6
					b = readIndexedIndirectX( cpu , memory );
					cpu.cycles += 6;
					break;
				case 0xF1: // Indirect,Y    ADC ($44),Y   $71  2   5+
					b = readIndirectIndexedY( cpu , memory , 5 );
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
			b &= 0xff;

			/*
  IF (P.D)
    t = bcd(A) - bcd(M) - !P.C
    P.V = (t>99 OR t<0) ? 1:0
  ELSE
    t = A - M - !P.C
    P.V = (t>127 OR t<-128) ? 1:0
  P.C = (t>=0) ? 1:0
  P.N = t.7
  P.Z = (t==0) ? 1:0
  A = t & 0xFF
			 */
			final int a = (cpu.accumulator & 0xff );
			final int result = a - b - ( cpu.isCleared(Flag.CARRY) ? 1 : 0 );

			// FIXME: Handle BCD mode
			if ( cpu.isSet( Flag.DECIMAL_MODE ) ) {
				throw new RuntimeException("SBC with BCD currently not implemented");
			}
			// (t>127 OR t<-128) ? 1:0
			cpu.setFlag(CPU.Flag.OVERFLOW , (result > 127) || (result < -128) );
			cpu.setFlag( Flag.CARRY , ( result & ~0b0111_1111) == 0 );
			cpu.setFlag( CPU.Flag.NEGATIVE , ( result & 0b1000_0000) != 0 );
			cpu.setFlag( CPU.Flag.ZERO , ( result & 0xff) == 0 );
			cpu.accumulator = (byte) result;
		}
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

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator) {
			/*
 Affects Flags: S Z C

MODE           SYNTAX       HEX LEN TIM
Accumulator   ASL A         $0A  1   2
Zero Page     ASL $44       $06  2   5
Zero Page,X   ASL $44,X     $16  2   6
Absolute      ASL $4400     $0E  3   6
Absolute,X    ASL $4400,X   $1E  3   7

ASL shifts all bits left one position. 0 is shifted into bit 0 and the original bit 7 is shifted into the Carry.
			 */
			short adr;
			int result;
			switch(opcode)
			{
				case 0x0A: // Accumulator   ROL A         $2A  1   2
					result = ((cpu.accumulator & 0xff) << 1);
					cpu.accumulator = (byte) result;
					cpu.pc++;
					cpu.cycles += 2;
					cpu.setFlag(Flag.ZERO , ( result & 0xff) == 0 );
					cpu.setFlag(Flag.NEGATIVE , (result & 0b10000000) != 0 );
					cpu.setFlag(Flag.CARRY , (result & 0b100000000) != 0 );
					return; /* RETURN ! */
				case 0x06: // Zero Page     ROL $44       $26  2   5
					adr = (short) ( memory.readByte( (short) (cpu.pc+1) ) & 0xff );
					cpu.pc+=2;
					result = ((memory.readByte( adr ) & 0xff) << 1);
					cpu.cycles += 5;
					break;
				case 0x16: // Zero Page,X   ROL $44,X     $36  2   6
					adr = memory.readByte( (short) (cpu.pc+1) );
					cpu.pc+=2;
					adr += cpu.x;
					adr &= 0xff;
					result = ((memory.readByte( adr ) & 0xff) << 1);
					cpu.cycles += 6;
					break;
				case 0x0E: // Absolute      ROL $4400     $2E  3   6
					adr = memory.readWord( (short) (cpu.pc+1) );
					cpu.pc+=3;
					result = ((memory.readByte( adr ) & 0xff) << 1);
					cpu.cycles += 6;
					break;
				case 0x1E: // Absolute,X    ROL $4400,X   $3E  3   7
					adr = memory.readWord( (short) (cpu.pc+1) );
					cpu.pc += 3;
					adr += cpu.x;
					result = ((memory.readByte( adr ) & 0xff) << 1);
					cpu.cycles += 7;
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
			memory.writeByte( adr , (byte) result );
			cpu.setFlag(Flag.ZERO , (result & 0xff) == 0 );
			cpu.setFlag(Flag.NEGATIVE , (result & 0b10000000) != 0 );
			cpu.setFlag(Flag.CARRY , (result & 0b100000000) != 0 );
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

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator) {
			/*
 Affects Flags: S Z C

ROL shifts all bits left one position. The Carry is shifted into bit 0 and the original bit 7 is shifted into the Carry.

  B = (B << 1) & $FE
  B = B | P.C
			 */
			short adr;
			final int carryMask = cpu.isSet( CPU.Flag.CARRY ) ? 1 : 0;
			int result;
			switch(opcode)
			{
				case 0x2A: // Accumulator   ROL A         $2A  1   2
					result = ((cpu.accumulator & 0xff) << 1) | carryMask ;
					cpu.accumulator = (byte) result;
					cpu.pc++;
					cpu.cycles += 2;
					cpu.setFlag(Flag.ZERO , ( result & 0xff) == 0 );
					cpu.setFlag(Flag.NEGATIVE , (result & 0b10000000) != 0 );
					cpu.setFlag(Flag.CARRY , (result & 0b100000000) != 0 );
					return; /* RETURN ! */
				case 0x26: // Zero Page     ROL $44       $26  2   5
					adr = (short) ( memory.readByte( (short) (cpu.pc+1) ) & 0xff );
					cpu.pc+=2;
					result = ((memory.readByte( adr ) & 0xff) << 1) | carryMask;
					cpu.cycles += 5;
					break;
				case 0x36: // Zero Page,X   ROL $44,X     $36  2   6
					adr = memory.readByte( (short) (cpu.pc+1) );
					cpu.pc+=2;
					adr += cpu.x;
					adr &= 0xff;
					result = ((memory.readByte( adr ) & 0xff) << 1) | carryMask;
					cpu.cycles += 6;
					break;
				case 0x2E: // Absolute      ROL $4400     $2E  3   6
					adr = memory.readWord( (short) (cpu.pc+1) );
					cpu.pc+=3;
					result = ((memory.readByte( adr ) & 0xff) << 1) | carryMask;
					cpu.cycles += 6;
					break;
				case 0x3E: // Absolute,X    ROL $4400,X   $3E  3   7
					adr = memory.readWord( (short) (cpu.pc+1) );
					cpu.pc += 3;
					adr += cpu.x;
					result = ((memory.readByte( adr ) & 0xff) << 1) | carryMask;
					cpu.cycles += 7;
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
			memory.writeByte( adr , (byte) result );
			cpu.setFlag(Flag.ZERO , (result & 0xff) == 0 );
			cpu.setFlag(Flag.NEGATIVE , (result & 0b10000000) != 0 );
			cpu.setFlag(Flag.CARRY , (result & 0b100000000) != 0 );
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

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator)
		{
			/*
			Affects Flags: S Z C

			MODE           SYNTAX       HEX LEN TIM
			Accumulator   LSR A         $4A  1   2
			Zero Page     LSR $44       $46  2   5
			Zero Page,X   LSR $44,X     $56  2   6
			Absolute      LSR $4400     $4E  3   6
			Absolute,X    LSR $4400,X   $5E  3   7

			LSR shifts all bits right one position. 0 is shifted into bit 7 and the original bit 0 is shifted into the Carry.
			 */


			final int origValue;
			short adr;
			final int result;
			switch(opcode)
			{
				case 0x4A: // Accumulator   ROR A         $2A  1   2
					origValue = cpu.accumulator & 0xff;
					result = ( origValue >>> 1);
					cpu.accumulator = (byte) result;
					cpu.pc++;
					cpu.cycles += 2;
					cpu.setFlag(Flag.ZERO , ( result & 0xff) == 0 );
					cpu.clearFlag(Flag.NEGATIVE);
					cpu.setFlag(Flag.CARRY , (origValue & 0x01) != 0);
					return; /* RETURN ! */
				case 0x46: // Zero Page     ROR $44       $26  2   5
					adr = (short) ( memory.readByte( (short) (cpu.pc+1) ) & 0xff );
					cpu.pc+=2;
					origValue = (memory.readByte( adr ) & 0xff);
					result = (origValue >>> 1);
					cpu.cycles += 5;
					break;
				case 0x56: // Zero Page,X   ROR $44,X     $36  2   6
					adr = memory.readByte( (short) (cpu.pc+1) );
					cpu.pc+=2;
					adr += cpu.x;
					adr &= 0xff;
					origValue = (memory.readByte( adr ) & 0xff);
					result = (origValue >>> 1);
					cpu.cycles += 6;
					break;
				case 0x4E: // Absolute      ROR $4400     $2E  3   6
					adr = memory.readWord( (short) (cpu.pc+1) );
					cpu.pc+=3;
					origValue = memory.readByte( adr ) & 0xff;
					result = (origValue >>> 1);
					cpu.cycles += 6;
					break;
				case 0x5E: // Absolute,X    ROR $4400,X   $3E  3   7
					adr = memory.readWord( (short) (cpu.pc+1) );
					cpu.pc += 3;
					adr += cpu.x;
					origValue = (memory.readByte( adr ) & 0xff);
					result = (origValue >>> 1);
					cpu.cycles += 7;
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
			memory.writeByte( adr , (byte) result );
			cpu.setFlag(Flag.ZERO , (result & 0xff) == 0 );
			cpu.clearFlag(Flag.NEGATIVE);
			cpu.setFlag(Flag.CARRY , (origValue & 0x01) != 0);
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

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator)
		{

			/*
 Affects Flags: S Z C

MODE           SYNTAX       HEX LEN TIM
Accumulator   ROR A         $6A  1   2
Zero Page     ROR $44       $66  2   5
Zero Page,X   ROR $44,X     $76  2   6
Absolute      ROR $4400     $6E  3   6
Absolute,X    ROR $4400,X   $7E  3   7

ROR shifts all bits right one position. The Carry is shifted into bit 7 and the original bit 0 is shifted into the Carry.
			 */
			final int origValue;
			short adr;
			final int carryMask = cpu.isSet( CPU.Flag.CARRY ) ? 0b1000_0000 : 0;
			final int result;
			switch(opcode)
			{
				case 0x6A: // Accumulator   ROR A         $2A  1   2
					origValue = cpu.accumulator & 0xff;
					result = ( origValue >> 1) | carryMask ;
					cpu.accumulator = (byte) result;
					cpu.pc++;
					cpu.cycles += 2;
					cpu.setFlag(Flag.ZERO , ( result & 0xff) == 0 );
					cpu.setFlag(Flag.NEGATIVE , (result & 0b10000000) != 0 );
					cpu.setFlag(Flag.CARRY , (origValue & 0x01) != 0);
					return; /* RETURN ! */
				case 0x66: // Zero Page     ROR $44       $26  2   5
					adr = (short) ( memory.readByte( (short) (cpu.pc+1) ) & 0xff );
					cpu.pc+=2;
					origValue = (memory.readByte( adr ) & 0xff);
					result = (origValue << 1) | carryMask;
					cpu.cycles += 5;
					break;
				case 0x76: // Zero Page,X   ROR $44,X     $36  2   6
					adr = memory.readByte( (short) (cpu.pc+1) );
					cpu.pc+=2;
					adr += cpu.x;
					adr &= 0xff;
					origValue = (memory.readByte( adr ) & 0xff);
					result = (origValue << 1) | carryMask;
					cpu.cycles += 6;
					break;
				case 0x6E: // Absolute      ROR $4400     $2E  3   6
					adr = memory.readWord( (short) (cpu.pc+1) );
					cpu.pc+=3;
					origValue = memory.readByte( adr ) & 0xff;
					result = (origValue << 1) | carryMask;
					cpu.cycles += 6;
					break;
				case 0x7E: // Absolute,X    ROR $4400,X   $3E  3   7
					adr = memory.readWord( (short) (cpu.pc+1) );
					cpu.pc += 3;
					adr += cpu.x;
					origValue = (memory.readByte( adr ) & 0xff);
					result = (origValue << 1) | carryMask;
					cpu.cycles += 7;
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
			memory.writeByte( adr , (byte) result );
			cpu.setFlag(Flag.ZERO , (result & 0xff) == 0 );
			cpu.setFlag(Flag.NEGATIVE , (result & 0b10000000) != 0 );
			cpu.setFlag(Flag.CARRY , (origValue & 0x01) != 0);
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
					cpu.x = readImmediateValue(cpu, memory);
					cpu.cycles += 2;
					break;
				case 0xA6:    // Zero Page     LDX $44        2   3
					cpu.x = readZeroPageValue(cpu,memory);
					cpu.cycles += 3;
					break;
				case 0xB6:    // Zero Page,Y   LDX $44,Y      2   4
					cpu.x = readAbsoluteZeroPageYValue(cpu,memory);
					cpu.cycles += 4;
					break;
				case 0xAE: // Absolute      LDX $4400      3   4
					cpu.x = readAbsoluteValue(cpu,memory);
					cpu.cycles += 4;
					break;
				case 0xBE: // Absolute,Y    LDX $4400,Y    3   4+
					cpu.x = readAbsoluteYValue(cpu,memory,4);
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
		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator) { handleMemIncDec(opcode, cpu, memory , (byte) -1 ); }
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

		@Override public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator) { handleMemIncDec( opcode , cpu , memory , (byte) 1 ); }
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

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator) {
			/*
 Affects Flags: N V Z

MODE           SYNTAX       HEX LEN TIM

  t = A & M
  P.N = t.7
  P.V = t.6
  P.Z = (t==0) ? 1:0

BIT sets the Z flag as though the value in the address tested were ANDed with the accumulator. The S and V flags are set to match bits 7 and 6 respectively in the value stored at the tested address.
			 */
			short address;
			switch( opcode )
			{
				case 0x24: // Zero Page     BIT $44       $24  2   3
					address = memory.readByte( (short) (cpu.pc+1) );
					cpu.pc+=2;
					address &= 0xff;
					cpu.cycles += 3;
					break;
				case 0x2c: // Absolute      BIT $4400     $2C  3   4
					address = memory.readWord( (short) (cpu.pc+1) );
					cpu.pc += 3;
					cpu.cycles += 4;
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
			final byte result = (byte) (cpu.accumulator & memory.readByte( address ));
			cpu.setFlag( CPU.Flag.ZERO , result == 0);
			cpu.setFlag( CPU.Flag.NEGATIVE , (result & 0b10000000) != 0);
			cpu.setFlag( CPU.Flag.OVERFLOW , (result & 0b01000000) != 0);
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
					cpu.y = readImmediateValue(cpu, memory);
					cpu.cycles += 2;
					break;
				case 0xA4:    // Zero Page     LDX $44        2   3
					cpu.y = readZeroPageValue(cpu,memory);
					cpu.cycles += 3;
					break;
				case 0xB4:    // Zero Page,Y   LDX $44,X      2   4
					cpu.y = readAbsoluteZeroPageXValue(cpu,memory);
					cpu.cycles += 4;
					break;
				case 0xAC: // Absolute      LDX $4400      3   4
					cpu.y = readAbsoluteValue(cpu,memory);
					cpu.cycles += 4;
					break;
				case 0xBC: // Absolute,Y    LDY $4400,X    3   4+
					cpu.y = readAbsoluteXValue(cpu,memory,4);
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

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator) {
			/*
 Affects Flags: S Z C

MODE           SYNTAX       HEX LEN TIM
Immediate     CPY #$44      $C0  2   2
Zero Page     CPY $44       $C4  2   3
Absolute      CPY $4400     $CC  3   4
					 */
			int b;
			switch ( opcode )
			{
				case 0xC0: // Immediate     CMP #$44      $C9  2   2
					b = readImmediateValue( cpu , memory );
					cpu.cycles += 2;
					break;
				case 0xC4: // Zero Page     CMP $44       $C5  2   3
					b = readZeroPageValue(cpu , memory );
					cpu.cycles += 3;
					break;
				case 0xCC: // Absolute      CMP $4400     $CD  3   4
					b = readAbsoluteValue( cpu , memory );
					cpu.cycles += 4;
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
			final int a = (cpu.y & 0xff);
			b &= 0xff;

			cpu.setFlag( Flag.CARRY , a >= b );
			cpu.setFlag( Flag.ZERO , a == b );
			cpu.setFlag( Flag.NEGATIVE , ( (a-b) & 0xffffff00 ) != 0 );
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

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator) {
			/*
			 Affects Flags: S Z C

			MODE           SYNTAX       HEX LEN TIM
			Immediate     CPX #$44      $E0  2   2
			Zero Page     CPX $44       $E4  2   3
			Absolute      CPX $4400     $EC  3   4

			Operation and flag results are identical to equivalent mode accumulator CMP ops.
					 */

			int b;
			switch ( opcode )
			{
				case 0xE0: // Immediate     CMP #$44      $C9  2   2
					b = readImmediateValue( cpu , memory );
					cpu.cycles += 2;
					break;
				case 0xE4: // Zero Page     CMP $44       $C5  2   3
					b = readZeroPageValue(cpu , memory );
					cpu.cycles += 3;
					break;
				case 0xEC: // Absolute      CMP $4400     $CD  3   4
					b = readAbsoluteValue( cpu , memory );
					cpu.cycles += 4;
					break;
				default:
					throw new RuntimeException("Unreachable code reached");
			}
			final int a = (cpu.x & 0xff);
			b &= 0xff;

			cpu.setFlag( Flag.CARRY , a >= b );
			cpu.setFlag( Flag.ZERO , a == b );
			cpu.setFlag( Flag.NEGATIVE , ( (a-b) & 0xffffff00 ) != 0 );
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
		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator) {
			/*
  PC = PC + 1
  bPoke(SP,PC.h)
  SP = SP - 1
  bPoke(SP,PC.l)
  SP = SP - 1
  bPoke(SP, (P|$10) )
  SP = SP - 1
  l = bPeek($FFFE)
  h = bPeek($FFFF)<<8
  PC = h|l


    store PC(hi)
    store PC(lo)
    store P
    fetch PC(lo) from $FFFE
    fetch PC(hi) from $FFFF

			 */
			if ( opcode != 0 ) {
				throw new RuntimeException("Unreachable code reached");
			}
			cpu.pc++;
			push( (byte) ( ( cpu.pc & 0xff00) >>8 ) , cpu , memory ); // push pc hi
			push( (byte) ( cpu.pc & 0xff ) , cpu , memory ); // push pc lo
			push( CPU.Flag.BREAK.set( cpu.flags ) , cpu, memory ); // BRK bit is SET on flags pushed to stack
			cpu.pc = memory.readWord( (short) CPU.BRK_VECTOR_LOCATION );
			cpu.cycles += 7;
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
	RTI("RTI" , (byte) 0x40 ) {

		@Override
		public void execute(int opcode, CPU cpu, IMemoryRegion memory, IEmulator emulator) {
			if ( (opcode & 0xff) != 0x40 ) {
				throw new RuntimeException("Unreachable code reached");
			}
			cpu.flags = pop(cpu,memory);
			final byte lo = pop(cpu,memory);
			final byte hi = pop(cpu,memory);
			cpu.pc = (short) ( hi<<8 | lo );
			cpu.cycles += 6;
		}
	},
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
		return (value & 0xff00) == 0;
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

	private static void updateZeroSigned(byte value,CPU cpu)
	{
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
		return ( adr1 & 0x0000ff00) != ( adr2 & 0x0000ff00);
	}

	/* ================
	 * Addressing modes
	 * ================ */

	// LDA ( $12 ) , Y
	private static byte readIndirectIndexedY(CPU cpu, IMemoryRegion memory,int cycles)
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
	private static byte readIndexedIndirectX(CPU cpu, IMemoryRegion memory) {
		cpu.pc ++;
		final short adr111 = memory.readByte( cpu.pc ); // zp offset
		cpu.pc++;
		final short adr222 = (short) (adr111 + cpu.x); // zp + offset
		final short adr333 = memory.readWord( adr222 );
		return memory.readByte( adr333 );
	}

	// LDA $1234 , Y
	private static byte readAbsoluteYValue(CPU cpu, IMemoryRegion memory,int cycles)
	{
		cpu.pc++;
		final short adr11 = memory.readWord( cpu.pc );
		cpu.pc += 2;
		final short adr22 = (short) (adr11 + cpu.y);
		cpu.cycles += isAcrossPageBoundary( adr11 , adr22 ) ? cycles+1 : cycles;
		return memory.readByte( adr22  ); // accu:= mem[ zp_adr + x ]
	}

	// LDA $1234 , X
	private static byte readAbsoluteXValue(CPU cpu, IMemoryRegion memory,int cycles) {
		final short adr1 = memory.readWord( (short) (cpu.pc+1) );
		cpu.pc += 3;
		final short adr2 = (short) (adr1 + cpu.x);
		cpu.cycles += isAcrossPageBoundary( adr1 , adr2 ) ? cycles+1 : cycles;
		return memory.readByte( adr2  ); // accu:= mem[ zp_adr + x ]
	}

	// LDA #$12
	private static byte readImmediateValue(CPU cpu, IMemoryRegion memory)
	{
		final byte result = memory.readByte( (short) (cpu.pc+1) ); // accu := mem[ cpu.pc + 1 ]
		cpu.pc += 2;
		return result;
	}

	// LDA $12
	private static byte readZeroPageValue(CPU cpu,IMemoryRegion memory)
	{
		final byte adr = memory.readByte( (short) (cpu.pc+1) );
		final byte result = memory.readByte( (short) (adr & 0xff) ); // accu := mem[ zp_adr ]
		cpu.pc += 2;
		return result;
	}

	// LDA $12 , X
	private static byte readAbsoluteZeroPageXValue(CPU cpu,IMemoryRegion memory)
	{
		final byte adr = (byte) (memory.readByte( (short) (cpu.pc+1) ) + cpu.x);
		final byte result = memory.readByte( (short) (adr & 0xff)  ); // accu:= mem[ zp_adr + x ]
		cpu.pc += 2;
		return result;
	}

	private static byte readAbsoluteZeroPageYValue(CPU cpu,IMemoryRegion memory)
	{
		final byte adr = (byte) (memory.readByte( (short) (cpu.pc+1) ) + cpu.y);
		final byte result = memory.readByte( (short) (adr & 0xff)  ); // accu:= mem[ zp_adr + x ]
		cpu.pc += 2;
		return result;
	}

	// LDA $1234
	private static byte readAbsoluteValue(CPU cpu,IMemoryRegion memory) {
		final byte result = memory.readByte( memory.readWord( (short) (cpu.pc+1) )  ); // accu:= mem[ adr  ]
		cpu.pc += 3;
		return result;
	}

	// == store ==

	private static void writeIndirectIndexedY(short accumulator, CPU cpu, IMemoryRegion memory) {
		final short adr1111 = memory.readByte( (short) (cpu.pc+1) ); // zp offset
		cpu.pc+=2;
		final short adr2222 = memory.readWord( adr1111 );
		final short adr3333 = (short) (adr2222 + cpu.y);
		memory.writeByte( adr3333 ,(byte) accumulator );
	}

	private static void writeIndexedIndirectX(short value, CPU cpu, IMemoryRegion memory)
	{
		final short adr111 = memory.readByte( (short) (cpu.pc+1) ); // zp offset
		cpu.pc+=2;
		final short adr222 = (short) (adr111 + cpu.x); // zp + offset
		final short adr333 = memory.readWord( adr222 );
		memory.writeByte( adr333 , (byte) value );
	}

	private static void writeAbsoluteYValue(short value , CPU cpu, IMemoryRegion memory) {
		final short adr11 = memory.readWord( (short) (cpu.pc+1) );
		cpu.pc += 3;
		final short adr22 = (short) (adr11 + cpu.y);
		memory.writeByte( adr22  , (byte) value ); // accu:= mem[ zp_adr + x ]
	}

	private static void writeAbsoluteXValue(short value, CPU cpu, IMemoryRegion memory) {
		final short adr1 = memory.readWord( (short) (cpu.pc+1) );
		cpu.pc += 3;
		final short adr2 = (short) (adr1 + cpu.x);
		memory.writeByte( adr2  , (byte) value ); // accu:= mem[ zp_adr + x ]
	}

	private static void writeAbsoluteValue(short value, CPU cpu, IMemoryRegion memory)
	{
		memory.writeByte( memory.readWord( (short) (cpu.pc+1) ) , (byte) value ); // accu:= mem[ adr  ]
		cpu.pc += 3;
	}

	private static void writeAbsoluteZeroPageXValue(short value, CPU cpu, IMemoryRegion memory) {
		final byte adr = (byte) (memory.readByte( (short) (cpu.pc+1) ) + cpu.x);
		cpu.pc += 2;
		memory.writeByte( (short) (adr & 0xff) , (byte) value ); // accu:= mem[ zp_adr + x ]
	}

	private static void writeAbsoluteZeroPageYValue(short value, CPU cpu, IMemoryRegion memory) {
		final byte adr = (byte) (memory.readByte( (short) (cpu.pc+1) ) + cpu.y);
		cpu.pc += 2;
		memory.writeByte( (short) (adr & 0xff) , (byte) value ); // accu:= mem[ zp_adr + x ]
	}

	private static void writeZeroPage(short value,CPU cpu,IMemoryRegion memory)
	{
		final short adr = memory.readByte( (short) (cpu.pc+1) );
		cpu.pc += 2;
		memory.writeByte( (short) (adr & 0xff) , (byte) value );
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
				cpu.setSP( cpu.x );
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
			case 0x68: // PLA (POP Accumulator)          $68  4
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
			case 0x28: // PLP (POP Processor status)     $28  4
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
				cpu.x = cpu.accumulator;
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
				cpu.y = cpu.accumulator;
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
		cpu.decSP();
		region.writeByte( cpu.sp , value );
	}

	private static byte pop(CPU cpu,IMemoryRegion region) {
		final byte result = region.readByte( cpu.sp );
		cpu.incSP();
		return result;
	}

	private static void handleMemIncDec(int opcode, CPU cpu, IMemoryRegion memory,byte incDec)
	{
		// Affects Flags: S Z
		short address;
		int cycles;

		switch( opcode )
		{
			case 0xE6: // Zero Page     INC $44       $E6  2   5
			case 0xC6: // Zero Page     DEC $44       $C6  2   5
				address = memory.readByte( (short) (cpu.pc+1) );
				cpu.pc+=2;
				cycles = 5;
				break;
			case 0xF6: // Zero Page,X   INC $44,X     $F6  2   6
			case 0xD6: // Zero Page,X   DEC $44,X     $D6  2   6
				address = memory.readByte( (short) (cpu.pc+1) );
				cpu.pc+=2;
				address += cpu.x;
				address &= 0xff;
				cycles = 6;
				break;
			case 0xEE: // Absolute      INC $4400     $EE  3   6
			case 0xCE: // Absolute      DEC $4400     $CE  3   6
				address = memory.readWord( (short) (cpu.pc+1) );
				cpu.pc+=3;
				cycles = 6;
				break;
			case 0xFE: // Absolute,X    INC $4400,X   $FE  3   7
			case 0xDE: // Absolute,X    DEC $4400,X   $DE  3   7
				address = memory.readWord( (short) (cpu.pc+1) );
				cpu.pc+=3;
				address += cpu.x;
				cycles = 7;
				break;
			default:
				throw new RuntimeException("Unreachable code reached");
		}
		cpu.cycles += cycles;
		byte value = memory.readByte( address );
		value += incDec;
		memory.writeByte( address , value );
		updateZeroSigned( value , cpu );
	}
}