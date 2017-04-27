package de.codesourcery.j6502.parser;

import de.codesourcery.j6502.assembler.AddressingMode;
import de.codesourcery.j6502.assembler.parser.Identifier;
import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Opcode;
import de.codesourcery.j6502.assembler.parser.Operator;
import de.codesourcery.j6502.assembler.parser.Parser;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.assembler.parser.ast.AbsoluteOperand;
import de.codesourcery.j6502.assembler.parser.ast.CommentNode;
import de.codesourcery.j6502.assembler.parser.ast.EquNode;
import de.codesourcery.j6502.assembler.parser.ast.IASTNode;
import de.codesourcery.j6502.assembler.parser.ast.IValueNode;
import de.codesourcery.j6502.assembler.parser.ast.ImmediateOperand;
import de.codesourcery.j6502.assembler.parser.ast.IncludeBinaryNode;
import de.codesourcery.j6502.assembler.parser.ast.IndirectOperandX;
import de.codesourcery.j6502.assembler.parser.ast.IndirectOperandY;
import de.codesourcery.j6502.assembler.parser.ast.InitializedMemoryNode;
import de.codesourcery.j6502.assembler.parser.ast.InstructionNode;
import de.codesourcery.j6502.assembler.parser.ast.NumberLiteral;
import de.codesourcery.j6502.assembler.parser.ast.NumberLiteral.Notation;
import de.codesourcery.j6502.assembler.parser.ast.OperatorNode;
import de.codesourcery.j6502.assembler.parser.ast.SetOriginNode;
import de.codesourcery.j6502.assembler.parser.ast.Statement;
import de.codesourcery.j6502.assembler.parser.ast.StringLiteralNode;
import junit.framework.TestCase;

public class ParserTest extends TestCase {

	private AST ast;

	private void parse(String s) {
		ast = new Parser( new Lexer(new Scanner(s) ) ).parse();
	}
	
    public void testParseIncBin() 
    {
        parse(".incbin \"/home/tobi/tmp/Pic0.koa\"");
        assertNotNull(ast);
        assertEquals(1,ast.getChildCount());
        IASTNode stmt = ast.child(0);
        assertEquals( Statement.class , stmt.getClass() );
        assertEquals(1,stmt.getChildCount());
        IASTNode incBin = stmt.child(0);
        assertEquals( IncludeBinaryNode.class , incBin.getClass() );        
        assertEquals( "/home/tobi/tmp/Pic0.koa" , ((IncludeBinaryNode) incBin).path );
    }	

	public void testParseEmptySource1() {
		parse("");
		assertNotNull(ast);
		assertEquals(0,ast.children.size());
	}

	public void testSetOrigin() {
		parse("    *= $1234");
		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof SetOriginNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof NumberLiteral);

