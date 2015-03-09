package de.codesourcery.j6502.emulator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.utils.CharsetConverter;
import de.codesourcery.j6502.utils.HexDump;

/**
 * Disk file format as described on http://unusedino.de/ec64/technical/formats/d64.html
 *
 * @author tobias.gierke@voipfuture.com
 */
public class D64File
{
	public static final boolean DEBUG_VERBOSE = true;

	private static final int IMAGE_SIZE_IN_BYTES = 174848;

	protected static final int MAX_DISKNAME_LEN = 16;
	protected static final int MAX_FILENAME_LEN = 16;

	private final byte[] data;

	private final BAM bam = new BAM();

	public static enum FileType {
		DEL,SEQ,PRG,USR,REL,UNKNOWN;
	}

	public final class DirectoryEntry {

		private final int offset;

		protected DirectoryEntry(int offset) {
			this.offset = offset;
		}

		public boolean isEmpty()
		{
			for ( int i = 0 ; i < 32 ; i++ ) {
				if ( data[offset+i] != 0 ) {
					return false;
				}
			}
			return true;
		}

		public FileType getFileType()
		{
			switch( data[ offset+2 ] & 0b111 )
			{
				case 0: return FileType.DEL; // 000 (0) - DEL
				case 1: return FileType.SEQ; // 001 (1) - SEQ
				case 2: return FileType.PRG; // 010 (2) - PRG
				case 3: return FileType.USR; // 011 (3) - USR
				case 4: return FileType.REL; // 100 (4) - REL
			}
			return FileType.UNKNOWN;
		}

		public InputStream createInputStream() {

			final int track = getFirstDataTrack();
			final int sector = getFirstDataSector();
			final int absoluteOffset = (getFirstSectorNoForTrack( track )+sector) * 256;

			if ( DEBUG_VERBOSE ) {
				System.out.println(this+": Creating input stream at "+track+"/"+sector+" for ");
			}
			return new InputStream()
			{
				private int currentSectorOffset = absoluteOffset;
				private int currentDataByteInSector = 2; //  because 0x00 = next data track and 0x01 = next data sector in this track

				private int bytesLeftInThisSector;

				private boolean eof;

				{ determineAvailableBytes(); }

				private void advanceToNextSector()
				{
					final int track = data[ currentSectorOffset ] & 0xff;
					if ( track == 0 )
					{
						return;
					}
					final int sector = data[ currentSectorOffset+1 ] & 0xff;

					if ( DEBUG_VERBOSE ) {
						System.out.println( DirectoryEntry.this+": Advancing to next sector at "+track+"/"+sector);
					}

					final int absoluteSector = getFirstSectorNoForTrack( track )+sector;
					currentSectorOffset = absoluteSector * 256;
					currentDataByteInSector =2;

					determineAvailableBytes();
				}

				private void determineAvailableBytes() {
					if ( data[ currentSectorOffset ] == 0 ) { // track == 0 => last data sector
						bytesLeftInThisSector = data[ currentSectorOffset+1 ] & 0xff;
					} else {
						bytesLeftInThisSector = 254; // 256 - 2 bytes for track/sector index
					}
					if ( DEBUG_VERBOSE ) {
						System.out.println(DirectoryEntry.this+": Current sector holds "+bytesLeftInThisSector+" bytes");
					}
				}

				@Override
				public int read() throws IOException
				{
					if ( bytesLeftInThisSector == 0 && ! eof ) {
						advanceToNextSector();
						eof |= ( bytesLeftInThisSector == 0 );
					}
					if ( eof )
					{
						if ( DEBUG_VERBOSE ) {
							System.out.println(DirectoryEntry.this+": End of file reached.");
						}
						return -1;
					}
					final int result = data[ currentDataByteInSector++ ] & 0xff;
					bytesLeftInThisSector--;
					return result;
				}
			};
		}

		public boolean hasFileType(FileType ft) {
			return ft.equals( getFileType() );
		}

		public int getNextDirEntryTrack() {
			return data[offset] & 0xff;
		}

		public int getNextDirEntrySector() {
			return data[offset+1] & 0xff;
		}

		public int getFirstDataTrack() {
			return data[offset+3] & 0xff;
		}

		public int getFirstDataSector() {
			return data[offset+4] & 0xff;
		}

		public boolean isClosed() {
			return (data[ offset+02 ] & 1<<7) != 0;
		}


		public boolean isLocked() {
			return (data[ offset+02 ] & 1<<6) != 0;
		}

