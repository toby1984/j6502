package de.codesourcery.j6502.emulator.tapedrive;

import java.util.ArrayList;
import java.util.List;

public class SquareWaveGenerator 
{
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

    public boolean signalAtStartOfWave;
    private WavePeriod currentWave;
    public boolean currentSignal;
    public int currentTicks;

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