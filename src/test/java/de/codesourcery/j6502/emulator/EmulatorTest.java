package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.assembler.Assembler;
import de.codesourcery.j6502.assembler.AssemblerTest;
import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Parser;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.disassembler.DisassemblerTest;
import de.codesourcery.j6502.emulator.CPU.Flag;
import de.codesourcery.j6502.emulator.exceptions.InvalidOpcodeException;
import de.codesourcery.j6502.utils.HexDump;

public class EmulatorTest  extends TestCase
{
	private static final String ILLEGAL_OPCODE = ".byte $64\n";

	public static final int PRG_LOAD_ADDRESS = MemorySubsystem.Bank.BANK1.range.getStartAddress();

	public void testCMP() {
/*
MODE           SYNTAX       HEX LEN TIM
Immediate     CMP #$44      $C9  2   2
Zero Page     CMP $44       $C5  2   3
Zero Page,X   CMP $44,X     $D5  2   4
Absolute      CMP $4400     $CD  3   4
Absolute,X    CMP $4400,X   $DD  3   4+
Absolute,Y    CMP $4400,Y   $D9  3   4+
Indirect,X    CMP ($44,X)   $C1  2   6
Indirect,Y    CMP ($44),Y   $D1  2   5+

   t = A - M
  P.N = t.7
  P.C = (A>=M) ? 1:0
  P.Z = (t==0) ? 1:0
 */

		execute("LDA #$01\n CMP #$02").assertFlags(CPU.Flag.NEGATIVE);
		execute("LDA #$02\n CMP #$01").assertFlags(CPU.Flag.CARRY);
		execute("LDA #$02\n CMP #$02").assertFlags(CPU.Flag.CARRY,CPU.Flag.ZERO);

		execute("LDA #$02\n STA $44\n LDA #$01\n CMP $44").assertFlags(CPU.Flag.NEGATIVE);
		execute("LDX #$0a\n LDA #$02\n STA $44,X\n LDA #$01\n CMP $44,X").assertFlags(CPU.Flag.NEGATIVE);
		execute("LDA #$02\n STA $4000\n LDA #$01\n CMP $4000").assertFlags(CPU.Flag.NEGATIVE);
		execute("LDX #$0a\n LDA #$02\n STA $4000,X\n LDA #$01\n CMP $4000,X").assertFlags(CPU.Flag.NEGATIVE);
		execute("LDY #$0a\n LDA #$02\n STA $4000,Y\n LDA #$01\n CMP $4000,Y").assertFlags(CPU.Flag.NEGATIVE);
		execute("LDX #$0a\n LDA #$02\n STA ($44,X)\n LDA #$01\n CMP ($44,X)").assertFlags(CPU.Flag.NEGATIVE);
		execute("LDY #$0a\n LDA #$02\n STA ($44),Y\n LDA #$01\n CMP ($44),Y").assertFlags(CPU.Flag.NEGATIVE);
	}

	public void testSBC() {

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
		 */

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
		// FIXME: Write tests !
		fail("testSBC() needs to be implemented");
	}

	public void testADC() {
		/*
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

MODE           SYNTAX       HEX LEN TIM
Immediate     ADC #$44      $69  2   2
Zero Page     ADC $44       $65  2   3
Zero Page,X   ADC $44,X     $75  2   4
Absolute      ADC $4400     $6D  3   4
Absolute,X    ADC $4400,X   $7D  3   4+
Absolute,Y    ADC $4400,Y   $79  3   4+
Indirect,X    ADC ($44,X)   $61  2   6
Indirect,Y    ADC ($44),Y   $71  2   5+
			 */

		execute("LDA #$01 CLC\n ADC #$01").assertA( 0x02 ).assertFlags();
		execute("LDA #$ff CLC\n ADC #$01").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO,CPU.Flag.OVERFLOW , CPU.Flag.CARRY);
		execute("LDA #$7f CLC\n ADC #$01").assertA( 0x80 ).assertFlags(CPU.Flag.OVERFLOW , CPU.Flag.NEGATIVE );

		execute("LDA #$01 STA $44 CLC\n LDA #$01\n ADC $44").assertA( 0x02 ).assertFlags();
		execute("LDX #$10\n LDA #$01 STA $44,X CLC\n LDA #$01\n ADC $44,X").assertA( 0x02 ).assertFlags();
		execute("LDA #$01 STA $4000 CLC\n LDA #$01\n ADC $4000").assertA( 0x02 ).assertFlags();

		execute("LDX #$10\n LDA #$01 STA $4000,X CLC\n LDA #$01\n ADC $4000,X").assertA( 0x02 ).assertFlags();
		execute("LDY #$10\n LDA #$01 STA $4000,Y CLC\n LDA #$01\n ADC $4000,Y").assertA( 0x02 ).assertFlags();

