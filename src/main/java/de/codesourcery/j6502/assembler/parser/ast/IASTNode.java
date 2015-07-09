package de.codesourcery.j6502.assembler.parser.ast;

import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import de.codesourcery.j6502.utils.ITextRegion;

public interface IASTNode extends Iterable<IASTNode> {

	@Override
	public Iterator<IASTNode> iterator();

	public ITextRegion getTextRegion();

	public void mergeRegion(ITextRegion region);

	public ITextRegion getTextRegionIncludingChildren();

	public boolean hasChildren();

	public IASTNode child(int idx);

	public IASTNode getParent();

	public int indexOf(IASTNode child);

	public void addChildren(List<IASTNode> children);

	public void insertChild(int index,IASTNode child);

	public int getChildCount();

	public boolean hasNoChildren();

	public List<IASTNode> getChildren();

	public void addChild(IASTNode child);

	public void removeChild(IASTNode child);

	public void setParent(IASTNode parent);

	public void visitDepthFirst(Consumer<IASTNode> visitor);

	public void visitParentFirst(Consumer<IASTNode> visitor);

}