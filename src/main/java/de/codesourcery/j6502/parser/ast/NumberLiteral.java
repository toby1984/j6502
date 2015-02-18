package de.codesourcery.j6502.parser.ast;

import de.codesourcery.j6502.assembler.NumberLiteralOutOfRangeException;

public class NumberLiteral extends ASTNode implements NumericValue {

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

	@Override
	public byte getByteValue()
	{
		if ( value < -128 || value > 255 )
		{
			throw NumberLiteralOutOfRangeException.byteRange( value );
		}
		return (byte) value;
	}

	@Override
	public short getWordValue() {
		if ( value < -32768 || value > 65535 )
		{
			throw NumberLiteralOutOfRangeException.wordRange( value );
		}
		return value;
	}

	@Override
	public boolean isValueAvailable() {
		return true;
	}
}
