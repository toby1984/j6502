package de.codesourcery.j6502.parser.ast;

public class NumberLiteral extends ASTNode {

	public static enum Notation {
		DECIMAL,HEXADECIMAL;
	}

	public final short value;
	public final Notation notation;

	public NumberLiteral(short value,Notation n) {
		this.value = value;
		this.notation = n;
	}

	@Override
	public String toString()
	{
		if ( notation == Notation.HEXADECIMAL ) {
			return "$"+Integer.toHexString( value );
		}
		return Short.toString( value );
	}
}
