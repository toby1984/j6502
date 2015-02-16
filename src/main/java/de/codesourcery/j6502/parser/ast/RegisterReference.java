package de.codesourcery.j6502.parser.ast;

import de.codesourcery.j6502.parser.Register;

public class RegisterReference extends ASTNode {

	public final Register register;

	public RegisterReference(Register register) {
		this.register = register;
	}

	@Override
	public String toString() {
		return register.name();
	}
}
