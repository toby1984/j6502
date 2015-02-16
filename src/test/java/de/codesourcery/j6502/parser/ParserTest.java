package de.codesourcery.j6502.parser;

import junit.framework.TestCase;
import de.codesourcery.j6502.assembler.AddressingMode;
import de.codesourcery.j6502.parser.ast.AST;
import de.codesourcery.j6502.parser.ast.AbsoluteOperand;
import de.codesourcery.j6502.parser.ast.CommentNode;
import de.codesourcery.j6502.parser.ast.ImmediateOperand;
import de.codesourcery.j6502.parser.ast.IndirectOperandX;
import de.codesourcery.j6502.parser.ast.IndirectOperandY;
import de.codesourcery.j6502.parser.ast.InstructionNode;
import de.codesourcery.j6502.parser.ast.NumberLiteral;
import de.codesourcery.j6502.parser.ast.NumberLiteral.Notation;
import de.codesourcery.j6502.parser.ast.Statement;

public class ParserTest extends TestCase {

	private Parser parser;

	private AST ast;

	private void parse(String s) {
		ast = new Parser( new Lexer(new Scanner(s) ) ).parse();
	}

	public void testParseEmptySource1() {
		parse("");
		assertNotNull(ast);
		assertEquals(0,ast.children.size());
	}

	public void testParseEmptySource2() {
		parse("     \n\n\n    ");
		assertNotNull(ast);
		assertEquals(0,ast.children.size());
	}

	public void testParseCommentLines() {
		parse("; first line\n\n\n"+
	          "; second line\n");

		assertNotNull(ast);
		assertEquals(2,ast.children.size());
		System.out.println("GOT:\n"+ast);
		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof CommentNode);
		assertEquals("first line" , ((CommentNode) ast.child(0).child(0) ).comment) ;

