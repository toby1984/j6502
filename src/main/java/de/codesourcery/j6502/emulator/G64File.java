package de.codesourcery.j6502.emulator;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;

import de.codesourcery.j6502.utils.BitStream;
import de.codesourcery.j6502.utils.HexDump;

/**
 * G64 disk format.
 * 
 * See http://www.unusedino.de/ec64/technical/formats/g64.html
 *
 * @author tobias.gierke@voipfuture.com
 */
public class G64File 
{

    private static final boolean DEBUG = false;

    /*
Nybble  Quintuple   
0000    01010 = 10      
0001    01011 = 11      
0010    10010 = 18      
0011    10011 = 19      
0100    01110 = 14      
0101    01111 = 15      
0110    10110 = 22      
0111    10111 = 23       
1000    01001 =  9   
1001    11001 = 25   
1010    11010 = 26
1011    11011 = 27   
1100    01101 = 13   
1101    11101 = 29
1110    11110 = 30
1111    10101 = 21
     */

    private static final int INVALID_GCR = 0xffffffff;

    public static final int[] TO_GCR = new int[] {
            0b01010,
            0b01011,
            0b10010,
            0b10011,
            0b01110,
            0b01111,
            0b10110,
            0b10111,
            0b01001,
            0b11001,
            0b11010,
            0b11011,
            0b01101,
            0b11101,
            0b11110,
            0b10101
    };

    public static final int[] FROM_GCR;

    static 
    {
        FROM_GCR = new int[32];
        Arrays.fill(FROM_GCR, INVALID_GCR );

        FROM_GCR[ 10 ] = 0b0000;     
        FROM_GCR[ 11 ] = 0b0001;     
        FROM_GCR[ 18 ] = 0b0010;     
        FROM_GCR[ 19 ] = 0b0011;     
        FROM_GCR[ 14 ] = 0b0100;     
        FROM_GCR[ 15 ] = 0b0101;     
        FROM_GCR[ 22 ] = 0b0110;     
        FROM_GCR[ 23 ] = 0b0111;      
        FROM_GCR[  9 ] = 0b1000;  
        FROM_GCR[ 25 ] = 0b1001;  
        FROM_GCR[ 26 ] = 0b1010;
        FROM_GCR[ 27 ] = 0b1011;  
        FROM_GCR[ 13 ] = 0b1100;  
        FROM_GCR[ 29 ] = 0b1101;
        FROM_GCR[ 30 ] = 0b1110;
        FROM_GCR[ 21 ] = 0b1111;       
    }

    private final SpeedZonesMap speedZonesMap = new SpeedZonesMap();

    protected final class TrackZoneSpeeds {

        private final int speed;
        private final boolean alwaysSameSpeed;

        public TrackZoneSpeeds(int speed) {
            this.speed = speed;
            this.alwaysSameSpeed = speed < 4;
        }

        public int getSpeedForByte(int byteOffset) 
        {
            if ( alwaysSameSpeed ) {
                return speed;
            }
            final int offsetInSpeedEntry = byteOffset/4;
            final int bitOffsetInSpeedEntry = 6 - (byteOffset - offsetInSpeedEntry)*2;
            final int mask = 0b11 << bitOffsetInSpeedEntry;
            final int value = (data[offsetInSpeedEntry] & 0xff);
            return (value & mask) >>> bitOffsetInSpeedEntry; 
        }
    }

