package de.codesourcery.j6502.assembler.parser.ast;

/**
 * Only used for JMP ($xxxx) instructions.
 */
public class IndirectOperand extends ASTNode {
	public IndirectOperand() {
		super(null);
	}
}
