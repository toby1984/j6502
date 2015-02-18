package de.codesourcery.j6502.assembler;

import de.codesourcery.j6502.assembler.parser.Identifier;

public interface ISymbolTable
{
	public void declareSymbol(Identifier identifier,Identifier parentIdentifier);

	public void defineSymbol(ISymbol<?> id);

	public ISymbol<?> getSymbol(Identifier identifier,Identifier parentIdentifier);

	public boolean isDefined(Identifier identifier,Identifier parentIdentifier);
}