		/**
		 * Returns file name as PET-ASCII , padded with $A0 and at most 16 characters long.
		 * @param name
		 */
		public void getFileName(byte[] name) {
			for ( int i = 0 ; i < 16 ; i++ )
			{
				name[i] = data[offset+i+5];
			}
		}

		/**
		 * Returns the file name as PET-ASCII , stripped of any trailing $A0 bytes.
		 * @param name
		 */
		public byte[] getTrimmedFileName()
		{
			final byte[] tmp = new byte[ MAX_FILENAME_LEN ];
			getFileName( tmp );
			int len = 0;
			for ( ; len < MAX_FILENAME_LEN ; len++ )
			{
				if ( (tmp[len] & 0xa0) == 0xa0) {
					break;
				}
			}
			byte[] result = new byte[ len ];
			System.arraycopy( tmp , 0 , result , 0 , len );
			return result;
		}

		public boolean matches(byte[] expectedPETASCII)
		{
			final byte[] actualPETASCII = getTrimmedFileName();
			if ( expectedPETASCII.length != actualPETASCII.length ) {
				return false;
			}
			for ( int i = 0 ; i < actualPETASCII.length ; i++ ) {
				if ( actualPETASCII[i] != expectedPETASCII[i] ) {
					return false;
				}
			}
			return true;
		}

		@Override
		public String toString() {
			return "\""+getFileNameAsASCII()+"\" , type: "+getFileType()+", size: "+getFileSizeInSectors()+" sectors, first data at "+getFirstDataTrack()+"/"+getFirstDataSector();
		}

		public String getFileNameAsASCII() {
			byte[] name = new byte[16];
			getFileName(name);
			StringBuffer buffer = new StringBuffer();
			for ( int i = 0 ; i < name.length ; i++ )
			{
				byte v = name[i];
				if ( (v & 0xff ) == 0xa0 || (v&0xff)== 0) {
					break;
				}
				buffer.append( CharsetConverter.petToASCII( v ) );
			}
			return buffer.toString();
		}

		public int getFirstSideSectorTrack() {
			return data[offset+0x15] & 0xff;
		}

		public int getFirstSideSector() {
			return data[offset+0x16] & 0xff;
		}

		public int getRelFileRecordLength() {
			return data[offset+0x17] & 0xff;
		}

		/*
          1E-1F: File size in sectors, low/high byte  order  ($1E+$1F*256).
                 The approx. filesize in bytes is <= #sectors * 254
		 */
		public int getFileSizeInSectors() {

			int low = data[offset+0x1e] & 0xff;
			int hi = data[offset+0x1f] & 0xff;
			return hi<<8 | low;
		}
	}

	/*
  Bytes: $00-1F: First directory entry
          00-01: Track/Sector location of next directory sector ($00 $00 if
                 not the first entry in the sector)
             02: File type.
                 Typical values for this location are:
                   $00 - Scratched (deleted file entry)
                    80 - DEL
                    81 - SEQ
                    82 - PRG
                    83 - USR
                    84 - REL
                 Bit 0-3: The actual filetype
                          000 (0) - DEL
                          001 (1) - SEQ
                          010 (2) - PRG
                          011 (3) - USR
                          100 (4) - REL
                          Values 5-15 are illegal, but if used will produce
                          very strange results. The 1541 is inconsistent in
                          how it treats these bits. Some routines use all 4
                          bits, others ignore bit 3,  resulting  in  values
                          from 0-7.
                 Bit   4: Not used
                 Bit   5: Used only during SAVE-@ replacement
                 Bit   6: Locked flag (Set produces ">" locked files)
                 Bit   7: Closed flag  (Not  set  produces  "*", or "splat"
                          files)
          03-04: Track/sector location of first sector of file
          05-14: 16 character filename (in PETASCII, padded with $A0)
          15-16: Track/Sector location of first side-sector block (REL file
                 only)
             17: REL file record length (REL file only, max. value 254)
          18-1D: Unused (except with GEOS disks)
          1E-1F: File size in sectors, low/high byte  order  ($1E+$1F*256).
                 The approx. filesize in bytes is <= #sectors * 254
          20-3F: Second dir entry. From now on the first two bytes of  each
                 entry in this sector  should  be  $00  $00,  as  they  are
                 unused.
          40-5F: Third dir entry
          60-7F: Fourth dir entry
          80-9F: Fifth dir entry
          A0-BF: Sixth dir entry
          C0-DF: Seventh dir entry
          E0-FF: Eighth dir entry
	 */

