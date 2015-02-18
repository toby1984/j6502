package de.codesourcery.j6502.assembler;

import de.codesourcery.j6502.assembler.parser.Identifier;

public final class Label implements ISymbol<Integer>
{
	private final Identifier name;
	private final Identifier parentName;
	private Integer address;

	public Label(Identifier name) {
		if (name == null) {
			throw new IllegalArgumentException("name must not be NULL");
		}
		this.name = name;
		this.parentName = null;
	}

	@Override
	public String toString() {
		return isGlobal() ? "global label '"+name.value+"'" : "local label '"+name.value+"' , child of '"+parentName.value+"'";
	}

	public Label(Identifier name,Identifier parentName) {
		if ( name == null ) {
			throw new IllegalArgumentException("name must not be NULL");
		}
		if ( parentName == null ) {
			throw new IllegalArgumentException("parentName must not be NULL");
		}
		this.name = name;
		this.parentName = parentName;
	}

	public boolean isLocal() {
		return parentName != null;
	}

	public boolean isGlobal() {
		return parentName == null;
	}

	@Override
	public Identifier getIdentifier() {
		return name;
	}

	@Override
	public Identifier getParentIdentifier() {
		return parentName;
	}

	@Override
	public Integer getValue() {
		return address;
	}

	@Override
	public void setValue(Integer address) {
		this.address = address;
	}

	@Override
	public boolean hasValue() {
		return address != null;
	}

	@Override
	public Type getType() {
		return Type.LABEL;
	}

	@Override
	public boolean hasType(Type t) {
		return t.equals( getType() );
	}
}