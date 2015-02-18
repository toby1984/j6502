package de.codesourcery.j6502.assembler;

import de.codesourcery.j6502.parser.ast.Identifier;


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