	public D64File(InputStream in) throws IOException
	{
		Validate.notNull(in, "in must not be NULL");

		final ByteArrayOutputStream tmp = new ByteArrayOutputStream(IMAGE_SIZE_IN_BYTES);
		final byte[] buffer = new byte[1024];
		try {
			int len;
			while ( ( len = in.read( buffer ) ) > 0 ) {
				tmp.write( buffer , 0 , len );
			}
		} finally {
			in.close();
		}
		this.data = tmp.toByteArray();
	}

	public D64File(File file) throws IOException
	{
		this( new FileInputStream(file ) );
	}

	public D64File(String imageFileOnClasspath) throws IOException
	{
		this( D64File.class.getResourceAsStream("/disks/"+imageFileOnClasspath) );
	}

	public static void main(String[] args) throws IOException
	{
		final D64File file = new D64File( "test.d64");

		final List<DirectoryEntry> directory = file.getDirectory();
		System.out.println("Disk contains "+directory.size()+" directory entries");
		directory.forEach( dir ->
		{
			System.out.println("==== File: "+dir+" ====");

			InputStream in = dir.createInputStream();
			int bytesRead = 0;
			try {
				while ( in.read() != -1 ) {
					bytesRead++;
				}
			} catch (Exception e) {
				throw new RuntimeException(e);
			} finally {
				IOUtils.closeQuietly( in );
			}
			System.out.println("Bytes read: "+bytesRead+" ( = "+bytesRead/254.0f+" sectors)");
		});

		InputStream inputStream = file.createDirectoryInputStream();

		byte[] buffer = new byte[2048];
		int bytesRead = inputStream.read(buffer);

		final HexDump dump = new HexDump();
		dump.setBytesPerLine( 32 );
		dump.setPrintAddress( true );
		System.out.println( dump.dump((short) 0, buffer, bytesRead, bytesRead) );
	}

	public Optional<DirectoryEntry> getDirectoryEntry(byte[] fileNameInPETSCII)
	{
		return getDirectory().stream().filter( entry -> entry.matches( fileNameInPETSCII ) ).findFirst();
	}

	public List<DirectoryEntry> getDirectory() {

		final int firstDirSector = getFirstSectorNoForTrack( 18 )+1; // +1 because the first sector on track 18 holds the BAM (block allocation map)

		final List<DirectoryEntry> result = new ArrayList<>();
		int offset = firstDirSector*256;
		DirectoryEntry entry = new DirectoryEntry(offset);

		do {
			if ( ! entry.isEmpty() ) {
				result.add( entry );
			}
			for ( int offsetInSector = 0x20 ; offsetInSector < 256 ; offsetInSector += 0x20 ) {
				DirectoryEntry tmp = new DirectoryEntry( offset + offsetInSector );
				if ( tmp.isEmpty() ) {
					break;
				}
				result.add( tmp );
			}
			final int track = entry.getNextDirEntryTrack();
			if ( track == 0 ) {
				break;
			}
			final int sectorOnTrack = entry.getNextDirEntrySector();
			final int totalSector = getFirstSectorNoForTrack( track )+sectorOnTrack;
			offset = totalSector * 256;
			entry = new DirectoryEntry(offset);
		} while ( entry.getNextDirEntryTrack() != 0 );
		return result;
	}

	public void writeSector(byte[] input,int sectorNo) {
		int offset = sectorNo*256;
		for ( int i = 0 ; i < 256 ; i++ ) {
			this.data[offset+i] = input[i];
		}
	}

	public void readSector(byte[] buffer, int sectorNo) {

		int offset = sectorNo*256;
		for ( int i = 0 ; i < 256 ; i++ ) {
			buffer[i]=this.data[offset+i];
		}
	}

	public int getFirstSectorNoForTrack(int trackNo) {

		switch( trackNo )
		{
			case 1: return   0;
			case 2: return  21;
			case 3: return  42;
			case 4: return  63;
			case 5: return  84;
			case 6: return 105;
			case 7: return 126;
			case 8:	return 147;
			case 9: return 168;
			case 10:return 189;
			case 11:return 210;
			case 12:return 231;
			case 13:return 252;
			case 14:return 273;
			case 15:return 294;
			case 16:return 315;
			case 17:return 336;
			case 18:return 357;
			case 19:return 376;
			case 20:return 395;
			case 21:return 414;
			case 22:return 433;
			case 23:return 452;
			case 24:return 471;
			case 25:return 490;
			case 26:return 508;
			case 27:return 526;
			case 28:return 544;
			case 29:return 562;
			case 30:return 580;
			case 31:return 598;
			case 32:return 615;
			case 33:return 632;
			case 34:return 649;
			case 35:return 666;
			case 36:return 683;
			case 37:return 700;
			case 38:return 717;
			case 39:return 734;
			case 40:return 751;
		}
		throw new IllegalArgumentException("Invalid track no. "+trackNo);
	}

