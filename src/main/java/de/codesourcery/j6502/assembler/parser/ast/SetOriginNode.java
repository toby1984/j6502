package de.codesourcery.j6502.assembler.parser.ast;

import de.codesourcery.j6502.assembler.ICompilationContext;
import de.codesourcery.j6502.assembler.exceptions.ValueUnavailableException;
import de.codesourcery.j6502.utils.HexDump;
import de.codesourcery.j6502.utils.ITextRegion;

public class SetOriginNode extends ASTNode implements ICompilationContextAware {

	public SetOriginNode(ITextRegion r) {
		super(r);
	}

	@Override
	public void visit(ICompilationContext context)
	{
		final IValueNode child = (IValueNode) child(0);
		if ( child.isValueAvailable() ) {
			context.debug( this , "Setting origin to $"+HexDump.toHexBigEndian( child.getWordValue() ) );
			context.setCurrentAddress( child.getWordValue() );
		} else if ( context.getPassNo() > 0 ) {
			throw new ValueUnavailableException( child );
		}
	}

	@Override
	public void passFinished(ICompilationContext context) {
	}
}