    public final class SpeedZonesMap 
    {
        /*

  Now we can look at the speed zone area. Below is a dump of the speed zone
offsets.

      00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F        ASCII
      -----------------------------------------------   ----------------
0150: .. .. .. .. .. .. .. .. .. .. .. .. 03 00 00 00   ............????
0160: 00 00 00 00 03 00 00 00 00 00 00 00 03 00 00 00   ????????????????
0170: 00 00 00 00 03 00 00 00 00 00 00 00 03 00 00 00   ????????????????
0180: 00 00 00 00 03 00 00 00 00 00 00 00 03 00 00 00   ????????????????
0190: 00 00 00 00 03 00 00 00 00 00 00 00 03 00 00 00   ????????????????
01A0: 00 00 00 00 03 00 00 00 00 00 00 00 03 00 00 00   ????????????????
01B0: 00 00 00 00 03 00 00 00 00 00 00 00 03 00 00 00   ????????????????
01C0: 00 00 00 00 03 00 00 00 00 00 00 00 03 00 00 00   ????????????????
01D0: 00 00 00 00 03 00 00 00 00 00 00 00 03 00 00 00   ????????????????
01E0: 00 00 00 00 02 00 00 00 00 00 00 00 02 00 00 00   ????????????????
01F0: 00 00 00 00 02 00 00 00 00 00 00 00 02 00 00 00   ????????????????
0200: 00 00 00 00 02 00 00 00 00 00 00 00 02 00 00 00   ????????????????
0210: 00 00 00 00 02 00 00 00 00 00 00 00 01 00 00 00   ????????????????
0220: 00 00 00 00 01 00 00 00 00 00 00 00 01 00 00 00   ????????????????
0230: 00 00 00 00 01 00 00 00 00 00 00 00 01 00 00 00   ????????????????
0240: 00 00 00 00 01 00 00 00 00 00 00 00 00 00 00 00   ????????????????
0250: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00   ????????????????
0260: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00   ????????????????
0270: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00   ????????????????
0280: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00   ????????????????
0290: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00   ????????????????
02A0: 00 00 00 00 00 00 00 00 00 00 00 00 .. .. .. ..   ????????????....

  Bytes: $015C-015F: Speed zone entry for track 1 ($03,  in  LO/HI  format,
                     see below for more)
          0160-0163: Speed zone entry for track 1.5 ($03)
             ...
          02A4-02A7: Speed zone entry for track 42 ($00)
          02A8-02AB: Speed zone entry for track 42.5 ($00)

  Starting at $02AC is the first track entry (from above, it is  the  first
entry for track 1.0)

  The speed offset entries can be a little more complex. The 1541 has  four
speed zones defined, which means the drive can write data at four  distinct
speeds. On a normal 1541 disk, these zones are as follows:

        Track Range  Storage in Bytes    Speed Zone
        -----------  ----------------    ----------
           1-17           7820               3  (slowest writing speed)
          18-24           7170               2
          25-30           6300               1
          31-4x           6020               0  (fastest writing speed)


  Note that you can, through custom programming of  the  1541,  change  the
speed zone of any track to something different (change the 3 to  a  0)  and
write data differently.

All the zones  use  4-byte  entries  in lo-hi format. If the value of the 
entry is less than 4, then  there  is  no speed offset block for the track 
and the value  is  applied  to  the  whole track. If the value is greater 
than 4 then we have an  actual  file  offset referencing 
a speed zone block for the track.
         */
        public TrackZoneSpeeds getSpeedZone(float track) 
        {
            int offset = (int) ( track*2 -2 );
            offset *= 4 ; // 4 bytes per entry
            offset += 0x015c;

            /*
        Track Range  Storage in Bytes    Speed Zone
        -----------  ----------------    ----------
           1-17           7820               3  (slowest writing speed)
          18-24           7170               2
          25-30           6300               1
          31-4x           6020               0  (fastest writing speed)             
             */
            final int value = (data[offset+3] & 0xff) << 24 | (data[offset+2] & 0xff) << 16 | ( data[offset+1] & 0xff) << 8 | (data[offset] & 0xff);
            return new TrackZoneSpeeds( value ); 
        }
    }

    public final class FileHeader 
    {
        /**
         * Below is a dump of the header, broken down into its various parts.  After
         * that will be an explanation of the  track  offset  and  speed  zone  offset
         * areas, as they demand much more explanation.
         * 
         * 
         * Addr  00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F        ASCII
         * ----  -----------------------------------------------   ----------------
         * 0000: 47 43 52 2D 31 35 34 31 00 54 F8 1E .. .. .. ..   GCR-1541?T°?....
         * 
         *   Bytes: $0000-0007: File signature "GCR-1541"
         *                0008: G64 version (presently only $00 defined)
         *                0009: Number of tracks in image (usually $54, decimal 84)
         *           000A-000B: Size of each stored track in bytes (usually  7928,  or
         *                      $1EF8 in LO/HI format.         
         *
         * @author tobias.gierke@code-sourcery.de
         */
        public void assertValid() throws RuntimeException 
        {
            if ( data.length < 12 ) {
                throw new RuntimeException("Too few bytes, at least 12 header bytes are expected");
            }
            final String MAGIC = "GCR-1541";
            for ( int i = 0 ; i < MAGIC.length() ; i++ ) 
            {
                final byte exp = (byte) MAGIC.charAt( i );
                if ( data[i] != exp ) {
                    throw new RuntimeException("Bad magic byte at offset "+i+" , expected "+exp+" but got "+data[i]);
                }
            }
            final int version = data[8] & 0xff;
            if ( version != 0 ) {
                throw new RuntimeException("G64 file format version "+version+" is not supported");
            }
        }

