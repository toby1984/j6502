package de.codesourcery.j6502.disassembler;

import java.io.ByteArrayOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.Random;

import junit.framework.TestCase;

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

public class DisassemblerTest extends TestCase {

	public void testSTX1() {

		final byte[] data = new byte[] { (byte) 0x9e ,0x1c ,(byte) 0x91 , 0x12 };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 4 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:    .byte $9e\n" +
				"0001:    .byte $1c\n" +
				"0002:    STA  ($12) , Y\n";
		assertEquals( expected , buffer.toString());
	}

	public void testASLWithAccu() {

		final byte[] data = new byte[] { (byte) 0x44  };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 1 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:    .byte $44\n";
		assertEquals( expected , buffer.toString());
	}

	public void testBVS() {
		// 		JMP  ($8507); 0014:  70 07 p.
		final byte[] data = new byte[] { (byte) 0x70, 0x07};
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 2 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:    BVS  $0007\n";
		assertEquals( expected , buffer.toString());
	}

	// BCS  $5b; 0110:  90 5b    .[
	public void testBVS2()
	{
		// 		JMP  ($8507); 0014:  70 07 p.
		final byte[] data = new byte[] { (byte) 0xea, (byte) 0x70, 0x07};
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 3 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:    NOP\n"+
								 "0001:    BVS  $0008\n";
		assertEquals( expected , buffer.toString());
	}

	public void testNOP() {
		final byte[] data = new byte[] { (byte) 0xea };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 1 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:    NOP\n";
		assertEquals( expected , buffer.toString());
	}

	public void testJMPIndirect() {
		// 		final String s = "JMP  $3146; 00d1:  6c 46 31 lF1";
		final byte[] data = new byte[] { (byte) 0x6c, (byte) 0x46, (byte) 0x31 };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 3 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:    JMP  ($3146)\n";
		assertEquals( expected , buffer.toString());
	}
	//
	public void testASL() {

		final byte[] data = new byte[] { (byte) 0x1e, (byte) 0x9e, (byte) 0x5a };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 3 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		final String expected = "0000:    ASL  $5a9e , X\n";
		assertEquals( expected , buffer.toString());
	}

	public void testSTX2() {

		final byte[] data = new byte[] { (byte) 0x96 , (byte) 0x8e };
		final Disassembler dis = new Disassembler();
		final StringBuilder buffer = new StringBuilder();
		dis.disassemble( 0 , data , 0 , 2 ).forEach( line -> buffer.append( line.toString()+"\n" ) );
		System.out.println( buffer );

//		final String expected = "0000:    .byte $9e\n" +
//				"0001:    .byte $1c\n" +
//				"0002:    STA  ($12) , Y\n";
//		assertEquals( expected , buffer.toString());
	}

	public void testRandom() throws IOException {

		final ByteArrayOutputStream out = new ByteArrayOutputStream();

		final Random rnd = new Random(0xdeadbeef);
		for ( int i = 0 ; i < 256 ; i++ ) {
			out.write( i );
			out.write( rnd.nextInt(256) );
			out.write( rnd.nextInt(256) );
		}

		final byte[] expected = out.toByteArray();

		final HexDump dump = new HexDump();
		System.out.println( dump.dump( (short) 0 , expected , 0 , expected.length ) );

		final Disassembler dis = new Disassembler();
		dis.setWriteAddresses( false );
		dis.setAnnotate( true );

		final List<Line> lines = dis.disassemble( 0 , expected , 0 , expected.length );

		final StringBuilder source = new StringBuilder();
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

	private void maybePrintError(final StringBuilder source, final Exception e)
	{
		if ( e instanceof ITextLocationAware)
		{
			final SourceHelper helper = new SourceHelper( source.toString() );
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
