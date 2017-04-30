package de.codesourcery.j6502.assembler.parser;

import de.codesourcery.j6502.assembler.AddressingMode;
import de.codesourcery.j6502.assembler.ICompilationContext;
import de.codesourcery.j6502.assembler.exceptions.BranchTargetOutOfRangeException;
import de.codesourcery.j6502.assembler.exceptions.InvalidAddressingModeException;
import de.codesourcery.j6502.assembler.parser.ast.IASTNode;
import de.codesourcery.j6502.assembler.parser.ast.IValueNode;
import de.codesourcery.j6502.assembler.parser.ast.IndirectOperand;
import de.codesourcery.j6502.assembler.parser.ast.InstructionNode;

public enum Opcode
{
	/* Immediate       LDA #$A5       $A9      2     2
     * Zero Page       LDA $A5        $A5      2     3
     * Zero Page,X     LDA $A5,X      $B5      2     4
     * Absolute        LDA $A5B6      $AD      3     4
     * Absolute,X      LDA $A5B6,X    $BD      3     4+
     * Absolute,Y      LDA $A5B6,Y    $B9      3     4+
     * (Indirect,X)    LDA ($A5,X)    $A1      2     6
     * (Indirect),Y    LDA ($A5),Y    $B1      2     5+
	 */
	LDA("LDA")
	{
		@Override
		public void assemble(InstructionNode ins, ICompilationContext writer) { assembleGeneric1(ins,writer, 0b101 ); }
	},
	/* Zero Page       STA $A5        $85      2     3
     * Zero Page,X     STA $A5,X      $95      2     4
     * Absolute        STA $A5B6      $8D      3     4
     * Absolute,X      STA $A5B6,X    $9D      3     5
     * Absolute,Y      STA $A5B6,Y    $99      3     5
     * (Indirect,X)    STA ($A5,X)    $81      2     6
     * (Indirect),Y    STA ($A5),Y    $91      2     6
	 */
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
	/* Immediate       ADC #$A5       $69      2     2
     * Zero Page       ADC $A5        $65      2     3
     * Zero Page,X     ADC $A5,X      $75      2     4
     * Absolute        ADC $A5B6      $6D      3     4
     * Absolute,X      ADC $A5B6,X    $7D      3     4+
     * Absolute,Y      ADC $A5B6,Y    $79      3     4+
     * (Indirect,X)    ADC ($A5,X)    $61      2     6
     * (Indirect),Y    ADC ($A5),Y    $71      2     5+
	 */
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
	/*
  Accumulator     ROL A          $2A      1    2
  Zero Page       ROL $A5        $26      2    5
  Zero Page,X     ROL $A5,X      $36      2    6
  Absolute        ROL $A5B6      $2E      3    6
  Absolute,X      ROL $A5B6,X    $3E      3    7
	 */
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
	NOP("NOP",(byte) 0xea ),
	// ======================
	// !!! ILLEGAL OPCODES !!!
	// =======================

