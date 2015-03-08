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
