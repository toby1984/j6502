package de.codesourcery.j6502.assembler.parser.ast;

import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.assembler.exceptions.NumberLiteralOutOfRangeException;
import de.codesourcery.j6502.utils.ITextRegion;

public class NumberLiteral extends ASTNode implements IValueNode
{
	public static enum Notation
	{
		DECIMAL,HEXADECIMAL,BINARY;
	}

	private static final Pattern DECIMAL_NUMBER_PATTERN = Pattern.compile("^[0-9]+$");
	private static final Pattern BINARY_NUMBER_PATTERN = Pattern.compile("^[01]+$");
	private static final Pattern HEX_NUMBER_PATTERN = Pattern.compile("^[0-9a-fA-F]+$");

	public final int value;
	public final Notation notation;

	public NumberLiteral(int value,Notation n,ITextRegion region) {
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
		if ( notation == Notation.BINARY )
		{
			String s = Integer.toBinaryString( value & 0xffff );
			if ( s.length() < 8 ) {
				s = StringUtils.repeat("0" , 8 - s.length() )+s;
			} else if ( s.length() < 16 ) {
				s = StringUtils.repeat("0" , 16 - s.length() )+s;
			}
			return "%"+s;
		}
		// decimal
		return Integer.toString( value );
	}

	@Override
	public byte getByteValue()
	{
		if ( value < -128 || value > 255 )
		{
			throw NumberLiteralOutOfRangeException.byteRange( (short) value );
		}
		return (byte) value;
	}

	@Override
	public short getWordValue()
	{
		if ( value < -32768 || value > 65535 )
		{
			throw NumberLiteralOutOfRangeException.wordRange( value );
		}
		return (short) value;
	}

	@Override
	public boolean isValueAvailable() {
		return true;
	}

	// DECIMAL_NUMBER_PATTERN
	public static boolean isValidDecimalNumber(String s)
	{
		return s != null && DECIMAL_NUMBER_PATTERN.matcher(s).matches();
	}

	public static boolean isValidBinaryNumber(String s)
	{
		return s != null && BINARY_NUMBER_PATTERN.matcher(s).matches();
	}

	public static boolean isValidHexString(String s)
	{
		return s != null && HEX_NUMBER_PATTERN.matcher(s).matches();
	}

	@Override
	public int evaluate() throws IllegalStateException {
		return value & 0xffff;
	}
}