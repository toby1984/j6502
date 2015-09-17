package de.codesourcery.j6502.emulator;

import java.time.LocalDateTime;
import java.time.temporal.ChronoField;

import de.codesourcery.j6502.emulator.CPU.IRQType;
import de.codesourcery.j6502.utils.Misc;

/**
 * Very inaccurate CIA6526 implementation.
 *
 * TODO: Have a look at http://ist.uwaterloo.ca/~schepers/MJK/cia6526.html to do it better.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class CIA extends Memory
{
	private static final boolean DEBUG = true;
	private static final boolean DEBUG_VERBOSE = false;

	public static final int CIA1_PRA        = 0x00;
	public static final int CIA1_PRB        = 0x01;
	public static final int CIA1_DDRA       = 0x02;
	public static final int CIA1_DDRB       = 0x03;
	public static final int CIA1_TALO       = 0x04;
	public static final int CIA1_TAHI       = 0x05;
	public static final int CIA1_TBLO       = 0x06;
	public static final int CIA1_TBHI       = 0x07;
	public static final int CIA1_TOD_10THS  = 0x08;
	public static final int CIA1_TOD_SECOND = 0x09;
	public static final int CIA1_TOD_MIN    = 0x0a;
	public static final int CIA1_TOD_HOUR   = 0x0b;
	public static final int CIA1_SDR        = 0x0c;
	public static final int CIA1_ICR        = 0x0d;
	public static final int CIA1_CRA        = 0x0e;
	public static final int CIA1_CRB        = 0x0f;

	public static final int CIA2_PRA        = 0x00;
	public static final int CIA2_PRB        = 0x01;
	public static final int CIA2_DDRA       = 0x02;
	public static final int CIA2_DDRB       = 0x03;
	public static final int CIA2_TALO       = 0x04;
	public static final int CIA2_TAHI       = 0x05;
	public static final int CIA2_TBLO       = 0x06;
	public static final int CIA2_TBHI       = 0x07;
	public static final int CIA2_TOD_10THS  = 0x08;
	public static final int CIA2_TOD_SECOND = 0x09;
	public static final int CIA2_TOD_MIN    = 0x0a;
	public static final int CIA2_TOD_HOUR   = 0x0b;
	public static final int CIA2_SDR        = 0x0c;
	public static final int CIA2_ICR        = 0x0d;
	public static final int CIA2_CRA        = 0x0e;
	public static final int CIA2_CRB        = 0x0f;

	/*
----
$DC00
PRA 	Data Port A 	Monitoring/control of the 8 data lines of Port A

        Read/Write: Bit 0..7 keyboard matrix columns
        Read: Joystick Port 2: Bit 0..3 Direction (Left/Right/Up/Down), Bit 4 Fire button. 0 = activated.
        Read: Lightpen: Bit 4 (as fire button), connected also with "/LP" (Pin 9) of the VIC
        Read: Paddles: Bit 2..3 Fire buttons, Bit 6..7 Switch control port 1 (%01=Paddles A) or 2 (%10=Paddles B)
----
$DC01
PRB 	Data Port B 	Monitoring/control of the 8 data lines of Port B. The lines are used for multiple purposes:

        Read/Write: Bit 0..7 keyboard matrix rows
        Read: Joystick Port 1: Bit 0..3 Direction (Left/Right/Up/Down), Bit 4 Fire button. 0 = activated.
        Read: Bit 6: Timer A: Toggle/Impulse output (see register 14 bit 2)
        Read: Bit 7: Timer B: Toggle/Impulse output (see register 15 bit 2)
----
$DC02
DDRA 	Data Direction Port A
        Bit X: 0=Input (read only), 1=Output (read and write)
----
$DC03
DDRB 	Data Direction Port B
        Bit X: 0=Input (read only), 1=Output (read and write)
----
$DC04
TA LO 	Timer A Low Byte
        Read: actual value Timer A (Low Byte)
        Writing: Set latch of Timer A (Low Byte)
----
$DC05
TA HI 	Timer A
        High Byte 	Read: actual value Timer A (High Byte)
        Writing: Set latch of timer A (High Byte) - if the timer is stopped, the high-byte will automatically be re-set as well
----
$DC06 	56326 	6
TB LO 	Timer B
        Low Byte 	Read: actual value Timer B (Low Byte)

        Writing: Set latch of Timer B (Low Byte)
----
$DC07 	56327 	7
TB HI 	Timer B
         High Byte 	Read: actual value Timer B (High Byte)

         Writing: Set latch of timer B (High Byte) - if the timer is stopped, the high-byte will automatically be re-set as well
----
$DC08 	56328 	8
TOD 10THS 	Real Time Clock
         1/10s 	Read:

         Bit 0..3: Tenth seconds in BCD-format ($0-$9)
         Bit 4..7: always 0
         Writing:
         Bit 0..3: if CRB-Bit7=0: Set the tenth seconds in BCD-format
         Bit 0..3: if CRB-Bit7=1: Set the tenth seconds of the alarm time in BCD-format
----------
$DC09 	56329 	9
TOD SEC 	Real Time Clock
         Seconds 	Bit 0..3: Single seconds in BCD-format ($0-$9)

         Bit 4..6: Ten (0..9) seconds in BCD-format ($0-$5)
         Bit 7: always 0
------------
$DC0A 	56330 	10
TOD MIN 	Real Time Clock
        Minutes 	Bit 0..3: Single minutes in BCD-format( $0-$9)

        Bit 4..6: Ten minutes in BCD-format ($0-$5)
        Bit 7: always 0
---------
$DC0B 	56331 	11
TOD HR 	Real Time Clock
        Hours 	Bit 0..3: Single hours in BCD-format ($0-$9)

        Bit 4..6: Ten hours in BCD-format ($0-$5)
        Bit 7: Differentiation AM/PM, 0=AM, 1=PM
        Writing into this register stops TOD, until register 8 (TOD 10THS) will be read.
---------------
$DC0C 	56332 	12
SDR 	Serial shift register
        The byte within this register will be shifted bitwise to or from the SP-pin with every positive slope at the CNT-pin.
---------------
$DC0D 	56333 	13
ICR 	Interrupt Control and status 	CIA1 is connected to the IRQ-Line.

        Read: (Bit0..4 = INT DATA, Origin of the interrupt)

        Bit 0: 1 = Underflow Timer A
        Bit 1: 1 = Underflow Timer B
        Bit 2: 1 = Time of day and alarm time is equal
        Bit 3: 1 = SDR full or empty, so full byte was transferred, depending of operating mode serial bus
        Bit 4: 1 = IRQ Signal occured at FLAG-pin (cassette port Data input, serial bus SRQ IN)
        Bit 5..6: always 0
        Bit 7: 1 = IRQ An interrupt occured, so at least one bit of INT MASK and INT DATA is set in both registers.

        Flags will be cleared after reading the register!

        Write: (Bit 0..4 = INT MASK, Interrupt mask)

        Bit 0: 1 = Interrupt release through timer A underflow
        Bit 1: 1 = Interrupt release through timer B underflow
        Bit 2: 1 = Interrupt release if clock=alarmtime
        Bit 3: 1 = Interrupt release if a complete byte has been received/sent.
        Bit 4: 1 = Interrupt release if a positive slope occurs at the FLAG-Pin.
        Bit 5..6: unused
        Bit 7: Source bit. 0 = set bits 0..4 are clearing the according mask bit. 1 = set bits 0..4 are setting the according mask bit. If all bits 0..4 are cleared, there will be no change to the mask.
-------------
$DC0E 	56334 	14
CRA 	Control Timer A

        Bit 0: 0 = Stop timer; 1 = Start timer
        Bit 1: 1 = Indicates a timer underflow at port B in bit 6.
        Bit 2: 0 = Through a timer overflow, bit 6 of port B will get high for one cycle , 1 = Through a timer underflow, bit 6 of port B will be inverted
        Bit 3: 0 = Timer-restart after underflow (latch will be reloaded), 1 = Timer stops after underflow.
        Bit 4: 1 = Load latch into the timer once.
        Bit 5: 0 = Timer counts system cycles, 1 = Timer counts positive slope at CNT-pin
        Bit 6: Direction of the serial shift register, 0 = SP-pin is input (read), 1 = SP-pin is output (write)
        Bit 7: Real Time Clock, 0 = 60 Hz, 1 = 50 Hz
-----------------
$DC0F 	56335 	15
CRB 	Control Timer B

        Bit 0: 0 = Stop timer; 1 = Start timer
        Bit 1: 1 = Indicates a timer underflow at port B in bit 7.
        Bit 2: 0 = Through a timer overflow, bit 7 of port B will get high for one cycle , 1 = Through a timer underflow, bit 7 of port B will be inverted
        Bit 3: 0 = Timer-restart after underflow (latch will be reloaded), 1 = Timer stops after underflow.
        Bit 4: 1 = Load latch into the timer once.
        Bit 5..6:

        %00 = Timer counts System cycle
        %01 = Timer counts positive slope on CNT-pin
        %10 = Timer counts underflow of timer A
        %11 = Timer counts underflow of timer A if the CNT-pin is high

        Bit 7: 0 = Writing into the TOD register sets the clock time, 1 = Writing into the TOD register sets the alarm time.

------------------
$DC10-$DCFF 	56336-56575 	- 	- 	The CIA 1 register are mirrored each 16 Bytes
[edit] CIA 2

The second CIA-chip is identical to the first. Therefore in the following table are only entries which are specific to the usage in the C64.
Adress range: $DD00-$DDFF, 56576-56831 Tasks: Serial bus, RS-232, VIC memory, NMI control


Hex 	Adress
Dec 	Register 	Function 	Remark
$DD00 	56576 	0
PRA 	Data Port A

		Bit 0..1: Select the position of the VIC-memory

		     %00, 0: Bank 3: $C000-$FFFF, 49152-65535
		     %01, 1: Bank 2: $8000-$BFFF, 32768-49151
		     %10, 2: Bank 1: $4000-$7FFF, 16384-32767
		     %11, 3: Bank 0: $0000-$3FFF, 0-16383 (standard)

		Bit 2: RS-232: TXD Output, userport: Data PA 2 (pin M)
		Bit 3..5: serial bus Output (0=High/Inactive, 1=Low/Active)

		    Bit 3: ATN OUT
		    Bit 4: CLOCK OUT
		    Bit 5: DATA OUT

		Bit 6..7: serial bus Input (0=Low/Active, 1=High/Inactive)

		    Bit 6: CLOCK IN
		    Bit 7: DATA IN

$DD01 	56577 	1
PRB 	Data Port B 	Bit 0..7: userport Data PB 0-7 (Pins C,D,E,F,H,J,K,L)

		The KERNAL offers several RS232-Routines, which use the pins as followed:
		Bit 0, 3..7: RS-232: reading

		    Bit 0: RXD
		    Bit 3: RI
		    Bit 4: DCD
		    Bit 5: User port pin J
		    Bit 6: CTS
		    Bit 7: DSR

		Bit 1..5: RS-232: writing

		    Bit 1: RTS
		    Bit 2: DTR
		    Bit 3: RI
		    Bit 4: DCD
		    Bit 5: User port pin J

$DD02 	56578 	2
DDRA 	Data direction
        Port A 	see CIA 1

$DD03 	56579 	3
        DDRB 	Data direction
Port B 	see CIA 1
$DD04 	56580 	4
TA LO 	Timer A
Low Byte 	see CIA 1
$DD05 	56581 	5
TA HI 	Timer A
High Byte 	see CIA 1
$DD06 	56582 	6
TB LO 	Timer B
Low Byte 	see CIA 1
$DD07 	56583 	7
TB HI 	Timer B
High Byte 	see CIA 1
$DD08 	56584 	8
TOD 10THS 	Real Time Clock
1/10s 	see CIA 1
$DD09 	56585 	9
TOD SEC 	Real Time Clock
Seconds 	see CIA 1
$DD0A 	56586 	10
TOD MIN 	Real Time Clock
Minutes 	see CIA 1
--------------
$DD0B 	56587 	11
TOD HR 	Real Time Clock
Hours 	see CIA 1
--------------
$DD0C 	56588 	12
SDR 	Serial
shift register 	see CIA 1
--------------
$DD0D 	56589 	13
ICR 	Interrupt control
and status 	CIA2 is connected to the NMI-Line.

Bit 4: 1 = NMI Signal occured at FLAG-pin (RS-232 data received)
Bit 7: 1 = NMI An interrupt occured, so at least one bit of INT MASK and INT DATA is set in both registers.
--------------
$DD0E 	56590 	14
CRA 	Control Timer A 	see CIA 1
--------------
$DD0F 	56591 	15
CRB 	Control Timer B 	see CIA 1
	 */

	private long tickCounter = 0;
	private boolean todRunning;

	// real-time clock
	private TimeOfDay timeOfDay = TimeOfDay.AM;
	private int tod10s = 0; // 10ths of seconds
	private int todSeconds = 0; // seconds
	private int todMinutes = 0; // minutes
	private int todHours= 0; // hours

	// RTC alarm time
	private boolean rtcAlarmIRQEnabled;
	private TimeOfDay todAlarmTimeOfDay;
	private int todAlarm10s = 0; // 10ths of seconds
	private int todAlarmSeconds = 0; // seconds
	private int todAlarmMinutes = 0; // minutes
	private int todAlarmHours= 0; // hours

	protected static enum TimeOfDay
	{
		AM,PM;
		public TimeOfDay flip() { return this == AM ? PM : AM; }
	}

	private int irqMask;
	private int icr_read;

	private boolean timerARunning = false;
	private boolean timerBRunning = false;

	private int timerAValue;
	private int timerALatch;

	private int timerBValue;
	private int timerBLatch;

	public CIA(String identifier, AddressRange range)
	{
		super(identifier, MemoryType.IOAREA , range);
	}

	@Override
	public boolean isReadsReturnWrites(int offset) {
        return false; // not for all registers
    }

	private void initTOD() {

		LocalDateTime now = LocalDateTime.now();

		int minute = now.get(ChronoField.MINUTE_OF_HOUR);
		int hour = now.get(ChronoField.HOUR_OF_DAY);
		int second = now.get(ChronoField.SECOND_OF_MINUTE);
		int tenths= now.get(ChronoField.MILLI_OF_SECOND)/100;

		this.tickCounter = 0;

		this.rtcAlarmIRQEnabled = false;

		this.timeOfDay = hour >= 12 ? TimeOfDay.PM : TimeOfDay.AM;

		this.tod10s = tenths;
		this.todSeconds = second;
		this.todMinutes = minute;
		this.todHours = hour >=12 ? hour-12 : hour;

		this.todRunning = true;
	}

	@Override
	public void reset()
	{
		super.reset();

		tickCounter = 0;
		initTOD();

		irqMask = 0;
		icr_read = 0;

		timerARunning = false;
		timerBRunning = false;
		timerAValue = 0xffff;
		timerBValue = 0xffff;
		timerALatch = 0xffff;
		timerBLatch = 0xffff;
	}

	/*
PRA  =  $dc00            ; CIA#1 (Port Register A)
DDRA =  $dc02            ; CIA#1 (Data Direction Register A)

PRB  =  $dc01            ; CIA#1 (Port Register B)
DDRB =  $dc03            ; CIA#1 (Data Direction Register B)

start    sei             ; interrupts deactivated

         lda #%11111111  ; CIA#1 port A = outputs  ==> Bits in PRA can be read and written
         sta DDRA

         lda #%11111101  ; select keyboard matrix column 1 (COL1)
         sta PRA

         lda #%00000000  ; CIA#1 port B = inputs
         sta DDRB        ; Bits in PRB can only be read

loop     lda PRB
         and #%00100000  ; masking row 5 (ROW5) , row is LOW active
         bne loop        ; wait until key "S"

         cli             ; interrupts activated

ende     rts             ; back to BASIC
	 */

	@Override
	public int readByte(int adr)
	{
		final int offset = ( adr & 0xffff ) % 0x10; // registers are mirrored/repeated every 16 bytes
		switch(offset)
		{
			case CIA1_ICR:
				int result = icr_read & 0xff;
				icr_read = 0;
				return result;
				/*
        Read: (Bit0..4 = INT DATA, Origin of the interrupt)

        Bit 0: 1 = Underflow Timer A
        Bit 1: 1 = Underflow Timer B
        Bit 2: 1 = Time of day and alarm time is equal
        Bit 3: 1 = SDR full or empty, so full byte was transferred, depending of operating mode serial bus
        Bit 4: 1 = IRQ Signal occured at FLAG-pin (cassette port Data input, serial bus SRQ IN)
        Bit 5..6: always 0
        Bit 7: 1 = IRQ An interrupt occured, so at least one bit of INT MASK and INT DATA is set in both registers.

        Flags will be cleared after reading the register!

        Write: (Bit 0..4 = INT MASK, Interrupt mask)

        Bit 0: 1 = Interrupt release through timer A underflow
        Bit 1: 1 = Interrupt release through timer B underflow
        Bit 2: 1 = Interrupt release if clock=alarmtime
        Bit 3: 1 = Interrupt release if a complete byte has been received/sent.
        Bit 4: 1 = Interrupt release if a positive slope occurs at the FLAG-Pin.
        Bit 5..6: unused
        Bit 7: Source bit. 0 = set bits 0..4 are clearing the according mask bit. 1 = set bits 0..4 are setting the according mask bit. If all bits 0..4 are cleared, there will be no change to the mask.
				 */
			case CIA1_TALO:
				return timerAValue & 0xff;
			case CIA1_TAHI:
				return (timerAValue >> 8 ) & 0xff;
			case CIA1_TBLO:
				return timerBValue & 0xff;
			case CIA1_TBHI:
				return (timerBValue >> 8 ) & 0xff;
				// ======== return ToD ====
			case CIA1_TOD_10THS: //  = 0x08;
				this.todRunning = false; //  Writing CIA1_TOD_10TS register stops TOD, until register 8 (TOD 10THS) is read.
				todRunning = true;
				return Misc.binaryToBCD( tod10s );
			case CIA1_TOD_SECOND: // = 0x09;
				return Misc.binaryToBCD( todSeconds );
			case CIA1_TOD_MIN: //    = 0x0a;
				return Misc.binaryToBCD( todMinutes );
			case CIA1_TOD_HOUR: //   = 0x0b;
				int result3 = Misc.binaryToBCD( todHours );
				// Bit 7: Differentiation AM/PM, 0=AM, 1=PM
				if ( timeOfDay == TimeOfDay.PM ) {
					result3 |= 1<<7;
				}
				return result3;
			default:
		}
		return super.readByte(offset);
	}

	@Override
	public void writeByte(int adr , byte value)
	{
		final int offset = ( adr & 0xffff ) % 0x10; // registers are mirrored/repeated every 16 bytes
		
		// System.out.println("Write to "+this+" @ "+HexDump.toAdr( offset ) );
		switch (offset)
		{
			// ============= Real time clock ==============
			case CIA1_TOD_10THS:
				if ( isSetRTCAlarmTime() ) {
					this.todAlarm10s = Misc.bcdToBinary( value & 0xff );
				} else {
					this.tod10s = Misc.bcdToBinary( value & 0xff );
				}
				return;
			case CIA1_TOD_SECOND:
				if ( isSetRTCAlarmTime() ) {
					this.todAlarmSeconds =  Misc.bcdToBinary( value & 0xff );
				} else {
					this.todSeconds =  Misc.bcdToBinary( value & 0xff );
				}
				return;
			case CIA1_TOD_MIN:
				if ( isSetRTCAlarmTime() ) {
					this.todAlarmMinutes = Misc.bcdToBinary( value & 0xff );
				} else {
					this.todMinutes = Misc.bcdToBinary( value & 0xff );
				}
				return;
			case CIA1_TOD_HOUR:
				if ( isSetRTCAlarmTime() ) {
					this.todAlarmTimeOfDay = (value & 1<<7) != 0 ? TimeOfDay.AM : TimeOfDay.PM;
					this.todAlarmHours = Misc.bcdToBinary( value & 0b0111_1111 );
				} else {
					this.timeOfDay = (value & 1<<7) != 0 ? TimeOfDay.AM : TimeOfDay.PM;
					this.todHours = Misc.bcdToBinary( value & 0b0111_1111 );
				}
				//  Writing into this register stops TOD, until register 8 (TOD 10THS) will be read.
				this.todRunning = false;
				return;
				// ===============================
			case CIA1_ICR:
				/*
		        Bit 0: 1 = Interrupt release through timer A underflow
		        Bit 1: 1 = Interrupt release through timer B underflow
		        Bit 2: 1 = Interrupt release if clock=alarmtime
		        Bit 3: 1 = Interrupt release if a complete byte has been received/sent.
		        Bit 4: 1 = Interrupt release if a positive slope occurs at the FLAG-Pin.
		        Bit 5..6: unused
		        Bit 7: Source bit. 0 = set bits 0..4 are clearing the according mask bit.
		                           1 = set bits 0..4 are setting the according mask bit.
		                           If all bits 0..4 are cleared, there will be no change to the mask.
				 */
				this.rtcAlarmIRQEnabled = (value & 1<<2 ) != 0;
				if ( (value & 1<<7) == 0 ) { // source bit = 0 => set bits 0...4 are clearing the bits in IRQ mask
					int mask = ~(value & 0b11111);
					irqMask &= mask;
				} else { // source bit = 1 => 1 = set bits 0..4 are setting the according mask bit. If all bits 0..4 are cleared, there will be no change to the mask.
					int mask = (value & 0b11111);
					irqMask |= mask;
				}
				if ( DEBUG ) {
					System.out.println( this+" ICR = "+Integer.toBinaryString( value ) );
				}
				break;
			case CIA1_TALO:
				timerALatch = ( timerALatch & 0xff00) | (value & 0xff);
				break;
			case CIA1_TAHI:
				timerALatch = ( timerALatch & 0x00ff) | (( value & 0xff) <<8);
				if ( ! timerARunning ) {
					timerAValue = ( timerAValue & 0x00ff) | (( value & 0xff) <<8);
				}
				break;
			case CIA1_TBLO:
				timerBLatch = ( timerBLatch & 0xff00) | (value & 0xff);
				break;
			case CIA1_TBHI:
				timerBLatch = ( timerBLatch & 0x00ff) | (( value & 0xff) <<8);
				if ( ! timerBRunning ) {
					timerBValue = ( timerBValue & 0x00ff) | (( value & 0xff) <<8);
				}
				break;
			case CIA1_CRA:
				/* Timer control A
				 * Bit 0: 0 = Stop timer; 1 = Start timer
				 * Bit 1: 1 = Indicates a timer underflow at port B in bit 6.
				 * Bit 2: 0 = Through a timer overflow, bit 6 of port B will get high for one cycle , 1 = Through a timer underflow, bit 6 of port B will be inverted
				 * Bit 3: 0 = Timer-restart after underflow (latch will be reloaded), 1 = Timer stops after underflow.
				 * Bit 4: 1 = Load latch into the timer once.
				 * Bit 5: 0 = Timer counts system cycles, 1 = Timer counts positive slope at CNT-pin
				 * Bit 6: Direction of the serial shift register, 0 = SP-pin is input (read), 1 = SP-pin is output (write)
				 * Bit 7: Real Time Clock, 0 = 60 Hz, 1 = 50 Hz
				 */
				boolean oldState = timerARunning;
				timerARunning = ( value & 1) != 0;
				if ( DEBUG_VERBOSE ) {
					if ( oldState != timerARunning ) {
						System.out.println( this+" , timer A running: "+timerARunning);
					}
				}
				if ( ( value & 1 << 4) != 0 ) {
					timerAValue = timerALatch;
				}
				break;
			case CIA1_CRB:
				/* Timer control B
				 *
				 * Bit 0: 0 = Stop timer; 1 = Start timer
				 * Bit 1: 1 = Indicates a timer underflow at port B in bit 7.
				 * Bit 2: 0 = Through a timer overflow, bit 7 of port B will get high for one cycle , 1 = Through a timer underflow, bit 7 of port B will be inverted
				 * Bit 3: 0 = Timer-restart after underflow (latch will be reloaded), 1 = Timer stops after underflow.
				 * Bit 4: 1 = Load latch into the timer once.
				 * Bit 5..6:
				 *
				 * %00 = Timer counts System cycle
				 * %01 = Timer counts positive slope on CNT-pin
				 * %10 = Timer counts underflow of timer A
				 * %11 = Timer counts underflow of timer A if the CNT-pin is high
				 *
				 * Bit 7: 0 = Writing into the TOD register sets the clock time, 1 = Writing into the TOD register sets the alarm time.
				 */
				oldState = timerBRunning;
				timerBRunning = ( value & 1) != 0;
				if ( DEBUG_VERBOSE ) {
					if ( oldState != timerBRunning ) {
						System.out.println( this+" , timer B running: "+timerBRunning);
					}
				}

				if ( ( value & 1 << 4) != 0 ) {
					timerBValue = timerBLatch;
				}
				break;
		}
		super.writeByte(offset, value);
	}

	private void increaseRTC(CPU cpu)
	{
		this.tod10s++;
		if ( this.tod10s > 9 ) {
			this.tod10s=0;
			this.todSeconds++;
			if ( this.todSeconds > 59 ) {
				this.todSeconds = 0;
				this.todMinutes++;
				if ( this.todMinutes > 59 ) {
					this.todMinutes = 0;
					this.todHours++;
					if ( this.todHours > 11 ) {
						this.todHours = 0;
						this.timeOfDay = this.timeOfDay.flip();
					}
				}
			}
		}
		if ( this.rtcAlarmIRQEnabled &&
				this.tod10s == this.todAlarm10s &&
				this.todSeconds == this.todAlarmSeconds &&
				this.todMinutes == this.todAlarmMinutes &&
				this.todHours == this.todAlarmHours &&
				this.timeOfDay == this.todAlarmTimeOfDay )
		{
			icr_read |= ( (1<<7)|(1<<2) ); // tod == tod alarm time
			cpu.queueInterrupt( IRQType.REGULAR );
		}
	}

	public void tick(CPU cpu)
	{
	    /*
	     * Method must ONLY be called when ph2 == HIGH
	     */
		tickCounter++;
		if ( todRunning & ( tickCounter % 100_000) == 0 ) // RTC increases in 1/10 of a second intervals = every 100 milliseconds = every 100.000 microseconds
		{
			increaseRTC( cpu );
		}

		/* $DC0E   CRA
        Bit 0: 0 = Stop timer; 1 = Start timer
        Bit 1: 1 = Indicates a timer underflow at port B in bit 6.
        Bit 2: 0 = Through a timer overflow, bit 6 of port B will get high for one cycle , 1 = Through a timer underflow, bit 6 of port B will be inverted
        Bit 3: 0 = Timer-restart after underflow (latch will be reloaded), 1 = Timer stops after underflow.
        Bit 4: 1 = Load latch into the timer once.
        Bit 5: 0 = Timer counts system cycles, 1 = Timer counts positive slope at CNT-pin
        Bit 6: Direction of the serial shift register, 0 = SP-pin is input (read), 1 = SP-pin is output (write)
        Bit 7: Real Time Clock, 0 = 60 Hz, 1 = 50 Hz
		 */
		final int cra = readByte( CIA1_CRA ); // Control Register A
		final int crb = readByte( CIA1_CRB ); // Control Register B
		if ( timerARunning )
		{
			if ( ( cra & (1<<5) ) == 0 ) // timer counts system cycles
			{
				timerAValue--;
				if ( timerAValue == 0 )
				{
					if ( timerBRunning && (crb & 0b1100000) == 0b1000000) { // timerB counts timerA underflow
						timerBValue--;
						if ( timerBValue == 0 ) {
							handleTimerBUnderflow( crb , cpu );
						}
					}
					handleTimerAUnderflow(cra , cpu );
				}
			}
		}

		/* $DC0F 	CRB 	Control Timer B
		 *
		 * Bit 0: 0 = Stop timer; 1 = Start timer
		 * Bit 1: 1 = Indicates a timer underflow at port B in bit 7.
		 * Bit 2: 0 = Through a timer overflow, bit 7 of port B will get high for one cycle , 1 = Through a timer underflow, bit 7 of port B will be inverted
		 * Bit 3: 0 = Timer-restart after underflow (latch will be reloaded), 1 = Timer stops after underflow.
		 * Bit 4: 1 = Load latch into the timer once.
		 * Bit 5..6:
		 *
		 * %00 = Timer counts System cycle
		 * %01 = Timer counts positive slope on CNT-pin
		 * %10 = Timer counts underflow of timer A
		 * %11 = Timer counts underflow of timer A if the CNT-pin is high
		 *
		 * Bit 7: 0 = Writing into the TOD register sets the clock time, 1 = Writing into the TOD register sets the alarm time.
		 */
		if ( timerBRunning )
		{
			final int mode = (crb >> 5) & 0b11;
			if ( mode == 0b00 ) { // Timer B counts System cycles
				timerBValue--;
				if ( timerBValue == 0 )
				{
					handleTimerBUnderflow(crb,cpu);
				}
			}
		}
	}

	private void handleTimerBUnderflow(int crb,CPU cpu)
	{
//		System.out.println("CIA #1 timer B underflow");
		icr_read |= ( (1<<7) | (1<<1) ); // timerB triggered underflow
		if ( (irqMask & 2 ) != 0 ) { // trigger interrupt on timer B underflow ?
			cpu.queueInterrupt( IRQType.REGULAR  );
		}
		if ( (crb & 1<<3) != 0 ) { // bit 3 = 1 => timer stops after underflow
			timerBRunning = false;
		}

		if ( DEBUG_VERBOSE ) {
			System.out.println(this+" , loading timer B latch = "+timerBLatch);
		}
		timerBValue = timerBLatch;
	}

	private void handleTimerAUnderflow(int cra,CPU cpu)
	{
//		System.out.println("CIA #1 timer A underflow");
		/*
        Bit 0: 1 = Interrupt release through timer A underflow
        Bit 1: 1 = Interrupt release through timer B underflow
		 */
	    icr_read |= ( (1<<7) | (1<<0) ); // timerA underflow triggered IRQ
		if ( (irqMask & 1) != 0 ) { // trigger interrupt on timer A underflow ?
			cpu.queueInterrupt( IRQType.REGULAR  );
		}
		if ( (cra & 1<<3) != 0 ) { // bit 3 = 1 => timer stops after underflow
			timerARunning = false;
		}
		if ( DEBUG_VERBOSE ) {
			System.out.println(this+" , loading timer A latch = "+timerALatch);
		}
		timerAValue = timerALatch;
	}

	private boolean isSetRTCAlarmTime()
	{
		return (readByte( CIA1_CRB ) & 1<<7) != 0;
	}
}