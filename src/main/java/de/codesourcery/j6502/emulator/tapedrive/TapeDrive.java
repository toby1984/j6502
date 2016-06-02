package de.codesourcery.j6502.emulator.tapedrive;

import org.apache.commons.lang.Validate;

public class TapeDrive 
{
	private static final boolean DEBUG = true;
	
	private T64File tape;
	
	public boolean motorOn;
	public boolean playPressed;
	
	/*
	 * Reading from tape:
	 * 
     * INIT: http://www.pagetable.com/c64rom/c64rom_en.html#F199	 
     * 
     * READ/WRITE: http://www.pagetable.com/c64rom/c64rom_en.html#F875
	 */

	public void insert(T64File tape) 
	{
		Validate.notNull(tape, "tape must not be NULL");
		this.tape = tape;
	}
	
	public void eject() {
		this.tape = null;
	}
	
	public void setMotor(boolean onOff) {
		if ( DEBUG && this.motorOn != onOff ) {
			System.out.println("TAPE MOTOR: "+onOff);
		}
		this.motorOn = onOff;
	}
	
	public void tick() {
		
	}
}