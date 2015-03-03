package de.codesourcery.j6502.assembler.parser.ast;

import de.codesourcery.j6502.assembler.parser.Identifier;
import de.codesourcery.j6502.utils.ITextRegion;

public class ConstantDefinitionNode extends ASTNode {

	public final Identifier identifier;
	
	public ConstantDefinitionNode(Identifier identifier,ITextRegion region) {
		super(region);
		this.identifier = identifier;
	}
}
