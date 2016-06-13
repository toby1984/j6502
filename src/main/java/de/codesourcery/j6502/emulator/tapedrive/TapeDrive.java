package de.codesourcery.j6502.emulator.tapedrive;

import java.io.File;
import java.io.IOException;

import org.apache.commons.lang.Validate;

/**
 * http://c64tapes.org/dokuwiki/doku.php?id=loaders:rom_loader
 * 
 * http://www.pagetable.com/c64rom/c64rom_en.html#F199	 
 * 
 * http://www.pagetable.com/c64rom/c64rom_en.html#F875
 * 
 * http://wav-prg.sourceforge.net/tape.html
 */
public class TapeDrive 
{
	private static final int SILENCE_TICKS = 985000;

    private static final boolean DEBUG = true;
	
	private final SquareWaveDriver driver = new SquareWaveDriver();
	
	private T64File tape;
	
	private boolean motorOn;
	private boolean keyPressed;
	
	private int tickCounter = 0;
	
	private int silenceTicks = SILENCE_TICKS;
	
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
	
	public void setMotorOn(boolean onOff) {
		if ( DEBUG && this.motorOn != onOff ) {
			System.out.println("TAPE MOTOR UNLOCKED: "+onOff);
		}
		this.motorOn = onOff;
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
	    if ( motorOn && keyPressed ) 
	    {
	        if ( silenceTicks > 0 ) 
	        {
	            silenceTicks--;
	            if ( silenceTicks == 0 ) {
	                System.out.println("Starting tape replay...");
	            }
	            return;
	        }
	        
	        tickCounter++;
	        if ( DEBUG && (tickCounter%985000) == 0 ) {
	            System.out.println("TAPE tick() : waves remaining - "+driver.wavesRemaining());
	        }	        
	        driver.tick();
	    }
	}
	
	public void reset() 
	{
	    silenceTicks = SILENCE_TICKS;
	    tickCounter = 0;
	    motorOn = false;
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
	
	public static void main(String[] args) throws IOException {
        
	    TapeDrive drive = new TapeDrive();
	    drive.insert( new T64File(new File("/home/tgierke/mars_develop_workspace/j6502/tapes/choplifter.t64")) );
	    drive.setKeyPressed( true );
	    drive.setMotorOn( true );
	    
	    int previousCycle=-1;
	    boolean previousSignal=false;
	    int cycle = 0;
	    while ( drive.driver.wavesRemaining() > 0 ) 
	    {
	        drive.tick();
	        final boolean signal = drive.currentSignal();
	        if ( cycle > 0 && previousSignal && ! signal ) 
	        {
	            int delta = cycle - previousCycle;
	            System.out.println(cycleToTime(cycle)+" -"+cycle+" - "+signal+" - length: "+delta);
	            previousCycle = cycle;
	        }
	        cycle++;
	        previousSignal = signal;
	    }
    }
	
	private static float cycleToTime(int cycle) {
	   return 1f / (985248f / (float) cycle);
	}
}