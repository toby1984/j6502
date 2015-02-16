package de.codesourcery.j6502.emulator;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

import de.codesourcery.j6502.utils.HexDump;

public class CPU
{
	private final IMemoryRegion memory;

	public short accumulator;
	public int pc;
	public short x;
	public short y;
	public int sp;

	public int flags;

	public static enum Flag
	{
		CARRY(1 <<0 , "C"),
		ZERO(1 <<1 , "Z" ),
		IRQ_DISABLE(1 <<2 , "I"),
		DECIMAL_MODE(1 <<3 , "D"),
		BREAK(1 <<4 , "B"),
		EXPANSION(1 <<5 , "X" ),
		OVERFLOW(1 <<6  , "O" ),
		NEGATIVE(1 <<7 , "N");

		public final int value;
		public final char symbol;

		private Flag(int value,String symbol) { this.value = value; this.symbol = symbol.charAt(0); }
		public boolean isSet(int flags) { return (flags&value) != 0; }
		public boolean isNotSet(int flags) { return (flags&value) == 0; }
		public int clear(int flags) { return (flags&~value); }
		public int set(int flags) { return flags | value; }
	}

/*
Immediate     LDA #$44      $A9  2   2
Zero Page     LDA $44       $A5  2   3
Zero Page,X   LDA $44,X     $B5  2   4
>Absolute      LDA $4400     $AD  3   4
>Absolute,X    LDA $4400,X   $BD  3   4+
>Absolute,Y    LDA $4400,Y   $B9  3   4+
Indirect,X    LDA ($44,X)   $A1  2   6
Indirect,Y    LDA ($44),Y   $B1  2   5+
 */
	private static enum AdrMode
	{
		/**
		 * This mode is only used with the X register.
         * Consider a situation where the instruction is
         *
         * LDA ($20,X)
         *
         * - X contains $04
         * - and memory at $24 contains 0024: 74 20
         *
         * First, X is added to $20 to get $24.
         * The target address will be fetched from $24 resulting in a target address of $2074.
         * Register A will be loaded with the contents of memory at $2074.
		 */
		INDEXED_INDIRECT_X,
		/** LDA $10 **/
		ZERO_PAGE,
		/** LDA #$10 */
		IMMEDIATE,
		/** LDA $da00 */
		ABSOLUTE,
		/** LDA ( $10 ) , Y */
		INDIRECT_INDEXED_Y,
		/** LDA ( $10 , X ) */
		ZERO_PAGE_X,
		/** LDA $1000 , Y */
		ABSOLUTE_INDEXED_Y,
		/** LDA $1000 , X */
		ABSOLUTE_INDEXED_X;
	}

	public CPU(IMemoryRegion memory)
	{
		this.memory = memory;
	}

	public void reset() {
		accumulator = 0;
		pc = 0;
		x = 0;
		y = 0;
		sp = 0;
		flags = 0;
	}

	public int singleStep()
	{
		// read op-code

		final int pcBackup = pc;
		final int value = memory.readByte( pc++ );

		/*
 Most instructions that explicitly reference memory locations have bit patterns of the form aaabbbcc. The aaa and cc bits determine the opcode, and the bbb bits determine the addressing mode.

 Instructions with cc = 01 are the most regular, and are therefore considered first. The aaa bits determine the opcode as follows:

aaa	opcode
000	ORA
001	AND
010	EOR
011	ADC
100	STA
101	LDA
110	CMP
111	SBC

 And the addressing mode (bbb) bits:
bbb	addressing mode
000	(zero page,X)
001	zero page
010	#immediate
011	absolute
100	(zero page),Y
101	zero page,X
110	absolute,Y
111	absolute,X
		 */

		try
		{
			final int opcode = (value >> 5 ) & 0b111;
			final int modeBits = ( value >> 2 ) & 0b111;
			final AdrMode mode;
			switch( modeBits ) {
				case 0b000:
					mode = AdrMode.INDEXED_INDIRECT_X; break; // LDA ($44,X)
				case 0b001:
					mode = AdrMode.ZERO_PAGE; break; // LDA $44
				case 0b010:
					mode = AdrMode.IMMEDIATE; break; // LDA #$44
				case 0b011:
					mode = AdrMode.ABSOLUTE; break; // LDA $4400
				case 0b100:
					mode = AdrMode.INDIRECT_INDEXED_Y; break; // LDA ($44), Y
				case 0b101:
					mode = AdrMode.ZERO_PAGE_X; break; // LDA $44, X
				case 0b110:
					mode = AdrMode.ABSOLUTE_INDEXED_Y; break; // LDA $4400, Y
				case 0b111:
					mode = AdrMode.ABSOLUTE_INDEXED_X; break; // LDA $4400, X
				default:
					throw new InvalidOpcodeException("Unknown addressing mode",pc, memory.readByte(pc ) );
			}

			switch( opcode )
			{
				case 0b000:
					return handleORA(opcode,mode);
				case 0b001:
					return handleAND(opcode,mode);
				case 0b010:
					return handleEOR(opcode,mode);
				case 0b011:
					return handleADC(opcode,mode);
				case 0b100:
					return handleSTA(opcode,mode);
				case 0b101:
					return handleLDA(opcode,mode);
				case 0b110:
					return handleCMP(opcode,mode);
				case 0b111:
					return handleSBC(opcode,mode);
				default:
					throw new InvalidOpcodeException("Unknown instruction",pc, memory.readByte(pc ) );
			}
		}
		catch(final Throwable t)
		{
			this.pc = pcBackup;
			throw t;
		}
	}

