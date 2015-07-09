package de.codesourcery.j6502.assembler.parser.ast;

import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.assembler.parser.Identifier;
import de.codesourcery.j6502.utils.ITextRegion;

// TODO: Currently unused AST node, may be used for invoking named,parameterized macros later on
public class FunctionCallNode extends ASTNode
{
	public final Identifier identifier;

	public FunctionCallNode(Identifier identifier,ITextRegion region)
	{
		super(region);
		Validate.notNull(identifier, "identifier must not be NULL");
		this.identifier = identifier;
	}
}
