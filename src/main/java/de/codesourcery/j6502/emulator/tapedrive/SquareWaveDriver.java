package de.codesourcery.j6502.emulator.tapedrive;

import de.codesourcery.j6502.emulator.D64File.FileType;
import de.codesourcery.j6502.emulator.tapedrive.SquareWaveGenerator.WavePeriod;
import de.codesourcery.j6502.emulator.tapedrive.T64File.DirEntry;

public class SquareWaveDriver 
{
    /*
     * http://c64tapes.org/dokuwiki/doku.php?id=loaders:rom_loader
     * http://www.atarimagazines.com/compute/issue57/turbotape.html
     * http://c64tapes.org/dokuwiki/doku.php?id=analyzing_loaders 
     */
    private static final boolean DEBUG = true;
    private final SquareWaveGenerator generator = new SquareWaveGenerator();
    
    public void insert(T64File file) 
    {
        generator.reset();
        
        if ( DEBUG ) {
            System.out.println("Loading tape: "+file);
        }
        // write tape lead
        writeLeader();
        
        for ( T64File.DirEntry entry : file.getDirEntries() ) 
        {
            if ( DEBUG ) {
                System.out.println("Generating wave forms for "+entry);
            }            
            writeFile( entry );
        }
    }
    
    public int wavesRemaining() {
        return generator.wavesRemaining();
    }    
    
    public boolean currentSignal() {
        return generator.currentSignal();
    }
    
    public void tick() 
    {
        generator.tick();
    }

    private void writeFile(DirEntry entry) 
    {
        /*
         * === HEADER ===
         */
        
        // first pass
        writeSync();
        
        writeHeader( entry );
        
        writeGap();

        // repetition
        writeSyncRepeated();
        
        writeHeader( entry );
        
        writeTrailer();      
        
        /*
         * === DATA ===
         */
        
        writeSync();
        
        writeData( entry );
        
        writeGap();
        
        writeSyncRepeated();        
        
        writeData( entry );        

        writeTrailer();
    }

    private void writeData(DirEntry entry) 
    {
        if ( DEBUG ) {
            System.out.println("Generating: "+entry.length()+" data bytes");
        }           
        int checksum=0;
        for ( int value : entry.data() ) {
            writeByteWithParity( value );
            checksum ^= (value & 0xff);
        }
        writeByteWithParity(checksum);
    }

    private void writeHeader(DirEntry entry) 
    {
        if ( DEBUG ) {
            System.out.println("Generating: HEADER");
        }   
        
        if ( ! entry.hasFileType( FileType.PRG ) ) {
            throw new RuntimeException("Sorry, this T64 file contains a "+entry.getFileType()+" file ("+entry.asciiName()+") which currently is not supported");
        }
        
        /*
  1 Byte   : File type.

    $01= relocatable program
    $02= Data block for SEQ file
    $03= non-relocatable program
    $04= SEQ file header
    $05= End-of-tape marker
         */
        if ( (entry.floppyFileType &0b111) != 0x02 ) {
            throw new RuntimeException("Illegal file type "+entry.floppyFileType+": "+entry);
        }
        
        writeByteWithParity( 0x01 ); // TODO: Hard-coded value: relocatable program
        
        // load address
        writeWord( entry.loadAddress );
        
        // end address
        writeWord( entry.endAddress );        
        
        // file name
        writeBytesWithParity( entry.petsciiName );
        if ( entry.petsciiName.length < 16 ) {
            // pad with blanks ( 0x20 )
            for ( int i = 0 , pad = 16 - entry.petsciiName.length ; i < pad ; i++ ) {
                writeByteWithParity( 0x20 );
            }
        } else if ( entry.petsciiName.length > 16 ) {
            throw new RuntimeException("File name too long: "+entry);
        }
        writeBytesWithParity( 0 , 171 ); // header body
    }
    
    private void writeLeader()
    {
        if ( DEBUG ) {
            System.out.print("Generating: LEAD - ");
        }         
        // write leader: $6A00 short pulses (10 seconds) 
        writeShortPulses( 0x6a00 );
    }
    
    private void writeGap() {
        if ( DEBUG ) {
            System.out.print("Generating: GAP - ");
        } 
        //  $4F   before HEADER REPEATED and DATA REPEATED
        writeShortPulses( 0x4f );
    }
    
    private void writeTrailer() 
    {
        if ( DEBUG ) {
            System.out.print("Generating: TRAILER - ");
        } 
        // Some trailing short pulses follow both HEADER REPEATED and DATA REPEATED. The standard amount is $4E pulses. 
        writeShortPulses( 0x4e );
    }    
    
    private void writeShortPulses(int count) 
    {
        if ( DEBUG ) {
            System.out.println("Generating: "+count+" SHORT_PULSES");
        }         
        for ( int i = 0 ; i < count ; i++) {
            generator.addWave( WavePeriod.SHORT );
        }
    }
    
    private void writeSync() 
    {
        if ( DEBUG ) {
            System.out.println("Generating: SYNC");
        }        
        writeBytesNoParity( new int[] { 0x89 ,0x88 ,0x87 ,0x86 ,0x85 ,0x84 ,0x83 ,0x82 ,0x81 } );
    }
    
    private void writeSyncRepeated() 
    {
        if ( DEBUG ) {
            System.out.println("Generating: SYNC_REPEATED");
        }   
        writeBytesNoParity( new int[] { 0x09 ,0x08 ,0x07 ,0x06 ,0x05 ,0x04 ,0x03 ,0x02 ,0x01 } );
    }
    
    private void writeBytesWithParity(int byteValue,int count) 
    {
        for ( int i = 0 ; i <count ; i++ ) {
            writeByteWithParity( byteValue );
        }
    }
    
    private void writeBytesWithParity(byte[] data) 
    {
        for ( int byteValue : data ) {
            writeByteWithParity( byteValue & 0xff );
        }
    }    
    
    private void writeBytesNoParity(int[] data) {
        for ( int byteValue : data ) {
            writeByteNoParity( byteValue );
        }
    }
    
    private void writeWord(int word) {
    
        // write lo first, then hi
        writeByteWithParity( word & 0xff );
        writeByteWithParity( (word >> 8  )& 0xff );
    }
    
    private void writeByteWithParity(int value) {
        writeByte(value,true);
    }
    
    private void writeByteNoParity(int value) {
        writeByte(value,false);
    }    
    
    private void writeByte(int value,boolean withParity) {
        
        int mask = 0b1000_0001; 
        int oneBitCount=0;
        for ( int i = 0 ; i < 8 ; i++ ) 
        {
            final boolean bitSet = ( value & mask ) != 0;
            if ( bitSet ) {
                oneBitCount++;
            }
            generator.addBit( bitSet );
            mask >>>= 1;
        }
        
        if ( withParity ) {
            // write parity bit
            // Each byte on tape ends with a parity bit, which is either 0 or 1 as required to make the total number of 1 bits in the byte odd
            if ( (oneBitCount & 1 ) == 0 ) { // we have an even number of 1 bits, add another 1 bit to make it odd  
                generator.addBit( true );
            } else {  
                // we have an odd number of 1 bits, add another 1 bit to make it odd 
                generator.addBit( true );
            }
        }
    }
    
    public void reset() {
        generator.reset();
    }
}