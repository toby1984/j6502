package de.codesourcery.j6502.emulator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import static de.codesourcery.j6502.emulator.SerializationHelper.*;

import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.utils.Misc;

public class EmulationState
{
    private static final byte[] MAGIC = { (byte) 0xde,(byte) 0xad,(byte) 0xbe,(byte) 0xef };

    private static final byte FILEFORMAT_VERSION = 1;

    public static enum EntryType 
    {
        HEADER((byte) 1),
        RAM((byte) 2),
        FLOPPY_RAM((byte) 3),
        C64_CPU((byte) 4),
        FLOPPY_CPU((byte) 5),
        CIA1_RAM((byte) 6),
        CIA1_FIELDS((byte) 7),
        CIA2_RAM((byte) 8),
        CIA2_FIELDS((byte) 9),
        VIC_RAM((byte) 10),
        VIC_FIELDS((byte) 11),
        IO_AREA((byte) 12);

        public final byte typeId;

        private EntryType(byte typeId) {
            this.typeId = typeId;
        }

        public static EntryType fromTypeId(byte id) {
            for ( EntryType t : values() ) {
                if ( t.typeId == id ) {
                    return t;
                }
            }
            throw new NoSuchElementException("Unknown entry type ID: "+id);
        }
    }

    public static final class EmulationStateEntry 
    {
        public EntryType type;
        public byte version;
        private byte[] payload;

        private int checksum;

        public EmulationStateEntry(EntryType type, int version) {

            Validate.notNull(type, "type must not be NULL");
            if ( version > Byte.MAX_VALUE || version < Byte.MIN_VALUE) {
                throw new IllegalArgumentException("Version out of range: "+version);
            }
            this.type = type;
            this.version = (byte) version;
            this.payload = new byte[0];
        }

        public byte[] getPayload()
        {
            return payload;
        }
        
        public EmulationStateEntry addTo(EmulationState state) {
            state.add( this );
            return this;
        }
        
        public ByteArrayInputStream toByteArrayInputStream() {
            return new ByteArrayInputStream( this.payload );
        }
        
        public EmulationStateEntry(EntryType type, byte version, byte[] payload)
        {
            Validate.notNull(type, "type must not be NULL");
            Validate.notNull(payload, "payload must not be NULL");
            this.type = type;
            this.version = version;
            this.payload = payload;
        }

        public int payloadLength() {
            return payload == null ? 0 : payload.length;
        }
        
        public EmulationStateEntry setPayload(byte[] data) {
            Validate.notNull(payload, "payload must not be NULL");
            this.payload = data;
            return this;
        }
        
        public EmulationStateEntry setPayload(IMemoryRegion region) {
            
            final int len = region.getAddressRange().getSizeInBytes();
            this.payload = new byte[ len ];
            for ( int i = 0 ; i < len ; i++ ) 
            {
                this.payload[i] = (byte) region.readByteNoSideEffects( i );
            }
            return this;
        }
        
        public void applyPayload(IMemoryRegion region,boolean applySideEffects) 
        {
            if ( applySideEffects ) 
            {
                for ( int i = 0,len = payload.length ; i < len ; i++ ) 
                {
                    region.writeByte( i , payload[i] );
                }
            } 
            else 
            {
                for ( int i = 0,len = payload.length ; i < len ; i++ ) 
                {
                    region.writeByteNoSideEffects( i , payload[i] );
                }
            }
        }

        @Override
        public String toString()
        {
            final String len = payload == null ? "<no payload>" : ""+payload.length;
            final String checksumOk;
            if ( checksumOk() ) {
                checksumOk = "OK ("+Misc.to32BitHex( this.checksum )+")";
            } else {
                checksumOk = "ERROR ( expected "+Misc.to32BitHex( calcChecksum() )+" , is "+Misc.to32BitHex( this.checksum )+")";
            }
            return "Entry[ type "+type+" , version "+version+", payload length: "+len+", "
            + "checksum: "+checksumOk+" ]";
        }

