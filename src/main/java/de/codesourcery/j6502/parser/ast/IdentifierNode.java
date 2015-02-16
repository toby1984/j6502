package de.codesourcery.j6502.parser.ast;

import java.util.regex.Pattern;

public class IdentifierNode extends ASTNode {

	private static final Pattern PATTERN =Pattern.compile("^[_a-zA-Z]{1}[a-zA-Z0-9]+[_0-9a-zA-Z]*");

	public final Identifier identifier;

	public IdentifierNode(Identifier id) {
		this.identifier = id;
	}

	@Override
	public String toString() {
		return identifier.value;
	}
}
