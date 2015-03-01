package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.emulator.Keyboard.Key;

/**
 * I/O area , memory bank 5 ($D000 - $DFFF).
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class IOArea extends Memory
{
	public static final boolean VIC_PAL_MODE = true;

	private long currentFrame;
	private short irqOnRaster = -1;
	
	// prA: Keyboard matrix columns ( 0 = read only this column)
	// prB: keyboard matrix rows ( 0 = key active) 
	private int[] keyboardColumns= new int[] { 0xff,0xff,0xff,0xff,0xff,0xff,0xff,0xff}; // all pins are LOW-active 

	public IOArea(String identifier, AddressRange range)
	{
		super(identifier, range);
	}
	
	public void keyPressed(Key key) 
	{
		final int mask = 1 << key.rowBitNo;
		synchronized(keyboardColumns) {
			keyboardColumns[ key.colBitNo ] &= ~mask; // pins are LOW active so clear bit of this key
		}
	}
	
	public void keyReleased(Key key) 
	{
		final int mask = 1 << key.rowBitNo;
		synchronized(keyboardColumns) {
			keyboardColumns[ key.colBitNo ] |= mask; // pins are LOW active so SET bit of this key
		}
	}
	
	/*    $D011 	53265 	17 	Steuerregister, Einzelbedeutung der Bits (1 = an):
	 *                          Bit 7: Bit 8 von $D012
	 *                          Bit 6: Extended Color Modus
	 *                          Bit 5: Bitmapmodus
	 *                          Bit 4: Bildausgabe eingeschaltet (Effekt erst beim nächsten Einzelbild)
	 *                          Bit 3: 25 Zeilen (sonst 24)
	 *                          Bit 2..0: Offset in Rasterzeilen vom oberen Bildschirmrand
	 *
	 *    $D012 	53266 	18 	Lesen: Aktuelle Rasterzeile
	 *                          Schreiben: Rasterzeile, bei der ein IRQ ausgelöst wird (zugehöriges Bit 8 in $D011)
	 */
	
	/*
PRA  =  $dc00            ; CIA#1 (Port Register A)
DDRA =  $dc02            ; CIA#1 (Data Direction Register A)

PRB  =  $dc01            ; CIA#1 (Port Register B)
DDRB =  $dc03            ; CIA#1 (Data Direction Register B)

start    sei             ; interrupts deactivated

         lda #%11111111  ; CIA#1 port A = outputs  ==> Bits in PRA can be read and written
         sta DDRA             

         lda #%00000000  ; CIA#1 port B = inputs
         sta DDRB        ; Bits in PRB can only be read

         lda #%11111101  ; select keyboard matrix column 1 (COL1)
         sta PRA
            
loop     lda PRB
         and #%00100000  ; masking row 5 (ROW5) , row is LOW active
         bne loop        ; wait until key "S" 

         cli             ; interrupts activated

ende     rts             ; back to BASIC	 
	 */
	
	private static int CIA1_PRA  = 0x0c00;
	private static int CIA1_DDRA = 0x0c02;	
	
	private static int CIA1_PRB  = 0x0c01;
	private static int CIA1_DDRB = 0x0c03;		
	
	private int portRegisterA;
	private int selectedKeyboardColumns;
	private int dataDirectionRegisterA;
	
	private int portRegisterB;
	private int dataDirectionRegisterB;
	
	/* CIA1 Port A Data Direction Register : $DC02
	 * 
     * Bit #x: 0 = Bit #x in port A can only be read 
     *         1 = Bit #x in port A can be read and written
	 */
	
	private int readDataDirectionRegisterA() { // $DC02
		return super.readByte( 0x0c02 );
	}
	
	private void writeDataDirectionRegisterA(byte value) { // $DC02
		super.writeByte( 0x0c02 , value );
	}	
	
	/* CIA1 Port A : $DC00
     * Keyboard matrix columns and joystick #2. 
     * 
     * Read bits:
     * 
     * Bit #0: 0 = Port 2 joystick up pressed.
     * Bit #1: 0 = Port 2 joystick down pressed.
     * Bit #2: 0 = Port 2 joystick right pressed.
     * Bit #3: 0 = Port 2 joystick left pressed.
     * Bit #4: 0 = Port 2 joystick fire pressed.
     * 
     * Write bits:
     * 
     * Bit #x: 0 = Select keyboard matrix column #x.
     * Bits #6-#7: Paddle selection; %01 = Paddle #1; %10 = Paddle #2.
	 */	
	private int readPortRegisterA() { // $dc00
		return super.readByte( 0xc00 );
	}
	
	private void writePortRegisterA(int value) { // $dc00
		super.writeByte( 0xc00 , (byte) value);
	}

	/*
	 * CIA1 Port B Data Direction Register: $DC03
	 * 
     * Bit #x: 0 = Bit #x in port B can only be read
     *         1 = Bit #x in port B can be read and written
	 */		
	private int readDataDirectionRegisterB() { // $DC03
		return super.readByte( 0xc03 );
	}

	private void writeDataDirectionRegisterB(int value) { // $DC03
		super.writeByte( 0xc03 , (byte) value );
	}
	
	/* CIA1 Port B : $DC01
     * Port B, keyboard matrix rows and joystick #1. 
     * 
     * Bits:
     * 
     * Bit #x: 0 = A key is currently being pressed in keyboard matrix row #x, in the column selected at memory address $DC00.
     * Bit #0: 0 = Port 1 joystick up pressed.
     * Bit #1: 0 = Port 1 joystick down pressed.
     * Bit #2: 0 = Port 1 joystick right pressed.
     * Bit #3: 0 = Port 1 joystick left pressed.
     * Bit #4: 0 = Port 1 joystick fire pressed.
	 */	
	private int readPortRegisterB() { // $dc01
		int columnMask = super.readByte( 0x0c00 );
		int bitMaskToTest = 1;
		for ( int i =0 ; i < 8 ; i++ ) 
		{
			if ( (columnMask & bitMaskToTest) == 0 ) {
				// found column to test
				synchronized( keyboardColumns ) {
					return keyboardColumns[ i ];
				}
			}
		}
		return super.readByte( 0xc01 );
	}
	
	private void writePortRegisterB(int value) { // $dc01
		super.writeByte( 0xc01 , (byte) value );
	}	

	@Override
	public void writeByte(int set, byte value)
	{
		final int offset = set & 0xffff;
		
		if ( offset == CIA1_PRA ) 
		{
			writePortRegisterA( value );
			return;
		} 
		if ( offset == CIA1_PRB ) 
		{
			writePortRegisterB( value );
			return;
		} 
		if ( offset == CIA1_DDRA ) {
			writeDataDirectionRegisterA( value );
			return;
		}
		if ( offset == CIA1_DDRB ) 
		{
			writeDataDirectionRegisterB( value );
			return;
		}
		
		if ( offset == VIC.VIC_SCANLINE )
		{
			// current scan line, lo-byte
			irqOnRaster = (short) (irqOnRaster | (value & 0xff) );
		}
		else if ( offset == VIC.VIC_CNTRL1 )
		{
			// current scan line, hi-byte
			if ( ( value & 0b1000_0000 ) != 0 ) {
				irqOnRaster = (short) ( 0b0100 | (irqOnRaster & 0xff) ); // set hi-bit
			} else {
				irqOnRaster = (short) (irqOnRaster & 0xff); // clear hi-bit
			}
		}
		super.writeByte(offset, value);
	}
	
	@Override
	public int readByte(int adr) 
	{
		final int offset = adr & 0xffff;
		if ( offset == CIA1_PRA ) 
		{
			return readPortRegisterA();
		} 
		if ( offset == CIA1_PRB ) 
		{
			return readPortRegisterB();
		} 
		if ( offset == CIA1_DDRA ) {
			return readDataDirectionRegisterA();
		}
		else if ( offset == CIA1_DDRB ) 
		{
			return readDataDirectionRegisterB();
		}
		return super.readByte(offset);
	}	

	public void tick(CPU cpu)
	{
		/*
		 * Update current raster line
		 */
		short rasterLine;
		if ( VIC_PAL_MODE ) // 312 raster lines , 57 cycles/line
		{
			currentFrame = cpu.cycles / (57*312);
			rasterLine =  (short) ( (cpu.cycles / 57) % 312 );
		} else { // NTSC =>  263 rasterlines and 65 cycles/line
			currentFrame = cpu.cycles / (65*263);
			rasterLine =  (short) ( (cpu.cycles / 65) % 263 );
		}
		final byte lo = (byte) rasterLine;
		final byte hi = (byte) (rasterLine>>8);

		int hiBit = readByte( VIC.VIC_CNTRL1 );
		if ( hi != 0 ) {
			hiBit |= 0b1000_0000;
		} else {
			hiBit &= 0b0111_1111;
		}
		writeByte( VIC.VIC_CNTRL1 , (byte) hiBit );
		writeWord( VIC.VIC_SCANLINE , lo );
	}
}