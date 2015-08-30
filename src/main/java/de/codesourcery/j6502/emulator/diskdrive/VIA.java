package de.codesourcery.j6502.emulator.diskdrive;

import de.codesourcery.j6502.emulator.AddressRange;
import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.Memory;
import de.codesourcery.j6502.emulator.CPU.IRQType;
import de.codesourcery.j6502.utils.HexDump;

/**
 * VIA 6522.
 *
 * Datasheet: http://www.princeton.edu/~mae412/HANDOUTS/Datasheets/6522.pdf
 * @author tobias.gierke@code-sourcery.de
 */
public class VIA extends Memory 
{
    protected static final int IRQBIT_IRQ_OCCURRED = 1<<7;
    protected static final int IRQBIT_TIMER1_TIMEOUT = 1<<6;
    protected static final int IRQBIT_TIMER2_TIMEOUT = 1<<5;
    protected static final int IRQBIT_CB1_ACTIVE_EDGE = 1<<4;
    protected static final int IRQBIT_CB2_ACTIVE_EDGE = 1<<3;
    protected static final int IRQBIT_SHIFTS_COMPLETED = 1<<2;
    protected static final int IRQBIT_CA1_ACTIVE_EDGE = 1<<1;
    protected static final int IRQBIT_CA2_ACTIVE_EDGE = 1<<0;

    public static enum ShiftRegisterMode {
        /*
         * |0|0|0| SR disabled
         * |0|0|1| Shift-in under control of T2
         * |0|1|0| Shift-in under control of phi2
         * |0|1|1| Shift-in under control of ext. clock
         * |1|0|0| Shift-out free-running at T2 rate
         * |1|0|1| Shift-out under control of T2
         * |1|1|0| Shift-out under control of phi2
         * |1|1|1| Shift-out under control of ext. clock         
         */
        DISABLED,
        SHIFT_IN_T2,
        SHIFT_IN_PHI2,
        SHIFT_IN_EXT_CLOCK,
        SHIFT_IN_AT_T2_RATE,
        SHIFT_OUT_FREE_RUN_T2_RATE,
        SHIFT_OUT_T2,
        SHIFT_OUT_PHI2,
        SHIFT_OUT_EXT_CLOCK
    }

    public static enum Timer1Mode {
        /*
         * |7|6| Timer #1 control bits
         * |0|0| Timed interrupt each time T1 is loaded.
         * |0|1| Continuous interrupts
         * |1|0| Timed interrupt each time T1 is loaded. (PB7: one-shot output)
         * |1|1| Continuous interrupts (PB7: square wave output)		 
         */
        IRQ_ON_LOAD,
        CONTINUOUS_IRQ,
        IRQ_ON_LOAD_PB7_ONESHOT,
        CONTINUOUS_IRQ_PB7_SQUARE_WAVE
    }

    public static enum Timer2Mode {
        // bit 5 - Timer #2 control ( 0 = timed interrupt, 1 = count-down with pules on PB6)
        TIMED_INTERRUPT,
        CNT_DOWN_WITH_PB6_PULSE
    }
    public static enum PortName { A,B };

    public interface VIAChangeListener 
    {
        public void portChanged(VIA via,VIA.Port port);
        public void controlLine1Changed(VIA via,VIA.Port port);
        public void controlLine2Changed(VIA via,VIA.Port port);
    }

    private ShiftRegisterMode shiftRegisterMode = ShiftRegisterMode.DISABLED;

    private Timer1Mode timer1Mode=Timer1Mode.IRQ_ON_LOAD;
    private Timer2Mode timer2Mode=Timer2Mode.TIMED_INTERRUPT;

    private boolean timer1Running;
    private boolean timer2Running;

    private final CPU cpu;
    private VIAChangeListener changeListener;

