package de.codesourcery.j6502.utils;

import java.util.Arrays;

public class BitOutputStream
{
	private int bitsWritten;
	private byte[] buffer = new byte[1024];

	public void writeBytes(String s)
	{
		for ( int i = 0 ; i < s.length() ; i++ ) {
			writeByte( s.charAt(i) );
		}
	}

	public void copyFrom(BitOutputStream other) {

		final int bitsAvailable = other.getBitsWritten();
		final BitStream in = new BitStream( other.toByteArray() , bitsAvailable );
		for ( int i = bitsAvailable ; i >= 0 ; i-- )
		{
				writeBit( in.readBit() );
		}
	}

	public void writeByte(char c)
	{
		writeByte((int) c);
	}

	public void writeBits(int value,int bitCount)
	{
		int mask = 1;
		for ( int i = 0 ; i < bitCount ; i++ )
		{
			writeBit( value & mask );
			mask = mask << 1;
		}
	}

	public void writeDWord(int value) {
		int mask = 0xff;
		for ( int i =0 ; i < 4 ; i++ )
		{
			writeByte( value & mask );
			mask = mask << 8;
		}
	}

	public void clear() {
		bitsWritten = 0;
		Arrays.fill( buffer , (byte) 0 );
	}

	public void writeWord(int value)
	{

		final int lo = value & 0xff;
		writeByte( lo );

		final int hi = (value & 0xff00) >>> 8;
		writeByte( hi );
	}

	public void writeByte(int value)
	{
		final int offset = bitsWritten >>> 3;
		if ( offset >= buffer.length ) {
			growBuffer();
		}
		if ( (offset << 3) == bitsWritten ) {
			buffer[ offset ] = (byte) value;
			bitsWritten+= 8;
		} else
		{
			int mask = 0b1000_0000;
			for ( int i = 7 ; i >= 0 ; i-- )
			{
				writeBit( ( value & mask) );
				mask = mask >>> 1;
			}
		}
	}

	public int getBitsWritten() {
		return bitsWritten;
	}

	private void growBuffer()
	{
		final byte[] tmp = new byte[buffer.length*2];
		System.arraycopy( buffer , 0 , tmp , 0 , buffer.length );
		buffer = tmp;
	}

	public void writeBit(int bit)
	{
		if ( bit != 0 )
		{
			final int offset = bitsWritten/8;
			if ( offset >= buffer.length ) {
				growBuffer();
			}
			buffer[ offset ] |= 1<<(7-bitsWritten % 8);
			bitsWritten++;
		}
	}

	public byte[] toByteArray()
	{
		final int len = (int) Math.ceil( bitsWritten/8f);
		final byte[] result = new byte[ len ];
		System.arraycopy( buffer , 0 , result , 0 , len );
		return result;
	}
}
