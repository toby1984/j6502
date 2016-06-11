package de.codesourcery.j6502.emulator.tapedrive;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.emulator.tapedrive.SquareWaveGenerator.WavePeriod;

public class WavReader 
{
    private byte[] data;
    
    public static final int CLOCK_CYCLES_PER_SECOND = 985000;
    
    protected static enum Endian 
    {
        BIG 
        {
            public int read32Bit(int offset,byte[] data) 
            {
                return read16Bit(offset,data)<<16 | read16Bit(offset+2,data);
            }
            
            public int read16Bit(int offset,byte[] data) 
            {
                return (data[offset] & 0xff ) << 8 | (data[offset+1] & 0xff );
            } 
        },
        LITTLE 
        {
            public int read32Bit(int offset,byte[] data) 
            {
                return read16Bit(offset+2,data)<<16 | read16Bit(offset,data);
            }
            
            public int read16Bit(int offset,byte[] data) 
            {
                return (data[offset+1] & 0xff ) << 8 | (data[offset] & 0xff );
            }             
        };
        
        public abstract int read16Bit(int offset,byte[] data);
        public abstract int read32Bit(int offset,byte[] data);
    }
    
    public static void main(String[] args) throws IOException 
    {
        new WavReader().load( new File("/home/tobi/mars_workspace/j6502/tape.wav") );
    }
    
    /*

The canonical WAVE format starts with the RIFF header:

0         4   ChunkID          Contains the letters "RIFF" in ASCII form
                               (0x52494646 big-endian form).
4         4   ChunkSize        36 + SubChunk2Size, or more precisely:
                               4 + (8 + SubChunk1Size) + (8 + SubChunk2Size)
                               This is the size of the rest of the chunk 
                               following this number.  This is the size of the 
                               entire file in bytes minus 8 bytes for the
                               two fields not included in this count:
                               ChunkID and ChunkSize.
8         4   Format           Contains the letters "WAVE"
                               (0x57415645 big-endian form).

The "WAVE" format consists of two subchunks: "fmt " and "data":
The "fmt " subchunk describes the sound data's format:

12        4   Subchunk1ID      Contains the letters "fmt "
                               (0x666d7420 big-endian form).
16        4   Subchunk1Size    16 for PCM.  This is the size of the
                               rest of the Subchunk which follows this number.
20        2   AudioFormat      PCM = 1 (i.e. Linear quantization)
                               Values other than 1 indicate some 
                               form of compression.
22        2   NumChannels      Mono = 1, Stereo = 2, etc.
24        4   SampleRate       8000, 44100, etc.
28        4   ByteRate         == SampleRate * NumChannels * BitsPerSample/8
32        2   BlockAlign       == NumChannels * BitsPerSample/8
                               The number of bytes for one sample including
                               all channels. I wonder what happens when
                               this number isn't an integer?
34        2   BitsPerSample    8 bits = 8, 16 bits = 16, etc.
          2   ExtraParamSize   if PCM, then doesn't exist
          X   ExtraParams      space for extra parameters

The "data" subchunk contains the size of the data and the actual sound:

36        4   Subchunk2ID      Contains the letters "data"
                               (0x64617461 big-endian form).
40        4   Subchunk2Size    == NumSamples * NumChannels * BitsPerSample/8
                               This is the number of bytes in the data.
                               You can also think of this as the size
                               of the read of the subchunk following this 
                               number.
44        *   Data             The actual sound data.     
     */    
    
