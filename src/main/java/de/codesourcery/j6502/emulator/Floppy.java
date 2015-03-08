package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.utils.RingBuffer;

public class Floppy extends SerialDevice 
{
	public Floppy(int deviceAddress) 
	{
		super(deviceAddress);
	}

	@Override
	public void onATN() {
		// TODO: Stop sending if the device is currently sending
	}	
	
	@Override
	public void tick() {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onUntalk(RingBuffer receiveBuffer) {
		// TODO Auto-generated method stub
		
	}

	@Override
	protected void onUnlisten(RingBuffer receiveBuffer) {
		// TODO Auto-generated method stub
		
	}
}