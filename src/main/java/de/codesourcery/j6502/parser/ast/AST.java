package de.codesourcery.j6502.parser.ast;

import org.apache.commons.lang.StringUtils;

public class AST extends ASTNode {

	@Override
	public String toString()
	{
		return StringUtils.join( children , "\n" );
	}
}