	private int handleLDA(int opcode,AdrMode mode)
	{
		int cycles = 0;
		/*
 Affects Flags: S Z

MODE           SYNTAX       HEX LEN TIM
Immediate     LDA #$44      $A9  2   2
Zero Page     LDA $44       $A5  2   3
Zero Page,X   LDA $44,X     $B5  2   4
>Absolute      LDA $4400     $AD  3   4
>Absolute,X    LDA $4400,X   $BD  3   4+
>Absolute,Y    LDA $4400,Y   $B9  3   4+
Indirect,X    LDA ($44,X)   $A1  2   6
Indirect,Y    LDA ($44),Y   $B1  2   5+

+ add 1 cycle if page boundary crossed

		 */
		switch(mode)
		{
			case ABSOLUTE: // LDA $4400
				accumulator = memory.readByte( pc++ );
				cycles = 2;
				break;
			case ABSOLUTE_INDEXED_X: // LDA $44,X
				final short base = memory.readWord( pc );
				pc += 2;
				int sum = (base+x) & 0xffff;
				accumulator = memory.readByte( sum );
				break;
			case ABSOLUTE_INDEXED_Y: // LDA $4400, Y
				final short base2 = memory.readWord( pc );
				pc += 2;
				sum = (base2 + y) & 0xffff;
				accumulator = memory.readByte( sum );
				break;
			case IMMEDIATE:
				accumulator = memory.readByte( pc++ );
				cycles = 2;
				break;
			case ZERO_PAGE:
				accumulator = memory.readByte( memory.readByte( pc++ ) );
				cycles = 3;
				break;
			case INDEXED_INDIRECT_X:
				break;
			case ZERO_PAGE_X:
				final short base3 = memory.readByte( pc++ );
				sum = (base3 + x) & 0xff;
				accumulator = memory.readByte( sum );
				cycles = 4;
				break;
			case INDIRECT_INDEXED_Y:
				final short base4 = memory.readByte( pc++ );
				sum = (base4 + y) & 0xff;
				accumulator = memory.readByte( sum );
				break;
			default:
				throw new InvalidOpcodeException("Unknown addressing mode",pc, (byte) opcode );
		}
		int toSet = 0;
		if ( accumulator == 0 ) {
			toSet = Flag.ZERO.set( toSet );
		}
		if ( (accumulator & 1 <<7 ) != 0 ) {
			toSet = Flag.NEGATIVE.set( toSet );
		}
		this.flags |= toSet;
		return cycles;
	}

	private byte loadValue(AdrMode mode) {
		switch(mode)
		{
			case ABSOLUTE:
				return memory.readByte( pc++ );
			case ABSOLUTE_INDEXED_X:
				final short base = memory.readWord( pc );
				pc += 2;
				int sum = (base+x) & 0xffff;
				return memory.readByte( sum );
			case ABSOLUTE_INDEXED_Y:
				final short base2 = memory.readWord( pc );
				pc += 2;
				sum = (base2 + y) & 0xffff;
				return memory.readByte( sum );
			case IMMEDIATE:
				return memory.readByte( pc++ );
			case ZERO_PAGE:
				return memory.readByte( memory.readByte( pc++ ) );
			case INDEXED_INDIRECT_X:
				final short base3 = memory.readByte( pc++ );
				sum = (base3+x) & 0xffff;
				final int adr = memory.readWord( sum );
				return memory.readByte( adr );
			case ZERO_PAGE_X:
				final short base4 = memory.readByte( pc++ );
				sum = (base4 + x) & 0xff;
				return memory.readByte( sum );
			case INDIRECT_INDEXED_Y:
				final short base5 = memory.readByte( pc++ );
				sum = (base5 + y) & 0xff;
				return memory.readByte( sum );
			default:
				throw new RuntimeException("Interal error,unhandled addressing mode: "+mode);
		}
	}

	private int handleSBC(int opcode,AdrMode mode) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("handleSBC not implemented yet");
	}

	private int handleCMP(int opcode,AdrMode mode) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("handleCMP not implemented yet");
	}

	private int handleSTA(int opcode,AdrMode mode) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("handleSTA not implemented yet");
	}

	private int handleADC(int opcode,AdrMode mode) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("handleADC not implemented yet");
	}

	private int handleEOR(int opcode,AdrMode mode) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("handleEOR not implemented yet");
	}

	private int handleAND(int opcode,AdrMode mode) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("handleAND not implemented yet");
	}

	private int handleORA(int opcode,AdrMode mode) {
		// TODO Auto-generated method stub
		throw new UnsupportedOperationException("handleORA not implemented yet");
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
		.append( "PC: ").append( HexDump.toHexBigEndian( (short) pc ) )
		.append("    ")
		.append( flagBuffer )
		.append("    ")
		.append("A: ").append( HexDump.toHex( (byte) accumulator ) )
		.append("    ")
		.append("X: ").append( HexDump.toHex( (byte) x ) )
		.append("    ")
		.append("Y: ").append( HexDump.toHex( (byte) y ) )
		.append("    ")
		.append("SP: ").append( HexDump.toHex( (byte) accumulator ) );

		return buffer.toString();
	}

	public Set<Flag> getFlags() {
		return Arrays.stream( Flag.values() ).filter( f -> f.isSet( this.flags ) ).collect(Collectors.toSet());
	}
}
