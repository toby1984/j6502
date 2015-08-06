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

		int i = 20000;
		while ( i-- >= 0 )
		{
			try {
				emulator.doOneCycle();
			}
			catch(final InvalidOpcodeException e)
			{
				e.printStackTrace();
				System.err.flush();
				System.out.flush();
				final Disassembler d = new Disassembler();
				final PrintWriter writer = new PrintWriter( System.out );
				final IMemoryRegion region = emulator.getMemory();
				System.out.println("Dumping "+HexDump.toAdr( emulator.getCPU().previousPC )+" : "+region);
				d.disassemble( region , 0xf6dc-10 , 32, writer );
				writer.flush();
				throw e;
			}
		}

		System.out.println("Finished");
		System.out.println( HexDump.INSTANCE.dump( (short) 0x0400 , emulator.getMemory() , 0x0400 , 25*40 ) );
	}

}
