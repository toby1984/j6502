package de.codesourcery.j6502.assembler;

import java.util.HashMap;
import java.util.Map;

import de.codesourcery.j6502.parser.Opcode;
import de.codesourcery.j6502.parser.ast.AST;
import de.codesourcery.j6502.parser.ast.ASTNode;
import de.codesourcery.j6502.parser.ast.Identifier;
import de.codesourcery.j6502.parser.ast.InstructionNode;
import de.codesourcery.j6502.parser.ast.LabelNode;
import de.codesourcery.j6502.parser.ast.Statement;

public class Assembler
{
	protected byte[] buffer = new byte[1024];
	protected int currentOffset;

	private final Map<Identifier,Integer> labelOffsets = new HashMap<>();

	public interface BufferWriter
	{
		public void writeByte(byte b);
		public void writeWord(short b);
		public void writeByte(ASTNode node);
		public void writeWord(ASTNode node);
	}

	private final BufferWriter writer = new BufferWriter() {

		@Override
		public void writeByte(byte b) {
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
		public void writeByte(ASTNode node) {
			writeByte( Opcode.getByteValue( node ) );
		}

		@Override
		public void writeWord(ASTNode node) {
			writeWord( Opcode.getWordValue( node ) );
		}
	};

	private void expandBuffer() {
		final byte[] newBuffer = new byte[buffer.length*2];
		System.arraycopy( buffer, 0 , newBuffer , 0 , buffer.length );
		buffer = newBuffer;
	}

	public byte[] assemble(AST ast)
	{
		for ( final ASTNode node : ast )
		{
			assemble( (Statement) node );
		}

		// return actual buffer
		final byte[] result = new byte[currentOffset];
		if ( currentOffset > 0 ) {
			System.arraycopy( buffer , 0 , result , 0 , currentOffset );
		}
		return result;
	}

	private void assemble(Statement stmt)
	{
		int i = 0;
		ASTNode node = stmt.child(0);
		if ( stmt.child(0) instanceof LabelNode)
		{
			registerLabel((LabelNode) stmt.child(0) );
			i++;
		}
		node = stmt.child(i);
		if ( node instanceof InstructionNode)
		{
			assemble((InstructionNode) node);
		}
	}

	private void registerLabel(LabelNode label) {
		if ( ! label.isGlobal() ) {
			throw new RuntimeException("Local labels not implemented yet");
		}
		if ( labelOffsets.containsKey( label.identifier ) ) {
			throw new DuplicateLabelException( label );
		}
		labelOffsets.put( label.identifier , currentOffset );
	}

	private void assemble(InstructionNode node)
	{
		node.opcode.assemble( node , writer );
	}
}
