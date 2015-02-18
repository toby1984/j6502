package de.codesourcery.j6502.assembler;

import de.codesourcery.j6502.parser.ast.IASTNode;
import de.codesourcery.j6502.parser.ast.LabelNode;

public interface ICompilationContext
{
	public int getCurrentAddress();
	public void writeByte(byte b);
	public void writeWord(short b);
	public void writeByte(IASTNode node);
	public void writeWord(IASTNode node);
	public ISymbolTable getSymbolTable();
	public LabelNode getPreviousGlobalLabel();

}