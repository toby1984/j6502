package de.codesourcery.j6502.utils;

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

	public static byte asciiToPET(char value) {
		return asciiToPET.get( value );
	}

	public static char petToASCII(byte value) {
		return petToASCII.get( value );
	}
}