        public int getTrackCount() {
            return data[9] & 0xff;
        }

        public int getTrackSizeInBytes() 
        {
            return toBigEndian( data[ 0x0a ] , data[ 0x0b ] );
        }
    }    

    public final class TrackDataOffsetMap 
    {
        /*
         * Below is a dump of the first section of a G64 file, showing  the  offsets
         * to the data portion for each track and half-track entry.
         * 
         *       00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F        ASCII
         *       -----------------------------------------------   ----------------
         * 0000: .. .. .. .. .. .. .. .. .. .. .. .. AC 02 00 00   ............????
         * 0010: 00 00 00 00 A6 21 00 00 00 00 00 00 A0 40 00 00   ?????!???????@??
         * 0020: 00 00 00 00 9A 5F 00 00 00 00 00 00 94 7E 00 00   ?????_???????~??
         * 0030: 00 00 00 00 8E 9D 00 00 00 00 00 00 88 BC 00 00   ????????????????
         * 0040: 00 00 00 00 82 DB 00 00 00 00 00 00 7C FA 00 00   ????????????|???
         * 0050: 00 00 00 00 76 19 01 00 00 00 00 00 70 38 01 00   ????v???????p8??
         * 0060: 00 00 00 00 6A 57 01 00 00 00 00 00 64 76 01 00   ????jW??????dv??
         * 0070: 00 00 00 00 5E 95 01 00 00 00 00 00 58 B4 01 00   ????^???????X+??
         * 0080: 00 00 00 00 52 D3 01 00 00 00 00 00 4C F2 01 00   ????R???????L???
         * 0090: 00 00 00 00 46 11 02 00 00 00 00 00 40 30 02 00   ????F???????@0??
         * 00A0: 00 00 00 00 3A 4F 02 00 00 00 00 00 34 6E 02 00   ????:O??????4n??
         * 00B0: 00 00 00 00 2E 8D 02 00 00 00 00 00 28 AC 02 00   ????.???????(???
         * 00C0: 00 00 00 00 22 CB 02 00 00 00 00 00 1C EA 02 00   ????"???????????
         * 00D0: 00 00 00 00 16 09 03 00 00 00 00 00 10 28 03 00   ?????????????(??
         * 00E0: 00 00 00 00 0A 47 03 00 00 00 00 00 04 66 03 00   ?????G???????f??
         * 00F0: 00 00 00 00 FE 84 03 00 00 00 00 00 F8 A3 03 00   ????????????°???
         * 0100: 00 00 00 00 F2 C2 03 00 00 00 00 00 EC E1 03 00   ?????+??????????
         * 0110: 00 00 00 00 E6 00 04 00 00 00 00 00 E0 1F 04 00   ????????????????
         * 0120: 00 00 00 00 DA 3E 04 00 00 00 00 00 D4 5D 04 00   ????+>???????]??
         * 0130: 00 00 00 00 CE 7C 04 00 00 00 00 00 C8 9B 04 00   ?????|??????????
         * 0140: 00 00 00 00 C2 BA 04 00 00 00 00 00 BC D9 04 00   ????+|???????+??
         * 0150: 00 00 00 00 B6 F8 04 00 00 00 00 00 .. .. .. ..   ?????°?????.....
         * 
         *   Bytes: $000C-000F: Offset  to  stored  track  1.0  ($000002AC,  in  LO/HI
         *                      format, see below for more)
         *           0010-0013: Offset to stored track 1.5 ($00000000)
         *           0014-0017: Offset to stored track 2.0 ($000021A6)
         *              ...
         *           0154-0157: Offset to stored track 42.0 ($0004F8B6)
         *           0158-015B: Offset to stored track 42.5 ($00000000)
         * 
         *   The track offsets require some explanation. When one is set to all 0's, no
         * track data exists for this entry. If there is a value, it  is  an  absolute
         * reference into the file (starting from the beginning of the file).
         */
        public int getTrackOffset(float track) 
        {
            // 1.0 / 0.5 = 2 => 0
            // 1.5 / 0.5 = 3 => 1
            // 2.0 / 0.4 = 4 => 2
            final float adjusted = (track/0.5f) - 2 ;
            final int index = 0x000c + (int) ( adjusted *4 );
            if ( DEBUG ) {
                System.out.println("Track "+track+" => offset @ "+wordToString(index));
            }

            if ( DEBUG ) {
                HexDump dump = new HexDump();
                System.out.println( dump.dump( (short) 0 , data , 0x000c , 32 ) );
            }

            final int byte1 = data[index  ] & 0xff;
            final int byte2 = data[index+1] & 0xff;
            final int byte3 = data[index+2] & 0xff;
            final int byte4 = data[index+3] & 0xff;

            if ( DEBUG ) {
                System.out.println("byte1: "+byteToString(byte1) );
                System.out.println("byte2: "+byteToString(byte2) );
                System.out.println("byte3: "+byteToString(byte3) );
                System.out.println("byte4: "+byteToString(byte4) );
            }
            final int value = byte4 << 24 | byte3 << 16 | byte2 << 8 | byte1;
            if ( DEBUG ) {
                System.out.println("Track "+track+" => starts at $"+Integer.toHexString( value ));
            }
            return value;
        }
    }

