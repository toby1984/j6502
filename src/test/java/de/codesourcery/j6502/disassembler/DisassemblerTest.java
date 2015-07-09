package de.codesourcery.j6502.disassembler;

import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Random;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.assembler.Assembler;
import de.codesourcery.j6502.assembler.AssemblerTest;
import de.codesourcery.j6502.assembler.exceptions.ITextLocationAware;
import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Parser;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.disassembler.Disassembler.Line;
import de.codesourcery.j6502.utils.HexDump;
import de.codesourcery.j6502.utils.SourceHelper;
import de.codesourcery.j6502.utils.SourceHelper.TextLocation;
import junit.framework.TestCase;
import sun.misc.IOUtils;

@SuppressWarnings("restriction")
public class DisassemblerTest extends TestCase {

	public void testSTX1() {

		final byte[] data = new byte[] { (byte) 0x9e ,0x1c ,(byte) 0x91 , 0x12 };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 4 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  .byte $9e\n" +
				"0001:  .byte $1c\n" +
				"0002:  STA   ($12) , Y\n";
		assertEquals( expected , buffer.toString());
	}

	public void testBranchForward() {

		// BPL +10
		final byte[] data = new byte[] { (byte) 0x10 , 10  };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 2 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  BPL   $000c\n";
		assertEquals( expected , buffer.toString());
	}

	public void testBranchBackwards() {

		// BPL -6
		final byte[] data = new byte[] { (byte) 0xea , (byte) 0xea ,(byte) 0xea ,(byte) 0xea ,0x10 , -6  };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 6 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  NOP\n" +
				"0001:  NOP\n" +
				"0002:  NOP\n" +
				"0003:  NOP\n" +
				"0004:  BPL   $0000\n";
		assertEquals( expected , buffer.toString());
	}

	public static void main(String[] args) throws IOException {

		final InputStream stream = DisassemblerTest.class.getResourceAsStream("/roms/basic_v2.rom");
		assertNotNull(stream);
		final byte[] data = IOUtils.readFully( stream , -1 , true);
		final Disassembler dis = new Disassembler();
		dis.setAnnotate( true );
		dis.disassemble( 0xa000 , data , 0x38a , data.length-0x38a , new FileWriter("/tmp/basic.asm") );
	}

	public void testASLWithAccu() {

		final byte[] data = new byte[] { (byte) 0x44  };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 1 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  .byte $44\n";
		assertEquals( expected , buffer.toString());
	}

	public void testBVS() {
		// 		JMP  ($8507); 0014:   70 07 p.
		final byte[] data = new byte[] { (byte) 0x70, 0x07};
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 2 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  BVS   $0009\n";
		assertEquals( expected , buffer.toString());
	}

	// BCS  $5b; 0110:   90 5b    .[
	public void testBVS2()
	{
		// 		JMP  ($8507); 0014:   70 07 p.
		final byte[] data = new byte[] { (byte) 0xea, (byte) 0x70, 0x07};
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 3 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  NOP\n"+
								 "0001:  BVS   $000a\n";
		assertEquals( expected , buffer.toString());
	}

	public void testORA() {
		// ORA   $5993     ; 0027:  0d 93 59 ..Y
		// Absolute      ORA $4400     $0D  3   4
		final byte[] data = new byte[] { (byte) 0x0d, (byte) 0x93, 0x59};
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 3 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  ORA   $5993\n";
		assertEquals( expected , buffer.toString());
	}

	public void testLSRZeroPage()
	{
		// LSR   $00cb     ; 00ea:  4e cb 00 N..
		final byte[] data = new byte[] { (byte) 0x4e, (byte) 0xcb, 0x00};
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 3 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  LSR   $00cb\n";
		assertEquals( expected , buffer.toString());
	}

	public void testLDA()
	{
		// LDA   $000a , X ; 001d:  bd 0a 00 ...
		final byte[] data = new byte[] { (byte) 0xbd, (byte) 0x0a, 0x00};
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 3 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  LDA   $000a , X\n";
		assertEquals( expected , buffer.toString());
	}

	public void testNOP() {
		final byte[] data = new byte[] { (byte) 0xea };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 1 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  NOP\n";
		assertEquals( expected , buffer.toString());
	}

	public void testJMPIndirect() {
		// 		final String s = "JMP  $3146; 00d1:   6c 46 31 lF1";
		final byte[] data = new byte[] { (byte) 0x6c, (byte) 0x46, (byte) 0x31 };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 3 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  JMP   ($3146)\n";
		assertEquals( expected , buffer.toString());
	}

