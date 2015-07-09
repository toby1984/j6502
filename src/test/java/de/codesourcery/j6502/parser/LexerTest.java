package de.codesourcery.j6502.parser;

import junit.framework.TestCase;
import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Operator;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.assembler.parser.Token;
import de.codesourcery.j6502.assembler.parser.TokenType;

public class LexerTest extends TestCase
{
	private Lexer lexer;

	public void testLexColon() {
		lex( ":" );
		assertToken(TokenType.COLON,":",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testLexMultipleTokens() {
		lex( "a 1bc , :" );
		assertToken(TokenType.CHARACTERS,"a",0);
		assertToken(TokenType.CHARACTERS,"1bc",2);
		assertToken(TokenType.COMMA,",",6);
		assertToken(TokenType.COLON,":",8);
		assertToken(TokenType.EOF,"",9);
		assertTrue( lexer.eof() );
	}

	public void testLexSingleCharacter() {
		lex( "a" );
		assertToken(TokenType.CHARACTERS,"a",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testLexMultipleCharacters() {
		lex( "abcdefgh" );
		assertToken(TokenType.CHARACTERS,"abcdefgh",0);
		assertToken(TokenType.EOF,"",8);
		assertTrue( lexer.eof() );
	}

	public void testLexSingleDigit() {
		lex( "1" );
		assertToken(TokenType.DIGITS,"1",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testLexMultipleDigits() {
		lex( "1234567890" );
		assertToken(TokenType.DIGITS,"1234567890",0);
		assertToken(TokenType.EOF,"",10);
		assertTrue( lexer.eof() );
	}

	public void testLexDot() {
		lex( "." );
		assertToken(TokenType.DOT,".",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testLexLF() {
		lex( "\n" );
		assertToken(TokenType.EOL,"\n",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testLexCRLF() {
		lex( "\r\n" );
		assertToken(TokenType.EOL,"\r\n",0);
		assertToken(TokenType.EOF,"",2);
		assertTrue( lexer.eof() );
	}

	public void testLexComma() {
		lex( "," );
		assertToken(TokenType.COMMA,",",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testLexDollar() {
		lex( "$" );
		assertToken(TokenType.DOLLAR,"$",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testLexHash() {
		lex( "#" );
		assertToken(TokenType.HASH,"#",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testLexPlus() {
		lex( "+" );
		assertOperator( Operator.PLUS , 0 );
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testLexMinus()
	{
		lex( "-" );
		assertOperator( Operator.BINARY_MINUS , 0 );
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testLexSingleQuote() {
		lex( "'" );
		assertToken(TokenType.SINGLE_QUOTE,"'",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testLexAddition() {
		lex( "1+2" );
		assertToken(TokenType.DIGITS,"1",0);
		assertToken(TokenType.OPERATOR,"+",1);
		assertToken(TokenType.DIGITS,"2",2);
		assertToken(TokenType.EOF,"",3);
		assertTrue( lexer.eof() );
	}

	public void testLexDoubleQuote() {
		lex( "\"" );
		assertToken(TokenType.DOUBLE_QUOTE,"\"",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testLexByteMeta() {
		lex(".byte");
		assertToken(TokenType.META_BYTE,".byte",0);
		assertToken(TokenType.EOF,"",5);
		assertTrue( lexer.eof() );
	}

	public void testLexEquMeta() {
		lex(".equ");
		assertToken(TokenType.META_EQU,".equ",0);
		assertToken(TokenType.EOF,"",4);
		assertTrue( lexer.eof() );
	}

	private void lex(String s) {
		lexer = new Lexer( new Scanner( s ) );
	}

	private void assertOperator(Operator op,int offset) {
		final Token token = lexer.next();
		assertEquals( TokenType.OPERATOR , token.type );
		assertEquals( op.symbol , token.text );
		assertEquals( op , token.operator() );
		assertEquals("Got : "+token,offset,token.offset);
	}

	private void assertToken(TokenType t,String text,int offset) {
		final Token token = lexer.next();
		assertEquals( t , token.type );
		assertEquals( text , token.text );
		assertEquals("Got : "+token,offset,token.offset);
	}
}