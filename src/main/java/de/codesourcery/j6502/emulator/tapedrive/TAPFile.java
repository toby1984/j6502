package de.codesourcery.j6502.emulator.tapedrive;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import de.codesourcery.j6502.emulator.tapedrive.WavePeriod.Type;

public class TAPFile implements TapeFile
{
    /*
     * Bytes: $0000-000B: File signature "C64-TAPE-RAW"
     *              000C: TAP version (see below for description)
     *                     $00 - Original layout
     *                      01 - Updated
     *         000D-000F: Future expansion
     *         0010-0013: File  data  size  (not  including  this  header,  in
     *                    LOW/HIGH format) i.e. This image is $00082151  bytes
     *                    long.
     *         0014-xxxx: File data             
     *                               (8 * data byte) 
     * pulse length (in seconds) =  ----------------
     *                               clock cycles
     * 
     *   Therefore, a data value of $2F (47 in decimal) would be:
     * 
     *     (47 * 8) / 985248 = 0.00038975 seconds.
     *                       = 0.00038163 seconds.
     * 
     *
     *   clock_cyles         
     * ---------------   =  data byte
     *         8         
     *                                                   
     * Overflow (File format #1 only)
     * 
     * Data value of 00 is followed by three bytes, representing a 24 bit value of C64 _CYCLES_ (NOT cycles/8). 
     * The order is as follow: 00 <bit0-7> <bit8-15> <bit16-24>.                         
     */  
    
    private final List<WavePeriod> data = new ArrayList<>(); 
    
    public TAPFile(File file) throws IOException 
    {
        final byte[] data = Files.readAllBytes( file.toPath() );

        // check header
        final char[] headerChars = "C64-TAPE-RAW".toCharArray();
        for ( int i = 0 ; i < headerChars.length ; i++ ) 
        {
            if ( data[i] != (byte) headerChars[i] ) {
                throw new IllegalArgumentException("Invalid T64 file header");
            }
        }   
        final int size1 = data[0x10] & 0xff;
        final int size2 = data[0x11] & 0xff;
        final int size3 = data[0x12] & 0xff;
        final int size4 = data[0x13] & 0xff;
        
        final int version = data[0x0c];
        if ( version != 0 && version != 1 ) {
            throw new IllegalArgumentException("Unsupported format version "+version);
        }
        
        final int size = size4 << 24 | size3 << 16 | size2 << 8 | size1;
        if ( (size+0x14) != data.length ) {
            throw new IllegalArgumentException("Invalid file size");
        }
        
        final Map<Integer,WavePeriod> cache = new HashMap<>();
        for ( int i = 0x14 ; i < data.length ;  ) 
        {
            final int value = data[i++] & 0xff;
            final int cycles;
            if ( value == 0x00 ) 
            {
                if ( version == 0 ) {
                    throw new RuntimeException("File format v1 file contains overflow");
                } 
                if ( (i+1) >= data.length ) {
                    throw new RuntimeException("File format v2 contains overflow indicator but not enough bytes, file truncated?");
                }
                final int value1 = data[i++] & 0xff;
                final int value2 = data[i++] & 0xff;
                final int value3 = data[i++] & 0xff;
                cycles = value3 << 16 | value2 << 8 | value1;                
            } else {
                cycles = value*8;
            }
            WavePeriod period = cache.get( cycles );
            if ( period == null ) {
                period = new WavePeriod(Type.CUSTOM,cycles);
                cache.put( cycles , period );
            }
            this.data.add( period );
        }
        System.out.println("TAP file version "+version+" loaded, "+cache.size()+" unique waveforms");
        cache.keySet().stream().sorted().map( key -> cache.get(key ) ).forEach( v -> System.out.println(v) );
    }

    public List<WavePeriod> getData() {
        return data;
    }
    
    public static void main(String[] args) throws IOException {
        new TAPFile( new File("/home/tobi/mars_workspace/j6502/tapes/vice.tap" ) );
    }
}