    public void load(File file) throws IOException 
    {
        data = Files.readAllBytes( file.toPath() );
     
        assertMatches(0 , 0x52494646 , "Not a RIFF file");
        assertMatches(8 , 0x57415645 , "Not a WAVE file");
        
        final int chunkSize = read32Bit( 4 );
        final int fileSize = chunkSize+8;
//        assertEquals(fileSize,data.length, "Filesize does not match chunkSize");
        
        // 'fmt' subchunk
        assertMatches(12 , 0x666d7420 , "missing 'fmt ' chunk");
        
        final int subchunk1Size = read32Bit( 16 );
        assertEquals( 16 , subchunk1Size , "Invalid subchunk #1 size, only 16 (PCM) is supported");
        final int audioFormat = read16Bit( 20 );
        assertEquals( 1 , audioFormat , "Unsupported audio format, not PCM.");
        final int numChannels = read16Bit( 22 );
        assertEquals( 1 , numChannels , "Unsupported channel count");
        final int sampleRate = read32Bit( 24 );
        final int byteRate = read32Bit( 28 );
        final int bitsPerSample = read16Bit( 34 );
        
        assertMatches( 36 , 0x64617461 , "'data' chunk missing" );
        final int dataChunkSize = read32Bit( 40 );
        
        System.out.println("File parsed ok.");
        System.out.println("Sample rate: "+sampleRate);
        System.out.println("Bits per sample: "+bitsPerSample);
        
        /*
         * Short  =  2450 (18) - 2594 (17) Hz
         * Medium =  1837.4999 (24) , 1917.3914 (23) , 2100.0 (21) , 2205.0 (20)
         * Long   = 1575.0001 (28) , 1633.3334 (27)
         */
        int lastZeroCrossing=0;
        int previousValue=0;
        final boolean printSamples=false;
        final boolean printWave=true;
        int previousGradient=0;
        int waveCount = 0;
        
        final StateMachine stateMachine = new StateMachine();
        
        for ( int sampleNo = 0 ; 44+sampleNo < data.length ; sampleNo++ ) 
        {
            final int value = data[44+sampleNo] & 0xff;

            if ( sampleNo == 0 ) {
                previousValue = value;
                if ( printSamples ) {
                    System.out.println();
                }
                continue;
            }
            final int gradient = value - previousValue;
            if ( sampleNo == 1 ) 
            {
                previousValue = value;
                previousGradient = gradient;
                if ( printSamples ) {
                System.out.println();
                }
                continue;
            }
            
            if ( printSamples ) {
                System.out.print( value +" ("+sign(gradient)+")");
            }
            
            final int prevSign = sign( previousGradient ) ; 
            final int currentSign = sign( gradient ) ; 
            if ( prevSign != currentSign && ( ( prevSign == -1 && currentSign == 1 ) || ( prevSign == 0 && currentSign == 1 ) ) ) 
            {
                final int period = sampleNo-lastZeroCrossing;
                final float cycles = secondsToClockCycles( sampleCountToSeconds( period , sampleRate ) );
                waveCount++;
                if ( printWave ) {
                    System.out.print(" => Wave "+waveCount+", "+sampleCountToSeconds( sampleNo ,  sampleRate )+ ": Zero crossing at sample "+sampleNo+" (samples: "+period+", cycles: "+cycles+",f="+sampleCountToFreq(period,sampleRate)+")");
                }
                WavePeriod type = sampleCountToPeriod( period );
                if ( type == null && ( sampleNo > 10 &&  period < 35 ) ) 
                {
                    System.out.flush();
                    throw new RuntimeException("Unhandled cycle count: "+period);
                }
                if ( printWave ) {
                    System.out.println( " => "+type );
                }
                stateMachine.add( type );
                lastZeroCrossing = sampleNo;
            } else {
                if ( printSamples ) {
                    System.out.println();
                }
            }
            
            previousValue = value;
            previousGradient = gradient;
        }
    }
    
    protected static abstract class State 
    {
        private final String name;
        
        public State(String name) { this.name = name; }
        
        public abstract void onEnter();
        
        public abstract State tick(WavePeriod wave);
        
        @Override
        public String toString() { return name; }
    }
    
