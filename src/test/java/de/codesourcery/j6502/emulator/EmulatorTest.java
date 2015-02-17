package de.codesourcery.j6502.emulator;

import java.util.Set;

import junit.framework.TestCase;

import org.apache.commons.lang.ArrayUtils;

import de.codesourcery.j6502.assembler.Assembler;
import de.codesourcery.j6502.emulator.CPU.Flag;
import de.codesourcery.j6502.parser.Lexer;
import de.codesourcery.j6502.parser.Parser;
import de.codesourcery.j6502.parser.Scanner;
import de.codesourcery.j6502.utils.HexDump;

public class EmulatorTest  extends TestCase
{
	private Emulator emulator;
	private static final int PRG_LOAD_ADDRESS = MemorySubsystem.Bank.BANK1.range.getStartAddress();

	@Override
	protected void setUp() throws Exception
	{
		super.setUp();
		emulator = new Emulator();
	}

	public void testLDAImmediateNonZero()
	{
		execute("LDA #$44");
		assertAccumulator( 0x44 );
		assertFlags();
	}

	public void testLDAImmediateZero()
	{
		execute("LDA #$00");
		assertAccumulator( 0x00 );
		assertFlags(CPU.Flag.ZERO);
	}

	public void testLDAImmediateNegative()
	{
		execute("LDA #$80");
		assertAccumulator( 0x80 );
		assertFlags(CPU.Flag.NEGATIVE);
	}

	// ============ helper ================

	private void execute(String asm)
	{
		final Parser p = new Parser(new Lexer(new Scanner(asm)));

		final Assembler a = new Assembler();
		final byte[] actual = a.assemble( p.parse() );

		final IMemoryProvider provider = new IMemoryProvider()
		{
			@Override
			public void loadInto(IMemoryRegion region)
			{
				region.bulkWrite( PRG_LOAD_ADDRESS , actual , 0 , actual.length );
				region.writeWord( CPU.RESET_VECTOR_LOCATION , (short) PRG_LOAD_ADDRESS );
			}
		};

		emulator.setMemoryProvider( provider );
		emulator.getCPU().pc = MemorySubsystem.Bank.BANK1.range.getStartAddress();
		emulator.singleStep();

		System.out.println("\n---------------------");
		System.out.println("Compiled: "+asm+"\n");
		System.out.println("Memory  :\n");
		System.out.println( HexDump.INSTANCE.dump((short) 0 , emulator.getMemory() , 0 , 16 ) );
		System.out.println("\nCPU     : "+emulator.getCPU());
	}

	private void assertFlags(CPU.Flag... flags)
	{
		final Set<Flag> enabledFlags = emulator.getCPU().getFlags();
		final int expectedCount = flags == null ? 0 : flags.length;
		assertEquals( "Expected flags "+ArrayUtils.toString( flags )+" but got "+enabledFlags , expectedCount , enabledFlags.size() );
		if ( flags != null ) {
			for ( final Flag exp : flags ) {
				if ( ! enabledFlags.contains( exp ) ) {
					fail( "Expected flags "+ArrayUtils.toString( flags )+" but got "+enabledFlags );
				}
			}
		}
	}

	private void assertAccumulator(int value) {

		final int expected = value;
		final int actual = emulator.getCPU().accumulator;
		assertEquals( expected & 0xff , actual & 0xff );
	}

}
