package de.codesourcery.j6502.emulator;

public class IOArea extends Memory
{
	public static final boolean VIC_PAL_MODE = true;

	// VIC registers
	private static final short VIC_SPRITE0_X_COORD = 0;
	private static final short VIC_SPRITE0_Y_COORD = 1;

	private static final short VIC_SPRITE1_X_COORD = 2;
	private static final short VIC_SPRITE1_Y_COORD = 3;

	private static final short VIC_SPRITE2_X_COORD = 4;
	private static final short VIC_SPRITE2_Y_COORD = 5;

	private static final short VIC_SPRITE3_X_COORD = 6;
	private static final short VIC_SPRITE3_Y_COORD = 7;

	private static final short VIC_SPRITE4_X_COORD = 8;
	private static final short VIC_SPRITE4_Y_COORD = 9;

	private static final short VIC_SPRITE5_X_COORD = 10;
	private static final short VIC_SPRITE5_Y_COORD = 11;

	private static final short VIC_SPRITE6_X_COORD = 12;
	private static final short VIC_SPRITE6_Y_COORD = 13;

	private static final short VIC_SPRITE7_X_COORD = 14;
	private static final short VIC_SPRITE7_Y_COORD = 15;

	private static final short VIC_SPRITE_X_COORDS_HI_BIT = 15;

	private static final short VIC_CNTRL1 = 17;

	private static final short VIC_SCANLINE = 18;

	private static final short VIC_LIGHTPEN_X_COORDS = 19;
	private static final short VIC_LIGHTPEN_Y_COORDS = 20;

	private static final short VIC_SPRITE_ENABLE = 21;

	private static final short VIC_CTRL2 = 22;

	private static final short VIC_SPRITE_DOUBLE_HEIGHT = 23;

	private static final short VIC_MEMORY_MAP = 24;

	private static final short VIC_IRQ_ACTIVE_BITS = 25;

	private static final short VIC_IRQ_ENABLE_BITS = 26;

	private static final short VIC_SPRITE_PRIORITY = 27;

	private static final short VIC_SPRITE_MULTICOLOR_MODE = 28;

	private static final short VIC_SPRITE_DOUBLE_WIDTH = 29;

	private static final short VIC_SPRITE_SPRITE_COLLISIONS = 30;
	private static final short VIC_SPRITE_BACKGROUND_COLLISIONS = 31;

	private static final short VIC_BORDER_COLOR = 32;
	private static final short VIC_BACKGROUND_COLOR = 33;

	private static final short VIC_BACKGROUND0_EXT_COLOR = 34;
	private static final short VIC_BACKGROUND1_EXT_COLOR = 35;
	private static final short VIC_BACKGROUND2_EXT_COLOR = 36;

	private static final short VIC_SPRITE_COLOR10_MULTICOLOR_MODE = 37;
	private static final short VIC_SPRITE_COLOR11_MULTICOLOR_MODE = 38;

	private static final short VIC_SPRITE0_COLOR10 = 39;
	private static final short VIC_SPRITE1_COLOR10 = 40;
	private static final short VIC_SPRITE2_COLOR10 = 41;
	private static final short VIC_SPRITE3_COLOR10 = 42;
	private static final short VIC_SPRITE4_COLOR10 = 43;
	private static final short VIC_SPRITE5_COLOR10 = 44;
	private static final short VIC_SPRITE6_COLOR10 = 45;
	private static final short VIC_SPRITE7_COLOR10 = 46;

	private long currentFrame;

