package de.codesourcery.j6502.assembler;

import java.util.function.Consumer;

import de.codesourcery.j6502.assembler.exceptions.ValueUnavailableException;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.assembler.parser.ast.IASTNode;
import de.codesourcery.j6502.assembler.parser.ast.ICompilationContextAware;
import de.codesourcery.j6502.assembler.parser.ast.IValueNode;
import de.codesourcery.j6502.utils.HexDump;
import de.codesourcery.j6502.utils.ITextRegion;
import de.codesourcery.j6502.utils.SourceHelper;
import de.codesourcery.j6502.utils.SourceHelper.TextLocation;



public class Assembler
{
	protected static final boolean DEBUG_ENABLED = false;

	protected DefaultContext context;

	protected final class DefaultContext implements ICompilationContext
	{
		protected byte[] buffer = new byte[1024];
		protected int currentWriteOffset;
		protected int origin;
		protected int currentAddress;
		protected int currentPassNo = 0;

		private final SourceMap sourceMap = new SourceMap();
		private final SourceHelper sourceHelper;
		private final ISymbolTable symbolTable = new SymbolTable();

		private boolean originSet = false;

		public DefaultContext(SourceHelper sourceHelper) {
			this.sourceHelper = sourceHelper;
		}

		@Override
		public void setOrigin(short adr)
		{
			if ( originSet ) {
				throw new IllegalStateException("Origin address already set to "+HexDump.toAdr( origin )+" , cannot set it more than once");
			}

			currentAddress = origin = adr;
			origin &= 0xffff;
			currentAddress &= 0xffff;
			originSet = true;
		}

		@Override
		public void writeByte(byte b)
		{
			if ( ! originSet ) {
				throw new IllegalStateException("Origin not set");
			}

			if ( currentWriteOffset  >= buffer.length ) {
				expandBuffer();
			}
			buffer[currentWriteOffset++] = b;
			currentAddress++;
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
		public void writeWord(short b)
		{
			if ( ! originSet ) {
				throw new IllegalStateException("Origin not set");
			}

			if ( currentWriteOffset >= buffer.length ) {
				expandBuffer();
			}
			// 6502 uses little-endian => low-byte first
			buffer[currentWriteOffset++] = (byte) (b & 0xff );
			buffer[currentWriteOffset++] = (byte) (( b & 0xff00) >> 8);
			currentAddress += 2;
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
		public void debug(IASTNode node, String msg) {
			if ( DEBUG_ENABLED ) {
				System.out.println("PASS #"+currentPassNo+" : "+node+" ("+node.toString()+") : "+msg);
			}
		}

		public void onePass(AST ast)
		{
			originSet = false;
			currentAddress = 0;
			currentWriteOffset = 0;
			buffer = new byte[1024];
			sourceMap.clear();

			final Consumer<IASTNode> visitor1 = n ->
			{
				if ( n instanceof ICompilationContextAware)
				{
					final int start = currentAddress;
					((ICompilationContextAware) n).visit( this );
					final int end = currentAddress;

					if ( start != end && sourceHelper != null )
					{
						ITextRegion region = n.getTextRegion();
						if ( region == null ) {
							region = n.getTextRegionIncludingChildren();
						}
						if ( region != null )
						{
							TextLocation loc = sourceHelper.getLocation( region.getStartingOffset() );
							if ( loc != null ) {
								sourceMap.addAddressRange( (short) start  , end-start , loc.lineNumber );
							}
						}
					}
				}
			};

			ast.visitParentFirst( visitor1);

			final Consumer<IASTNode> visitor2 = n ->
			{
				if ( n instanceof ICompilationContextAware)
				{
					((ICompilationContextAware) n).passFinished( this );
				}
			};

			ast.visitParentFirst( visitor2 );

			currentPassNo++;
		}

		@Override
		public ISymbolTable getSymbolTable() {
			return symbolTable;
		}

		@Override
		public int getCurrentAddress() {
			return currentAddress;
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
			final byte[] result = new byte[currentWriteOffset];
			if ( currentWriteOffset > 0 ) {
				System.arraycopy( buffer , 0 , result , 0 , currentWriteOffset );
			}
			return result;
		}
	};

	/**
	 * Returns the intended output / starting address of the generated binary.
	 * @return
	 */
	public int getOrigin()
	{
		return context.origin;
	}

	public byte[] assemble(AST ast)
	{
		return assemble(ast,null);
	}

	public byte[] assemble(AST ast,SourceHelper helper)
	{
		context = new DefaultContext( helper);
		context.onePass( ast );
		context.onePass( ast );
		return context.getBytes();
	}

	public SourceMap getSourceMap() {
		return context.sourceMap;
	}
}