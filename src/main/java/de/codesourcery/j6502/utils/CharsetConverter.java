package de.codesourcery.j6502.utils;

import java.io.ByteArrayOutputStream;
import java.util.HashMap;
import java.util.Map;

public class CharsetConverter
{
	private static final Map<Byte,Character> petToASCII = new HashMap<>();
	private static final Map<Character,Byte> asciiToPET = new HashMap<>();

	static
	{
		for ( int i = 0 ; i < 256 ; i++ ) {

			if ( i >= 65 && i <= 90 ) {
				petToASCII.put( (byte) i , (char) (97+(i-65)) );
				asciiToPET.put( (char) (97+(i-65)) , (byte) i );
			} else if ( i >= 97 && i <= 122 ) {
				petToASCII.put( (byte) i , (char) (65+(i-97)) );
				asciiToPET.put( (char) (65+(i-97)) , (byte) i );
			} else if ( i >= 193 && i <= 218 ) {
				petToASCII.put( (byte) i , (char) (97+(i-193)) );
				// asciiToPET.put( (char) (97+(i-193)) , (byte) i );
			} else {
				petToASCII.put( (byte) i , (char) i );
				asciiToPET.put( (char) i , (byte) i );
			}
		}
	}

	/**
	 * Converts PET ASCII into a string,replacing unprintable characters with '?'.
	 *
	 * @param data
	 * @return
	 */
	public static String petToString(byte[] data,int offset,int len)
	{
		StringBuilder buffer = new StringBuilder();
		for ( int i = 0 ; i < len ; i++ ) {
			char c = petToASCII( data[ offset + i] );
			if ( c >= 9 ) {
				buffer.append( c );
			} else {
				buffer.append("?");
			}
		}
		return buffer.toString();
	}

	public static byte asciiToPET(char value) {
		return asciiToPET.get( value );
	}

	public static String petToASCII(byte[] data)
	{
		final StringBuilder buffer = new StringBuilder();
		for ( byte b : data ) {
			buffer.append( petToASCII( b ) );
		}
		return buffer.toString();
	}

	public static char petToASCII(byte value) {
		return petToASCII.get( value );
	}

	public static byte[] asciiToPET(char[] charArray) 
	{
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		for ( char c : charArray ) {
			out.write( asciiToPET( c ) );
		}
		return out.toByteArray();
	}
}