		assertEquals( (short) 0x1234 , ((NumberLiteral) ast.child(0).child(0).child(0) ).getWordValue() ) ;
	}

	public void testByteInitializedMemory() {
		parse("    .byte $01,2,3,$4");
		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof InitializedMemoryNode);

		final InitializedMemoryNode node = (InitializedMemoryNode) ast.child(0).child(0);
		assertEquals( 4 , node.getChildCount() );
		assertEquals( (short) 1 , ((IValueNode) node.child(0)).getWordValue() );
		assertEquals( (short) 2 , ((IValueNode) node.child(1)).getWordValue() );
		assertEquals( (short) 3 , ((IValueNode) node.child(2)).getWordValue() );
		assertEquals( (short) 4 , ((IValueNode) node.child(3)).getWordValue() );
	}
	
    public void testByteInitializedMemoryWithString() {
        parse("    .byte $01,2,3,$4,\"abc\"");
        assertNotNull(ast);
        assertEquals(1,ast.children.size());

        assertTrue( ast.child(0) instanceof Statement);
        assertTrue( ast.child(0).child(0) instanceof InitializedMemoryNode);

        final InitializedMemoryNode node = (InitializedMemoryNode) ast.child(0).child(0);
        assertEquals( 5 , node.getChildCount() );
        assertEquals( (short) 1 , ((IValueNode) node.child(0)).getWordValue() );
        assertEquals( (short) 2 , ((IValueNode) node.child(1)).getWordValue() );
        assertEquals( (short) 3 , ((IValueNode) node.child(2)).getWordValue() );
        assertEquals( (short) 4 , ((IValueNode) node.child(3)).getWordValue() );
        assertEquals( "abc", ((StringLiteralNode) node.child(4)).value );
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
	
	public void testParseEquWithLocalLabel() {

		parse("global\n.local .equ $0a");

		assertNotNull(ast);
		assertEquals(2,ast.children.size());

		assertTrue( ast.child(1) instanceof Statement);
		assertTrue( ast.child(1).child(0) instanceof EquNode);
		assertTrue( ast.child(1).child(0).child(0) instanceof NumberLiteral);

		EquNode node = (EquNode) ast.child(1).child(0);
		assertEquals( new Identifier("local") , node.getIdentifier() );
		NumberLiteral num = (NumberLiteral) ast.child(1).child(0).child(0);
		assertEquals( 10 , num.getWordValue() );
	}	

    public void testParseEquWithNumber() {

        parse("label: .equ $0a");

        assertNotNull(ast);
        assertEquals(1,ast.children.size());

        assertTrue( ast.child(0) instanceof Statement);
        assertTrue( ast.child(0).child(0) instanceof EquNode);
        assertTrue( ast.child(0).child(0).child(0) instanceof NumberLiteral);

        EquNode node = (EquNode) ast.child(0).child(0);
        assertEquals( new Identifier("label") , node.getIdentifier() );
        NumberLiteral num = (NumberLiteral) ast.child(0).child(0).child(0);
        assertEquals( 10 , num.getWordValue() );
    }
    
	public void testParseComplexExpression() 
	{
        parse("abc .equ ( 1 * 2 ) + 3");
		assertNotNull(ast);
	}
	
	public void testParseEquWithUnaryMinus()
	{
		parse("label: .equ -3");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof EquNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof OperatorNode);

		final OperatorNode op = (OperatorNode) ast.child(0).child(0).child(0);
		assertEquals( Operator.UNARY_MINUS , op.operator );
		assertEquals( -3 ,  op.evaluate() );
	}	
	
	public void testParseEquWithUnaryPlus()
	{
		parse("label: .equ +3");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof EquNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof OperatorNode);

		final OperatorNode op = (OperatorNode) ast.child(0).child(0).child(0);
		assertEquals( Operator.UNARY_PLUS , op.operator );
		assertEquals( 3 ,  op.evaluate() );
	}	
	
	public void testParseEquWithLeftShift()
	{
		parse("label: .equ 1 << 1");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof EquNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof OperatorNode);

		final OperatorNode op = (OperatorNode) ast.child(0).child(0).child(0);
		assertEquals( Operator.SHIFT_LEFT, op.operator );
		assertEquals( 2 ,  op.evaluate() );
	}	
	
	public void testParseEquWithRightShift()
	{
		parse("label: .equ 2 >> 1");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof EquNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof OperatorNode);

		final OperatorNode op = (OperatorNode) ast.child(0).child(0).child(0);
		assertEquals( Operator.SHIFT_RIGHT , op.operator );
		assertEquals( 1 ,  op.evaluate() );
	}		
	
	public void testParseEquWithBitwiseNegation()
	{
		parse("label: .equ ~ 0");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof EquNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof OperatorNode);

		final OperatorNode op = (OperatorNode) ast.child(0).child(0).child(0);
		assertEquals( Operator.BITWISE_NEGATION , op.operator );
		assertEquals( -1 ,  op.evaluate() );
	}	
	
	public void testParseEquWithBitwiseAND()
	{
		parse("label: .equ  %111 & 2");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof EquNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof OperatorNode);

		final OperatorNode op = (OperatorNode) ast.child(0).child(0).child(0);
		assertEquals( Operator.BITWISE_AND, op.operator );
		assertEquals( 2 ,  op.evaluate() );
	}	
	
	public void testParseEquWithBitwiseOR()
	{
		parse("label: .equ  %101 | 2");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof EquNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof OperatorNode);

		final OperatorNode op = (OperatorNode) ast.child(0).child(0).child(0);
		assertEquals( Operator.BITWISE_OR , op.operator );
		assertEquals( 7 ,  op.evaluate() );
	}		
	
	public void testParseEquWithLowerByte()
	{
		parse("label: .equ  < $abcd ");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof EquNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof OperatorNode);

		final OperatorNode op = (OperatorNode) ast.child(0).child(0).child(0);
		assertEquals( Operator.LOWER_BYTE, op.operator );
		assertEquals( 0xcd ,  op.evaluate() );
	}	
	
	public void testParseEquWithUpperByte()
	{
		parse("label: .equ  > $abcd ");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof EquNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof OperatorNode);

		final OperatorNode op = (OperatorNode) ast.child(0).child(0).child(0);
		assertEquals( Operator.UPPER_BYTE, op.operator );
		assertEquals( 0xab ,  op.evaluate() );
	}		
	
	public void testParseEquDivide()
	{
		parse("label .equ  12 / 3 ");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof EquNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof OperatorNode);

		final OperatorNode op = (OperatorNode) ast.child(0).child(0).child(0);
		assertEquals( Operator.DIVIDE , op.operator );
		assertEquals( 4 ,  op.evaluate() );
	}	

	public void testParseEquWithExpression1()
	{
		parse("label: .equ 1+2*3");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof EquNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof OperatorNode);

		assertEquals( 7 ,  ((OperatorNode) ast.child(0).child(0).child(0)).evaluate() );
	}

	public void testParseEquWithExpression2()
	{
		parse("label: .equ (1+2)*3");

		assertNotNull(ast);
		assertEquals(1,ast.children.size());

		assertTrue( ast.child(0) instanceof Statement);
		assertTrue( ast.child(0).child(0) instanceof EquNode);
		assertTrue( ast.child(0).child(0).child(0) instanceof OperatorNode);

		assertEquals( 9 ,  ((OperatorNode) ast.child(0).child(0).child(0)).evaluate() );
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
		assertEquals( 0xda00 , lit.value );
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