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
	
	private TapeFile tape;
	
	private boolean motorOn;
	private boolean keyPressed;
	
	private int tickCounter = 0;
	
	private int silenceTicks = SILENCE_TICKS;
	
	private final TapeRecording tapeRecording = new TapeRecording();
	
	protected final class TapeRecording 
	{
	    public int[] transitions = new int[10];
	    public int writePtr = 0;
	    
	    private boolean signal;
	    private long lastSignalChange=-1;
	    
	    public int size() 
	    {
	        return writePtr;
	    }
	    
	    private void recordChange(long tickDelta) 
	    {
	        if ( writePtr == transitions.length ) 
	        {
	            final int[] tmp = new int[ transitions.length + transitions.length/2 ];
	            System.arraycopy( this.transitions , 0 ,tmp , 0 , writePtr );
	            transitions= tmp;
	        }
	        System.out.println("RECORDED PULSE , len: "+tickDelta);
	        transitions[writePtr++]=(int) tickDelta;
	    }
	    
	    public void reset() 
	    {
	        writePtr = 0;
	        lastSignalChange=-1;
	    }
	    
	    public void record(boolean signal) 
	    {
	        if ( lastSignalChange == -1 || lastSignalChange == tickCounter ) 
	        {
	            this.lastSignalChange = tickCounter;
	            this.signal = signal;
	            return;
	        }
	        if ( this.signal != signal ) 
	        {
	            recordChange( tickCounter - lastSignalChange );
	            this.signal = signal;
	            lastSignalChange = tickCounter;
	        }
	    }

        public int[] getRecording() 
        {
            final int[] result = new int[ writePtr ];
            System.arraycopy( this.transitions , 0 , result , 0 , writePtr );
            return result;
        }
	}
	
	public void insert(TapeFile tape) 
	{
		Validate.notNull(tape, "tape must not be NULL");
		this.tape = tape;
		reset();
	}
	
    public void setTapeOut(boolean signal) 
    {
        if ( !( motorOn && keyPressed ) ) 
        {
            System.err.println("Trying to write to TAPE_OUT while motor is off and/or no key pressed ?");
        }
        tapeRecording.record( signal );
    }
    
    public int[] getRecording() {
        return tapeRecording.getRecording();
    }
	
	public void eject() {
		this.tape = null;
		this.driver.reset();
		this.tapeRecording.reset();
	}

	public TapeFile getTape() {
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
	    this.silenceTicks = SILENCE_TICKS;
	    this.tickCounter = 0;
	    this.motorOn = false;
	    this.keyPressed = false;
	    
        this.tapeRecording.reset();	    
	    
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
        
	    int a=0;
	    int b=1;
	    int c=1;
	    System.out.println("a^b="+(a^b^c));
	    System.exit(0);
	    
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