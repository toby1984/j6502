package de.codesourcery.j6502.assembler;

import junit.framework.TestCase;
import de.codesourcery.j6502.assembler.parser.Identifier;
import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Parser;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.disassembler.DisassemblerTest;
import de.codesourcery.j6502.emulator.EmulatorTest;
import de.codesourcery.j6502.utils.HexDump;

public class AssemblerTest extends TestCase {

	private static final int PRG_LOAD_ADDRESS = EmulatorTest.PRG_LOAD_ADDRESS;
	private static final String SET_ORIGIN_CMD = "*="+HexDump.toAdr( PRG_LOAD_ADDRESS )+"\n";

	private void assertCompilesTo(String asm, int expected1, int... expected2)
	{
		final String source = ! asm.contains("*=") ? SET_ORIGIN_CMD+asm : asm;
		final Parser p = new Parser(new Lexer(new Scanner(source)));

		final Assembler a = new Assembler();
		final byte[] actual = a.assemble( p.parse() );
		assertArrayEquals( actual, expected1,expected2 );
	}

	public static void assertArrayEquals(byte[] actual,int expected1,int... expected2)
	{
		final int len = 1 + ( expected2 != null ? expected2.length : 0 );
		final byte[] expected = new byte[ len ];
		expected[0] = (byte) expected1;
		if ( expected2 != null ) {
			for ( int i = 0 ; i < expected2.length ; i++ ) {
				expected[1+i] = (byte) expected2[i];
			}
		}
		assertArrayEquals(expected,actual);
	}

	public static void assertArrayEquals(byte[] expected, byte[] actual)
	{
		assertArrayEquals(0,expected,actual);
	}

	public static void assertArrayEquals(int offset, byte[] expected, byte[] actual)
	{

		final int min = Math.min( expected.length , actual.length );

		for ( int i =  0 ; i < min ; i++ ) {
			final byte ex = (byte) (expected[i] & 0xff);
			assertEquals( "Mismatch on byte @ "+HexDump.toAdr(offset+i)+" , got $"+
			HexDump.byteToString( actual[i] )+" but expected $"+HexDump.byteToString( ex )+"\n\n"+HexDump.INSTANCE.dump((short) 0, actual, 0, actual.length), ex , actual[i] );
		}
		if ( actual.length != expected.length ) {
			fail("Length mismatch: actual = "+actual.length+" , expected = "+expected.length+"\n\n"+HexDump.INSTANCE.dump((short) 0, actual, 0, actual.length) );
		}
	}

	private void assertDoesNotCompile(String s)
	{
		final Parser p = new Parser(new Lexer(new Scanner(s)));

		final Assembler a = new Assembler();

		try {
			a.assemble( p.parse() );
			fail("Compiling "+s+" should've failed");
		} catch(final Exception e) {
			System.out.println("EXPECTED FAILURE: "+e.getMessage());
			// ok
		}
	}
	
	public void testImmediateValueOutOfRange() {
		assertDoesNotCompile("*=$c000\nLDA #1234");
	}

	public void testJMPAbs() {
		final String s = "JMP  $3146; 00d1:  4c 46 31 lF1";
		assertCompilesTo(s , 0x4c , 0x46 , 0x31 );
	}

	public void testASL3() {
		final String s = "ASL  $ff9e , X; 005a:  1e 9e 5a ..Z";
		assertCompilesTo(s , 0x1e , 0x9e , 0xff );
	}
	
	public void testASL32() {
		final String s = "label: ASL  $ff9e , X; 005a:  1e 9e 5a ..Z";
		assertCompilesTo(s , 0x1e , 0x9e , 0xff );
	}	

	public void testORA2() {
		final String s = "ORA  $e1; 000f:  05 e1    ..";
		assertCompilesTo(s , 0x05 , 0xe1 );
	}
	
	public void testSTA2() {
		final String s = "STA $01,Y";
		assertCompilesTo(s , 0x99 , 0x01 , 0x00 );
	}

	public void testASL2() {
		final String s = "ASL  $ff9e , X; 005a:  1e 9e 5a ..Z";
		assertCompilesTo(s , 0x1e , 0x9e , 0xff );
	}

	public void testBackwardsReferenceToGlobalLabelInZeroPage()
	{
		final String s = SET_ORIGIN_CMD+"NOP\n"+
				          "label:\n"+
	                      "       JMP label";

		final Parser p = new Parser(new Lexer(new Scanner(s)));

		final Assembler a = new Assembler();
		final byte[] actual = a.assemble( p.parse() );

		final ISymbolTable symbolTable = a.context.getSymbolTable();

		final Identifier id = new Identifier("label");
		assertTrue( symbolTable.isDefined( id , null ) );
		final ISymbol<?> symbol = symbolTable.getSymbol( id , null );
		assertNotNull( symbol );
		assertEquals( Label.class , symbol.getClass() );
		final Label label = (Label) symbol;
		assertTrue( label.hasValue() );

		final int branchTarget = PRG_LOAD_ADDRESS+1;
		assertEquals( new Integer(branchTarget) , label.getValue() );

		final byte lo = (byte) (branchTarget & 0xff);
		final byte hi = (byte) ((branchTarget >>8) & 0xff);
		assertArrayEquals( actual , 0xea, 0x4c, lo , hi );
	}

