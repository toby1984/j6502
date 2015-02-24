package de.codesourcery.j6502.ui;

import de.codesourcery.j6502.utils.HexDump;

public final class Breakpoint {

	public final short address;
	public final boolean isOneshot;

	public Breakpoint(short address, boolean isOneshot)
	{
		this.address = address;
		this.isOneshot = isOneshot;
	}

	@Override
	public String toString() {
		return "breakpoint @ "+HexDump.toAdr( address )+" (one-shot: "+isOneshot+")";
	}
}
