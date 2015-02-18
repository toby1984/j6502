package de.codesourcery.j6502.parser.ast;


public interface NumericValue extends IASTNode
{
	public byte getByteValue();

	public short getWordValue();

	/**
	 * Whether we have enough information to calculate the node's value.
	 * @return
	 */
	public boolean isValueAvailable();
}
