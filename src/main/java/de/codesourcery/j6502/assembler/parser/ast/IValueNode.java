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

	/**
	 *
	 * @return
	 * @throws IllegalStateException if {@link #isValueAvailable()} returned <code>false</code> and you called this method regardless
	 */
	public int evaluate() throws IllegalStateException;
}
