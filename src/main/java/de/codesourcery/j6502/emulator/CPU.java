package de.codesourcery.j6502.emulator;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import de.codesourcery.j6502.utils.HexDump;

public class CPU
{
	public static final int RESET_VECTOR_LOCATION = 0xfffc;
	public static final int BRK_VECTOR_LOCATION = 0xfffe;

	/*
On an NMI, the CPU pushes the low byte and the high byte of the program counter as well as the processor status onto the stack, disables interrupts and
loads the vector from $FFFA/$FFFB into the program counter and continues fetching instructions from there.
On an IRQ, the CPU does the same as in the NMI case, but uses the vector at $FFFE/$FFFF.
	 */
	public static final int IRQ_VECTOR_LOCATION = 0xfffe;

	private final IMemoryRegion memory;

	public long cycles = 0;
	public short previousPC;
	public short pc;
	public byte accumulator;
	public byte x;
	public byte y;
	public short sp;

	public byte flags;

	public static enum Flag
	{
		CARRY(1 <<0 , "C"), // 1
		ZERO(1 <<1 , "Z" ), // 2
		IRQ_DISABLE(1 <<2 , "I"), // 4
		DECIMAL_MODE(1 <<3 , "D"), // 8
		BREAK(1 <<4 , "B"), // 16
		UNUSED(1 <<5 , "X" ), // 32
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

	public void interrupt(IMemoryRegion mainMemory)
	{
		if ( ! isSet(Flag.IRQ_DISABLE ) )
		{
			push( (byte) ( ( pc & 0xff00) >>8 ) ,  memory ); // push pc hi
			push( (byte) ( pc & 0xff ) , memory ); // push pc lo
			push( flags , memory ); // push processor flags
			pc = memory.readWord( (short) CPU.IRQ_VECTOR_LOCATION );
		}
	}

	public void push(short value,IMemoryRegion region)
	{
		final byte hi = (byte) ((value >> 8) & 0xff);
		final byte lo = (byte) (value & 0xff);
		push( hi , region );
		push( lo , region );
	}

	public void push(byte value,IMemoryRegion region)
	{
		decSP();
		region.writeByte( sp , value );
	}

	public byte pop(IMemoryRegion region) {
		final byte result = region.readByte( sp );
		incSP();
		return result;
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
		pc = memory.readWord( (short) RESET_VECTOR_LOCATION );
		previousPC = pc;
		System.out.println("CPU.reset(): Settings PC to "+HexDump.toAdr( pc ) );
		accumulator = 0;
		x = y = 0;
		sp = 0x1ff;
		flags = CPU.Flag.IRQ_DISABLE.set((byte) 0);
	}

	@Override
	public String toString()
	{
		// PC: 0000    A: FF    X: FF    Y: 00    SP: 00
		final String flagBuffer = "Flags: "+getFlagsString();

		final int end = 0x01ff;
		int start = end - 16;
		final int currentSp = sp & 0xffff;
		if ( start > currentSp ) {
			start = currentSp;
		}
		final String dump = getStackDump();

		final StringBuilder buffer = new StringBuilder();
		buffer
		.append( "PC: ").append( HexDump.toHexBigEndian( pc ) )
		.append("    ")
		.append( flagBuffer )
		.append("    ")
		.append("A: ").append( HexDump.toHex( accumulator ) )
		.append("    ")
		.append("X: ").append( HexDump.toHex( x ) )
		.append("    ")
		.append("Y: ").append( HexDump.toHex( y ) )
		.append("    ")
		.append("SP: ").append( HexDump.toAdr( sp )+" "+dump );

		return buffer.toString();
	}

	public String getStackDump()
	{
		final int end = 0x01ff;
		int start = end - 16;
		final int currentSp = sp & 0xffff;
		if ( start > currentSp ) {
			start = currentSp;
		}
		return HexDump.INSTANCE.dump( (short) start , sp , memory , start , 16 );
	}

	public String getFlagsString()
	{
		final StringBuilder flagBuffer = new StringBuilder();
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
		return flagBuffer.toString();
	}

	public Set<Flag> getFlags() {
		return Arrays.stream( Flag.values() ).filter( f -> f.isSet( this.flags ) ).collect(Collectors.toSet());
	}

	public void setSP(byte value)
	{
		int expanded = value;
		expanded &= 0xff;
		this.sp = (short) ((0x0100 | expanded));
	}

	public void incSP() {
		short expanded = this.sp;
		expanded &= 0xff;
		expanded++;
		this.sp = (short) ((0x0100 | expanded));
	}

	public void decSP() {
		short expanded = this.sp;
		expanded &= 0xff;
		expanded--;
		expanded &= 0xff;
		this.sp = (short) ((0x0100 | expanded));
	}
}
