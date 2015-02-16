package de.codesourcery.j6502.parser.ast;

public class LabelNode extends ASTNode
{
	public final Identifier identifier;
	public final LabelNode parentIdentifier;

	public LabelNode(Identifier id) {
		this(id,null);
	}

	public LabelNode(Identifier id,LabelNode parentLabel) {
		this.identifier = id;
		this.parentIdentifier = parentLabel;
	}

	public boolean isLocal() {
		return parentIdentifier != null;
	}

	public boolean isGlobal() {
		return parentIdentifier == null;
	}

	@Override
	public String toString()
	{
		return isLocal() ? "."+identifier.value : identifier.value+":";
	}
}
