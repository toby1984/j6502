package de.codesourcery.j6502.assembler.exceptions;

import de.codesourcery.j6502.assembler.parser.ast.IValueNode;

public class ValueUnavailableException extends RuntimeException {

	public final IValueNode valueNode;

	public ValueUnavailableException(IValueNode valueNode) {
		super("Failed to evaluate "+valueNode);
		this.valueNode = valueNode;
	}
}
