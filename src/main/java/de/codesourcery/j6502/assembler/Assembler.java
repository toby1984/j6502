package de.codesourcery.j6502.assembler;

import java.util.function.Consumer;

import de.codesourcery.j6502.assembler.exceptions.ValueUnavailableException;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.assembler.parser.ast.IASTNode;
import de.codesourcery.j6502.assembler.parser.ast.ICompilationContextAware;
import de.codesourcery.j6502.assembler.parser.ast.IValueNode;
import de.codesourcery.j6502.utils.HexDump;



public class Assembler
{
	protected DefaultContext context = new DefaultContext();

	protected final class DefaultContext implements ICompilationContext
	{
		protected byte[] buffer = new byte[1024];
		protected int currentOffset;
		protected int currentPassNo = 0;

		private final ISymbolTable symbolTable = new SymbolTable();

		@Override
		public void setCurrentAddress(short adr)
		{
			int value = adr;
			value = value & 0xffff;
			if ( value < currentOffset ) {
				throw new RuntimeException("Internal error, cannot decrease ORIGIN to "+HexDump.toAdr( adr )
						+ " (already at "+HexDump.toAdr( (short) currentOffset )+")" );
			}

			for ( int delta = value - currentOffset; delta > 0 ; delta-- )
			{
				writeByte((byte) 0);
			}
		}

		@Override
		public void writeByte(byte b)
		{
			if ( currentOffset  >= buffer.length ) {
				expandBuffer();
			}
			buffer[currentOffset++] = b;
		}

		@Override
		public void debug(IASTNode node, String msg) {
			System.out.println("PASS #"+currentPassNo+" : "+node+" ("+node.toString()+") : "+msg);
		}

		public void onePass(AST ast)
		{
			currentOffset = 0;
			buffer = new byte[1024];

			final Consumer<IASTNode> visitor1 = n ->
			{
				if ( n instanceof ICompilationContextAware)
				{
					((ICompilationContextAware) n).visit( context );
				}
			};

			ast.visitParentFirst( visitor1);

			final Consumer<IASTNode> visitor2 = n ->
			{
				if ( n instanceof ICompilationContextAware)
				{
					((ICompilationContextAware) n).passFinished( context );
				}
			};

			ast.visitParentFirst( visitor2 );

			currentPassNo++;
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
		public void writeByte(IASTNode node)
		{
			final IValueNode lit = (IValueNode) node;
			byte value;
			if ( ! lit.isValueAvailable() )
			{
				if ( getPassNo() != 0 ) {
					throw new ValueUnavailableException(lit);
				}
				value = (byte) 0xff;
			} else {
				value = lit.getByteValue();
			}
			writeByte( value );
		}

		@Override
		public void writeWord(IASTNode node)
		{
			final IValueNode lit = (IValueNode) node;
			short value;
			if ( ! lit.isValueAvailable() )
			{
				if ( getPassNo() != 0 ) {
					throw new ValueUnavailableException(lit);
				}
				value = (short) 0xffff;
			} else {
				value = lit.getWordValue();
			}
			writeWord( value );
		}

		@Override
		public int getCurrentAddress() {
			return currentOffset;
		}

		@Override
		public int getPassNo() {
			return currentPassNo;
		}

		private void expandBuffer() {
			final byte[] newBuffer = new byte[buffer.length*2];
			System.arraycopy( buffer, 0 , newBuffer , 0 , buffer.length );
			buffer = newBuffer;
		}

		public byte[] getBytes() {
			final byte[] result = new byte[currentOffset];
			if ( currentOffset > 0 ) {
				System.arraycopy( buffer , 0 , result , 0 , currentOffset );
			}
			return result;
		}
	};

	public byte[] assemble(AST ast)
	{
		context = new DefaultContext();
		context.onePass( ast );
		context.onePass( ast );
		return context.getBytes();
	}
}