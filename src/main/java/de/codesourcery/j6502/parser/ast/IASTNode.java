package de.codesourcery.j6502.parser.ast;

import java.util.Iterator;
import java.util.function.Consumer;

public interface  IASTNode extends Iterable<IASTNode> {

	@Override
	public Iterator<IASTNode> iterator();

	public boolean hasChildren();

	public IASTNode child(int idx);

	public int getChildCount();

	public boolean hasNoChildren();

	public void addChild(IASTNode child);

	public void setParent(IASTNode parent);

	public void visitBreadthFirst(Consumer<IASTNode> visitor);

}