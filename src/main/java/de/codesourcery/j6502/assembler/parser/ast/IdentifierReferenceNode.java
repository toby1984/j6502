package de.codesourcery.j6502.assembler.parser.ast;

import de.codesourcery.j6502.assembler.ICompilationContext;
import de.codesourcery.j6502.assembler.ISymbol;
import de.codesourcery.j6502.assembler.exceptions.NumberLiteralOutOfRangeException;
import de.codesourcery.j6502.assembler.parser.Identifier;
import de.codesourcery.j6502.utils.ITextRegion;

public class IdentifierReferenceNode extends ASTNode implements IValueNode , ICompilationContextAware
{
	public final Identifier identifier;
	private ISymbol<? extends Number> symbol;

	public IdentifierReferenceNode(Identifier id,ITextRegion region) {
		super(region);
		this.identifier = id;
	}

	@Override
	public String toString() {
		return identifier.value+" (symbol: "+symbol+")";
	}

	@Override
	public int evaluate() throws IllegalStateException
	{
		if ( ! isValueAvailable() ) {
			throw new IllegalStateException("getByte() called on unresolved symbol "+this);
		}
		return symbol.getValue().intValue();
	}

	@Override
	public byte getByteValue()
	{
		final int value = symbol.getValue().intValue();

		if ( value < -128 || value > 255 )
		{
			throw NumberLiteralOutOfRangeException.byteRange( (short) value );
		}
		return (byte) value;
	}

	@Override
	public short getWordValue()
	{
		final int value = evaluate();

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

	private LabelNode findPreviousGlobalLabel(IASTNode child) {

		IASTNode parent = child.getParent();
		if ( parent == null ) {
			return null;
		}
		final int idx = parent.indexOf( child );
		if ( idx == -1 ) {
			throw new IllegalArgumentException( child+" is not a child of "+parent);
		}
		LabelNode[] result = new LabelNode[1];
		for ( int i = idx-1; i >= 0 ; i--)
		{
			IASTNode n = parent.child(i);
			n.visitDepthFirst( node ->
			{
				if ( node instanceof LabelNode&& ((LabelNode) node).isGlobal()) {
					if ( result[0] == null ) {
						result[0] = (LabelNode) node;
					}
				}
			});
			if ( result[0] != null ) {
				return result[0];
			}
		}
		return findPreviousGlobalLabel(parent);
	}

	@Override
	public void visit(ICompilationContext context)
	{
	}

	@Override
	public void passFinished(ICompilationContext context)
	{
		if ( this.symbol != null ) {
			return;
		}

		ISymbol<?> symbol = null;
		LabelNode previousGlobalLabel = null;
		if ( context.getSymbolTable().isDefined( identifier , null ) ) { // first check whether this is something in the global scope
			symbol = context.getSymbolTable().getSymbol( identifier , null );
		}
		else if ( ( previousGlobalLabel = findPreviousGlobalLabel( this ) ) != null )
		{
			// assuming it's the identifier of a local label
			final Identifier globalId = previousGlobalLabel.identifier;
			if ( context.getSymbolTable().isDefined( identifier ,globalId ) )
			{
				symbol = context.getSymbolTable().getSymbol( identifier , globalId );
			}
		}

		if ( symbol != null )
		{
			context.debug( this , "Resolved identifier to "+symbol);
			switch( symbol.getType() )
			{
				case EQU:
				case LABEL:
					context.debug( this , "Identifier "+this.identifier+" resolves to: "+symbol);
					this.symbol = (ISymbol<? extends Number>) symbol;
					return;
				default:
					throw new RuntimeException("Internal error, don't know how to get value from "+symbol);
			}
		} else {
			context.debug( this , "Failed to resolve identifier "+this.identifier);
		}
	}
}