	public int getTrackForSector(int sector)
	{
		if ( sector <  21) return 1;
		if ( sector <  42) return 2;
		if ( sector <  63) return 3;
		if ( sector <  84) return 4;
		if ( sector < 105) return 5;
		if ( sector < 126) return 6;
		if ( sector < 147) return 7;
		if ( sector < 168) return 8;
		if ( sector < 189) return 9;
		if ( sector < 210) return 10;
		if ( sector < 231) return 11;
		if ( sector < 252) return 12;
		if ( sector < 273) return 13;
		if ( sector < 294) return 14;
		if ( sector < 315) return 15;
		if ( sector < 336) return 16;
		if ( sector < 357) return 17;
		if ( sector < 376) return 18;
		if ( sector < 395) return 19;
		if ( sector < 414) return 20;
		if ( sector < 433) return 21;
		if ( sector < 452) return 22;
		if ( sector < 471) return 23;
		if ( sector < 490) return 24;
		if ( sector < 508) return 25;
		if ( sector < 526) return 26;
		if ( sector < 544) return 27;
		if ( sector < 562) return 28;
		if ( sector < 580) return 29;
		if ( sector < 598) return 30;
		if ( sector < 615) return 31;
		if ( sector < 632) return 32;
		if ( sector < 649) return 33;
		if ( sector < 666) return 34;
		if ( sector < 683) return 35;
		if ( sector < 700) return 36;
		if ( sector < 717) return 37;
		if ( sector < 734) return 38;
		if ( sector < 751) return 39;
		return 40;
	}

	public int getSectorCount() {
		return 683;
	}

	public int getTrackCount() {
		return 35;
	}

