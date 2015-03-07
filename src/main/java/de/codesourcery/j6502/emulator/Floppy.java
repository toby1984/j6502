package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.emulator.IECBus.Command;

public class Floppy extends SerialDevice {

	private final int deviceAddress;
	
	private boolean listening = false;
	private boolean talking = false;
	
	public Floppy(int deviceAddress) 
	{
		this.deviceAddress = deviceAddress;
	}
	
	public int getPrimaryAddress() {
		return deviceAddress;
	}
	
	private void assertListening() {
		if ( ! listening ) {
			throw new IllegalStateException("Not listening ?");
		}
	}
	
	private void assertNotListening() {
		if ( listening ) {
			throw new IllegalStateException("Listening ?");
		}
	}	
	
	private void assertTalking() {
		if ( ! talking ) {
			throw new IllegalStateException("Not talking ?");
		}
	}
	
	private void assertNotTalking() {
		if ( talking ) {
			throw new IllegalStateException("Talking ?");
		}
	}	

	@Override
	public void processCommand(Command command) 
	{
		switch(command.type) 
		{
			case CLOSE:
				break;
			case OPEN:
				break;
			case OPEN_CHANNEL:
				break;
			default:
				throw new RuntimeException("Unhandled IEC bus command: "+command);
		}
	}

	@Override
	public boolean listen(int deviceAdress) 
	{
		if ( this.deviceAddress == deviceAdress ) 
		{
			assertNotListening();
			listening = true;
			return true;
		}
		return false;
	}

	@Override
	public void unlisten() {
		assertListening();
		listening = false;
	}

	@Override
	public void talk(int deviceAdress) 
	{
		if ( this.deviceAddress != deviceAdress ) {
			throw new IllegalArgumentException("Don't try talking to me as if I was device #"+deviceAdress+" , I'm #"+this.deviceAddress);
		}
		assertNotTalking();
		talking = true;
	}

	@Override
	public void untalk() 
	{
		assertTalking();
		talking = false;
	}
}