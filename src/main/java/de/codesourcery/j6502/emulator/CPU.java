package de.codesourcery.j6502.emulator;

import java.util.Arrays;
import java.util.Collection;
import java.util.Set;
import java.util.stream.Collectors;

import de.codesourcery.j6502.utils.HexDump;

public class CPU
{
	public static final int RESET_VECTOR_LOCATION = 0xfffc;
	public static final int BRK_VECTOR_LOCATION = 0xfffe; // same as IRQ_VECTOR_LOCATION
	public static final int IRQ_VECTOR_LOCATION = 0xfffe; // same as BRK_VECTOR_LOCATION
	public static final int NMI_VECTOR_LOCATION = 0xfffa;

	private final IMemoryRegion memory;

	public static enum IRQType { REGULAR , NMI , BRK , NONE };

	private IRQType interruptQueued = IRQType.NONE;

	public long cycles = 0;
	public short previousPC;

	private int pc;
	private int accumulator;
	private int x;
	private int y;
	public short sp;
	
	public int lastInsDuration;
	
	private boolean breakOnInterrupt;
	private boolean breakpointReached;

	private byte flags = CPU.Flag.EXTENSION.set((byte)0); // extension bit is always 1

	public static enum Flag
	{
		CARRY(1 <<0 , "C" , "Carry"), // 1
		ZERO(1 <<1 , "Z" , "Zero" ), // 2
		IRQ_DISABLE(1 <<2 , "I" , "IRQ disabled"), // 4
		DECIMAL_MODE(1 <<3 , "D" , "Decimal mode"), // 8
		BREAK(1 <<4 , "B" , "Break"), // 16
		EXTENSION(1 <<5 , "X" , "<unused>"), // 32
		OVERFLOW(1 <<6  , "O" , "Overflow"), // 64
		NEGATIVE(1 <<7 , "N" , "Negative"); // 128

		public final int value;
		public final char symbol;
		public final String name;

		private Flag(int value,String symbol,String name) { this.value = value; this.symbol = symbol.charAt(0); this.name = name; }

		public boolean isSet(byte flags) { return (flags&value) != 0; }
		public boolean isNotSet(byte flags) { return (flags&value) == 0; }
		public byte clear(byte flags) { return (byte) (flags&~value); }
		public byte set(byte flags) { return (byte) (flags | value); }

		public static String toFlagString(byte bitMask)
		{
			StringBuilder result = new StringBuilder();
			for ( Flag f : values() ) {
				result.append( f.isSet( bitMask ) ? f.symbol : '.' );
			}
			return result.toString();
		}

		public static byte toBitMask(Collection<CPU.Flag> flags)
		{
			int result = 0;
			for ( CPU.Flag f : flags ) {
				result |= f.value;
			}
			return (byte) result;
		}
		
		public static Flag fromSymbol(char c) 
		{
		    final char reg = Character.toLowerCase( c );
		    for ( int i = 0 ; i < values().length ; i++ ) {
		        if ( values()[i].symbol == reg ) {
		            return values()[i];
		        }
		    }
		    throw new RuntimeException("Unknown register symbol: "+c);
		}
	}

	public void populateFrom(CPU other) {
	    this.interruptQueued = other.interruptQueued;
	    this.cycles = other.cycles;
	    this.previousPC = other.previousPC;
	    this.pc = other.pc;
	    this.accumulator = other.accumulator;
	    this.x = other.x;
	    this.y = other.y;
	    this.sp = other.sp;
	    this.flags = other.flags;
	}

	public boolean matches(CPU other)
	{
	    boolean result= this.interruptQueued == other.interruptQueued
        && this.previousPC == other.previousPC
        && this.pc == other.pc
        && this.accumulator == other.accumulator
        && this.x == other.x
        && this.y == other.y
        && this.sp == other.sp
        && this.flags == other.flags;

	    if ( result && this.cycles != other.cycles ) {
	        System.err.println("WARNING: Cycle count mismatch , this: "+this.cycles+" <-> other: "+other.cycles);
	    }
	    return result;
	}

	public byte getFlagBits() {
		return flags;
	}

	public void setFlagBits(byte bits) {
		this.flags = CPU.Flag.EXTENSION.set( bits ); // extension bit is always 1
	}

	public void pc(int value) {
		this.pc = value & 0xffff;
	}

	public int pc() {
		return pc;
	}

	public void incPC() {
		this.pc = (this.pc + 1) & 0xffff;
	}

	public void incPC(int increment) {
		this.pc = (this.pc + increment) & 0xffff;
	}

	public CPU(IMemoryRegion memory)
	{
		this.memory = memory;
	}

	private void performInterrupt()
	{
        pushByte( (byte) ( ( pc & 0xff00) >>8 ) ,  memory ); // push pc hi
        pushByte( (byte) ( pc & 0xff ) , memory ); // push pc lo

	    switch( interruptQueued )
	    {
            case BRK:
                pushByte( CPU.Flag.BREAK.set( flags ) , memory ); // push processor flags
                pc = memory.readWord( CPU.BRK_VECTOR_LOCATION );
                break;
            case NMI:
                pushByte( flags , memory ); // push processor flags
                pc = memory.readWord( CPU.NMI_VECTOR_LOCATION );
                break;
            case REGULAR:
                pushByte( flags , memory ); // push processor flags
                pc = memory.readWord( CPU.IRQ_VECTOR_LOCATION );
                break;
            default:
                throw new RuntimeException("Unhandled IRQ type: "+interruptQueued);
	    }
		flags = CPU.Flag.IRQ_DISABLE.set( this.flags );
		clearInterruptQueued();
	}

