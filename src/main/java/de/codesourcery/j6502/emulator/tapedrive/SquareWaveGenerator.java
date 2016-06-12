package de.codesourcery.j6502.emulator.tapedrive;

import java.util.ArrayList;
import java.util.List;

public class SquareWaveGenerator 
{
    public enum WavePeriod
    {
        /*
    /*
         * http://c64tapes.org/dokuwiki/doku.php?id=loaders:rom_loader
         * http://www.atarimagazines.com/compute/issue57/turbotape.html
         * http://c64tapes.org/dokuwiki/doku.php?id=analyzing_loaders 
         *         
         * CPU freq: 985248 Hz
         * 
         * (S)hort  : 2840 Hz
         * (M)edium : 1953 Hz
         * (L)ong   : 1488 Hz
         * 
         * Default timing thresholds at start of algorithm (speed constant $B0 contains 0)
         * 
         * => short pulse = 240-432 cycles , avg. 336 cycles .... 2280 Hz - 4105 Hz  
         * => medium pulse = 432-584 cycles , avg. 508 cycles ...1687 Hz - 2280 Hz
         * => long pulse = 584-760 cycles, avg. 672 cycles .. 1296 Hz - 1687  
         * 
         */
        SHORT("SHORT", 346 ), // 336 
        MEDIUM("MEDIUM", 504 ), // 508
        LONG("LONG", 662 ), // 672
        SILENCE_SHORT("SILENCE SHORT (0.33s)" , 325131) { 

            @Override
            public void onEnter(SquareWaveGenerator state) 
            {
                state.currentTicks = ticks;
            }

            @Override
            public boolean tick(SquareWaveGenerator state)
            {
                // just wait without toggling signal line
                state.currentTicks--;
                if ( state.currentTicks <= 0 ) 
                {
                    return true; // advance to next state
                }
                return false;
            }
        },
        SILENCE_LONG("SILENCE LONG (1.32s)" , 325131*4) { 

            @Override
            public void onEnter(SquareWaveGenerator state) 
            {
                state.currentTicks = ticks;
            }

            @Override
            public boolean tick(SquareWaveGenerator state)
            {
                // just wait without toggling signal line
                state.currentTicks--;
                if ( state.currentTicks <= 0 ) 
                {
                    return true; // advance to next state
                }
                return false;
            }
        };        
        

        protected final int ticks;
        private final String name;

        private WavePeriod(String name,double ticks) {
            this.name = name;
            this.ticks = (int) ticks;
        }

        public void onEnter(SquareWaveGenerator state) 
        {
            state.currentTicks = ticks/2; // square wave with 50% duty cycle
            state.signalAtStartOfWave = state.currentSignal;
        }

        public boolean tick(SquareWaveGenerator state) 
        {
            state.currentTicks--;
            if ( state.currentTicks <= 0 ) 
            {
                state.currentSignal = ! state.currentSignal;
                state.currentTicks = ticks/2; // square wave with 50% duty cycle
                if ( state.currentSignal == state.signalAtStartOfWave ) 
                {
                    return true; // advance to next state
                }
            }
            return false;
        }

        @Override
        public String toString() {
            return name+" ("+ticks+" ticks)";
        }
    }

    private final WaveArray waves = new WaveArray(1024);

    protected static final class Marker  // TODO: class is debug only , remove when done
    {
        public final int count;
        public final String label;

        public Marker(int count, String label) {
            this.count = count;
            this.label = label;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    protected static final class WaveArray 
    {
        public int waveCount=0;
        public int wavePtr=0;
        public WavePeriod[] waves;

        private final List<Marker> markers = new ArrayList<>(); // TODO: Remove debug code

        public WaveArray(int initialSize) {
            this.waves = new WavePeriod[initialSize];
        }

        public int size() {
            return waveCount;
        }

        public void addMarker(String label) {
            markers.add( new Marker( wavePtr , label ) );
        }

        public int wavesRemaining() {
            return waveCount-wavePtr;
        }

        public void add(WavePeriod p) 
        {
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
            
            while ( ! markers.isEmpty() && markers.get(0).count <= wavePtr ) 
            {
                System.out.flush();
                System.err.println("********************** Wave: "+markers.remove(0)+" *****************");
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

    private boolean signalAtStartOfWave;
    private WavePeriod currentWave;
    private boolean currentSignal;
    private int currentTicks;

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

    public void addMarker(String label) {
        waves.addMarker( label );
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