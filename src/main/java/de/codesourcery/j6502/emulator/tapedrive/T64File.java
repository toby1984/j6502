package de.codesourcery.j6502.emulator.tapedrive;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import de.codesourcery.j6502.utils.CharsetConverter;

public class T64File 
{
	private final byte[] data;
	
	/*
	 *       $ 0-20: File magic header ('C64xxxxxxx')
     *       $20-21: Tape version number of either $0100 or $0101. I am  not  sure
     *               what differences exist between versions.
     *        22-23: Maximum  number  of  entries  in  the  directory,  stored  in
     *               low/high byte order (in this case $0190 = 400 total)
     *        24-25: Total number of used entries, once again  in  low/high  byte.
     *               Used = $0005 = 5 entries.
     *        26-27: Not used
     *        28-3F: Tape container name, 24 characters, padded with $20 (space)	 
	 */
	private String header;
	private int version;
	private int maxEntries;
	private int usedEntries;
	private String containerName;
	
	private final List<DirEntry> entries = new ArrayList<>();	
	
	public T64File(File file) throws IOException 
	{
		this( new FileInputStream(file) );
	}
	
	public T64File(InputStream in) throws IOException 
	{
		try 
		{
			byte[] data= new byte[1024];
			final byte[] buffer= new byte[1024];
			int len = 0;
			int dataPtr = 0;
			while ( (len=in.read( buffer ) ) > 0 ) 
			{
				if ( (dataPtr+len) >= data.length ) {
					final byte[] newData = new byte[ data.length + data.length/2 ];
					System.arraycopy( data , 0 , newData , 0 , dataPtr );
					data = newData;
				}
				System.out.println("read "+len+" bytes, dataptr="+dataPtr+", array len = "+data.length);
				System.arraycopy( buffer, 0 , data , dataPtr , len );
				dataPtr += len;
			}
			this.data = new byte[ dataPtr ];
			if ( dataPtr > 0 ) {
				System.arraycopy( data , 0 , this.data , 0 , dataPtr );
			}
			parse();
		} 
		finally 
		{
			in.close();
		}
	}
	
	private void parse() throws IOException 
	{
		final StringBuilder header = new StringBuilder();
		
		for ( int i =0 ; i < 32 && data[i] != 0 ; i++ ) {
			header.append( (char) data[i] );
		}
		this.header = header.toString();
		if ( ! this.header.startsWith( "C64" ) ) {
			throw new IOException("File contains unrecognized T64 header: '"+this.header+"'");
		}
		version = readWord( 0x20 );
		maxEntries = readWord( 0x22 );
		usedEntries = readWord( 0x24 );
		containerName = getASCIIString( 0x28 , 24 ).trim();
		
		for (int i = 0 ; i < usedEntries ; i++ ) {
			this.entries.add( new DirEntry( 64 + i*32 ) );
		}
	}
	
	private int readWord(int offset) {
		int low = data[offset] & 0xff;
		int hi = data[offset+1] & 0xff;
		return hi<<8 | low;
	}
	
