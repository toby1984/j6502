package de.codesourcery.j6502.assembler.parser;

import java.util.Stack;

import de.codesourcery.j6502.assembler.exceptions.ParseException;
import de.codesourcery.j6502.assembler.parser.ExpressionToken.ExpressionTokenType;
import de.codesourcery.j6502.assembler.parser.ast.ExpressionNode;
import de.codesourcery.j6502.assembler.parser.ast.IASTNode;
import de.codesourcery.j6502.assembler.parser.ast.OperatorNode;
import de.codesourcery.j6502.utils.TextRegion;

/**
 * This class implements Dijkstra's 'Shunting yard' algorithm
 * with additional support for variable-argument functions (shamelessly stolen from
 * http://www.kallisti.net.nz/blog/2008/02/extension-to-the-shunting-yard-algorithm-to-allow-variable-numbers-of-arguments-to-functions/).
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class ShuntingYard
{
    private final Stack<ExpressionToken> valueQueue = new Stack<>();
    private final Stack<ExpressionToken> stack = new Stack<ExpressionToken>();

    private final Stack<Integer> argsCountStack = new Stack<Integer>();
    private final Stack<Boolean> argsMarkerStack = new Stack<Boolean>();

    public boolean expectingValue = true;

    public ShuntingYard() {
    }

    public boolean isFunctionOnStack()
    {
        for ( final ExpressionToken t : this.stack ) {
            if ( t.isFunction() ) {
                return true;
            }
        }
        return false;
    }

    public boolean isEmpty() {
        return this.valueQueue.isEmpty() && this.stack.isEmpty();
    }

    public void pushValue(IASTNode node)
    {
    	this.expectingValue = false;
        output( new ExpressionToken(ExpressionTokenType.VALUE , node ) );
        // If the were values stack has a value on it, pop it and push true
        if ( ! this.argsMarkerStack.isEmpty() ) {
            this.argsMarkerStack.pop();
            this.argsMarkerStack.push( Boolean.TRUE );
        }
    }

    /*
    [X] Read a token.
    [X] If the token is a number, then add it to the output queue.
    [X] If the token is a function token, then push it onto the stack.
    [X] If the token is a left parenthesis, then push it onto the stack.

    [X] If the token is a function argument separator (e.g., a comma):

        Until the token at the top of the stack is a left parenthesis, pop operators off the stack onto the output queue. If no left parentheses are encountered, either the separator was misplaced or parentheses were mismatched.

    [X] If the token is an operator, o1, then:

        while there is an operator token, o2, at the top of the stack, and

                either o1 is left-associative and its precedence is less than or equal to that of o2,
                or o1 has precedence less than that of o2,

            pop o2 off the stack, onto the output queue;

        push o1 onto the stack.


    [X] If the token is a right parenthesis:

        Until the token at the top of the stack is a left parenthesis, pop operators off the stack onto the output queue.
        Pop the left parenthesis from the stack, but not onto the output queue.
        If the token at the top of the stack is a function token, pop it onto the output queue.
        If the stack runs out without finding a left parenthesis, then there are mismatched parentheses.

When there are no more tokens to read:

    While there are still operator tokens in the stack:

        If the operator token on the top of the stack is a parenthesis, then there are mismatched parentheses.
        Pop the operator onto the output queue.

     */

    public void pushOperator(ExpressionToken tok1)
    {
    	if ( this.expectingValue && tok1.getType() == ExpressionTokenType.OPERATOR )
    	{
    		final OperatorNode op = (OperatorNode) tok1.getNode();
    		if ( op.operator == Operator.BINARY_MINUS ) {
    			op.setType( Operator.UNARY_MINUS );
    		} else if ( op.operator == Operator.BINARY_PLUS ) {
    			op.setType( Operator.UNARY_PLUS );
    		}
    	}
    	
        if ( tok1.isParensClose() )
        {
            if ( ! popUntil(ExpressionTokenType.PARENS_OPEN ) ) {
                throw new ParseException("Mismatched closing parens",tok1.getTextRegion());
            }

            final ExpressionToken openingParens = this.stack.pop(); // pop opening parens

            // If the token at the top of the stack is a function token, pop it onto the output queue.
            if ( ! this.stack.isEmpty() && this.stack.peek().hasType(ExpressionTokenType.FUNCTION) )
            {
                output(this.stack.pop());
            }
            else {
                // not a function invocation, wrap in ExpressionNode
                final ExpressionToken pop = this.valueQueue.pop();
                final ExpressionNode expr = new ExpressionNode( openingParens.getTextRegion() );
                expr.mergeRegion( tok1.getTextRegion() );
                expr.insertChild( 0 , pop.getNode() );
                this.valueQueue.push( new ExpressionToken( ExpressionTokenType.EXPRESSION , expr ) );
            }
            return;
        }    	
    	
    	this.expectingValue = true; // TODO: Does not properly handle postfix operators

        if ( tok1.isParensOpen() )
        {
            this.stack.push(tok1);
            return;
        }
        
        if ( tok1.isFunction() )
        {
            // Push 0 onto the arg count stack. If the were values stack has a value on it, pop it and push true. Push false onto were values.
            this.argsCountStack.push(Integer.valueOf(0));
            if ( ! this.argsMarkerStack.isEmpty() )
            {
                this.argsMarkerStack.pop();
                this.argsMarkerStack.push( Boolean.TRUE );
            }
            this.argsMarkerStack.push(Boolean.FALSE);
            this.stack.push(tok1);
            return;
        }
        if ( tok1.isArgumentDelimiter() )
        {
            if ( ! isFunctionOnStack() ) {
                throw new ParseException("Unexpected argument delimiter",tok1.getTextRegion());
            }
            popUntil(ExpressionTokenType.PARENS_OPEN );

            // Pop were values into w. If w is true, pop arg count into a, increment a and push back into arg count. Push false into were values.
            if ( this.argsMarkerStack.pop() ) {
                this.argsCountStack.push(this.argsCountStack.pop() +  1 );
            }
            this.argsMarkerStack.push(Boolean.FALSE);
            return;
        }

        /* If the token is an operator, o1, then:
         *
         *    while there is an operator token, o2, at the top of the stack, and
         *
         *            either o1 is left-associative and its precedence is less than or equal to that of o2,
         *            or o1 has precedence less than that of o2,
         *
         *        pop o2 off the stack, onto the output queue;
         *
         *    push o1 onto the stack.
         */
        if ( tok1.isOperator() )
        {
            final Operator o1 = ((OperatorNode) tok1.getNode()).operator;

            while ( ! this.stack.isEmpty() && this.stack.peek().isOperator() )
            {
                final ExpressionToken tok2 = this.stack.peek();
                final Operator o2 = ((OperatorNode) tok2.getNode()).operator;
                if ( ( o1.isLeftAssociative() && o1.getPrecedence() <= o2.getPrecedence() ) ||
                        o1.getPrecedence() < o2.getPrecedence() )
                {
                    output( this.stack.pop() );
                } else {
                    break;
                }
            }

            this.stack.push( tok1 );
            return;
        }
        throw new ParseException("Unreachable code reached while processing "+tok1,tok1.getTextRegion());
    }

    private boolean popUntil(ExpressionTokenType type)
    {
        boolean found = false;
        while( true )
        {
            if ( this.stack.isEmpty() ) {
                break;
            }
            if ( this.stack.peek().hasType( type ) ) {
                found = true;
                break;
            }
            output( this.stack.pop() );
        }

        return found;
    }

    private void output(ExpressionToken tok)
    {
        if ( tok.isParensOpen() ) {
            throw new ParseException("No matching closing parens",tok.getTextRegion());
        }

        if ( tok.isValue() )
        {
            this.valueQueue.add( tok );
            return;
        }

        if ( tok.isFunction() )
        {
            /* Pop stack into f
             * Pop arg count into a
             * Pop were values into w
             * If w is true, increment a
             * Set the argument count of f to a
             * Push f onto output queue
             */
            int argCount = this.argsCountStack.pop();
            if ( this.argsMarkerStack.pop() ) {
                argCount+=1;
            }

            while ( argCount > 0 ) {
                tok.getNode().insertChild( 0 , this.valueQueue.pop().getNode() );
                argCount--;
            }

            this.valueQueue.push( tok );
            return;
        }

        if ( tok.isOperator() )
        {
            while( ! tok.hasAllOperands() )
            {
                if ( this.valueQueue.isEmpty() ) {
                    throw new ParseException("Operator "+tok.getNode()+" lacks operand",tok.getTextRegion());
                }
                tok.getNode().insertChild( 0 , this.valueQueue.pop().getNode() );
            }
            this.valueQueue.push( tok );
            return;
        }
        throw new ParseException("Unreachable code reached while outputting "+tok,tok.getTextRegion());
    }

    public IASTNode getResult(TextRegion currentParseOffset)
    {
        while ( ! this.stack.isEmpty() ) {
            output( this.stack.pop() );
        }

        if ( this.valueQueue.isEmpty() ) {
        	return null;
        }

        if ( this.valueQueue.size() != 1 )
        {
            if ( this.valueQueue.size() > 1 )
            {
                boolean onlyValues = true;
                for ( final ExpressionToken tok : this.valueQueue ) {
                    if ( ! tok.isValue() ) {
                        onlyValues = false;
                    }
                }
                if ( onlyValues ) {
                    throw new ParseException("Values without operator ? "+this.valueQueue, this.valueQueue.peek().getTextRegion() );
                }
            }
            throw new ParseException("Internal error,output queue has size "+this.valueQueue.size()+" , expected size: 1", currentParseOffset );
        }

        final ExpressionToken token = this.valueQueue.pop();
		final IASTNode result = token.getNode();
        if ( result == null ) {
            throw new ParseException("Internal error, ExpressionToken "+token+" has no associated node ?",currentParseOffset);
        }
        return result;
    }
}