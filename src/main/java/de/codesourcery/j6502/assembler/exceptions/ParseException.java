package de.codesourcery.j6502.assembler.exceptions;

import de.codesourcery.j6502.assembler.parser.Token;
import de.codesourcery.j6502.utils.ITextRegion;
import de.codesourcery.j6502.utils.SourceHelper;
import de.codesourcery.j6502.utils.SourceHelper.TextLocation;

public class ParseException extends RuntimeException implements ITextLocationAware {

	public final int offset;

	public ParseException(String message,Token token)
	{
		this( message , token.offset);
	}

	public ParseException(String message,ITextRegion region)
	{
		this( message ,region == null ? -1 : region.getStartingOffset() );
	}

	public ParseException(String message,ITextRegion region,Throwable t)
	{
		this( message ,region == null ? -1 : region.getStartingOffset() , t  );
	}

	public ParseException(String message,int offset)
	{
		this(message,offset,null);
	}

	public ParseException(String message,int offset,Throwable t)
	{
		super(message,t);
		this.offset = offset;
	}

	@Override
	public TextLocation getTextLocation(SourceHelper helper)
	{
		if ( offset > 0 ) {
			return helper.getLocation( offset );
		}
		return null;
	}
}
