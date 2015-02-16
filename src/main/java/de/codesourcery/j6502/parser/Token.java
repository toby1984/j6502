package de.codesourcery.j6502.parser;

public final class Token {

	public final TokenType type;
	public final String text;
	public final int offset;

	public Token(TokenType type, String text, int offset)
	{
		if ( type == null ) {
			throw new IllegalArgumentException("type must not be NULL");
		}
		if ( text == null ) {
			throw new IllegalArgumentException("text must not be NULL");
		}
		this.type = type;
		this.text = text;
		this.offset = offset;
	}

	public boolean hasType(TokenType t) {
		return t.equals( this.type );
	}

	@Override
	public String toString() {
		return "Token[ "+type+" , text: >"+text+"< , offset: "+offset+" ]";
	}
}
