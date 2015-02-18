package de.codesourcery.j6502.assembler.parser.ast;


public class ImmediateOperand extends ASTNode {

	public ImmediateOperand() {
		super(null);
	}

	@Override
	public String toString() {
		return "#"+child(0).toString();
	}
}
