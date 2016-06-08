package de.codesourcery.j6502.emulator.tapedrive;

public class SquareWaveGenerator 
{
    public enum WavePeriod
    {
        SHORT("SHORT", 357 ),
        MEDIUM("MEDIUM", 504 ),
        LONG("LONG", 662 );    	
        
        private final int ticks;
        private final String name;
        
        private WavePeriod(String name,int ticks) {
            this.name = name;
            this.ticks = ticks;
        }
        
        public void onEnter(SquareWaveGenerator state) {
            state.currentTicks = ticks/2; // square wave with 50% duty cycle
            state.currentSignal = false;
        }
        
        public boolean tick(SquareWaveGenerator state) 
        {
            state.currentTicks--;
            if ( state.currentTicks == 0 ) 
            {
                state.currentSignal = ! state.currentSignal;
                state.currentTicks = ticks/2; // square wave with 50% duty cycle
                if ( state.currentSignal == false ) 
                {
                    return true;
                }
            }
            return false;
        }
        
        @Override
        public String toString() {
            return name;
        }
    }
    
    private final WaveArray waves = new WaveArray(1024);
    
    protected static final class WaveArray 
    {
    	public int waveCount=0;
        public int wavePtr=0;
        public WavePeriod[] waves;
    
        public WaveArray(int initialSize) {
        	this.waves = new WavePeriod[initialSize];
        }
        
    	public int size() {
    		return waveCount;
    	}
    	
    	public int wavesRemaining() {
    		return waveCount-wavePtr;
    	}
    	
    	public void add(WavePeriod p) {
    		
    		if ( wavePtr == waves.length ) 
    		{
    			final WavePeriod[] newData = new WavePeriod[ waves.length + waves.length/2 ];
    			System.arraycopy( this.waves , 0 , newData , 0 , this.waves.length );
    			waves = newData;
    		}
    		waves[wavePtr++]=p;
    		waveCount++;
    	}
    	
    	public WavePeriod pop() 
    	{
    		if ( wavePtr == waveCount ) {
    			return null;
    		}
    		return waves[wavePtr++];
    	}
    	
    	public void clear() 
    	{
    		waveCount = 0;
    		waves = new WavePeriod[ this.waves.length ];
    		rewind();
    	}    	
    	
    	public void rewind() {
    		wavePtr = 0;
    	}
    }
    
    private WavePeriod currentWave;
    private boolean currentSignal;
    private int currentTicks;
    
    /*
; ==> A 0 bit is represented by a    short   wave   followed by a   medium   wave
; ==> A 1 bit is represented by a   medium   wave   followed by a    short   wave 

;    985 Khz CPU freq
; = 1,0152285 microseconds = 1.0152285*10-6
; = 0,0010152284264 milliseconds per CPU cycle
;          
; (S)hort  : 2840 Hz => 0.0003521126637 s   = 0.3521126637   ms =  346,830973743 cycles = 347 cycles
; (M)edium : 1953 Hz => 0,000512032770097 s = 0.512032770097 ms =  504,352278544 cycles = 504 cycles    
; (L)ong   : 1488 Hz => 0,000672043010753 s = 0,672043010753 ms =  661,962365589 cycles = 662 cycles

; >>>>>>>>>>>> Pulse length detection triggers on DESCENDING (negative) edges ONLY <<<<<<<<<<<<<<
     */
    
    public boolean currentSignal() 
    {
        return currentSignal;
    }
    
    public int wavesRemaining() {
        return waves.wavesRemaining();
    }
    
    public void reset() {
        waves.clear();
        currentWave = null;
        currentSignal=false;
    }
    
    public void rewind() {
    	waves.rewind();
    }
    
    public void tick() 
    {
        if ( currentWave == null ) 
        {
        	currentWave = waves.pop();
        	if ( currentWave == null ) {
                currentWave = WavePeriod.SHORT; // pulses of SHORT length are used as a trailer by the tape format
            } 
            currentWave.onEnter(this);
            return;
        }
        if ( currentWave.tick(this) ) {
            currentWave = null;
        }
    }
    
    public void addWave(WavePeriod period) {
        waves.add( period );
    }
    
    public void addBit(boolean bit) 
    {
        if ( bit ) {
// ==> A 1 bit is represented by a   medium   wave   followed by a    short   wave
            addWave( WavePeriod.MEDIUM );            
            addWave( WavePeriod.SHORT );
        } else {
// ==> A 0 bit is represented by a    short   wave   followed by a   medium   wave
            addWave( WavePeriod.SHORT );
            addWave( WavePeriod.MEDIUM );
        }
    }
}