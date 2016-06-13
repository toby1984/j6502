package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.apache.commons.lang.StringUtils;

public class MemoryBreakpointHelper 
{
	public static final int BREAKPOINT_TYPE_READ = 1;
	public static final int BREAKPOINT_TYPE_WRITE = 2;
	private static final int BREAKPOINT_HIT_READ_FLAG = 4;
	private static final int BREAKPOINT_HIT_WRITE_FLAG = 8;
	
	private final byte[] memoryBreakpoints;
	
	private boolean memoryBreakpointHit;
	
	private final List<MemoryBreakpoint> breakpoints = new ArrayList<>();
	
	public static final class MemoryBreakpoint 
	{
		public final int address;
		public final int typeMask;
		
		public MemoryBreakpoint(int address,int typeMask) 
		{
			this.address = address;
			this.typeMask = typeMask;
			assertMemoryBreakpointTypeValid(typeMask);
		}
		
		private void assertMemoryBreakpointTypeValid(int type) 
		{
			if ( type == 0 || (type & ~(BREAKPOINT_TYPE_READ|BREAKPOINT_TYPE_WRITE)) != 0 ) {
				throw new IllegalArgumentException("Invalid memory breakpoint type: "+type);
			}
		}		
		
		public boolean equals(MemoryBreakpoint other) 
		{
			if ( other instanceof MemoryBreakpoint) {
				return this.address == other.address && this.typeMask == other.typeMask;
			}
			return false;
		}
		
		public boolean isWrite() {
			return (typeMask & BREAKPOINT_TYPE_WRITE) != 0;
		}
		
		public boolean isRead() {
			return (typeMask & BREAKPOINT_TYPE_READ) != 0;
		}		
		
		@Override
		public String toString() 
		{
			final String type;
			if ( isWrite() && isRead() ) {
				type = " - READ|WRITE";
			} else if ( isWrite()) {
				type = " - WRITE";
			} else {
				type = " - READ";
			}
			return "$"+StringUtils.leftPad(Integer.toHexString( this.address) , 4 , '0' )+type;
		}
	}
	
	public MemoryBreakpointHelper(AddressRange range) 
	{
		this.memoryBreakpoints = new byte[range.getSizeInBytes()];
		Arrays.fill( this.memoryBreakpoints , (byte) 0 );
	}
	
	public void reset() 
	{
		clearAllBreakpointHits();
	}
	
	public final void setMemoryBreakpoint(MemoryBreakpoint bp) 
	{
		this.breakpoints.add(bp);
		this.memoryBreakpoints[ bp.address ] |= bp.typeMask;
	}
	
	public final void removeMemoryBreakpoint(MemoryBreakpoint bp) 
	{
		if ( breakpoints.remove( bp ) ) 
		{
			memoryBreakpoints[bp.address] &= ~bp.typeMask;
			memoryBreakpoints[bp.address] &= ~(bp.typeMask <<2); // clear breakpoint hit
		}
	}
	
	protected final void checkWriteBreakpoint(int address) 
	{
		if ( ! breakpoints.isEmpty() && ( memoryBreakpoints[ address ] & BREAKPOINT_TYPE_WRITE) != 0 ) 
		{
			memoryBreakpoints[ address ] |= BREAKPOINT_HIT_WRITE_FLAG;
			memoryBreakpointHit = true;
		}
	}
	
	protected final void checkReadBreakpoint(int address) 
	{
		if ( ! breakpoints.isEmpty() && ( memoryBreakpoints[ address ] & BREAKPOINT_TYPE_READ ) != 0 ) 
		{
			memoryBreakpoints[ address ] |= BREAKPOINT_HIT_READ_FLAG;
			memoryBreakpointHit = true;
		}
	}	
	
	protected final void checkReadWriteBreakpoint(int address) 
	{
		if ( ! breakpoints.isEmpty() ) 
		{
			final int memValue = memoryBreakpoints[ address ];
			if ( (memValue & (BREAKPOINT_TYPE_READ|BREAKPOINT_TYPE_WRITE)) != 0 ) 
			{
				if ( (memValue & BREAKPOINT_TYPE_READ) != 0 ) {
					memoryBreakpoints[ address ] |= BREAKPOINT_HIT_READ_FLAG;
				} 
				if ( (memValue & BREAKPOINT_TYPE_WRITE) != 0 ) {
					memoryBreakpoints[ address ] |= BREAKPOINT_HIT_WRITE_FLAG;
				}
				memoryBreakpointHit = true;
			}
		}
	}
	
	public final List<MemoryBreakpoint> getMemoryBreakpoints(List<MemoryBreakpoint> resultList) 
	{
		return new ArrayList<>( this.breakpoints );
	}
	
	public final void getMemoryBreakpointHits(List<MemoryBreakpoint> resultList) 
	{
		resultList.clear();
		if ( ! memoryBreakpointHit ) 
		{
			return;
		}
		for ( int i = 0 , len = this.breakpoints.size() ; i < len ; i++ ) 
		{
			final MemoryBreakpoint bp = this.breakpoints.get(i);
			if ( (memoryBreakpoints[ bp.address ] & bp.typeMask<<2 ) != 0 ) 
			{
				System.out.println("getMemoryBreakpointHits(): Breakpoint hit: "+bp);
				resultList.add( bp );
			}
		}
	}
	
	public void clearAllBreakpointHits() 
	{
		if ( memoryBreakpointHit ) 
		{
			for ( int i = 0 ; i < memoryBreakpoints.length ; i++ ) 
			{
				memoryBreakpoints[i] &= ~(BREAKPOINT_HIT_READ_FLAG|BREAKPOINT_HIT_WRITE_FLAG);
			}	
			memoryBreakpointHit = false;
		}
	}
	
	public boolean isBreakpointHit() {
		return memoryBreakpointHit;
	}
	
	public final void clearAllMemoryBreakpoints() 
	{
		Arrays.fill(this.memoryBreakpoints,(byte) 0 );
		this.breakpoints.clear();
		memoryBreakpointHit = false;
	}	
}
