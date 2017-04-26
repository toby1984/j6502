package de.codesourcery.j6502.utils;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.emulator.AddressRange;
import de.codesourcery.j6502.emulator.IMemoryRegion;
import de.codesourcery.j6502.emulator.IMemoryRegion.MemoryType;

public class HexDump {

	private int bytesPerLine = 16;

	public static final HexDump INSTANCE = new HexDump();

	private boolean printAddress = true;

	public static void main(String[] args) {

		final byte[] test = "dasisteinlangertext".getBytes();
		System.out.println( new HexDump().dump((short) 0,test,0, test.length ) );
	}

	public void setPrintAddress(boolean printAddress) {
		this.printAddress = printAddress;
	}

	public String dump(short startingAddress, IMemoryRegion region, int offset, int len)
	{
		return dump(startingAddress,(short) 0,region,offset,len,false);
	}

	public String dump(short startingAddress, short addressToMark,IMemoryRegion region, int offset, int len)
	{
		return dump(startingAddress,addressToMark,region,offset,len,true);
	}

	private String dump(short startingAddress, short addressToMark,IMemoryRegion region, int offset, int len,boolean mark)
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

			if ( printAddress ) {
				lineBuffer.append( toHexBigEndian( currentAddress ) ).append(": ");
			}
			currentAddress += bytesPerLine;

			int bytesOnLine = 0;
			for ( int bytes = bytesPerLine ; bytes > 0 && l > 0 ; l--)
			{
				final int adr = index % region.getAddressRange().getSizeInBytes();
				index++;
				final byte value = (byte) region.readByteNoSideEffects( adr & 0xffff );
				final char intValue = CharsetConverter.petToASCII( value );
				final boolean doMark = mark && adr == addressToMark;
				char toAppend;
				if ( intValue >= 32 && intValue < 127 ) {
					toAppend = CharsetConverter.petToASCII( value );
				} else {
					toAppend = '.';
				}
				if ( doMark ) {
					asciiBuffer.append("[").append( toAppend ).append("]");
				} else {
					asciiBuffer.append( toAppend );
				}

				if ( lineBuffer.length() > 0 ) {
					lineBuffer.append(" ");
				}
				if ( doMark ) {
					lineBuffer.append( "[").append( byteToString( value ) ).append("]");
				} else {
					lineBuffer.append( byteToString( value ) );
				}
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
		return dump(startingAddress,new IMemoryRegion("dummy", MemoryType.RAM,new AddressRange( 0,len) ) {

			@Override
			public void reset() {
				throw new UnsupportedOperationException();
			}

			@Override
			public boolean isReadsReturnWrites(int offset) {
	             throw new UnsupportedOperationException();
			}

			@Override
			public void bulkWrite(int startingAddress, byte[] data,int datapos, int len) {
				throw new UnsupportedOperationException();
			}

			@Override
			public int readByte(int offset) {
				return data[offset & 0xffff] & 0xff;
			}

			@Override
			public int readWord(int offset) {
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
			
			public void writeByteNoSideEffects(int offset, byte value) {
                throw new UnsupportedOperationException();			    
			}

			@Override
			public String dump(int offset, int len) {
				throw new UnsupportedOperationException();
			}

            @Override
            public int readByteNoSideEffects(int offset) {
                return data[offset & 0xffff] & 0xff;
            }
		} , offset , len );
	}

	public static String byteToString(byte b)
	{
		int value = b;
		value &= 0xff;
		final String byteString = Integer.toString( value , 16 );
		return StringUtils.leftPad( byteString , 2 , '0' );
	}

	public static String toAdr(int b) {
		return "$"+toHexBigEndian((short) b);
	}

	public static String toHexBigEndian(int b)
	{
		final int value = b;
		final int low = value & 0xff;
		final int hi = (value>>8) & 0xff;
		final String hiNibble = StringUtils.leftPad( Integer.toString( hi , 16 ) , 2 , '0' );
		final String loNibble = StringUtils.leftPad( Integer.toString( low , 16) , 2 , '0' );
		return hiNibble+loNibble;
	}

	public static String toHex(short b)
	{
		final int value = b;
		final int low = value & 0xff;
		final int hi = (value>>8) & 0xff;
		final String hiNibble = StringUtils.leftPad( Integer.toString( hi , 16 ) , 2 , '0' );
		final String loNibble = StringUtils.leftPad( Integer.toString( low , 16) , 2 , '0' );
		return loNibble+hiNibble;
	}

	public static String toHex(byte value)
	{
		final int low = value & 0xff;
		final String loNibble = StringUtils.leftPad( Integer.toString( low , 16) , 2 , '0' );
		return loNibble;
	}

	public void setBytesPerLine(int i) {
		this.bytesPerLine = i;
	}

	public static String toBinaryString(byte v) {
		return "%"+StringUtils.leftPad( Integer.toBinaryString( v & 0xff ) , 8 , '0' );
	}
}