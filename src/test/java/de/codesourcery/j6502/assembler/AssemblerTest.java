package de.codesourcery.j6502.assembler;

import junit.framework.TestCase;
import de.codesourcery.j6502.parser.Lexer;
import de.codesourcery.j6502.parser.Parser;
import de.codesourcery.j6502.parser.Scanner;

public class AssemblerTest extends TestCase {

	private void assertCompilesTo(String s, int... expected)
	{
		final Parser p = new Parser(new Lexer(new Scanner(s)));

		final Assembler a = new Assembler();
		final byte[] actual = a.assemble( p.parse() );
		if ( actual.length != expected.length ) {
			fail("Length mismatch: actual = "+actual.length+" , expected = "+expected.length);
		}
		for ( int i =  0 ; i < expected.length ; i++ ) {
			final byte ex = (byte) (expected[i] & 0xff);
			assertEquals( ex , actual[i] );
		}
	}

	public void testLDA() {

/*
					// MODE           SYNTAX         HEX LEN TIM
					// Absolute      LDA $4400     $AD  3   4
*/
		assertCompilesTo( "LDA $4400" , 0xad, 0x00, 0x44 );

/*
					// MODE           SYNTAX         HEX LEN TIM
					// Absolute,X    LDA $4400,X   $BD  3   4+
*/
		assertCompilesTo( "LDA $4400,X" , 0xbd,0x00,0x44);

/*
					// MODE           SYNTAX         HEX LEN TIM
					// Absolute,Y      LDA $4400,Y   $B9  3   4+
*/
		assertCompilesTo( "LDA $4400,Y" , 0xb9,0x00,0x44);
/*
					// MODE           SYNTAX         HEX LEN TIM
					// Zero Page,X   LDA $44,X     $B5  2   4
*/
		assertCompilesTo( "LDA $44,X" , 0xb5,0x44);

/*
					// MODE           SYNTAX         HEX LEN TIM
					// # Immediate     LDA #$44      $A9  2   2
*/
		assertCompilesTo( "LDA #$44" , 0xa9,0x44);
/*
					// MODE           SYNTAX         HEX LEN TIM
					// Indirect,X      LDA ($44,X)   $A1  2   6
*/
		assertCompilesTo( "LDA ($44,X)" , 0xa1,0x44);

/*
					// MODE           SYNTAX         HEX LEN TIM
					// Indirect,Y      LDA ($44),Y   $B1  2   5+
*/
		assertCompilesTo( "LDA ($44),Y" , 0xb1,0x44);

/*
					// MODE           SYNTAX         HEX LEN TIM
					// Zero Page     LDA $44       $A5  2   3		#
 */
		assertCompilesTo( "LDA $44" , 0xa5,0x44);
	}
}
