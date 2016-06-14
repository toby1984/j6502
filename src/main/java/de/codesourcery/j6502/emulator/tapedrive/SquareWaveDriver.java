package de.codesourcery.j6502.emulator.tapedrive;

import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.emulator.D64File.FileType;
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
    
    public void insert(TapeFile file) 
    {
        Validate.notNull(file, "file must not be NULL");
        if ( file instanceof T64File ) {
            insert( (T64File) file);
        } else if ( file instanceof TAPFile ) {
            insert( (TAPFile) file);
        } else {
            throw new IllegalArgumentException("Unhandled file type: "+file.getClass().getName());
        }
    }
    
    private void insert(T64File file) 
    {
        generator.reset();
        
        if ( DEBUG ) {
            System.out.println("Loading tape: "+file);
        }
        // write tape lead
        generator.addMarker("Pilot");        
        writePilotTone();
        
        for ( T64File.DirEntry entry : file.getDirEntries() ) 
        {
            if ( DEBUG ) {
                System.out.println("Generating wave forms for "+entry);
            }            
            writeFile( entry );
        }
        
        generator.rewind();
    }
    
    private void insert(TAPFile file) 
    {
        generator.reset();
        
        if ( DEBUG ) {
            System.out.println("Loading tape: "+file);
        }
        
        for ( WavePeriod p : file.getData() ) {
            generator.addWave( p );
        }
        
        generator.rewind();
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
        generator.addMarker("FileEntry");
        
        /*
         * === HEADER ===
         */
        
        // first pass
        generator.addMarker("Sync #1");
        writeSync(); // raw
        
        generator.addMarker("Header #1");
        writeHeader( entry );
        
        generator.addMarker("Gap #1");
        
        writeShortPulses( 79 );
        
        // repetition
        generator.addMarker("Sync #2 repeated");
        writeSyncRepeated();
        
        generator.addMarker("Header #2 repeated");
        writeHeader( entry );
        
        generator.addMarker("Trailer #1 repeated");
        
        writeTrailer(); // short pulses
        
        generator.addMarker("Gap short (0.33s)");
        generator.addWave( WavePeriod.SILENCE_SHORT );        
        
        /*
         * === DATA ===
         */
        
        generator.addMarker("Sync data");        
        writeSync();
        
        generator.addMarker("data");           
        writeData( entry );
        
        generator.addMarker("data gap");            
        writeShortPulses( 79 );
        
        generator.addMarker("Gap short (0.33s)");
        generator.addWave( WavePeriod.SILENCE_SHORT );                 
        
        generator.addMarker("Sync data repeated");           
        writeSyncRepeated();        
        
        generator.addMarker("data repeated");          
        writeData( entry );        

        generator.addMarker("data trailer repeated");          
        writeTrailer();
        
        generator.addMarker("Gap long (0.33s)");
        generator.addWave( WavePeriod.SILENCE_LONG );          
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
        
        int checksum = 0;
        
        writeByte( 0x01 ); // TODO: Hard-coded value: relocatable program
        checksum ^= 0x01;
        
        
        // load address
        writeWord( entry.loadAddress );
        checksum ^= (entry.loadAddress & 0xff);
        checksum ^= ((entry.loadAddress >>> 8)& 0xff);
        
        // end address
        writeWord( entry.endAddress );    
        checksum ^= (entry.endAddress & 0xff);
        checksum ^= ((entry.endAddress >>> 8)& 0xff);        
        
        // file name
        for ( int i = 0 ; i < entry.petsciiName.length ; i++ ) {
            final byte value = entry.petsciiName[i];
            writeByte( value );
            checksum ^= (value & 0xff);
        }

        // pad remaining header with blanks ( 0x20 )
        // so that total header size is 1 + 2 + 2 + 187 + 1 = 193 bytes         
        final int padding = 187 - entry.petsciiName.length;
        if ( padding < 0 ) {
            throw new RuntimeException("File name too long: "+entry);
        }
        for ( int i = 0 ; i < padding ; i++ ) 
        {
                writeByte( 0x20 );
                checksum ^= 0x20;
        } 
        
        // write checksum byte
        writeByte( checksum );
    }
    
    private void writeData(DirEntry entry) 
    {
        if ( DEBUG ) {
            System.out.println("Generating: "+entry.length()+" data bytes");
        }           
        int checksum=0;
        for ( int value : entry.data() ) 
        {
            writeByte( value );
            checksum ^= value;
        }
        writeByte(checksum);
    }    
    
    private void writePilotTone()
    {
        if ( DEBUG ) {
            System.out.print("Generating: LEAD - ");
        }         
        // write leader: $6A00 short pulses (10 seconds) 
        writeShortPulses( 0x6a00 );
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
        generator.addMarker("SYNC");
        writeBytesWithParity( new byte[] { (byte) 0x89 ,(byte) 0x88 ,(byte) 0x87 ,(byte) 0x86 ,(byte) 0x85 ,(byte) 0x84 ,(byte) 0x83 ,(byte) 0x82 ,(byte) 0x81 } );
    }
    
    private void writeEndOfDataMarker() {
    	generator.addWave( WavePeriod.LONG );
    	generator.addWave( WavePeriod.SHORT );
    }    
    
    private void writeSyncRepeated() 
    {
        if ( DEBUG ) {
            System.out.println("Generating: SYNC_REPEATED");
        }
        generator.addMarker("SYNC REPEATED");
        writeBytesWithParity( new byte[] { 0x09 ,0x08 ,0x07 ,0x06 ,0x05 ,0x04 ,0x03 ,0x02 ,0x01 } );
    }
    
    private void writeBytesWithParity(byte[] data) 
    {
        writeBytesWithParity( data , 0 , data.length );
    }
    
    private void writeBytesWithParity(byte[] data,int offset,int count) 
    {
        for ( int i = 0 ; i < count ; i++ ) {
            writeByte( data[offset+i] & 0xff );
        }
    }  
    
    private void writeWord(int word) {
    
        // write lo first, then hi
        writeByte( word & 0xff );
        writeByte( (word >>> 8  ) & 0xff );
    }
    
    private int writeRawByte(int value) {
        
        int mask = 0b0000_0001; 
        int oneBitCount=0;
        for ( int i = 0 ; i < 8 ; i++ ) 
        {
            final boolean bitSet = ( value & mask ) != 0;
            if ( bitSet ) {
                oneBitCount++;
            }
            generator.addBit( bitSet );
            mask <<= 1;
        }
        return oneBitCount;
    }    
    
    private void writeByte(int value) {
        
        // start of byte marker
        generator.addWave( WavePeriod.LONG );
        generator.addWave( WavePeriod.MEDIUM );
    	
        final int oneBitCount=writeRawByte(value);
        
        // write parity bit
        // Each byte on tape ends with a parity bit, which is either 0 or 1 as required to make the total number of 1 bits in the byte odd
        if ( (oneBitCount & 1 ) == 0 ) { // we have an even number of 1 bits, add another 1 bit to make it odd  
            generator.addBit( true );
        } else {  
            // we already have an odd number of 1 bits, add another 0 bit to leave it at that 
            generator.addBit( false );
        }
    }
    
    public void reset() {
        generator.rewind();
    }
}