        @Override
        public int hashCode()
        {
            int result = 31 + checksum;
            result = 31 * result + Arrays.hashCode(payload);
            result = 31 * result + type.hashCode();
            return 31 * result + version;
        }

        @Override
        public boolean equals(Object obj)
        {
            if ( obj instanceof EmulationStateEntry) 
            {
                final EmulationStateEntry other = (EmulationStateEntry) obj;
                return checksum == other.checksum &&
                        Arrays.equals(payload, other.payload) &&
                        type == other.type &&
                        version == other.version;
            }
            return false;
        }

        public boolean checksumOk() 
        {
            final int expected = calcChecksum();
            final int actual = checksum;
            final boolean result = expected == actual;
            if ( ! result ) {
                new Exception("dummy").printStackTrace();
            }
            return result;
        }

        private void setChecksum(int checksum) {
            this.checksum = checksum;
        }

        @SuppressWarnings("resource")
        private int calcChecksum() {
            return calcChecksum( new ChecksumCalculator() ).checksum;
        }

        public ChecksumCalculator calcChecksum(ChecksumCalculator chksum) 
        {
            chksum.reset();
            try 
            {
                chksum.write( type.typeId );
                chksum.write( version );
                writeIntNoTag( payloadLength() , chksum );
                if ( payload != null ) {
                    chksum.write( payload );
                }
                return chksum;
            } 
            catch (IOException e) 
            {
                e.printStackTrace();
                throw new RuntimeException(e);
            }            
        }

        public boolean hasType(EntryType t) {
            return t.equals( this.type );
        }

        public int getChecksum()
        {
            return checksum;
        }
    }

    private final List<EmulationStateEntry> entries = new ArrayList<>();

    private EmulationState() 
    {
    }

    public static EmulationState newInstance() 
    {
        final EmulationState result = new EmulationState();
        final byte[] payload = new byte[ MAGIC.length + 1 ]; // last byte holds header count, set when writing
        System.arraycopy( MAGIC , 0 , payload , 0 , MAGIC.length );
        final EmulationStateEntry header = new EmulationStateEntry( EntryType.HEADER , FILEFORMAT_VERSION , payload );
        setHeaderCount( header , 0 );
        header.setChecksum( header.calcChecksum() );
        result.add( header );
        return result;
    }    

    public EmulationStateEntry add(EmulationStateEntry entry) 
    {
        if ( entries.stream().anyMatch( existing -> existing.hasType( entry.type ) ) ) 
        {
            throw new IllegalArgumentException("Cannot add more than one entry for any type, offender: "+entry);
        }
        this.entries.add(entry);
        return entry;
    }
    
    public void visitEntries(Consumer<EmulationStateEntry> visitor)
    {
        entries.forEach( visitor );
    }

    public boolean hasEntries() {
        return ! entries.isEmpty();
    }

