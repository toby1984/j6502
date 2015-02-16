package de.codesourcery.j6502.parser;

public class ParseException extends RuntimeException {

	public final int offset;

	public ParseException(String message,int offset)
	{
		super(message);
		this.offset = offset;
	}
}