	private String getASCIIString(int offset,int len) 
	{
		return CharsetConverter.petToASCII( Arrays.copyOfRange( this.data , offset ,offset+len ) );
	}
	
	
	/*
	 *** T64 (Tape containers for C64s)
	 *** Document revision: 1.5
	 *** Last updated: March 11, 2004
	 *** Contributors/sources: Miha Peternel

  This format, designed  by  Miha  Peternel,  is  for  use  with  his  C64s
emulator. It has a very structured directory with each entry taking  up  32
bytes, and a reasonably well-documented format.

  It has a large header  at  the  beginning  of  the  file  used  for  file
signature, tape name, number of directory entries, used  entries,  and  the
remainder for actual tape directory entries.

  Following immediately after the end of the directory comes the  data  for
each file. Each directory entry includes the information of where its  data
starts in the file (referenced to the beginning of the file),  as  well  as
the starting and ending C64 load addresses. From  these  addresses  we  can
determine how long the stored file is (end-start).

  Unfortunately, in the early days of the C64s emulator,  before  Miha  had
his MAKETAPE utility ready, another program called CONV64 was on the scene,
and it created faulty T64 files. The ending load address was usually set to
$C3C6 regardless of file size. Be aware that these files  are  still  quite
common on the Web and FTP sites.

Here is a HEX dump of the first few bytes of a standard T64 file:

        00 01 02 03 04 05 06 07 08 09 0A 0B 0C 0D 0E 0F        ASCII
        -----------------------------------------------   ----------------
000000: 43 36 34 53 20 74 61 70 65 20 69 6D 61 67 65 20   C64S.tape.image.
000010: 66 69 6C 65 00 00 00 00 00 00 00 00 00 00 00 00   file............
000020: 01 01 90 01 05 00 00 00 43 36 34 53 20 44 45 4D   ........C64S.DEM
000030: 4F 20 54 41 50 45 20 20 20 20 20 20 20 20 20 20   O.TAPE..........
000040: 01 01 01 08 85 1F 00 00 00 04 00 00 00 00 00 00   ................
000050: 53 50 59 4A 4B 45 52 48 4F 45 4B 20 20 20 20 20   SPYJKERHOEK.....
000060: 01 01 01 08 B0 CA 00 00 84 1B 00 00 00 00 00 00   ................
000070: 49 4D 50 4F 53 53 49 42 4C 45 20 4D 49 53 53 2E   IMPOSSIBLE MISS.
...
0003E0: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00   ................
0003F0: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00   ................
000400: 1A 08 E4 07 9E 32 30 38 30 14 14 14 14 14 14 14   ................

  The first 32 bytes ($000000-00001F) represent the signature of the  file,
telling us it is a tape container for C64S. Note that it is padded with $00
to make the signature 32 bytes long.

000000: 43 36 34 53 20 74 61 70 65 20 69 6D 61 67 65 20   C64S.tape.image.
000010: 66 69 6C 65 00 00 00 00 00 00 00 00 00 00 00 00   file............

  It is important that the string "C64" be at the  beginning  of  the  file
because it is the string which is common enough to be used to identify  the
file type. There are several variations of  this  string  like  "C64S  tape
file" or "C64 tape image file". The string is stored in ASCII.

  The next 32 bytes contain all the info about the directory  size,  number
of used entries, tape container name, tape version#, etc.

000020: 01 01 90 01 05 00 00 00 43 36 34 53 20 44 45 4D   ........C64S.DEM
000030: 4F 20 54 41 50 45 20 20 20 20 20 20 20 20 20 20   O TAPE..........

Bytes:$20-21: Tape version number of either $0100 or $0101. I am  not  sure
              what differences exist between versions.
       22-23: Maximum  number  of  entries  in  the  directory,  stored  in
              low/high byte order (in this case $0190 = 400 total)
       24-25: Total number of used entries, once again  in  low/high  byte.
              Used = $0005 = 5 entries.
       26-27: Not used
       28-3F: Tape container name, 24 characters, padded with $20 (space)

*/
	
	public final class DirEntry 
	{
		/*
         *     0 = free (usually)
         *     1 = Normal tape file
         *     3 = Memory Snapshot, v .9, uncompressed
         * 2-255 = Reserved (for memory snapshots)		 
		 */
		public int cs64sFileType;
		
		public int floppyFileType;
		public int loadAddress;
		public int endAddress;
		public int fileDataOffset;
		public byte[] petsciiName;
		
