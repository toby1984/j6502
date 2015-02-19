package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import junit.framework.TestCase;

import org.apache.commons.lang.ArrayUtils;

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

	private static final int PRG_LOAD_ADDRESS = MemorySubsystem.Bank.BANK1.range.getStartAddress();

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

		public Helper setSP(int value)
		{
			return block( () ->
			{
				emulator.getCPU().sp = (short) value;
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
				emulator.getMemory().writeByte( address , (byte) value );
			});
		}

		public Helper writeWord(int address,int value)
		{
			return block( () ->
			{
				emulator.getMemory().writeWord( address , (short) value );
			});
		}

		public Helper assertA(int value) {

			maybeExecute();

			final int expected = value;
			final int actual = emulator.getCPU().accumulator;
			assertEquals( expected & 0xff , actual & 0xff );
			return this;
		}

		// assertSP
		public Helper assertSP(int value) {

			maybeExecute();

			final short expected = (short) (value & 0xffff);
			final short actual = emulator.getCPU().sp;
			assertEquals( expected , actual );
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
			try {
				final String asm = "*= "+HexDump.toAdr( PRG_LOAD_ADDRESS )+"\n"+source;
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
						region.bulkWrite( 0 , actual , 0 , actual.length );
						region.writeWord( CPU.RESET_VECTOR_LOCATION , (short) PRG_LOAD_ADDRESS );
					}
				};

				emulator.reset();

				emulator.setMemoryProvider( provider );
				emulator.getCPU().pc = (short) PRG_LOAD_ADDRESS;

				blocks.forEach( b -> b.run() );

				int instructions = 10;
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

	private Helper execute(String source)
	{
		return new Helper(source);
	}

	private static String hex(byte b) {
		return "$"+HexDump.toHex( b );
	}
	private static String hex(short b) {
		return "$"+HexDump.toHex( b );
	}
}