	public void pushWord(short value,IMemoryRegion region)
	{
		final byte hi = (byte) ((value >> 8) & 0xff);
		final byte lo = (byte) (value & 0xff);
		pushByte( hi , region );
		pushByte( lo , region );
	}

	public void pushByte(byte value,IMemoryRegion region)
	{
		region.writeByte( sp , value );
		decSP();
	}

	public int pop(IMemoryRegion region)
	{
		incSP();
		return region.readByte( sp );
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

	public boolean isNotSet(Flag f) {
		return f.isNotSet( this.flags );
	}

	public boolean isCleared(Flag f) {
		return f.isNotSet( this.flags );
	}

	public void reset()
	{
	    lastInsDuration = 0;
	    
	    breakOnInterrupt = false;
	    breakpointReached = false;
	    
		cycles = 1;
		
		pc = memory.readWord( RESET_VECTOR_LOCATION );
		System.out.println("RESET: PC of CPU "+this.memory+" now points to "+HexDump.toAdr( pc ) );

		previousPC = (short) pc;
		setAccumulator(0);
		setX(0);
		setY(0);
		clearInterruptQueued();
		sp = 0x1ff;
		setFlagBits( CPU.Flag.IRQ_DISABLE.set( (byte) 0) );
	}
	
	public void setBreakOnInterrupt() {
	    breakOnInterrupt = true;
	}
	
	public void setBreakpointReached() {
	    this.breakpointReached = true;
	}
	
	public boolean isBreakpointReached() 
	{
	    if ( breakpointReached ) {
	        breakpointReached = false;
	        return true;
	    }
        return false;
    }

	public void queueInterrupt(IRQType type)
	{
	    if ( type == IRQType.NONE ) {
	        throw new IllegalArgumentException("Cannot queue IRQType.NONE");
	    }
	    if ( this.interruptQueued == IRQType.NMI ) {
	        return; // do not replace NMI with any other interrupt
	    }
		this.interruptQueued = type;
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

		final String accuBinary = HexDump.toBinaryString( (byte) accumulator );
		buffer
        .append( "Cyles: ").append( cycles )
        .append("    ")
		.append( "PC: ").append( HexDump.toHexBigEndian( pc ) )
		.append("    ")
		.append( flagBuffer )
		.append("    ")
		.append("A: ").append( HexDump.byteToString( (byte) getAccumulator() ) ).append("( "+accuBinary+" )")
		.append("    ")
		.append("X: ").append( HexDump.byteToString( (byte) getX() ) )
		.append("    ")
		.append("Y: ").append( HexDump.byteToString( (byte) getY() ) )
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

	/**
	 * 
	 * @return <code>true</code> if PC was changed to an IRQ routine, otherwise <code>false</code>
	 */
    public boolean handleInterrupt()
	{
	    switch( interruptQueued )
	    {
	        case NMI:
	            performInterrupt();
	            if ( breakOnInterrupt ) {
	                breakpointReached = true;
	                breakOnInterrupt = false;
	            }
	            return true;
	        case NONE:
	            break;
	        case REGULAR:
            case BRK:
                if ( isCleared( Flag.IRQ_DISABLE ) ) 
                {
                    performInterrupt();
                    if ( breakOnInterrupt ) {
                        breakpointReached = true;
                        breakOnInterrupt = false;
                    }
                    return true;
                }
                break;
            default:
                throw new RuntimeException("Unreachable code reached");
	    }
	    return false;
	}

	public boolean isInterruptQueued() {
		return interruptQueued != IRQType.NONE;
	}

	public void clearInterruptQueued() {
		this.interruptQueued = IRQType.NONE;
	}

	public void setSP(int value)
	{
		final int expanded = value & 0xff;
		this.sp = (short) ((0x0100 | expanded));
	}

	public void incSP() {
		int expanded = this.sp &0xff;
		expanded++;
		expanded &= 0xff;
		this.sp = (short) ((0x0100 | expanded));
	}

	public void decSP() {
		int expanded = this.sp & 0xff;
		expanded--;
		expanded &= 0xff;
		this.sp = (short) ((0x0100 | expanded));
	}

	public int getX() {
		return x;
	}

	public void setX(int x) {
		this.x = (x & 0xff);
	}

	public int getY() {
		return y;
	}

	public void setY(int y) {
		this.y = (y & 0xff);
	}

	public int getAccumulator() {
		return accumulator;
	}

	public void setAccumulator(int accumulator) {
		this.accumulator = accumulator & 0xff;
	}

	public int getSP() {
		return this.sp & 0xffff;
	}

    public boolean isBreakOnIRQ() {
        return breakOnInterrupt;
    }
}