    private final byte[] data;
    private final BitStream bitStream;

    private int readByte() 
    {
        int value = bitStream.readBit();
        for ( int i = 0 ; i < 7 ; i++) {
            value <<= 1;
            value |= bitStream.readBit();
        }
        return value;
    }

    private int readGCRByte() 
    {
        // TODO: What comes first , lo nybble or hi ??
        int value = bitStream.readBit();
        for ( int i = 0 ; i < 4 ; i++) {
            value <<= 1;
            value |= bitStream.readBit();
        }

        int hi = gcrDecode( value );

        value = bitStream.readBit();
        for ( int i = 0 ; i < 4 ; i++) {
            value <<= 1;
            value |= bitStream.readBit();
        }
        int lo = gcrDecode( value );

        int result = hi << 4 | lo;
        return result;
    }

    private final TrackDataOffsetMap trackDataOffsetMap = new TrackDataOffsetMap();

    protected static int gcrDecode(int value) throws IllegalArgumentException
    {
        if ( value < 0 || value > 32 ) {
            throw new IllegalArgumentException("Cannot GCR-decode: "+value);
        }
        final int result = FROM_GCR[ value ];
        String binary = Integer.toBinaryString( result );
        while ( binary.length() < 4 ) {
            binary = "0"+binary;
        }
        if ( result == INVALID_GCR ) {
            throw new IllegalArgumentException("Cannot GCR-decode: "+value);
        }
        return result;
    }

    private final FileHeader fileHeader = new FileHeader();

    public G64File( InputStream in) throws IOException 
    {
        if ( in == null ) {
            throw new IllegalArgumentException("Input stream must not be NULL");
        }

        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try 
        { 
            int value = -1;
            while( ( ( value = in.read() ) != -1 ) ) {
                out.write( value );
            }
        } finally {
            in.close();
        }
        out.close();

        this.data = out.toByteArray();
        this.bitStream = new BitStream( data );

        if ( DEBUG ) {
            HexDump dump = new HexDump();
            System.out.println( dump.dump( (short) 0 , data , 0x000c , 32 ) );
        }

        getFileHeader().assertValid();
    }

    private static int toBigEndian( int lo , int hi ) {
        return (( hi & 0xff) << 8 ) | ( lo & 0xff);
    }

    public FileHeader getFileHeader() {
        return fileHeader;
    }

    public TrackDataOffsetMap getTrackOffsetMap() {
        return trackDataOffsetMap;
    }

    public static enum PartType { HEADER, DATA , SYNC , GAP  }

    public abstract class TrackPart 
    {
        public final PartType type;
        public final int firstBit;
        public final int lengthInBits;

        public TrackPart(PartType type,int firstBit,int lengthInBits) {
            this.type = type;
            this.firstBit = firstBit;
            this.lengthInBits = lengthInBits;
        }

        public abstract void read();

        public int getLengthInBits() {
            return lengthInBits;
        }
        
        @Override
        public String toString() {
            return type.toString()+" ( "+firstBit+" - "+firstBit + lengthInBits+" )";
        }
    }

    protected static String byteToString(int value) 
    {
        String result = Integer.toHexString( value & 0xff );
        while ( result.length() < 2 ) {
            result = "0"+result;
        }
        return "$"+result;
    }

    private static String wordToString(int value) 
    {
        String result = Integer.toHexString( value & 0xffff );
        while ( result.length() < 4 ) {
            result = "0"+result;
        }
        return "$"+result;
    }

    public final class HeaderPart extends TrackPart {

        private int blockId;
        private int headerBlockChecksum;
        private int sector;
        private int track;
        private int formatIdLo;
        private int formatIdHi;

