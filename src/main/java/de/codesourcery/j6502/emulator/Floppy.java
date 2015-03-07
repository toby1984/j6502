package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.utils.RingBuffer;

public class Floppy extends SerialDevice 
{
	public Floppy(int deviceAddress) 
	{
		super(deviceAddress);
	}

	@Override
	protected void processCommand(RingBuffer buffer) 
	{
		if ( isListening() ) {
			
		} else if ( isTalking() ) {
			
		} else {
			throw new IllegalStateException("Neither listener nor talker");
		}
	}	
}