		public DirEntry(int offset) throws IOException 
		{
			this.cs64sFileType = data[offset+0];
			this.floppyFileType = data[offset+1];
			this.loadAddress = readWord( offset+2 );
			this.endAddress = readWord( offset+4 );
			
			int loWord = readWord( offset + 0x08 );
			int hiWord = readWord( offset + 0x0a );
			this.fileDataOffset = hiWord << 16 | loWord;
			
			if ( length() < 0 ) {
				throw new IOException("Directory entry at offset "+toHex( offset )+" has invalid size "+length());
			}
			if ( fileDataOffset < 0 || fileDataOffset+length() > data.length ) {
				throw new IOException("Directory entry at offset "+toHex( offset )+" has file start offset "+toHex( fileDataOffset )+" , size "+length());
			}			
			this.petsciiName = Arrays.copyOfRange( data , offset+0x10 , offset+0x10+16 );
		}
		
		public int length() {
			return endAddress-loadAddress;
		}
		
		public String asciiName() {
			return CharsetConverter.petToASCII( petsciiName );
		}

		@Override
		public String toString() {
			return "DirEntry [name="+asciiName()+", length="+length()+", cs64sFileType=" + cs64sFileType + ", floppyFileType=" + floppyFileType + ", loadAddress="
					+ toHex(loadAddress) + ", endAddress=" + toHex(endAddress)+ ", fileDataOffset=" + toHex(fileDataOffset) + "]";
		}
		
	}
	
	private static String toHex(int i ) {
		return "$"+Integer.toHexString( i );
	}
	
	/*
  The next 32 bytes (and  on  until  the  end  of  the  directory)  contain
individual directory entries.

000040: 01 01 01 08 85 1F 00 00 00 04 00 00 00 00 00 00   ................
000050: 53 50 59 4A 4B 45 52 48 4F 45 4B 20 20 20 20 20   SPYJKERHOEK.....
000060: 01 01 01 08 B0 CA 00 00 84 1B 00 00 00 00 00 00   ................
000070: 49 4D 50 4F 53 53 49 42 4C 45 20 4D 49 53 53 2E   IMPOSSIBLE MISS.

Bytes   $40: C64s filetype
                  0 = free (usually)
                  1 = Normal tape file
                  3 = Memory Snapshot, v .9, uncompressed
              2-255 = Reserved (for memory snapshots)
         41: 1541 file type (0x82 for PRG, 0x81 for  SEQ,  etc).  You  will
             find it can vary  between  0x01,  0x44,  and  the  normal  D64
             values. In reality any value that is not a $00 is  seen  as  a
             PRG file. When this value is a $00 (and the previous  byte  at
             $40 is >1), then the file is a special T64 "FRZ" (frozen) C64s
             session snapshot.
      42-43: Start address (or Load address). This is the first  two  bytes
             of the C64 file which is usually the load  address  (typically
             $01 $08). If the file is a snapshot, the address will be 0.
      44-45: End address (actual end address in memory,  if  the  file  was
             loaded into a C64). If  the  file  is  a  snapshot,  then  the
             address will be a 0.
      46-47: Not used
      48-4B: Offset into the container file (from the beginning)  of  where
             the C64 file starts (stored as low/high byte)
      4C-4F: Not used
      50-5F: C64 filename (in PETASCII, padded with $20, not $A0)

  Typically, an empty entry will have no contents at all, and not just have
the first byte set to $00. If you only set the C64s filetype  byte  to  $00
and then use the file in C64S, you will see  the  entry  is  still  in  the
directory.

0003E0: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00   ................
0003F0: 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00   ................

  Starting at $000400 (assuming a directory with 30 entries)  we  now  have
actual file data.

000400: 1A 08 E4 07 9E 32 30 38 30 14 14 14 14 14 14 14   .....2080.......

---------------------------------------------------------------------------	 
	 */
	
	public static void main(String[] args) throws Exception 
	{
	
		final T64File file = new T64File( new File("/home/tgierke/mars_workspace/j6502/tapes/choplifter.t64" ) );
		System.out.println("File: "+file);
		System.out.println("Entries: "+file.entries);
	}

	@Override
	public String toString() {
		return "T64File [header=" + header + ", version=" + version + ", maxEntries=" + maxEntries + ", usedEntries="
				+ usedEntries + ", containerName=" + containerName + "]";
	}
}