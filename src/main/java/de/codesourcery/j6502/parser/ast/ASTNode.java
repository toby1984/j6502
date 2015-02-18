package de.codesourcery.j6502.parser.ast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

public abstract class ASTNode implements IASTNode {

	public final List<IASTNode> children = new ArrayList<>();
	public IASTNode parent;

	public ASTNode() {
	}

	@Override
	public Iterator<IASTNode> iterator() {
		return children.iterator();
	}

	@Override
	public void visitBreadthFirst(Consumer<IASTNode> visitor)
	{
		visitor.accept( this );
		for ( final IASTNode child : children ) {
			child.visitBreadthFirst( visitor );
		}
	}

	@Override
	public boolean hasChildren() {
		return ! children.isEmpty();
	}

	@Override
	public IASTNode child(int idx) {
		return children.get(idx);
	}

	@Override
	public int getChildCount() {
		return children.size();
	}

	@Override
	public boolean hasNoChildren() {
		return children.isEmpty();
	}

	@Override
	public void addChild(IASTNode child)
	{
		this.children.add( child );
		child.setParent(this);
	}

	@Override
	public void setParent(IASTNode parent) {
		this.parent = parent;
	}
}
