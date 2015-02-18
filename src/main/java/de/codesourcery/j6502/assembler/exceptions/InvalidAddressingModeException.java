package de.codesourcery.j6502.assembler.exceptions;

import de.codesourcery.j6502.assembler.parser.ast.InstructionNode;
import de.codesourcery.j6502.utils.SourceHelper;
import de.codesourcery.j6502.utils.SourceHelper.TextLocation;

public class InvalidAddressingModeException extends RuntimeException implements ITextLocationAware {

	public final InstructionNode ins;

	public InvalidAddressingModeException(InstructionNode ins) {
		super("Invalid addressing mode "+ins.getAddressingMode()+" on "+ins);
		this.ins = ins;
	}

	@Override
	public TextLocation getTextLocation(SourceHelper helper)
	{
		return helper.getLocation( ins.getTextRegion() );
	}

}
