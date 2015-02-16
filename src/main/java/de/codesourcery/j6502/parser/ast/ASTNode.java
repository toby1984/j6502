package de.codesourcery.j6502.parser.ast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public abstract class ASTNode implements Iterable<ASTNode> {

	public final List<ASTNode> children = new ArrayList<>();
	public ASTNode parent;

	public ASTNode() {
	}

	@Override
	public Iterator<ASTNode> iterator() {
		return children.iterator();
	}

	public boolean hasChildren() {
		return ! children.isEmpty();
	}

	public ASTNode child(int idx) {
		return children.get(idx);
	}

	public int getChildCount() {
		return children.size();
	}

	public boolean hasNoChildren() {
		return children.isEmpty();
	}

	public void addChild(ASTNode child)
	{
		this.children.add( child );
		child.setParent(this);
	}

	public void setParent(ASTNode parent) {
		this.parent = parent;
	}
}
