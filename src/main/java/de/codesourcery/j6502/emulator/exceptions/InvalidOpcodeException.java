package de.codesourcery.j6502.emulator.exceptions;

public class InvalidOpcodeException extends RuntimeException {

	public final int address;
	public final byte data;

	public InvalidOpcodeException(String msg , int address, byte data)
	{
		super( msg+" (offending opcode:  $"+Integer.toHexString( data & 0xff )+" @ $"+Integer.toHexString(address & 0xffff) );
		this.address = address;
		this.data = data;
	}
}