	public void testUnknownOp() {
		// JMP   ($9d77)   ; 0164:  7c 77 9d |w.
		final byte[] data = new byte[] { (byte) 0x7c, (byte) 0x77, (byte) 0x9d };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 3 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  .byte $7c\n" +
				"0001:  .byte $77\n" +
				"0002:  .byte $9d\n";
		assertEquals( expected , buffer.toString());
	}

	public void testUnknownOp3() {
		// JMP   ($fc0b)   ; 012c:  64 0b fc d..
		final byte[] data = new byte[] { (byte) 0x64, (byte) 0x0b, (byte) 0xfc };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 3 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  .byte $64\n" +
				"0001:  .byte $0b\n" +
				"0002:  .byte $fc\n";
		assertEquals( expected , buffer.toString());
	}

	public void testUnknownOp2() {
		final byte[] data = new byte[] { (byte) 0x74, (byte) 0xc7, (byte) 0xf7 };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 3 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  .byte $74\n" +
				"0001:  .byte $c7\n" +
				"0002:  .byte $f7\n";
		assertEquals( expected , buffer.toString());
	}

	public void testASL() {

		final byte[] data = new byte[] { (byte) 0x1e, (byte) 0x9e, (byte) 0x5a };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 3 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:  ASL   $5a9e , X\n";
		assertEquals( expected , buffer.toString());
	}

	public void testSTX2() {

		final byte[] data = new byte[] { (byte) 0x96 , (byte) 0x8e };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 2 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		System.out.println( buffer );
	}

	public void testRandom() throws IOException
	{
		long seed = -6364634748321646817L; // System.currentTimeMillis();
		final Random rnd = new Random(seed);
		for ( int i = 0 ; i < 20 ; i++ )
		{
			try {
				doTestRandom(rnd);
			} catch(final Throwable e) {
				System.err.println("********* SEED: "+seed);
				throw e;
			}
			seed = 31*rnd.nextLong()+System.currentTimeMillis();
			rnd.setSeed( seed );
		}
	}

	private void doTestRandom(Random rnd) throws IOException {

		final ByteArrayOutputStream out = new ByteArrayOutputStream();

		for ( int i = 0 ; i < 256 ; i++ )
		{
			out.write( i ); // opcode
			// I intentionally make sure the operands are >= 128 here
			// to avoid ambiguous intepretation of absolute addressing with
			// operands <= 0xff versus zero page addressing.
			//
			// Given something like LDA $000a the assembler
			// the assembler will always prefer the zero-page opcode
			// (because it's shorter & faster) over the absolute addressing one
			// but this will obviously lead to different binary output because
			// the absolute vs zero-page variants of the instruction have
			// different opcodes.
			out.write( 128 + rnd.nextInt(128) ); // low
			out.write( 128 + rnd.nextInt(128) ); // hi
		}

		final byte[] expected = out.toByteArray();

		final HexDump dump = new HexDump();
		System.out.println( dump.dump( (short) 0 , expected , 0 , expected.length ) );

		final Disassembler dis = new Disassembler();
		dis.setWriteAddresses( false );
		dis.setAnnotate( true );

		final List<Line> lines = dis.disassemble( 0 , expected , 0 , expected.length );

		final StringBuilder source = new StringBuilder("*=$0000\n");
		lines.forEach( line -> {
			source.append( line.toString() ).append("\n");
		});

		// parse source

		try( FileWriter writer = new FileWriter("/tmp/test.c64") ) {
			writer.write( source.toString() );
		}

		System.out.println("\n===========\n"+source+"\n==============");

		final Parser parser = new Parser( new Lexer( new Scanner(source.toString() ) ) );
		final AST ast;
		try {
			ast = parser.parse();
		}
		catch(final Exception e)
		{
			maybePrintError(source, e);
			throw e;
		}

		// compile again
		final Assembler asm = new Assembler();
		final byte[] compiled;
		try {
			compiled = asm.assemble( ast );
		}
		catch(final Exception e)
		{
			maybePrintError(source, e);
			e.printStackTrace();
			throw e;
		}

		AssemblerTest.assertArrayEquals( expected , compiled );
	}

	public static void maybePrintError(final StringBuilder source, final Exception e)
	{
		maybePrintError(source.toString(),e);
	}

	public static void maybePrintError(final String source, final Exception e)
	{
		if ( e instanceof ITextLocationAware)
		{
			final SourceHelper helper = new SourceHelper( source );
			final TextLocation location = ((ITextLocationAware) e).getTextLocation( helper );
			if ( location != null )
			{
				final int start = helper.getLineStartingOffset( location );
				final int delta = location.absoluteOffset - start;
				final String line = helper.getLineText( location );
				System.err.println( "Error at "+location );
				System.err.println( line );
				System.err.println( StringUtils.repeat( " " , delta )+"^ "+e.getMessage() );
			}
		}
	}
}
