package de.codesourcery.j6502.assembler.parser;

import java.util.regex.Pattern;

public final class Identifier {

	private static final Pattern PATTERN =Pattern.compile("^[_a-zA-Z]{1}[a-zA-Z0-9]+[_0-9a-zA-Z]*");

	public final String value;

	public Identifier(String s)
	{
		if ( ! isValidIdentifier(s) ) {
			throw new IllegalArgumentException("Not a valid identifier");
		}
		this.value = s;
	}

	public static final boolean isValidIdentifier(String s) 
	{
		if ( s != null && PATTERN.matcher( s ).matches() ) 
		{
			switch ( s.toLowerCase() ) // avoid identifiers that might get mixed up with registers 
			{
				case "x":
				case "y":
					return false;
				default:
					return true;
			}
		}
		return false;
	}

	@Override
	public String toString() {
		return value;
	}

	@Override
	public int hashCode() {
		return 31 + value.hashCode();
	}

	@Override
	public boolean equals(Object obj) {
		if ( obj instanceof Identifier) {
			return this.value.equals( ((Identifier) obj).value );
		}
		return false;
	}
}
