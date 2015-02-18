package de.codesourcery.j6502.assembler.exceptions;

import de.codesourcery.j6502.assembler.parser.ast.InstructionNode;
import de.codesourcery.j6502.utils.SourceHelper;
import de.codesourcery.j6502.utils.SourceHelper.TextLocation;

public class BranchTargetOutOfRangeException extends RuntimeException implements ITextLocationAware {

	public final InstructionNode node;

	public BranchTargetOutOfRangeException(InstructionNode node,int offendingOffset)
	{
		super("Branch target out-of-range: "+node+" , actual offset: "+offendingOffset);
		this.node = node;
	}

	@Override
	public TextLocation getTextLocation(SourceHelper helper) {
		return helper.getLocation( node.getTextRegion() );
	}
}
