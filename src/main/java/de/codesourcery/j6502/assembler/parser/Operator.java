package de.codesourcery.j6502.assembler.parser;

import java.util.ArrayList;
import java.util.List;

public enum Operator
{
	PLUS("+",2,1) {
		@Override
		public int evaluate(int op1, int op2) {
			return op1+op2;
		}
	},
	BINARY_MINUS("-",2,1) {
		@Override
		public int evaluate(int op1, int op2) {
			return op1 - op2;
		}
	},
	UNARY_MINUS("-",1,1) {
		@Override
		public int evaluate(int op1, int op2) {
			return op1 - op2;
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
	MULTIPLY("*",2,2) {
		@Override
		public int evaluate(int op1, int op2) {
			return op1*op2;
		}
	},
	DIVIDE("/",2,2)	{
		@Override
			public int evaluate(int op1, int op2) {
				return op1 / op2;
			}
	};
//	PARENS_OPEN("(",1,2)
//	{
//		@Override
//		public int evaluate(int value1) {
//			return value1;
//		}
//
//		@Override
//		public boolean isLeftAssociative() {
//			return false;
//		}
//
//		@Override
//		protected boolean canBeParsed() {
//			return false;
//		}
//	};

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
