package de.codesourcery.j6502.parser.ast;

public interface INodeWithAddress extends IASTNode {

	public int getAddress();

	public void setAddress();
}
