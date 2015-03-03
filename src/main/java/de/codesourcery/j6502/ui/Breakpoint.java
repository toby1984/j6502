package de.codesourcery.j6502.ui;

import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.utils.HexDump;

public final class Breakpoint {

	public final int address;
	public final boolean isOneshot;
	public final byte cpuFlagsMask;
	public final boolean checkCPUFlags;

	public Breakpoint(int address, boolean isOneshot)
	{
		this.address = address & 0xffff;
		this.isOneshot = isOneshot;
		this.cpuFlagsMask = 0;
		this.checkCPUFlags = false;
	}
	
	public Breakpoint(int address, boolean isOneshot,byte cpuFlagsMask)
	{
		this.address = address & 0xffff;
		this.isOneshot = isOneshot;
		this.cpuFlagsMask = cpuFlagsMask;
		this.checkCPUFlags = true;
	}	

	@Override
	public String toString() {
		return "breakpoint @ "+HexDump.toAdr( address )+" (one-shot: "+isOneshot+")";
	}
	
	public boolean isTriggered(CPU cpu) 
	{
		if ( ! checkCPUFlags ) {
			return true;
		}
		return (cpu.getFlagBits() & cpuFlagsMask) != 0;
	}
}