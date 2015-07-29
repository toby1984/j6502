package de.codesourcery.j6502.assembler.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import de.codesourcery.j6502.assembler.exceptions.ParseException;
import de.codesourcery.j6502.assembler.parser.ExpressionToken.ExpressionTokenType;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.assembler.parser.ast.ASTNode;
import de.codesourcery.j6502.assembler.parser.ast.AbsoluteOperand;
import de.codesourcery.j6502.assembler.parser.ast.CommentNode;
import de.codesourcery.j6502.assembler.parser.ast.EquNode;
import de.codesourcery.j6502.assembler.parser.ast.IASTNode;
import de.codesourcery.j6502.assembler.parser.ast.IdentifierReferenceNode;
import de.codesourcery.j6502.assembler.parser.ast.ImmediateOperand;
import de.codesourcery.j6502.assembler.parser.ast.IncludeBinaryNode;
import de.codesourcery.j6502.assembler.parser.ast.IndirectOperand;
import de.codesourcery.j6502.assembler.parser.ast.IndirectOperandX;
import de.codesourcery.j6502.assembler.parser.ast.IndirectOperandY;
import de.codesourcery.j6502.assembler.parser.ast.InitializedMemoryNode;
import de.codesourcery.j6502.assembler.parser.ast.InstructionNode;
import de.codesourcery.j6502.assembler.parser.ast.LabelNode;
import de.codesourcery.j6502.assembler.parser.ast.NumberLiteral;
import de.codesourcery.j6502.assembler.parser.ast.NumberLiteral.Notation;
import de.codesourcery.j6502.assembler.parser.ast.OperatorNode;
import de.codesourcery.j6502.assembler.parser.ast.RegisterReference;
import de.codesourcery.j6502.assembler.parser.ast.SetOriginNode;
import de.codesourcery.j6502.assembler.parser.ast.Statement;
import de.codesourcery.j6502.utils.ITextRegion;
import de.codesourcery.j6502.utils.TextRegion;

public class Parser
{
	private final Lexer lexer;

	private final AST ast = new AST();

	private LabelNode labelOnCurrentLine = null;
	private LabelNode previousGlobalLabel = null;
	private ASTNode currentNode = null;

	public Parser(Lexer lexer) {
		this.lexer = lexer;
	}

	public AST parse()
	{
		while ( ! lexer.peek( TokenType.EOF ) )
		{
			// skip newlines
			while ( lexer.peek(TokenType.EOL ) ) {
				lexer.next();
			}

			if ( parseStatement() ) {
				ast.addChild( currentNode );
			}
		}
		lexer.next(TokenType.EOF);
		return ast;
	}

	private void fail(String msg,int offset)
	{
		throw new ParseException( msg , offset);
	}

	private void fail(String msg) {
		throw new ParseException( msg ,lexer.currentOffset());
	}

