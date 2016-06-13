package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public abstract class IMemoryRegion
{
    public static enum MemoryType {
        RAM,ROM,IOAREA;
    }
    
    private final MemoryType type;
	private final String identifier;
	private final AddressRange addressRange;
    protected final MemoryBreakpointsContainer breakpointsContainer;

	public IMemoryRegion(String identifier,MemoryType type,AddressRange range)
	{
	    this.type = type;
		this.identifier = identifier;
		this.addressRange = range;
		this.breakpointsContainer = new MemoryBreakpointsContainer( identifier , type , range );
	}
	
	public MemoryBreakpointsContainer getBreakpointsContainer() {
        return breakpointsContainer;
    }
	
	public abstract void reset();

	public String getIdentifier() {
		return identifier;
	}

	public AddressRange getAddressRange() {
		return addressRange;
	}

	public final void bulkRead(int startingAddress,final byte[] inputBuffer, final int len)
	{
		for ( int i = 0 ; i < len ; i++ , startingAddress++ )
		{
			inputBuffer[i] = (byte) readByte( startingAddress & 0xffff );
		}
	}

	public abstract void bulkWrite(int startingAddress,byte[] data, int datapos, int len);

	public abstract int readByte(int offset);
	
	public abstract int readWord(int offset);

	public abstract void writeWord(int offset,short value);
	
	public abstract void writeByte(int offset,byte value);

	public abstract String dump(int offset, int len);

	public abstract boolean isReadsReturnWrites(int offset);

	/**
	 * Reads a byte without triggering side-effects related to
	 * memory-mapped I/O (like clearing IRQs etc.).
	 *  
	 * @param offset
	 * @return
	 */
	public abstract int readByteNoSideEffects(int offset);

	@Override
	public String toString() {
		return addressRange+" - "+identifier;
	}
}
