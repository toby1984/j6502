package de.codesourcery.j6502.disassembler;

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.j6502.utils.HexDump;

public class Disassembler
{
	protected static final String EMPTY_STRING = "";

	private byte[] data;

	private final HexDump hexdump = new HexDump();

	private int previousOffset;
	private int currentOffset;
	private int bytesLeft ;
	private final List<Line> lines = new ArrayList<>();

	private boolean writeAddresses = true;
	private boolean annotate = false;

	private int baseAddressToPrint;

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

			if ( writeAddresses )
			{
				if ( arguments == EMPTY_STRING ) {
					return HexDump.toHexBigEndian( address )+":    "+instruction+comment;
				}
				return HexDump.toHexBigEndian( address )+":    "+instruction+"  "+arguments+comment;
			}
			if ( arguments == EMPTY_STRING ) {
				return instruction+comment;
			}
			return instruction+"  "+arguments+comment;
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

	public void setAnnotate(boolean annotate) {
		this.annotate = annotate;
	}

	public void setWriteAddresses(boolean writeAddresses) {
		this.writeAddresses = writeAddresses;
	}

	public List<Line> disassemble(int baseAdrToPrint,byte[] data,int offset,int len)
	{
//		this.hexdump.setPrintAddress( false );
		this.hexdump.setBytesPerLine(3);

		this.baseAddressToPrint = baseAdrToPrint;
		lines.clear();
		this.data = data;
		this.currentOffset = offset;
		this.previousOffset = currentOffset;
		this.bytesLeft = len;
		while ( bytesLeft > 0 )
		{
			disassembleLine();
			this.previousOffset = currentOffset;
		}
		return new ArrayList<>(lines);
	}

	private void disassembleLine()
	{
		int value = readByte();

		value = value & 0xff;

		// mixed bag
		switch( value )
		{
			case 0x00:
			case 0x20:
			case 0x40:
			case 0x60:
			case 0x08:
			case 0x28:
			case 0x48:
			case 0x68:
			case 0x88:
			case 0xa8:
			case 0xc8:
			case 0xe8:
			case 0x18:
			case 0x38:
			case 0x58:
			case 0x78:
			case 0x98:
			case 0xb8:
			case 0xd8:
			case 0xf8:
			case 0x8a:
			case 0x9a:
			case 0xaa:
			case 0xba:
			case 0xca:
			case 0xea:
				disassemblyMixedBag( (byte) value );
				return;
			default:
				// $$FALL-THROUGH$$
		}

		// branch instructions
		switch( value ) {
			case 0x10:
			case 0x30:
			case 0x50:
			case 0x70:
			case 0x90:
			case 0xB0:
			case 0xD0:
			case 0xF0:
				disassembleConditionalBranch((byte) value);
				return;
			default:
				// $$FALL-THROUGH$$
		}

		final int cc = (value & 0b11);
		if ( cc == 0b01 )
		{
			disassembleGeneric1((byte) value);
			return;
		}
		if ( cc == 0b10 ) {
			disassembleGeneric2((byte) value);
			return;
		}
		if ( cc == 0b00 ) {
			disassembleGeneric3((byte) value);
			return;
		}
		disassemblyMixedBag( (byte) value );
	}

