package de.codesourcery.j6502.parser;

import java.util.regex.Pattern;

import de.codesourcery.j6502.parser.ast.AST;
import de.codesourcery.j6502.parser.ast.ASTNode;
import de.codesourcery.j6502.parser.ast.AbsoluteOperand;
import de.codesourcery.j6502.parser.ast.CommentNode;
import de.codesourcery.j6502.parser.ast.IASTNode;
import de.codesourcery.j6502.parser.ast.Identifier;
import de.codesourcery.j6502.parser.ast.IdentifierReferenceNode;
import de.codesourcery.j6502.parser.ast.ImmediateOperand;
import de.codesourcery.j6502.parser.ast.IndirectOperand;
import de.codesourcery.j6502.parser.ast.IndirectOperandX;
import de.codesourcery.j6502.parser.ast.IndirectOperandY;
import de.codesourcery.j6502.parser.ast.InstructionNode;
import de.codesourcery.j6502.parser.ast.LabelNode;
import de.codesourcery.j6502.parser.ast.NumberLiteral;
import de.codesourcery.j6502.parser.ast.NumberLiteral.Notation;
import de.codesourcery.j6502.parser.ast.RegisterReference;
import de.codesourcery.j6502.parser.ast.Statement;

public class Parser
{
	private final Lexer lexer;

	private final AST ast = new AST();

	private LabelNode previousGlobalLabel = null;
	private ASTNode currentNode = null;

	public Parser(Lexer lexer) {
		this.lexer = lexer;
	}


	public AST parse()
	{
		while ( ! lexer.peek( TokenType.EOF ) )
		{
			parseStatement();

			if ( currentNode.hasChildren() ) {
				ast.addChild( currentNode );
			}
			currentNode = null;
		}
		lexer.next(TokenType.EOF);
		return ast;
	}

	private void fail(String msg) {
		throw new ParseException( msg ,lexer.currentOffset());
	}

	private boolean skipComment() {

		if ( ! lexer.peek(TokenType.SEMICOLON ) ) {
			return false;
		}
		lexer.next( TokenType.SEMICOLON );

		final StringBuffer buffer = new StringBuffer();
		try
		{
			do {
				final Token t = lexer.peek();
				if ( t.hasType(TokenType.EOL ) || t.hasType(TokenType.EOF ) ) {
					break;
				}
				lexer.setSkipWhitespace( false );
				buffer.append( lexer.next().text );
			} while ( true );
		}
		finally {
			lexer.setSkipWhitespace(true);
		}
		while( lexer.peek( TokenType.EOL ) )
		{
			lexer.next();
		}

		currentNode.addChild( new CommentNode( buffer.toString() ) );
		return true;
	}

	private void parseStatement()
	{
		currentNode = new Statement();

		boolean gotLocalLabel = false;

		if ( skipComment() ) {
			return;
		}

		if ( lexer.peek(TokenType.DOT) ) { // local label
			if ( previousGlobalLabel == null ) {
				fail("Local label without previous global label");
			}
			lexer.next();
			if ( lexer.peek(TokenType.CHARACTERS ) && Identifier.isValidIdentifier( lexer.peek().text ) ) {
				currentNode.addChild( new LabelNode( new Identifier( lexer.next().text ) , previousGlobalLabel ) );
				gotLocalLabel = true;
			} else {
				fail("Expected local label identifier");
			}
		}

		if ( skipComment() ) {
			return;
		}

		if ( lexer.peek(TokenType.CHARACTERS ) )  { // label or opcode
			final Token tok = lexer.next();
			if ( ! gotLocalLabel )
			{
				if ( lexer.peek(TokenType.COLON ) ) { // global label
					lexer.next();
					if ( ! Identifier.isValidIdentifier( tok.text ) ) {
						fail("Not a valid identifier: "+tok.text);
					}
					previousGlobalLabel = new LabelNode( new Identifier( tok.text ) );
					currentNode.addChild( previousGlobalLabel );
				} else {
					lexer.push( tok );
				}
			} else {
				lexer.push( tok );
			}

			// parse opCode
			final ASTNode instruction = parseInstruction();
			if ( instruction != null ) {
				currentNode.addChild(instruction);
			}
		}
		else if ( ! lexer.peek(TokenType.EOL) && ! lexer.peek(TokenType.EOF ) )
		{
			fail("Expected label or opcode");
		}

		if ( skipComment() ) {
			return;
		}

		// [label] [ opcode ] [ #<number>[, [comment]

		// skip newlines
		while ( lexer.peek(TokenType.EOL ) ) {
			lexer.next();
		}
	}

	private ASTNode parseInstruction() {

		final Token token = lexer.peek();
		if ( token.hasType(TokenType.CHARACTERS ) )
		{
			final Opcode op = Opcode.getOpcode( token.text );
			if ( op != null )
			{
				final InstructionNode ins = new InstructionNode(op);

				lexer.next(); // read opcode

				if ( ! (lexer.peek(TokenType.EOF) || lexer.peek( TokenType.EOF) || lexer.peek( TokenType.SEMICOLON ) ) )
				{
					final ASTNode left = parseLeftArgument( op );
					if ( left != null )
					{
						ins.addChild( left );
						if ( lexer.peek(TokenType.COMMA ) )
						{
							lexer.next();
							final IASTNode right = parseRightArgument();
							if ( right != null ) {
								ins.addChild( right );
							} else {
								fail("Missing right operand");
							}
						}
					}
				}
				return ins;
			}
		}
		return null;
	}

