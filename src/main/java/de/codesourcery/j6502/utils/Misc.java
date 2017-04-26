package de.codesourcery.j6502.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

public class Misc
{
    private static final Pattern VALID_HEX_STRING = Pattern.compile("^\\$?([0-9a-fA-f]+)$");  
    
	private static final char[] BCD = new char[] { '0' , '1' , '2' , '3' , '4' , '5' , '6' , '7' , '8' , '9' };

	public static int bcdToBinary(int bcdValue) {

		int loNibble = bcdValue & 0b1111;
		int hiNibble = (bcdValue >> 4 ) & 0b1111;

		if ( loNibble > 9 ) {
			loNibble = 9;
		}
		if ( hiNibble > 9 ) {
			hiNibble = 9;
		}
		return hiNibble*10+loNibble;
	}
	
	public static String to16BitHex(int value) {
	    return "$"+StringUtils.leftPad( Integer.toHexString( value & 0xffff ) , 4 , '0' );
	}
	
    public static String to32BitHex(int value) {
        return "$"+StringUtils.leftPad( Integer.toHexString( value ) , 8 , '0' );
    }	
	
    public static String to8BitHex(int value) {
        return StringUtils.leftPad( Integer.toHexString( value & 0xff ) , 2 , '0' );
    }	
    
    public static String to8BitBinary(int value) {
        return StringUtils.leftPad( Integer.toBinaryString( value & 0xff ) , 8 , '0' );
    }    
    
    public static Integer parseHexAddress(String input) 
    {
        if ( input != null ) 
        {
            final Matcher matcher = VALID_HEX_STRING.matcher( input.trim() );
            if ( matcher.matches() ) {
                return Integer.parseInt( matcher.group(1).toLowerCase() , 16 );
            }
        }
        return null;
    }
    
    public static boolean isValidHexAddress(String input) 
    {
        return input != null && VALID_HEX_STRING.matcher( input ).matches();
    }    

	public  static int binaryToBCD(int value)
	{
		// BCD = 00 - 99
		value = value & 0xff;
		if ( value > 99 ) {
			value = 99;
		}
		final String s = StringUtils.leftPad( Integer.toString(value) , 2 , "0" ); // "10"

		final char lowNibble = s.charAt(1);
		final char hiNibble = s.charAt(0);

		int hiIdx = -1;
		int lowIdx = -1;
		for (int i = 0; i < BCD.length; i++)
		{
			final char toTest = BCD[i];
			if ( toTest == lowNibble ) {
				lowIdx=i;
			}
			if ( toTest == hiNibble ) {
				hiIdx=i;
			}
		}
		return hiIdx<<4 | lowIdx;
	}
}
