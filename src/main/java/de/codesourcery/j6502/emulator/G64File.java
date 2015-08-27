package de.codesourcery.j6502.emulator;

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.utils.BitOutputStream;
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
	private final TrackDataOffsetMap trackDataOffsetMap = new TrackDataOffsetMap();
	private final FileHeader fileHeader = new FileHeader();

	private final byte[] data;

	public static final class GCRDecodingException extends RuntimeException {

		public GCRDecodingException(int offendingValue) {
			super("Invalid GCR value: "+offendingValue);
		}
	}

	public final class TrackZoneSpeeds {

		public final int speed;
		public final boolean alwaysSameSpeed;

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
			assertValidTrackNo(track);
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

	protected static void assertValidTrackNo(float track) {
		if ( track < 1.0 || track > 42.5 ) {
			throw new IllegalArgumentException("Track number out-of-range: "+track);
		}
	}

	protected static final int[] EMPTY_ARRAY = new int[0];

	protected static final class GCRDecodingResult
	{
		public int[] decodedData;
		public int[] nibblesWithDecodingErrors; // nibble offset, 0 = hi-nibble of first byte, 1 = lo-nibble of first byte, 2 = hi-nibble of second byte, ...

		public GCRDecodingResult(int[] decodedData, int[] nibblesWithDecodingErrors) {
			this.decodedData = decodedData;
			this.nibblesWithDecodingErrors = nibblesWithDecodingErrors;
		}

		public GCRDecodingResult(int[] decodedData) {
			this.decodedData = decodedData;
			this.nibblesWithDecodingErrors = EMPTY_ARRAY;
		}
	}

	private static int readGCRByte(BitStream bitStream) throws GCRDecodingException
	{
		// FIRST read data so bitstream gets advanced even if GCR decoding later fails
		final int hiNibble = readNibble(bitStream);
		final int loNibble = readNibble(bitStream);
		return gcrDecode( hiNibble , loNibble );
	}

	private static void gcrEncode(int byteValue,BitOutputStream out)
	{
		int lo = TO_GCR[ byteValue & 0b1111 ];
		int hi = TO_GCR[ (byteValue & 0b1111_0000) >>> 4 ];
		out.writeBits( hi ,  5 );
		out.writeBits( lo ,  5 );
	}

	private static int gcrDecode(int hiNibble,int loNibble) throws GCRDecodingException {
		final int hi = gcrDecode( hiNibble );
		final int lo = gcrDecode( loNibble );
		return hi << 4 | lo;
	}

	private static int readNibble(BitStream bitStream)
	{
		int hiValue = bitStream.readBit();
		for ( int i = 0 ; i < 4 ; i++) {
			hiValue <<= 1;
			hiValue |= bitStream.readBit();
		}
		return hiValue;
	}

	private GCRDecodingResult readGCRBytes(BitStream bitStream,int count) throws GCRDecodingException
	{
		int[] result = new int[count];
		final int[] nibbles = new int[ count*2 ];
		// FIRST read data so bitstream gets advanced even if GCR decoding later fails
		for ( int i = 0 , j = 0 ; i < count ; i++ , j+= 2 ) {
			nibbles[j] = readNibble(bitStream); // hi nibble
			nibbles[j+1] = readNibble(bitStream); // low nibble
		}
		// now try to decode it
		List<Integer> decodingErrors = null;
		for ( int i = 0 , j = 0 ; i < count ; i++ )
		{
			int hiNibble = 0;
			int loNibble = 0;
			try
			{
				hiNibble = gcrDecode( nibbles[j] );
			}
			catch(GCRDecodingException e)
			{
				if ( decodingErrors == null )
				{
					decodingErrors = new ArrayList<>();
				}
				decodingErrors.add( Integer.valueOf(j ) );
			}
			j++;

			try {
				loNibble = gcrDecode( nibbles[j] );
			}
			catch(GCRDecodingException e)
			{
				if ( decodingErrors == null )
				{
					decodingErrors = new ArrayList<>();
				}
				decodingErrors.add( Integer.valueOf(j ) );
			}
			j++;

			result[i] = hiNibble << 4 | loNibble;
		}

		if ( decodingErrors == null ) {
			return new GCRDecodingResult( result );
		}
		final int[] errors = new int[ decodingErrors.size() ];
		for ( int i = 0 ; i < decodingErrors.size() ; i++ )
		{
			errors[i] = decodingErrors.get(i).intValue();
		}
		return new GCRDecodingResult( result , errors );
	}

	protected static int gcrDecode(int value) throws GCRDecodingException
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
			throw new GCRDecodingException( value );
		}
		return result;
	}

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

	public static enum PartType { HEADER, DATA , SYNC , GAP , UNKNOWN  }

	public static enum ErrorType { CHECKSUM_ERROR , UNKNOWN_BLOCK_ID , INVALID_GCR ,

		// header on physical track X contains track no Y where X != Y
		TRACK_NUMBER_MISMATCH,
		// two headers on the same track share the same sector number
		DUPLICATE_SECTOR_NUMBER;
	}

	public static final class Error
	{
		public final ErrorType type;
		public int nibbleOffset; // nibble offset relative to start of TrackPart

		public Error(ErrorType type, int nibbleOffset)
		{
			Validate.notNull(type, "type must not be NULL");
			this.type = type;
			this.nibbleOffset = nibbleOffset;
		}

		@Override
		public String toString() {
			return type+" at nibble "+nibbleOffset;
		}
	}

	public final class UnknownPart extends TrackPart {

		public UnknownPart(int firstBit, int lengthInBits) {
			super(PartType.UNKNOWN, firstBit, lengthInBits);
		}

		@Override
		public void read(BitStream bitStream)
		{
		}

		@Override
		protected String getDetailString() {
			return "";
		}
	}

	public abstract class TrackPart
	{
		public final PartType type;
		public final int firstBit;
		public final int lengthInBits;

		private final List<Error> otherErrors = new ArrayList<>();

		public TrackPart(PartType type,int firstBit,int lengthInBits)
		{
			if ( lengthInBits <= 0 ) {
				throw new IllegalArgumentException("Part length must be >0 , was: "+lengthInBits);
			}
			if ( firstBit < 0 ) {
				throw new IllegalArgumentException("firstBit must be >= 0, was: "+firstBit);
			}
			Validate.notNull(type, "type must not be NULL");
			this.type = type;
			this.firstBit = firstBit;
			this.lengthInBits = lengthInBits;
		}

		public final HeaderPart asHeader() {
			return (HeaderPart) this;
		}

		public final DataPart asData() {
			return (DataPart) this;
		}

		public final boolean isData() {
			return hasType(PartType.DATA);
		}

		public final boolean isHeader() {
			return hasType(PartType.HEADER);
		}

		public final boolean hasType(PartType t) {
			return t.equals( type );
		}

		public final void registerError(ErrorType type, int nibbleOffset)
		{
			otherErrors.add( new Error(type,nibbleOffset));
		}

		public final List<Error> getErrors()
		{
			if ( otherErrors.isEmpty() )
			{
				return parseErrors();
			}
			return Stream.concat( otherErrors.stream(), parseErrors().stream() ).collect( Collectors.toCollection( ArrayList::new ) );
		}

		protected List<Error> parseErrors() {
			return Collections.emptyList();
		}

		public abstract void read(BitStream bitStream);

		public final int getLengthInBits() {
			return lengthInBits;
		}

		@Override
		public final String toString()
		{
			String details = getDetailString();
			if ( details.length() > 0 ) {
				details = " , "+details;
			}
			String errors = hasErrors() ? ", ERRORS!" : "";

			final float lengthInBytes = lengthInBits/8f;
			final DecimalFormat DF = new DecimalFormat("#####0.###");
			return type.toString()+" ( bit "+firstBit+" , "+lengthInBits+" bits = "+DF.format( lengthInBytes )+" bytes "+errors+details+")";
		}

		protected String getDetailString() {
			return "";
		}

		public final boolean hasErrors() {
			return ! otherErrors.isEmpty() || ! getErrors().isEmpty();
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

	public final class HeaderPart extends TrackPart
	{
		private GCRDecodingResult decodingResult;
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
		protected String getDetailString()
		{
			return "blockId: "+byteToString(blockId)+","+
					"checksum: "+byteToString( headerBlockChecksum)+","+
					"track: "+byteToString( track )+","+
					"sector:"+byteToString( sector )+","+
					"formatId: "+wordToString( toBigEndian( formatIdLo , formatIdHi ) );
		}

		@Override
		protected List<Error> parseErrors()
		{
			final List<Error> result = new ArrayList<>();

			if ( blockId != 0x08 ) {
				result.add( new Error(ErrorType.UNKNOWN_BLOCK_ID , 0 ) );
			}

			final int expectedCheckSum = xor( sector , track , formatIdLo , formatIdHi );
			if ( headerBlockChecksum != ( expectedCheckSum & 0xff) )
			{
				result.add( new Error(ErrorType.CHECKSUM_ERROR , 0 ) );
			}

			if ( decodingResult.nibblesWithDecodingErrors.length > 0 )
			{
				for ( int nibble : decodingResult.nibblesWithDecodingErrors )
				{
					result.add( new Error(ErrorType.INVALID_GCR , nibble ) );
				}
			}
			return result;
		}

		@Override
		public void read(BitStream bitStream)
		{
			decodingResult = readGCRBytes( bitStream , 6 );

			final int[] data = decodingResult.decodedData;

			blockId = data[0];
			headerBlockChecksum = data[1];
			sector = data[2];
			track = data[3];
			formatIdLo = data[4];
			formatIdHi = data[5];
		}
	}

	public final class GapPart extends TrackPart
	{
		public GapPart(int firstBit,int lengthInBits)
		{
			super(PartType.GAP,firstBit,lengthInBits);
		}

		@Override
		public void read(BitStream bitStream) {
		}
	}

	public final class SyncPart extends TrackPart
	{
		public SyncPart(int firstBit,int lengthInBits)
		{
			super(PartType.SYNC,firstBit,lengthInBits);
		}

		@Override
		public void read(BitStream bitStream) {
		}
	}

	public final class DataPart extends TrackPart {

		public HeaderPart header;
		private GCRDecodingResult decodingResult;
		private int blockId;
		private final byte[] sectorData = new byte[256];
		private int checksum;

		public DataPart(int firstBit) {
			super(PartType.DATA,firstBit, 258*10);
		}

		@Override
		protected String getDetailString() {
			return "blockId: "+byteToString(blockId)+", checksum: "+byteToString( checksum);
		}

		@Override
		public void read(BitStream bitStream)
		{
			decodingResult = readGCRBytes( bitStream, 256+2 );

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
			final int[] data = decodingResult.decodedData;
			blockId = data[0];
			checksum = data[257];

			for ( int i = 0 ; i < 256 ; i++ )
			{
				final int value = data[i+1];
				sectorData[i] = (byte) value;
			}
		}

		@Override
		protected List<Error> parseErrors()
		{
			final List<Error> result = new ArrayList<>();

			if ( blockId != 0x07 ) {
				result.add( new Error(ErrorType.UNKNOWN_BLOCK_ID , 0 ) );
			}

			int expectedChecksum = 0;
			for ( int i = 0 ; i < 256 ; i++ )
			{
				final int value = sectorData[i] & 0xff;
				expectedChecksum ^= value;
			}

			if ( expectedChecksum != checksum )
			{
				result.add( new Error(ErrorType.CHECKSUM_ERROR , 0 ) );
			}

			if ( decodingResult.nibblesWithDecodingErrors.length > 0 )
			{
				for ( int nibble : decodingResult.nibblesWithDecodingErrors )
				{
					result.add( new Error(ErrorType.INVALID_GCR , nibble ) );
				}
			}
			return result;
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

		public boolean isTrackComplete()
		{
			final Map<Integer,Integer> sectorNumbersByCount = new HashMap<>();

			getParts().stream()
			.filter( TrackPart::isHeader )
			.mapToInt( h -> ((HeaderPart) h).sector )
			.forEach( sectorNum -> sectorNumbersByCount.merge( sectorNum  , 1 , (a,b) -> a.intValue() + b.intValue() ) );

			final int truncatedTrackNo = ( (int) (trackNo*10)) / 10 ;
			final int expected = D64File.getSectorsOnTrack( truncatedTrackNo );

			for ( int sectorNo = 0 ; sectorNo < expected ; sectorNo++ )
			{
				final Integer occurranceCount =  sectorNumbersByCount.get( Integer.valueOf( sectorNo ) );
				if ( occurranceCount == null ) {
					System.err.println("No header for track "+trackNo+" , sector "+sectorNo);
					return false;
				}
				if ( occurranceCount.intValue() != 1 ) {
					System.err.println("Found more than one header for track "+trackNo+" , sector "+sectorNo);
					return false;
				}
			}
			return true;
		}

		public byte[] getSectorData() {

			if ( ! isTrackComplete() ) {
				throw new RuntimeException("Cannot get sector from track "+trackNo+" , track is not complete");
			}

			final List<DataPart> data = getParts().stream().filter( p -> p.type == PartType.DATA ).map( a -> (DataPart) a ).collect( Collectors.toList() );

			final Comparator<? super DataPart> comp = (a,b) ->
			{
				if ( a.header != null && b.header != null ) {
					return Integer.compare(a.header.sector , a.header.sector );
				}
				if ( a.header != null ) {
					return 1;
				}
				return b.header != null ? -1 : 0;
			};
			data.sort( comp );

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
			final TrackParser parser = new TrackParser( trackNo , G64File.this.data );
			parser.parse( offset , lengthInBytes );
			return parser.getParts();
		}
	}

	protected final class TrackParser
	{
		private List<TrackPart> partsList = new ArrayList<>();

		private boolean firstSyncFound = false;
		private final BitStream bitStream;
		private final float trackNo;

		public TrackParser(final float trackNo,byte[] g64DiskData) {
			bitStream = new BitStream( g64DiskData );
			this.trackNo = trackNo;
		}
		public List<TrackPart> getParts() {
			return partsList;
		}

		public void parse(int offset,int lengthInBytes )
		{
			firstSyncFound = false;
			partsList.clear();
			bitStream.reset();
			bitStream.setStartingOffset( offset , lengthInBytes*8 );

			if ( DEBUG ) {
				System.out.println("@ start: bitstream @ "+bitStream.currentBitOffset());
			}
			/*
       1. Header sync       FF FF FF FF FF (40 'on' bits, not GCR)
       2. Header info       52 54 B5 29 4B 7A 5E 95 55 55 (10 GCR bytes)
       3. Header gap        55 55 55 55 55 55 55 55 55 (9 bytes, never read)
       4. Data sync         FF FF FF FF FF (40 'on' bits, not GCR)
       5. Data block        55...4A (325 GCR bytes)
       6. Inter-sector gap  55 55 55 55...55 55 (4 to 12 bytes, never read)
			 */

			if ( DEBUG ) {
				System.out.println("Scanning track "+trackNo+" with "+lengthInBytes+" bytes");
			}

			if ( skipSync( false , false ) )
			{
				outer:
					do
					{
						if ( DEBUG ) {
							System.out.println("Bitstream @ "+bitStream.currentBitOffset());
						}
						final int currentStart = bitStream.currentBitOffset();
						int blockId = 0;
						try
						{
							blockId = readGCRByte(bitStream);
							bitStream.rewind(10); // 1 byte = 10 bits GCR
						}
						catch(GCRDecodingException e)
						{
							System.err.println("Reading block ID failed with GCR decoding error,advancing to next sync");
							bitStream.rewind( 10 );
							if ( ! skipSync( false , true ) )
							{
								System.err.println("No (more) syncs");
								break;
							}
							continue;
						}

						final TrackPart part;
						switch( blockId ) {
							case 0x08:
								part  = new HeaderPart( currentStart );
								break;
							case 0x07:  // data block
								part = new DataPart( currentStart );
								break;
							default:
								System.err.println("Unrecognized block ID: "+blockId);
								if ( ! skipSync( false , true ) )
								{
									System.err.println("No (more) syncs");
									break outer;
								}
								continue;
						}

						part.read(bitStream);

						if ( DEBUG ) {
							System.out.println("READ: "+part);
						}

						addPart( part );

						// check whether track no from sector header matches our expectation
						if ( part instanceof HeaderPart && isEvenTrackNo( trackNo ) )
						{
							// if this is a half-track, we can't tell which track no. will be stored , do not check
							final int actualTrackNo = ( (int) (trackNo*10)) / 10 ;
							final HeaderPart header = (HeaderPart) part;
							if ( header.track != actualTrackNo ) {
								header.registerError( ErrorType.TRACK_NUMBER_MISMATCH , header.firstBit );
							}
						}

						if ( ! bitStream.hasWrapped() ) {
							skipSync( true , false );
						}

					} while ( ! bitStream.hasWrapped() );
			}

			if ( DEBUG ) {
				System.out.println("Part count: "+partsList.size());
			}

			if ( partsList.isEmpty() ) {
				addPart( new UnknownPart( 0 , lengthInBytes*8 ) );
			}

			final int partLenInBits = partsList.stream().mapToInt( p->p.lengthInBits).sum();
			final int trackLenInBits = lengthInBytes*8;
			if ( partLenInBits != trackLenInBits ) {
				throw new RuntimeException("Internal error, track "+trackNo+" has "+trackLenInBits+" bits but combined parts have "+partLenInBits);
			}
		}

		private void addPart(TrackPart part)
		{
			if ( partsList.size() > 0 )
			{
				final TrackPart previousPart = partsList.get( partsList.size()-1 );
				final int previousEnd = ( previousPart.firstBit + previousPart.lengthInBits) % bitStream.size();
				if ( previousEnd != part.firstBit )
				{
					throw new IllegalArgumentException("Current part "+part+" does not line up with "+previousPart+" ,\n expected part to start @ "+previousEnd+" but started at "+part.firstBit);
				}
			}

			if ( DEBUG ) {
				System.out.println("Track "+trackNo+" | ADD: "+part);
			}

			if ( part.isHeader() )
			{
				final HeaderPart header = (HeaderPart) part;
				for ( TrackPart existing : partsList )
				{
					if ( existing.isHeader() && header.sector == existing.asHeader().sector  )
					{
						header.registerError(ErrorType.DUPLICATE_SECTOR_NUMBER, header.firstBit );
						break;
					}
				}
			}
			this.partsList.add( part );
		}

		private boolean skipSync(boolean addGap,boolean addUnknown) throws NoSuchElementException
		{
			int successiveOneBits = 0;

			boolean oneBitFound = false;
			final int searchStart = bitStream.currentBitOffset();
			int syncStart = searchStart;
			while ( ! bitStream.hasWrapped() )
			{
				final int offset = bitStream.currentBitOffset();
				final int bit = bitStream.readBit();
				if ( bit != 0 ) // => 1-bit
				{
					if ( ! oneBitFound ) {
						syncStart = offset;
					}
					oneBitFound = true;
					successiveOneBits++;
				}
				else // => 0-bit
				{
					// zero bit read
					if ( successiveOneBits >= 10 )
					{
						if ( syncStart != searchStart )
						{
							final int length = bitStream.distanceInBits( searchStart , syncStart );
							if ( addGap )
							{
								addPart( new GapPart( searchStart , length ) );
							} else if ( addUnknown) {
								addPart( new UnknownPart( searchStart , length ) );
							}
						}

						if ( ! firstSyncFound )
						{
							if ( DEBUG ) {
								System.out.println("First sync on track start at bit "+syncStart);
							}
							bitStream.mark(syncStart);
							firstSyncFound = true;
						}

						addPart( new SyncPart( syncStart , successiveOneBits ) );
						bitStream.rewind(1); // fix offset, we already read a 0-bit from the upcoming bytes
						return true;
					}
					oneBitFound = false;
					successiveOneBits = 0;
				}
			}

			final int length = bitStream.distanceInBits( searchStart , bitStream.currentBitOffset() );
			if ( length > 0 ) {
				addPart( new UnknownPart( searchStart , length ) );
			}
			return false;
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
		assertValidTrackNo(trackNo);
		final int offset = trackDataOffsetMap.getTrackOffset( trackNo );
		final int totalSizeInBytes = toBigEndian( data[offset] , data[offset+1] );
		if ( offset == 0 ) { // g64 file holds no data for this track
			return Optional.empty();
		}
		return Optional.of( new TrackData( trackNo , offset+2 , totalSizeInBytes ) );
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

	public SpeedZonesMap getSpeedZonesMap() {
		return speedZonesMap;
	}

	public void toD64(OutputStream out) throws IOException
	{
		try
		{
			for ( int i = 1 ; i <= 35 ; i++ ) // TODO: No handling of tracks > 35 here
			{
				final Optional<TrackData> trackData = getTrackData( i );
				out.write( trackData.get().getSectorData() );
			}
		} finally {
			out.close();
		}
	}

	protected static boolean isEvenTrackNo(float trackNo)
	{
		final int truncatedTrackNo = ( (int) (trackNo*10)) / 10 ;
		return trackNo == truncatedTrackNo;
	}

	public static int toG64(D64File file,OutputStream outStream) throws IOException
	{

		final BitOutputStream out = new BitOutputStream();
		final int maxTrackNum = file.getTrackCount();
		for ( int track = 1 ; track <= maxTrackNum ; track++)
		{
			final int sectors = D64File.getSectorsOnTrack( track );
			final byte[] image = new byte[ sectors * 256 ];
			for ( int sector = 0 ; sector < sectors ; sector++ )
			{
				file.getRawData( track , sector , image , sector*256 );
			}
		}

		/*
Bytes: $0000-0007: File signature "GCR-1541"
               0008: G64 version (presently only $00 defined)
               0009: Number of tracks in image (usually $54, decimal 84)
          000A-000B: Size of each stored track in bytes (usually  7928,  or $1EF8 in LO/HI format.
		 */
		out.writeBytes("GCR-1541"); // $0008: G64 version (presently only $00 defined)
		out.writeByte( 0x00 ); // $0009: Number of tracks in image (usually $54, decimal 84)
		out.writeWord( 7928 ); // $000A-000B: Size of each stored track in bytes (usually  7928,  or $1EF8 in LO/HI format.

		/*
   Track Range  Avg Size       Tail Gap        MNIB
                 (bytes)   (even/odd sectors)   Size
    -----------  --------  ------------------   ----
       1-17       ~7720           9/9           7692
      18-24       ~7165           9/19          7142
      25-30       ~6690           9/13          6666
      31-         ~6270           9/10          6250
		 */

		/*
Bytes: $000C-000F: Offset  to  stored  track  1.0  ($000002AC,  in  LO/HI
                     format, see below for more)
          0010-0013: Offset to stored track 1.5 ($00000000)
          0014-0017: Offset to stored track 2.0 ($000021A6)
             ...
          0154-0157: Offset to stored track 42.0 ($0004F8B6)
          0158-015B: Offset to stored track 42.5 ($00000000)
		 */

		// prepare offset table
		final int offsetTable[] = new int[84];
		int currentOffset = 0x2ac;
		for ( int trackNo = 1 ; trackNo <= maxTrackNum ; trackNo++ ) // half-tracks are not supported by D64
		{
			final int offset = (trackNo-1)*2*4; // 4 bytes per pointer, multiply by 2 to skip half-tracks
			offsetTable[ offset ] = currentOffset;

			final int bytesOnTrack;
			if ( trackNo <= 17 ) {
				bytesOnTrack = 7692;
			} else if ( trackNo <= 17 ) {
				bytesOnTrack = 7142;
			} else if ( trackNo <= 24 ) {
				bytesOnTrack = 6666;
			} else {
				bytesOnTrack = 6250;
			}
			currentOffset += bytesOnTrack+2; // (2 bytes are used to store the actual track length)
		}

		// write offset table
		for ( int i = 0 ; i < offsetTable.length ; i++ )
		{
			out.writeDWord( offsetTable[i] );
		}

		// prepare speed-zone table
/*
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
 */
		final int speedZoneTable[] = new int[84];
		for ( int trackNo = 1 ; trackNo <= maxTrackNum ; trackNo++ ) // half-tracks are not supported by D64
		{
			final int offset = (trackNo-1)*2*4; // 4 bytes per pointer, multiply by 2 to skip half-tracks
			offsetTable[ offset ] = currentOffset;

			final int speed;
			if ( trackNo <= 17 ) {
				speed = 0b11;
			} else if ( trackNo <= 17 ) {
				speed = 0b10;
			} else if ( trackNo <= 24 ) {
				speed = 0b01;
			} else {
				speed = 0b00;
			}
			speedZoneTable[ offset ] = speed;
		}

		// write speed-zone table
		for ( int i = 0 ; i < speedZoneTable.length ; i++ )
		{
			out.writeDWord( speedZoneTable[i] );
		}

		// write data for each track
		currentOffset = 0x2ac;
		for ( int trackNo = 1 ; trackNo <= maxTrackNum ; trackNo++ ) // half-tracks are not supported by D64
		{
			final int offset = (trackNo-1)*2*4; // 4 bytes per pointer, multiply by 2 to skip half-tracks
			final BitOutputStream tmp = new BitOutputStream();
			int bytesWritten = writeTrack( file , trackNo , tmp );

			out.writeWord( bytesWritten );
			out.copyFrom( tmp );
		}

		// write bit stream to output
		outStream.write( out.toByteArray() );
		return out.getBitsWritten();
	}

	// return: actual size in bytes
	private static int writeTrack(D64File file, int trackNo,BitOutputStream trackData)
	{
		final int maxSector = D64File.getSectorsOnTrack( trackNo );
		final byte[] sectorData= new byte[ D64File.BYTES_PER_SECTOR ];

		int start = trackData.getBitsWritten();
		for ( int sector = 0 ; sector < maxSector ; sector++ )
		{
			file.getRawData( trackNo , sector , sectorData, 0 );
			writeSector( trackNo , sector , sectorData , trackData );
		}
		int end = trackData.getBitsWritten();
		final int lenInBytes = (int) Math.ceil( (end-start)/8f);
		return lenInBytes;
	}

	private static void writeSync(BitOutputStream stream)
	{
		for ( int i = 0 ; i < 40 ; i++ )
		{
			stream.writeBit(1);
		}
	}

	private static void writeSector(int track,int sector,byte[] sectorData, BitOutputStream stream)
	{
		// ============= header ===========

		writeSync( stream ); // write header sync

		final int formatIdLo = 0x41;
		final int formatIdHi = 0x4d;

		gcrEncode( 0x08 , stream ); // header block id 0x08
		gcrEncode( xor( sector , track , formatIdLo , formatIdHi ) , stream ); // checksum
		gcrEncode( sector , stream );
		gcrEncode( track , stream );
		gcrEncode( formatIdLo, stream );
		gcrEncode( formatIdHi, stream );

		// write header gap
		for ( int len = 8 ; len >= 0 ; len--) {
			stream.writeByte( 0x55 );
		}

		// ============= data ===========
		writeSync( stream ); // write data sync

		gcrEncode( 0x07 , stream ); // data block id 0x08

		// write GCR-encoded data block
		int checksum = 0;
		for ( int i = 0 ; i < 256 ; i++ )
		{
			final int value = sectorData[i] & 0xff;
			checksum ^= value;
			gcrEncode( value , stream );
		}
		gcrEncode( checksum , stream );

		// inter-sector gap
		for ( int len = 12 ; len >= 0 ; len--) {
			stream.writeByte( 0x55 );
		}
	}
}