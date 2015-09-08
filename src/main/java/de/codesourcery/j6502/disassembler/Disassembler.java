package de.codesourcery.j6502.disassembler;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.emulator.AddressRange;
import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.CPUImpl;
import de.codesourcery.j6502.emulator.IMemoryRegion;
import de.codesourcery.j6502.emulator.Memory;
import de.codesourcery.j6502.emulator.CPUImpl.ByteProvider;
import de.codesourcery.j6502.utils.HexDump;

public class Disassembler
{
	protected static final String EMPTY_STRING = "";

	private IMemoryRegion data;

	private final HexDump hexdump = new HexDump();

	private int previousOffset;
	private short currentOffset;
	private Consumer<Line> lineConsumer;

	private boolean writeAddresses = true;
	private boolean annotate = false;

	private int baseAddressToPrint;

	private ByteProvider byteProvider;
	private final StringBuilder buffer = new StringBuilder();

	private static final IMemoryRegion DUMMY_MEM = new Memory("dummy", AddressRange.range(0,65535) );
	private static final CPUImpl DUMMY_CPU = new CPUImpl( new CPU( DUMMY_MEM ) , DUMMY_MEM );

	public final class Line
	{
		public final short address;
		public final String instruction;
		public final String arguments;
		public final String comment;

		public Line(short address,String instruction,String comment)
		{
			this.address = address;
			this.instruction = instruction;
			arguments = EMPTY_STRING;
			this.comment = comment;
		}

		public Line(short address,String instruction,String arguments,String comment)
		{
			this.address = address;
			this.instruction = instruction;
			this.arguments = arguments;
			this.comment = comment;
		}

		@Override
		public String toString()
		{
			return getAsString();
		}

		public String getAsString()
		{
			String adrCol = EMPTY_STRING;
			final String insCol = instruction;
			final String argsCol = arguments;
			final String commentCol = comment;
			if ( writeAddresses )
			{
				adrCol = HexDump.toHexBigEndian( address )+":  ";
			}
			final String paddedArgs = StringUtils.rightPad( argsCol , 10 );
			return (adrCol+StringUtils.rightPad(insCol,6)+paddedArgs+commentCol).trim();
		}
	}

	public static void main(String[] args) {

		final int[] data = new int[] {
				0xad, 0x00, 0x44 , // Absolute      LDA $4400     $AD  3   4
				0xbd,0x00,0x44,    // Absolute,X    LDA $4400,X   $BD  3   4+
				0xb9,0x00,0x44,    // Absolute,Y    LDA $4400,Y   $B9  3   4+
				0xb5,0x44,         // Zero Page,X   LDA $44,X     $B5  2   4
				0xa9,0x44,         // # Immediate   LDA #$44      $A9  2   2
				0xa1,0x44,         // Indirect,X    LDA ($44,X)   $A1  2   6
				0xb1,0x44,         // Indirect,Y    LDA ($44),Y   $B1  2   5+
				0xa5,0x44};        // Zero Page     LDA $44       $A5  2   3		#

		final byte[] real = new byte[ data.length ];
		for ( int i = 0 ; i < data.length ; i++ ) {
			real[i]=(byte) data[i];
		}

		new Disassembler().disassemble( 0x1000, real , 0 , data.length ).forEach( System.out::println );
	}

	public Disassembler setAnnotate(boolean annotate) {
		this.annotate = annotate;
		return this;
	}

	public Disassembler setWriteAddresses(boolean writeAddresses) {
		this.writeAddresses = writeAddresses;
		return this;
	}

