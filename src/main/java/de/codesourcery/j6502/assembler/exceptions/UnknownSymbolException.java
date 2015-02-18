package de.codesourcery.j6502.assembler.exceptions;

import de.codesourcery.j6502.assembler.parser.Identifier;


public class UnknownSymbolException extends RuntimeException {

	public final Identifier identifier;
	public final Identifier parentIdentifier;

	public UnknownSymbolException(Identifier identifier,Identifier parentIdentifier)
	{
		super("Unknown symbol "+( parentIdentifier == null ? identifier.toString() : parentIdentifier.toString()+"."+identifier.toString()) );
		this.identifier = identifier;
		this.parentIdentifier = parentIdentifier;
	}

}
