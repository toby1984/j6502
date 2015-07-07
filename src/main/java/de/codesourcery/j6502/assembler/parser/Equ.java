package de.codesourcery.j6502.assembler.parser;

import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.assembler.ISymbol;

public class Equ implements ISymbol<Integer> {

	private Identifier identifier;
	private Integer value;
	
	public Equ(Identifier identifier) 
	{
		Validate.notNull(identifier, "identifier must not be NULL");
		this.identifier = identifier;
	}
	
	@Override
	public Identifier getIdentifier() {
		return identifier;
	}

	@Override
	public Identifier getParentIdentifier() {
		return null;
	}

	@Override
	public Integer getValue() {
		return value;
	}

	@Override
	public void setValue(Integer value) {
		this.value = value;
	}

	@Override
	public boolean hasValue() {
		return value != null;
	}

	@Override
	public de.codesourcery.j6502.assembler.ISymbol.Type getType() {
		return Type.EQU;
	}

	@Override
	public boolean hasType(de.codesourcery.j6502.assembler.ISymbol.Type t) {
		return t.equals( getType() );
	}
}