    public static EmulationState read(byte[] data) 
    {
        try {
            return read( new ByteArrayInputStream( data ) );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static EmulationState read(InputStream in) throws IOException 
    {
        final EmulationState result = new EmulationState();
        EmulationStateEntry entry=null;
        while ( ( entry = readEntry(in) ) != null ) 
        {
            result.add( entry );
        }

        if ( ! result.hasEntries() ) {
            throw new EOFException("File is empty");
        }
        final EmulationStateEntry first = result.entries.get(0);
        if ( ! first.hasType( EntryType.HEADER ) ) 
        {
            throw new IOException("Expected a HEADER type first but got "+first.type);
        }
        if ( first.version != FILEFORMAT_VERSION ) {
            throw new IOException("Unsupported file format version: "+first.version);
        }  

        if ( first.payloadLength() < MAGIC.length+1 ) {
            throw new IOException("File magic not recognized");
        }

        if ( ! Arrays.equals( MAGIC , Arrays.copyOf(first.payload , MAGIC.length) ) ) { 
            throw new IOException("File magic not recognized");
        }
        
        final int headerCount = EmulationState.getHeaderCount( first );
        if ( headerCount != result.entries.size() ) {
            throw new IOException("Expected "+headerCount+" header entries but got only "+result.entries.size());
        }

        return result;
    }

    private static EmulationStateEntry readEntry(InputStream in) throws IOException 
    {
        int value = in.read();
        if ( value == -1 ) {
            return null;
        }
        final EntryType type = EntryType.fromTypeId( (byte) value );
        final byte version = (byte) readByteNoTag( in );
        final int checksum = readIntNoTag( in );
        final int payloadLength = readIntNoTag( in );
        final byte[] payload = new byte[ payloadLength ];
        for ( int i = 0 ; i < payloadLength ; i++ ) 
        {
            payload[i] = (byte) readByteNoTag( in ); 
        }

        final EmulationStateEntry result = new EmulationStateEntry( type , version , payload );
        result.setChecksum( checksum );
        if ( ! result.checksumOk() ) 
        {
            throw new IOException("File corrupted - checksum violation on entry "+result);
        }
        return result;
    }

    protected static final class ChecksumCalculator extends OutputStream {

        public int checksum;
        private int index = 1;

        @Override
        public void write(int b) throws IOException
        {
            checksum = 31*checksum + index * ( b & 0xff );
        }

        public void reset() {
            checksum = 0;
            index = 1;
        }
    }

    private static void setHeaderCount(EmulationStateEntry e,int count) 
    {
        if ( ! e.hasType( EntryType.HEADER ) ) {
            throw new IllegalArgumentException("Expected a header but got "+e);
        }
        if ( e.payload == null || e.payload.length != MAGIC.length+1 ) {
            throw new RuntimeException("Header payload has bad length: "+e);
        }
        if ( count > 255 ) {
            throw new IllegalArgumentException("Header count too large: "+count);
        }
        e.payload[ e.payload.length - 1 ] = (byte) count;
    }

    private static int getHeaderCount(EmulationStateEntry e) 
    {
        if ( ! e.hasType( EntryType.HEADER ) ) {
            throw new IllegalArgumentException("Expected a header but got "+e);
        }
        if ( e.payload == null || e.payload.length != MAGIC.length+1 ) {
            throw new RuntimeException("Header payload has bad length: "+e);
        }
        return e.payload[ e.payload.length - 1 ] & 0xff;
    }


    public void write(OutputStream out) throws IOException 
    {
        @SuppressWarnings("resource")
        final ChecksumCalculator chksum = new ChecksumCalculator(); 

        for ( EmulationStateEntry entry : entries ) 
        {
            if ( entry.hasType( EntryType.HEADER ) ) 
            {
                setHeaderCount( entry , entries.size() );
            }
            entry.setChecksum( entry.calcChecksum( chksum ).checksum );

            out.write( entry.type.typeId );
            out.write( entry.version );
            writeIntNoTag( entry.checksum , out );
            writeIntNoTag( entry.payloadLength() , out );
            if ( entry.payload != null ) {
                out.write( entry.payload );
            }
        }
    }

    public byte[] toByteArray() 
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try {
            write( out );
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return out.toByteArray();
    }

    @Override
    public String toString()
    {
        return this.entries.stream().map( e -> e.toString() ).collect( Collectors.joining("\n") );
    }

    @Override
    public int hashCode()
    {
        return 31 + entries.hashCode();
    }

    @Override
    public boolean equals(Object obj)
    {
        if ( obj instanceof EmulationState) {
            final EmulationState other = (EmulationState) obj;
            return entries.equals(other.entries);
        }
        return false;
    }
    
    public Optional<EmulationStateEntry> maybeGetEntry(EntryType type) 
    {
        return entries.stream().filter( e -> e.hasType( type ) ).findFirst();
    }
    
    public EmulationStateEntry getEntry(EntryType type) 
    {
        return maybeGetEntry(type).orElseThrow( () -> new NoSuchElementException("Found no entry with type "+type ) );
    }    
}