	private void disassemblyMixedBag(byte opcode)
	{
		final int offset = currentOffset-1;
		int op = opcode;
		op = opcode & 0xff;

		/*
BRK	JSR abs	RTI	RTS
00	20	    40	60

(JSR is the only absolute-addressing instruction that doesn't fit the aaabbbcc pattern.)

Other single-byte instructions:
PHP 	PLP 	PHA 	PLA 	DEY 	TAY 	INY 	INX
08 	     28 	48 	     68 	88 	    A8 	    C8 	    E8

CLC 	SEC 	CLI 	SEI 	TYA 	CLV 	CLD 	SED
18 	    38 	    58 	    78 	    98 	    B8 	     D8 	F8

TXA 	TXS 	TAX 	TSX 	DEX 	NOP
8A 	    9A 	    AA 	    BA 	    CA 	    EA
		 */
		final String ins;
		switch( op )
		{
			case 0x00: ins="BRK" ; break;
			case 0x20: // JSR is the only absolute-addressing instruction that doesn't fit the aaabbbcc pattern.
				if ( ! assertTwoBytesAvailable( offset , opcode) ) {
					return;
				}
				final String args = "$"+HexDump.toHexBigEndian( readWord() );
				addLine( (short) offset , "JSR",args);
				return;
			case 0x40: ins="RTI" ; break;
			case 0x60: ins="RTS" ; break;
			case 0x08: ins="PHP" ; break;
			case 0x28: ins="PLP" ; break;
			case 0x48: ins="PHA" ; break;
			case 0x68: ins="PLA" ; break;
			case 0x88: ins="DEY" ; break;
			case 0xa8: ins="TAY" ; break;
			case 0xc8: ins="INY" ; break;
			case 0xe8: ins="INX" ; break;
			//
			case 0x18: ins="CLC" ; break;
			case 0x38: ins="SEC" ; break;
			case 0x58: ins="CLI" ; break;
			case 0x78: ins="SEI" ; break;
			case 0x98: ins="TYA" ; break;
			case 0xb8: ins="CLV" ; break;
			case 0xd8: ins="CLD" ; break;
			case 0xf8: ins="SED" ; break;
			//
			case 0x8a: ins="TXA" ; break;
			case 0x9a: ins="TXS" ; break;
			case 0xaa: ins="TAX" ; break;
			case 0xba: ins="TSX" ; break;
			case 0xca: ins="DEX" ; break;
			case 0xea: ins="NOP" ; break;
			default:
				unknownOpcode( offset , opcode );
				return;
		}
		addLine( (short) offset , ins );
	}

	private void disassembleConditionalBranch(byte opcode)
	{
		final int opcodeAddress = currentOffset-1;
		/*
		 * Pattern is: xxy10000
xx	flag
00	negative
01	overflow
10	carry
11	zero
		 */

		final int flag = (opcode >> 6) & 0b11;
		final boolean branchOnSet = (opcode & 100000) != 0;

		String ins;
		switch(flag) {
			case 0b00: // negative
				ins = branchOnSet ? "BMI" : "BPL";
				break;
			case 0b01: // overflow
				ins = branchOnSet ? "BVS" : "BVC";
				break;
			case 0b10: // carry
				ins = branchOnSet ? "BCS" : "BCC";
				break;
			case 0b11:
				ins = branchOnSet ? "BEQ" : "BNE";
				break;
			default:
				unknownOpcode( currentOffset-1, opcode );
				return;
		}
		if ( ! assertOneByteAvailable( opcodeAddress , opcode ) ) {
			return;
		}
		final int offset = readByte();
		final int branchTarget = opcodeAddress  + offset;
		final String args = "$"+HexDump.toHexBigEndian( (short) branchTarget );
		addLine( (short) opcodeAddress , ins , args );
	}

	private void addLine(short address,String instruction)
	{
		final int adr = baseAddressToPrint + address;
		String comment = EMPTY_STRING;
		if ( annotate ) {
			comment = "; "+hexdump.dump( (short) adr , data , previousOffset , currentOffset - previousOffset );
		}
		lines.add( new Line( (short) adr , instruction , comment ) );
	}

	private void addLine(short address,String instruction,String arguments)
	{
		final int adr = baseAddressToPrint + address;
		String comment = EMPTY_STRING;
		if ( annotate ) {
			comment = "; "+hexdump.dump( (short) adr , data , previousOffset , currentOffset - previousOffset );
		}
		lines.add( new Line( (short) adr , instruction , arguments , comment ) );
	}

	private void unknownOpcode(int address , byte value)
	{
		appendByte( (short) address , value );
	}

	private void appendByte(int address, byte value)
	{
		addLine( (short) address , ".byte $"+HexDump.toHex( value ) );

	}