    /* Behaviour of input registers (IR) / output registers (OR)
     *
     * - If you read a pin on IRA and input latching is disabled for port A, then you
     *   will simply read the current state of the corresponding PA pin, regardless of
     *   whether that pin is set to be an input or an output.
     *
     * - If you read a pin on IRA and input latching is enabled for port A, then you
     *   will read the actual IRA, which is the last value that was latched into IRA.
     *
     * - If you read a pin on IRB and the pin is set to be an input with latching
     *   disabled, then you will read the current state of the corresponding PB pin.
     *
     * - If you read a pin on IRB and the pin is set to be an input with latching
     *   enabled, then you will read the actual IRB.
     *
     * - If you read a pin on IRB and the pin is set to be an output, then you will
     *   actually read ORB, which contains the last value that was written to port B.
     *
     * - Writing to a pin which is set to be an input will change the OR for that pin,
     *   but the state of the pin itself will not change as long as the DDR dictates
     *   that it is an input.
     *
     *   Port A:
     *
     *   | Latching |     DDR      |  Result
     *   | disabled |   dont-care  |  state of PA pin
     *   | enabled  |   dont-care  |  state of IR
     *
     *   Port B:
     *
     *   | Latching  |     DDR      |  Result
     *   | disabled  |   input      |  state of PB pin
     *   | enabled   |   input      |  state of IR
     *   | dont-care |   output     |  state of OR <<< !!
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

    private final Port portA = new Port(PortName.A);
    private final Port portB = new Port(PortName.B) 
    {
        @Override
        protected int readRegisterLatchingDisabled()
        {
            int result = 0;
            for ( int i = 7, mask = 1<<7 ; i >= 0 ; i-- , mask = mask >>> 1 )
            {
                if ( isOutput( i ) ) {
                    result |= ( or & mask);
                } else {
                    result |= ( pinsIn & mask);
                }
            }
            return result;
        }

        @Override
        protected int readRegisterLatchingEnabled()
        {
            int result = 0;
            for ( int i = 7, mask = 1<<7 ; i >= 0 ; i-- , mask = mask >>> 1 )
            {
                if ( isOutput( i ) ) {
                    result |= ( or & mask);
                } else {
                    result |= ( ir & mask);
                }
            }
            return result;
        }
    };

    protected class Port
    {
        protected boolean latchingEnabled = false;

        protected int ddr; // 0 = pin is input , 1 = pin is output
        protected int ir;
        protected int or;
        protected int pinsOut;
        protected int pinsIn;

        private boolean controlLine1;
        private boolean controlLine2;

        private final PortName portName;

        public Port(PortName portName) {
            this.portName = portName;
        }
        
        public void reset() 
        {
            ddr = 0;
            ir = 0;
            or = 0;
            pinsOut = 0;
            
            latchingEnabled = false;
            
            controlLine1 = false;
            controlLine2 = false;
        }
        
        public PortName getPortName() {
            return portName;
        }
        
        public int getPinsOut() {
            return pinsOut;
        }

        public final int readRegister()
        {
            return latchingEnabled ? readRegisterLatchingEnabled() : readRegisterLatchingDisabled();
        }

        public void tick()
        {
            int result = pinsOut;
            int oldValue = result;
            int value = or & ddr; // retain only bits for output
            result &= ~ddr; // clear all output bits
            result |= value;
            pinsOut = result;
            if ( oldValue != result ) 
            {
                changeListener.portChanged( VIA.this , this );
            }
        }

        public int getDDR() {
            return ddr;
        }

        public boolean getControlLine1() {
            return controlLine1;
        }

        public boolean getControlLine2() {
            return controlLine2;
        }

        public void setControlLine1(boolean ca1,boolean notifyListeners) 
        {
            if ( this.controlLine1 != ca1 && notifyListeners) {
                this.controlLine1 = ca1;
                changeListener.controlLine1Changed( VIA.this , this );
            } else {
                this.controlLine1 = ca1;
            }
        }

        public void setControlLine2(boolean ca2,boolean notifyListeners) 
        {
            if ( this.controlLine2 != ca2 && notifyListeners) {
                this.controlLine2 = ca2;
                changeListener.controlLine2Changed( VIA.this , this );
            } else {
                this.controlLine2 = ca2;
            }		
        }

        public void setDDR(int ddr) 
        {
            this.ddr = ddr & 0xff;
        }

        public final void writeRegister(int value)
        {
            or = value & 0xff;
        }
        
        public final void setInputPins(int value) {
            this.pinsIn = value & 0xff;
        }
        
        public final int getPinsIn() {
            return pinsIn;
        }

        public final boolean isInput(int bit) {
            return (ddr & (1<<bit)) == 0;
        }

        public final boolean isOutput(int bit) {
            return (ddr & (1<<bit)) != 0;
        }

        protected int readRegisterLatchingDisabled() {
            return pinsIn;
        }

        protected int readRegisterLatchingEnabled() {
            return ir;
        }

        public void setLatchingEnabled(boolean latchingEnabled) {
            this.latchingEnabled = latchingEnabled;
        }

        public void setInputPin(int bit, boolean set) 
        {
            if ( set ) {
                this.pinsIn |= 1<<bit;
            } else {
                this.pinsIn &= ~1<<bit;
            }
        }
    }

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

    protected static enum PCRControlBits 
    {
        /*
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
        INPUT_NEG_ACTIVE_EDGE(0),
        INDEPENDENT_IRQ_INPUT_NEG_EDGE(1),
        INPUT_POS_ACTIVE_EDGE(2),
        INDEPENDENT_IRQ_INPUT_POS_EDGE(3),
        HANDSHAKE_OUTPUT(4),
        PULSE_OUTPUT(5),
        LOW_OUTPUT(6),
        HIGH_OUTPUT(7);

        public final int mask;

        private PCRControlBits(int mask) {
            this.mask = mask;
        }
    }
    protected final class ControlLines 
    {
        private boolean line1;
        private boolean line2;

        private PCRControlBits mode = PCRControlBits.INPUT_NEG_ACTIVE_EDGE;

        // If that bit is 0, CA1 is active-low; If that bit is 1, CA1 is active-high.          
        private boolean irqActiveHi;

        public void setLine1(boolean line1) {
            this.line1 = line1;
        }

        public void setLine2(boolean line2) {
            this.line2 = line2;
        }

        public boolean getLine1() {
            return line1;
        }

        public boolean getLine2() {
            return line2;
        }

        public PCRControlBits getMode() {
            return mode;
        }

        public void setMode(PCRControlBits mode) {
            this.mode = mode;
        }

        public void setIRQActiveHi(boolean activeHi) {
            this.irqActiveHi = activeHi;
        }

        public boolean isIRQActiveHi() {
            return irqActiveHi;
        }
    }

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

    public VIA(String identifier, AddressRange range,CPU cpu)
    {
        super(identifier, range);
        this.cpu = cpu;
    }

    @Override
    public void reset()
    {
        /* Clear all internal registers
         * except t1/t2 counter, latches and SR
         */
        
