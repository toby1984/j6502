package de.codesourcery.j6502.assembler.parser.ast;

import de.codesourcery.j6502.assembler.ICompilationContext;
import de.codesourcery.j6502.assembler.Label;
import de.codesourcery.j6502.assembler.exceptions.DuplicateSymbolException;
import de.codesourcery.j6502.assembler.exceptions.ParseException;
import de.codesourcery.j6502.assembler.parser.Identifier;
import de.codesourcery.j6502.utils.ITextRegion;

public class LabelNode extends ASTNode implements ICompilationContextAware
{
	public final Identifier identifier;
	public final LabelNode parentIdentifier;

	public Label symbol;

	public LabelNode(Identifier id,ITextRegion region) {
		this(id,null,region);
	}

	public LabelNode(Identifier id,LabelNode parentLabel,ITextRegion region) {
		super(region);
		this.identifier = id;
		this.parentIdentifier = parentLabel;
	}

	public boolean isLocal() {
		return parentIdentifier != null;
	}

	public boolean isGlobal() {
		return parentIdentifier == null;
	}

	@Override
	public String toString()
	{
		return isLocal() ? "."+identifier.value : identifier.value+":";
	}

	@Override
	public void visit(ICompilationContext context)
	{
		final LabelNode label = this;
		if ( context.getPassNo() == 0 )
		{
			if ( label.isGlobal() ) {
				this.symbol = new Label( label.identifier );
			} else {
				this.symbol = new Label( label.identifier , label.parentIdentifier.identifier );
			}
			context.debug( this , "Created "+this.symbol);
		}

		context.debug( this , "Assigning address "+context.getCurrentAddress()+" to "+this.symbol);
		symbol.setValue( context.getCurrentAddress() );
		if ( context.getPassNo() == 0 )
		{
			context.debug( this , "Defined "+symbol);
			
			try {
				context.getSymbolTable().defineSymbol( symbol );
			} catch(DuplicateSymbolException e) {
				throw new ParseException( e.getMessage() , getTextRegion() , e );
			}
		}
	}

	@Override
	public void passFinished(ICompilationContext context) {
	}
}