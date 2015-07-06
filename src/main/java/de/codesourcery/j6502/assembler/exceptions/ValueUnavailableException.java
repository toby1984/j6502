package de.codesourcery.j6502.assembler.exceptions;

import de.codesourcery.j6502.assembler.parser.ast.IValueNode;

public class ValueUnavailableException extends ParseException {

	public final IValueNode valueNode;

	public ValueUnavailableException(IValueNode valueNode) 
	{
		super("Failed to evaluate "+valueNode,valueNode.getTextRegion());
		this.valueNode = valueNode;
	}
}
