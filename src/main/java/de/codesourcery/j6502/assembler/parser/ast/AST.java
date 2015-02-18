package de.codesourcery.j6502.assembler.parser.ast;

import org.apache.commons.lang.StringUtils;

public class AST extends ASTNode {

	public AST() {
		super(null);
	}

	@Override
	public String toString()
	{
		return StringUtils.join( children , "\n" );
	}
}
