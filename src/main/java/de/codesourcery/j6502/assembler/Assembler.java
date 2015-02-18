package de.codesourcery.j6502.assembler;

import java.util.HashMap;
import java.util.Map;

import de.codesourcery.j6502.parser.Opcode;
import de.codesourcery.j6502.parser.ast.AST;
import de.codesourcery.j6502.parser.ast.IASTNode;
import de.codesourcery.j6502.parser.ast.ICompilationContextAware;
import de.codesourcery.j6502.parser.ast.Identifier;
import de.codesourcery.j6502.parser.ast.InstructionNode;
import de.codesourcery.j6502.parser.ast.LabelNode;
import de.codesourcery.j6502.parser.ast.Statement;



public class Assembler
{
	protected byte[] buffer = new byte[1024];
	protected int currentOffset;
	protected LabelNode previousGlobalLabel;

	private final Map<Identifier,Integer> labelOffsets = new HashMap<>();

	private final ISymbolTable symbolTable = new SymbolTable();

	private final ICompilationContext context = new ICompilationContext()
	{
		@Override
		public void writeByte(byte b)
		{
			if ( currentOffset  >= buffer.length ) {
				expandBuffer();
			}
			buffer[currentOffset++] = b;
		}

		@Override
		public void writeWord(short b)
		{
			if ( currentOffset >= buffer.length ) {
				expandBuffer();
			}
			// 6502 uses little-endian => low-byte first

			buffer[currentOffset++] = (byte) (b & 0xff );
			buffer[currentOffset++] = (byte) (( b & 0xff00) >> 8);
		}

		@Override
		public ISymbolTable getSymbolTable() {
			return symbolTable;
		}

		@Override
		public void writeByte(IASTNode node) {
			writeByte( Opcode.getByteValue( node ) );
		}

		@Override
		public void writeWord(IASTNode node) {
			writeWord( Opcode.getWordValue( node ) );
		}

		@Override
		public int getCurrentAddress() {
			return currentOffset;
		}

		@Override
		public LabelNode getPreviousGlobalLabel() {
			return previousGlobalLabel;
		}
	};

	private void expandBuffer() {
		final byte[] newBuffer = new byte[buffer.length*2];
		System.arraycopy( buffer, 0 , newBuffer , 0 , buffer.length );
		buffer = newBuffer;
	}

	public byte[] assemble(AST ast) {
		return assemble(ast,context);
	}

	public byte[] assemble(AST ast,ICompilationContext context)
	{
		for ( final IASTNode node : ast )
		{
			assemble( (Statement) node , context );
		}

		// return actual buffer
		final byte[] result = new byte[currentOffset];
		if ( currentOffset > 0 ) {
			System.arraycopy( buffer , 0 , result , 0 , currentOffset );
		}
		return result;
	}

	private void assemble(Statement stmt,ICompilationContext context)
	{
		final int i = 0;
		IASTNode node = stmt.child(0);

		// process labels
		stmt.visitBreadthFirst( n ->
		{
			if ( n instanceof LabelNode)
			{
				final LabelNode label = (LabelNode) n;
				final Label symbol;
				if ( label.isLocal() ) {
					symbol = new Label( label.identifier , null );
				} else {
					symbol = new Label( label.identifier , label.parentIdentifier.identifier );
					previousGlobalLabel = label;
				}
				symbol.setValue( currentOffset );
				context.getSymbolTable().defineSymbol( symbol );
			}
			else if ( n instanceof ICompilationContextAware)
			{
				((ICompilationContextAware) n).visit( context );
			}
		});

		node = stmt.child(i);
		if ( node instanceof InstructionNode)
		{
			final InstructionNode ins = (InstructionNode) node;
			ins.opcode.assemble( ins , context );
		}
	}
}