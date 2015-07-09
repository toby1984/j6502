package de.codesourcery.j6502.assembler.parser;

import java.util.ArrayList;
import java.util.List;

public enum Operator
{
	BINARY_PLUS("+",2,4) { // ok
		@Override
		public int evaluate(int op1, int op2) {
			return op1+op2;
		}
	},
	BINARY_MINUS("-",2,4) { //ok
		@Override
		public int evaluate(int op1, int op2) {
			return op1 - op2;
		}
	},
	UNARY_MINUS("-",1,6) { // ok
		@Override
		public int evaluate(int op1) {
			return -op1;
		}

		@Override
		public boolean isLeftAssociative() {
			return false;
		}

		@Override
		protected boolean canBeParsed() {
			return false;
		}
	},
	UNARY_PLUS("-",1,6) { // ok
		@Override
		public int evaluate(int op1) {
			return op1;
		}

		@Override
		public boolean isLeftAssociative() {
			return false;
		}

		@Override
		protected boolean canBeParsed() {
			return false;
		}
	},	
	BITWISE_NEGATION("~",1,6) { // ok
		@Override
		public int evaluate(int op1) {
			return ~op1;
		}
	},
	BITWISE_AND("&",2,2) // ok
	{
		@Override
		public int evaluate(int op1,int op2) {
			return op1 & op2;
		}
	},	
	BITWISE_OR("|",2,1) // ok 
	{
		@Override
		public int evaluate(int op1,int op2) {
			return op1 | op2;
		}
	},	
	SHIFT_LEFT("<<",2,3) // ok
	{
		@Override
		public int evaluate(int op1,int op2) {
			return op1 << op2;
		}
	},	
	SHIFT_RIGHT(">>",2,3) // ok
	{
		@Override
		public int evaluate(int op1,int op2) {
			return op1 >> op2;
		}
	},		
	MULTIPLY("*",2,5) { // ok
		@Override
		public int evaluate(int op1, int op2) {
			return op1*op2;
		}
	},
	DIVIDE("/",2, 5 )	{ // ok
		@Override
		public int evaluate(int op1, int op2) {
			return op1 / op2;
		}
	},
	LOWER_BYTE("<",1, 6 )	{ // ok
		@Override
		public int evaluate(int op1) {
			return op1 & 0xff;
		}
		@Override
		public boolean isLeftAssociative() {
			return false;
		}
	},
	UPPER_BYTE(">",1, 6 )	{ // ok
		@Override
		public int evaluate(int op1) {
			return ( op1 >> 8 ) & 0xff;
		}
		@Override
		public boolean isLeftAssociative() {
			return false;
		}		
	};

	public final int operandCount;
	public final String symbol;
	public final int precedence;

	private Operator(String symbol,int operandCount,int precedence)
	{
		this.symbol=symbol;
		this.operandCount=operandCount;
		this.precedence = precedence;
	}

	public boolean isLeftAssociative() {
		return true;
	}

	protected boolean canBeParsed() {
		return true;
	}

	public int getPrecedence() {
		return precedence;
	}

	public final int evaluate(int value1,int... values)
	{
		if( values == null|| values.length==0) {
			return evaluate(value1);
		}
		if ( values.length == 1 ) {
			return evaluate(value1,values[0] );
		}
		throw new UnsupportedOperationException("There's no operator that supports "+(1+values.length)+" operands");
	}

	public int evaluate(int op1,int op2)
	{
		throw new UnsupportedOperationException("evaluate(op1,op2) not implemented for "+this);
	}

	public int evaluate(int value1)
	{
		throw new UnsupportedOperationException("evaluate(arg1) not implemented for "+this);
	}

	public static boolean isValidOperator(String s)
	{
		for ( Operator op : values () )
		{
			if ( op.canBeParsed() && s.equals( op.symbol ) ) {
				return true;
			}
		}
		return false;
	}

	public static List<Operator> getMatchingOperators(String s)
	{
		final List<Operator> operator = new ArrayList<>();
		for ( Operator op : values () )
		{
			if ( op.canBeParsed() && s.equals( op.symbol ) ) {
				operator.add( op );
			}
		}
		return operator;
	}
}