	public InputStream createDirectoryInputStream()
	{
		/*
		 * Formats the directory listing as a fake BASIC program and
		 * sends this to the computer.
		 *
         * 0 "TEST DISK       " 23 2A
         * 20   "FOO"               PRG
         * 3    "BAR"               PRG
         * 641 BLOCKS FREE.
         *
         * In this example, “TEST DISK” is the disk name, “23″ the disk ID and “2A” the filesystem format/version
         * (always 2A on 1540/1541/1551/1570/1571 – but this was only a redundant copy of the version information
         * which was never read and could be changed).
		 *
		 * Syntax:
		 *
		 * 0801  0E 08    - next line starts at 0x080E
		 * 0803  0A 00    - line number 10
		 * 0805  99       - token for PRINT
		 * 0806  "HELLO!" - ASCII text of line
		 * 080D  00       - end of line
		 * 080E  00 00    - end of program
		 *
		 * The example directory listing from above would be encoded by the floppy like this:
		 *
		 * 0801  21 08    - next line starts at 0x0821
		 * 0803  00 00    - line number 0
		 * 0805  '"TEST DISK       " 23 2A '
		 * 0820  00       - end of line
		 * 0821  21 08    - next line starts at 0x0821
		 * 0823  14 00    - line number 20
		 * 0825  '  "FOO"               PRG '
		 * 0840  00       - end of line
		 * [...]
		 */

		// determine how much padding we need to add to each line so that all file names line up nicely
		final int maxSectorCount = getDirectory().stream().mapToInt( d -> d.getFileSizeInSectors() ).max().orElse( 0 );
		final int maxSectorCountStringLen = Integer.toString( maxSectorCount ).length();

		// header line
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write( 0x01 ); // lo , load address: 0x401
		out.write( 0x04 ); // hi

		// 0x101 => dummy ptr to next line in basic listing (will be re-calculated by BASIC interpreter anyway so actual value doesn't matter)
		out.write( 0x01 ); // lo
		out.write( 0x01 ); // hi

		// drive (for simplicities sake I always return drive #0)
		out.write( 0x00 ); // lo
		out.write( 0x00 ); // hi

		// quote
		out.write( 0x22 );

        // write disk name padded to 16 characters
		final byte[] diskName = bam.getDiskName();
		for ( int len = 0 ; len < diskName.length ; len++ ) {
			out.write( diskName[len] );
		}
		// write padding bytes
		for ( int delta = 32 - 7 - diskName.length - 8 ; delta > 0 ; delta-- )
		{
			out.write( 0x20 );
		}
		// quote
		out.write( 0x22 );

		try
		{
			// whitespace
			out.write( 0x20 );
			// two byte disk ID
			out.write( bam.getDiskID() );
			// whitespace
			out.write( 0x20 );
			// two byte DOS version
			out.write( bam.getDOSType() );
		}
		catch (IOException e)
		{
			throw new RuntimeException("Can never happen...");
		}
		// terminate line
		out.write( 0x00 );

		for ( DirectoryEntry entry : getDirectory() )
		{
			// 0x101 => dummy ptr to next line in basic listing (will be re-calculated by BASIC interpreter anyway so value doesn't matter)
			int lineWidth = 0;
			out.write( 0x01 ); // lo
			out.write( 0x01 ); // hi
			lineWidth += 2;

			final int sectors = entry.getFileSizeInSectors();
			// number of sectors (here always zero because this is the disk name line)
			out.write( sectors & 0xff ); // lo
			out.write( (sectors >> 8 ) & 0xff ); // hi
			lineWidth += 2;

			// write padding whitespace to nicely line up filenames
			byte[] fileName = entry.getTrimmedFileName();
			for ( int delta = maxSectorCountStringLen - fileName.length ; delta > 0 ; delta-- ) {
				out.write( 0x20 );
				lineWidth++;
			}

			// quote
			out.write( 0x22 );
			lineWidth++;

			// write file name
			for ( int i = 0 ; i < fileName.length ; i++ ) {
				out.write( fileName[i] );
				lineWidth++;
			}

			// quote
			out.write( 0x22 );
			lineWidth++;

			// every line in the basic listing needs to be exactly 32 bytes long. Four bytes are already spent on line ptrs + sector counts, making
			// the 28 remaining bytes look like this:
			// <SECTOR COUNT><ALIGNING>"FILENAME       "      PRG<zero byte>
			// align program type
			for ( int remaining = 32 - lineWidth - 4; remaining > 0 ; remaining--) { // "-4" , because of line ptrs + sector count, "-3" because of program type string, "-1" because of terminating zero byte
				out.write(0x20);
			}

			// write file type
			switch( entry.getFileType() ) {
				case DEL:
					out.write( (byte) 'D'); out.write( (byte) 'E'); out.write( (byte) 'L');
					break;
				case PRG:
					out.write( (byte) 'P'); out.write( (byte) 'R'); out.write( (byte) 'G');
					break;
				case REL:
					out.write( (byte) 'R'); out.write( (byte) 'E'); out.write( (byte) 'L');
					break;
				case SEQ:
					out.write( (byte) 'S'); out.write( (byte) 'E'); out.write( (byte) 'Q');
					break;
				case USR:
					out.write( (byte) 'U'); out.write( (byte) 'S'); out.write( (byte) 'R');
					break;
				default:
					out.write( (byte) '?'); out.write( (byte) '?'); out.write( (byte) '?');
			}

			// terminate line
			out.write( 0x00 );

		}
		return new ByteArrayInputStream( out.toByteArray() );
	}

	protected final class BAM
	{
		private final int offset = getFirstSectorNoForTrack( 18 )*256;

		/*
  Bytes:$00-01: Track/Sector location of the first directory sector (should
                be set to 18/1 but it doesn't matter, and don't trust  what
                is there, always go to 18/1 for first directory entry)
            02: Disk DOS version type (see note below)
                  $41 ("A")
            03: Unused
         04-8F: BAM entries for each track, in groups  of  four  bytes  per
                track, starting on track 1 (see below for more details)
         90-9F: Disk Name (padded with $A0)
         A0-A1: Filled with $A0
         A2-A3: Disk ID
            A4: Usually $A0
         A5-A6: DOS type, usually "2A"
         A7-AA: Filled with $A0
         AB-FF: Normally unused ($00), except for 40 track extended format,
                see the following two entries:
         AC-BF: DOLPHIN DOS track 36-40 BAM entries (only for 40 track)
         C0-D3: SPEED DOS track 36-40 BAM entries (only for 40 track)
		 */
		public byte getDOSVersion() {
			return data[ offset + 0x02 ];
		}

		public byte[] getDiskID()
		{
			return new byte[] { data[ offset + 0x02 ], data[ offset + 0x03 ] };
		}

		public byte[] getDOSType()
		{
			return new byte[] { data[ offset + 0xa5 ], data[ offset + 0xa6 ] };
		}

		public byte[] getDiskName() {
			int len = 0;
			int start = offset+0x90;
			for ( ; len < 16 && ( data[ start+ len ] & 0xff) != 0xa0 ; len++ ) {
				len++;
			}
			byte[] result = new byte[len];
			System.arraycopy( data , offset+0x90 , result , 0 , len );
			return result;
		}
	}
}