	/* I/O area is fixed at $D000 - $DFFF.
	 *
	 * SID $d000 - $d02f
	 * ---
	 *
	 * Adresse (hex) 	Adresse (dez) 	Register 	Inhalt
     *    $D000 	53248 	0 	X-Koordinate für Sprite 0 (0..255)
     *    $D001 	53249 	1 	Y-Koordinate für Sprite 0 (0..255)
     *    $D002 	53250 	2 	X-Koordinate für Sprite 1 (0..255)
     *    $D003 	53251 	3 	Y-Koordinate für Sprite 1 (0..255)
     *    $D004 	53252 	4 	X-Koordinate für Sprite 2 (0..255)
     *    $D005 	53253 	5 	Y-Koordinate für Sprite 2 (0..255)
     *    $D006 	53254 	6 	X-Koordinate für Sprite 3 (0..255)
     *    $D007 	53255 	7 	Y-Koordinate für Sprite 3 (0..255)
     *    $D008 	53256 	8 	X-Koordinate für Sprite 4 (0..255)
     *    $D009 	53257 	9 	Y-Koordinate für Sprite 4 (0..255)
     *    $D00A 	53258 	10 	X-Koordinate für Sprite 5 (0..255)
     *    $D00B 	53259 	11 	Y-Koordinate für Sprite 5 (0..255)
     *    $D00C 	53260 	12 	X-Koordinate für Sprite 6 (0..255)
     *    $D00D 	53261 	13 	Y-Koordinate für Sprite 6 (0..255)
     *    $D00E 	53262 	14 	X-Koordinate für Sprite 7 (0..255)
     *    $D00F 	53263 	15 	Y-Koordinate für Sprite 7 (0..255)
     *    $D010 	53264 	16 	Bit 8 für die obigen X-Koordinaten (0..255) , jedes Bit steht für eins der Sprites 0..7
     *
     *    $D011 	53265 	17 	Steuerregister, Einzelbedeutung der Bits (1 = an):
     *                          Bit 7: Bit 8 von $D012
     *                          Bit 6: Extended Color Modus
     *                          Bit 5: Bitmapmodus
     *                          Bit 4: Bildausgabe eingeschaltet (Effekt erst beim nächsten Einzelbild)
     *                          Bit 3: 25 Zeilen (sonst 24)
     *                          Bit 2..0: Offset in Rasterzeilen vom oberen Bildschirmrand
     *
     *    $D012 	53266 	18 	Lesen: Aktuelle Rasterzeile
     *                          Schreiben: Rasterzeile, bei der ein IRQ ausgelöst wird (zugehöriges Bit 8 in $D011)
     *    $D013 	53267 	19 	Lightpen X-Koordinate (assoziiert mit Pin LP am VIC)
     *    $D014 	53268 	20 	Lightpen Y-Koordinate
     *    $D015 	53269 	21 	Spriteschalter, Bit = 1: Sprite n an (0..255)
     *    $D016 	53270 	22 	Steuerregister, Einzelbedeutung der Bits (1 = an):
     *                          Bit 7..5: unbenutzt
     *                          Bit 4: Multicolor-Modus
     *                          Bit 3: 40 Spalten (an)/38 Spalten (aus)
     *                          Bit 2..0: Offset in Pixeln vom linken Bildschirmrand
     *
     *    $D017 	53271 	23 	Spriteschalter, Bit = 1: Sprite n doppelt hoch (0..255)
     *    $D018 	53272 	24 	VIC-Speicherkontrollregister
     *
     *                          Bit 7..4: Basisadresse des Bildschirmspeichers in aktueller 16K-Bank des VIC = 64*(Bit 7…4)[2]
     *                          Bit 3..1: Basisadresse des Zeichensatzes = 1024*(Bit 3…1).
     *
     *    _oder_ im Bitmapmodus
     *
     *                          Bit 3: Basisadresse der Bitmap = 1024*8 (Bit 3)
     *                          (8kB-Schritte in VIC-Bank)
     *                          Bit 0 ist immer 1: nur 2 kB Schritte in VIC-Bank[3]
     *
     *                          Beachte: Das Character-ROM wird nur in VIC-Bank 0 und 2 ab 4096 eingeblendet
     *
     *    $D019 	53273 	25 	Interrupt Request, Bit = 1 = an
     *                          Lesen:
     *                          Bit 7: IRQ durch VIC ausgelöst
     *                          Bit 6..4: unbenutzt
     *                          Bit 3: Anforderung durch Lightpen
     *                          Bit 2: Anforderung durch Sprite-Sprite-Kollision (Reg. $D01E)
     *                          Bit 1: Anforderung durch Sprite-Hintergrund-Kollision (Reg. $D01F)
     *                          Bit 0: Anforderung durch Rasterstrahl (Reg. $D012)
     *                          Schreiben:
     *                          1 in jeweiliges Bit schreiben = zugehöriges Interrupt-Flag löschen
     *    $D01A 	53274 	26 	Interrupt Request: Maske, Bit = 1 = an
     *                          Ist das entsprechende Bit hier und in $D019 gesetzt, wird ein IRQ ausgelöst und Bit 7 in $D019 gesetzt
     *                          Bit 7..4: unbenutzt
     *                          Bit 3: IRQ wird durch Lightpen ausgelöst
     *                          Bit 2: IRQ wird durch S-S-Kollision ausgelöst
     *                          Bit 1: IRQ wird durch S-H-Kollision ausgelöst
     *                          Bit 0: IRQ wird durch Rasterstrahl ausgelöst
     *    $D01B 	53275 	27 	Priorität Sprite-Hintergrund, Bit = 1: Hintergrund liegt vor Sprite n (0..255)
     *    $D01C 	53276 	28 	Sprite-Darstellungsmodus, Bit = 1: Sprite n ist Multicolor
     *    $D01D 	53277 	29 	Spriteschalter, Bit = 1: Sprite n doppelt breit (0..255)
     *    $D01E 	53278 	30 	Sprite-Info: Bits = 1: Sprites miteinander kollidiert (0..255)
     *                          Wird durch Zugriff gelöscht
     *    $D01F 	53279 	31 	Sprite-Info: Bits = 1: Sprite n mit Hintergrund kollidiert (0..255)
     *                          Wird durch Zugriff gelöscht
     *    $D020 	53280 	32 	Farbe des Bildschirmrands (0..15)
     *    $D021 	53281 	33 	Bildschirmhintergrundfarbe (0..15)
     *    $D022 	53282 	34 	Bildschirmhintergrundfarbe 1 bei Extended Color Modus (0..15)
     *    $D023 	53283 	35 	Bildschirmhintergrundfarbe 2 bei Extended Color Mode (0..15)
     *    $D024 	53284 	36 	Bildschirmhintergrundfarbe 3 bei Extended Color Mode (0..15)
     *
     *    $D025 	53285 	37 	gemeinsame Spritefarbe 0 im Sprite-Multicolormodus, Bitkombination %01 (0..15)
     *    $D026 	53286 	38 	gemeinsame Spritefarbe 1 im Sprite-Multicolormodus, Bitkombination %11 (0..15)
     *
     *    $D027 	53287 	39 	Farbe Sprite 0, Bitkombination %10 (0..15)
     *    $D028 	53288 	40 	Farbe Sprite 1, Bitkombination %10 (0..15)
     *    $D029 	53289 	41 	Farbe Sprite 2, Bitkombination %10 (0..15)
     *    $D02A 	53290 	42 	Farbe Sprite 3, Bitkombination %10 (0..15)
     *    $D02B 	53291 	43 	Farbe Sprite 4, Bitkombination %10 (0..15)
     *    $D02C 	53292 	44 	Farbe Sprite 5, Bitkombination %10 (0..15)
     *    $D02D 	53293 	45 	Farbe Sprite 6, Bitkombination %10 (0..15)
     *    $D02E 	53294 	46 	Farbe Sprite 7, Bitkombination %10 (0..15)
     *    $D02F 	53295 	47 	nur VIC IIe (C128)
     *                          Bit 7..3: unbenutzt
     *                          Bit 2..0: Status der 3 zusätzlichen Tastaturpins
     *                          $D030 	53296 	48 	nur VIC IIe (C128)
     *
     *                          Bit 7..2: unbenutzt
     *                          Bit 1: Testmode
     *                          Bit 0: 0 => Slow-Mode (1 MHz), 1 => Fast-Mode (2 MHz)
	 */

	public IOArea(String identifier, AddressRange range)
	{
		super(identifier, range);
	}

	@Override
	public byte readByte(short offset) {
		return super.readByte(offset);
	}

	@Override
	public short readWord(short offset)
	{
		return super.readWord( offset );
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
	public void writeByte(short offset, byte value)
	{
		if ( offset == VIC_SCANLINE )
		{
			// current scan line, lo-byte
			irqOnRaster = (short) (irqOnRaster | (value & 0xff) );
		}
		else if ( offset == VIC_CNTRL1 )
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

		byte hiBit = readByte( VIC_CNTRL1 );
		if ( hi != 0 ) {
			hiBit |= 0b1000_0000;
		} else {
			hiBit &= 0b0111_1111;
		}
		writeByte( VIC_CNTRL1 , hiBit );
		writeWord( VIC_SCANLINE , lo );
	}
}