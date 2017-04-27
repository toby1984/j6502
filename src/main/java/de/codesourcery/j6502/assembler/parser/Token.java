package de.codesourcery.j6502.assembler.parser;

import java.util.List;

import de.codesourcery.j6502.utils.ITextRegion;
import de.codesourcery.j6502.utils.TextRegion;

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
	
	public boolean isCharacters(String s) 
	{
	    return hasType(TokenType.CHARACTERS) && s.equalsIgnoreCase( text );
	}

	public boolean hasType(TokenType t) {
		return t.equals( this.type );
	}

	public ITextRegion region() {
		return new TextRegion(offset,text.length());
	}

	@Override
	public String toString() {
		return "Token[ "+type+" , text: >"+text+"< , offset: "+offset+" ]";
	}

	public Operator operator()
	{
		if ( ! hasType(TokenType.OPERATOR ) ) {
			throw new UnsupportedOperationException("You may not invoke operator() on a token of type "+type+",offending token: "+this);
		}
		final List<Operator> operators = Operator.getMatchingOperators( this.text );
		if ( operators.size() != 1 ) {
			throw new RuntimeException("Internal error, expected exactly one operator for token "+this+" but got "+operators);
		}
		return operators.get(0);
	}
}
