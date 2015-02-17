package de.codesourcery.j6502.assembler;

import de.codesourcery.j6502.parser.ast.InstructionNode;

public class BranchTargetOutOfRangeException extends RuntimeException {

	public BranchTargetOutOfRangeException(InstructionNode node,int offendingOffset)
	{
		super("Branch target out-of-range: "+node+" , actual offset: "+offendingOffset);
	}
}
