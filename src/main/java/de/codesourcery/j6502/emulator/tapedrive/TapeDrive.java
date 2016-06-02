package de.codesourcery.j6502.emulator.tapedrive;

import org.apache.commons.lang.Validate;

public class TapeDrive 
{
	private static final boolean DEBUG = true;
	
	private final SquareWaveDriver driver = new SquareWaveDriver();
	
	private T64File tape;
	
	private boolean motorUnlocked;
	private boolean keyPressed;
	
	private int tickCounter = 0;
	
	/*
	 * Reading from tape:
	 * 
     * INIT: http://www.pagetable.com/c64rom/c64rom_en.html#F199	 
     * 
     * READ/WRITE: http://www.pagetable.com/c64rom/c64rom_en.html#F875
     * 
     * http://wav-prg.sourceforge.net/tape.html
	 */

	public void insert(T64File tape) 
	{
		Validate.notNull(tape, "tape must not be NULL");
		this.tape = tape;
		reset();
	}
	
	public void eject() {
		this.tape = null;
		this.driver.reset();
	}

	public T64File getTape() {
	    return tape;
	}
	
	// cassette: The signal "/Flag" of the CIA 1 is connected to the "/SRQ IN" line of the serial interface. 
	public boolean currentSignal() {
	    return driver.currentSignal();
	}
	
	public void setMotorUnlocked(boolean onOff) {
		if ( DEBUG && this.motorUnlocked != onOff ) {
			System.out.println("TAPE MOTOR UNLOCKED: "+onOff);
		}
		this.motorUnlocked = onOff;
	}
	
	public boolean isKeyPressed() {
	    return keyPressed;
	}
	
	public void setKeyPressed(boolean yesNo) {
        if ( DEBUG && this.keyPressed != yesNo ) {
            System.out.println("KEY PRESSED: "+yesNo);
        }	    
	    this.keyPressed = yesNo;
	}
	
	public void tick() 
	{
	    if ( motorUnlocked && keyPressed ) 
	    {
	        tickCounter++;
	        if ( DEBUG && (tickCounter%500000) == 0 ) {
	            System.out.println("TAPE tick() : waves remaining - "+driver.wavesRemaining());
	        }
	        driver.tick();
	    }
	}
	
	public void reset() 
	{
	    tickCounter = 0;
	    motorUnlocked = false;
	    keyPressed = false;
	    
	    if ( this.tape != null ) 
	    {
	        this.driver.insert( this.tape ); // resets as well
	        if ( DEBUG ) {
	            System.out.println("Tape inserted: "+tape+" , waves: "+this.driver.wavesRemaining());
	        }
	    } else {
	        this.driver.reset();
            System.out.println("Tape inserted: <NONE> , waves: "+this.driver.wavesRemaining());	        
	    }
	}
}