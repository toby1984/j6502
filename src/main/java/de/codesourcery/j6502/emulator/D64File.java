package de.codesourcery.j6502.emulator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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
	public static final boolean DEBUG_VERBOSE = false;

	protected static final int BYTES_PER_SECTOR = 256;

	protected static final int BAM_OFFSET = getFirstSectorNoForTrack( 18 ) * BYTES_PER_SECTOR;

	private static final int IMAGE_SIZE_IN_BYTES = 174848;

	protected static final int DIR_ENTRY_SIZE = 32;

	protected static final int MAX_DISKNAME_LEN = 16;
	protected static final int MAX_FILENAME_LEN = 16;

	protected static final int DIR_LOAD_ADR = 0x801;

	private final String source;
	private final byte[] data;

	private final BAM bam = new BAM();

	public static enum FileType {
		DEL,SEQ,PRG,USR,REL,UNKNOWN;
	}

	public void write(OutputStream out) throws IOException 
	{
		out.write( data );
	}

	public final class DirectoryEntry 
	{
		public final int trackNo; // track that holds this directory entry
		public final int sectorOnTrack; // sector on track that holds this directory entry
		public final int dirEntryIndexInSector; // index of this directory within the sector ( 0...7) 
		private final int absEntryOffset;

		protected DirectoryEntry(int absEntryOffset,int dirEntryIndexInSector,int trackNo,int sectorOnTrack) 
		{
			if ( dirEntryIndexInSector < 0 || dirEntryIndexInSector > 7 ) {
				throw new IllegalArgumentException("Invalid directory entry index "+dirEntryIndexInSector);
			}
			// NOTE: This method must NEVER write to the data[] buffer , otherwise 
			// allocDirectoryEntry(boolean) with simulate == true would corrupt the disk 
			this.absEntryOffset = absEntryOffset;
			this.dirEntryIndexInSector = dirEntryIndexInSector;
			this.trackNo = trackNo;
			this.sectorOnTrack = sectorOnTrack;
		}

		protected void init() 
		{
			for ( int i = 0 ; i < DIR_ENTRY_SIZE ; i++ ) 
			{
				data[absEntryOffset+i] = 0;
			}
			setFileName( new byte[0] );
		}

		public boolean isEmpty()
		{
			for ( int i = 0 ; i < DIR_ENTRY_SIZE ; i++ ) {
				if ( data[absEntryOffset+i] != 0 ) {
					return false;
				}
			}
			return true;
		}

		protected void setFileType(FileType type) 
		{
			data[ absEntryOffset ] &= ~0b111; // clear lowest 3 bits
			switch ( type ) 
			{
				case DEL: break;
				case PRG: data[ absEntryOffset ] |= 0b010; break;
				case REL: data[ absEntryOffset ] |= 0b100; break;
				case SEQ: data[ absEntryOffset ] |= 0b001; break;
				case UNKNOWN: 
					throw new RuntimeException("cannot set directory entry to UNKNOWN");
				case USR: data[ absEntryOffset ] |= 0b011; break;
				default:
					throw new RuntimeException("Internal error,unhandled file type "+type);
			}
		}

		public FileType getFileType()
		{
			switch( data[ absEntryOffset ] & 0b111 )
			{
				case 0: return FileType.DEL; // 000 (0) - DEL
				case 1: return FileType.SEQ; // 001 (1) - SEQ
				case 2: return FileType.PRG; // 010 (2) - PRG
				case 3: return FileType.USR; // 011 (3) - USR
				case 4: return FileType.REL; // 100 (4) - REL
			}
			return FileType.UNKNOWN;
		}

		public InputStream createInputStream()
		{
			final int track = getFirstDataTrack();
			final int sector = getFirstDataSector();
			final int absoluteOffset = (getFirstSectorNoForTrack( track )+sector) * BYTES_PER_SECTOR;

			if ( DEBUG_VERBOSE ) {
				System.out.println(this+": Creating input stream at "+track+"/"+sector+" for '"+getFileNameAsASCII()+"' (offset: 0x"+Long.toHexString( absoluteOffset )+")");
			}

			if ( getFileType() != FileType.PRG && getFileType() != FileType.DEL ) { // I have a Summer Games .d64 image that loads DEL files...
				throw new RuntimeException("Sorry, handling file type "+getFileType()+" is currently not implemented");
			}

			return new InputStream()
			{
				private int globalBufferIndex = absoluteOffset;
				private int currentGlobalDataByteOffset = globalBufferIndex+2; //  because 0x00 = next data track and 0x01 = next data sector in this track

				private int bytesLeftInThisSector;

				private boolean eof;

				{ determineAvailableBytes(); }

				private void advanceToNextSector()
				{
					final int track = data[ globalBufferIndex ] & 0xff;
					if ( track == 0 )
					{
						return;
					}
					final int sector = data[ globalBufferIndex+1 ] & 0xff;

					if ( DEBUG_VERBOSE ) {
						System.out.println( DirectoryEntry.this+": Advancing to next sector at "+track+"/"+sector);
					}

					globalBufferIndex = ( getFirstSectorNoForTrack( track ) +sector ) * BYTES_PER_SECTOR;
					currentGlobalDataByteOffset = globalBufferIndex + 2; // +2 because first two bytes are link ptr

					determineAvailableBytes();
				}

				private void determineAvailableBytes()
				{
					if ( data[ globalBufferIndex ] == 0 ) { // track == 0 => last data sector
						bytesLeftInThisSector = data[ globalBufferIndex+1 ] & 0xff;
					} 
					else 
					{
						bytesLeftInThisSector = BYTES_PER_SECTOR - 2; // 2 bytes for track/sector index
					}
					if ( DEBUG_VERBOSE ) {
						System.out.println(DirectoryEntry.this+": Current sector holds "+bytesLeftInThisSector+" bytes");
					}
				}

				@Override
				public int read() throws IOException
				{
					if ( bytesLeftInThisSector == 0 && ! eof )
					{
						if ( DEBUG_VERBOSE ) {
							System.out.println("No more bytes in this sector,moving on");
						}
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
					final int result = data[ currentGlobalDataByteOffset++ ] & 0xff;
					bytesLeftInThisSector--;
					return result;
				}
			};
		}

		public boolean hasFileType(FileType ft)
		{
			return ft.equals( getFileType() );
		}

		public int getFirstDataTrack() {
			return data[absEntryOffset+1] & 0xff;
		}

		protected void setFirstDataTrack(int trackNo) {
			data[absEntryOffset+1] = (byte) trackNo;
		}

		public int getFirstDataSector() {
			return data[absEntryOffset+2] & 0xff;
		}

		protected void setFirstDataSector(int sector) {
			data[absEntryOffset+2] = (byte) sector;
		}

		public boolean isClosed() {
			return (data[ absEntryOffset ] & 1<<7) != 0;
		}

		protected void setClosed(boolean yesNo) {
			if ( yesNo ) {
				data[ absEntryOffset ] |= 1<<7;
			} else {
				data[ absEntryOffset ] &= ~(1<<7);
			}
		}

		public boolean isLocked() {
			return (data[ absEntryOffset ] & 1<<6) != 0;
		}

		protected void setLocked(boolean yesNo) 
		{
			if ( yesNo ) {
				data[ absEntryOffset ] |= 1<<6;
			} else {
				data[ absEntryOffset ] &= ~(1<<6);
			}
		}

		protected void setFileName(byte[] petASCII) 
		{
			if ( petASCII.length > 16 ) {
				throw new IllegalArgumentException("Filename too long");
			}
			for ( int i = 0 ; i < 16 ; i++ )
			{
				data[absEntryOffset+i+3] = (byte) 0xa0;
			}

			for ( int i = 0 ; i < petASCII.length; i++ )
			{
				data[absEntryOffset+i+3] = petASCII[i];
			}
		}

		/**
		 * Returns file name as PET-ASCII , padded with $A0 and at most 16 characters long.
		 * @param name
		 */
		public void getFileName(byte[] name) {
			for ( int i = 0 ; i < 16 ; i++ )
			{
				name[i] = data[absEntryOffset+i+3];
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
		public String toString() 
		{
			String dataSector="";
			try {
				final int dataOffset = BYTES_PER_SECTOR*( getFirstSectorNoForTrack( getFirstDataTrack() ) + getFirstDataSector() );
				dataSector = "first data at "+getFirstDataTrack()+"/"+getFirstDataSector()+" @ "+dataOffset;
			} catch(IllegalArgumentException e) {
				dataSector = "first data at "+getFirstDataTrack()+"/"+getFirstDataSector()+" , INVALID !!!";
			}
			return "\""+getFileNameAsASCII()+"\" , dir entry idx. "+this.dirEntryIndexInSector+" @ "+this.absEntryOffset+" ("+trackNo+"/"+sectorOnTrack+") ,"
			+ "type: "+getFileType()+", size: "+getFileSizeInSectors()+" sectors, "+dataSector;
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
			return data[absEntryOffset+0x13] & 0xff;
		}

		protected void setFirstSideSectorTrack(int trackNo) {
			data[absEntryOffset+0x13] = (byte) trackNo;
		}

		public int getFirstSideSector() {
			return data[absEntryOffset+0x14] & 0xff;
		}

		protected void setFirstSideSector(int sectorNo) {
			data[absEntryOffset+0x14] = (byte) sectorNo;
		}		

		public int getRelFileRecordLength() {
			return data[absEntryOffset+0x15] & 0xff;
		}

		protected void setRelFileRecordLength(int length) {
			data[absEntryOffset+0x15]=(byte) length;
		}		

		/*
          1E-1F: File size in sectors, low/high byte  order  ($1E+$1F*SECTOR_SIZE).
                 The approx. filesize in bytes is <= #sectors * 254
		 */
		public int getFileSizeInSectors() {

			final int low = data[absEntryOffset+0x1c] & 0xff;
			final int hi = data[absEntryOffset+0x1d] & 0xff;
			return hi<<8 | low;
		}

		protected void setFileSizeInSectors(int count) 
		{
			final int lo = count & 0xff;
			final int hi = (count>>8) & 0xff;

			data[absEntryOffset+0x1c] = (byte) lo;
			data[absEntryOffset+0x1d] = (byte) hi;
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
          1E-1F: File size in sectors, low/high byte  order  ($1E+$1F*SECTOR_SIZE).
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

	public D64File(InputStream in,String source) throws IOException
	{
		Validate.notNull(in, "in must not be NULL");
		Validate.notNull(source, "source must not be NULL");
		this.source = source;
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
		this( new FileInputStream(file ) , "file:"+file.getAbsolutePath() );
	}

	public D64File(String imageFileOnClasspath) throws IOException
	{
		this( openDiskFileFromClassPath(imageFileOnClasspath ) , "classpath:"+imageFileOnClasspath );
	}

	public String getSource() {
		return source;
	}

	private static InputStream openDiskFileFromClassPath(String imageFileOnClasspath) throws FileNotFoundException
	{
		final String path = "/disks/"+imageFileOnClasspath;
		final InputStream  stream = D64File.class.getResourceAsStream( path );
		if ( stream == null ) {
			throw new FileNotFoundException("Failed to load file from classpath:"+path);
		}
		return stream;
	}

	public static void main(String[] args) throws IOException
	{
		DirectoryEntry toHexDump = null;

		System.out.println("BAM offset: "+getFirstSectorNoForTrack( 18 ) * BYTES_PER_SECTOR);

		//		final D64File file = new D64File( "test.d64");
		final D64File file = new D64File( "test.d64");

		System.out.println("Disk name: "+file.getDiskNameInASCII());
		
		InputStream stream = file.createDirectoryInputStream();
		while( stream.read() != -1 ) {};
		
//		toHexDump = file.savePRG( "test2.txt" , (short) 0x0801 , new ByteArrayInputStream( "blah".getBytes() ) );

		final List<DirectoryEntry> directory = file.getDirectory();

		System.out.println("Disk contains "+directory.size()+" directory entries");
		System.out.println("Blocks free: "+file.getFreeSectorCount());

		directory.forEach( dir ->
		{
			System.out.println("==== File: "+dir+" ====");
		});

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

		if ( toHexDump != null ) 
		{
			InputStream inputStream = toHexDump.createInputStream();

			byte[] buffer = new byte[2048];
			final int bytesRead = inputStream.read(buffer);
			System.out.println("Read "+bytesRead+" bytes to hex-dump");

			final HexDump dump = new HexDump();
			dump.setBytesPerLine( 32 );
			dump.setPrintAddress( true );
			System.out.println( dump.dump((short) 0, buffer, bytesRead, bytesRead) );
		}
	}

	public Optional<DirectoryEntry> getDirectoryEntry(byte[] fileNameInPETSCII)
	{
		return getDirectory().stream().filter( entry -> entry.matches( fileNameInPETSCII ) ).findFirst();
	}

	public List<DirectoryEntry> getDirectory() {

		int trackNo = 18;
		int sectorNo = 1; // => 1 because the first sector on track 18 holds the BAM (block allocation map)

		final List<DirectoryEntry> result = new ArrayList<>();
		while(true) 
		{
			final int absSector = getFirstSectorNoForTrack( trackNo )+sectorNo;
			final int offset = absSector*BYTES_PER_SECTOR;

			for ( int offsetInSector = 2 , entryIndexInSector = 0 ; entryIndexInSector < 8 ; entryIndexInSector++ ) 
			{
				final DirectoryEntry tmp=  new DirectoryEntry( offset + offsetInSector , entryIndexInSector , trackNo , sectorNo );
				if ( tmp.isEmpty() ) {
					break;
				}
				result.add( tmp );
				offsetInSector += DIR_ENTRY_SIZE;
			}

			trackNo = data[ offset ] & 0xff;
			sectorNo = data[ offset+1 ] & 0xff;
			if ( trackNo == 0) {
				break;
			}
		}
		return result;
	}

	protected static final class DirEntryCreationResult 
	{
		public final DirectoryEntry dirEntry;
		public final boolean allocatedNewDirSector;

		public DirEntryCreationResult(DirectoryEntry dirEntry, boolean allocatedNewDirSector) 
		{
			this.dirEntry = dirEntry;
			this.allocatedNewDirSector = allocatedNewDirSector;
		}
	}

	protected DirEntryCreationResult allocDirectoryEntry(boolean simulate) throws IOException 
	{
		int trackNo = 18;
		int sectorNo = 1; // => 1 because the first sector on track 18 holds the BAM (block allocation map)

		while(true) 
		{
			final int absSector = getFirstSectorNoForTrack( trackNo )+sectorNo;
			final int offset = absSector*BYTES_PER_SECTOR;

			for ( int offsetInSector = 2 , entryIndexInSector = 0 ; offsetInSector < BYTES_PER_SECTOR ; offsetInSector += DIR_ENTRY_SIZE , entryIndexInSector++ ) 
			{
				final DirectoryEntry tmp = new DirectoryEntry( offset + offsetInSector , entryIndexInSector , trackNo , sectorNo );
				if ( tmp.isEmpty() ) 
				{
					// sector has unused dir entry , no need to allocate a new sector just to hold the new entry
					if ( ! simulate ) 
					{
						tmp.init();
					}
					return new DirEntryCreationResult(tmp,false);
				}
			}
			final int nextTrackNo = data[ offset ] & 0xff;
			final int nextSecorNo = data[ offset+1 ] & 0xff;
			if ( nextTrackNo == 0  ) 
			{
				// need to allocate new sector 
				for ( BAMEntry e : getAllocationMap() ) 
				{
					if ( e.getFreeSectorCountFromBitmap() > 0 ) 
					{
						final int relSector = e.getOffsetOfFirstFreeSectorOnThisTrack();
						if ( relSector == -1 ) {
							throw new RuntimeException("Internal error");
						}

						final int byteOffset = getFirstSectorNoForTrack( e.trackNo ) + relSector;
						final DirectoryEntry newEntry = new DirectoryEntry( byteOffset+2 , 0 , e.trackNo , relSector );

						if ( ! simulate ) 
						{
							e.markAllocated( relSector );

							// create link to new directory sector
							data[ offset ] = (byte) e.trackNo;
							data[ offset+1 ] = (byte) relSector;

							// mark sector as being the last in a chain of directory sectors
							data[ byteOffset    ] = 0 ;
							data[ byteOffset +1 ] = 0 ;

							newEntry.init();
						}
						return new DirEntryCreationResult(newEntry,true);
					}
				}
				throw new IOException("Failed to allocate new sector for directory entry");
			}
			trackNo = nextTrackNo;
			sectorNo = nextSecorNo;
		}
	}

	public int getSectorsOnTrack(int trackNo) 
	{
		if ( trackNo < 1 ) {
			throw new IllegalArgumentException("Invalid track no. "+trackNo);
		}

		/*
 1 - 17 	21 	16M/4/(13+0) = 307 692
18 - 24 	19 	16M/4/(13+1) = 285 714
25 - 30 	18 	16M/4/(13+2) = 266 667
31 - 35 	17 	16M/4/(13+3) = 250 000
36 - 42 	17 	16M/4/(13+3) = 250 000		 
		 */
		if ( trackNo <= 17 ) {
			return 21;
		}
		if ( trackNo <= 24 ) {
			return 19;
		}
		if ( trackNo <= 30 ) {
			return 18;
		}		
		return 17;
	}

	public static int getFirstSectorNoForTrack(int trackNo)
	{
		switch(trackNo) {
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
			default:
				throw new IllegalArgumentException("Invalid track no. "+trackNo);
		}
	}

	public int getSectorCount() {
		return 683;
	}

	public int getTrackCount() {
		return 35;
	}

	public int getFreeSectorCount() 
	{
		int totalFree = getBAM().getAllocationMap().stream().mapToInt( e -> e.getFreeSectorsCount() ).sum();
		return totalFree - getBAM().getAllocationMap( 18 ).getFreeSectorsCount();
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
		final List<byte[]> lines = new ArrayList<>();
		for ( DirectoryEntry entry : getDirectory() )
		{
			final byte[] line = toDirectoryLine(entry);
//			if ( line.length != 32 ) {
//				throw new RuntimeException("Internal error, directory line has "+line.length+" bytes?");
//			}
			lines.add( line );
		}

		final byte[] blocksFree = new byte[32];
		final byte[] blocksFreePET = CharsetConverter.asciiToPET( "blocks free.".toCharArray() );
		blocksFree[0]=1;
		blocksFree[1]=1;

		final int freeSectorsCount = getFreeSectorCount();
		blocksFree[2]= (byte) (freeSectorsCount & 0xff);
		blocksFree[3]= (byte) ( ( freeSectorsCount >> 8 ) & 0xff );
		for ( int i=4,j=0 ; j < blocksFreePET.length ; i++,j++) {
			blocksFree[i] = blocksFreePET[j];
		}
		lines.add( blocksFree );

		// header line
		final byte[] header = createHeaderLine();
//		if( header.length != 32 ) {
//			throw new RuntimeException("Internal error,unexpected header line length: "+header.length);
//		}

		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		try
		{
			out.write(header);
			for ( byte[] line : lines ) {
				out.write(line);
			}
		} catch (IOException e) {
			e.printStackTrace();
		}

		final byte[] buffer = out.toByteArray();

		if ( lines.isEmpty() ) {
			buffer[0] = 0;
			buffer[1] = 0;
		}

		System.out.println("Directory input stream has "+buffer.length+" bytes.");
		return new ByteArrayInputStream( buffer );
	}

	private byte[] createHeaderLine() 
	{
		final ByteArrayOutputStream out = new ByteArrayOutputStream();
		out.write( DIR_LOAD_ADR & 0xff ); // lo , load address
		out.write( (DIR_LOAD_ADR >> 8) & 0xff ); // hi, load address

		// ptr to next line in basic listing
		out.write( 01 );
		out.write( 01 );

		// drive (for simplicities sake I always return drive #0)
		out.write( 0x00 ); // lo
		out.write( 0x00 ); // hi

		// 'reverse' character so that C64 will
		// show the directory using inverted glyphs
		out.write( 0x12 );

		// quote
		out.write( 0x22 );

		try
		{
			// write disk name
			final byte[] diskName = bam.getDiskName();
			out.write( diskName );
			
			// write padding bytes
			for ( int delta = 16 - diskName.length ; delta > 0 ; delta-- )
			{
				out.write( 0x20 );
			}
			
			// quote
			out.write( 0x22 );
			
			// whitespace
			out.write( 0x20 );
			
			// two byte disk ID
			final byte[] diskID = bam.getDiskID();
			
			out.write( diskID );
			
			out.write( data[BAM_OFFSET + 164] );	
			
			// two byte DOS version
			out.write( bam.getDOSType() );
		}
		catch (IOException e)
		{
			throw new RuntimeException("Can never happen...");
		}
		// write zero byte so BASIC interpreter knows this is the end of the line
		out.write( 0x00 );

		return out.toByteArray();
	}

	private byte[] toDirectoryLine(final DirectoryEntry entry)
	{
		final ByteArrayOutputStream out = new ByteArrayOutputStream();

		// dummy ptr to next line in basic listing , will be fixed later
		int lineWidth = 0;
		out.write( 0x01 ); // lo
		out.write( 0x01 ); // hi
		lineWidth += 2;

		final int sectors = entry.getFileSizeInSectors();

		// output number of sectors
		out.write( sectors & 0xff ); // lo
		out.write( (sectors >> 8 ) & 0xff ); // hi
		lineWidth += 2;

		// insert padding whitespace to nicely line up filenames
		byte[] fileName = entry.getTrimmedFileName();
		for ( int delta = 4-Integer.toString( sectors ).length() ; delta > 0 ; delta-- )
		{
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
		for ( int remaining = 16 - fileName.length ; remaining > 0 ; remaining--) 
		{ 
			out.write(0x20);
			lineWidth++;
		}
		
		if ( ! entry.isClosed() ) {
			out.write( (byte) '*');
		} else {
			out.write( (byte) ' ');
		}
		lineWidth+=1;
		
		// write file type
		switch( entry.getFileType() ) {
			case DEL:
				out.write( (byte) 'D'); out.write( (byte) 'E'); out.write( (byte) 'L');
				lineWidth+=3;
				break;
			case PRG:
				out.write( (byte) 'P'); out.write( (byte) 'R'); out.write( (byte) 'G');
				lineWidth+=3;
				break;
			case REL:
				out.write( (byte) 'R'); out.write( (byte) 'E'); out.write( (byte) 'L');
				lineWidth+=3;
				break;
			case SEQ:
				out.write( (byte) 'S'); out.write( (byte) 'E'); out.write( (byte) 'Q');
				lineWidth+=3;
				break;
			case USR:
				out.write( (byte) 'U'); out.write( (byte) 'S'); out.write( (byte) 'R');
				lineWidth+=3;
				break;
			default:
				out.write( (byte) '?'); out.write( (byte) '?'); out.write( (byte) '?');
				lineWidth+=3;
		}
		
		if ( entry.isLocked() ) {
			out.write( (byte) '<');
		} else {
			out.write( (byte) ' ');
		}
		lineWidth+=1;
		
		while ( lineWidth < 31 ) 
		{
			out.write( 0x20 );
			lineWidth+=1;	
		}	
		
		// terminate line
		out.write( 0x00 );
		
		return out.toByteArray();
	}

	public final class BAMEntry 
	{
		private final int entryOffset;
		public final int trackNo;

		public BAMEntry(int trackNo) 
		{
			this.trackNo = trackNo;
			this.entryOffset = BAM_OFFSET + 4 + (trackNo-1)*4;
		}

		/**
		 * Returns the number of free sectors as reported by the
		 * 'freeSectors' field of this BAM entry.
		 * 
		 * Note that the returned value may not actually reflect the
		 * true number of free sectors, use {@link #getFreeSectorCountFromBitmap()}
		 * if you really need this.
		 * 
		 * @return
		 */
		public int getFreeSectorsCount() {
			return data[ entryOffset ] & 0xff;
		}

		private void setFreeSectorsCount(int count) {
			data[ entryOffset ] = (byte) count;
		}

		public int sectorsOnTrack() {
			return getSectorsOnTrack( trackNo );
		}

		/**
		 * Returns the number of free sectors of this BAM entry
		 * by directly inspecting the block allocation bitmap.
		 * @return
		 */
		public int getFreeSectorCountFromBitmap() 
		{
			int count = 0;
			for ( int i = 0 , max = sectorsOnTrack() ; i < max ; i++ ) 
			{
				if ( isFree( i ) ) {
					count++;
				}
			}
			return count;
		}

		public int getOffsetOfFirstFreeSectorOnThisTrack() 
		{
			for ( int i = 0, max = sectorsOnTrack() ; i < max ; i++ ) 
			{
				if ( isFree(i ) ) 
				{
					return i;
				}
			}
			return -1;
		}

		public boolean isFree(int sectorOnTrack) 
		{
			if ( sectorOnTrack >= sectorsOnTrack() ) {
				throw new IllegalArgumentException("Track #"+trackNo+" only has "+sectorsOnTrack()+" sectors but you requested "+sectorOnTrack);
			}
			final int byteOffset = sectorOnTrack / 8; // 3 bytes allocation map with 1 bit per sector
			final int bitInByte = sectorOnTrack - byteOffset*8;
			final int allocation = data[ entryOffset + 1 + byteOffset ] & 0xff;
			return  ( allocation & (1 << bitInByte) ) != 0;
		}

		public boolean isAllocated(int sectorOnTrack) {
			return ! isFree( sectorOnTrack );
		}

		public void markAllocated(int relSector) 
		{
			if ( relSector >= sectorsOnTrack() ) {
				throw new IllegalArgumentException("Track #"+trackNo+" only has "+sectorsOnTrack()+" sectors but you requested "+relSector);
			}			
			final int byteOffset = relSector / 8; // 3 bytes allocation map with 1 bit per sector
			final int bitInByte = relSector - byteOffset*8;
			data[ entryOffset + 1 + byteOffset ] &= ~(1 << bitInByte);
			setFreeSectorsCount( getFreeSectorCountFromBitmap() );
		}
	}

	public final class BAM
	{
		private final List<BAMEntry> allocationMap;

		public BAM() 
		{
			final List<BAMEntry> result = new ArrayList<>();
			for ( int track = 1 ; track < 36 ; track++ ) 
			{
				result.add( new BAMEntry( track ) );
			}
			this.allocationMap = Collections.unmodifiableList( result );
		}

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
			return data[ BAM_OFFSET + 0x02 ];
		}

		public byte[] getDiskID()
		{
			return new byte[] { data[ BAM_OFFSET + 162 ], data[ BAM_OFFSET + 163 ] };
		}

		public byte[] getDOSType()
		{
			return new byte[] { data[ BAM_OFFSET + 165 ], data[ BAM_OFFSET + 166 ] };
		}

		public byte[] getDiskName()
		{
			int count = 0;
			for ( int i = 0 ; i < 16 ; i++ ) 
			{
				if ( data[ BAM_OFFSET + 0x90 + i ] == 0xa0 ) {
					break;
				}
				count++;
			}
			final byte[] result = new byte[count];
			System.arraycopy( data , BAM_OFFSET+0x90 , result , 0 , count );
			System.out.println("Disk name @ "+(BAM_OFFSET+0x90));
			return result;
		}

		public BAMEntry getAllocationMap(int track) {
			return allocationMap.stream().filter( e -> e.trackNo == track ).findFirst().orElseThrow( () -> new IllegalArgumentException("Invalid track no. "+track));
		}

		public List<BAMEntry> getAllocationMap() 
		{
			return new ArrayList<>( allocationMap );
		}
	}

	protected BAM getBAM() {
		return bam;
	}

	public List<BAMEntry> getAllocationMap() {
		return bam.getAllocationMap();
	}

	public String getDiskNameInASCII() 
	{
		byte[] diskName = bam.getDiskName();
		
		return CharsetConverter.petToASCII( diskName );
	}

	public DirectoryEntry savePRG(String fileNameInASCII , short prgLoadAdr, InputStream inputStream) throws IOException 
	{
		// copy input to byte array so we know the
		// number of bytes that need to be written
		final ByteArrayOutputStream buffer = new ByteArrayOutputStream();

		int in=-1;
		while( (in=inputStream.read() ) != -1 ) 
		{
			buffer.write( in );
		}

		// shift buffer by 2 bytes so we can insert the program load address
		// as the first 2 bytes
		byte[] payload = buffer.toByteArray();
		final byte[] tmp = new byte[ payload.length +2 ];
		System.arraycopy( payload , 0 , tmp , 2 , payload.length );
		payload = tmp;

		// insert program load address at start of payload
		payload[ 0 ] = (byte) ( prgLoadAdr & 0xff); // lo
		payload[ 1 ] = (byte) ((prgLoadAdr>>8) & 0xff); // hi		

		final int payloadSizeInSectors = (int) Math.ceil( payload.length / 254f ); 

		// simulate creating a new directory entry so we know whether we'll need one additional sector to hold the directory entry
		final int dirEntrySectorCount = allocDirectoryEntry(true).allocatedNewDirSector ? 1 : 0;
		final int sectorsRequired= payloadSizeInSectors + dirEntrySectorCount; 

		// make sure there's enough disk space available
		final int sectorsAvailable = getAllocationMap().stream().mapToInt( entry -> entry.getFreeSectorCountFromBitmap() ).sum();
		if ( sectorsAvailable < sectorsRequired ) {
			throw new IOException("Disk full (sectors required: "+sectorsRequired+" , sectors available: "+sectorsAvailable+")");
		}

		// allocate new directory entry
		final DirectoryEntry dirEntry = allocDirectoryEntry(false).dirEntry;
		dirEntry.setFileType( FileType.PRG );
		dirEntry.setClosed(false);
		dirEntry.setLocked( false );
		dirEntry.setFileName( CharsetConverter.asciiToPET( fileNameInASCII.toCharArray() ) );
		dirEntry.setFileSizeInSectors( payloadSizeInSectors );

		final List<BAMEntry> allocationMap = getAllocationMap();

		int readOffset = 0;
		int previousSectorOffset=-1;
		for (int i = 0; i < allocationMap.size(); i++) 
		{
			if ( readOffset >= payload.length ) 
			{
				dirEntry.setClosed( true );
				return dirEntry;
			}

			final BAMEntry bamEntry = allocationMap.get(i);
			final int relSector = bamEntry.getOffsetOfFirstFreeSectorOnThisTrack();
			if ( relSector != -1 ) 
			{
				bamEntry.markAllocated( relSector );

				final int absSector = getFirstSectorNoForTrack( bamEntry.trackNo ) + relSector;
				final int dataOffset = absSector * BYTES_PER_SECTOR; // first 2 bytes are used for linking the sectors

				final int payloadBytesFreeInThisSector=BYTES_PER_SECTOR-2;
				final int writeOffset = dataOffset+2;

				if ( previousSectorOffset == -1 )
				{
					// first data sector of file, update directory entry to point to it
					dirEntry.setFirstDataTrack( bamEntry.trackNo );
					dirEntry.setFirstDataSector( relSector );
				} 
				else 
				{
					this.data[ previousSectorOffset ] = (byte) bamEntry.trackNo;
					this.data[ previousSectorOffset+1 ] = (byte) relSector;
				} 

				previousSectorOffset = dataOffset;

				final int bytesLeftToWrite = payload.length - readOffset;
				final int bytesUsedInThisSector =  (bytesLeftToWrite > payloadBytesFreeInThisSector ? payloadBytesFreeInThisSector : bytesLeftToWrite);

				this.data[ dataOffset    ] = 0; // track number, 0 = last sector in file	... clear link to track of next data sector just in case this is the last sector we're writing		
				this.data[ dataOffset +1 ] = (byte) bytesUsedInThisSector; // bytes used in this sector, will be overwritten with sector number if this is not the last sector we're writing

				for ( int j = 0 ; j < payloadBytesFreeInThisSector ; j++ ) 
				{
					if ( readOffset < payload.length ) 
					{
						this.data[ writeOffset + j ] = payload[ readOffset++ ];
					} else {
						this.data[ writeOffset + j ] = 0; // TODO: I'm currently clearing the unused part of the sector, maybe leave it as it is ?
					}
				}
			}
		}
		// not reachable, unless the block allocation bitmap handling is screwed up for some reason (bug)
		throw new RuntimeException("Internal error, disk full ??");
	}	
}