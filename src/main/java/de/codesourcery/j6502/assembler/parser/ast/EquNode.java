package de.codesourcery.j6502.assembler.parser.ast;

import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.assembler.ICompilationContext;
import de.codesourcery.j6502.assembler.exceptions.DuplicateSymbolException;
import de.codesourcery.j6502.assembler.exceptions.ParseException;
import de.codesourcery.j6502.assembler.parser.Equ;
import de.codesourcery.j6502.assembler.parser.Identifier;
import de.codesourcery.j6502.utils.ITextRegion;

public class EquNode extends ASTNode implements ICompilationContextAware
{
	private Identifier identifier;
	
	public EquNode(Identifier identifier,ITextRegion region) 
	{
		super(region);
		Validate.notNull(identifier, "identifier must not be NULL");
		this.identifier = identifier;
	}
	
	public Identifier getIdentifier() {
		return identifier;
	}

	@Override
	public void visit(ICompilationContext context) 
	{
		if ( context.getPassNo() == 0 ) 
		{
			try {
				context.getSymbolTable().defineSymbol( new Equ( identifier ) );
			} catch(DuplicateSymbolException e) {
				throw new ParseException( e.getMessage() , getTextRegion() ,e ); 
			}
		} 
		else 
		{
			final Equ equ = (Equ) context.getSymbolTable().getSymbol( identifier , null );
			final int value = ((IValueNode) child(0)).getWordValue();
			equ.setValue( value ); 
		}
	}

	@Override
	public void passFinished(ICompilationContext context) {
	}
}
