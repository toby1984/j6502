package de.codesourcery.j6502.utils;

public final class RingBuffer {

	private final byte[] data = new byte[1024];

	private int readPtr;
	private int writePtr;

	private int bytesInBuffer;

	public RingBuffer() {
	}

	public void reset() {
		writePtr = readPtr = 0;
		bytesInBuffer = 0;
	}

	public boolean isEmpty() {
		return bytesInBuffer == 0;
	}

	public boolean isNotEmpty() {
		return bytesInBuffer != 0;
	}

	public int getBytesAvailable() {
		return bytesInBuffer;
	}

	public int getRemainingBytesFree() {
		return data.length - bytesInBuffer;
	}

	public byte[] readFully()
	{
		final byte[] result = new byte[ bytesInBuffer ];
		for ( int offset = 0 ; ! isEmpty() ; )
		{
			result[offset++] = read();
		}
		return result;
	}

	public int read(byte[] data)
	{
		int offset = 0;
		final int len = data.length;
		while ( ! isEmpty() && offset < len)
		{
			data[offset++] = read();
		}
		return offset;
	}

	public boolean isFull()
	{
		return bytesInBuffer == data.length;
	}

	public byte read() {
		if ( isEmpty() ) {
			throw new IllegalStateException("Buffer empty");
		}
		byte result = data[readPtr];
		readPtr = (readPtr+1) % data.length;
		bytesInBuffer--;
		return result;
	}

	public void write(byte value) {
		if ( isFull() ) {
			throw new IllegalStateException("Buffer full");
		}
		data[writePtr] = value;
		writePtr = (writePtr+1) % data.length;
		bytesInBuffer++;
	}
}
