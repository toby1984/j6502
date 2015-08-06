package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.utils.HexDump;

public final class Breakpoint {

	public final int address;
	public final boolean isOneshot;
	public final byte cpuFlagsMask;
	public final boolean checkCPUFlags;
	public final boolean isEnabled;
	
	public Breakpoint(int address, boolean isOneshot,boolean isEnabled)
	{
		this.address = address & 0xffff;
		this.isOneshot = isOneshot;
		this.cpuFlagsMask = 0;
		this.checkCPUFlags = false;
		this.isEnabled = isEnabled;
	}
	
	public Breakpoint(int address, boolean isOneshot,byte cpuFlagsMask,boolean isEnabled)
	{
		this.address = address & 0xffff;
		this.isOneshot = isOneshot;
		this.cpuFlagsMask = cpuFlagsMask;
		this.checkCPUFlags = true;
		this.isEnabled = isEnabled;
	}	

	@Override
	public String toString() {
		return "breakpoint @ "+HexDump.toAdr( address )+" (one-shot: "+isOneshot+")";
	}
	
	public Breakpoint withEnabled(boolean yesNo) 
	{
	    if ( checkCPUFlags ) {
	        return new Breakpoint(address,isOneshot,cpuFlagsMask,yesNo);
	    } 
	    return new Breakpoint( address , isOneshot , yesNo );
	}
	
	public boolean isTriggered(CPU cpu) 
	{
	    if ( ! isEnabled ) {
	        return false;
	    }
	    
		if ( ! checkCPUFlags ) {
			return true;
		}
		return (cpu.getFlagBits() & cpuFlagsMask) != 0;
	}
}