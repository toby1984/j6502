package de.codesourcery.j6502.emulator.diskdrive;

import de.codesourcery.j6502.emulator.AddressRange;
import de.codesourcery.j6502.emulator.Memory;
import de.codesourcery.j6502.utils.HexDump;

/**
 * VIA 6522.
 *
 * Datasheet: http://www.princeton.edu/~mae412/HANDOUTS/Datasheets/6522.pdf
 * @author tobias.gierke@code-sourcery.de
 */
public class VIA extends Memory {

    /* Behaviour of input registers (IR) / output registers (OR)
     *
     * - If you read a pin on IRA and input latching is disabled for port A, then you
     *   will simply read the current state of the corresponding PA pin, regardless of
     *   whether that pin is set to be an input or an output.
     *
     * - If you read a pin on IRA and input latching is enabled for port A, then you
     *   will read the actual IRA, which is the last value that was latched into IRA.
     *
     * - If you read a pin on IRB and the pin is set to be an input (with latching
     *   disabled), then you will read the current state of the corresponding PB pin.
     *
     * - If you read a pin on IRB and the pin is set to be an input (with latching
     *   enabled), then you will read the actual IRB.
     *
     * - If you read a pin on IRB and the pin is set to be an output, then you will
     *   actually read ORB, which contains the last value that was written to port B.
     *
     * - Writing to a pin which is set to be an input will change the OR for that pin,
     *   but the state of the pin itself will not change as long as the DDR dictates
     *   that it is an input.
     *
     *   Note that there is no such thing as "output latching" on the 6522; Writing to
     *   ORA or ORB will always simply set the OR, and the OR will then retain that
     *   value until it is written to again. The ORs are also never transparent;
     *   Whereas an input bus which has input latching turned off can change with its
     *   input without the Enable pin even being cycled,
     *
     *   >>> outputting to an OR will not take effect until the Enable (clock) pin has made a transition to low or high <<<
     *
     *   >>> Writing to a pin which is set to be an input does nothing. <<<<
     */
    protected static final int PORTB = 0x0000; // Data port B
    protected static final int PORTA = 0x0001; // Data port A

    protected static final int DDRB  = 0x0002; // Data direction register port B0-B7 ( 0 = pin is input , 1 = pin is output)
    protected static final int DDRA  = 0x0003; // Data direction register port A0-A7 ( 0 = pin is input , 1 = pin is output)

    protected static final int T1CL  = 0x0004; // Timer #1 Low-Order Counter
    protected static final int T1CH  = 0x0005; // Timer #1 High-Order Counter (write: Latches transferred into counter)
    protected static final int T1LL  = 0x0006; // Timer #1 Low-Order Latch
    protected static final int T1LH  = 0x0007; // Timer #1 High-Order Latch (NO transfer to counter upon write)

    protected static final int T2CL  = 0x0008; // Timer #2 Low-Order Latch ( write: write to Timer #2 Low-Order Latch / read: read Timer #2 Low-Order Counter,T2 IRQ FLAG IS RESET)
    protected static final int T2CH  = 0x0009; // Timer #2 High Order Counter ( write: T2CL and T2CH written to counter / read: read Timer #2 High-Order Counter,T2 IRQ FLAG IS RESET)

    protected static final int SR    = 0x000A; // shift register

    /* Auxiliary Control Register.
     *
     * Input latching is enabled/disabled via the ACR.
     *
     * The last two bits of this register control latching; The second-last bit is for
     * port B, and the last bit is for port A. If the corresponding bit in the ACR
     * is on (1), then latching for that port is enabled. If the corresponding bit
     * in the ACR is off (0), then latching for that port is disabled.
     *
     * bit 7 - Timer #1 control ( see below )
     * bit 6 - Timer #1 control ( see below )
     * bit 5 - Timer #2 control ( 0 = timed interrupt, 1 = count-down with pules on PB6)
     * bit 4 - shift register control (see below)
     * bit 3 - shift register control (see below)
     * bit 2 - shift register control (see below)
     * bit 1 - port B latching ( 0 = disable , 1 = enable)
     * bit 0 - port A latching ( 0 = disable , 1 = enable)
     *
     * |7|6| Timer #1 control bits
     * |0|0| Timed interrupt each time T1 is loaded.
     * |0|1| Continuous interrupts
     * |1|0| Timed interrupt each time T1 is loaded. (PB7: one-shot output)
     * |1|1| Continuous interrupts (PB7: square wave output)
     *
     * |4|3|2| *** shift register control bits ***
     * |0|0|0| SR disabled
     * |0|0|1| Shift-in under control of T2
     * |0|1|0| Shift-in under control of phi2
     * |0|1|1| Shift-in under control of ext. clock
     * |1|0|0| Shift-out free-running at T2 rate
     * |1|0|1| Shift-out under control of T2
     * |1|1|0| Shift-out under control of phi2
     * |1|1|1| Shift-out under control of ext. clock
     */
    protected static final int ACR   = 0x000B;

