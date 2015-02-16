package de.codesourcery.j6502.parser.ast;

import de.codesourcery.j6502.assembler.AddressingMode;
import de.codesourcery.j6502.assembler.InvalidAddressingModeException;
import de.codesourcery.j6502.parser.Opcode;
import de.codesourcery.j6502.parser.Register;

public class InstructionNode extends ASTNode
{
	public final Opcode opcode;

	public InstructionNode(Opcode opcode) {
		this.opcode = opcode;
	}

	@Override
	public String toString()
	{
		final StringBuffer buffer = new StringBuffer();
		buffer.append( opcode.getMnemonic() );
		if ( hasChildren() ) {
			buffer.append(" ");
			buffer.append( child(0).toString() );
			if ( getChildCount() > 1 ) {
				buffer.append(" , ");
				buffer.append( child(1).toString() );
			}
		}
		return buffer.toString();
	}

	public AddressingMode getAddressingMode() {
		/*
	INDIRECT_ZERO_PAGE_X, // 000	(zero page,X) OK
	ZERO_PAGE, // 001	zero page OK
	IMMEDIATE, // 010	#immediate OK
	ABSOLUTE,	// 011	absolute OK
	INDIRECT_ZERO_PAGE_Y, // 100	(zero page),Y OK
	ABSOLUTE_ZERO_PAGE_X, // 101	zero page,X OK
	ABSOLUTE_Y, // 110	absolute,Y OK
	ABSOLUTE_X; // 111	absolute,X OK
		 */
		final ASTNode child0 = child(0);
		final ASTNode child1 = getChildCount() > 1 ? child(1) : null;
		if ( child1 == null )
		{
			if ( child0 instanceof IndirectOperandX)
			{
				Opcode.getByteValue( child(0).child(0) );
				return AddressingMode.INDEXED_INDIRECT_X;
			}
			else if ( child0 instanceof IndirectOperandY)
			{
				Opcode.getByteValue( child(0).child(0) );
				return AddressingMode.INDIRECT_INDEXED_Y;
			}
			else if ( child0 instanceof ImmediateOperand )
			{
				Opcode.getByteValue( child(0).child(0) );
				return AddressingMode.IMMEDIATE;
			}
			else if ( child0 instanceof AbsoluteOperand)
			{
				final short value = Opcode.getWordValue( child0.child(0) );
				if ( Opcode.isZeroPage( value ) ) {
					return AddressingMode.ZERO_PAGE;
				}
				return AddressingMode.ABSOLUTE;
			}
		}
		else if ( child0 instanceof AbsoluteOperand)
		{
			final short value = Opcode.getWordValue( child0.child(0) );

			if ( child1 instanceof RegisterReference)
			{
				final Register reg = ((RegisterReference) child1).register;
				switch( reg )
				{
					case X:
						if ( Opcode.isZeroPage( value ) ) {
							return AddressingMode.ZERO_PAGE_X;
						}
						return AddressingMode.ABSOLUTE_INDEXED_X;
					case Y:
						return AddressingMode.ABSOLUTE_INDEXED_Y;
					default:
						// $$FALL-THROUGH$$
				}
			}
		}
		throw new InvalidAddressingModeException( this );
	}

}