	/*
	 * Accumulator: ASL
	 * Immediate: LDA #$0a
	 * Absolute: LDX $0da0 (loads value from address $0d0a)
	 *    + Zero-page: LDX $2 (loads value from address $2)
	 * Relative offset: BPL $2D ( -127/+128 offset relative to current PC)
	 * Absolute Indexed:
	 *           INC $F001,Y ( value at $f001+Y is incremented)
	 *           INC $F001,X ( value at $f001+X is incremented)
	 *     +Zero-page indexed:
	 *           LDA $01,X
	 *           LDA $01,Y
	 * Zero Page Indexed Indirect: (zp,x):
	 *           STA ($15,X) ( accumulator will be written to the address that $15+x points to )
	 */

	private ASTNode parseLeftArgument(Opcode currentIns)
	{
		ASTNode result = null;
		if ( lexer.peek(TokenType.PARENS_OPEN ) ) { // indirect addressing

			lexer.next();

			final IASTNode number = parseExpression();
			if ( number == null ) {
				fail("Indirect addressing requires an address");
			}

			if ( lexer.peek(TokenType.PARENS_CLOSE) ) // ( $ff ) , y
			{
				lexer.next();

				if ( currentIns == Opcode.JMP )
				{
					result = new IndirectOperand();
					result.addChild( number );
					return result;
				}
				lexer.next(TokenType.COMMA);
				final RegisterReference reg = parseRegister();
				if ( reg.register == Register.Y ) {
					result = new IndirectOperandY();
					result.addChild( number );
					return result;
				} else {
					fail("Expected reference to Y register");
				}
			} else if ( lexer.peek(TokenType.COMMA ) ) { // ( $ff ,x )
				lexer.next(TokenType.COMMA);
				final RegisterReference reg = parseRegister();
				if ( reg.register == Register.X )
				{
					lexer.next(TokenType.PARENS_CLOSE);
					result = new IndirectOperandX();
					result.addChild( number );
					return result;
				} else {
					fail("Expected reference to X register");
				}
			} else {
				fail("Expected a comma or closing parens");
			}
		}
		else if ( lexer.peek(TokenType.HASH ) ) { // immediate addressing
			lexer.next();
			result = new ImmediateOperand();
			final IASTNode value = parseExpression();
			if ( value == null ) {
				fail("Immediate mode requires an argument");
			}
			result.addChild( value );
		} else if ( lexer.peek(TokenType.DOLLAR) || lexer.peek(TokenType.DIGITS ) ) { // absolute addressing
			result = new AbsoluteOperand();
			result.addChild( parseExpression() );
		}
		else if ( lexer.peek(TokenType.CHARACTERS ) && Identifier.isValidIdentifier( lexer.peek().text ) ) // absolute addressing: reference to label
		{
			throw new RuntimeException("TODO: Support labels as operand values");
		}
		return result;
	}

	private IASTNode parseRightArgument()
	{
		final RegisterReference reg = parseRegister();
		if ( reg != null ) {
			return reg;
		}
		return parseExpression();
	}

	private RegisterReference parseRegister()
	{
		if ( lexer.peek( TokenType.CHARACTERS ) )
		{
			final Register r = Register.getRegister( lexer.peek().text );
			if ( r != null ) {
				lexer.next();
				return new RegisterReference( r );
			}
		}
		return null;
	}

	public IASTNode parseExpression()
	{
		final Notation notation;
		final StringBuilder buffer = new StringBuilder();
		long value;
		if ( lexer.peek(TokenType.DOLLAR ) )
		{
			notation = Notation.HEXADECIMAL;
			lexer.next(); // consume '$'

			while ( true)
			{
				if ( lexer.peek(TokenType.DIGITS ) || ( lexer.peek( TokenType.CHARACTERS ) && isValidHexCharacters( lexer.peek().text ) ) ) {
					buffer.append( lexer.next().text );
				} else {
					break;
				}
			}
			if ( buffer.length() == 0 ) {
				fail("Expected a hexadecimal number");
			}
			value = Long.valueOf( buffer.toString().toLowerCase() , 16 );
		}
		else if ( lexer.peek(TokenType.DIGITS ) )
		{
			notation = Notation.DECIMAL;
			buffer.append( lexer.next().text );
			value = Long.parseLong( buffer.toString() );
		}
		else if ( lexer.peek(TokenType.CHARACTERS ) )
		{
			final Token tok = lexer.peek();
			if ( Identifier.isValidIdentifier( tok.text ) ) {
				return new IdentifierReferenceNode( new Identifier( tok.text ) );
			}
			return null;
		} else {
			return null;
		}

		if ( value < -127 || value > 65535 ) {
			fail("Number of or range: "+buffer+" ("+value+")");
		}
		return new NumberLiteral((short) value, notation );
	}

	private static final Pattern VALID_HEX_NUMBER = Pattern.compile("[0-9a-fA-F]+");

	private boolean isValidHexCharacters(String text)
	{
		return VALID_HEX_NUMBER.matcher(text).matches();
	}
}