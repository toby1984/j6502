package de.codesourcery.j6502.utils;

import junit.framework.TestCase;

public class SourceHelperTest extends TestCase
{
	public void testCountLines1() {

		final String source = "1\n"+
		"2\n"+
		"3";

		final SourceHelper helper = new SourceHelper( source );
		assertEquals( "1" , helper.getLineText( 1 ) );
		assertEquals( "2" , helper.getLineText( 2 ) );
		assertEquals( "3" , helper.getLineText( 3 ) );
	}

	public void testCountLines2() {

		final String source = "1\n"+
		"2\n"+
		"3\n";

		final SourceHelper helper = new SourceHelper( source );
		assertEquals( "1" , helper.getLineText( 1 ) );
		assertEquals( "2" , helper.getLineText( 2 ) );
		assertEquals( "3" , helper.getLineText( 3 ) );
	}

	public void testLineNoByOffset() {

		final String source = "xxx\n"+
		"yyy\n"+
		"zzz";

		final SourceHelper helper = new SourceHelper( source );
		assertEquals( new Integer(0) , helper.getLineStartingOffset( 1 ) );
		assertEquals( new Integer(4) , helper.getLineStartingOffset( 2 ) );
		assertEquals( new Integer(8) , helper.getLineStartingOffset( 3 ) );
	}
}
