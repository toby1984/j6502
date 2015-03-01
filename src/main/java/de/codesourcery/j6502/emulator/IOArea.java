package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.utils.HexDump;

public class IOArea extends Memory
{
	public static final boolean VIC_PAL_MODE = true;

	private long currentFrame;

	public IOArea(String identifier, AddressRange range)
	{
		super(identifier, range);
	}

	@Override
	public int readByte(int offset) {
		return super.readByte(offset);
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

	private short irqOnRaster = -1;

	@Override
	public void writeByte(int set, byte value)
	{
		final int offset = set & 0xffff;
//		if ( ( getAddressRange().getStartAddress()+offset == 0xdd00 ) ) {
//			System.out.println("CIA2 $dd00 = "+HexDump.toBinaryString( value ) );
//		}
		
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