package de.codesourcery.j6502.emulator;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

import de.codesourcery.j6502.utils.HexDump;

public class NewVIC extends Memory
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

	private static final int CPU_CLOCK_CYCLES_PER_LINE = 63;
	private static final int LAST_CPU_CYLE_IN_LINE = CPU_CLOCK_CYCLES_PER_LINE-1;

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


	public static final int TOTAL_RASTER_LINES = 403; // PAL-B
	public static final int VISIBLE_RASTER_LINES = 312; // PAL-B

	public static final int VBLANK_FIRST_LINE = 300 ; // PAL-B
	public static final int VBLANK_LAST_LINE = 15 ; // PAL-B

	public static final int MAX_X = 503;

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
	
	protected static final int DISPLAY_WIDTH= 504;
	protected static final int DISPLAY_HEIGHT = 312;
	
	private BufferedImage frontBuffer;
	private BufferedImage backBuffer;
	
	private WriteOnceMemory characterROM;
	
	protected static final int colorRAM = 0xD800;
	protected int videoRAM;
	protected int glyphRAM;

	private volatile int textColumnCount=40;
	private volatile int textRowCount=25;

	private final IMemoryRegion mainMemory;
	
	protected int leftBorder;
	protected int rightBorder;
	protected int topBorder;
	protected int bottomBorder;
	
	protected int backgroundColor;
	protected int borderColor;
	
	private int irqOnRaster = -1;
	
	private int beamX;
	private int beamY;

	private boolean displayEnabled; // aka 'DEN'
	private boolean badLinePossible;

	private int cycleOnCurrentLine;

	private int xScroll;
	private int yScroll;

	protected final PixelGenerator textGenerator = new PixelGenerator() 
	{
		
		@Override
		public int getPixel() {
			// TODO Auto-generated method stub
			return 0;
		}
	};

	public NewVIC(String identifier, AddressRange range,IMemoryRegion mainMemory)
	{
		super(identifier, range);
		this.mainMemory = mainMemory;
		
		/*

		The dimensions of the video display for the different VIC types are as
		follows:

		          | Video  | # of  | Visible | Cycles/ |  Visible
		   Type   | system | lines |  lines  |  line   | pixels/line
		 ---------+--------+-------+---------+---------+------------
		   6569   |  PAL-B |  312  |   284   |   63    |    403

		          | First  |  Last  |              |   First    |   Last
		          | vblank | vblank | First X coo. |  visible   |  visible
		   Type   |  line  |  line  |  of a line   |   X coo.   |   X coo.
		 ---------+--------+--------+--------------+------------+-----------
		   6569   |  300   |   15   |  404 ($194)  | 480 ($1e0) | 380 ($17c)

		If you are wondering why the first visible X coordinates seem to come after
		the last visible ones: This is because for the reference point to mark the
		beginning of a raster line, the occurrence of the raster IRQ has been
		chosen, which doesn't coincide with X coordinate 0 but with the coordinate
		given as "First X coo. of a line". The X coordinates run up to $1ff (only
		$1f7 on the 6569) within a line, then comes X coordinate 0. This is
		explained in more detail in the explanation of the structure of a raster
		line.
		
		                                          0
		        |----------VVVVVVVVVVVVVVVVVVVVVV|VVVVVVVVVVVVVV----| $1f7
		        ^ $194     ^ $1e0                ^ $1f7        ^$17c
		 */
		
		frontBuffer = new BufferedImage(DISPLAY_WIDTH,DISPLAY_HEIGHT,BufferedImage.TYPE_INT_RGB);
		backBuffer  = new BufferedImage(DISPLAY_WIDTH,DISPLAY_HEIGHT,BufferedImage.TYPE_INT_RGB);
	}
	
	protected abstract class PixelGenerator 
	{
		public abstract int getPixel();
	}

	public void tick(CPU cpu,boolean clockHigh)
	{
		if ( clockHigh )
		{
			render(cpu,clockHigh);
			cycleOnCurrentLine++;
		} else {
			render(cpu,clockHigh);
		}
	}

	private void render(CPU cpu,boolean clockHigh)
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
		 */
		int value = readByte(VIC_MEMORY_MAPPING);
		int videoRAMOffset = 1024 * ( ( value & 0b1111_0000) >>4 ); // The four most significant bits form a 4-bit number in the range 0 thru 15: Multiplied with 1024 this gives the start address for the screen character RAM.
		int glyphRAMOffset = 2048 * ( ( value & 0b0000_1110) >> 1); // Bits 1 thru 3 (weights 2 thru 8) form a 3-bit number in the range 0 thru 7: Multiplied with 2048 this gives the start address for the character set.

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

		videoRAM = bankAdr+videoRAMOffset;
		glyphRAM = bankAdr+glyphRAMOffset;
		
		// handle the next 8 pixels
		int tmpBeamX = beamX;
		int tmpBeamY = beamY;
		
		for ( int i = 0 ; i < 4 ; i++ ) 
		{
			backBuffer.setRGB( tmpBeamX , tmpBeamY , getNextPixel(tmpBeamX,tmpBeamY) );
			tmpBeamX++;
			if ( tmpBeamX == 504 ) {
				tmpBeamX = 0;
				tmpBeamY++;
				if ( tmpBeamY == 312 ) 
				{
					tmpBeamY = 0;
					swapBuffers();
				}
//				if ( irqOnRaster == tmpBeamY ) 
//				{
//					cpu.queueInterrupt();
//				}
			}
		}
		beamX = tmpBeamX;
		beamY = tmpBeamY;
	}

	@Override
	public int readByte(int offset) 
	{
		int result = super.readByte(offset);
		
		switch( offset ) 
		{
			case VIC_CTRL1:
			case VIC_SCANLINE:
				final byte lo = (byte) beamY;
				final byte hi = (byte) (beamY>>8);

				int hiBit = super.readByte( VIC.VIC_CTRL1 );
				if ( hi != 0 ) {
					hiBit |= 0b1000_0000;
				} else {
					hiBit &= 0b0111_1111;
				}
				if ( offset == VIC.VIC_CTRL1 ) {
					return hiBit;
				}
				return lo;
			default:
				// $$FALL-THROUGH$$
		}
		return result;
	}
	@Override
	public void writeByte(int offset, byte value)
	{
		super.writeByte(offset, value);
		
		switch( offset ) 
		{
			case VIC.VIC_SCANLINE:
				// current scan line, lo-byte
				irqOnRaster = (short) (irqOnRaster | (value & 0xff) );
				break;
			case VIC_CTRL1:

				/*
				 *    $D011 	53265 	17 	Steuerregister, Einzelbedeutung der Bits (1 = an):
				 *                          Bit 7: Bit 8 von $D012
				 *                          Bit 6: Extended Color Modus
				 *                          Bit 5: Bitmapmodus
				 *                          Bit 4: Bildausgabe eingeschaltet (Effekt erst beim nächsten Einzelbild)
				 *                          Bit 3: 25 Zeilen (sonst 24)
				 *                          Bit 2..0: Offset in Rasterzeilen vom oberen Bildschirmrand
				 */
				
				if ( ( value & 0b1000_0000 ) != 0 ) {
					irqOnRaster = (short) ( 0b0100 | (irqOnRaster & 0xff) ); // set hi-bit
				} else {
					irqOnRaster = (short) (irqOnRaster & 0xff); // clear hi-bit
				}
				
				yScroll= ( value & 0x111);
				displayEnabled = (value & 0b10000) != 1 ? true : false;
				textRowCount   = (value & 0b01000) != 0 ? 25 : 24; // RSEL
				updateBorders();
				System.out.println("Write to $D011: value="+Integer.toBinaryString( value & 0xff)+" , text columns: "+textColumnCount+" , text rows: "+textRowCount);
				break;
			case VIC_CTRL2:
				/*
				 *    $D016 	53270 	22 	Steuerregister, Einzelbedeutung der Bits (1 = an):
				 *                          Bit 7..5: unbenutzt
				 *                          Bit 4: Multicolor-Modus
				 *                          Bit 3: 40 Spalten (an)/38 Spalten (aus)
				 *                          Bit 2..0: Offset in Pixeln vom linken Bildschirmrand
				 */
				xScroll = value & 0b111;
				textColumnCount = ( value & 0b1000) != 0 ? 40 : 38; // CSEL
				updateBorders();
				System.out.println("Write to $D016: value="+Integer.toBinaryString( value & 0xff)+" , text columns: "+textColumnCount+" , text rows: "+textRowCount);
				break;
			case VIC_BORDER_COLOR:
				borderColor = INT_COLORS[ (value & 0xf) ];
				break;
			case VIC_BACKGROUND_COLOR:
				backgroundColor = INT_COLORS[ (value & 0xf ) ];
				break;
			default:
				// $$FALL-THROUGH$$
		}
	}

	private int getNextPixel(int beamX,int beamY)
	{
		if ( beamY < topBorder || beamY > bottomBorder || beamX < leftBorder || beamX > rightBorder  ) 
		{
			return borderColor;
		}
		// get current character color
		int x = beamX - leftBorder;
		int y = beamY - topBorder;
		
		final int byteOffset = (y/8) * textColumnCount + (x/8);
		final int bitOffset = 7-(x%8);
		
//		System.out.println("("+x+","+y+") => byte "+byteOffset+" , bit "+bitOffset);
		
//		System.out.println("Video RAM = "+HexDump.toAdr( videoRAM ) );

		final int character = mainMemory.readByte( videoRAM + byteOffset );

		final int glyphAdr = character << 3; // *8 bytes per glyph
		final int word = characterROM.readByte( glyphAdr + (y%8) );
//		System.out.println("Offset "+byteOffset+" = "+(char) character);
		int mask = 1 << bitOffset;
		if ( (word & mask) != 0 ) 
		{
			final int color = mainMemory.readByte( colorRAM + byteOffset ) % 0b1111; // color ram is actually a 4-bit static RAM
			return INT_COLORS[ color ];
		}			
		return backgroundColor;
	}

	private boolean isBadLine() // Only call this method on the negative clock edge
	{
		if ( beamY == 0x30 ) {
			badLinePossible |= displayEnabled;
		}
		/*

 A Bad Line Condition is given at any arbitrary clock cycle, if at the
 negative edge of �0 at the beginning of the cycle RASTER >= $30 and RASTER
 <= $f7 and the lower three bits of RASTER are equal to YSCROLL and if the
 display_enabled (DEN) bit was set during an arbitrary cycle of raster line $30.
		 */
		return beamY >= 0x30 && beamY <= 0xf7 && (beamY & 0b111) == yScroll && badLinePossible;
	}

	private void updateBorders()
	{
	/*
The VIC uses two flip flops to generate the border around the display
window: A main border flip flop and a vertical border flip flop.

The main border flip flop controls the border display. If it is set, the
VIC displays the color stored in register $d020, otherwise it displays the
color that the priority multiplexer switches through from the graphics or
sprite data sequencer. So the border overlays the text/bitmap graphics as
well as the sprites. It has the highest display priority.

The vertical border flip flop is for auxiliary control of the upper/lower
border. If it is set, the main border flip flop cannot be reset. Apart from
that, the vertical border flip flop controls the output of the graphics
data sequencer. The sequencer only outputs data if the flip flop is
not set, otherwise it displays the background color. This was probably done
to prevent sprite-graphics collisions in the border area.

There are 2�2 comparators belonging to each of the two flip flops. There
comparators compare the X/Y position of the raster beam with one of two
hardwired values (depending on the state of the CSEL/RSEL bits) to control
the flip flops. The comparisons only match if the values are reached
precisely. There is no comparison with an interval.

The horizontal comparison values:

       |   CSEL=0   |   CSEL=1
 ------+------------+-----------
 Left  |  31 ($1f)  |  24 ($18)
 Right | 335 ($14f) | 344 ($158)

And the vertical ones:

        |   RSEL=0  |  RSEL=1
 -------+-----------+----------
 Top    |  55 ($37) |  51 ($33)
 Bottom | 247 ($f7) | 251 ($fb)

The flip flops are switched according to the following rules:
*/
		final boolean csel = textColumnCount == 40 ? true : false;
		
		if ( csel ) {
			leftBorder = 24;
			rightBorder = 344;
		} else {
			leftBorder = 31;
			rightBorder = 335;
		}

		final boolean rsel = textRowCount == 25 ? true : false;

		if ( rsel ) {
			topBorder = 51;
			bottomBorder = 251;
		} else {
			topBorder = 55;
			bottomBorder = 247;
		}
	}

	@Override
	public void reset()
	{
		super.reset();

		displayEnabled = true;

		textColumnCount=40;
		textRowCount=25;

		cycleOnCurrentLine = 0;

		irqOnRaster = -1;
		
		beamX = 0;
		beamY = 0;
		badLinePossible = true;

		characterROM = MemorySubsystem.loadCharacterROM();
	}

	private void swapBuffers()
	{
		BufferedImage tmp = frontBuffer;
		frontBuffer= backBuffer;
		backBuffer = tmp;
	}

	public void render(Graphics2D graphics,int width,int height)
	{
		graphics.drawImage( frontBuffer , 0 , 0 , width, height , null );
	}
}