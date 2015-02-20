package de.codesourcery.j6502.emulator;

import java.io.PrintWriter;

import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.emulator.exceptions.InvalidOpcodeException;
import de.codesourcery.j6502.utils.HexDump;

public class C64
{

	public static void main(String[] args) {

		final Emulator emulator = new Emulator();
		emulator.reset();

		int i = 100;
		while ( i-- >= 0 )
		{
			try {
				emulator.singleStep();
			}
			catch(final InvalidOpcodeException e)
			{
				e.printStackTrace();
				System.err.flush();
				System.out.flush();
				final Disassembler d = new Disassembler();
				final PrintWriter writer = new PrintWriter( System.out );
				final IMemoryRegion region = emulator.getMemory();
				System.out.println("Dumping "+region+" at "+HexDump.toAdr( emulator.getCPU().previousPC ) );
				d.disassemble( region , emulator.getCPU().previousPC , 16, writer );
				writer.flush();
				throw e;
			}
		}
	}

}