        public HeaderPart(int firstBit) {
            super(PartType.HEADER,firstBit,6 * 10 ); // 6 GCR-encoded bytes
        }

        @Override
        public String toString() {
            return "HEADER[ blockId="+byteToString(blockId)+","+
                    "headerBlockChecksum="+byteToString( headerBlockChecksum)+","+
                    "track="+byteToString( track )+","+
                    "sector="+byteToString( sector )+","+
                    "formatId="+wordToString( toBigEndian( formatIdLo , formatIdHi ) )+" ]";
        }

        public void read() 
        {
            blockId = readGCRByte();
            if ( blockId != 0x08 ) {
                throw new RuntimeException("Illegal header block id "+blockId);
            }
            headerBlockChecksum = readGCRByte();
            sector = readGCRByte();
            track = readGCRByte();
            formatIdLo = readGCRByte();
            formatIdHi = readGCRByte();

            final int expectedCheckSum = xor( sector , track , formatIdLo , formatIdHi );
            if ( headerBlockChecksum != ( expectedCheckSum & 0xff) ) 
            {
                throw new RuntimeException("Invalid header checksum, got "+byteToString( headerBlockChecksum )+" but expected "+byteToString( expectedCheckSum) );
            }
        }
    }

    public final class GapPart extends TrackPart 
    {
        public GapPart(HeaderPart sectorHeader,int firstBit,int lengthInBits) 
        {
            super(PartType.GAP,firstBit,lengthInBits);
        }

        @Override
        public void read() {
        }
    }

    public final class SyncPart extends TrackPart 
    {
        public SyncPart(int firstBit,int lengthInBits) 
        {
            super(PartType.SYNC,firstBit,lengthInBits);
        }

        @Override
        public void read() {
        }
    }    

    public final class DataPart extends TrackPart {

        public final int trackNo;
        public final int sectorNo;
        private int blockId;
        private final byte[] sectorData = new byte[256];
        private int checksum;

        public DataPart(HeaderPart sectorHeader,int firstBit) {
            super(PartType.DATA,firstBit, 258*10);
            this.trackNo = sectorHeader.track;
            this.sectorNo = sectorHeader.sector;
        }

        @Override
        public String toString() {
            return "DATA[ blockId="+byteToString(blockId)+","+
                    "checksum="+byteToString( checksum)+" ]";
        }

        public void read() {
            /*
The 325 byte data block (#5) is GCR encoded and must be  decoded  to  its
normal 260 bytes to be understood. For comparison, ZipCode Sixpack  uses  a
326 byte GCR sector (why?), but the last byte (when properly rearranged) is
not used. The data block is made up of the following:

Byte    $00 - data block ID ($07)
01-100 - 256 bytes data
  101 - data block checksum (EOR of $01-100)
102-103 - $00 ("off" bytes, to make the sector size a multiple of 5)                     
             */
            blockId = readGCRByte();
            if ( blockId != 0x07 ) {
                throw new RuntimeException("Illegal data block id "+blockId);
            }
            int expectedChecksum = 0;
            for ( int i = 0 ; i < 256 ; i++ ) 
            {
                final int value = readGCRByte();
                sectorData[i] = (byte) value;
                expectedChecksum ^= value;
            }
            checksum = readGCRByte();
            if ( (expectedChecksum & 0xff) != checksum ) 
            {
                System.err.println("Invalid data block checksum, got "+byteToString( checksum )+" but expected "+byteToString( expectedChecksum ) );
            }
        }
    }    

    public final class TrackData 
    {
        private final float trackNo;
        private final int offset;
        public final int lengthInBytes;

        public TrackData(float trackNo, int offset,int lengthInBytes) 
        {
            if ( DEBUG ) {
                System.out.println("Track "+trackNo+" starts at offset "+offset+" and has "+lengthInBytes+" bytes");
            }
            this.trackNo = trackNo;
            this.offset = offset;
            this.lengthInBytes = lengthInBytes;
        }

        public byte[] getSectorData() {

            final List<DataPart> data = getParts().stream().filter( p -> p.type == PartType.DATA ).map( a -> (DataPart) a ).collect( Collectors.toList() );

            data.sort( (a,b) -> Integer.compare(a.sectorNo,b.sectorNo) );

            final ByteArrayOutputStream out = new ByteArrayOutputStream();
            data.forEach( s -> {
                try {
                    out.write( s.sectorData );
                } catch (Exception e) {
                    e.printStackTrace();
                } 
            });
            return out.toByteArray();
        }

