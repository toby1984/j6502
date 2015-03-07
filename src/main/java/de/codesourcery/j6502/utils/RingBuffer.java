package de.codesourcery.j6502.utils;

public class RingBuffer {

	private final byte[] data = new byte[1024];
	
	private int readPtr;
	private int writePtr;
	
	public RingBuffer() {
	}
	
	public void reset() {
		writePtr = readPtr = 0;
	}
	
	public boolean isEmpty() {
		return readPtr == writePtr;
	}
	
	public boolean isNotEmpty() {
		return readPtr != writePtr;
	}
	
	public boolean isFull() 
	{
		return ( (writePtr+1) % data.length ) == readPtr; 
	}
	
	public byte read() {
		if ( isEmpty() ) {
			throw new IllegalStateException("Buffer empty");
		}
		byte result = data[readPtr];
		readPtr = (readPtr+1) % data.length;
		return result;
	}
	
	public void write(byte value) {
		if ( isFull() ) {
			throw new IllegalStateException("Buffer full");
		}
		data[writePtr] = value;
		writePtr = (writePtr+1) % data.length;
	}
}