    protected static final State READ_BYTE = new State("READ_BYTE") 
    {
        private int bitCount = 0;
        private int value = 0;
        private boolean firstWave;
        private int byteCount = 0;
        
        private WavePeriod waveZero;
        
        @Override
        public void onEnter() 
        {
            value = 0;
            bitCount = 0;
            firstWave=true;
        }

        @Override
        public State tick(WavePeriod wave) 
        {
            if ( firstWave ) {
                waveZero = wave;
                firstWave = ! firstWave;                
                return this;
            } 
            
            if ( waveZero == WavePeriod.SHORT ) { // => 0 bit
                
                if ( wave != WavePeriod.MEDIUM ) {
                    System.err.println("Not a valid bit wave sequence ("+waveZero+","+wave+"), assuming 0 bit");
                }
                value >>>= 1;
            } else if ( waveZero == WavePeriod.MEDIUM && wave == WavePeriod.SHORT ) { // 1 bit
                value >>>= 1;
                value |= (1<<8);
            } else {
                throw new RuntimeException("Not a valid bit wave sequence ("+waveZero+","+wave+")");
            }

            firstWave = ! firstWave;
            bitCount++;
            
            if ( bitCount == 9 ) 
            {
                if ( (Integer.bitCount( value ) & 1) == 0 ) {
                    throw new RuntimeException("Checksum error on "+Integer.toBinaryString( value ) );
                }
                System.out.println("Byte $"+StringUtils.leftPad( Integer.toHexString( byteCount ) , 4 , "0" )+" : $"+Integer.toHexString( value & 0xff ) );
                byteCount++;
                return WAIT_FOR_DATA_MARKER;
            }
            return this;
        }
    };
    
    protected static final State WAIT_FOR_DATA_MARKER = new State("WAIT_FOR_DATA_MARKER") {

        private WavePeriod previous = null;
        
        @Override
        public State tick(WavePeriod wave) 
        {
            if ( previous == null ) {
                previous = wave;
                return this;
            }
            if ( previous == WavePeriod.LONG && wave == WavePeriod.MEDIUM ) {
                return READ_BYTE;
            }
            previous=wave;
            return this;                
        }

        @Override
        public void onEnter() {
            previous = null;
        }
    };
    
    protected static final class StateMachine 
    {
        private State state = WAIT_FOR_DATA_MARKER;
        
        public void add(WavePeriod wave) 
        {
            State newState = state.tick( wave );
            if ( newState != state ) {
//                System.out.println("State transition: "+state+" -> "+newState);
                newState.onEnter();
                state = newState;
            }
        }
    }
    
    private int sign(int value) {
        if ( value < 0 ) {
            return -1;
        }
        if ( value > 0 ) {
            return 1;
        }
        return 0;
    }
    
    private WavePeriod sampleCountToPeriod(int count) {
        
        if ( count <= 19 ) {
            return WavePeriod.SHORT;
        }
        if ( count >= 20 && count <= 26 ) {
            return WavePeriod.MEDIUM;
        }
        if ( count > 26 && count <= 29 ) {
            return WavePeriod.LONG;
        }
        return null;
    }
    
    private float secondsToClockCycles(float seconds) 
    {
        return (float) CLOCK_CYCLES_PER_SECOND/(1f/seconds);
    }
    
    private float sampleCountToSeconds(int count,int sampleRate)  {
        return 1f / ((float) sampleRate/count);
    }
    
    private float sampleCountToFreq(int count,int sampleRate) {
        float period =  1f / ((float) sampleRate/count);
        return 1f/period;
    }
    
    private void assertEquals(int expected,int actual,String msg) {
        if ( expected != actual ) {
            throw new IllegalArgumentException( msg+" , expected: "+expected+" , actual: "+actual );
        }
    }
    
    private void assertMatches(int offset,int value,String message) {
        if ( ! matches(offset,value ) ) {
            throw new IllegalArgumentException( message );
        }
    }
    private boolean matches(int offset,int value) {
        return Endian.BIG.read32Bit(offset,data) == value;
    }
    
    private int read32Bit(int offset) {
        return Endian.LITTLE.read32Bit( offset , data );
    }
    
    private int read16Bit(int offset) {
        return Endian.LITTLE.read16Bit( offset , data );
    }    
}