package de.codesourcery.j6502.assembler.parser.ast;

import de.codesourcery.j6502.utils.ITextRegion;

public class ExpressionNode extends ASTNode implements IValueNode {

	public ExpressionNode(ITextRegion region) {
		super(region);
	}

	@Override
	public byte getByteValue()
	{
		return (byte) evaluate();
	}

	@Override
	public int evaluate()
	{
		if ( getChildCount() != 1 ) {
			throw new IllegalStateException("No value available , node needs to have exactly one child");
		}
		return ((IValueNode) child(0)).evaluate();
	}

	@Override
	public short getWordValue() {
		return (short) evaluate();
	}

	@Override
	public boolean isValueAvailable() {
		return hasChildren();
	}
}