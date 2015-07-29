package de.codesourcery.j6502.assembler.parser.ast;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

import de.codesourcery.j6502.assembler.ICompilationContext;
import de.codesourcery.j6502.utils.ITextRegion;

public class IncludeBinaryNode extends ASTNode implements ICompilationContextAware 
{
	public String path;
	
	public IncludeBinaryNode(String path,ITextRegion region) {
		super(region);
		this.path = path;
		
	}
	@Override
	public void visit(ICompilationContext context) 
	{
		try 
		{
			final byte[] buffer = new byte[1024];
			int len = -1;
			InputStream in = new FileInputStream( path );
			while ( (len = in.read( buffer ) ) > 0 ) 
			{
				for ( int i = 0 ; i < len ; i++ ) 
				{
					context.writeByte( buffer[i] );
				}
			}
			in.close();
		} catch(IOException e) {
			throw new RuntimeException("Failed to read binary data from filesystem resource '"+path+"'");
		}
		
	}

	@Override
	public void passFinished(ICompilationContext context) {
	}
}
