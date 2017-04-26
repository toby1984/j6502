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
        VIC_FIELDS((byte) 11);

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
            System.out.println( expected+" = "+actual+" => "+result);
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
                writeInt( payloadLength() , chksum );
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

        return result;
    }

    private static EmulationStateEntry readEntry(InputStream in) throws IOException 
    {
        int value = in.read();
        if ( value == -1 ) {
            return null;
        }
        final EntryType type = EntryType.fromTypeId( (byte) value );
        final byte version = (byte) readByte( in );
        final int checksum = readInt( in );
        final int payloadLength = readInt( in );
        final byte[] payload = new byte[ payloadLength ];
        for ( int i = 0 ; i < payloadLength ; i++ ) 
        {
            payload[i] = (byte) readByte( in ); 
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
            System.out.println("calculated checksum: "+Misc.to32BitHex( entry.checksum ) );

            out.write( entry.type.typeId );
            out.write( entry.version );
            writeInt( entry.checksum , out );
            writeInt( entry.payloadLength() , out );
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

    public static int readByte(InputStream in) throws IOException {
        final int value = in.read();
        if ( value == -1 ) {
            throw new EOFException("Premature end of file");
        }
        return value & 0xff;
    }
    
    public static void writeLong(long value, OutputStream out) throws IOException {

        writeInt( (int) (( value >> 32) & 0xffffffff) , out );
        writeInt( (int) (value & 0xffffffff) , out );
    }
    
    public static long readLong(InputStream in) throws IOException {

        final long hi = readInt( in ) & 0xffffffff;
        final long lo = readInt( in ) & 0xffffffff;
        return hi << 32 | lo;
    }    
    
    public static void writeIntArray(int[] array, OutputStream out) throws IOException {
        writeInt( array.length , out );
        for (  int v : array ) {
            writeInt( v , out );
        }
    }
    
    public static int[] readIntArray(InputStream in) throws IOException {
        final int len = readInt( in );
        final int[] result = new int[len];
        for ( int i = 0 ; i < len ; i++ ) {
            result[i] = readByte(in);
        }
        return result;
    }
    
    public static void populateIntArray(int[] arrayToFill, InputStream in) throws IOException {
        final int len = readInt( in );
        if ( len != arrayToFill.length ) {
            throw new IllegalArgumentException("Input array has size "+arrayToFill.length+" but de-serialized array has size "+len);
        }
        for ( int i = 0 ; i < len ; i++ ) {
            arrayToFill[i] = readInt(in);
        }
    }
    
    public static void writeInt(int value, OutputStream out) throws IOException {

        writeShort( (short) ((value >> 16) & 0xffff), out );
        writeShort( (short) (value & 0xffff), out );
    }
    
    public static int readInt(InputStream in) throws IOException {

        final int hi = readShort( in ) & 0xffff; 
        final int lo = readShort( in ) & 0xffff;
        return hi << 16 | lo;
    }
    
    public static void writeShort(int value, OutputStream out) throws IOException {

        out.write( (value >>  8) & 0xff );
        out.write( (value      ) & 0xff );
    }    
    
    public static short readShort(InputStream in) throws IOException {

        final int hi = readByte( in ) & 0xff;
        final int lo = readByte( in ) & 0xff;
        return (short) (hi << 8 | lo);
    }     
    
    public static void writeBoolean(boolean value,OutputStream out) throws IOException 
    {
        out.write( value ? 0xff : 0 );
    }
    
    public static boolean readBoolean(InputStream in) throws IOException 
    {
        return readByte( in ) != 0;
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