	private void disassembleGeneric1(byte opcode)
	{
		final int opcodeAddress = currentOffset-1;

		final int instruction = (opcode & 0b11100000) >> 5;
		final int addressingMode = (opcode & 0b11100) >> 2;

		/*
         * And the addressing mode (bbb) bits:
         *
         * bbb	addressing mode
         * 000	(zero page,X)
         * 001	zero page
         * 010	#immediate
         * 011	absolute
         * 100	(zero page),Y
         * 101	zero page,X
         * 110	absolute,Y
         * 111	absolute,X
		 */
		final String ins;
		switch ( instruction )
		{
			case 0b000: ins="ORA"; break;
			case 0b001: ins="AND"; break;
			case 0b010: ins="EOR"; break;
			case 0b011: ins="ADC"; break;
			case 0b100: ins="STA"; break;
			case 0b101: ins="LDA"; break;
			case 0b110: ins="CMP"; break;
			case 0b111: ins="SBC"; break;
			default:
				unknownOpcode( currentOffset-1 , opcode);
				return;
		}

		final String args;
		switch( addressingMode )
		{
			case 0b000: //	(zero page,X)
				if ( ! assertOneByteAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "($"+HexDump.toHex( readByte() )+" , X)";
				break;
			case 0b001: //	zero page
				if ( ! assertOneByteAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "$"+HexDump.toHex( readByte() );
				break;
			case 0b010: //	#immediate
				if ( "STA".equals( ins ) || bytesLeft < 1 ) {
					appendByte( currentOffset-1 , opcode );
					return;
				}
				args = "#$"+HexDump.toHex( readByte() );
				break;
			case 0b011: //	absolute
				if ( ! assertTwoBytesAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "$"+HexDump.toHexBigEndian( readWord() );
				break;
			case 0b100: //	(zero page),Y
				if ( ! assertOneByteAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "($"+HexDump.toHex( readByte() )+") , Y";
				break;
			case 0b101: //	zero page,X
				if ( ! assertOneByteAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "$"+HexDump.toHex( readByte() )+" , X";
				break;
			case 0b110: //	absolute,Y
				if ( ! assertTwoBytesAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "$"+HexDump.toHexBigEndian( readWord() )+" , Y";
				break;
			case 0b111: //	absolute,X
				if ( ! assertTwoBytesAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "$"+HexDump.toHexBigEndian( readWord() )+" , X";
				break;
			default:
				appendByte( currentOffset-1 , opcode );
				return;
		}
		addLine( (short) opcodeAddress , ins , args );
	}

	private void disassembleGeneric2(byte opcode)
	{
		final int opcodeAddress = currentOffset-1;

		final int instruction = (opcode & 0b11100000) >> 5;
		final int addressingMode = (opcode & 0b11100) >> 2;

		final String ins;
		switch ( instruction )
		{
			case 0b000: ins="ASL"; break;
			case 0b001: ins="ROL"; break;
			case 0b010: ins="LSR"; break;
			case 0b011: ins="ROR"; break;
			case 0b100: ins="STX"; break;
			case 0b101: ins="LDX"; break;
			case 0b110: ins="DEC"; break;
			case 0b111: ins="INC"; break;
			default:
				unknownOpcode( currentOffset-1 , opcode);
				return;
		}

		/* pattern:  aaabbbcc
		 *
		 * where cc = 10
		 *
		 * and bbb:
		 *
         * bbb	addressing mode
         * 000	#immediate
         * 001	zero page
         * 010	accumulator
         * 011	absolute
         * 101	zero page,X
         * 111	absolute,X
		 */
		final String args;
		switch( addressingMode )
		{
			case 0b000: //	#immediate
				if ( ! "LDX".equals( ins ) || bytesLeft < 1 ) {
					appendByte( currentOffset-1 , opcode );
					return;
				}
				args = "#$"+HexDump.toHex( readByte() );
				break;
			case 0b001: //	zero page
				if ( ! assertOneByteAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "$"+HexDump.toHex( readByte() );
				break;
			case 0b010: //	ACCU / implied
				if ( "STX".equals( ins ) || "LDX".equals( ins ) || "DEC".equals( ins ) || "INC".equals( ins ) ) {
					appendByte( currentOffset-1 , opcode );
					return;
				}
				args = EMPTY_STRING;
				break;
			case 0b011: //	absolute
				if ( ! assertTwoBytesAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "$"+HexDump.toHexBigEndian( readWord() );
				break;
			case 0b101: //	zero page,X
				if ( ! assertOneByteAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				// with STX and LDX, "zero page,X" addressing becomes "zero page,Y"
				if ( "LDX".equals( ins ) || "STX".equals( ins ) ) {
					args = "$"+HexDump.toHex( readByte() )+" , Y";
				} else {
					args = "$"+HexDump.toHex( readByte() )+" , X";
				}
				break;
			case 0b111: //	absolute,X

				if ( "STX".equals( ins ) ) {
					appendByte( currentOffset-1 , opcode );
					return;
				}
				if ( ! assertTwoBytesAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				// with LDX, "absolute,X" becomes "absolute,Y".
				if ( "LDX".equals( ins ) ) {
					args = "$"+HexDump.toHexBigEndian( readWord() )+" , Y";
				} else {
					args = "$"+HexDump.toHexBigEndian( readWord() )+" , X";
				}
				break;
			default:
				appendByte( currentOffset-1 , opcode );
				return;
		}
		addLine( (short) opcodeAddress , ins , args );
	}

	private void disassembleGeneric3(byte opcode) {

		/*
         * the cc = 00 instructions. Again, the opcodes are different:
         * aaa	opcode
         * 001	BIT
         * 010	JMP
         * 011	JMP (abs)
         * 100	STY
         * 101	LDY
         * 110	CPY
         * 111	CPX
		 */

		final int opcodeAddress = currentOffset-1;

		final int instruction = (opcode & 0b11100000) >> 5;
		final int addressingMode = (opcode & 0b11100) >> 2;

		final String ins;
		switch ( instruction )
		{
			case 0b001: ins="BIT"; break;
			case 0b010: ins="JMP"; break;
			case 0b011:
				ins="JMP";
				if ( ! assertTwoBytesAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				final String args = "($"+HexDump.toHexBigEndian( readWord() )+")";
				addLine( (short) opcodeAddress , ins , args );
				return;
			case 0b100: ins="STY"; break;
			case 0b101: ins="LDY"; break;
			case 0b110: ins="CPY"; break;
			case 0b111: ins="CPX"; break;
			default:
				unknownOpcode( currentOffset-1 , opcode);
				return;
		}

		/*
bbb	addressing mode
000	#immediate
001	zero page
011	absolute
101	zero page,X
111	absolute,X
		 */

		final String args;
		switch( addressingMode )
		{
			case 0b000: //	#immediate
				if ( isUnsupportedInstruction( ins , opcode , "BIT" , "JMP" , "STY") || ! assertOneByteAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "#$"+HexDump.toHex( readByte() );
				break;
			case 0b001: //	zero page
				if ( isUnsupportedInstruction( ins , opcode , "JMP") || ! assertOneByteAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "$"+HexDump.toHex( readByte() );
				break;
			case 0b011: //	absolute
				if ( ! assertTwoBytesAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "$"+HexDump.toHexBigEndian( readWord() );
				break;
			case 0b101: //	zero page,X
				if ( ! ("STY".equals(ins) || "LDY".equals(ins ) ) ) {
					appendByte( currentOffset-1 , opcode );
					return;
				}
				if ( ! assertOneByteAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "$"+HexDump.toHex( readByte() )+" , X";
				break;
			case 0b111: //	absolute,X
				if ( ! "LDY".equals( ins ) ) {
					appendByte( currentOffset -1 , opcode );
					return;
				}
				if ( ! assertTwoBytesAvailable( opcodeAddress , opcode ) ) {
					return;
				}
				args = "$"+HexDump.toHexBigEndian( readWord() )+" , X";
				break;
			default:
				appendByte( currentOffset-1 , opcode );
				return;
		}
		addLine( (short) opcodeAddress , ins , args );
	}

	private boolean isUnsupportedInstruction(String actualIns,byte opcode, String in1,String... other)
	{
		final List<String> all = new ArrayList<>();
		all.add( in1.toLowerCase() );
		if ( other != null )
		{
			for ( final String s : other ) {
				all.add( s.toLowerCase() );
			}
		}
		final String lowerIns = actualIns.toLowerCase();
		if ( all.stream().anyMatch( s -> s.equals( lowerIns ) ) ) {
			appendByte( currentOffset-1 , opcode );
			return true;
		}
		return false;
	}

	private boolean assertTwoBytesAvailable(int address , byte opcode )
	{
		if ( bytesLeft < 2 )
		{
			appendByte( (short) address , opcode );
			int adr = address+1;
			while ( bytesLeft > 0 )
			{
				appendByte( adr++ , readByte() );
			}
			return false;
		}
		return true;
	}

	private boolean assertOneByteAvailable(int address,byte opcode)
	{
		if ( bytesLeft < 1 )
		{
			appendByte( (short) address , opcode );
			return false;
		}
		return true;
	}

	private byte readByte()
	{
		bytesLeft--;
		return data[currentOffset++];
	}

	private short readWord()
	{
		int lo = readByte();
		lo = lo & 0xff;
		int hi = readByte();
		hi = hi & 0xff;
		final int result = (hi << 8 | lo);
		return (short) (result & 0xffff);
	}
}