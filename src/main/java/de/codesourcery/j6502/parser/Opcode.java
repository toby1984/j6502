package de.codesourcery.j6502.parser;

import de.codesourcery.j6502.assembler.Assembler.BufferWriter;
import de.codesourcery.j6502.assembler.InvalidAddressingModeException;
import de.codesourcery.j6502.assembler.NumberLiteralOutOfRangeException;
import de.codesourcery.j6502.parser.ast.ASTNode;
import de.codesourcery.j6502.parser.ast.InstructionNode;
import de.codesourcery.j6502.parser.ast.NumberLiteral;

public enum Opcode
{
	LDA("LDA")
	{
		@Override
		public void assemble(InstructionNode ins, BufferWriter writer)
		{
			final ASTNode child0 = ins.child(0);
			switch( ins.getAddressingMode() )
			{
				case ABSOLUTE:
					// MODE           SYNTAX         HEX LEN TIM
					// Absolute      LDA $4400     $AD  3   4
					writer.writeByte( (byte) 0xad ); // LDA $4400
					writer.writeWord( child0.child(0) );
					break;
				case ABSOLUTE_INDEXED_X:
					// MODE           SYNTAX         HEX LEN TIM
					// Absolute,X    LDA $4400,X   $BD  3   4+
					writer.writeByte( (byte) 0xbd ); // LDA $4400
					writer.writeWord( child0.child(0) );
					break;
				case ABSOLUTE_INDEXED_Y:
					// MODE           SYNTAX         HEX LEN TIM
					// Absolute,Y      LDA $4400,Y   $B9  3   4+
					writer.writeByte( (byte) 0xb9 ); // LDA $4400
					writer.writeWord( child0.child(0) );
					break;
				case ZERO_PAGE_X:
					// MODE           SYNTAX         HEX LEN TIM
					// Zero Page,X   LDA $44,X     $B5  2   4
					writer.writeByte( (byte) 0xb5 ); // LDA #$44
					writer.writeByte( child0.child(0) );
					break;
				case IMMEDIATE:
					// MODE           SYNTAX         HEX LEN TIM
					// # Immediate     LDA #$44      $A9  2   2
					writer.writeByte( (byte) 0xa9 ); // LDA #$44
					writer.writeByte( ins.child(0).child(0) );
					break;
				case INDEXED_INDIRECT_X:
					// MODE           SYNTAX         HEX LEN TIM
					// Indirect,X      LDA ($44,X)   $A1  2   6
					writer.writeByte( (byte) 0xa1 );
					writer.writeByte( ins.child(0).child(0) );
					break;
				case INDIRECT_INDEXED_Y:
					// MODE           SYNTAX         HEX LEN TIM
					// Indirect,Y      LDA ($44),Y   $B1  2   5+
					writer.writeByte( (byte) 0xb1 );
					writer.writeByte( ins.child(0).child(0) );
					break;
				case ZERO_PAGE:
					// MODE           SYNTAX         HEX LEN TIM
					// Zero Page     LDA $44       $A5  2   3		#
					writer.writeByte( (byte) 0xa5 ); // LDA $4400
					writer.writeByte( ins.child(0).child(0) );
					break;
				default:
					throw new InvalidAddressingModeException( ins );
			}
		}

	},
	STA("STA")
	{
		@Override
		public void assemble(InstructionNode ins, BufferWriter writer) {
			throw new RuntimeException("STA not implemented");
		}
	},
	ASL("ASL")	{
		@Override
		public void assemble(InstructionNode ins, BufferWriter writer) {
			throw new RuntimeException("ASL not implemented");
		}
	};

	private final String mnemonic;

	private Opcode(String mnemonic) {
		this.mnemonic = mnemonic.toUpperCase();
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

	public static byte getByteValue(ASTNode node)
	{
		final NumberLiteral lit = (NumberLiteral) node;
		if ( lit.value < -127 || lit.value > 255 )
		{
			throw NumberLiteralOutOfRangeException.byteRange( lit.value );
		}
		return (byte) lit.value;
	}

	public static boolean isZeroPage(short value) {
		return value >= -127 && value <= 255;
	}

	public static short getWordValue(ASTNode node)
	{
		final NumberLiteral lit = (NumberLiteral) node;
		if ( lit.value < -32767 || lit.value > 65535 )
		{
			throw NumberLiteralOutOfRangeException.wordRange( lit.value );
		}
		// 6502 uses little endian, swap bytes
		return lit.value;
	}

	public abstract void assemble(InstructionNode ins, BufferWriter writer);
}
