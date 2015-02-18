package de.codesourcery.j6502.parser.ast;

import de.codesourcery.j6502.assembler.ICompilationContext;
import de.codesourcery.j6502.assembler.ISymbol;
import de.codesourcery.j6502.assembler.NumberLiteralOutOfRangeException;

public class IdentifierReferenceNode extends ASTNode implements NumericValue , ICompilationContextAware
{
	public final Identifier identifier;
	private ISymbol<? extends Number> symbol;

	public IdentifierReferenceNode(Identifier id) {
		this.identifier = id;
	}

	@Override
	public String toString() {
		return identifier.value;
	}

	@Override
	public byte getByteValue()
	{
		if ( ! isValueAvailable() ) {
			throw new IllegalStateException("getByte() called on unresolved symbol "+this);
		}
		final short value = symbol.getValue().shortValue();

		if ( value < -128 || value > 255 )
		{
			throw NumberLiteralOutOfRangeException.byteRange( value );
		}
		return (byte) value;
	}

	@Override
	public short getWordValue()
	{
		if ( ! isValueAvailable() ) {
			throw new IllegalStateException("getByte() called on unresolved symbol "+this);
		}
		final int value = symbol.getValue().intValue();

		if ( value < -32768 || value > 65535 )
		{
			throw NumberLiteralOutOfRangeException.wordRange( value );
		}
		return (short) value;
	}

	@Override
	public boolean isValueAvailable()
	{
		return symbol != null && symbol.hasValue();
	}

	@Override
	public void visit(ICompilationContext context)
	{
		ISymbol<?> symbol = null;
		if ( context.getSymbolTable().isDefined( identifier , null ) ) { // first check whether this is something in the global scope
			symbol = context.getSymbolTable().getSymbol( identifier , null );
		}
		else if ( context.getPreviousGlobalLabel() != null )
		{
			// assuming it's the identifier of a local label
			final Identifier globalId = context.getPreviousGlobalLabel().identifier;
			if ( context.getSymbolTable().isDefined( identifier ,globalId ) )
			{
				symbol = context.getSymbolTable().getSymbol( identifier , globalId );
			}
		}

		if ( symbol != null )
		{
			switch( symbol.getType() )
			{
				case LABEL:
					this.symbol = (ISymbol<? extends Number>) symbol;
					return;
				default:
					throw new RuntimeException("Internal error, don't know how to get value from "+symbol);
			}
		}
	}
}