		execute("LDX #$10\n LDA #$01 STA ($44,X) CLC\n LDA #$01\n ADC ($44,X)").assertA( 0x02 ).assertFlags();
		execute("LDY #$10\n LDA #$01 STA ($44),Y CLC\n LDA #$01\n ADC ($44),Y").assertA( 0x02 ).assertFlags();
	}

	public void testCPX()
	{
		execute("LDX #$01\n CPX #$02").assertFlags(CPU.Flag.NEGATIVE);
		execute("LDX #$02\n CPX #$01").assertFlags(CPU.Flag.CARRY);
		execute("LDX #$02\n CPX #$02").assertFlags(CPU.Flag.CARRY,CPU.Flag.ZERO);

		execute("LDA #$02\n STA $44\n LDX #$01\n CPX $44").assertFlags(CPU.Flag.NEGATIVE);
		execute("LDA #$02\n STA $4000\n LDX #$01\n CPX $4000").assertFlags(CPU.Flag.NEGATIVE);
	}

	public void testCPY()
	{
		execute("LDY #$01\n CPY #$02").assertFlags(CPU.Flag.NEGATIVE);
		execute("LDY #$02\n CPY #$01").assertFlags(CPU.Flag.CARRY);
		execute("LDY #$02\n CPY #$02").assertFlags(CPU.Flag.CARRY,CPU.Flag.ZERO);

		execute("LDA #$02\n STA $44\n LDY #$01\n CPY $44").assertFlags(CPU.Flag.NEGATIVE);
		execute("LDA #$02\n STA $4000\n LDY #$01\n CPY $4000").assertFlags(CPU.Flag.NEGATIVE);
	}

	public void testLDA() {
		/*
MODE           SYNTAX       HEX LEN TIM
Immediate     LDA #$44      $A9  2   2
Zero Page     LDA $44       $A5  2   3
Zero Page,X   LDA $44,X     $B5  2   4
Absolute      LDA $4400     $AD  3   4
Absolute,X    LDA $4400,X   $BD  3   4+
Absolute,Y    LDA $4400,Y   $B9  3   4+
Indirect,X    LDA ($44,X)   $A1  2   6
Indirect,Y    LDA ($44),Y   $B1  2   5+
		 */
		execute("LDA #$00").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);
		execute("LDA #$ff").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
		execute("LDA #$12").assertA( 0x12 ).assertFlags();
		execute("LDA $44").writeByte( 0x44 , 23 ).assertA( 23 ).assertFlags();
		execute("LDA 40 , X ").setX(10).writeByte( 50 , 0x12 ).assertA( 0x12 ).assertFlags();
		execute("LDA $4000 , X ").setX(0x10).writeByte( 0x4010 , 0x12 ).assertA( 0x12 ).assertFlags();
		execute("LDA $4000 , Y ").setY(0x10).writeByte( 0x4010 , 0x12 ).assertA( 0x12 ).assertFlags();
		execute("LDA (40,X)").setX(10).writeWord( 50 , 0x1234 ).writeByte( 0x1234 , 0x12 ).assertA( 0x12 ).assertFlags();
		execute("LDA (40),Y").setY(0x10).writeWord( 40 , 0x1200 ).writeByte( 0x1210 , 0x12 ).assertA( 0x12 ).assertFlags();
	}

	public void testAND() {

		// Zero Page     AND $44       $25  2   2
		execute("LDA #$ff\n STA $44\n LDA #$ff\n AND $44").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Immediate     AND #$44      $29  2   2
		execute("LDA #$ff\n AND #$ff").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
		execute("LDA #$ff\n AND #$00").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);
		execute("LDA #$01\n AND #$01").assertA( 0x01 ).assertFlags();

		// Zero Page,X   AND $44,X     $35  2   3
		execute("LDX #$05\n LDA #$ff\n STA $44,X\n LDA #$ff AND $44,X").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Absolute      AND $4400     $2D  3   4
		execute("LDA #$ff\n STA $1234\n LDA #$ff\n AND $1234").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Absolute,X    AND $4400,X   $3D  3   4+
		execute("LDX #$05\n LDA #$ff\n STA $1234,X\n LDA #$ff\n AND $1234,X").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Absolute,Y    AND $4400,Y   $39  3   4+
		execute("LDY #$05\n LDA #$ff\n STA $1234,Y\n LDA #$ff\n AND $1234,Y").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Indirect,X    AND ($44,X)   $21  2   6
		execute("LDX #$05\n LDA #$ff\n STA ($1234,X)\n LDA #$ff\n AND ($1234,X)").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Indirect,Y    AND ($44),Y   $31  2   5+
		execute("LDX #$05\n LDA #$ff\n STA ($44),Y\n LDA #$ff\n AND ($44),Y").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
	}

	public void testROL() {
/*
 Affects Flags: S Z C

MODE           SYNTAX       HEX LEN TIM
Accumulator   ROL A         $2A  1   2
Zero Page     ROL $44       $26  2   5
Zero Page,X   ROL $44,X     $36  2   6
Absolute      ROL $4400     $2E  3   6
Absolute,X    ROL $4400,X   $3E  3   7

ROL shifts all bits left one position. The Carry is shifted into bit 0 and the original bit 7 is shifted into the Carry.
 */
		final byte pattern1         = 0b0101_0101;
		final byte pattern2 = (byte) 0b1010_1010;
		execute("CLC\n LDA #"+hex(pattern1)+"\n ROL").assertA( pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
		execute("SEC\n LDA #"+hex(pattern2)+"\n ROL").assertA( pattern1 ).assertFlags(CPU.Flag.CARRY);

		execute("CLC\n LDA #"+hex((byte) 0b1000_0000 )+"\n ROL").assertA( 0 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.ZERO);
		execute("CLC\n LDA #"+hex((byte) 0b0100_0000 )+"\n ROL").assertA( 0x80 ).assertFlags(CPU.Flag.NEGATIVE);

		execute("CLC\n LDA #"+hex(pattern1)+"\n STA $44\n ROL $44").assertMemoryContains( 0x44 , pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
		execute("CLC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $40,X\n ROL $40,X").assertMemoryContains( 0x50, pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);

		execute("CLC\n LDA #"+hex(pattern1)+"\n STA $4000\n ROL $4000").assertMemoryContains( 0x4000 , pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
		execute("CLC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $4000,X\n ROL $4000,X").assertMemoryContains( 0x4010, pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
	}

	public void testASL() {
/*
 Affects Flags: S Z C

MODE           SYNTAX       HEX LEN TIM
Accumulator   ASL A         $2A  1   2
Zero Page     ASL $44       $26  2   5
Zero Page,X   ASL $44,X     $36  2   6
Absolute      ASL $4400     $2E  3   6
Absolute,X    ASL $4400,X   $3E  3   7

ASL shifts all bits left one position. The Carry is shifted into bit 0 and the original bit 7 is shifted into the Carry.
 */
		final byte pattern1         = 0b0101_0101;
		final byte pattern2 = (byte)  0b1010_1010;
		final byte pattern2s = (byte) 0b0101_0100;
		execute("CLC\n LDA #"+hex(pattern1)+"\n ASL").assertA( pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
		execute("SEC\n LDA #"+hex(pattern2)+"\n ASL").assertA( pattern2s ).assertFlags(CPU.Flag.CARRY);

		execute("CLC\n LDA #"+hex((byte) 0b1000_0000 )+"\n ASL").assertA( 0 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.ZERO);
		execute("CLC\n LDA #"+hex((byte) 0b0100_0000 )+"\n ASL").assertA( 0x80 ).assertFlags(CPU.Flag.NEGATIVE);

		execute("CLC\n LDA #"+hex(pattern1)+"\n STA $44\n ASL $44").assertMemoryContains( 0x44 , pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
		execute("CLC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $40,X\n ASL $40,X").assertMemoryContains( 0x50, pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);

		execute("CLC\n LDA #"+hex(pattern1)+"\n STA $4000\n ASL $4000").assertMemoryContains( 0x4000 , pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
		execute("CLC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $4000,X\n ASL $4000,X").assertMemoryContains( 0x4010, pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
	}

	public void testLSR() {
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

		final byte pattern1                = 0b01010101;
		final byte pattern1_shifted = (byte) 0b00101010;

		final byte pattern2         = (byte) 0b10101010;

		execute("CLC\n LDA #"+hex(pattern2)+"\n LSR").assertA( pattern1 ).assertFlagsNotSet(CPU.Flag.CARRY);
		execute("SEC\n LDA #"+hex(pattern1)+"\n LSR").assertA( pattern1_shifted ).assertFlags(CPU.Flag.CARRY);

		execute("CLC\n LDA #"+hex((byte) 0b0000_0001 )+"\n LSR").assertA( 0 ).assertFlags(CPU.Flag.ZERO,CPU.Flag.CARRY);

		execute("SEC\n LDA #"+hex(pattern1)+"\n STA $44\n LSR $44").assertMemoryContains( 0x44 , pattern1_shifted ).assertFlags(CPU.Flag.CARRY);
		execute("SEC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $40,X\n LSR $40,X").assertMemoryContains( 0x50, pattern1_shifted ).assertFlags(CPU.Flag.CARRY);

		execute("SEC\n LDA #"+hex(pattern1)+"\n STA $4000\n LSR $4000").assertMemoryContains( 0x4000 , pattern1_shifted ).assertFlags(CPU.Flag.CARRY);
		execute("SEC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $4000,X\n LSR $4000,X").assertMemoryContains( 0x4010, pattern1_shifted ).assertFlags(CPU.Flag.CARRY);
	}

	public void testROR() {
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
		final byte pattern1        = 0b0101_0101;
		final byte pattern2 = (byte) 0b1010_1010;
		execute("CLC\n LDA #"+hex(pattern2)+"\n ROR").assertA( pattern1 ).assertFlagsNotSet(CPU.Flag.CARRY);
		execute("SEC\n LDA #"+hex(pattern1)+"\n ROR").assertA( pattern2 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.NEGATIVE);

		execute("CLC\n LDA #"+hex((byte) 0b0000_0001 )+"\n ROR").assertA( 0 ).assertFlags(CPU.Flag.ZERO,CPU.Flag.CARRY);
		execute("SEC\n LDA #"+hex((byte) 0b0000_0000 )+"\n ROR").assertA( 0b1000_0000 ).assertFlags(CPU.Flag.NEGATIVE);

		execute("SEC\n LDA #"+hex(pattern1)+"\n STA $44\n ROR $44").assertMemoryContains( 0x44 , pattern2 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.NEGATIVE);
		execute("SEC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $40,X\n ROR $40,X").assertMemoryContains( 0x50, pattern2 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.NEGATIVE);

		execute("SEC\n LDA #"+hex(pattern1)+"\n STA $4000\n ROR $4000").assertMemoryContains( 0x4000 , pattern2 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.NEGATIVE);
		execute("SEC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $4000,X\n ROR $4000,X").assertMemoryContains( 0x4010, pattern2 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.NEGATIVE);
	}

	public void testEOR()
	{
		// Zero Page     AND $44       $25  2   2
		execute("LDA #$ff\n STA $44\n LDA #$ff\n EOR $44").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);

		// Immediate     EOR #$44      $29  2   2
		execute("LDA #$ff\n EOR #$ff").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);
		execute("LDA #$ff\n EOR #$00").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
		execute("LDA #$00\n EOR #$ff").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
		execute("LDA #$00\n EOR #$00").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);

		// Zero Page,X   EOR $44,X     $35  2   3
		execute("LDX #$05\n LDA #$ff\n STA $44,X\n LDA #$00 EOR $44,X").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Absolute      EOR $4400     $2D  3   4
		execute("LDA #$ff\n STA $1234\n LDA #$00\n EOR $1234").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Absolute,X    EOR $4400,X   $3D  3   4+
		execute("LDX #$05\n LDA #$ff\n STA $1234,X\n LDA #$00\n EOR $1234,X").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Absolute,Y    EOR $4400,Y   $39  3   4+
		execute("LDY #$05\n LDA #$ff\n STA $1234,Y\n LDA #$00\n EOR $1234,Y").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Indirect,X    EOR ($44,X)   $21  2   6
		execute("LDX #$05\n LDA #$ff\n STA ($1234,X)\n LDA #$00\n EOR ($1234,X)").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Indirect,Y    EOR ($44),Y   $31  2   5+
		execute("LDX #$05\n LDA #$ff\n STA ($44),Y\n LDA #$00\n EOR ($44),Y").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
	}

	public void testORA() {

		// Zero Page     ORA $44       $25  2   2
		execute("LDA #$ff\n STA $44\n LDA #$00\n ORA $44").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Immediate     ORA #$44      $29  2   2
		execute("LDA #$00\n ORA #$ff").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
		execute("LDA #$00\n ORA #$00").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);
		execute("LDA #$00\n ORA #$01").assertA( 0x01 ).assertFlags();

		// Zero Page,X   ORA $44,X     $35  2   3
		execute("LDX #$05\n LDA #$00\n STA $44,X\n LDA #$ff ORA $44,X").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Absolute      ORA $4400     $2D  3   4
		execute("LDA #$ff\n STA $1234\n LDA #$00\n ORA $1234").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Absolute,X    ORA $4400,X   $3D  3   4+
		execute("LDX #$05\n LDA #$ff\n STA $1234,X\n LDA #$00\n ORA $1234,X").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Absolute,Y    ORA $4400,Y   $39  3   4+
		execute("LDY #$05\n LDA #$ff\n STA $1234,Y\n LDA #$00\n ORA $1234,Y").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Indirect,X    ORA ($44,X)   $21  2   6
		execute("LDX #$05\n LDA #$ff\n STA ($1234,X)\n LDA #$00\n ORA ($1234,X)").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// Indirect,Y    ORA ($44),Y   $31  2   5+
		execute("LDX #$05\n LDA #$ff\n STA ($44),Y\n LDA #$00\n ORA ($44),Y").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
	}

	public void testBRK() {

		execute("LDX #$00\n BRK\n .byte $64\n LDX #$12")
		.writeWord( CPU.BRK_VECTOR_LOCATION , PRG_LOAD_ADDRESS+4 )
		.assertFlagsNotSet(CPU.Flag.BREAK)
		.assertX( 0x12 );
	}

	public void testSTX() {
		/*
STX (STore X register)

Affects Flags: none

MODE           SYNTAX       HEX LEN TIM
Zero Page     STX $44       $86  2   3
Zero Page,Y   STX $44,Y     $96  2   4
Absolute      STX $4400     $8E  3   4
		 */
		execute("STX $0a").setX(0x12).assertMemoryContains( 0x0a , 0x12 );
		execute("STX $40,Y").setX(0x12).setY(0x10).assertMemoryContains( 0x50 , 0x12 );
		execute("STX $1234").setX(0xab).assertMemoryContains( 0x1234 , 0xab );
	}

	public void testJSR()
	{
		final int returnAdr = PRG_LOAD_ADDRESS+2; // JSR instruction has 3 bytes but return address stored on stack needs to be (returnAdr-1)
		final byte lo = (byte) (returnAdr & 0xff);
		final byte hi = (byte) ((returnAdr>>8) & 0xff);
		execute("JSR $3000").assertPC(0x3000).assertOnStack( lo, hi);
	}

	public void testDec() {

		/*
MODE           SYNTAX       HEX LEN TIM
Zero Page     DEC $44       $C6  2   5
Zero Page,X   DEC $44,X     $D6  2   6
Absolute      DEC $4400     $CE  3   6
Absolute,X    DEC $4400,X   $DE  3   7
		 */
		// zero page
		execute("LDA #$02\n STA $02\n DEC $02").assertMemoryContains( 0x02 , 0x01 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
		execute("LDA #$01\n STA $02\n DEC $02").assertMemoryContains( 0x02 , 0x00 ).assertFlags( CPU.Flag.ZERO );
		execute("LDA #$00\n STA $02\n DEC $02").assertMemoryContains( 0x02 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

		// zero page , X
		execute("LDA #$02\n STA $03\n LDX #$01\n DEC $02,X").assertMemoryContains( 0x03 , 0x01 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
		execute("LDA #$01\n STA $03\n LDX #$01\n DEC $02,X").assertMemoryContains( 0x03 , 0x00 ).assertFlags( CPU.Flag.ZERO );
		execute("LDA #$00\n STA $03\n LDX #$01\n DEC $02,X").assertMemoryContains( 0x03 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

		// zero page , X with wraparound
		execute("LDA #$01\n STA $03\n LDX #$04\n DEC $ff,X").assertMemoryContains( 0x03 , 0x00 ).assertFlags( CPU.Flag.ZERO );

		// absolute
		execute("LDA #$02\n STA $1234\n DEC $1234").assertMemoryContains( 0x1234 , 0x01 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
		execute("LDA #$01\n STA $1234\n DEC $1234").assertMemoryContains( 0x1234 , 0x00 ).assertFlags( CPU.Flag.ZERO );
		execute("LDA #$00\n STA $1234\n DEC $1234").assertMemoryContains( 0x1234 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

		// absolute , X
		execute("LDA #$02\n STA $1235\n LDX #$01\n DEC $1234,X").assertMemoryContains( 0x1235 , 0x01 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
		execute("LDA #$01\n STA $1235\n LDX #$01\n DEC $1234,X").assertMemoryContains( 0x1235 , 0x00 ).assertFlags( CPU.Flag.ZERO );
		execute("LDA #$00\n STA $1235\n LDX #$01\n DEC $1234,X").assertMemoryContains( 0x1235 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

		// absolute with wrap-around
		execute("LDA #$00\n STA $03\n LDX #$04\n DEC $ffff,X").assertMemoryContains( 0x03 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );
	}

	public void testBIT() {

		// accu & memory

		// 0xff & 0xff = 0xff
		execute("LDA #$ff\n LDX #$ff\n STX $05\n BIT $05").assertFlags( CPU.Flag.NEGATIVE , CPU.Flag.OVERFLOW);

		// 0xff & 0x00 = 0x00
		execute("LDA #$ff\n LDX #$00\n STX $05\n BIT $05").assertFlags( CPU.Flag.ZERO);

		// 0xff & 0x40 = 0x40
		execute("LDA #$ff\n LDX #$40\n STX $05\n BIT $05").assertFlags( CPU.Flag.OVERFLOW );
	}

	public void testInc() {

/*
 		 Affects Flags: S Z

MODE           SYNTAX       HEX LEN TIM
Zero Page     INC $44       $E6  2   5
Zero Page,X   INC $44,X     $F6  2   6
Absolute      INC $4400     $EE  3   6
Absolute,X    INC $4400,X   $FE  3   7

 */
		// zero page
		execute("LDA #$02\n STA $02\n INC $02").assertMemoryContains( 0x02 , 0x03 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
		execute("LDA #$ff\n STA $02\n INC $02").assertMemoryContains( 0x02 , 0x00 ).assertFlags( CPU.Flag.ZERO );
		execute("LDA #$fe\n STA $02\n INC $02").assertMemoryContains( 0x02 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

		// zero page , X
		execute("LDA #$02\n STA $03\n LDX #$01\n INC $02,X").assertMemoryContains( 0x03 , 0x03 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
		execute("LDA #$ff\n STA $03\n LDX #$01\n INC $02,X").assertMemoryContains( 0x03 , 0x00 ).assertFlags( CPU.Flag.ZERO );
		execute("LDA #$fe\n STA $03\n LDX #$01\n INC $02,X").assertMemoryContains( 0x03 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

		// zero page , X with wraparound
		execute("LDA #$ff\n STA $03\n LDX #$04\n INC $ff,X").assertMemoryContains( 0x03 , 0x00 ).assertFlags( CPU.Flag.ZERO );

		// absolute
		execute("LDA #$02\n STA $1234\n INC $1234").assertMemoryContains( 0x1234 , 0x03 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
		execute("LDA #$ff\n STA $1234\n INC $1234").assertMemoryContains( 0x1234 , 0x00 ).assertFlags( CPU.Flag.ZERO );
		execute("LDA #$fe\n STA $1234\n INC $1234").assertMemoryContains( 0x1234 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

		// absolute , X
		execute("LDA #$02\n STA $1235\n LDX #$01\n INC $1234,X").assertMemoryContains( 0x1235 , 0x03 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
		execute("LDA #$ff\n STA $1235\n LDX #$01\n INC $1234,X").assertMemoryContains( 0x1235 , 0x00 ).assertFlags( CPU.Flag.ZERO );
		execute("LDA #$fe\n STA $1235\n LDX #$01\n INC $1234,X").assertMemoryContains( 0x1235 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

		// absolute with wrap-around
		execute("LDA #$fe\n STA $03\n LDX #$04\n INC $ffff,X").assertMemoryContains( 0x03 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );
	}

	public void testRTI() {

		final int returnAdr = PRG_LOAD_ADDRESS+14;
		final byte lo = low(returnAdr);
		final byte hi = high(returnAdr);

		final byte flags = CPU.Flag.CARRY.set((byte)0);

		final String source = "LDX #$00\n"+ // 2 bytes , clear X so we are sure that its value was set by the subroutine
		        "LDA #"+hex(hi)+"\nPHA\n"+ // 3 bytes , push return address hi
				"LDA #"+hex(lo)+"\nPHA\n"+ // 3 bytes , push return address lo
				"LDA #"+hex(flags)+"\n PHA\n"+ // 3 bytes , push processor flags
				"CLC\n"+ // 1 byte , clear carry so we know that processor flags got restored by RTI
                "RTI\n"+ // 1 byte , return , invokes our subroutine
                ILLEGAL_OPCODE+ // 1 byte
	            "sub: LDX #$11\n"+ // set X so we know the sub routine got executed
	            ILLEGAL_OPCODE;

		execute(source).assertX(0x11).assertFlags( CPU.Flag.CARRY );
	}

	public void testRTS()
	{
		final String source = "JSR sub\n"+
		                       "LDA #$12\n"+
		                       ILLEGAL_OPCODE+
				               "sub: LDX #$11\n"+
				               "     RTS";
		execute(source).assertA(0x12).assertX(0x11);
	}

	public void testStackOperations() {

/*
 		TXS (Transfer X to Stack ptr)   $9A  2
TSX (Transfer Stack ptr to X)   $BA  2
PHA (PusH Accumulator)          $48  3
PLA (PuLl Accumulator)          $68  4
PHP (PusH Processor status)     $08  3
PLP (PuLl Processor status)     $28  4
 */

		execute("LDX #$12\n TXS").assertSP( 0x12 );
		execute("LDX #$12\n TXS\n LDX #$00\n TSX").assertSP( 0x12 ).assertX( 0x12 ).assertFlags();
		execute("LDX #$00\n TXS\n LDX #$aa\n TSX").assertSP( 0x00 ).assertX( 0x00 ).assertFlags( CPU.Flag.ZERO );
		execute("LDX #$ff\n TXS\n LDX #$aa\n TSX").assertSP( 0xff ).assertX( 0xff ).assertFlags( CPU.Flag.NEGATIVE);

		execute("LDA #$12\n PHA").assertOnStack( (byte) 0x12);
		execute("LDA #$12\n PHA\n LDA #$00\n PLA").assertA( 0x12 ).assertFlags();
		execute("LDA #$00\n PHA\n LDA #$ff\n PLA").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);
		execute("LDA #$ff\n PHA\n LDA #$00\n PLA").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		execute("LDA #$ff\n PHP\n LDA #$00\n PLP").assertA( 0x00 ).assertFlags(CPU.Flag.NEGATIVE);
		execute("LDA #$00\n PHP\n LDA #$ff\n PLP").assertA( 0xff ).assertFlags(CPU.Flag.ZERO);
	}

	public void testChangeProcessorFlags()
	{
		execute("SEC").assertFlags(CPU.Flag.CARRY);
		execute("SEI").assertFlags(CPU.Flag.IRQ_DISABLE);
		execute("SED").assertFlags(CPU.Flag.DECIMAL_MODE);

		// FIXME: Set carry flag before executing CLV
		execute("CLC").assertFlagsNotSet(CPU.Flag.CARRY);
		execute("SEI \nCLI").assertFlagsNotSet(CPU.Flag.IRQ_DISABLE);
		execute("SED \nCLD").assertFlagsNotSet(CPU.Flag.DECIMAL_MODE);

		// FIXME: Set overflow flag before executing CLV
		execute("CLV").assertFlagsNotSet(CPU.Flag.OVERFLOW);
	}

	public void testRegisterIns() {
		/*
			case 0xAA: // TAX (Transfer A to X)    $AA
			case 0x8A: // TXA (Transfer X to A)    $8A
			case 0xA8: // TAY (Transfer A to Y)    $A8
			case 0x98: // TYA (Transfer Y to A)    $98

			case 0xCA: // DEX (DEcrement X)        $CA
			case 0x88: // DEY (DEcrement Y)        $88
			case 0xE8: // INX (INcrement X)        $E8
			case 0xC8: // INY (INcrement Y)        $C8
		 */
		execute("LDA #$12\n LDX #$00\n TAX").assertX( 0x12 ).assertFlagsNotSet(CPU.Flag.ZERO, CPU.Flag.NEGATIVE );
		execute("LDX #$12\n LDA #$00\n TXA").assertA( 0x12 ).assertFlagsNotSet(CPU.Flag.ZERO, CPU.Flag.NEGATIVE );

		execute("LDA #$00\n LDY #$ff\n TAX").assertX( 0x00 ).assertFlags(CPU.Flag.ZERO);
		execute("LDX #$00\n LDY #$ff TXA").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);

		execute("LDA #$ff\n LDY #$00\n TAX").assertX( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
		execute("LDX #$ff\n LDY #$0 TXA").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		execute("LDA #$12\n LDY #$ff\n TAY").assertY( 0x12 ).assertFlagsNotSet(CPU.Flag.ZERO, CPU.Flag.NEGATIVE );
		execute("LDY #$12\n LDA #$00\n TYA").assertA( 0x12 ).assertFlagsNotSet(CPU.Flag.ZERO, CPU.Flag.NEGATIVE );

		execute("LDA #$00\n LDY #$ff\n TAY").assertY( 0x00 ).assertFlags(CPU.Flag.ZERO);
		execute("LDY #$00\n LDA #$ff\n TYA").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);

		execute("LDA #$ff\n LDY #$00\n TAY").assertY( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
		execute("LDY #$ff\n LDA #$00\n TYA").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		execute("LDA #$12\n LDY #$00\n TAY").assertY( 0x12 ).assertFlagsNotSet(CPU.Flag.ZERO, CPU.Flag.NEGATIVE );
		execute("LDY #$12\n LDA #$00 TYA").assertA( 0x12 ).assertFlagsNotSet(CPU.Flag.ZERO, CPU.Flag.NEGATIVE );

		// dec
		execute("LDX #$02\n DEX").assertX( 0x01 ).assertFlagsNotSet(CPU.Flag.NEGATIVE,CPU.Flag.ZERO);
		execute("LDY #$02\n DEY").assertY( 0x01 ).assertFlagsNotSet(CPU.Flag.NEGATIVE,CPU.Flag.ZERO);

		execute("LDX #$01\n DEX").assertX( 0x00 ).assertFlags(CPU.Flag.ZERO);
		execute("LDY #$01\n DEY").assertY( 0x00 ).assertFlags(CPU.Flag.ZERO);

		execute("LDX #$00\n DEX").assertX( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
		execute("LDY #$00\n DEY").assertY( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

		// inc
		execute("LDX #$00\n INX").assertX( 0x01 ).assertFlagsNotSet(CPU.Flag.NEGATIVE,CPU.Flag.ZERO);
		execute("LDY #$00\n INY").assertY( 0x01 ).assertFlagsNotSet(CPU.Flag.NEGATIVE,CPU.Flag.ZERO);

		execute("LDX #$ff\n INX").assertX( 0x00 ).assertFlags(CPU.Flag.ZERO);
		execute("LDY #$ff\n INY").assertY( 0x00 ).assertFlags(CPU.Flag.ZERO);

		execute("LDX #$7f\n INX").assertX( 0x80 ).assertFlags(CPU.Flag.NEGATIVE);
		execute("LDY #$7f\n INY").assertY( 0x80 ).assertFlags(CPU.Flag.NEGATIVE);
	}

	public void testSTY()
	{
		/*
		MODE           SYNTAX       HEX LEN TIM
		Zero Page     STY $44       $84  2   3
		Zero Page,X   STY $44,X     $94  2   4
		Absolute      STY $4400     $8C  3   4
		 */
		execute("STY $0a").setY(0x12).assertMemoryContains( 0x0a , 0x12 );
		execute("STY $40,X").setY(0x12).setX(0x10).assertMemoryContains( 0x50 , 0x12 );
		execute("STY $1234").setY(0xab).assertMemoryContains( 0x1234 , 0xab );
	}

	public void testSTA() {
		/*
MODE           SYNTAX       HEX LEN TIM
Zero Page     STA $44       $85  2   3
Zero Page,X   STA $44,X     $95  2   4
Absolute      STA $4400     $8D  3   4
Absolute,X    STA $4400,X   $9D  3   5
Absolute,Y    STA $4400,Y   $99  3   5
Indirect,X    STA ($44,X)   $81  2   6
Indirect,Y    STA ($44),Y   $91  2   6
		 */
		execute("STA $0a").setA(0x12).assertMemoryContains( 0x0a , 0x12 );
		execute("STA $40,X").setA(0x12).setX(0x10).assertMemoryContains( 0x50 , 0x12 );
		execute("STA $1234").setA(0xab).assertMemoryContains( 0x1234 , 0xab );
		execute("STA $1200 , X ").setA(0xab).setX(0x10).assertMemoryContains( 0x1210 , 0xab );
		execute("STA $1200 , Y ").setA(0xab).setY(0x10).assertMemoryContains( 0x1210 , 0xab );
		execute("STA (40,X) ").setA(0xab).setX(10).writeWord( 50 , 0x1234 ).assertMemoryContains( 0x1234 , 0xab );
		execute("STA (40),Y ").setA(0xab).setY(0x10).writeWord( 40 , 0x1200 ).assertMemoryContains( 0x1210 , 0xab );
	}

	public void testLDX() {
		/*
MODE           SYNTAX       HEX LEN TIM
Immediate     LDX #$44      $A2  2   2
Zero Page     LDX $44       $A6  2   3
Zero Page,Y   LDX $44,Y     $B6  2   4
Absolute      LDX $4400     $AE  3   4
Absolute,Y    LDX $4400,Y   $BE  3   4+
		 */
		execute("LDX #$00").assertX( 0x00 ).assertFlags(CPU.Flag.ZERO);
		execute("LDX #$ff").assertX( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
		execute("LDX #$12").assertX( 0x12 ).assertFlags();
		execute("LDX $44").writeByte( 0x44 , 23 ).assertX( 23 ).assertFlags();
		execute("LDX 40 , Y ").setY(10).writeByte( 50 , 0x12 ).assertX( 0x12 ).assertFlags();
		execute("LDX $4000").writeByte( 0x4000 , 0x12 ).assertX( 0x12 ).assertFlags();
		execute("LDX $4000 , Y ").setY(0x10).writeByte( 0x4010 , 0x12 ).assertX( 0x12 ).assertFlags();
	}

	public void testLDY() {
		/*
MODE           SYNTAX       HEX LEN TIM
Immediate     LDY #$44      $A0  2   2
Zero Page     LDY $44       $A4  2   3
Zero Page,X   LDY $44,X     $B4  2   4
Absolute      LDY $4400     $AC  3   4
Absolute,X    LDY $4400,X   $BC  3   4+
		 */
		execute("LDY #$00").assertY( 0x00 ).assertFlags(CPU.Flag.ZERO);
		execute("LDY #$ff").assertY( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
		execute("LDY #$12").assertY( 0x12 ).assertFlags();
		execute("LDY $44").writeByte( 0x44 , 23 ).assertY( 23 ).assertFlags();
		execute("LDY 40 , X ").setX(10).writeByte( 50 , 0x12 ).assertY( 0x12 ).assertFlags();
		execute("LDY $4000").writeByte( 0x4000 , 0x12 ).assertY( 0x12 ).assertFlags();
		execute("LDY $4000 , X ").setX(0x10).writeByte( 0x4010 , 0x12 ).assertY( 0x12 ).assertFlags();
	}

	public void testJMP() {
		execute("JMP $2000").assertPC( 0x2000 );
		execute("JMP ($2000)").writeWord(0x2000, 0x1234 ).assertPC( 0x1234 );
	}

	public void testNOP() {
		execute("NOP").assertPC( PRG_LOAD_ADDRESS+1 );
	}

	public void testBranchOnFlagSet() {

		final int dest = PRG_LOAD_ADDRESS+100;
		final String destination = HexDump.toAdr( dest );

		execute("BMI "+destination).setFlags(CPU.Flag.NEGATIVE).assertPC( dest );
		execute("BVS "+destination).setFlags(CPU.Flag.OVERFLOW).assertPC( dest );
		execute("BCS "+destination).setFlags(CPU.Flag.CARRY).assertPC( dest );
		execute("BEQ "+destination).setFlags(CPU.Flag.ZERO).assertPC( dest );

		execute("BPL "+destination).clearFlags(CPU.Flag.NEGATIVE).assertPC( dest );
		execute("BVC "+destination).clearFlags(CPU.Flag.OVERFLOW).assertPC( dest );
		execute("BCC "+destination).clearFlags(CPU.Flag.CARRY).assertPC( dest );
		execute("BNE "+destination).clearFlags(CPU.Flag.ZERO).assertPC( dest );
	}

	public void testLDAImmediateNonZero()
	{
		execute("LDA #$44").assertA( 0x44 ).assertFlags();
	}

	public void testLDAImmediateZero()
	{
		execute("LDA #$00").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);
	}

	public void testLDAImmediateNegative()
	{
		execute("LDA #$80").assertA( 0x80 ).assertFlags(CPU.Flag.NEGATIVE);
	}

	// ============ helper ================

	protected final class Helper
	{
		private Emulator emulator = new Emulator();
		private final String source;
		private boolean executed = false;

		private final List<Runnable> blocks = new ArrayList<>();

		public Helper(String source) {
			this.source = source;
		}

		public Helper assertFlags(CPU.Flag... flags)
		{
			maybeExecute();

			final Set<Flag> enabledFlags = emulator.getCPU().getFlags();
			final int expectedCount = flags == null ? 0 : flags.length;
			assertEquals( "Expected flags "+ArrayUtils.toString( flags )+" but got "+enabledFlags , expectedCount , enabledFlags.size() );
			if ( flags != null ) {
				for ( final Flag exp : flags ) {
					if ( ! enabledFlags.contains( exp ) ) {
						fail( "Expected flags "+ArrayUtils.toString( flags )+" but got "+enabledFlags );
					}
				}
			}
			return this;
		}

		public Helper assertFlagsNotSet(CPU.Flag flag1,CPU.Flag... flags2)
		{
			maybeExecute();

			final Set<Flag> enabledFlags = emulator.getCPU().getFlags();
			final Set<Flag> clearedFlags = new HashSet<>();
			clearedFlags.add( flag1 );
			if ( flags2 != null ) {
				clearedFlags.addAll(Arrays.asList(flags2));
			}
			for ( final Flag exp : clearedFlags)
			{
				if ( enabledFlags.contains( exp ) ) {
					fail( "Expected CPU flag  "+exp+" to be cleared but wasn't");
				}
			}
			return this;
		}

		public Helper setA(int value) {
			return block( () ->
			{
				emulator.getCPU().accumulator = (byte) value;
			});
		}

		public Helper setX(int value) {
			return block( () ->
			{
				emulator.getCPU().x = (byte) value;
			});
		}

		public Helper setY(int value) {
			return block( () ->
			{
				emulator.getCPU().y = (byte) value;
			});
		}

		public Helper setFlags(CPU.Flag flag1,CPU.Flag... flags2)
		{
			return block( () ->
			{
				emulator.getCPU().setFlag( flag1 );
				if ( flags2 != null ) {
					for ( final CPU.Flag f : flags2 ) {
						emulator.getCPU().setFlag( f );
					}
				}
			});
		}

		public Helper clearFlags(CPU.Flag flag1,CPU.Flag... flags2)
		{
			return block( () ->
			{
				emulator.getCPU().clearFlag( flag1 );
				if ( flags2 != null ) {
					for ( final CPU.Flag f : flags2 ) {
						emulator.getCPU().clearFlag( f );
					}
				}
			});
		}

		public Helper writeByte(int address,int value)
		{
			return block( () ->
			{
				emulator.getMemory().writeByte( (short) address , (byte) value );
			});
		}

		public Helper writeWord(int address,int value)
		{
			return block( () ->
			{
				emulator.getMemory().writeWord( (short) address , (short) value );
			});
		}

		public Helper assertA(int value) {

			maybeExecute();

			final int expected = value;
			final int actual = emulator.getCPU().accumulator;
			assertEquals( "Expected accumulator to contain "+hex((byte)expected)+" ("+bin((byte) expected)+")"
					+ " but was "+hex((byte)actual)+" ("+bin((byte)actual)+")",expected & 0xff , actual & 0xff );
			return this;
		}

		// assertSP
		public Helper assertSP(int value) {

			maybeExecute();

			final short expected = (short) (value & 0xffff);
			final short actual = emulator.getCPU().sp;
			assertEquals( (byte) expected , (byte) (actual & 0xff) );
			return this;
		}

		public Helper assertX(int value) {

			maybeExecute();

			final int expected = value;
			final int actual = emulator.getCPU().x;
			assertEquals( expected & 0xff , actual & 0xff );
			return this;
		}

		public Helper assertY(int value) {

			maybeExecute();

			final int expected = value;
			final int actual = emulator.getCPU().y;
			assertEquals( expected & 0xff , actual & 0xff );
			return this;
		}

		public Helper assertPC(int value) {

			maybeExecute();

			final short expected = (short) value;
			final short actual = emulator.getCPU().pc;
			assertEquals( "Expected PC = "+hex(expected)+" but was "+hex(actual) , expected , actual );
			return this;
		}

		public Helper assertOnStack(byte b1,byte... bytes)
		{
			maybeExecute();

			final int len = 1 + ( bytes != null ? bytes.length : 0 );
			final byte[] expected = new byte[len];
			expected[0]=b1;
			if ( bytes != null ) {
				for ( int i = 0 ; i < bytes.length ; i++ ) {
					expected[1+i] = bytes[i];
				}
			}

			short adr = emulator.getCPU().sp;
			for ( int i = 0 ; i < expected.length ; i++ )
			{
				final byte actual =  emulator.getMemory().readByte( adr );
				final byte exp = expected[i];
				assertEquals( "Expected byte $"+HexDump.toHex(exp)+" on stack @ "+HexDump.toAdr(adr)+" but got $"+
						HexDump.toHex(actual) , exp , actual );
				adr += 1;
			}
			return this;
		}

		public Helper block(Runnable r)
		{
			assertFalse("Program already executed" , executed );
			blocks.add( r );
			return this;
		}

		public Helper assertMemoryContains(int offset,int expected1,int... expected2) {

			maybeExecute();

			final int len = 1 + ( expected2 == null ? 0 : expected2.length );
			final byte[] expected = new byte[len];
			expected[0] = (byte) expected1;
			if ( expected2 != null ) {
				for ( int i = 0 ;i < expected2.length ; i++ ) {
					expected[i+1] = (byte) expected2[i];
				}
			}

			final byte[] actual = new byte[ expected.length ];
			for ( int i = 0 ; i < actual.length ; i++ )
			{
				actual[i] = emulator.getMemory().readByte( (short) (offset+i) );
			}

			AssemblerTest.assertArrayEquals( offset , expected , actual );
			return this;
		}

		private void maybeExecute()
		{
			if ( executed ) {
				return;
			}
			try
			{
				final int ADDITIONAL_BYTES = 1; // number of bytes we insert before executing the test payload

				final String asm = "*= "+HexDump.toAdr( PRG_LOAD_ADDRESS-ADDITIONAL_BYTES )+"\nCLI\n"+source;
				// assemble
				final Parser p = new Parser(new Lexer(new Scanner( asm )));
				final Assembler a = new Assembler();
				final byte[] actual;
				try {
					actual = a.assemble( p.parse() );
				}
				catch(final RuntimeException e)
				{
					DisassemblerTest.maybePrintError( asm , e );
					throw e;
				}

				final IMemoryProvider provider = new IMemoryProvider()
				{
					@Override
					public void loadInto(IMemoryRegion region)
					{
						// switch to all-ram so overwritten RESET and BRK vectors
						// actually take effect (otherwise the memory region would be mapped to kernel rom
						// and thus reads would always return the ROM values)
						((MemorySubsystem) emulator.getMemory()).setMemoryLayout( (byte) 0 );

						// fill memory with invalid instructions
						// so the CPU stops when it runs off the compiled program
						for ( int i = 2 ; i < 65536 ; i+=2) { // starting at 0x02 because of PLA data & control registers at 0x00 and 0x01
							region.writeWord( (short) i , (short) 0x6464 );
						}

						region.bulkWrite( (short) a.getOrigin() , actual , 0 , actual.length );
						region.writeWord( (short) CPU.RESET_VECTOR_LOCATION , (short) PRG_LOAD_ADDRESS );

						System.out.println("At program start: ");
						HexDump.INSTANCE.dump( (short) PRG_LOAD_ADDRESS , region , PRG_LOAD_ADDRESS , 1024 );
					}
				};

				emulator.reset();

				emulator.setMemoryProvider( provider );
				emulator.getCPU().pc = (short) (PRG_LOAD_ADDRESS-ADDITIONAL_BYTES);

				blocks.forEach( b -> b.run() );

				int instructions = 30;
				while ( instructions-- > 0 )
				{
					try {
						emulator.singleStep();
					} catch(final InvalidOpcodeException e) {
						break;
					}
				}
				if ( instructions <= 0 ) {
					System.err.println("WARNING -- stopped execution after 10 instructions");
				}

				System.out.println("\n---------------------");
				System.out.println("Compiled: "+asm+"\n");
				System.out.println("Memory  :\n");
				System.out.println( HexDump.INSTANCE.dump((short) PRG_LOAD_ADDRESS  , emulator.getMemory() , PRG_LOAD_ADDRESS , 16 ) );
				System.out.println("\nCPU     : "+emulator.getCPU());
				System.out.println("Stack:\n");

				int sp =  emulator.getCPU().sp;
				System.out.println("SP: "+hex(emulator.getCPU().sp ) );
				sp = sp & 0xffff;
				System.out.println( HexDump.INSTANCE.dump( emulator.getCPU().sp  , emulator.getMemory() , sp , 16 ) );
			} finally {
				executed = true;
			}
		}
	}

	private static byte low(int value) {
		return (byte) value;
	}

	private static byte high(int value) {
		return (byte) (value>>8);
	}

	private Helper execute(String source)
	{
		return new Helper(source);
	}

	private static String bin(byte b) {
		return "0b"+StringUtils.leftPad( Integer.toBinaryString( b & 0xff ) , 8 , '0' );
	}

	private static String hex(byte b) {
		return "$"+HexDump.toHex( b );
	}

	private static String hex(short b) {
		return "$"+HexDump.toHex( b );
	}
}
