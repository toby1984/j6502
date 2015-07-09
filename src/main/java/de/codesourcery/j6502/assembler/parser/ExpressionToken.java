package de.codesourcery.j6502.assembler.parser;

import de.codesourcery.j6502.assembler.exceptions.ParseException;
import de.codesourcery.j6502.assembler.parser.ast.FunctionCallNode;
import de.codesourcery.j6502.assembler.parser.ast.IASTNode;
import de.codesourcery.j6502.assembler.parser.ast.OperatorNode;
import de.codesourcery.j6502.utils.ITextRegion;
import de.codesourcery.j6502.utils.TextRegion;

/**
 * Helper class used by {@link ShuntingYard} to keep track of parsed tokens.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class ExpressionToken {

    private final ITextRegion textRegion;
    private final ExpressionTokenType type;
    private final IASTNode token;

    public static enum ExpressionTokenType {
        FUNCTION,
        ARGUMENT_DELIMITER,
        OPERATOR,
        PARENS_OPEN,
        PARENS_CLOSE,
        EXPRESSION,
        VALUE;
    }

    public ExpressionToken(ExpressionTokenType type,TextRegion region)
    {
        this.type = type;
        this.token = null;
        this.textRegion = region;
    }

    public ExpressionToken(ExpressionTokenType type,Token token)
    {
        this.type = type;
        this.token = null;
        this.textRegion = token.region();
    }

    public ExpressionToken(ExpressionTokenType type, IASTNode token)
    {
        this.type = type;
        this.token = token;
        this.textRegion = token.getTextRegion();
    }

    public ITextRegion getTextRegion()
    {
        return this.textRegion;
    }

    @Override
    public String toString()
    {
        switch( this.type ) {
            case ARGUMENT_DELIMITER:
                return ",";
            case FUNCTION:
           		return ((FunctionCallNode) getNode()).identifier.value;
            case OPERATOR:
                return ((OperatorNode) getNode()).operator.toString();
            case PARENS_CLOSE:
                return ")";
            case PARENS_OPEN:
                return "(";
            case VALUE:
                return getNode().toString();
            default:
                return this.type+" | "+this.token;
        }
    }

    public boolean isArgumentDelimiter() {
        return hasType(ExpressionTokenType.ARGUMENT_DELIMITER);
    }

    public boolean isFunction() {
        return hasType(ExpressionTokenType.FUNCTION);
    }

    public boolean isOperator() {
        return hasType(ExpressionTokenType.OPERATOR);
    }

    public boolean hasAllOperands() {
        if ( ! isOperator() ) {
            throw new ParseException("hasAllOperands() invoked on something that's not an operator: "+this , this.textRegion );
        }
        return ((OperatorNode) getNode()).hasAllOperands();
    }

    public boolean isLeftAssociative()
    {
        if ( ! isOperator() ) {
            throw new ParseException("isLeftAssociative() invoked on something that's not an operator: "+this, this.textRegion);
        }
        return ((OperatorNode) getNode()).operator.isLeftAssociative();
    }

    public boolean isValue() {
        return hasType(ExpressionTokenType.VALUE);
    }

    public boolean isParens() {
        return isParensOpen() || isParensClose();
    }

    public boolean isParensOpen() {
        return hasType(ExpressionTokenType.PARENS_OPEN);
    }

    public boolean isParensClose() {
        return hasType(ExpressionTokenType.PARENS_CLOSE);
    }

    public IASTNode getNode()
    {
        return this.token;
    }

    public boolean hasType(ExpressionTokenType t) {
        return getType() == t;
    }

    public ExpressionTokenType getType()
    {
        return this.type;
    }
}