	public void testByteInitializedMemory() {
		assertCompilesTo(".byte $01,2,3,$4" , 1 , 2, 3, 4 );
	}
	
	public void testEQU1() 
	{
		assertCompilesTo("reg: .equ $12\n.byte reg" , 0x12 );
	}	
	
	public void testEQU2() 
	{
		assertCompilesTo("CIA2_PRA: .equ $d000\nSTA CIA2_PRA" , 0x8d , 0x00, 0xd0 );
	}	

	public void testByteInitializedMemoryWithLabel() {
		assertCompilesTo("label: .byte $01,2,3,$4" , 1 , 2, 3, 4 );
	}
	
	public void testSetOriginForwards()
	{
		final String s = "*= $0a\n"
				           + " NOP";

		final Parser p = new Parser(new Lexer(new Scanner(s)));

		final Assembler a = new Assembler();
		final byte[] actual = a.assemble( p.parse() );
		assertEquals(1, actual.length );
		assertArrayEquals( actual , 0xea );
	}

	public void testSetOriginBackwardsFails()
	{
		final String s = " NOP\n"+
				          " *= $0";

		final Parser p = new Parser(new Lexer(new Scanner(s)));

		final Assembler a = new Assembler();
		try {
			final byte[] actual = a.assemble( p.parse() );
			fail("Should've failed");
		} catch(final Exception e) {
			e.printStackTrace();
			// ok
		}
	}

	public void testForwardsReferenceToGlobalLabel()
	{
		final String s = SET_ORIGIN_CMD+"JMP label\n"+
	                      "NOP\n"+
				          "label:";

		final Parser p = new Parser(new Lexer(new Scanner(s)));

		final Assembler a = new Assembler();
		final byte[] actual;
		try {
			actual = a.assemble( p.parse() );
		}
		catch(final Exception e)
		{
			DisassemblerTest.maybePrintError( new StringBuilder(s) , e );
			throw e;
		}

		final ISymbolTable symbolTable = a.context.getSymbolTable();

		final Identifier id = new Identifier("label");
		assertTrue( symbolTable.isDefined( id , null ) );
		final ISymbol<?> symbol = symbolTable.getSymbol( id , null );
		assertNotNull( symbol );
		assertEquals( Label.class , symbol.getClass() );
		final Label label = (Label) symbol;
		assertTrue( label.hasValue() );

		final int branchTarget = PRG_LOAD_ADDRESS+4;
		assertEquals( new Integer(branchTarget) , label.getValue() );

		final byte lo = (byte) (branchTarget & 0xff);
		final byte hi = (byte) ((branchTarget >>8) & 0xff);
		assertArrayEquals( actual , 0x4c, lo , hi , 0xea );
	}

	public void testForwardsReferenceToLocalLabel()
	{
		final String s = SET_ORIGIN_CMD+"global:"+
	                     "NOP\n"+ // 1 byte
				         "JMP local1\n"+ // 3 bytes
	                     "NOP\n"+ // 1 byte
				         ".local1";

		final Parser p = new Parser(new Lexer(new Scanner(s)));

		final Assembler a = new Assembler();
		final byte[] actual = a.assemble( p.parse() );

		final ISymbolTable symbolTable = a.context.getSymbolTable();

		final Identifier globalName = new Identifier("global");
		final Identifier localName = new Identifier("local1");
		assertTrue( symbolTable.isDefined( globalName , null ) );
		assertTrue( symbolTable.isDefined( localName , globalName ) );

		final ISymbol<?> globalSymbol = symbolTable.getSymbol( globalName , null );
		assertNotNull( globalSymbol );
		assertEquals( Label.class , globalSymbol.getClass() );
		final Label globalLabel = (Label) globalSymbol;
		assertTrue( globalLabel.hasValue() );

		assertEquals( new Integer(PRG_LOAD_ADDRESS) , globalLabel.getValue() );

		final ISymbol<?> localSymbol = symbolTable.getSymbol( localName , globalName );
		assertNotNull( localSymbol );
		assertEquals( Label.class , globalSymbol.getClass() );
		final Label localLabel = (Label) localSymbol;
		assertTrue( localLabel.hasValue() );

		final int branchTarget = PRG_LOAD_ADDRESS+5;
		assertEquals( new Integer(branchTarget) , localLabel.getValue() );

		final byte lo = (byte) (branchTarget & 0xff);
		final byte hi = (byte) ((branchTarget >>8) & 0xff);

		assertEquals( new Integer(branchTarget) , localLabel.getValue() );

		assertArrayEquals( actual , 0xea, 0x4c, lo , hi , 0xea );
	}

