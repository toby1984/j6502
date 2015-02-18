package de.codesourcery.j6502.parser;

import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.assembler.parser.Token;
import de.codesourcery.j6502.assembler.parser.TokenType;
import junit.framework.TestCase;

public class LexerTest extends TestCase
{
	private Lexer lexer;

	public void testParseColon() {
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

	public void testParseSingleCharacter() {
		lex( "a" );
		assertToken(TokenType.CHARACTERS,"a",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testParseMultipleCharacters() {
		lex( "abcdefgh" );
		assertToken(TokenType.CHARACTERS,"abcdefgh",0);
		assertToken(TokenType.EOF,"",8);
		assertTrue( lexer.eof() );
	}

	public void testParseSingleDigit() {
		lex( "1" );
		assertToken(TokenType.DIGITS,"1",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testParseMultipleDigits() {
		lex( "1234567890" );
		assertToken(TokenType.DIGITS,"1234567890",0);
		assertToken(TokenType.EOF,"",10);
		assertTrue( lexer.eof() );
	}

	public void testParseDot() {
		lex( "." );
		assertToken(TokenType.DOT,".",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testParseLF() {
		lex( "\n" );
		assertToken(TokenType.EOL,"\n",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testParseCRLF() {
		lex( "\r\n" );
		assertToken(TokenType.EOL,"\r\n",0);
		assertToken(TokenType.EOF,"",2);
		assertTrue( lexer.eof() );
	}

	public void testParseComma() {
		lex( "," );
		assertToken(TokenType.COMMA,",",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testParseDollar() {
		lex( "$" );
		assertToken(TokenType.DOLLAR,"$",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testParseHash() {
		lex( "#" );
		assertToken(TokenType.HASH,"#",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testParseSingleQuote() {
		lex( "'" );
		assertToken(TokenType.SINGLE_QUOTE,"'",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	public void testParseDoubleQuote() {
		lex( "\"" );
		assertToken(TokenType.DOUBLE_QUOTE,"\"",0);
		assertToken(TokenType.EOF,"",1);
		assertTrue( lexer.eof() );
	}

	private void lex(String s) {
		lexer = new Lexer( new Scanner( s ) );
	}

	private void assertToken(TokenType t) {
		assertEquals( t , lexer.next().type );
	}

	private void assertToken(TokenType t,String text) {
		final Token token = lexer.next();
		assertEquals( t , token.type );
		assertEquals( text , token.text );
	}

	private void assertToken(TokenType t,String text,int offset) {
		final Token token = lexer.next();
		assertEquals( t , token.type );
		assertEquals( text , token.text );
		assertEquals("Got : "+token,offset,token.offset);
	}
}
