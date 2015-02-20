package de.codesourcery.j6502.assembler;

import de.codesourcery.j6502.assembler.parser.ast.IASTNode;

public interface ICompilationContext
{
	public void setOrigin(short adr);

	public int getCurrentAddress();

	public void writeByte(byte b);

	public void writeWord(short b);

	public void writeByte(IASTNode node);

	public void writeWord(IASTNode node);

	public ISymbolTable getSymbolTable();

	public int getPassNo();

	public void debug(IASTNode node,String msg);
}