        portA.reset();
        portB.reset();
        
        timer1Running=false;
        timer2Running=false;

        timer1Mode = Timer1Mode.IRQ_ON_LOAD;
        timer2Mode = Timer2Mode.TIMED_INTERRUPT;

        shiftRegisterMode = ShiftRegisterMode.DISABLED;
    }

    @Override
    public int readByte(int offset)
    {
        switch( offset )
        {
            case PORTB:
                return portB.readRegister();
            case PORTA:
                return portA.readRegister();
            case DDRB:
                return portB.getDDR();
            case DDRA:
                return portA.getDDR();
            case T1CL:
                clearInterupt(IRQBIT_TIMER1_TIMEOUT);
                return timer1 & 0xff;
            case T1CH:
                return (timer1 & 0xff00) >> 8;
            case T1LL:
                return t1latchlo;
            case T1LH:
                return t1latchhi;
            case T2CL:
                clearInterupt(IRQBIT_TIMER2_TIMEOUT);
                return (timer2 & 0xff);
            case T2CH:
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
                return irqEnable | 0b1000_0000; // see spec, bit 7 will always be '1' when reading
            case PORTA_NOHANDSHAKE:
                return portA.readRegister();
            default:
                throw new IllegalArgumentException("No register at "+HexDump.toAdr(offset));
        }
    }

    @Override
    public void writeByte(int offset, byte value)
    {
        switch( offset )
        {
            case PORTA:
                portA.writeRegister( value );
                break;
            case PORTB:
                portB.writeRegister( value );
                break;
            case DDRA:
                portA.setDDR( value );
                break;
            case DDRB:
                portB.setDDR( value );
                break;
            case T1CL:
                timer1 = timer1 & ~0xff;
                timer1 |= (value & 0xff);
                break;
            case T1CH:
                timer1 = timer1 & ~0xff00;
                timer1 |= (value & 0xff) << 8;
                timer1Running = true;
                clearInterupt(IRQBIT_TIMER1_TIMEOUT);
                break;
            case T1LL:
                t1latchlo = value & 0xff;
                break;
            case T1LH:
                t1latchhi = value & 0xff;
                break;
            case T2CL:
                t2latchlo = value & 0xff;
                break;
            case T2CH:
                t2latchhi = value & 0xff;
                timer2 = ( t2latchhi << 8 ) | t2latchlo;
                timer2Running = true;
                clearInterupt(IRQBIT_TIMER2_TIMEOUT);
                break;
            case SR:
                this.sr = value & 0xff;
                break;
            case ACR:
                this.acr = value & 0xff;
                /*
                 * bit 1 - port B latching ( 0 = disable , 1 = enable)
                 * bit 0 - port A latching ( 0 = disable , 1 = enable)
                 * 
                 * |7|6| Timer #1 control bits
                 * |0|0| Timed interrupt each time T1 is loaded.
                 * |0|1| Continuous interrupts
                 * |1|0| Timed interrupt each time T1 is loaded. (PB7: one-shot output)
                 * |1|1| Continuous interrupts (PB7: square wave output)
                 */
                switch( (value & 0b1100_0000) >> 8 ) {
                    case 0: timer1Mode = Timer1Mode.IRQ_ON_LOAD; break;
                    case 1: timer1Mode = Timer1Mode.CONTINUOUS_IRQ; break;
                    case 2: timer1Mode = Timer1Mode.IRQ_ON_LOAD_PB7_ONESHOT; break;
                    case 3: timer1Mode = Timer1Mode.CONTINUOUS_IRQ_PB7_SQUARE_WAVE; break;
                    default:
                        throw new RuntimeException("Unreachable code reached");
                }
                if ( (value & 1<<5) == 0 ) {
                    // // bit 5 - Timer #2 control ( 0 = timed interrupt, 1 = count-down with pules on PB6)
                    timer2Mode = Timer2Mode.TIMED_INTERRUPT;
                } else {
                    timer2Mode = Timer2Mode.CNT_DOWN_WITH_PB6_PULSE;
                    throw new RuntimeException("Unimplemented timer #2 mode: "+Timer2Mode.CNT_DOWN_WITH_PB6_PULSE);
                }
                switch( (value & 0b0001_1100) >> 2) 
                {
                    /*
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
                    case 0: 
                        shiftRegisterMode = ShiftRegisterMode.DISABLED; 
                        break;
                    case 1: 
                        shiftRegisterMode = ShiftRegisterMode.SHIFT_IN_T2; 
                        throw new RuntimeException("Unsupported shift register mode: "+shiftRegisterMode);
                    case 2: 
                        shiftRegisterMode = ShiftRegisterMode.SHIFT_IN_PHI2;
                        throw new RuntimeException("Unsupported shift register mode: "+shiftRegisterMode);
                    case 3: 
                        shiftRegisterMode = ShiftRegisterMode.SHIFT_IN_EXT_CLOCK; 
                        throw new RuntimeException("Unsupported shift register mode: "+shiftRegisterMode);
                    case 4: 
                        shiftRegisterMode = ShiftRegisterMode.SHIFT_OUT_FREE_RUN_T2_RATE;
                        throw new RuntimeException("Unsupported shift register mode: "+shiftRegisterMode);
                    case 5: 
                        shiftRegisterMode = ShiftRegisterMode.SHIFT_OUT_T2; 
                        throw new RuntimeException("Unsupported shift register mode: "+shiftRegisterMode);
                    case 6: 
                        shiftRegisterMode = ShiftRegisterMode.SHIFT_OUT_PHI2;
                        throw new RuntimeException("Unsupported shift register mode: "+shiftRegisterMode);
                    case 7: 
                        shiftRegisterMode = ShiftRegisterMode.SHIFT_OUT_EXT_CLOCK;
                        throw new RuntimeException("Unsupported shift register mode: "+shiftRegisterMode);
                        default:
                            throw new RuntimeException("Unreachable code reached");
                }
                portA.setLatchingEnabled( ( value & 1 ) != 0 );
                portB.setLatchingEnabled( ( value & 2 ) != 0 );
                break;
            case PCR:
                this.pcr = value & 0xff;
                break;
            case IFR:
                this.irqFlags = this.irqFlags & ~(value & 0b0111_1111);
                if ( ( irqFlags & 0b0111_1111) == 0 ) { // clear global irq flag bit if all IRQs are acknowledged 
                    irqFlags &= 0b0111_1111;
                }
                break;
            case IER:
                if ( ( value & 0b1000_0000 ) == 0 ) { // clear bits
                    value &= 0b0111_1111;
                    this.irqEnable &= ~value;
                } else { // set bits
                    value &= 0b0111_1111;
                    this.irqEnable |= value;
                }
                break;
            case PORTA_NOHANDSHAKE:
                portA.writeRegister( value );
                break;
            default:
                throw new IllegalArgumentException("No register at "+HexDump.toAdr(offset));
        }
    }

    public void tick()
    {
        if ( timer1Running && --timer1 == 0 ) {
            switch( timer1Mode ) 
            {
                case IRQ_ON_LOAD:
                case CONTINUOUS_IRQ:
                    setInterrupt(IRQBIT_TIMER1_TIMEOUT);
                    timer1 = t1latchhi << 8 | t1latchlo;
                    break;
                case CONTINUOUS_IRQ_PB7_SQUARE_WAVE:
                case IRQ_ON_LOAD_PB7_ONESHOT:
                default:
                    throw new RuntimeException("Unimplemented timer #1 mode: "+timer1Mode);
            }
        }
        if ( timer2Running && --timer2 == 0 ) 
        {
            setInterrupt( IRQBIT_TIMER2_TIMEOUT );
            timer2 = t2latchhi << 8 | t2latchlo;	
        }
        portA.tick();
        portB.tick();
    }
    
    private void setInterrupt(int bitMask) 
    {
        if ( (irqEnable & bitMask) != 0 ) {
            if ( ( irqFlags & bitMask) == 0 ) {
                irqFlags |= ( IRQBIT_IRQ_OCCURRED | bitMask );
                cpu.queueInterrupt( IRQType.REGULAR );
            }
        } else {
            irqFlags |= ( IRQBIT_IRQ_OCCURRED | bitMask );
        }
    }
    
    private void clearInterupt(int bitMask) 
    {
        irqFlags &= ~bitMask;
        if ( ( irqFlags & 0b0111_1111) == 0 ) { // clear global irq flag bit if all IRQs are acknowledged 
            irqFlags &= 0b0111_1111;
        }
    }

    public Port getPortA() {
        return portA;
    }

    public Port getPortB() {
        return portB;
    }

    public void setChangeListener(VIAChangeListener changeListener) {
        this.changeListener = changeListener;
    }
}