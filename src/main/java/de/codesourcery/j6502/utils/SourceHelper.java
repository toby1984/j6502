package de.codesourcery.j6502.utils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import de.codesourcery.j6502.assembler.parser.Token;

public class SourceHelper
{
	public static final class TextLocation
	{
		public final int lineNumber;
		public final int columnNumber;
		public final int absoluteOffset;

		public TextLocation(int lineNumber, int columnNumber,int absoluteOffset) {
			this.lineNumber = lineNumber;
			this.columnNumber = columnNumber;
			this.absoluteOffset = absoluteOffset;
		}

		@Override
		public String toString() {
			return "line "+lineNumber+" , column "+columnNumber+" (offset "+absoluteOffset+")";
		}

		@Override
		public int hashCode() {
			final int result = 31 * 1 + columnNumber;
			return 31 * result + lineNumber;
		}

		@Override
		public boolean equals(Object obj) {
			if ( obj instanceof TextLocation )
			{
				final TextLocation other = (TextLocation) obj;
				return columnNumber == other.columnNumber &&
					   lineNumber == other.lineNumber;
			}
			return false;
		}
	}

	private final Map<Integer,Integer> lineStartOffsetByLineNumber = new HashMap<>();
	private final Map<Integer,Integer> lineNumberByLineStartOffset = new HashMap<>();

	private final String source;

	public SourceHelper(String source)
	{
		this.source = source;
		try {
			parse( new ByteArrayInputStream( source.getBytes() ) );
		} catch (final IOException e) {
			throw new RuntimeException(e);
		}
	}

	public Integer getLineStartingOffset(TextLocation loc)
	{
		return getLineStartingOffset( loc.lineNumber );
	}

	public Integer getLineStartingOffset(int lineNo)
	{
		return lineStartOffsetByLineNumber.get( lineNo );
	}

	private void parse(InputStream in) throws IOException
	{
		int currentLine = 1; // line numbers are 1-based
		int lineStartOffset = 0;
		int currentOffset = 0;
		final byte[] buffer = new byte[1024];

		int bytesRead = 0;
		while ( ( bytesRead = in.read( buffer ) ) > 0 )
		{
			for ( int i = 0 ; i < bytesRead ; i++ ) {
				final byte b = buffer[i];
				if ( b == '\n' )
				{
					lineStartOffsetByLineNumber.put( currentLine , lineStartOffset );
					lineNumberByLineStartOffset.put( lineStartOffset , currentLine );
					currentLine++;
					currentOffset++;
					lineStartOffset = currentOffset;
				} else {
					currentOffset++;
				}
			}
		}
		lineStartOffsetByLineNumber.put( currentLine , lineStartOffset );
		lineNumberByLineStartOffset.put( lineStartOffset , currentLine );
	}


	public TextLocation getLocation(Token token)
	{
		return getLocation(token.offset);
	}

	public TextLocation getLocation(final int offset)
	{
		int minMaxOffset = -1;
		for ( final Entry<Integer, Integer> entry : lineNumberByLineStartOffset.entrySet() )
		{
			final Integer lineStartOffset = entry.getKey();
			if ( lineStartOffset <= offset && lineStartOffset > minMaxOffset ) {
				minMaxOffset = lineStartOffset;
			}
		}

		if ( minMaxOffset == -1 ) {
			return null;
		}
		final int column = (offset - minMaxOffset);
		final int lineNo = lineNumberByLineStartOffset.get( minMaxOffset );
		return new TextLocation( lineNo , column+1 , offset ); // column numbers are 1-based
	}

	public TextLocation getLocation(ITextRegion textRegion) {
		return getLocation( textRegion.getStartingOffset() );
	}

	public String getLineText(int lineNo)
	{
		final Integer startingOffset = getLineStartingOffset( lineNo );
		if ( startingOffset == null ) {
			return null;
		}
		int end = startingOffset;
		while ( end < source.length() && source.charAt(end) != '\n' ) {
			end++;
		}

		return source.substring( startingOffset , end );
	}

	public String getLineText(TextLocation location) {
		return getLineText(location.lineNumber);
	}
}