	SKW("SKW") // skip word
	{
	    /* see http://www.ffd2.com/fridge/docs/6502-NMOS.extra.opcodes
	     *
         * SKW skips next word (two bytes).
         * Opcodes: 0C, 1C, 3C, 5C, 7C, DC, FC.
         * Takes 4 cycles to execute.
         *
         * To be dizzyingly precise, SKW actually performs a read operation.  It's
         * just that the value read is not stored in any register.  Further, opcode 0C
         * uses the absolute addressing mode.
         * The two bytes which follow it form the
         * absolute address.  All the other SKW opcodes use the absolute indexed X
         * addressing mode.  If a page boundary is crossed, the execution time of one
         * of these SKW opcodes is upped to 5 clock cycles.
	     */

		@Override
		public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			writer.writeByte( (byte) 0x0c );
		}
	},
	/*
	 * See http://www.retro-programming.de/?page_id=2248
	 *
	 * AXS: Akku AND X-Register+Stored to memory (alternatives Mnemonic: SAX)
     * AXS absolut ($8F, 3B, 4T, <keine>)
     *
     * AXS funktioniert so: Die Inhalte von Akku und X-Register werden UND-Verknüpft, aber OHNE eines der beiden Register zu ändern!
     * Das Ergbnis wird dann an der angegebenen Adresse abgelegt. Die Flags im Statusregister (SR) bleiben ebenfalls unverändert!
     *
     * Wollte man das mit normalen Befehlen nachbilden, dann bräuchte man eine ganze Menge davon:
     *
	 * Adressierung | OpCode | Bytes | TZ
     * absolut      |  $8F   |   3   |  4
     * Zero-Page    |  $87   |   2   |  3
     * Zero-Page,Y  |  $97   |   2   |  4
     * indirekt X   |  $83   |   2   |  6
	 */
	AXS("AXS")
	{
		// TODO: Maybe add assembler support ??

		@Override
		public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			final IASTNode child0 = ins.child(0);
			switch( ins.getAddressingMode() )
			{
				case ABSOLUTE: // 0x8f
					writer.writeByte( (byte) 0x8f );
					writer.writeWord( child0.child(0) );
					break;
				case INDEXED_INDIRECT_X: // $83
					writer.writeByte( (byte) 0x83 );
					writer.writeByte( child0.child(0) );
					break;
				case ZERO_PAGE: // $87
					writer.writeByte( (byte) 0x87 );
					writer.writeByte( child0.child(0) );
					break;
				case ZERO_PAGE_Y: // $97
					writer.writeByte( (byte) 0x97 );
					writer.writeByte( child0.child(0) );
					break;
				default:
					throw new RuntimeException("AXS does not support addressing mode "+ins.getAddressingMode());
			}
		}
	},
	/*
	 * Illegal opcode, see http://www.ffd2.com/fridge/docs/6502-NMOS.extra.opcodes
	 *
	 * HLT crashes the microprocessor.  When this opcode is executed, program
	 * execution ceases.  No hardware interrupts will execute either.  The author
	 * has characterized this instruction as a halt instruction since this is the
	 * most straightforward explanation for this opcode's behaviour.  Only a reset
	 * will restart execution.  This opcode leaves no trace of any operation
	 * performed!  No registers affected.
	 */
	HLT("HLT",(byte) 0x02)
	{
		@Override
		public void assemble(InstructionNode ins, ICompilationContext writer)
		{
			writer.writeByte( (byte) 0x02 );
		}
	};

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

	private static byte getRelativeOffset(InstructionNode ins, ICompilationContext writer)
	{
		final IValueNode lit = (IValueNode) ins.child(0).child(0);
		if ( ! lit.isValueAvailable() ) {
			return 0;
		}

		final int relOffset = (lit.getWordValue() & 0xffff) - writer.getCurrentAddress()-2; // -2 because branching is done relative to the END of the branch instruction

		if( relOffset > 255 || relOffset < -128 ) {
			throw new BranchTargetOutOfRangeException(ins, relOffset);
		}
		return (byte) relOffset;
	}

	public String getMnemonic() {
		return mnemonic;
	}

	public static boolean isValidOpcode(String s) {
		return s != null && getOpcode(s) != null;
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

	public static boolean isZeroPage(int value) {
		value = value & 0xffff;
		return (value & 0xff00) == 0;
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
		AddressingMode addressingMode = ins.getAddressingMode();
		switch( addressingMode )
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
			case ZERO_PAGE_Y:
			case ZERO_PAGE_X:
				// MODE           SYNTAX         HEX LEN TIM
				// Zero Page,X   LDA $44,X     $B5  2   4
				opCode = (aaa << 5) | ( 0b101 << 2) | cc;
				if ( ins.opcode == Opcode.STA && addressingMode == AddressingMode.ZERO_PAGE_Y) {
					writer.writeByte( (byte) 0x99 ); // LDA $44,X
					writer.writeWord( child0.child(0) );
				} else {
					writer.writeByte( (byte) opCode ); // LDA $44,X
					writer.writeByte( child0.child(0) );
				}
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
		final IValueNode lit = (IValueNode) node;
		if ( ! lit.isValueAvailable() ) {
			return (byte) 0xff;
		}
		return lit.getByteValue();
	}

	public static short getWordValue(IASTNode node)
	{
		final IValueNode lit = (IValueNode) node;
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