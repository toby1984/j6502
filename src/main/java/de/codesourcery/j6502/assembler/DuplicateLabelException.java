package de.codesourcery.j6502.assembler;

import de.codesourcery.j6502.parser.ast.LabelNode;

public class DuplicateLabelException extends RuntimeException {

	public final LabelNode label;

	public DuplicateLabelException(LabelNode label) {
		super("Duplicate "+(label.isGlobal()?"global":"local")+" label "+label.identifier);
		this.label = label;
	}
}
