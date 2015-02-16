package de.codesourcery.j6502.parser.ast;

public class ImmediateOperand extends ASTNode {

	@Override
	public String toString() {
		return "#"+child(0).toString();
	}
}
