package de.codesourcery.j6502.parser;

import de.codesourcery.j6502.assembler.AddressingMode;
import de.codesourcery.j6502.assembler.BranchTargetOutOfRangeException;
import de.codesourcery.j6502.assembler.ICompilationContext;
import de.codesourcery.j6502.assembler.InvalidAddressingModeException;
import de.codesourcery.j6502.parser.ast.IASTNode;
import de.codesourcery.j6502.parser.ast.IndirectOperand;
import de.codesourcery.j6502.parser.ast.InstructionNode;
import de.codesourcery.j6502.parser.ast.NumericValue;

public enum Opcode
{
	// generic #1
	LDA("LDA")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b101 ); }

	},
	STA("STA")
	{
		@Override
		public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			if ( ins.getAddressingMode() == AddressingMode.IMMEDIATE ) // STA #$xx makes no sense
			{
				throw new InvalidAddressingModeException( ins );
			}
			assembleGeneric1(ins,writer, 0b100 );
		}
	},
	ORA("ORA")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b000 ); }
	},
	AND("AND")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b001 ); }
	},
	EOR("EOR")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b010 ); }
	},
	ADC("ADC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b011 ); }
	},
	CMP("CMP")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b110 ); }
	},
	SBC("SBC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b111 ); }
	},
	// generic #2
	ASL("ASL")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			if ( ins.getAddressingMode() == AddressingMode.IMMEDIATE ) // ASL #$xx makes no sense
			{
				throw new InvalidAddressingModeException( ins );
			}
			assembleGeneric2(ins,writer, 0b000 );
		}
	},
	ROL("ROL")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			if ( ins.getAddressingMode() == AddressingMode.IMMEDIATE ) // ROL #$xx makes no sense
			{
				throw new InvalidAddressingModeException( ins );
			}
			assembleGeneric2(ins,writer, 0b001 );
		}
	},
	LSR("LSR")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			if ( ins.getAddressingMode() == AddressingMode.IMMEDIATE ) // LSR #$xx makes no sense
			{
				throw new InvalidAddressingModeException( ins );
			}
			assembleGeneric2(ins,writer, 0b010 );
		}
	},
	ROR("ROR")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			if ( ins.getAddressingMode() == AddressingMode.IMMEDIATE ) // ROR #$xx makes no sense
			{
				throw new InvalidAddressingModeException( ins );
			}
			assembleGeneric2(ins,writer, 0b011 );
		}
	},
	STX("STX")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case ABSOLUTE:
				case ZERO_PAGE:
				case ZERO_PAGE_Y:
					assembleGeneric2(ins,writer, 0b100 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}
		}
	},
	LDX("LDX")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case IMPLIED:
				case ZERO_PAGE_X:
				case ABSOLUTE_INDEXED_X:
				case INDEXED_INDIRECT_X:
				case INDIRECT_INDEXED_Y:
					throw new InvalidAddressingModeException( ins );
				default:
					// $$FALL-THROUGH$$
			}
			assembleGeneric2(ins,writer, 0b101 );
		}
	},
	DEC("DEC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case ABSOLUTE:
				case ABSOLUTE_INDEXED_X:
				case ZERO_PAGE:
				case ZERO_PAGE_X:
					assembleGeneric2(ins,writer, 0b110 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}
		}
	},
	INC("INC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case ABSOLUTE:
				case ABSOLUTE_INDEXED_X:
				case ZERO_PAGE:
				case ZERO_PAGE_X:
					assembleGeneric2(ins,writer, 0b111 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}
		}
	},
	// generic #3
	BIT("BIT")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case ABSOLUTE:
				case ZERO_PAGE:
					assembleGeneric3(ins,writer, 0b001 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}

		}
	},
	JMP("JMP")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			/*
			 * Indirect      JMP ($5597)   $6C  3   5
			 * Absolute      JMP $5597     $4C  3   3
			 */
			if ( ins.hasChildren() )
			{
				final IASTNode child0 = ins.child(0);
				final IASTNode child1 = ins.getChildCount() > 1 ? ins.child(1) : null;
				if ( child0 != null && child0 instanceof IndirectOperand && child1 == null ) { // JMP ($xxxx)
					writer.writeByte( (byte) 0x6c );
					writer.writeWord( child0.child(0) );
					return;
				}
				switch ( ins.getAddressingMode() )
				{
					case ABSOLUTE:
					case ZERO_PAGE:
						writer.writeByte( (byte) 0x4c );
						writer.writeWord( child0.child(0) );
						return;
					default:
						// $$FALL-THROUGH$$
				}
			}
			throw new InvalidAddressingModeException( ins );

		}
	},
	STY("STY")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case ZERO_PAGE:
				case ABSOLUTE:
				case ZERO_PAGE_X:
					assembleGeneric3(ins,writer, 0b100 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}
		}
	},
	LDY("LDY")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case IMMEDIATE:
				case ZERO_PAGE:
				case ZERO_PAGE_X:
				case ABSOLUTE:
				case ABSOLUTE_INDEXED_X:
					assembleGeneric3(ins,writer, 0b101 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}

		}
	},
	CPY("CPY")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case IMMEDIATE:
				case ZERO_PAGE:
				case ABSOLUTE:
					assembleGeneric3(ins,writer, 0b110 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}

		}
	},
	CPX("CPX")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() )
			{
				case IMMEDIATE:
				case ZERO_PAGE:
				case ABSOLUTE:
					assembleGeneric3(ins,writer, 0b111 );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}

		}
	},
	/* Conditional branches.
	 *
	 * The conditional branch instructions all have the form xxy10000
	 * with xx being the flag under test and y being the flag's expected value (either set or not set).
     *
     * xx	flag
     * 00	negative
     * 01	overflow
     * 10	carry
     * 11	zero
     *
	 * BPL	BMI	BVC	BVS	BCC	BCS BNE	BEQ
	 * 10	30	50	70	90	B0 	D0	F0
     */
	BPL("BPL")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0x10 ); }
	},
	BMI("BMI")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0x30 ); }
	},
	BVC("BVC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0x50 ); }
	},
	BVS("BVS")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0x70 ); }
	},
	BCC("BCC")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0x90 ); }
	},
	BCS("BCS")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0xb0 ); }
	},
	BNE("BNE")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0xd0 ); }
	},
	BEQ("BEQ")
	{
		@Override public void assemble(InstructionNode ins, ICompilationContext writer) { assembleConditionalBranch( ins , writer , (byte) 0xf0 ); }
	},
	BRK("BRK") {

		@Override
		public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			if ( ins.getAddressingMode() != AddressingMode.IMPLIED ) {
				throw new InvalidAddressingModeException( ins );
			}
			writer.writeByte( (byte) 0x00 );
		}
	},
	JSR("JSR") {

		@Override
		public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			switch( ins.getAddressingMode() ) {
				case ABSOLUTE:
				case ZERO_PAGE:
					writer.writeByte( (byte) 0x20 );
					writer.writeWord( getWordValue( ins.child(0).child(0) ) );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}

		}
	},
	RTI("RTI" , (byte) 0x40 ),
	RTS("RTS" , (byte) 0x60 ),
	PHP("PHP",(byte) 0x08),
	PLP("PLP",(byte) 0x28),
	PHA("PHA",(byte) 0x48),
	PLA("PLA",(byte) 0x68),
	DEY("DEY",(byte) 0x88),
	TAY("TAY",(byte) 0xa8),
	INY("INY",(byte) 0xc8),
	INX("INX",(byte) 0xe8),
	CLC("CLC",(byte) 0x18),
	SEC("SEC",(byte) 0x38),
	CLI("CLI",(byte) 0x58),
	SEI("SEI",(byte) 0x78),
	TYA("TYA",(byte) 0x98),
	CLV("CLV",(byte) 0xb8),
	CLD("CLD",(byte) 0xd8),
	SED("SED",(byte) 0xf8),
	TXA("TXA",(byte) 0x8a),
	TXS("TXS",(byte) 0x9a),
	TAX("TAX",(byte) 0xaa),
	TSX("TSX",(byte) 0xba),
	DEX("DEX",(byte) 0xca),
	NOP("NOP",(byte) 0xea );

	private final String mnemonic;
	private final byte opcode;

	private Opcode(String mnemonic) {
		this.mnemonic = mnemonic.toUpperCase();
		this.opcode = 0;
	}

	private Opcode(String mnemonic,byte opcode) {
		this.mnemonic = mnemonic.toUpperCase();
		this.opcode = opcode;
	}

	private static void assembleConditionalBranch(InstructionNode ins,ICompilationContext writer,byte opcode)
	{
		if ( ins.getAddressingMode() != AddressingMode.ABSOLUTE && ins.getAddressingMode() != AddressingMode.ZERO_PAGE) // ASL #$xx makes no sense
		{
			throw new InvalidAddressingModeException( ins );
		}
		final byte relOffset = getRelativeOffset( ins , writer );
		writer.writeByte( opcode  );
		writer.writeByte( relOffset );
	}

	private static byte getRelativeOffset(InstructionNode ins, ICompilationContext writer) {
		final int relOffset = getWordValue( ins.child(0).child(0) ) - writer.getCurrentAddress();
		if( relOffset < -128 || relOffset > 127 ) {
			throw new BranchTargetOutOfRangeException(ins, relOffset);
		}
		return (byte) relOffset;
	}

	public String getMnemonic() {
		return mnemonic;
	}

	public static Opcode getOpcode(String s)
	{
		final String up = s.toUpperCase();
		for ( final Opcode op : values() ) {
			if ( op.mnemonic.equals( up ) ) {
				return op;
			}
		}
		return null;
	}

	@Override
	public String toString() {
		return mnemonic;
	}

	public static boolean isZeroPage(short value) {
		return value >= -128 && value <= 127;
	}

	private static void assembleGeneric1(InstructionNode ins, ICompilationContext writer,int aaa)
	{
		final int cc = 0b01;
		/*
		 * pattern:  aaabbbcc
		 *
		 * where cc = 01
		 *
		 * and bbb:
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
		final IASTNode child0 = ins.child(0);
		int opCode=0;
		switch( ins.getAddressingMode() )
		{
			case ABSOLUTE:
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute      LDA $4400     $AD  3   4
				opCode = (aaa << 5) | ( 0b011 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400
				writer.writeWord( child0.child(0) );
				break;
			case ABSOLUTE_INDEXED_X:
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute,X    LDA $4400,X   $BD  3   4+
				opCode = (aaa << 5) | ( 0b111 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400,X
				writer.writeWord( child0.child(0) );
				break;
			case ABSOLUTE_INDEXED_Y:
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute,Y      LDA $4400,Y   $B9  3   4+
				opCode = (aaa << 5) | ( 0b110 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400,Y
				writer.writeWord( child0.child(0) );
				break;
			case ZERO_PAGE_X:
				// MODE           SYNTAX         HEX LEN TIM
				// Zero Page,X   LDA $44,X     $B5  2   4
				opCode = (aaa << 5) | ( 0b101 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $44,X
				writer.writeByte( child0.child(0) );
				break;
			case IMMEDIATE:
				// MODE           SYNTAX         HEX LEN TIM
				// # Immediate     LDA #$44      $A9  2   2
				opCode = (aaa << 5) | ( 0b010 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA #$44
				writer.writeByte( child0.child(0) );
				break;
			case INDEXED_INDIRECT_X:
				// MODE           SYNTAX         HEX LEN TIM
				// Indirect,X      LDA ($44,X)   $A1  2   6
				opCode = (aaa << 5) | ( 0b000 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA ($44,X)
				writer.writeByte( child0.child(0) );
				break;
			case INDIRECT_INDEXED_Y:
				// MODE           SYNTAX         HEX LEN TIM
				// Indirect,Y      LDA ($44),Y   $B1  2   5+
				opCode = (aaa << 5) | ( 0b100 << 2) | cc;
				writer.writeByte( (byte) opCode );
				writer.writeByte( child0.child(0) ); // LDA ($44),Y
				break;
			case ZERO_PAGE:
				// MODE           SYNTAX         HEX LEN TIM
				// Zero Page     LDA $44       $A5  2   3		#
				opCode = (aaa << 5) | ( 0b001 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $44
				writer.writeByte( child0.child(0) );
				break;
			default:
				throw new InvalidAddressingModeException( ins );
		}
	}

	private static void assembleGeneric2(InstructionNode ins, ICompilationContext writer,int aaa)
	{
		final int cc = 0b10;
		/*
		 * pattern:  aaabbbcc
		 *
		 * where cc = 10
		 *
		 * and bbb:
		 *
		 * bbb	addressing mode
		 * 000	#immediate     OK
		 * 001	zero page      OK
		 * 010	accumulator    OK
		 * 011	absolute       OK
		 * 101	zero page,X    OK
		 * 111	absolute,X     OK
		 */
		int opCode=0;
		if ( ins.getAddressingMode() == AddressingMode.IMPLIED ) {
			// MODE           SYNTAX         HEX LEN TIM
			// Absolute      ASL             $AD  3   4
			opCode = (aaa << 5) | ( 0b010 << 2) | cc;
			writer.writeByte( (byte) opCode ); // LDA $4400
			return;
		}

		final IASTNode child0 = ins.child(0);
		switch( ins.getAddressingMode() )
		{
			case IMMEDIATE: // OK
				// MODE           SYNTAX         HEX LEN TIM
				// # Immediate     LDA #$44      $A9  2   2
				opCode = (aaa << 5) | ( 0b000 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA #$44
				writer.writeByte( child0.child(0) );
				break;
			case ZERO_PAGE: // OK
				// MODE           SYNTAX         HEX LEN TIM
				// Zero Page     LDA $44       $A5  2   3		#
				opCode = (aaa << 5) | ( 0b001 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $44
				writer.writeByte( child0.child(0) );
				break;
			case ABSOLUTE: // OK
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute      LDA $4400     $AD  3   4
				opCode = (aaa << 5) | ( 0b011 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400
				writer.writeWord( child0.child(0) );
				break;
			case ZERO_PAGE_Y:
				if ( ins.opcode != Opcode.LDX && ins.opcode != Opcode.STX ) {
					throw new InvalidAddressingModeException( ins );
				}
			case ZERO_PAGE_X: // OK
				// MODE           SYNTAX         HEX LEN TIM
				// Zero Page,X   LDA $44,X     $B5  2   4
				opCode = (aaa << 5) | ( 0b101 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $44,X
				writer.writeByte( child0.child(0) );
				break;
			case ABSOLUTE_INDEXED_Y: // OK
				if ( ins.opcode != Opcode.LDX && ins.opcode != Opcode.STX ) {
					throw new InvalidAddressingModeException( ins );
				}
			case ABSOLUTE_INDEXED_X: // OK
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute,X    LDA $4400,X   $BD  3   4+
				opCode = (aaa << 5) | ( 0b111 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400,X
				writer.writeWord( child0.child(0) );
				break;
			default:
				throw new InvalidAddressingModeException( ins );
		}
	}

	private static void assembleGeneric3(InstructionNode ins, ICompilationContext writer,int aaa)
	{
		final int cc = 0b00;
		/*
		 * pattern:  aaabbbcc
		 *
		 * where cc = 00
		 *
		 * and bbb:
		 *
		 * bbb	addressing mode
		 * 000	#immediate
		 * 001	zero page
		 * 011	absolute
		 * 101	zero page,X
		 * 111	absolute,X
		 */
		final IASTNode child0 = ins.child(0);
		int opCode=0;
		switch( ins.getAddressingMode() )
		{
			case IMMEDIATE:
				// MODE           SYNTAX         HEX LEN TIM
				// # Immediate     LDA #$44      $A9  2   2
				opCode = (aaa << 5) | ( 0b000 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA #$44
				writer.writeByte( child0.child(0) );
				break;
			case ZERO_PAGE:
				// MODE           SYNTAX         HEX LEN TIM
				// Zero Page     LDA $44       $A5  2   3		#
				opCode = (aaa << 5) | ( 0b001 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $44
				writer.writeByte( child0.child(0) );
				break;
			case ABSOLUTE:
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute      LDA $4400     $AD  3   4
				opCode = (aaa << 5) | ( 0b011 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400
				writer.writeWord( child0.child(0) );
				break;
			case ZERO_PAGE_X:
				// MODE           SYNTAX         HEX LEN TIM
				// Zero Page,X   LDA $44,X     $B5  2   4
				opCode = (aaa << 5) | ( 0b101 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $44,X
				writer.writeByte( child0.child(0) );
				break;
			case ABSOLUTE_INDEXED_X:
				// MODE           SYNTAX         HEX LEN TIM
				// Absolute,X    LDA $4400,X   $BD  3   4+
				opCode = (aaa << 5) | ( 0b111 << 2) | cc;
				writer.writeByte( (byte) opCode ); // LDA $4400,X
				writer.writeWord( child0.child(0) );
				break;
			default:
				throw new InvalidAddressingModeException( ins );
		}
	}

	public static byte getByteValue(IASTNode node)
	{
		final NumericValue lit = (NumericValue) node;
		if ( ! lit.isValueAvailable() ) {
			return (byte) 0xff;
		}
		return lit.getByteValue();
	}

	public static short getWordValue(IASTNode node)
	{
		final NumericValue lit = (NumericValue) node;
		if ( ! lit.isValueAvailable() ) {
			return (short) 0xffff;
		}
		return lit.getWordValue();
	}

	public void assemble(InstructionNode ins, ICompilationContext writer)
	{
		if ( ins.getAddressingMode() != AddressingMode.IMPLIED ) {
			throw new InvalidAddressingModeException( ins );
		}
		writer.writeByte( opcode );
	}
}
