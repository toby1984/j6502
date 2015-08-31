package de.codesourcery.j6502.emulator;


public interface SerialDevice
{
	public void tick(IECBus bus);
	
	/**
	 * Returns the LOGICAL value of the clock line (true = HIGH , false = LOW).
	 * @return
	 */
	public boolean getClock();
	
	/**
	 * Returns the LOGICAL value of the data line (true = HIGH , false = LOW).
	 * @return
	 */	
	public boolean getData();
	
	/**
	 * Returns the LOGICAL value of the ATN line (true = HIGH , false = LOW).
	 * @return
	 */		
	public boolean getATN();
	
	public int getPrimaryAddress();
	
	public void reset();
	
	public boolean isDataTransferActive();
}