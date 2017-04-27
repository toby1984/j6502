package de.codesourcery.j6502.assembler.parser.ast;

import de.codesourcery.j6502.assembler.ICompilationContext;
import de.codesourcery.j6502.assembler.exceptions.ValueUnavailableException;
import de.codesourcery.j6502.utils.CharsetConverter;
import de.codesourcery.j6502.utils.ITextRegion;

public class InitializedMemoryNode extends ASTNode implements ICompilationContextAware {

	public static enum Type {
		BYTES,
		WORDS;
	}

	public final InitializedMemoryNode.Type type;

	public InitializedMemoryNode(ITextRegion region,InitializedMemoryNode.Type type) {
		super(region);
		if ( type == null ) {
			throw new IllegalArgumentException("type must not be NULL");
		}
		this.type = type;
	}

	@Override
	public void visit(ICompilationContext context)
	{
		for ( final IASTNode child : children )
		{
		    if ( child instanceof StringLiteralNode) 
		    {
		        for ( char c : ((StringLiteralNode) child).value.toCharArray() ) 
		        {
		            byte value = CharsetConverter.asciiToPET( c );
		            context.writeByte( value );
		        }
		    } 
		    else if ( child instanceof IValueNode)
			{
				final IValueNode value = (IValueNode) child;
				if ( value.isValueAvailable() ) {
					switch( type )
					{
						case BYTES:
							context.writeByte( value.getByteValue() );
							break;
						case WORDS:
						    context.writeWord( value.getWordValue() );
						    break;
						default:
							throw new RuntimeException("Internal error,unhandled type "+type);
					}
				}
				else if ( context.getPassNo() > 0 )
				{
					throw new ValueUnavailableException( value );
				}
			} else {
			    throw new RuntimeException("Internal error,unhandled child node: "+child); 
			}
		}
	}

	@Override
	public void passFinished(ICompilationContext context) {
	}
}