	private IMemoryRegion wrap(int startingOffset, byte[] data) {
		return new IMemoryRegion("dummy", new AddressRange(startingOffset & 0xffff, data.length))
		{
			@Override
			public void writeWord(int offset, short value) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void writeByte(int offset, byte value) {
				throw new UnsupportedOperationException();
			}

		    @Override
			public boolean isReadsReturnWrites(int offset) {
	            throw new UnsupportedOperationException();
		    }

			@Override
			public void reset() {
				throw new UnsupportedOperationException();
			}

			@Override
			public int readWord(int offset)
			{
				final int low = readByte(offset) & 0xff;
				final int hi = readByte( (short) (offset+1) ) & 0xff;
				return ((hi<<8) | low);
			}

			@Override
			public int readByte(int offset) {
				return data[ offset & 0xffff ] & 0xff;
			}

			@Override
			public int readAndWriteByte(int offset) {
				return data[ offset & 0xffff ] & 0xff;
			}

			@Override
			public String dump(int offset, int len) {
				throw new UnsupportedOperationException();
			}

			@Override
			public void bulkWrite(int startingAddress, byte[] data, int datapos,int len) {
				throw new UnsupportedOperationException();
			}

            @Override
            public int readByteNoSideEffects(int offset) {
                return data[ offset & 0xffff ] & 0xff;
            }
		};
	}

	public List<Line> disassemble(int baseAdrToPrint,byte[] data,int offset,int len)
	{
		final List<Line> result = new ArrayList<>();
		disassemble(wrap(baseAdrToPrint,data), offset, len, result::add );
		return result;
	}

	public void disassemble(int baseAdrToPrint,byte[] data,int offset,int len,OutputStream out)
	{
		final OutputStreamWriter writer = new OutputStreamWriter(out);
		disassemble(baseAdrToPrint, data, offset, len , writer );
	}

	public void disassemble(int baseAdrToPrint,byte[] data,int offset,int len,Writer writer)
	{
		disassemble( wrap(baseAdrToPrint,data) , offset , len , writer );
	}

	public void disassemble(IMemoryRegion region,int offset,int len,Writer writer)
	{
		disassemble(region, offset, len, new Consumer<Line>()
		{
			private boolean firstLine = true;
			@Override
			public void accept(Line line)
			{
				try
				{
					if ( ! firstLine ) {
						writer.write("\n");
					}
					firstLine = false;
					writer.write( line.getAsString() );
				}
				catch (final Exception e)
				{
					throw new RuntimeException(e);
				}
			}
		});
	}

	public void disassemble(IMemoryRegion data,int offset,int len,Consumer<Line> lineConsumer)
	{
		this.hexdump.setBytesPerLine(3);

		this.baseAddressToPrint = data.getAddressRange().getStartAddress();
		this.data = data;
		this.currentOffset = (short) offset;
		this.previousOffset = currentOffset;
		this.lineConsumer = lineConsumer;

		setup( data , offset , len );

		while ( byteProvider.availableBytes() > 0 )
		{
			disassembleLine();
			this.previousOffset = currentOffset;
		}
	}

	private void setup(IMemoryRegion data,int offset,int len)
	{
		byteProvider = new ByteProvider()
		{
			private int ptr = offset;
			private int insOffset;

			private void assertBytesRemaining(int expected)
			{
				if ( availableBytes() < expected ) {
					throw new IllegalStateException("Out of data, expected "+expected+" bytes to be available but got only "+availableBytes());
				}
			}

			@Override
			public int readWord() {
				assertBytesRemaining(2);
				final int result = data.readWord( ptr );
				ptr += 2;
				return result;
			}

			@Override
			public int readByte() {
				assertBytesRemaining(1);
				return data.readByte( ptr++ );
			}

			@Override
			public int availableBytes() {
				return len-(ptr-offset);
			}

			@Override
			public void mark() {
				insOffset = ptr;
			}

			@Override
			public int getMark()
			{
				return insOffset;
			}

			@Override
			public int toAbsoluteAddress(int relOffset)
			{
				return baseAddressToPrint + insOffset + relOffset;
			}
		};
	}

	private void disassembleLine()
	{
		buffer.setLength(0);

		byteProvider.mark();

		DUMMY_CPU.disassemble( buffer , "   " , byteProvider );
		System.out.println( buffer.toString() );
		addLine( (short) byteProvider.getMark()  , buffer.toString() );
	}

	private void addLine(short address,String instruction)
	{
		final int adr = baseAddressToPrint + address;
		String comment = EMPTY_STRING;
		if ( annotate ) {
			comment = "; "+hexdump.dump( (short) adr , data , previousOffset , currentOffset - previousOffset );
		}
		lineConsumer.accept( new Line( (short) adr , instruction , comment ) );
	}
}