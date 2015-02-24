package de.codesourcery.j6502.ui;

import de.codesourcery.j6502.utils.HexDump;

public final class Breakpoint {

	public final int address;
	public final boolean isOneshot;

	public Breakpoint(int address, boolean isOneshot)
	{
		this.address = address & 0xffff;
		this.isOneshot = isOneshot;
	}

	@Override
	public String toString() {
		return "breakpoint @ "+HexDump.toAdr( address )+" (one-shot: "+isOneshot+")";
	}
}
