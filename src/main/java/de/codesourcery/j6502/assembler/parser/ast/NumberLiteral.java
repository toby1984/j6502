package de.codesourcery.j6502.assembler.parser.ast;

import de.codesourcery.j6502.assembler.exceptions.NumberLiteralOutOfRangeException;
import de.codesourcery.j6502.utils.ITextRegion;

public class NumberLiteral extends ASTNode implements IValueNode {

	public static enum Notation {
		DECIMAL,HEXADECIMAL;
	}

	public final short value;
	public final Notation notation;

	public NumberLiteral(short value,Notation n,ITextRegion region) {
		super(region);
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
		int v = value;
		v = v & 0xff;
		if ( v < -128 || v > 255 )
		{
			throw NumberLiteralOutOfRangeException.byteRange( value );
		}
		return (byte) v;
	}

	@Override
	public short getWordValue()
	{
		final int v = value;
		if ( v < -32768 || v > 65535 )
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
