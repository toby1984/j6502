package de.codesourcery.j6502.assembler.exceptions;

import de.codesourcery.j6502.utils.SourceHelper;
import de.codesourcery.j6502.utils.SourceHelper.TextLocation;

public class ParseException extends RuntimeException implements ITextLocationAware {

	public final int offset;

	public ParseException(String message,int offset)
	{
		super(message);
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
