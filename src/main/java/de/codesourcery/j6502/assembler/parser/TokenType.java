package de.codesourcery.j6502.assembler.parser;

public enum TokenType
{
	// single character tokens
	COLON,
	COMMA,
	DOLLAR,
	HASH,
	PERCENTAGE,
	SINGLE_QUOTE,
	DOUBLE_QUOTE,
	SEMICOLON,
	PARENS_OPEN,
	PARENS_CLOSE,
	DOT,
	EOL,
	EOF,
	EQUALS,
	// multi-character tokens
	DIGITS,
	CHARACTERS,
	WHITESPACE,
	OPERATOR,
	META_BYTE,
	META_EQU,
	META_INCBIN;
}