	public void testLDA() {

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      LDA $4400     $AD  3   4
		assertCompilesTo( "LDA $4400" , 0xad, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    LDA $4400,X   $BD  3   4+
		assertCompilesTo( "LDA $4400,X" , 0xbd,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      LDA $4400,Y   $B9  3   4+
		assertCompilesTo( "LDA $4400,Y" , 0xb9,0x00,0x44);
		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   LDA $44,X     $B5  2   4
		assertCompilesTo( "LDA $44,X" , 0xb5,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     LDA #$44      $A9  2   2
		assertCompilesTo( "LDA #$44" , 0xa9,0x44);
		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      LDA ($44,X)   $A1  2   6
		assertCompilesTo( "LDA ($44,X)" , 0xa1,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      LDA ($44),Y   $B1  2   5+
		assertCompilesTo( "LDA ($44),Y" , 0xb1,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     LDA $44       $A5  2   3		#
		assertCompilesTo( "LDA $44" , 0xa5,0x44);
	}

	public void testSTA()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      STA $4400     $AD  3   4
		assertCompilesTo( "STA $4400" , 0x8d, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    STA $4400,X   $BD  3   4+
		assertCompilesTo( "STA $4400,X" , 0x9d,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      STA $4400,Y   $B9  3   4+
		assertCompilesTo( "STA $4400,Y" , 0x99,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   STA $44,X     $B5  2   4
		assertCompilesTo( "STA $44,X" , 0x95,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     STA #$44      $A9  2   2
		assertDoesNotCompile( "STA #$44");

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      STA ($44,X)   $A1  2   6
		assertCompilesTo( "STA ($44,X)" , 0x81,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      STA ($44),Y   $B1  2   5+
		assertCompilesTo( "STA ($44),Y" , 0x91,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     STA $44       $A5  2   3		#
		assertCompilesTo( "STA $44" , 0x85,0x44);
	}

	public void testORA()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      ORA $4400     $AD  3   4
		assertCompilesTo( "ORA $4400" , 0x0d, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    ORA $4400,X   $BD  3   4+
		assertCompilesTo( "ORA $4400,X" , 0x1d,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      ORA $4400,Y   $B9  3   4+
		assertCompilesTo( "ORA $4400,Y" , 0x19,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   ORA $44,X     $B5  2   4
		assertCompilesTo( "ORA $44,X" , 0x15,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     ORA #$44      $A9  2   2
		assertCompilesTo( "ORA #$44" , 0x09 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      ORA ($44,X)   $A1  2   6
		assertCompilesTo( "ORA ($44,X)" , 0x01,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      ORA ($44),Y   $B1  2   5+
		assertCompilesTo( "ORA ($44),Y" , 0x11,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     ORA $44       $A5  2   3		#
		assertCompilesTo( "ORA $44" , 0x05,0x44);
	}

	public void testAND()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     AND #$44      $A9  2   2
		assertCompilesTo( "AND #$44" , 0x29 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     AND $44       $A5  2   3		#
		assertCompilesTo( "AND $44" , 0x25,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   AND $44,X     $B5  2   4
		assertCompilesTo( "AND $44,X" , 0x35,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      AND $4400     $AD  3   4
		assertCompilesTo( "AND $4400" , 0x2d, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    AND $4400,X   $BD  3   4+
		assertCompilesTo( "AND $4400,X" , 0x3d,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      AND $4400,Y   $B9  3   4+
		assertCompilesTo( "AND $4400,Y" , 0x39,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      AND ($44,X)   $A1  2   6
		assertCompilesTo( "AND ($44,X)" , 0x21,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      AND ($44),Y   $B1  2   5+
		assertCompilesTo( "AND ($44),Y" , 0x31,0x44);
	}

	public void testEOR()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     EOR #$44      $A9  2   2
		assertCompilesTo( "EOR #$44" , 0x49 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     EOR $44       $A5  2   3		#
		assertCompilesTo( "EOR $44" , 0x45,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   EOR $44,X     $B5  2   4
		assertCompilesTo( "EOR $44,X" , 0x55,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      EOR $4400     $AD  3   4
		assertCompilesTo( "EOR $4400" , 0x4d, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    EOR $4400,X   $BD  3   4+
		assertCompilesTo( "EOR $4400,X" , 0x5d,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      EOR $4400,Y   $B9  3   4+
		assertCompilesTo( "EOR $4400,Y" , 0x59,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      EOR ($44,X)   $A1  2   6
		assertCompilesTo( "EOR ($44,X)" , 0x41,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      EOR ($44),Y   $B1  2   5+
		assertCompilesTo( "EOR ($44),Y" , 0x51,0x44);
	}

	public void testADC()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     ADC #$44      $A9  2   2
		assertCompilesTo( "ADC #$44" , 0x69 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     ADC $44       $A5  2   3		#
		assertCompilesTo( "ADC $44" , 0x65,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   ADC $44,X     $B5  2   4
		assertCompilesTo( "ADC $44,X" , 0x75,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      ADC $4400     $AD  3   4
		assertCompilesTo( "ADC $4400" , 0x6d, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    ADC $4400,X   $BD  3   4+
		assertCompilesTo( "ADC $4400,X" , 0x7d,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      ADC $4400,Y   $B9  3   4+
		assertCompilesTo( "ADC $4400,Y" , 0x79,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      ADC ($44,X)   $A1  2   6
		assertCompilesTo( "ADC ($44,X)" , 0x61,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      ADC ($44),Y   $B1  2   5+
		assertCompilesTo( "ADC ($44),Y" , 0x71,0x44);
	}

	public void testCMP()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     CMP #$44      $A9  2   2
		assertCompilesTo( "CMP #$44" , 0xc9 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     CMP $44       $A5  2   3		#
		assertCompilesTo( "CMP $44" , 0xc5,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   CMP $44,X     $B5  2   4
		assertCompilesTo( "CMP $44,X" , 0xd5,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      CMP $4400     $AD  3   4
		assertCompilesTo( "CMP $4400" , 0xcd, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    CMP $4400,X   $BD  3   4+
		assertCompilesTo( "CMP $4400,X" , 0xdd,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      CMP $4400,Y   $B9  3   4+
		assertCompilesTo( "CMP $4400,Y" , 0xd9,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      CMP ($44,X)   $A1  2   6
		assertCompilesTo( "CMP ($44,X)" , 0xc1,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      CMP ($44),Y   $B1  2   5+
		assertCompilesTo( "CMP ($44),Y" , 0xd1,0x44);
	}

	public void testSBC()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     SBC #$44      $A9  2   2
		assertCompilesTo( "SBC #$44" , 0xe9 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     SBC $44       $A5  2   3		#
		assertCompilesTo( "SBC $44" , 0xe5,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   SBC $44,X     $B5  2   4
		assertCompilesTo( "SBC $44,X" , 0xf5,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      SBC $4400     $AD  3   4
		assertCompilesTo( "SBC $4400" , 0xed, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    SBC $4400,X   $BD  3   4+
		assertCompilesTo( "SBC $4400,X" , 0xfd,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      SBC $4400,Y   $B9  3   4+
		assertCompilesTo( "SBC $4400,Y" , 0xf9,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      SBC ($44,X)   $A1  2   6
		assertCompilesTo( "SBC ($44,X)" , 0xe1,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      SBC ($44),Y   $B1  2   5+
		assertCompilesTo( "SBC ($44),Y" , 0xf1,0x44);
	}

	public void testASL()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   ASL A         $A9  1   2
		assertCompilesTo( "ASL" , 0x0a );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     ASL $44       $A5  2   3		#
		assertCompilesTo( "ASL $44" , 0x06,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   ASL $44,X     $B5  2   4
		assertCompilesTo( "ASL $44,X" , 0x16,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      ASL $4400     $AD  3   4
		assertCompilesTo( "ASL $4400" , 0x0e, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    ASL $4400,X   $BD  3   4+
		assertCompilesTo( "ASL $4400,X" , 0x1e,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     SBC #$44      $A9  2   2
		assertDoesNotCompile( "ASL #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      SBC $4400,Y   $B9  3   4+
		assertDoesNotCompile( "ASL $4400,Y");

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      SBC ($44,X)   $A1  2   6
		assertDoesNotCompile( "ASL ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      SBC ($44),Y   $B1  2   5+
		assertDoesNotCompile( "ASL ($44),Y" );
	}

	public void testROL()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   ROL A         $A9  1   2
		assertCompilesTo( "ROL" , 0x2a );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     ROL $44       $A5  2   3		#
		assertCompilesTo( "ROL $44" , 0x26,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   ROL $44,X     $B5  2   4
		assertCompilesTo( "ROL $44,X" , 0x36,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      ROL $4400     $AD  3   4
		assertCompilesTo( "ROL $4400" , 0x2e, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    ROL $4400,X   $BD  3   4+
		assertCompilesTo( "ROL $4400,X" , 0x3e,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     SBC #$44      $A9  2   2
		assertDoesNotCompile( "ROL #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      SBC $4400,Y   $B9  3   4+
		assertDoesNotCompile( "ROL $4400,Y");

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      SBC ($44,X)   $A1  2   6
		assertDoesNotCompile( "ROL ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      SBC ($44),Y   $B1  2   5+
		assertDoesNotCompile( "ROL ($44),Y" );
	}

	public void testLSR()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   LSR A         $A9  1   2
		assertCompilesTo( "LSR" , 0x4a );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     LSR $44       $A5  2   3		#
		assertCompilesTo( "LSR $44" , 0x46,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   LSR $44,X     $B5  2   4
		assertCompilesTo( "LSR $44,X" , 0x56,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      LSR $4400     $AD  3   4
		assertCompilesTo( "LSR $4400" , 0x4e, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    LSR $4400,X   $BD  3   4+
		assertCompilesTo( "LSR $4400,X" , 0x5e,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     SBC #$44      $A9  2   2
		assertDoesNotCompile( "LSR #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      SBC $4400,Y   $B9  3   4+
		assertDoesNotCompile( "LSR $4400,Y");

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      SBC ($44,X)   $A1  2   6
		assertDoesNotCompile( "LSR ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      SBC ($44),Y   $B1  2   5+
		assertDoesNotCompile( "LSR ($44),Y" );
	}

	public void testROR()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   ROR A         $A9  1   2
		assertCompilesTo( "ROR" , 0x6a );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     ROR $44       $A5  2   3		#
		assertCompilesTo( "ROR $44" , 0x66,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   ROR $44,X     $B5  2   4
		assertCompilesTo( "ROR $44,X" , 0x76,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      ROR $4400     $AD  3   4
		assertCompilesTo( "ROR $4400" , 0x6e, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    ROR $4400,X   $BD  3   4+
		assertCompilesTo( "ROR $4400,X" , 0x7e,0x00,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     ROR #$44      $A9  2   2
		assertDoesNotCompile( "ROR #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      ROR $4400,Y   $B9  3   4+
		assertDoesNotCompile( "ROR $4400,Y");

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      ROR ($44,X)   $A1  2   6
		assertDoesNotCompile( "ROR ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      ROR ($44),Y   $B1  2   5+
		assertDoesNotCompile( "ROR ($44),Y" );
	}

	public void testBCSForward() {
		// BCC   $0013     ; 000e:  90 05    ..
		assertCompilesTo( "*= $0e\n"
				+ "BCC $0015" , 0x90,0x05);
	}
	public void testSTX()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      STX $4400,Y   $B9  3   4+
		assertDoesNotCompile( "STX $4400,Y");

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   STX A         $A9  1   2
		assertDoesNotCompile( "STX" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     STX $44       $A5  2   3		#
		assertCompilesTo( "STX $44" , 0x86,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   STX $44,X     $B5  2   4
		assertDoesNotCompile( "STX $44,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      STX $4400     $AD  3   4
		assertCompilesTo( "STX $4400" , 0x8e, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    STX $4400,X   $BD  3   4+
		assertDoesNotCompile( "STX $4400,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     STX #$44      $A9  2   2
		assertDoesNotCompile( "STX #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      STX ($44,X)   $A1  2   6
		assertDoesNotCompile( "STX ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      STX ($44),Y   $B1  2   5+
		assertDoesNotCompile( "STX ($44),Y" );
	}

	public void testLDX()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     LDX #$44      $A9  2   2
		assertCompilesTo( "LDX #$44" , 0xa2 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     LDX $44       $A5  2   3		#
		assertCompilesTo( "LDX $44" , 0xa6,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,Y   LDX $44,X     $B5  2   4
		assertCompilesTo( "LDX $44,Y" , 0xb6,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      LDX $4400     $AD  3   4
		assertCompilesTo( "LDX $4400" , 0xae, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      LDX $4400,Y   $B9  3   4+
		assertCompilesTo( "LDX $4400,Y" , 0xbe , 0x00 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   LDX A         $A9  1   2
		assertDoesNotCompile( "LDX" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   LDX $44,X     $B5  2   4
		assertDoesNotCompile( "LDX $44,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    LDX $4400,X   $BD  3   4+
		assertDoesNotCompile( "LDX $4400,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      LDX ($44,X)   $A1  2   6
		assertDoesNotCompile( "LDX ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      LDX ($44),Y   $B1  2   5+
		assertDoesNotCompile( "LDX ($44),Y" );
	}

	public void testDEC()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     DEC $44       $A5  2   3		#
		assertCompilesTo( "DEC $44" , 0xc6,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   DEC $44,X     $B5  2   4
		assertCompilesTo( "DEC $44,X" , 0xd6 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      DEC $4400     $AD  3   4
		assertCompilesTo( "DEC $4400" , 0xce, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    DEC $4400,X   $BD  3   4+
		assertCompilesTo( "DEC $4400,X" , 0xde , 0x00 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      DEC $4400,Y   $B9  3   4+
		assertDoesNotCompile( "DEC $4400,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,Y   DEC $44,X     $B5  2   4
		assertDoesNotCompile( "DEC $44,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     DEC #$44      $A9  2   2
		assertDoesNotCompile( "DEC #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   DEC A         $A9  1   2
		assertDoesNotCompile( "DEC" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      DEC ($44,X)   $A1  2   6
		assertDoesNotCompile( "DEC ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      DEC ($44),Y   $B1  2   5+
		assertDoesNotCompile( "DEC ($44),Y" );
	}

	public void testINC()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     INC $44       $A5  2   3		#
		assertCompilesTo( "INC $44" , 0xe6,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   INC $44,X     $B5  2   4
		assertCompilesTo( "INC $44,X" , 0xf6 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      INC $4400     $AD  3   4
		assertCompilesTo( "INC $4400" , 0xee, 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    INC $4400,X   $BD  3   4+
		assertCompilesTo( "INC $4400,X" , 0xfe , 0x00 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      INC $4400,Y   $B9  3   4+
		assertDoesNotCompile( "INC $4400,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,Y   INC $44,X     $B5  2   4
		assertDoesNotCompile( "INC $44,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     INC #$44      $A9  2   2
		assertDoesNotCompile( "INC #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   INC A         $A9  1   2
		assertDoesNotCompile( "INC" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      INC ($44,X)   $A1  2   6
		assertDoesNotCompile( "INC ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      INC ($44),Y   $B1  2   5+
		assertDoesNotCompile( "INC ($44),Y" );
	}

	public void testBIT()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     BIT $44       $A5  2   3		#
		assertCompilesTo( "BIT $44" , 0x24,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   BIT $44,X     $B5  2   4
		assertDoesNotCompile( "BIT $44,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      BIT $4400     $AD  3   4
		assertCompilesTo( "BIT $4400" , 0x2c , 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    BIT $4400,X   $BD  3   4+
		assertDoesNotCompile( "BIT $4400,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      BIT $4400,Y   $B9  3   4+
		assertDoesNotCompile( "BIT $4400,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,Y   BIT $44,X     $B5  2   4
		assertDoesNotCompile( "BIT $44,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     BIT #$44      $A9  2   2
		assertDoesNotCompile( "BIT #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   BIT A         $A9  1   2
		assertDoesNotCompile( "BIT" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      BIT ($44,X)   $A1  2   6
		assertDoesNotCompile( "BIT ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      BIT ($44),Y   $B1  2   5+
		assertDoesNotCompile( "BIT ($44),Y" );
	}

	public void testJMP() {
		/*
		 * Indirect      JMP ($5597)   $6C  3   5
		 * Absolute      JMP $5597     $4C  3   3
		 */
		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     JMP $44       $A5  2   3		#
		assertCompilesTo( "JMP $44" , 0x4c,0x44 , 0x00 );
		assertCompilesTo( "JMP $4400" , 0x4c,0x00 , 0x44 );

		assertCompilesTo( "JMP ($44)" , 0x6c,0x44 , 0x00 );
		assertCompilesTo( "JMP ($4400)" , 0x6c,0x00 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   JMP $44,X     $B5  2   4
		assertDoesNotCompile( "JMP $44,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    JMP $4400,X   $BD  3   4+
		assertDoesNotCompile( "JMP $4400,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      JMP $4400,Y   $B9  3   4+
		assertDoesNotCompile( "JMP $4400,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,Y   JMP $44,X     $B5  2   4
		assertDoesNotCompile( "JMP $44,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     JMP #$44      $A9  2   2
		assertDoesNotCompile( "JMP #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   JMP A         $A9  1   2
		assertDoesNotCompile( "JMP" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      JMP ($44,X)   $A1  2   6
		assertDoesNotCompile( "JMP ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      JMP ($44),Y   $B1  2   5+
		assertDoesNotCompile( "JMP ($44),Y" );
	}

	public void testSTY()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     STY $44       $A5  2   3		#
		assertCompilesTo( "STY $44" , 0x84,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   STY $44,X     $B5  2   4
		assertCompilesTo( "STY $44,X" , 0x94 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      STY $4400     $AD  3   4
		assertCompilesTo( "STY $4400" , 0x8c , 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    STY $4400,X   $BD  3   4+
		assertDoesNotCompile( "STY $4400,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      STY $4400,Y   $B9  3   4+
		assertDoesNotCompile( "STY $4400,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,Y   STY $44,X     $B5  2   4
		assertDoesNotCompile( "STY $44,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     STY #$44      $A9  2   2
		assertDoesNotCompile( "STY #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   STY A         $A9  1   2
		assertDoesNotCompile( "STY" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      STY ($44,X)   $A1  2   6
		assertDoesNotCompile( "STY ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      STY ($44),Y   $B1  2   5+
		assertDoesNotCompile( "STY ($44),Y" );
	}

	public void testLDY()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     LDY #$44      $A9  2   2
		assertCompilesTo( "LDY #$44" , 0xa0 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     LDY $44       $A5  2   3		#
		assertCompilesTo( "LDY $44" , 0xa4,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   LDY $44,X     $B5  2   4
		assertCompilesTo( "LDY $44,X" , 0xb4 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      LDY $4400     $AD  3   4
		assertCompilesTo( "LDY $4400" , 0xac , 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    LDY $4400,X   $BD  3   4+
		assertCompilesTo( "LDY $4400,X" , 0xbc , 0x00 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      LDY $4400,Y   $B9  3   4+
		assertDoesNotCompile( "LDY $4400,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,Y   LDY $44,X     $B5  2   4
		assertDoesNotCompile( "LDY $44,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   LDY A         $A9  1   2
		assertDoesNotCompile( "LDY" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      LDY ($44,X)   $A1  2   6
		assertDoesNotCompile( "LDY ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      LDY ($44),Y   $B1  2   5+
		assertDoesNotCompile( "LDY ($44),Y" );
	}

	public void testCPY()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     CPY #$44      $A9  2   2
		assertCompilesTo( "CPY #$44" , 0xc0 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     CPY $44       $A5  2   3		#
		assertCompilesTo( "CPY $44" , 0xc4,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      CPY $4400     $AD  3   4
		assertCompilesTo( "CPY $4400" , 0xcc , 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   CPY $44,X     $B5  2   4
		assertDoesNotCompile( "CPY $44,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    CPY $4400,X   $BD  3   4+
		assertDoesNotCompile( "CPY $4400,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      CPY $4400,Y   $B9  3   4+
		assertDoesNotCompile( "CPY $4400,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,Y   CPY $44,X     $B5  2   4
		assertDoesNotCompile( "CPY $44,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   CPY A         $A9  1   2
		assertDoesNotCompile( "CPY" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      CPY ($44,X)   $A1  2   6
		assertDoesNotCompile( "CPY ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      CPY ($44),Y   $B1  2   5+
		assertDoesNotCompile( "CPY ($44),Y" );
	}

	public void testCPX()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     CPX #$44      $A9  2   2
		assertCompilesTo( "CPX #$44" , 0xe0 , 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     CPX $44       $A5  2   3		#
		assertCompilesTo( "CPX $44" , 0xe4,0x44);

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      CPX $4400     $AD  3   4
		assertCompilesTo( "CPX $4400" , 0xec , 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   CPX $44,X     $B5  2   4
		assertDoesNotCompile( "CPX $44,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    CPX $4400,X   $BD  3   4+
		assertDoesNotCompile( "CPX $4400,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      CPX $4400,Y   $B9  3   4+
		assertDoesNotCompile( "CPX $4400,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,Y   CPX $44,X     $B5  2   4
		assertDoesNotCompile( "CPX $44,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   CPX A         $A9  1   2
		assertDoesNotCompile( "CPX" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      CPX ($44,X)   $A1  2   6
		assertDoesNotCompile( "CPX ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      CPX ($44),Y   $B1  2   5+
		assertDoesNotCompile( "CPX ($44),Y" );
	}

	public void testConditionalBranches()
	{
		 // BPL	BMI	BVC	BVS	BCC	BCS BNE	BEQ
		 // 10	30	50	70	90	B0 	D0	F0
		final byte relOffset = 4;

		// binary gets loaded at $0000
		assertCompilesTo( "BPL $"+Integer.toHexString( PRG_LOAD_ADDRESS + relOffset ) , 0x10 , 0x02 );
		assertCompilesTo( "BMI $"+Integer.toHexString( PRG_LOAD_ADDRESS + relOffset ) , 0x30 , 0x02 );
		assertCompilesTo( "BVC $"+Integer.toHexString( PRG_LOAD_ADDRESS + relOffset ) , 0x50 , 0x02 );
		assertCompilesTo( "BVS $"+Integer.toHexString( PRG_LOAD_ADDRESS + relOffset ) , 0x70 , 0x02 );
		assertCompilesTo( "BCC $"+Integer.toHexString( PRG_LOAD_ADDRESS + relOffset ) , 0x90 , 0x02 );
		assertCompilesTo( "BCS $"+Integer.toHexString( PRG_LOAD_ADDRESS + relOffset ) , 0xb0 , 0x02 );
		assertCompilesTo( "BNE $"+Integer.toHexString( PRG_LOAD_ADDRESS + relOffset ) , 0xd0 , 0x02 );
		assertCompilesTo( "BEQ $"+Integer.toHexString( PRG_LOAD_ADDRESS + relOffset ) , 0xf0 , 0x02 );
	}

	public void testBRK()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     BRK #$44      $A9  2   2
		assertDoesNotCompile( "BRK #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     BRK $44       $A5  2   3		#
		assertDoesNotCompile( "BRK $44" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      BRK $4400     $AD  3   4
		assertDoesNotCompile( "BRK $4400" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   BRK $44,X     $B5  2   4
		assertDoesNotCompile( "BRK $44,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    BRK $4400,X   $BD  3   4+
		assertDoesNotCompile( "BRK $4400,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      BRK $4400,Y   $B9  3   4+
		assertDoesNotCompile( "BRK $4400,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,Y   BRK $44,X     $B5  2   4
		assertDoesNotCompile( "BRK $44,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   BRK A         $A9  1   2
		assertCompilesTo( "BRK" , 0x00 );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      BRK ($44,X)   $A1  2   6
		assertDoesNotCompile( "BRK ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      BRK ($44),Y   $B1  2   5+
		assertDoesNotCompile( "BRK ($44),Y" );
	}

	public void testJSR()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     JSR #$44      $A9  2   2
		assertDoesNotCompile( "JSR #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     JSR $44       $A5  2   3		#
		assertCompilesTo( "JSR $44" , 0x20 , 0x44, 0x00 );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      JSR $4400     $AD  3   4
		assertCompilesTo( "JSR $4400" , 0x20 , 0x00, 0x44 );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   JSR $44,X     $B5  2   4
		assertDoesNotCompile( "JSR $44,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    JSR $4400,X   $BD  3   4+
		assertDoesNotCompile( "JSR $4400,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      JSR $4400,Y   $B9  3   4+
		assertDoesNotCompile( "JSR $4400,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,Y   JSR $44,X     $B5  2   4
		assertDoesNotCompile( "JSR $44,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   JSR A         $A9  1   2
		assertDoesNotCompile( "JSR" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      JSR ($44,X)   $A1  2   6
		assertDoesNotCompile( "JSR ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      JSR ($44),Y   $B1  2   5+
		assertDoesNotCompile( "JSR ($44),Y" );
	}

	public void testRTI()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     RTI #$44      $A9  2   2
		assertDoesNotCompile( "RTI #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     RTI $44       $A5  2   3		#
		assertDoesNotCompile( "RTI $44" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      RTI $4400     $AD  3   4
		assertDoesNotCompile( "RTI $4400" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   RTI $44,X     $B5  2   4
		assertDoesNotCompile( "RTI $44,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    RTI $4400,X   $BD  3   4+
		assertDoesNotCompile( "RTI $4400,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      RTI $4400,Y   $B9  3   4+
		assertDoesNotCompile( "RTI $4400,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,Y   RTI $44,X     $B5  2   4
		assertDoesNotCompile( "RTI $44,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   RTI A         $A9  1   2
		assertCompilesTo( "RTI" , 0x40 );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      RTI ($44,X)   $A1  2   6
		assertDoesNotCompile( "RTI ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      RTI ($44),Y   $B1  2   5+
		assertDoesNotCompile( "RTI ($44),Y" );
	}

	public void testRTS()
	{
		// MODE           SYNTAX         HEX LEN TIM
		// # Immediate     RTS #$44      $A9  2   2
		assertDoesNotCompile( "RTS #$44" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page     RTS $44       $A5  2   3		#
		assertDoesNotCompile( "RTS $44" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute      RTS $4400     $AD  3   4
		assertDoesNotCompile( "RTS $4400" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,X   RTS $44,X     $B5  2   4
		assertDoesNotCompile( "RTS $44,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,X    RTS $4400,X   $BD  3   4+
		assertDoesNotCompile( "RTS $4400,X" );

		// MODE           SYNTAX         HEX LEN TIM
		// Absolute,Y      RTS $4400,Y   $B9  3   4+
		assertDoesNotCompile( "RTS $4400,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// Zero Page,Y   RTS $44,X     $B5  2   4
		assertDoesNotCompile( "RTS $44,Y" );

		// MODE           SYNTAX         HEX LEN TIM
		// # Accumulator   RTS A         $A9  1   2
		assertCompilesTo( "RTS" , 0x60 );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,X      RTS ($44,X)   $A1  2   6
		assertDoesNotCompile( "RTS ($44,X)" );

		// MODE           SYNTAX         HEX LEN TIM
		// Indirect,Y      RTS ($44),Y   $B1  2   5+
		assertDoesNotCompile( "RTS ($44),Y" );
	}

	public void testOpcodesWithImmediateModeOnly()
	{
		assertCompilesTo( "PHP" , 0x08 );
		assertCompilesTo( "PLP" , 0x28 );
		assertCompilesTo( "PHA" , 0x48 );
		assertCompilesTo( "PLA" , 0x68 );
		assertCompilesTo( "DEY" , 0x88 );
		assertCompilesTo( "TAY" , 0xa8 );
		assertCompilesTo( "INY" , 0xc8 );
		assertCompilesTo( "INX" , 0xe8 );
		assertCompilesTo( "CLC" , 0x18 );
		assertCompilesTo( "SEC" , 0x38 );
		assertCompilesTo( "CLI" , 0x58 );

		assertCompilesTo( "SEI" , 0x78 );
		assertCompilesTo( "TYA" , 0x98 );
		assertCompilesTo( "CLV" , 0xb8 );
		assertCompilesTo( "CLD" , 0xd8 );
		assertCompilesTo( "SED" , 0xf8 );
		assertCompilesTo( "TXA" , 0x8a );
		assertCompilesTo( "TXS" , 0x9a );
		assertCompilesTo( "TAX" , 0xaa );
		assertCompilesTo( "TSX" , 0xba );
		assertCompilesTo( "DEX" , 0xca );
		assertCompilesTo( "NOP" , 0xea );
	}
}