		assertTrue( ast.child(1) instanceof Statement);
		assertTrue( ast.child(1).child(0) instanceof CommentNode);
		assertEquals("second line" , ((CommentNode) ast.child(1).child(0) ).comment) ;
	}

	public void testParseASL()
	{
		parse("ASL");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof InstructionNode);
		assertEquals( Opcode.ASL , ((InstructionNode) ast.child(0).child(0)).opcode );
		assertEquals( 0 , ast.child(0).child(0).getChildCount() );
	}

	public void testParseLDAImmediateDecimal()
	{
		parse("LDA #123");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof InstructionNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof ImmediateOperand);
		assertTrue( ast.child(0).child(0).child(0).child(0) instanceof NumberLiteral);

		assertEquals( Opcode.LDA, ((InstructionNode) ast.child(0).child(0)).opcode );

		final NumberLiteral lit = (NumberLiteral) ast.child(0).child(0).child(0).child(0);
		assertEquals( (short) 123 , lit.value );
		assertEquals( Notation.DECIMAL , lit.notation );
	}

	public void testParseLDAImmediateHexadecimal()
	{
		parse("LDA #$ff");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof InstructionNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof ImmediateOperand);
		assertTrue( ast.child(0).child(0).child(0).child(0) instanceof NumberLiteral);

		assertEquals( Opcode.LDA, ((InstructionNode) ast.child(0).child(0)).opcode );

		final NumberLiteral lit = (NumberLiteral) ast.child(0).child(0).child(0).child(0);
		assertEquals( (short) 255 , lit.value );
		assertEquals( Notation.HEXADECIMAL , lit.notation );
	}

	public void testParseLDAAbsoluteDecimal()
	{
		parse("LDA 123");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof InstructionNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof AbsoluteOperand);
		assertTrue( ast.child(0).child(0).child(0).child(0) instanceof NumberLiteral);

		assertEquals( Opcode.LDA, ((InstructionNode) ast.child(0).child(0)).opcode );

		final NumberLiteral lit = (NumberLiteral) ast.child(0).child(0).child(0).child(0);
		assertEquals( (short) 123 , lit.value );
		assertEquals( Notation.DECIMAL , lit.notation );
	}

	public void testParseLDAAbsoluteHexadecimal()
	{
		parse("LDA $da00");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof InstructionNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof AbsoluteOperand);
		assertTrue( ast.child(0).child(0).child(0).child(0) instanceof NumberLiteral);

		final InstructionNode ins = (InstructionNode) ast.child(0).child(0);
		assertEquals( AddressingMode.ABSOLUTE , ins.getAddressingMode() );
		assertEquals( Opcode.LDA, ins.opcode );

		final NumberLiteral lit = (NumberLiteral) ast.child(0).child(0).child(0).child(0);
		assertEquals( (short) 0xda00 , lit.value );
		assertEquals( Notation.HEXADECIMAL , lit.notation );
	}

	public void testParseLDAZeroPage()
	{
		parse("LDA $44");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof InstructionNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof AbsoluteOperand);
		assertTrue( ast.child(0).child(0).child(0).child(0) instanceof NumberLiteral);

		final InstructionNode ins = (InstructionNode) ast.child(0).child(0);
		assertEquals( AddressingMode.ZERO_PAGE , ins.getAddressingMode() );
		assertEquals( Opcode.LDA, ins.opcode );

		final NumberLiteral lit = (NumberLiteral) ast.child(0).child(0).child(0).child(0);
		assertEquals( (short) 0x44 , lit.value );
		assertEquals( Notation.HEXADECIMAL , lit.notation );
	}

	public void testParseLDAAbsoluteX()
	{
		parse("LDA $4400,X");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof InstructionNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof AbsoluteOperand);
		assertTrue( ast.child(0).child(0).child(0).child(0) instanceof NumberLiteral);

		final InstructionNode ins = (InstructionNode) ast.child(0).child(0);
		assertEquals( AddressingMode.ABSOLUTE_INDEXED_X , ins.getAddressingMode() );
		assertEquals( Opcode.LDA, ins.opcode );

		final NumberLiteral lit = (NumberLiteral) ast.child(0).child(0).child(0).child(0);
		assertEquals( (short) 0x4400 , lit.value );
		assertEquals( Notation.HEXADECIMAL , lit.notation );
	}

	public void testParseLDAAbsoluteY()
	{
		parse("LDA $4400,Y");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof InstructionNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof AbsoluteOperand);
		assertTrue( ast.child(0).child(0).child(0).child(0) instanceof NumberLiteral);

		final InstructionNode ins = (InstructionNode) ast.child(0).child(0);
		assertEquals( AddressingMode.ABSOLUTE_INDEXED_Y , ins.getAddressingMode() );
		assertEquals( Opcode.LDA, ins.opcode );

		final NumberLiteral lit = (NumberLiteral) ast.child(0).child(0).child(0).child(0);
		assertEquals( (short) 0x4400 , lit.value );
		assertEquals( Notation.HEXADECIMAL , lit.notation );
	}

	public void testParseLDAAbsoluteZeroPageX()
	{
		parse("LDA $44,X");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof InstructionNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof AbsoluteOperand);
		assertTrue( ast.child(0).child(0).child(0).child(0) instanceof NumberLiteral);

		final InstructionNode ins = (InstructionNode) ast.child(0).child(0);
		assertEquals( AddressingMode.ZERO_PAGE_X , ins.getAddressingMode() );
		assertEquals( Opcode.LDA, ins.opcode );

		final NumberLiteral lit = (NumberLiteral) ast.child(0).child(0).child(0).child(0);
		assertEquals( (short) 0x44 , lit.value );
		assertEquals( Notation.HEXADECIMAL , lit.notation );
	}

	public void testParseLDAImmediate()
	{
		parse("LDA #$12");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof InstructionNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof ImmediateOperand);
		assertTrue( ast.child(0).child(0).child(0).child(0) instanceof NumberLiteral);

		final InstructionNode ins = (InstructionNode) ast.child(0).child(0);
		assertEquals( AddressingMode.IMMEDIATE , ins.getAddressingMode() );
		assertEquals( Opcode.LDA, ins.opcode );

		final NumberLiteral lit = (NumberLiteral) ast.child(0).child(0).child(0).child(0);
		assertEquals( (short) 0x12 , lit.value );
		assertEquals( Notation.HEXADECIMAL , lit.notation );
	}

	public void testParseLDAIndirectIndexedX()
	{
		parse("LDA ( $12 , X )");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof InstructionNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof IndirectOperandX);
		assertTrue( ast.child(0).child(0).child(0).child(0) instanceof NumberLiteral);

		final InstructionNode ins = (InstructionNode) ast.child(0).child(0);
		assertEquals( AddressingMode.INDEXED_INDIRECT_X , ins.getAddressingMode() );
		assertEquals( Opcode.LDA, ins.opcode );

		final NumberLiteral lit = (NumberLiteral) ast.child(0).child(0).child(0).child(0);
		assertEquals( (short) 0x12 , lit.value );
		assertEquals( Notation.HEXADECIMAL , lit.notation );
	}

	public void testParseLDAIndexedIndirectY()
	{
		parse("LDA ( $12 ) , Y");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof InstructionNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof IndirectOperandY);
		assertTrue( ast.child(0).child(0).child(0).child(0) instanceof NumberLiteral);

		final InstructionNode ins = (InstructionNode) ast.child(0).child(0);
		assertEquals( AddressingMode.INDIRECT_INDEXED_Y , ins.getAddressingMode() );
		assertEquals( Opcode.LDA, ins.opcode );

		final NumberLiteral lit = (NumberLiteral) ast.child(0).child(0).child(0).child(0);
		assertEquals( (short) 0x12 , lit.value );
		assertEquals( Notation.HEXADECIMAL , lit.notation );
	}


}