    /* Periphal control register.
     *
     * CA1 is set to active-high or active-low through the last bit (bit 0) of the PCR, or
     * Peripheral Control Register (at Register Select address C hex, 12 decimal, or
     * 1100 binary). If that bit is 0, CA1 is active-low; If that bit is 1, CA1 is
     * active-high. CB1 is set to active-high or active-low through the fourth bit
     * (bit 4) of the PCR. If that bit is 0, CB1 is active-low; If that bit is 1,
     * CB1 is active-high.
     *
     * bit 7 - CB2 control bits (see below)
     * bit 6 - CB2 control bits (see below)
     * bit 5 - CB2 control bits (see below)
     * bit 4 - CB1 interrupt control (0=negative active edge,1=positive active edge)
     * bit 3 - CA2 control bits (see below)
     * bit 2 - CA2 control bits (see below)
     * bit 1 - CA2 control bits (see below)
     * bit 0 - CA1 interrupt control (0=negative active edge,1=positive active edge)
     *
     * |3|2|1| *** CA2/CB2 control bits ***
     * |0|0|0| Input negative active edge
     * |0|0|1| Independent interrupt input negative edge (*)
     * |0|1|0| Input positive active edge
     * |0|1|1| Independent interrupt input positive edge (*)
     * |1|0|0| Handshake output
     * |1|0|1| Pulse output
     * |1|1|0| Low output
     * |1|1|1| High output
     *
     *  (*) if CA2/CB2 is set to "independent interrupt input" than
     *  writing to/reading from PORTA/PORTB will NOT clear the IRQ flag
     *  bit, instead the bit must be cleared by writing into the IFR.
     */

    protected static final int PCR   = 0x000C;

    /*
     * Interrupt Flag Register.
     *
     * To clear bits, write a '1' into the corresponding position.
     *
     * >>> bit 7 can only be cleared indirectly by clearing all other bits <<<
     *
     * bit 7 - set/clear
     * bit 6 - Time out of timer 1
     * bit 5 - Time-out of timer 2
     * bit 4 - CB1 active edge
     * bit 3 - CB2 active edge
     * bit 2 - Completed 8 shifts
     * bit 1 - CA1 active edge
     * bit 0 - CA2 active edge
     */
    protected static final int IFR   = 0x000D;

    /*
     * Interrupt Enable Register.
     *
     * bit 7 - set/clear , 1 => sets all bits that are set to 1 , 0 => clears all bits that are set to 1
     * bit 6 - Timer 1
     * bit 5 - Timer 2
     * bit 4 - CB1
     * bit 3 - CB2
     * bit 2 - Shift register
     * bit 1 - CA1
     * bit 0 - CA2
     *
     * >>> Clearing a bit here will clear the corresponding bit in IFR as well <<<
     * >>> READING the register will always return bit 7 = 1 <<<<
     */
    protected static final int IER   = 0x000E;

    /*
     * Same as PORTA but no handshake
     */
    protected static final int PORTA_NOHANDSHAKE  = 0x000F;

    private int readPortA; // port A read buffer
    private int readPortB; // port B read buffer

    private int writePortA; // port A write buffer
    private int writePortB; // port B write buffer

    private int porta;
    private int portb;

    private int ddra;
    private int ddrb;

    private int timer1;

    private int t1latchlo;
    private int t1latchhi;

    private int timer2;

    private int t2latchlo;
    private int t2latchhi;

    private int irqEnable;
    private int irqFlags;

    private int pcr;

    private int acr;

    private int sr;

    public VIA(String identifier, AddressRange range)
    {
        super(identifier, range);
    }

    @Override
    public void reset()
    {
        /* Clear all internal registers
         * except t1/t2 counter, latches and SR
         */
    }

    @Override
    public int readByte(int offset)
    {
    	switch( offset )
    	{
		    case PORTB:
		    	return readPortB;
		    case PORTA:
		    	return readPortA;
		    case DDRB:
		    	return ddrb;
		    case DDRA:
		    	return ddra;
		    case T1CL:
		    	return timer1 & 0xff;
		    case T1CH:
		    	return (timer1 & 0xff00) >> 8;
		    case T1LL:
		    	return t1latchlo;
		    case T1LH:
		    	return t1latchhi;
		    case T2CL:
		    	irqFlags &= ~(1<<5);
		    	return (timer2 & 0xff);
		    case T2CH:
		    	irqFlags &= ~(1<<5);
		    	return (timer2 & 0xff00) >> 8;
		    case SR:
		    	return sr;
		    case ACR:
		    	return acr;
		    case PCR:
		    	return pcr;
		    case IFR:
		    	return irqFlags;
		    case IER:
		    	return irqEnable;
		    case PORTA_NOHANDSHAKE:
		    	return readPortA;
    		default:
    			throw new IllegalArgumentException("No register at "+HexDump.toAdr(offset));
    	}
    }

    @Override
    public void writeByte(int offset, byte value)
    {
//
//    	switch( offset )
//    	{
//		    case PORTB:
//		    	return readPortB;
//		    case PORTA:
//		    	return readPortA;
//		    case DDRB:
//		    	return ddrb;
//		    case DDRA:
//		    	return ddra;
//		    case T1CL:
//		    	return timer1 & 0xff;
//		    case T1CH:
//		    	return (timer1 & 0xff00) >> 8;
//		    case T1LL:
//		    	return t1latchlo;
//		    case T1LH:
//		    	return t1latchhi;
//		    case T2CL:
//		    	irqFlags &= ~(1<<5);
//		    	return (timer2 & 0xff);
//		    case T2CH:
//		    	irqFlags &= ~(1<<5);
//		    	return (timer2 & 0xff00) >> 8;
//		    case SR:
//		    	return sr;
//		    case ACR:
//		    	return acr;
//		    case PCR:
//		    	return pcr;
//		    case IFR:
//		    	return irqFlags;
//		    case IER:
//		    	return irqEnable;
//		    case PORTA_NOHANDSHAKE:
//		    	return readPortA;
//    		default:
//    			throw new IllegalArgumentException("No register at "+HexDump.toAdr(offset));
//    	}
    }

    public void tick(boolean clock)
    {

    }
}