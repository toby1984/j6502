package de.codesourcery.j6502.parser.ast;

public class AbsoluteOperand extends ASTNode {

	@Override
	public String toString() {
		return child(0).toString();
	}
}
