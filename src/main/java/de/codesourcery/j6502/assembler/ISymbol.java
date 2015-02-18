package de.codesourcery.j6502.assembler;

import de.codesourcery.j6502.assembler.parser.Identifier;

public interface ISymbol<T> {

	public static enum Type {
		LABEL;
	}

	public Identifier getIdentifier();

	public Identifier getParentIdentifier();

	public T getValue();

	public void setValue(T value);

	/**
	 * Whether this symbol has a valid value.
	 * @return
	 */
	public boolean hasValue();

	public Type getType();

	public boolean hasType(Type t);
}
