package de.codesourcery.j6502.assembler.parser.ast;

import de.codesourcery.j6502.utils.ITextRegion;

public class CommentNode extends ASTNode {

	public final String comment;

	public CommentNode(String comment,ITextRegion region) {
		super(region);
		this.comment = comment;
	}

	@Override
	public String toString() {
		return "; "+comment;
	}
}
