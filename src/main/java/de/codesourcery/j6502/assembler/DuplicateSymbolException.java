package de.codesourcery.j6502.assembler;


public class DuplicateSymbolException extends RuntimeException {

	public final ISymbol symbol;

	public DuplicateSymbolException(ISymbol symbol)
	{
		super("Duplicate symbol "+symbol);
		this.symbol = symbol;
	}
}
