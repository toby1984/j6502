package de.codesourcery.j6502.utils;

import junit.framework.TestCase;

public class MiscTest extends TestCase {

	public void testInvalidBCDToBinaryReturns00() {
		int input = 0xff;
		int out = Misc.bcdToBinary( input );
		assertEquals( 99 , out );
	}

	public void testBCDToBinary() {
		int input = 0x45;
		int out = Misc.bcdToBinary( input );
		assertEquals( 45 , out );
	}

	public void testBinaryToBCD()
	{
		int input = 45;
		int out = Misc.binaryToBCD( input );
		assertEquals( 0x45 , out );
	}

	public void testInvalidBinaryToBCDReturns99()
	{
		int input = 0xff;
		int out = Misc.binaryToBCD( input );
		assertEquals( 0x99 , out );
	}

}
