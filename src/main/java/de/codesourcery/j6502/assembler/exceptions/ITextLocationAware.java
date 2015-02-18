package de.codesourcery.j6502.assembler.exceptions;

import de.codesourcery.j6502.utils.SourceHelper;
import de.codesourcery.j6502.utils.SourceHelper.TextLocation;

public interface ITextLocationAware {

	public TextLocation getTextLocation(SourceHelper helper);
}
