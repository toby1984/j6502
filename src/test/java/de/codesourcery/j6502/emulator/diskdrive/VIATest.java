package de.codesourcery.j6502.emulator.diskdrive;

import de.codesourcery.j6502.emulator.AddressRange;
import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.Memory;
import junit.framework.TestCase;

public class VIATest extends TestCase {

	private VIA via;
	private CPU cpu;

	@Override
	protected void setUp() throws Exception
	{
		super.setUp();

		final Memory memory = new Memory("dummy" , AddressRange.range(0, 0xffff) );
		cpu = new CPU( memory);
		via = new VIA( "Test VIA" , AddressRange.range(0,16) , cpu );
	}

	public void testNoIRQsAreTriggeredAfterReset()
	{
		cpu.reset();
		via.reset();

		assertFalse( cpu.isInterruptQueued() );
		assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );

		for ( int i = 0 ; i < 65536 ; i++ ) {
			via.tick();
		}
		assertFalse( cpu.isInterruptQueued() );
		assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );
	}

	public void testTimersAreNotRunningAfterReset()
	{
		cpu.reset();
		via.reset();

		assertFalse( cpu.isInterruptQueued() );
		assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );
		assertEquals( 0 , via.readTimer1Counter() );
		assertEquals( 0 , via.readTimer2Counter() );

		for ( int i = 0 ; i < 65536 ; i++ ) {
			via.tick();
		}
		assertFalse( cpu.isInterruptQueued() );
		assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );

		assertEquals( 0 , via.readTimer1Counter() );
		assertEquals( 0 , via.readTimer2Counter() );
	}

	private void loadTimer1(int value)
	{
		via.writeByte( VIA.T1LL , (byte) (value & 0xff) );
		via.writeByte( VIA.T1LH , (byte) ((value & 0xff00) >> 8));
		via.writeByte( VIA.T1CH , (byte) ((value & 0xff00) >> 8));
	}

	public void testTimer1OneShotModeWithIRQEnabled() {

		final int count = 0x1234;

		cpu.reset();
		via.reset();

		loadTimer1( count );

		via.writeByte( VIA.IER  , (byte) 0b1100_0000 ); // enable IRQ

		assertFalse( cpu.isInterruptQueued() );
		for ( int i = 0 ; i < count-1 ; i++ ) {
			via.tick();
		}
		assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );
		assertFalse( cpu.isInterruptQueued() );
		via.tick();
		assertEquals( 0b1100_0000 , via.readByte( VIA.IFR ) );
		assertTrue( cpu.isInterruptQueued() );
		assertEquals(count,via.readTimer1Counter());

		cpu.clearInterruptQueued();
		via.writeByte( VIA.IFR  , (byte) 0b0100_0000 ); // clear IRQ
		assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );

		assertFalse( cpu.isInterruptQueued() );
		for ( int i = 0 ; i < count ; i++ ) {
			via.tick();
		}
		assertEquals( 0 ,via.readTimer1Counter() );

		assertFalse( cpu.isInterruptQueued() );
		assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );
		assertFalse( cpu.isInterruptQueued() );
	}

	public void testTimer1ContinousModeWithIRQEnabled() {

		final int count = 0x1234;

		cpu.reset();
		via.reset();

		loadTimer1( count );

		via.writeByte( VIA.IER  , (byte) 0b1100_0000 ); // enable IRQ

		/*
	     * bit 7 - Timer #1 control ( see below )
	     * bit 6 - Timer #1 control ( see below )
	     * bit 5 - Timer #2 control ( 0 = timed interrupt, 1 = count-down with pules on PB6)
	     * bit 4 - shift register control (see below)
	     * bit 3 - shift register control (see below)
	     * bit 2 - shift register control (see below)
	     * bit 1 - port B latching ( 0 = disable , 1 = enable)
	     * bit 0 - port A latching ( 0 = disable , 1 = enable)
	     *
	     *       * |7|6| Timer #1 control bits
	     * |0|0| Timed interrupt each time T1 is loaded.
	     * |0|1| Continuous interrupts
	     * |1|0| Timed interrupt each time T1 is loaded. (PB7: one-shot output)
	     * |1|1| Continuous interrupts (PB7: square wave output)
			 */
			via.writeByte( VIA.ACR , (byte) 0b0100_0000 ); // enable continous mode

		assertFalse( cpu.isInterruptQueued() );
		for ( int i = 0 ; i < count-1 ; i++ ) {
			via.tick();
		}
		assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );
		assertFalse( cpu.isInterruptQueued() );
		via.tick();
		assertEquals( 0b1100_0000 , via.readByte( VIA.IFR ) );
		assertTrue( cpu.isInterruptQueued() );
		assertEquals(count,via.readTimer1Counter());

		cpu.clearInterruptQueued();
		via.writeByte( VIA.IFR  , (byte) 0b0100_0000 ); // clear IRQ
		assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );

		assertFalse( cpu.isInterruptQueued() );
		for ( int i = 0 ; i < count ; i++ ) {
			via.tick();
		}
		assertEquals( 0 ,via.readTimer1Counter() );

		assertFalse( cpu.isInterruptQueued() );
		assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );
		assertFalse( cpu.isInterruptQueued() );
	}
}
