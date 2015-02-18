package de.codesourcery.j6502.parser.ast;

import de.codesourcery.j6502.assembler.ICompilationContext;

public interface ICompilationContextAware extends IASTNode {

	public void visit(ICompilationContext context);
}
