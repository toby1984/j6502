package de.codesourcery.j6502.parser;

public enum Register
{
	PC,
	SP,
	X,
	Y;

	public static Register getRegister(String s)
	{
		if ( s != null )
		{
			switch(s.toLowerCase())
			{
				case "pc":
					return PC;
				case "sp":
					return SP;
				case "x":
					return X;
				case "y":
					return Y;
			}
		}
		return null;
	}
}