	private boolean parseComment() {

		if ( lexer.peek(TokenType.SEMICOLON ) )
		{
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
		return false;
	}

	private boolean parseStatement()
	{
		labelOnCurrentLine = null;

		currentNode = new Statement();

		if ( ! ( lexer.peek( TokenType.EOF ) || lexer.peek( TokenType.EOL ) ) )
		{
			final boolean gotLabel = parseLabel();

			final boolean gotInstruction = parseInstructionOrMeta();

			final boolean gotComment = parseComment();

			if ( ! gotLabel && ! gotInstruction && ! gotComment ) {
				fail("Syntax error");
			}
			return true;
		}
		return false;
	}

	private boolean parseInstructionOrMeta()
	{
		if ( parseMeta() ) { // *=  .byte   .equ
			return true;
		}

		final Token insToken = lexer.peek();
		if ( insToken.hasType(TokenType.CHARACTERS ) )
		{
			final Opcode op = Opcode.getOpcode( insToken.text );
			if ( op != null )
			{
				lexer.next(); // consume instruction token

				final InstructionNode ins = new InstructionNode( op , insToken.region() );
				if ( ! ( lexer.peek(TokenType.EOF) || lexer.peek(TokenType.EOL) || lexer.peek( TokenType.SEMICOLON ) ) )
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
				currentNode.addChild( ins );
				return true;
			}
		}
		return false;
	}

	private boolean parseLabel() {

		Token localLabelDot = null;
		if ( lexer.peek( TokenType.DOT ) )
		{
			localLabelDot = lexer.next();
		}

		if ( lexer.peek( TokenType.CHARACTERS ) && Identifier.isValidIdentifier( lexer.peek().text ) )
		{
			if ( localLabelDot != null || Opcode.getOpcode( lexer.peek().text ) == null )
			{
				final Token idToken = lexer.next();
				final Identifier id = new Identifier( idToken.text );
				if( localLabelDot != null ) // local label
				{
					if ( previousGlobalLabel == null )
					{
						throw new ParseException( "Local label '"+lexer.peek().text+" without preceding global label", lexer.peek() );
					}
					labelOnCurrentLine = new LabelNode( id , previousGlobalLabel , localLabelDot.region().merge( idToken.region() ));
					currentNode.addChild( labelOnCurrentLine );
				}
				else // global label
				{
					ITextRegion region = idToken.region();
					if ( lexer.peek( TokenType.COLON ) )
					{
						final Token colonToken = lexer.next();
						region = region.merge( colonToken.region() );
					}

					labelOnCurrentLine = previousGlobalLabel = new LabelNode( id , region );
					currentNode.addChild( previousGlobalLabel  );
				}
				return true;
			}
		}

		if( localLabelDot != null ) {
			lexer.push( localLabelDot );
		}
		return false;
	}

	private boolean parseMeta()
	{
		if ( lexer.peek( TokenType.OPERATOR ) && lexer.peek().operator().symbol.equals("*") ) // *=
		{
			final Token tok = lexer.next();
			if ( lexer.peek(TokenType.EQUALS ) )
			{
				final Token tok2 = lexer.next();
				final IASTNode expr = parseExpression();
				if ( expr != null )
				{
					final SetOriginNode node = new SetOriginNode( new TextRegion(tok.offset , tok2.offset - tok.offset ) );
					node.addChild( expr );
					currentNode.addChild( node );
					return true;
				}
				lexer.push(tok2);
			}
			lexer.push( tok );
		}
		
		if ( lexer.peek(TokenType.META_INCBIN ) ) // .incbin "file"
		{
			final Token startingQuotes = lexer.next();
			if ( ! lexer.peek( TokenType.DOUBLE_QUOTE ) ) 
			{
				fail(".incbin requires a file name in double quotes"); // always throws ParseException
				return false; // make compiler happy
			}

			lexer.next( TokenType.DOUBLE_QUOTE );
			
			lexer.setSkipWhitespace( false );
			try 
			{
				final StringBuilder buffer = new StringBuilder();
				while ( ! lexer.eof() && ! lexer.peek( TokenType.DOUBLE_QUOTE ) && ! lexer.peek( TokenType.EOL) ) 
				{
					buffer.append( lexer.next().text );
				}
				if ( ! lexer.peek( TokenType.DOUBLE_QUOTE ) ) 
				{
					fail("Unterminated string"); // always throws ParseException
					return false; // make compiler happy
				}
				lexer.next( TokenType.DOUBLE_QUOTE );
				currentNode.addChild( new IncludeBinaryNode( buffer.toString(), new TextRegion( startingQuotes.offset , buffer.length()+2 ) ) );
				return true;
			} 
			finally {
				lexer.setSkipWhitespace( true );
			}
		}		

		if ( lexer.peek(TokenType.META_BYTE ) ) // .byte
		{
			final Token token = lexer.next();
			final InitializedMemoryNode node = new InitializedMemoryNode( token.region() , InitializedMemoryNode.Type.BYTES );
			node.addChildren( parseMemInitList() );
			currentNode.addChild( node );
			return true;
		}

		if ( lexer.peek(TokenType.META_EQU ) && labelOnCurrentLine != null ) // .equ
		{
			final Token token = lexer.next();
			final IASTNode exprNode = parseExpression();
			if ( exprNode == null ) {
				fail("expected an expression");
			}

			final EquNode node = new EquNode( labelOnCurrentLine.identifier , new TextRegion( labelOnCurrentLine.getTextRegion() ).merge( token.region() ) );

			// 'labelOnCurrentLine' has already been registered as a LabelNode, remove it from the AST
			// since the label identifier is actually part of the EQU node.
			// Failing to remove the label node here will trigger a 'duplicate symbol' execution later on
			currentNode.removeChild( labelOnCurrentLine );
			if ( previousGlobalLabel == labelOnCurrentLine ) {
				previousGlobalLabel = null;
			}
			labelOnCurrentLine = null;

			node.addChild( exprNode );
			currentNode.addChild( node );
			return true;
		}

		return false;
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
			final IASTNode expression = parseExpression();
			result.addChild( expression );
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

	private IASTNode parseExpression()
	{
		final ShuntingYard yard = new ShuntingYard();

		final int offset = lexer.currentOffset();
		int openingParensCount=0;
		while(true)
		{
			final IASTNode value = parseValue();
			if ( value != null )
			{
				yard.pushValue( value );
				continue;
			}

			if ( lexer.peek( TokenType.OPERATOR ) )
			{
				final List<Operator> operators = Operator.getMatchingOperators( lexer.peek().text );
				if ( operators.size() == 1 )
				{
					final OperatorNode opNode = new OperatorNode( operators.get(0), lexer.next().region() );
					yard.pushOperator( new ExpressionToken( ExpressionTokenType.OPERATOR, opNode ) );
					continue;
				}
				throw new RuntimeException("Internal error, lexer returned token that lex'ed to "+operators.size()+" operators ? "+lexer.peek());
			}

			if ( lexer.peek( TokenType.PARENS_OPEN ) )
			{
				openingParensCount++;
				yard.pushOperator( new ExpressionToken( ExpressionTokenType.PARENS_OPEN , lexer.next() ) );
				continue;
			}

			if ( openingParensCount > 0 && lexer.peek( TokenType.PARENS_CLOSE)  )
			{
				openingParensCount--;
				yard.pushOperator( new ExpressionToken( ExpressionTokenType.PARENS_CLOSE , lexer.next() ) );
				continue;
			}
			break;
		}
		return yard.getResult( new TextRegion( offset , lexer.currentOffset() - offset ) );
	}

	private IASTNode parseValue()
	{
		final IASTNode result = parseNumber();
		if ( result != null ) {
			return result;
		}
		return parseIdentifier();
	}

	private IASTNode parseIdentifier()
	{
		if ( lexer.peek(TokenType.CHARACTERS ) )
		{
			final Token tok = lexer.peek();
			if ( Identifier.isValidIdentifier( tok.text ) ) {
				lexer.next();
				return new IdentifierReferenceNode( new Identifier( tok.text ) , tok.region() );
			}
		}
		return null;
	}

	private IASTNode parseNumber()
	{
		final int startOffset = lexer.currentOffset();

		final Function<String,Integer> conversion;
		final Predicate<Token> isValid;
		final Notation notation;
		Token prefix = null;
		switch( lexer.peek().type )
		{
			case PERCENTAGE:
				prefix = lexer.next();
				notation = Notation.BINARY;
				isValid = token-> token.hasType( TokenType.DIGITS ) && NumberLiteral.isValidBinaryNumber( token.text );
				conversion = s -> Integer.parseInt( s , 2 );
				break;
			case DOLLAR:
				prefix = lexer.next();
				notation = Notation.HEXADECIMAL;
				isValid = token-> ( token.hasType( TokenType.DIGITS ) || token.hasType( TokenType.CHARACTERS) ) && NumberLiteral.isValidHexString( token.text );
				conversion = s -> Integer.parseInt( s.toLowerCase() , 16 );
				break;
			case DIGITS:
				isValid = token -> token.hasType( TokenType.DIGITS ) && NumberLiteral.isValidDecimalNumber( token.text );
				notation = Notation.DECIMAL;
				conversion = s -> Integer.parseInt( s.toLowerCase() );
				break;
			default:
				return null;
		}

		final StringBuilder buffer = new StringBuilder();
		while( true )
		{
			if ( ! isValid.test( lexer.peek() ) ) {
				break;
			}
			buffer.append( lexer.next().text );
		}

		if ( buffer.length() == 0 )
		{
			if ( prefix != null ) {
				lexer.push( prefix );
			}
			return null;
		}

		final int value = conversion.apply( buffer.toString() );
		if ( value < Short.MIN_VALUE || value > 65535 ) {
			fail("Number of or range: "+buffer+" ("+value+")" , startOffset );
		}
		return new NumberLiteral( value, notation , new TextRegion( startOffset , lexer.currentOffset() - startOffset ) );
	}
}