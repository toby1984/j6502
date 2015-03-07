package de.codesourcery.j6502.emulator;

public abstract class SerialDevice 
{
	public abstract boolean listen(int deviceAdress);
	
	public abstract void unlisten();
	
	public abstract void talk(int deviceAdress);
	
	public abstract void untalk();
	
	public abstract void processCommand(IECBus.Command command); 
}