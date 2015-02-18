package de.codesourcery.j6502.assembler.parser.ast;


public interface IValueNode extends IASTNode
{
	public byte getByteValue();

	public short getWordValue();

	/**
	 * Returns whether this node has all information required to calculate it's value.
	 * @return
	 */
	public boolean isValueAvailable();
}
