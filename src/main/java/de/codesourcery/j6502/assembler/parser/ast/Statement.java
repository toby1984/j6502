package de.codesourcery.j6502.assembler.parser.ast;

import org.apache.commons.lang.StringUtils;

public class Statement extends ASTNode {

	public Statement() {
		super(null);
	}

	@Override
	public String toString()
	{
		return StringUtils.join( children , " " );
	}
}
