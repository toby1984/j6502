package de.codesourcery.j6502.ui;

public final class Breakpoint {

	public final short address;
	public final boolean isOneshot;
	
	public Breakpoint(short address, boolean isOneshot) 
	{
		this.address = address;
		this.isOneshot = isOneshot;
	}
}
