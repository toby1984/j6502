package de.codesourcery.j6502.emulator.diskdrive;

import de.codesourcery.j6502.emulator.AddressRange;
import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.Memory;
import de.codesourcery.j6502.emulator.diskdrive.VIA.Port;
import de.codesourcery.j6502.emulator.diskdrive.VIA.VIAChangeListener;
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

        cpu.reset();  
        via.reset();

        via.setChangeListener( new VIAChangeListener() {

            @Override
            public void portChanged(VIA via, Port port) {
                // TODO Auto-generated method stub

            }

            @Override
            public void controlLine2Changed(VIA via, Port port) {
                // TODO Auto-generated method stub

            }

            @Override
            public void controlLine1Changed(VIA via, Port port) {
                // TODO Auto-generated method stub

            }
        });
    }

    public void testReadInputPortALatchingDisabled() 
    {
        via.getPortA().setInputPins( 0xff );
        via.writeByte( VIA.DDRA , (byte) 0b0000_0000); // switch all pins to be inputs (0 = input , 1 = output)
        via.writeByte( VIA.PORTA , (byte) 0x00); // should only write to ORA but pins remain the same

        assertEquals( 0xff , via.readByte( VIA.PORTA ) );
        assertEquals( 0xff , via.getPortA().getPins() );
    }

    public void testReadOutputPortALatchingDisabled() 
    {
        via.getPortA().setInputPins( 0xab );
        via.writeByte( VIA.DDRA , (byte) 0b1111_1111); // switch all pins to be outputs (0 = input , 1 = output)
        via.writeByte( VIA.PORTA , (byte) 0x55); // pins == OR

        assertEquals( 0x55 , via.readByte( VIA.PORTA ) ); // read should return level on PA pins
        assertEquals( 0x55 , via.getPortA().getPins() ); // 
    }   

    public void testTimer1ReadingLowOrderLatchDoesNotClearIRQ() 
    {
        /* ACR:
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
         */
        via.writeByte( VIA.ACR , (byte) 0b0000_0000 );

        /* IER:
         * 
         * bit 7 - set/clear , 1 => sets all bits that are set to 1 , 0 => clears all bits that are set to 1
         * bit 6 - Timer 1
         * bit 5 - Timer 2
         * bit 4 - CB1
         * bit 3 - CB2
         * bit 2 - Shift register
         * bit 1 - CA1
         * bit 0 - CA2
         */
        via.writeByte( VIA.IER , (byte) 0b1100_0000 ); // enable timer1 IRQ
        via.writeByte( VIA.T1LL , (byte) 0x05 );
        via.writeByte( VIA.T1LH , (byte) 0x06 ); 
        via.writeByte( VIA.T1CH , (byte) 0x06 ); // write & start timer

        for ( int i = 0 ; i < 0x0604 ; i++ ) {
            via.tick();
        }
        assertFalse( cpu.isInterruptQueued() );
        assertEquals( 0 , via.readByte( VIA.IFR ) );
        
        via.tick();
        
        assertTrue( cpu.isInterruptQueued() );
        assertEquals( 0b1100_0000 , via.readByte( VIA.IFR ) );
        
        via.tick();
        via.tick();
        via.tick();
        
        assertEquals( 5 , via.readByte( VIA.T1LL ) );
        
        assertTrue( cpu.isInterruptQueued() );
        assertEquals( 0b1100_0000 , via.readByte( VIA.IFR ) );
    }
    
    public void testTimer1ReadingHighOrderLatchDoesNotClearIRQ() 
    {
        /* ACR:
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
         */
        via.writeByte( VIA.ACR , (byte) 0b0000_0000 );

        /* IER:
         * 
         * bit 7 - set/clear , 1 => sets all bits that are set to 1 , 0 => clears all bits that are set to 1
         * bit 6 - Timer 1
         * bit 5 - Timer 2
         * bit 4 - CB1
         * bit 3 - CB2
         * bit 2 - Shift register
         * bit 1 - CA1
         * bit 0 - CA2
         */
        via.writeByte( VIA.IER , (byte) 0b1100_0000 ); // enable timer1 IRQ
        via.writeByte( VIA.T1LL , (byte) 0x05 );
        via.writeByte( VIA.T1LH , (byte) 0x06 ); 
        via.writeByte( VIA.T1CH , (byte) 0x06 ); // write & start timer

        for ( int i = 0 ; i < 0x0604 ; i++ ) {
            via.tick();
        }
        assertFalse( cpu.isInterruptQueued() );
        assertEquals( 0 , via.readByte( VIA.IFR ) );
        
        via.tick();
        
        assertTrue( cpu.isInterruptQueued() );
        assertEquals( 0b1100_0000 , via.readByte( VIA.IFR ) );
        
        via.tick();
        via.tick();
        via.tick();
        
        assertEquals( 0x06 , via.readByte( VIA.T1LH ) );
        
        assertTrue( cpu.isInterruptQueued() );
        assertEquals( 0b1100_0000 , via.readByte( VIA.IFR ) );
    }    
    
    public void testTimer1ReadingLowOrderCounterClearsIRQ() 
    {
        /* ACR:
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
         */
        via.writeByte( VIA.ACR , (byte) 0b0000_0000 );

        /* IER:
         * 
         * bit 7 - set/clear , 1 => sets all bits that are set to 1 , 0 => clears all bits that are set to 1
         * bit 6 - Timer 1
         * bit 5 - Timer 2
         * bit 4 - CB1
         * bit 3 - CB2
         * bit 2 - Shift register
         * bit 1 - CA1
         * bit 0 - CA2
         */
        via.writeByte( VIA.IER , (byte) 0b1100_0000 ); // enable timer1 IRQ
        via.writeByte( VIA.T1LL , (byte) 0x05 );
        via.writeByte( VIA.T1LH , (byte) 0x06 ); 
        via.writeByte( VIA.T1CH , (byte) 0x06 ); // write & start timer

        for ( int i = 0 ; i < 0x0604 ; i++ ) {
            via.tick();
        }
        assertFalse( cpu.isInterruptQueued() );
        assertEquals( 0 , via.readByte( VIA.IFR ) );
        
        via.tick();
        
        assertTrue( cpu.isInterruptQueued() );
        assertEquals( 0b1100_0000 , via.readByte( VIA.IFR ) );
        
        assertEquals( 0x05 , via.readByte( VIA.T1CL ) );
        
        assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );
    }       
    
    public void testTimer1WritingHighOrderCounterClearsIRQ() 
    {
        /* ACR:
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
         */
        via.writeByte( VIA.ACR , (byte) 0b0000_0000 );

        /* IER:
         * 
         * bit 7 - set/clear , 1 => sets all bits that are set to 1 , 0 => clears all bits that are set to 1
         * bit 6 - Timer 1
         * bit 5 - Timer 2
         * bit 4 - CB1
         * bit 3 - CB2
         * bit 2 - Shift register
         * bit 1 - CA1
         * bit 0 - CA2
         */
        via.writeByte( VIA.IER , (byte) 0b1100_0000 ); // enable timer1 IRQ
        via.writeByte( VIA.T1LL , (byte) 0x05 );
        via.writeByte( VIA.T1LH , (byte) 0x06 ); 
        via.writeByte( VIA.T1CH , (byte) 0x06 ); // write & start timer

        for ( int i = 0 ; i < 0x0604 ; i++ ) {
            via.tick();
        }
        assertFalse( cpu.isInterruptQueued() );
        assertEquals( 0 , via.readByte( VIA.IFR ) );
        
        via.tick();
        
        assertTrue( cpu.isInterruptQueued() );
        assertEquals( 0b1100_0000 , via.readByte( VIA.IFR ) );
        
        via.writeByte(VIA.T1CH , (byte) 0x32 );
        assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );
        
        assertEquals( 0x05 , via.readByte( VIA.T1CL ) );
        assertEquals( 0x06 , via.readByte( VIA.T1CH ) );
    }      
    
    public void testTimer2ReadingLowOrderLatchCounterClearsIRQ() 
    {
        /* ACR:
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
         */
        via.writeByte( VIA.ACR , (byte) 0b0000_0000 );

        /* IER:
         * 
         * bit 7 - set/clear , 1 => sets all bits that are set to 1 , 0 => clears all bits that are set to 1
         * bit 6 - Timer 1
         * bit 5 - Timer 2
         * bit 4 - CB1
         * bit 3 - CB2
         * bit 2 - Shift register
         * bit 1 - CA1
         * bit 0 - CA2
         */
        via.writeByte( VIA.IER , (byte) 0b1010_0000 ); // enable timer1 IRQ
        via.writeByte( VIA.T2CL , (byte) 0x05 );
        via.writeByte( VIA.T2CH , (byte) 0x06 ); // start timer 

        for ( int i = 0 ; i < 0x0604 ; i++ ) {
            via.tick();
        }
        assertFalse( cpu.isInterruptQueued() );
        assertEquals( 0 , via.readByte( VIA.IFR ) );
        
        via.tick();
        
        assertTrue( cpu.isInterruptQueued() );
        assertEquals( 0b1010_0000 , via.readByte( VIA.IFR ) );
        
        assertEquals( 0x05 , via.readByte(VIA.T2CL));
        assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );
    }   
    
    public void testTimer2WritingHighOrderLatchCounterClearsIRQ() 
    {
        /* ACR:
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
         */
        via.writeByte( VIA.ACR , (byte) 0b0000_0000 );

        /* IER:
         * 
         * bit 7 - set/clear , 1 => sets all bits that are set to 1 , 0 => clears all bits that are set to 1
         * bit 6 - Timer 1
         * bit 5 - Timer 2
         * bit 4 - CB1
         * bit 3 - CB2
         * bit 2 - Shift register
         * bit 1 - CA1
         * bit 0 - CA2
         */
        via.writeByte( VIA.IER , (byte) 0b1010_0000 ); // enable timer1 IRQ
        via.writeByte( VIA.T2CL , (byte) 0x05 );
        via.writeByte( VIA.T2CH , (byte) 0x06 ); // start timer 

        for ( int i = 0 ; i < 0x0604 ; i++ ) {
            via.tick();
        }
        assertFalse( cpu.isInterruptQueued() );
        assertEquals( 0 , via.readByte( VIA.IFR ) );
        
        via.tick();
        
        assertTrue( cpu.isInterruptQueued() );
        via.writeByte( VIA.T2CH , (byte) 0x07 );
        
        assertEquals( 0b0000_0000 , via.readByte( VIA.IFR ) );
    }  
    
    public void testTimer1ReadingHighOrderCounterDoesNotClearIRQ() 
    {
        /* ACR:
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
         */
        via.writeByte( VIA.ACR , (byte) 0b0000_0000 );

        /* IER:
         * 
         * bit 7 - set/clear , 1 => sets all bits that are set to 1 , 0 => clears all bits that are set to 1
         * bit 6 - Timer 1
         * bit 5 - Timer 2
         * bit 4 - CB1
         * bit 3 - CB2
         * bit 2 - Shift register
         * bit 1 - CA1
         * bit 0 - CA2
         */
        via.writeByte( VIA.IER , (byte) 0b1100_0000 ); // enable timer1 IRQ
        via.writeByte( VIA.T1LL , (byte) 0x05 );
        via.writeByte( VIA.T1LH , (byte) 0x06 ); 
        via.writeByte( VIA.T1CH , (byte) 0x06 ); // write & start timer

        for ( int i = 0 ; i < 0x0604 ; i++ ) {
            via.tick();
        }
        assertFalse( cpu.isInterruptQueued() );
        assertEquals( 0 , via.readByte( VIA.IFR ) );
        
        via.tick();
        
        assertTrue( cpu.isInterruptQueued() );
        assertEquals( 0b1100_0000 , via.readByte( VIA.IFR ) );
        
        assertEquals( 0x06 , via.readByte( VIA.T1CH ) );
        
        assertEquals( 0b1100_0000 , via.readByte( VIA.IFR ) );
    }       

    public void testReadOutputPortALatchingEnabled() 
    {
        via.getPortA().setInputPins( 0xab );

        /* ACR:
         * bit 7 - Timer #1 control ( see below )
         * bit 6 - Timer #1 control ( see below )
         * bit 5 - Timer #2 control ( 0 = timed interrupt, 1 = count-down with pules on PB6)
         * bit 4 - shift register control (see below)
         * bit 3 - shift register control (see below)
         * bit 2 - shift register control (see below)
         * bit 1 - port B latching ( 0 = disable , 1 = enable)
         * bit 0 - port A latching ( 0 = disable , 1 = enable)
         */
        via.writeByte( VIA.ACR , (byte) 0b0000_00001 ); // enable latching on Port A
        via.writeByte( VIA.DDRA , (byte) 0b11111_1111); // switch all pins to be outputs (0 = input , 1 = output)
        via.writeByte( VIA.PORTA , (byte) 0x55); // should only write to ORA but pins remain the same

        assertEquals( 0xab , via.readByte( VIA.PORTA ) ); // returns IR from the last time latching occurred
        assertEquals( 0x55 , via.getPortA().getPins() );
    }        

    public void testReadOutputPortBLatchingDisabled() 
    {
        via.getPortB().setInputPins( 0xab );
        via.writeByte( VIA.DDRB , (byte) 0b1111_1111); // switch all pins to be outputs (0 = input , 1 = output)
        via.writeByte( VIA.PORTB , (byte) 0x55); // pins == OR

        assertEquals( 0x55 , via.readByte( VIA.PORTB ) ); // read should return level on PA pins
        assertEquals( 0x55 , via.getPortB().getPins() ); // 
    }     

    public void testSwitchingPortADDRFromInputToOutputUpdatesPins() 
    {
        via.writeByte( VIA.DDRA , (byte) 0b0000_0000); // switch all pins to be inputs (0 = input , 1 = output)
        via.writeByte( VIA.PORTA , (byte) 0x55); // pins == OR
        via.getPortA().setInputPins( 0xab );

        assertEquals( 0xab , via.readByte( VIA.PORTA ) ); // read should return level on PA pins
        assertEquals( 0xab , via.getPortA().getPins() ); // 

        via.writeByte( VIA.DDRA , (byte) 0b1111_1111); // switch all pins to be outputs (0 = input , 1 = output)

        assertEquals( 0x55 , via.readByte( VIA.PORTA ) ); // read should return level on PA pins
        assertEquals( 0x55 , via.getPortA().getPins() ); // 
    }  

    public void testSwitchingPortBDDRFromInputToOutputUpdatesPins() 
    {
        via.writeByte( VIA.DDRB , (byte) 0b0000_0000); // switch all pins to be inputs (0 = input , 1 = output)
        via.writeByte( VIA.PORTB , (byte) 0x55); // pins == OR
        via.getPortB().setInputPins( 0xab );

        assertEquals( 0xab , via.readByte( VIA.PORTB ) ); // read should return level on PA pins
        assertEquals( 0xab , via.getPortB().getPins() ); // 

        via.writeByte( VIA.DDRB , (byte) 0b1111_1111); // switch all pins to be outputs (0 = input , 1 = output)

        assertEquals( 0x55 , via.readByte( VIA.PORTB ) ); // read should return level on PA pins
    }

    public void testReadInputPortALatchingEnabled() 
    {
        via.getPortA().setInputPins( 0xff );

        /* ACR:
         * bit 7 - Timer #1 control ( see below )
         * bit 6 - Timer #1 control ( see below )
         * bit 5 - Timer #2 control ( 0 = timed interrupt, 1 = count-down with pules on PB6)
         * bit 4 - shift register control (see below)
         * bit 3 - shift register control (see below)
         * bit 2 - shift register control (see below)
         * bit 1 - port B latching ( 0 = disable , 1 = enable)
         * bit 0 - port A latching ( 0 = disable , 1 = enable)
         */
        via.writeByte( VIA.ACR , (byte) 0b0000_00001 ); // enable latching on Port A
        via.writeByte( VIA.DDRA , (byte) 0b0000_0000); // switch all pins to be inputs (0 = input , 1 = output)
        via.writeByte( VIA.PORTA , (byte) 0x00); // should only write to ORA but pins remain the same

        assertEquals( 0xff , via.readByte( VIA.PORTA ) );
        assertEquals( 0xff , via.getPortA().getPins() );
    }    

    public void testReadInputPortBLatchingDisabled() 
    {
        via.getPortB().setInputPins( 0xff );
        via.writeByte( VIA.DDRB , (byte) 0b0000_0000); // switch all pins to be inputs (0 = input , 1 = output)
        via.writeByte( VIA.PORTB , (byte) 0x55); // should only write to ORA but pins remain the same

        assertEquals( 0xff , via.readByte( VIA.PORTB ) );
        assertEquals( 0xff , via.getPortB().getPins() );
    }

    public void testReadInputPortBLatchingEnabled() 
    {
        via.getPortB().setInputPins( 0xff );

        /* ACR:
         * bit 7 - Timer #1 control ( see below )
         * bit 6 - Timer #1 control ( see below )
         * bit 5 - Timer #2 control ( 0 = timed interrupt, 1 = count-down with pules on PB6)
         * bit 4 - shift register control (see below)
         * bit 3 - shift register control (see below)
         * bit 2 - shift register control (see below)
         * bit 1 - port B latching ( 0 = disable , 1 = enable)
         * bit 0 - port A latching ( 0 = disable , 1 = enable)
         */
        via.writeByte( VIA.ACR , (byte) 0b0000_00010 ); // enable latching on Port B
        via.writeByte( VIA.DDRB , (byte) 0b0000_0000); // switch all pins to be inputs (0 = input , 1 = output)
        via.writeByte( VIA.PORTB , (byte) 0x55); // should only write to ORA but pins remain the same

        assertEquals( 0xff , via.readByte( VIA.PORTB ) );
        assertEquals( 0xff , via.getPortB().getPins() );
    }      



    public void testNoIRQsAreTriggeredAfterReset()
    {
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
         * |7|6| Timer #1 control bits
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
