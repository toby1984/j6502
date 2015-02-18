package de.codesourcery.j6502.assembler;

public class NumberLiteralOutOfRangeException extends RuntimeException  {

	public final int actualValue;

	public NumberLiteralOutOfRangeException(String msg,int actualValue) {
		super(msg);
		this.actualValue = actualValue;
	}

	public static NumberLiteralOutOfRangeException byteRange(short actualValue) {
		return new NumberLiteralOutOfRangeException("Number literal out of range (0...255 or -127...+128): "+actualValue,actualValue);
	}

	public static NumberLiteralOutOfRangeException wordRange(int actualValue) {
		return new NumberLiteralOutOfRangeException("Number literal out of range (-32767...32768 or 0...65535): "+actualValue,actualValue);
	}
}
