package de.codesourcery.j6502.assembler.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

import de.codesourcery.j6502.assembler.exceptions.ParseException;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.assembler.parser.ast.ASTNode;
import de.codesourcery.j6502.assembler.parser.ast.AbsoluteOperand;
import de.codesourcery.j6502.assembler.parser.ast.CommentNode;
import de.codesourcery.j6502.assembler.parser.ast.IASTNode;
import de.codesourcery.j6502.assembler.parser.ast.IdentifierReferenceNode;
import de.codesourcery.j6502.assembler.parser.ast.ImmediateOperand;
import de.codesourcery.j6502.assembler.parser.ast.IndirectOperand;
import de.codesourcery.j6502.assembler.parser.ast.IndirectOperandX;
import de.codesourcery.j6502.assembler.parser.ast.IndirectOperandY;
import de.codesourcery.j6502.assembler.parser.ast.InitializedMemoryNode;
import de.codesourcery.j6502.assembler.parser.ast.InstructionNode;
import de.codesourcery.j6502.assembler.parser.ast.LabelNode;
import de.codesourcery.j6502.assembler.parser.ast.NumberLiteral;
import de.codesourcery.j6502.assembler.parser.ast.NumberLiteral.Notation;
import de.codesourcery.j6502.assembler.parser.ast.RegisterReference;
import de.codesourcery.j6502.assembler.parser.ast.SetOriginNode;
import de.codesourcery.j6502.assembler.parser.ast.Statement;
import de.codesourcery.j6502.utils.ITextRegion;
import de.codesourcery.j6502.utils.TextRegion;

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
			skipNewLines();

			parseStatement();

			skipComment();

			while ( lexer.peek(TokenType.EOL ) ) {
				lexer.next();
			}

			if ( currentNode.hasChildren() )
			{
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
		final int startOffset = lexer.next( TokenType.SEMICOLON ).offset;

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

		final TextRegion region = new TextRegion( startOffset , lexer.currentOffset() - startOffset );
		currentNode.addChild( new CommentNode( buffer.toString() , region ) );
		return true;
	}

	private void parseStatement()
	{
		currentNode = new Statement();

		boolean gotLocalLabel = false;

		if ( skipComment() ) {
			return;
		}

		int startOffset = lexer.currentOffset();

		// handle *=
		if ( lexer.peek(TokenType.STAR ) ) {
			final Token tok = lexer.next();
			if ( lexer.peek(TokenType.EQUALS ) ) // *=
			{
				final Token tok2 = lexer.next();
				final IASTNode expr = parseExpression();
				if ( expr != null )
				{
					final SetOriginNode node = new SetOriginNode( new TextRegion(tok.offset , tok2.offset - tok.offset ) );
					node.addChild( expr );
					currentNode.addChild( node );
					return;
				} else {
					lexer.push(tok);
				}
			} else {
				lexer.push( tok );
			}
		}

		if ( lexer.peek(TokenType.DOT) ) { // .localLabel or meta

			final Token dot = lexer.next(); // consume dot

			//
			if ( lexer.peek(TokenType.CHARACTERS ) ) {
				switch( lexer.peek().text.toLowerCase() )
				{
					case "byte":
						final Token keyword = lexer.next();

						final InitializedMemoryNode node = new InitializedMemoryNode( dot.toRegion().merge( keyword.toRegion() ) , InitializedMemoryNode.Type.BYTES );
						node.addChildren( parseMemInitList() );
						currentNode.addChild( node );
						return;
					default:
						// $$FALL-THROUGH$$
				}
			}

			// ok, not a meta
			if ( previousGlobalLabel == null ) {
				fail("Local label without previous global label");
			}
			if ( lexer.peek(TokenType.CHARACTERS ) )
			{
				if ( Identifier.isValidIdentifier( lexer.peek().text ) ) {
					final Identifier identifier = new Identifier( lexer.next().text );
					final TextRegion region = new TextRegion( startOffset , lexer.currentOffset() - startOffset );
					currentNode.addChild( new LabelNode( identifier , previousGlobalLabel , region ) );
					gotLocalLabel = true;
				} else {
					fail("Not a valid label identifier: "+lexer.peek().text );
				}
			} else {
				fail("Expected valid local label identifier");
			}
		}

		if ( skipComment() ) {
			return;
		}

		startOffset = lexer.currentOffset();
		if ( lexer.peek(TokenType.CHARACTERS ) )  { // label or opcode
			final Token tok = lexer.next(); // consume token so we can peek at the next one
			if ( ! gotLocalLabel )
			{
				if ( lexer.peek(TokenType.COLON ) ) { // global label
					lexer.next(); // consume colon
					if ( ! Identifier.isValidIdentifier( tok.text ) ) {
						fail("Not a valid label identifier: "+tok.text);
					}
					final TextRegion region = new TextRegion( startOffset , lexer.currentOffset() - startOffset );
					previousGlobalLabel = new LabelNode( new Identifier( tok.text ) , region );
					currentNode.addChild( previousGlobalLabel );
				} else {
					// push back token, most likely an opcode
					lexer.push( tok );
				}
			} else {
				// push back token, most likely an opcode
				lexer.push( tok );
			}

			// parse opCode
			final IASTNode instruction = parseInstruction();
			if ( instruction != null ) {
				currentNode.addChild(instruction);
			} else {
				fail("Expected an instruction");
			}
		}
		else if ( ! lexer.peek(TokenType.EOL) && ! lexer.peek(TokenType.EOF ) )
		{
			fail("Expected label or opcode");
		}
	}

	private List<IASTNode> parseMemInitList()
	{
		final List<IASTNode> result = new ArrayList<>();
		do
		{
			final IASTNode expr = parseExpression();
			if ( expr == null ) {
				fail("Expected a value/expression");
			}
			result.add( expr );
			if ( ! lexer.peek(TokenType.COMMA ) ) {
				break;
			}
			lexer.next(); // consume comma
		} while ( true );
		return result;
	}

	private void skipNewLines() {
		while ( lexer.peek(TokenType.EOL ) ) {
			lexer.next();
		}
	}

	private IASTNode parseInstruction() {

		final Token token = lexer.peek();
		if ( token.hasType(TokenType.CHARACTERS ) )
		{
			final Opcode op = Opcode.getOpcode( token.text );
			if ( op != null )
			{
				lexer.next(); // read opcode
				final InstructionNode ins = new InstructionNode( op , new TextRegion(token.offset, lexer.currentOffset() - token.offset ) );

				if ( ! (lexer.peek(TokenType.EOF) || lexer.peek( TokenType.EOF) || lexer.peek( TokenType.SEMICOLON ) ) )
				{
					final IASTNode left = parseLeftArgument( op );
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

	private IASTNode parseLeftArgument(Opcode currentIns)
	{
		IASTNode result = null;
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
			final IASTNode tmp = parseExpression();
			if ( tmp != null ) {
				result = new AbsoluteOperand();
				result.addChild( tmp );
			}
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
				final Token token = lexer.next();
				return new RegisterReference( r , new TextRegion( token.offset , lexer.currentOffset() - token.offset ) );
			}
		}
		return null;
	}

	public IASTNode parseExpression()
	{
		final Notation notation;
		final StringBuilder buffer = new StringBuilder();
		final long value;
		final ITextRegion region;
		if ( lexer.peek(TokenType.DOLLAR ) )
		{
			final int startOffset = lexer.currentOffset();

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
			region = new TextRegion( startOffset , lexer.currentOffset() - startOffset );
			value = Long.valueOf( buffer.toString().toLowerCase() , 16 );
		}
		else if ( lexer.peek(TokenType.DIGITS ) )
		{
			notation = Notation.DECIMAL;
			final Token token = lexer.next();
			buffer.append( token.text );

			region = new TextRegion( token.offset , lexer.currentOffset() - token.offset );
			value = Long.parseLong( buffer.toString() );

		}
		else if ( lexer.peek(TokenType.CHARACTERS ) )
		{
			final Token tok = lexer.peek();
			if ( Identifier.isValidIdentifier( tok.text ) ) {
				lexer.next();
				return new IdentifierReferenceNode( new Identifier( tok.text ) , new TextRegion(tok.offset, lexer.currentOffset() - tok.offset ) );
			}
			return null;
		} else {
			return null;
		}

		if ( value < -127 || value > 65535 ) {
			fail("Number of or range: "+buffer+" ("+value+")");
		}
		return new NumberLiteral((short) value, notation , region );
	}

	private static final Pattern VALID_HEX_NUMBER = Pattern.compile("[0-9a-fA-F]+");

	private boolean isValidHexCharacters(String text)
	{
		return VALID_HEX_NUMBER.matcher(text).matches();
	}
}