package de.codesourcery.j6502.assembler.parser.ast;

import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.assembler.parser.Operator;
import de.codesourcery.j6502.utils.ITextRegion;

public class OperatorNode extends ASTNode implements IValueNode
{
	public Operator operator;

	public OperatorNode(Operator op,ITextRegion region)
	{
		super(region);
		Validate.notNull(op, "op must not be NULL");
		this.operator = op;
	}
	
	@Override
	public String toString() 
	{
	    if ( operator.operandCount != getChildCount() ) {
	        return "Operator: "+operator.symbol+" (expected "+operator.operandCount+" children but got "+getChildCount()+")";
	    }
	    if ( operator.operandCount == 2) 
	    {
	        return child(0).toString()+operator.symbol+child(1).toString();
	    }
	    if ( operator.isLeftAssociative() ) {
	        return child(0).toString()+operator.symbol;
	    } 
        return operator.symbol+child(0).toString();
	}

	@Override
	public int evaluate() {

		if ( ! hasAllOperands() ) {
			throw new IllegalStateException("Internal error, node "+this+" requires "+operator.operandCount+" operands but has "+getChildCount());
		}

		switch ( operator.operandCount )
		{
			case 1:
				int v1 = ((IValueNode) child(0)).getWordValue() & 0xffff;
				return operator.evaluate( v1 );
			case 2:
				int va = ((IValueNode) child(0)).getWordValue() & 0xffff;
				int vb = ((IValueNode) child(1)).getWordValue() & 0xffff;
				return operator.evaluate( va, vb );
			default:
				throw new RuntimeException("Internal error,unhandled operand count "+operator.operandCount);
		}
	}

	public void setType(Operator op)
	{
		Validate.notNull(op, "op must not be NULL");
		this.operator = op;
	}

	public boolean hasAllOperands()
	{
		return getChildCount() == operator.operandCount;
	}

	@Override
	public byte getByteValue() {
		return (byte) evaluate();
	}

	@Override
	public short getWordValue() {
		return (short) evaluate();
	}

	@Override
	public boolean isValueAvailable()
	{
		if ( ! hasAllOperands() ) {
			throw new RuntimeException("Internal error, node "+this+" requires "+operator.operandCount+" operands but has "+getChildCount());
		}

		switch ( operator.operandCount )
		{
			case 1:
				return ((IValueNode) child(0)).isValueAvailable();
			case 2:
				return ((IValueNode) child(0)).isValueAvailable() && ((IValueNode) child(1)).isValueAvailable();
			default:
				throw new RuntimeException("Internal error,unhandled operand count "+operator.operandCount);
		}
	}

}
