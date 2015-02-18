package de.codesourcery.j6502.assembler.parser.ast;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.function.Consumer;

import de.codesourcery.j6502.utils.ITextRegion;
import de.codesourcery.j6502.utils.TextRegion;

public abstract class ASTNode implements IASTNode {

	public final List<IASTNode> children = new ArrayList<>();

	private ITextRegion textRegion;
	public IASTNode parent;

	public ASTNode(ITextRegion region)
	{
		this.textRegion = region != null ? new TextRegion(region) : null;
	}

	@Override
	public IASTNode getParent() {
		return parent;
	}

	@Override
	public ITextRegion getTextRegion() {
		return textRegion;
	}

	@Override
	public ITextRegion getTextRegionIncludingChildren()
	{
		if ( hasNoChildren() ) {
			return textRegion;
		}
		ITextRegion result = this.textRegion == null ? null : new TextRegion(this.textRegion);
		for ( final IASTNode child : children ) {
			if ( result == null ) {
				result = child.getTextRegionIncludingChildren();
			} else {
				result.merge( child.getTextRegionIncludingChildren() );
			}
		}
		return result;
	}

	@Override
	public Iterator<IASTNode> iterator() {
		return children.iterator();
	}

	@Override
	public int indexOf(IASTNode child) {
		return children.indexOf( child );
	}

	@Override
	public void visitDepthFirst(Consumer<IASTNode> visitor)
	{
		for ( final IASTNode child : children ) {
			child.visitDepthFirst( visitor );
		}
		visitor.accept( this );
	}

	@Override
	public void visitParentFirst(Consumer<IASTNode> visitor)
	{
		visitor.accept( this );
		for ( final IASTNode child : children ) {
			child.visitParentFirst( visitor );
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
	public void addChildren(List<IASTNode> children)
	{
		for ( final IASTNode child : children) {
			addChild(child);
		}
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
