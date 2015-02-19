package de.codesourcery.j6502.emulator;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import de.codesourcery.j6502.utils.HexDump;

public class CPU
{
	public static final int RESET_VECTOR_LOCATION = 0xfffc;

	private final IMemoryRegion memory;

	public short accumulator;
	public short pc;
	public byte x;
	public byte y;
	public short sp;

	public byte flags;

	public long cycles = 0;

	public static enum Flag
	{
		CARRY(1 <<0 , "C"), // 1
		ZERO(1 <<1 , "Z" ), // 2
		IRQ_DISABLE(1 <<2 , "I"), // 4
		DECIMAL_MODE(1 <<3 , "D"), // 8
		BREAK(1 <<4 , "B"), // 16
		EXPANSION(1 <<5 , "X" ), // 32
		OVERFLOW(1 <<6  , "O" ), // 64
		NEGATIVE(1 <<7 , "N"); // 128

		public final int value;
		public final char symbol;

		private Flag(int value,String symbol) { this.value = value; this.symbol = symbol.charAt(0); }
		public boolean isSet(byte flags) { return (flags&value) != 0; }
		public boolean isNotSet(byte flags) { return (flags&value) == 0; }
		public byte clear(byte flags) { return (byte) (flags&~value); }
		public byte set(byte flags) { return (byte) (flags | value); }
	}

	public CPU(IMemoryRegion memory)
	{
		this.memory = memory;
	}

	public void setFlag(Flag f) {
		this.flags = f.set( this.flags );
	}

	public void clearFlag(Flag f) {
		this.flags = f.clear( this.flags );
	}

	public void setFlag(Flag f,boolean onOff) {
		this.flags = onOff ? f.set( this.flags ) : f.clear( this.flags );
	}

	public boolean isSet(Flag f) {
		return f.isSet( this.flags );
	}

	public boolean isCleared(Flag f) {
		return f.isNotSet( this.flags );
	}

	public void reset()
	{
		cycles = 0;
		pc = memory.readWord( RESET_VECTOR_LOCATION );
		accumulator = 0xaa;
		x = y = 0;
		sp = 0xfd;
		flags = 0;
	}

	@Override
	public String toString()
	{
		// PC: 0000    A: FF    X: FF    Y: 00    SP: 00

		final StringBuilder flagBuffer = new StringBuilder("Flags: ");
		final Flag[] values = Flag.values();
		for (int i = values.length -1; i >= 0 ; i--)
		{
			final Flag f = values[i];
			if ( f.isSet( flags ) ) {
				flagBuffer.append( f.symbol );
			} else {
				flagBuffer.append(".");
			}
		}
		final StringBuilder buffer = new StringBuilder();
		buffer
		.append( "PC: ").append( HexDump.toHexBigEndian( pc ) )
		.append("    ")
		.append( flagBuffer )
		.append("    ")
		.append("A: ").append( HexDump.toHex( (byte) accumulator ) )
		.append("    ")
		.append("X: ").append( HexDump.toHex( x ) )
		.append("    ")
		.append("Y: ").append( HexDump.toHex( y ) )
		.append("    ")
		.append("SP: ").append( HexDump.toHex( sp ) );

		return buffer.toString();
	}

	public Set<Flag> getFlags() {
		return Arrays.stream( Flag.values() ).filter( f -> f.isSet( this.flags ) ).collect(Collectors.toSet());
	}
}
