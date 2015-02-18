package de.codesourcery.j6502.assembler.parser.ast;


public class AbsoluteOperand extends ASTNode {

	public AbsoluteOperand() {
		super(null);
	}

	@Override
	public String toString() {
		return child(0).toString();
	}
}
