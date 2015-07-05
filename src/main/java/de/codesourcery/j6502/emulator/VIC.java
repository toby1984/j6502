package de.codesourcery.j6502.emulator;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class VIC extends Memory
{
	public  static final Color Black 	     = color( 0, 0, 0); // 0
	public  static final Color White        = color( 255, 255, 255); // 1
	public  static final Color Red          = color( 136, 0, 0); // 2
	public  static final Color Cyan         = color( 170, 255, 238); // 3
	public  static final Color Violet       = color( 204, 68, 204 ); // 4
	public  static final Color Green        = color( 0, 204, 85  ); // 5
	public  static final Color Blue         = color( 	0, 0, 170  ); // 6
	public  static final Color Yellow         = color( 238, 238, 119 ); // 7
	public  static final Color Orange       = color( 221, 136, 85); // 8
	public  static final Color Brown        = color( 102, 68, 0); // 9
	public  static final Color Lightred    = color( 255, 119, 119); // 10
	public  static final Color Grey1       = color( 51, 51, 51); // 11
	public  static final Color Grey2       = color( 119, 119, 119); // 12
	public  static final Color Lightgreen  = color( 170, 255, 102); // 13
	public  static final Color Lightblue   = color( 0, 136, 255 ); // 14
	public  static final Color Lightgrey   = color(  187, 187, 187); // 15

	public long cycleCount;
	public int rasterLine;

	public short irqOnRaster = -1;

	private static Color color(int r,int g,int b) {
		return new Color(r,g,b);
	}

	public static final Color[] AWT_COLORS = { Black,White,Red,Cyan,Violet,Green,Blue,Yellow,Orange,Brown,Lightred,Grey1,Grey2,Lightgreen,Lightblue,Lightgrey};

	public  static final int[] INT_COLORS = new int[16];

	static {
		for ( int i = 0 ; i < AWT_COLORS.length ; i++ )
		{
			final int rgb = AWT_COLORS[i].getRGB();
			INT_COLORS[i] = rgb;
		}
	}

	/* I/O area is fixed at $D000 - $DFFF.
	 *
	 * VIC $d000 - $d02f
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

	public static final boolean VIC_PAL_MODE = true;

	// VIC registers
	public  static final int VIC_SPRITE0_X_COORD = 0;
	public  static final int VIC_SPRITE0_Y_COORD = 1;

	public  static final int VIC_SPRITE1_X_COORD = 2;
	public  static final int VIC_SPRITE1_Y_COORD = 3;

	public  static final int VIC_SPRITE2_X_COORD = 4;
	public  static final int VIC_SPRITE2_Y_COORD = 5;

	public  static final int VIC_SPRITE3_X_COORD = 6;
	public  static final int VIC_SPRITE3_Y_COORD = 7;

	public  static final int VIC_SPRITE4_X_COORD = 8;
	public  static final int VIC_SPRITE4_Y_COORD = 9;

	public  static final int VIC_SPRITE5_X_COORD = 0x0a;
	public  static final int VIC_SPRITE5_Y_COORD = 0x0b;

	public  static final int VIC_SPRITE6_X_COORD = 0x0c;
	public  static final int VIC_SPRITE6_Y_COORD = 0x0d;

	public  static final int VIC_SPRITE7_X_COORD = 0x0e;
	public  static final int VIC_SPRITE7_Y_COORD = 0x0f;

	public  static final int VIC_SPRITE_X_COORDS_HI_BIT = 0x10;

	public  static final int VIC_CTRL1 = 0x11;

	public  static final int VIC_SCANLINE = 0x12;

	public  static final int VIC_LIGHTPEN_X_COORDS = 0x13;
	public  static final int VIC_LIGHTPEN_Y_COORDS = 0x14;

	public  static final int VIC_SPRITE_ENABLE = 0x15;

	public  static final int VIC_CTRL2 = 0x16;

	public  static final int VIC_SPRITE_DOUBLE_HEIGHT = 0x17;

	public  static final int VIC_MEMORY_MAPPING = 0x18;

	public  static final int VIC_IRQ_ACTIVE_BITS = 0x19;

	public  static final int VIC_IRQ_ENABLE_BITS = 0x1a;

	public  static final int VIC_SPRITE_PRIORITY = 0x1b;

	public  static final int VIC_SPRITE_MULTICOLOR_MODE = 0x1c;

	public  static final int VIC_SPRITE_DOUBLE_WIDTH = 0x1d;

	public  static final int VIC_SPRITE_SPRITE_COLLISIONS = 0x1e;
	public  static final int VIC_SPRITE_BACKGROUND_COLLISIONS = 0x1f;

	public  static final int VIC_BORDER_COLOR = 0x20;
	public  static final int VIC_BACKGROUND_COLOR = 0x21;

	public  static final int VIC_BACKGROUND0_EXT_COLOR = 0x22;
	public  static final int VIC_BACKGROUND1_EXT_COLOR = 0x23;
	public  static final int VIC_BACKGROUND2_EXT_COLOR = 0x24;

	public  static final int VIC_SPRITE_COLOR10_MULTICOLOR_MODE = 0x25;
	public  static final int VIC_SPRITE_COLOR11_MULTICOLOR_MODE = 0x26;

	public  static final int VIC_SPRITE0_COLOR10 = 0x27;
	public  static final int VIC_SPRITE1_COLOR10 = 0x28;
	public  static final int VIC_SPRITE2_COLOR10 = 0x29;
	public  static final int VIC_SPRITE3_COLOR10 = 0x2a;
	public  static final int VIC_SPRITE4_COLOR10 = 0x2b;
	public  static final int VIC_SPRITE5_COLOR10 = 0x2c;
	public  static final int VIC_SPRITE6_COLOR10 = 0x2d;
	public  static final int VIC_SPRITE7_COLOR10 = 0x2e;

	public static final int BORDER_HEIGHT_PIXELS = 15;
	public static final int BORDER_WIDTH_PIXELS = 15;

	public static final int SCREEN_ROWS = 25;
	public static final int SCREEN_COLS = 40;

	public static final int VIEWPORT_WIDTH = SCREEN_COLS*8;
	public static final int VIEWPORT_HEIGHT = SCREEN_ROWS*8;

	public static final int BG_BUFFER_WIDTH = 2*BORDER_WIDTH_PIXELS + VIEWPORT_WIDTH;

	public static final int BG_BUFFER_HEIGHT = 2*BORDER_HEIGHT_PIXELS + VIEWPORT_HEIGHT;

	private BufferedImage buffer;
	private Graphics2D graphics;

	private WriteOnceMemory characterROM;

	public VIC(String identifier,AddressRange adr) {
		super(identifier,adr);
	}

	private BufferedImage getBuffer(Graphics2D g) {
		if ( buffer == null )
		{
			// 8x8 pixel per character
			// 40x25 characters on screen
			buffer = g.getDeviceConfiguration().createCompatibleImage( BG_BUFFER_WIDTH , BG_BUFFER_HEIGHT );
		}
		return buffer;
	}

	public Graphics2D getBufferGraphics(Graphics2D g) {
		if ( graphics == null ) {
			graphics = getBuffer( g ).createGraphics();
		}
		return graphics;
	}

	public void reset()
	{
		super.reset();
		
		this.cycleCount = 0;
		this.rasterLine = 0;
		this.irqOnRaster = -1;

		characterROM = MemorySubsystem.loadCharacterROM();
	}

	private Color getBorderColor() {
		int borderColor = readByte( VIC_BORDER_COLOR ) & 0b1111;
		return AWT_COLORS[ borderColor ];
	}

	private Color getBackgroundColor() {
		int bgColor = readByte( VIC_BACKGROUND_COLOR ) & 0b1111;
		return AWT_COLORS[ bgColor ];
	}

	public void render(MemorySubsystem mainMemory,Graphics2D g,int width,int height)
	{
		// video RAM location
		/*
		 *    $D018 	53272 	24 	VIC-Speicherkontrollregister
		 *
		 *                           vvvvggg
		 *                          Bit 7..4: Basisadresse des Bildschirmspeichers in aktueller 16K-Bank des VIC = 64*(Bit 7…4)[2]
		 *                          Bit 3..1: Basisadresse des Zeichensatzes = 1024*(Bit 3…1).
		 *
         * When in TEXT SCREEN MODE, the VIC-II looks to 53272 for information on where the character set and text screen character RAM is located:
         *
         * The four most significant bits form a 4-bit number in the range 0 thru 15: Multiplied with 1024 this gives the start address for the screen character RAM.
         * Bits 1 thru 3 (weights 2 thru 8) form a 3-bit number in the range 0 thru 7: Multiplied with 2048 this gives the start address for the character set.
		 */
		int value = readByte(VIC_MEMORY_MAPPING);
		int videoRAMOffset = 1024 * ( ( value & 0b1111_0000) >>4 );
		int glyphRAMOffset = 2048 * ( ( value & 0b0000_1110) >> 1);

		/* CIA #2 , $DD00
		 * Bit 0..1: Select the position of the VIC-memory
		 * %00, 0: Bank 3: $C000-$FFFF, 49152-65535
		 * %01, 1: Bank 2: $8000-$BFFF, 32768-49151
		 * %10, 2: Bank 1: $4000-$7FFF, 16384-32767
		 * %11, 3: Bank 0: $0000-$3FFF, 0-16383 (standard)
		 *
         * Bank no.	Bit pattern in 56576/$DD00    Character ROM available?
         * 3	xxxxxx11	0–16383	$0000–$3FFF	      Yes, at 4096–8192 $1000–$1FFF
         * 2	xxxxxx10	16384–32767	$4000–$7FFF	  No
         * 1	xxxxxx01	32768–49151	$8000–$BFFF	  Yes, at 36864–40959 $9000–$9FFF
         * 0	xxxxxx00	49152–65535	$C000–$FFFF	  No
		 */
		final int bankNo = mainMemory.readByte( 0xdd00 ) & 0b11;
//		System.out.println("Selected VIC bank: "+bankNo);

		int bankAdr;
		switch( bankNo ) {
			case 0b00:
				bankAdr = 0xc000;
				break;
			case 0b01:
				bankAdr = 0x8000;
				break;
			case 0b10:
				bankAdr = 0x4000;
				break;
			case 0b11:
				bankAdr = 0x0000;
				break;
			default:
				throw new RuntimeException("Unreachable code reached");
		}

		final int colorRAM = 0xD800;
		int videoRAM = bankAdr+videoRAMOffset;
		// int glyphRAM = bankAdr+glyphRAMOffset;

		BufferedImage buffer = getBuffer(g);
		Graphics2D bufferGraphics = getBufferGraphics(g);

		// clear screen
		bufferGraphics.setColor( getBorderColor() );
		bufferGraphics.fillRect( 0 , 0 , BG_BUFFER_WIDTH , BG_BUFFER_HEIGHT );

		bufferGraphics.setColor( getBackgroundColor() );
		bufferGraphics.fillRect( BORDER_WIDTH_PIXELS , BORDER_HEIGHT_PIXELS , VIEWPORT_WIDTH , VIEWPORT_HEIGHT );

//		System.out.println("Video RAM = "+HexDump.toAdr( videoRAM ) );
//		System.out.println("Glyph RAM = "+HexDump.toAdr( glyphRAM ) );
		for ( int y = 0 ; y < SCREEN_ROWS ; y++ )
		{
			for ( int x =0 ; x < SCREEN_COLS ; x++ )
			{
				final int offset = y * SCREEN_COLS + x;
				final int color = mainMemory.readByte( colorRAM + offset ) % 0b1111;
				final int character = mainMemory.readByte( videoRAM + offset );

				int xPixel = BORDER_WIDTH_PIXELS  + (x << 3); // *8
				int yPixel = BORDER_HEIGHT_PIXELS + (y << 3); // *8

				int glyphAdr = character << 3; // *8 bytes per glyph

				for ( int i = 0 ; i < 8 ; i++ )
				{
					// FIXME: I assume that the character ROM is always available to the VIC , this is not
					// FIXME: 100% technically correct since this actually depends on the selected VIC bank (see above)
					final int word = characterROM.readByte( glyphAdr );

					for (int mask = 0b10000000 , row = 0 ; row <  8 ; row++ , mask = mask >> 1 )
					{
						if ( (word & mask) != 0 ) {
							buffer.setRGB( xPixel+row,yPixel , INT_COLORS[ color ] );
						}
					}
					glyphAdr++;
					yPixel++;
				}
			}
		}
		g.drawImage( buffer , 0 , 0 , width, height , null );
	}

	public void tick(CPU cpu,boolean clockHigh)
	{
		if ( ! clockHigh ) {
			return;
		}
		
		cycleCount++;

		// decrement

		// Increment current scanline position

		if ( VIC_PAL_MODE ) // 312 raster lines , 57 cycles/line
		{
			while ( cycleCount >= 57 ) {
				rasterLine++;
				cycleCount-=57;
			}
			if ( rasterLine >= 312 ) {
				rasterLine = 0;
				cycleCount=0;
			}
		} else { // NTSC =>  263 rasterlines and 65 cycles/line
			while ( cycleCount >= 65 ) {
				rasterLine++;
				cycleCount-=65;
			}
			if ( rasterLine >= 263 ) {
				rasterLine = 0;
				cycleCount=0;
			}
		}

		final byte lo = (byte) rasterLine;
		final byte hi = (byte) (rasterLine>>8);

		int hiBit = readByte( VIC.VIC_CTRL1 );
		if ( hi != 0 ) {
			hiBit |= 0b1000_0000;
		} else {
			hiBit &= 0b0111_1111;
		}
		writeByte( VIC.VIC_CTRL1 , (byte) hiBit );
		writeWord( VIC.VIC_SCANLINE , lo );
	}
	
	@Override
	public void writeByte(int offset, byte value) 
	{
		if ( offset == VIC.VIC_SCANLINE )
		{
			// current scan line, lo-byte
			irqOnRaster = (short) (irqOnRaster | (value & 0xff) );
		}
		else if ( offset == VIC.VIC_CTRL1 )
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
	
	/*
	 *    $D011 	53265 	17 	Steuerregister, Einzelbedeutung der Bits (1 = an):
	 *                          Bit 7: Bit 8 von $D012
	 *                          Bit 6: Extended Color Modus
	 *                          Bit 5: Bitmapmodus
	 *                          Bit 4: Bildausgabe eingeschaltet (Effekt erst beim nächsten Einzelbild)
	 *                          Bit 3: 25 Zeilen (sonst 24)
	 *                          Bit 2..0: Offset in Rasterzeilen vom oberen Bildschirmrand	 
	 */
	
	private int getRowCount() {
		int result = readByte( VIC_CTRL1 );
		return ( result & 1<<3) == 0 ? 24: 25; 
	}
	
	/*
	 *    $D016 	53270 	22 	Steuerregister, Einzelbedeutung der Bits (1 = an):
	 *                          Bit 7..5: unbenutzt
	 *                          Bit 4: Multicolor-Modus
	 *                          Bit 3: 40 Spalten (an)/38 Spalten (aus)
	 *                          Bit 2..0: Offset in Pixeln vom linken Bildschirmrand	 
	 */
	
	private int getColumnCount() {
		int result = readByte( VIC_CTRL2 );
		return ( result & 1<<3) == 0 ? 38 : 40; 
	}	
}