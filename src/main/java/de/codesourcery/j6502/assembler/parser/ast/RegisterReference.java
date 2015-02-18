package de.codesourcery.j6502.assembler.parser.ast;

import de.codesourcery.j6502.assembler.parser.Register;
import de.codesourcery.j6502.utils.ITextRegion;

public class RegisterReference extends ASTNode {

	public final Register register;

	public RegisterReference(Register register,ITextRegion region) {
		super(region);
		this.register = register;
	}

	@Override
	public String toString() {
		return register.name();
	}
}