        public List<TrackPart> getParts() 
        {
            bitStream.setStartingOffset( offset , lengthInBytes*8 );

            final List<TrackPart> parts = new ArrayList<>(); 

            /*
       1. Header sync       FF FF FF FF FF (40 'on' bits, not GCR)
       2. Header info       52 54 B5 29 4B 7A 5E 95 55 55 (10 GCR bytes)
       3. Header gap        55 55 55 55 55 55 55 55 55 (9 bytes, never read)
       4. Data sync         FF FF FF FF FF (40 'on' bits, not GCR)
       5. Data block        55...4A (325 GCR bytes)
       6. Inter-sector gap  55 55 55 55...55 55 (4 to 12 bytes, never read)         
             */
            boolean firstSyncFound = false;
            TrackPart previousPart = null;
            do
            {
                final int currentBit = bitStream.currentBitOffset();

                if ( ! skipSync() ) 
                {
                    if ( DEBUG ) {
                        System.out.println("No sync");
                    }
                    if ( bitStream.hasWrapped() ) {
                        return parts;
                    }
                    throw new NoSuchElementException("Missing SYNC");
                }

                parts.add( new SyncPart( currentBit , bitStream.currentBitOffset() - currentBit ) );

                if ( ! firstSyncFound ) {
                    bitStream.mark();
                    firstSyncFound = true;
                } 

                final int blockId = readGCRByte();
                bitStream.rewind(10); // 1 byte = 10 bits GCR

                final TrackPart part;
                if ( blockId == 0x08 ) 
                {
                    part  = new HeaderPart( bitStream.currentBitOffset() );
                } 
                else if ( blockId == 0x07 ) // data block
                { 
                    if ( previousPart == null ) {
                        throw new IllegalStateException("Data block without previous header");
                    }
                    if ( previousPart.type != PartType.HEADER ) {
                        throw new IllegalStateException("Two data blocks in a row ??");
                    }
                    part = new DataPart( (HeaderPart) previousPart , bitStream.currentBitOffset() );
                } else {
                    throw new RuntimeException("Unrecognized block ID: "+blockId);
                }
                part.read();
                if ( DEBUG ) {
                    System.out.println("READ: "+part);
                }
                parts.add( part );
                previousPart = part;
            } while ( ! bitStream.hasWrapped() );
            return parts;
        }
    }

    private static int xor(int i1,int i2,int... additional) {
        int result = 0;
        result ^= i1;
        result ^= i2;
        if ( additional != null ) 
        {
            for ( int i = 0 , len = additional.length ; i < len ; i++ ) {
                result ^= additional[i];
            }
        }
        return result;
    }

    public Optional<TrackData> getTrackData(float trackNo) 
    {
        final int offset = trackDataOffsetMap.getTrackOffset( trackNo );
        final int totalSizeInBytes = toBigEndian( data[offset] , data[offset+1] );
        if ( offset == 0 ) { // g64 file holds no data for this track
            return Optional.empty();
        }
        return Optional.of( new TrackData( trackNo , offset+2 , totalSizeInBytes ) );
    }

    private boolean skipSync() throws NoSuchElementException
    {
        int successiveOneBits = 0;

        //        System.out.println("Looking for sync @ "+bitStream);
        while ( ! bitStream.hasWrapped() )
        {
            final int bit = bitStream.readBit();
            if ( bit != 0 ) {
                successiveOneBits++;
            } 
            else 
            {
                // zero bit read
                if ( successiveOneBits >= 40 ) 
                {
                    //                    System.out.println("Zero after "+successiveOneBits+" 1'er bits");
                    bitStream.rewind(1);
                    return true;
                }
                successiveOneBits = 0;
            }
        }
        return false;
    }

    public static void main(String[] args) throws IOException 
    {
        final InputStream in = G64File.class.getResourceAsStream( "/disks/pitfall.g64" );
        final G64File file = new G64File( in );

        ByteArrayOutputStream d64Out = new ByteArrayOutputStream();

        for ( int i = 1 ; i < 36 ; i++ ) 
        {
            System.out.println("=== Track "+i+" ===");
            final Optional<TrackData> trackData = file.getTrackData( i );
            if ( trackData.isPresent() ) {
                d64Out.write( trackData.get().getSectorData() );
            }
        }
        byte[] raw = d64Out.toByteArray();
        FileOutputStream fileOut = new FileOutputStream("/home/tobi/tmp/pitfall_from_g64.d64");
        fileOut.write( raw );
        fileOut.close();
        System.out.println("D64 file has "+raw.length+" bytes");
    }
}