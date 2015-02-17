package de.codesourcery.j6502.assembler;

import de.codesourcery.j6502.parser.ast.InstructionNode;

public class InvalidAddressingModeException extends RuntimeException {

	public final InstructionNode ins;

	public InvalidAddressingModeException(InstructionNode ins) {
		super("Invalid addressing mode "+ins.getAddressingMode()+" on "+ins);
		this.ins = ins;
	}
}
