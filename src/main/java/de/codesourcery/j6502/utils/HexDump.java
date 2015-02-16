package de.codesourcery.j6502.utils;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.emulator.AddressRange;
import de.codesourcery.j6502.emulator.IMemoryRegion;

public class HexDump {

	private final short bytesPerLine = 16;

	public static final HexDump INSTANCE = new HexDump();

	public static void main(String[] args) {

		final byte[] test = "dasisteinlangertext".getBytes();
		System.out.println( new HexDump().dump((short) 0,test,0, test.length ) );
	}

	public String dump(short startingAddress, IMemoryRegion region, int offset, int len)
	{
		short currentAddress = startingAddress;
		final StringBuilder buffer = new StringBuilder();
		final StringBuilder lineBuffer = new StringBuilder();
		final StringBuilder asciiBuffer = new StringBuilder();

		for ( int l = len , index = offset ; l > 0 ; )
		{
			lineBuffer.setLength( 0 );
			asciiBuffer.setLength(0);

			if ( buffer.length() > 0 ) {
				lineBuffer.append("\n");
			}

			lineBuffer.append( toHexBigEndian( currentAddress ) ).append(": ");
			currentAddress += bytesPerLine;

			int bytesOnLine = 0;
			for ( int bytes = bytesPerLine ; bytes > 0 && l > 0 ; l--)
			{
				final byte value = region.readByte(index++);
				int intValue = value;
				intValue &= 0xff;
				if ( intValue >= 32 && intValue < 127 ) {
					asciiBuffer.append( (char) intValue );
				} else {
					asciiBuffer.append( '.' );
				}

				if ( lineBuffer.length() > 0 ) {
					lineBuffer.append(" ");
				}
				lineBuffer.append( toHex( value ) );
				bytesOnLine++;
				bytes--;
			}

			while ( bytesOnLine < bytesPerLine )
			{
				lineBuffer.append("   ");
				bytesOnLine++;
			}
			buffer.append( lineBuffer ).append(" ").append( asciiBuffer );
		}
		return buffer.toString();
	}

	public String dump(short startingAddress , byte[] data,int offset,int len)
	{
		return dump(startingAddress,new IMemoryRegion("dummy", new AddressRange( 0,len) ) {

			@Override
			public void reset() {
				throw new UnsupportedOperationException();
			}

			@Override
			public void bulkWrite(int startingAddress, byte[] data,int datapos, int len) {
				throw new UnsupportedOperationException();
			}

			@Override
			public byte readByte(int offset) {
				return data[offset];
			}

			@Override
			public short readWord(int offset) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void writeWord(int offset, short value) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void writeByte(int offset, byte value) {
				throw new UnsupportedOperationException();
			}
		} , offset , len );
	}

	public static String toHex(byte b)
	{
		int value = b;
		value &= 0xff;
		return StringUtils.leftPad( Integer.toString( value , 16 ) , 2 , '0' );
	}

	public static String toHexBigEndian(short b)
	{
		final int value = b;
		final int low = value & 0xff;
		final int hi = (value>>8) & 0xff;
		return StringUtils.leftPad( Integer.toString( hi , 16 )+Integer.toString(low,16) , 4 , '0' );
	}

	public static String toHex(short b)
	{
		final int value = b;
		final int low = value & 0xff;
		final int hi = (value>>8) & 0xff;
		return StringUtils.leftPad( Integer.toString( low, 16 )+Integer.toString(hi,16) , 4 , '0' );
	}
}