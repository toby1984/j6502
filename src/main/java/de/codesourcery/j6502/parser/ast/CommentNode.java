package de.codesourcery.j6502.parser.ast;

public class CommentNode extends ASTNode {

	public final String comment;

	public CommentNode(String comment) {
		this.comment = comment;
	}

	@Override
	public String toString() {
		return "; "+comment;
	}
}
