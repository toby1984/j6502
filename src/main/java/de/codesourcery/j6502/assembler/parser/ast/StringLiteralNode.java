package de.codesourcery.j6502.assembler.parser.ast;

import de.codesourcery.j6502.utils.ITextRegion;

public class StringLiteralNode extends ASTNode
{
    public final String value;
    
    public StringLiteralNode(String value,ITextRegion region) {
        super(region);
        this.value=value;
    }
}