package de.codesourcery.j6502.assembler.exceptions;

import de.codesourcery.j6502.assembler.parser.Token;

public class SyntaxErrorException extends RuntimeException
{
	public final Token token;

	public SyntaxErrorException(String msg,Token token) {
		super(msg+" @ "+token);
		this.token = token;
	}
}
