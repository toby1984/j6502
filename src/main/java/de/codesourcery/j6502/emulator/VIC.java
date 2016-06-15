package de.codesourcery.j6502.emulator;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import de.codesourcery.j6502.emulator.CPU.IRQType;
import de.codesourcery.j6502.emulator.MemorySubsystem.RAMView;
import de.codesourcery.j6502.utils.HexDump;

public class VIC extends IMemoryRegion
{
    private static final int RASTER_IRQ_X_COORDINATE = 76; // only on 6569
    public static final boolean DEBUG_RASTER_IRQ = true;
    public static final boolean DEBUG_FPS = true;
    public static final boolean DEBUG_GRAPHIC_MODES = true;

    protected static final boolean DEBUG_MEMORY_LAYOUT = true;

    protected static final boolean DEBUG_SET_GRAPHICS_MODE = true;

    public static final int TOTAL_RASTER_LINES = 403; // PAL-B
    public static final int VISIBLE_RASTER_LINES = 312; // PAL-B
    public static final int VBLANK_FIRST_LINE = 300 ; // PAL-B
    public static final int VBLANK_LAST_LINE = 15 ; // PAL-B
    public static final int MAX_X = 503;

    // interrupt flag bits
    public static final int IRQ_LIGHTPEN = 1<<3;
    public static final int IRQ_SPRITE_SPRITE = 1<<2;
    public static final int IRQ_SPRITE_BACKGROUND = 1<<1;
    public static final int IRQ_RASTER = 1<<0;

    protected static final class GraphicsMode
    {
        public final boolean bitMapMode;
        public final boolean extendedColorMode;
        public final boolean multiColorMode;

        public GraphicsMode(boolean bitMapMode, boolean extendedColorMode, boolean multiColorMode) {
            this.bitMapMode = bitMapMode;
            this.extendedColorMode = extendedColorMode;
            this.multiColorMode = multiColorMode;
        }

        @Override
        public String toString() {
            return "bitmap: "+bitMapMode+" | multi-color: "+multiColorMode+" | extended: "+extendedColorMode;
        }
    }

    /* VIC I/O area is fixed at $D000 - $DFFF.
     *
     * VIC $d000 - $d02f
     * ---
     *
     * Adresse (hex)  Adresse (dez)  Register  Inhalt
     *  ( ) $D000  53248  0  X-Koordinate für Sprite 0 (0..255)
     *  ( ) $D001  53249  1  Y-Koordinate für Sprite 0 (0..255)
     *  ( ) $D002  53250  2  X-Koordinate für Sprite 1 (0..255)
     *  ( ) $D003  53251  3  Y-Koordinate für Sprite 1 (0..255)
     *  ( ) $D004  53252  4  X-Koordinate für Sprite 2 (0..255)
     *  ( ) $D005  53253  5  Y-Koordinate für Sprite 2 (0..255)
     *  ( ) $D006  53254  6  X-Koordinate für Sprite 3 (0..255)
     *  ( ) $D007  53255  7  Y-Koordinate für Sprite 3 (0..255)
     *  ( ) $D008  53256  8  X-Koordinate für Sprite 4 (0..255)
     *  ( ) $D009  53257  9  Y-Koordinate für Sprite 4 (0..255)
     *  ( ) $D00A  53258  10  X-Koordinate für Sprite 5 (0..255)
     *  ( ) $D00B  53259  11  Y-Koordinate für Sprite 5 (0..255)
     *  ( ) $D00C  53260  12  X-Koordinate für Sprite 6 (0..255)
     *  ( ) $D00D  53261  13  Y-Koordinate für Sprite 6 (0..255)
     *  ( ) $D00E  53262  14  X-Koordinate für Sprite 7 (0..255)
     *  ( ) $D00F  53263  15  Y-Koordinate für Sprite 7 (0..255)
     *
     *  ( ) $D010  53264  16  Bit 8 für die obigen X-Koordinaten (0..255) , jedes Bit steht für eins der Sprites 0..7
     *  ( )                       The least significant bit corresponds to sprite #0, and the most sigificant bit to sprite #7.
     *
     *  ( ) $D011  53265  17  Steuerregister, Einzelbedeutung der Bits (1 = an):
     *  ( )                       Bit 7: Bit 8 von $D012
     *  ( )                       Bit 6: Extended Color Modus
     *  ( )                       Bit 5: Bitmapmodus
     *  ( )                       Bit 4: Bildausgabe eingeschaltet (Effekt erst beim nächsten Einzelbild)
     *  ( )                       Bit 3: 25 Zeilen (sonst 24)
     *  ( )                       Bit 2..0: Offset in Rasterzeilen vom oberen Bildschirmrand
     *  ( )
     *  ( ) $D012  53266  18  Lesen    : Aktuelle Rasterzeile
     *  ( )                   Schreiben: Rasterzeile, bei der ein IRQ ausgelöst wird (zugehöriges Bit 8 in $D011)
     *
     *  ( ) $D013  53267  19  Lightpen X-Koordinate (assoziiert mit Pin LP am VIC)
     *
     *  ( ) $D014  53268  20  Lightpen Y-Koordinate
     *
     *  ( ) $D015  53269  21  Spriteschalter, Bit = 1: Sprite n an (0..255)
     *
     *  ( ) $D016  53270  22  Steuerregister, Einzelbedeutung der Bits (1 = an):
     *  ( )                       Bit 7..5: unbenutzt
     *  ( )                       Bit 4: Multicolor-Modus
     *  ( )                       Bit 3: 40 Spalten (an)/38 Spalten (aus)
     *  ( )                       Bit 2..0: Offset in Pixeln vom linken Bildschirmrand
     *  ( )
     *  ( ) $D017  53271  23  Spriteschalter, Bit = 1: Sprite n doppelt hoch (0..255)
     *
     *  ( ) $D018  53272  24  VIC-Speicherkontrollregister
     *  ( )
     *  ( )                       Bit 7..4: Basisadresse des Bildschirmspeichers in aktueller 16K-Bank des VIC = 64*(Bit 7…4)[2]
     *  ( )                       Bit 3..1: Basisadresse des Zeichensatzes = 1024*(Bit 3…1).
     *  ( )
     *  ( ) _oder_ im Bitmapmodus
     *  ( )
     *  ( )                       Bit 3: Basisadresse der Bitmap = 1024*8 (Bit 3)
     *  ( )                       (8kB-Schritte in VIC-Bank)
     *  ( )                       Bit 0 ist immer 1: nur 2 kB Schritte in VIC-Bank[3]
     *  ( )
     *  ( )                       Beachte: Das Character-ROM wird nur in VIC-Bank 0 und 2 ab 4096 eingeblendet
     *  ( )
     *  ( ) $D019  53273  25  Interrupt Request, Bit = 1 = an
     *  ( )                       Lesen:
     *  ( )                       Bit 7: IRQ durch VIC ausgelöst
     *  ( )                       Bit 6..4: unbenutzt
     *  ( )                       Bit 3: Anforderung durch Lightpen
     *  ( )                       Bit 2: Anforderung durch Sprite-Sprite-Kollision (Reg. $D01E)
     *  ( )                       Bit 1: Anforderung durch Sprite-Hintergrund-Kollision (Reg. $D01F)
     *  ( )                       Bit 0: Anforderung durch Rasterstrahl (Reg. $D012)
     *  ( )                       Schreiben:
     *  ( )                       1 in jeweiliges Bit schreiben = zugehöriges Interrupt-Flag löschen
     *
     *  ( ) $D01A  53274  26  Interrupt Request: Maske, Bit = 1 = an
     *  ( )                       Ist das entsprechende Bit hier und in $D019 gesetzt, wird ein IRQ ausgelöst und Bit 7 in $D019 gesetzt
     *  ( )                       Bit 7..4: unbenutzt
     *  ( )                       Bit 3: IRQ wird durch Lightpen ausgelöst
     *  ( )                       Bit 2: IRQ wird durch S-S-Kollision ausgelöst
     *  ( )                       Bit 1: IRQ wird durch S-H-Kollision ausgelöst
     *  ( )                       Bit 0: IRQ wird durch Rasterstrahl ausgelöst
     *
     *  ( ) $D01B  53275  27  Priorität Sprite-Hintergrund, Bit = 1: Hintergrund liegt vor Sprite n (0..255)
     *
     *  ( ) $D01C  53276  28  Sprite-Darstellungsmodus, Bit = 1: Sprite n ist Multicolor
     *
     *  ( ) $D01D  53277  29  Spriteschalter, Bit = 1: Sprite n doppelt breit (0..255)
     *
     *  ( ) $D01E  53278  30  Sprite-Info: Bits = 1: Sprites miteinander kollidiert (0..255)
     *  ( )                       Wird durch Zugriff gelöscht
     *
     *  ( ) $D01F  53279  31  Sprite-Info: Bits = 1: Sprite n mit Hintergrund kollidiert (0..255)
     *  ( )                       Wird durch Zugriff gelöscht
     *
     *  ( ) $D020  53280  32  Farbe des Bildschirmrands (0..15)
     *
     *  ( ) $D021  53281  33  Bildschirmhintergrundfarbe (0..15)
     *
     *  ( ) $D022  53282  34  Bildschirmhintergrundfarbe 1 bei Extended Color Modus (0..15)
     *
     *  ( ) $D023  53283  35  Bildschirmhintergrundfarbe 2 bei Extended Color Mode (0..15)
     *
     *  ( ) $D024  53284  36  Bildschirmhintergrundfarbe 3 bei Extended Color Mode (0..15)
     *
     *  ( ) $D025  53285  37  gemeinsame Spritefarbe 0 im Sprite-Multicolormodus, Bitkombination %01 (0..15)
     *  ( ) $D026  53286  38  gemeinsame Spritefarbe 1 im Sprite-Multicolormodus, Bitkombination %11 (0..15)
     *  ( )
     *  ( ) $D027  53287  39  Farbe Sprite 0, Bitkombination %10 (0..15)
     *  ( ) $D028  53288  40  Farbe Sprite 1, Bitkombination %10 (0..15)
     *  ( ) $D029  53289  41  Farbe Sprite 2, Bitkombination %10 (0..15)
     *  ( ) $D02A  53290  42  Farbe Sprite 3, Bitkombination %10 (0..15)
     *  ( ) $D02B  53291  43  Farbe Sprite 4, Bitkombination %10 (0..15)
     *  ( ) $D02C  53292  44  Farbe Sprite 5, Bitkombination %10 (0..15)
     *  ( ) $D02D  53293  45  Farbe Sprite 6, Bitkombination %10 (0..15)
     *  ( ) $D02E  53294  46  Farbe Sprite 7, Bitkombination %10 (0..15)
     *  ( ) $D02F  53295  47  nur VIC IIe (C128)
     *  ( )                       Bit 7..3: unbenutzt
     *  ( )                       Bit 2..0: Status der 3 zusätzlichen Tastaturpins
     *  ( )                       $D030  53296  48  nur VIC IIe (C128)
     *  ( )
     *  ( )                       Bit 7..2: unbenutzt
     *  ( )                       Bit 1: Testmode
     *  ( )                       Bit 0: 0 => Slow-Mode (1 MHz), 1 => Fast-Mode (2 MHz)
     */

    public static final boolean DEBUG_RASTER_IRQ = false;
    protected static final boolean DEBUG_MEMORY_LAYOUT = true;
    protected static final boolean DEBUG_SET_GRAPHICS_MODE = true;
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

    /* $D011  53265  17  Steuerregister, Einzelbedeutung der Bits (1 = an):
     *                       Bit 7: Bit 8 von $D012
     *                       Bit 6: Extended Color Modus
     *                       Bit 5: Bitmapmodus
     *                       Bit 4: Bildausgabe eingeschaltet (Effekt erst beim nächsten Einzelbild)
     *                       Bit 3: 25 Zeilen (sonst 24)
     *                       Bit 2..0: Offset in Rasterzeilen vom oberen Bildschirmrand
     */
    public  static final int VIC_CTRL1 = 0x11;

    /*
     * $D012  53266  18  Lesen    : Aktuelle Rasterzeile
     *                   Schreiben: Rasterzeile, bei der ein IRQ ausgelöst wird (zugehöriges Bit 8 in $D011)
     */
    public  static final int VIC_SCANLINE = 0x12;

    public  static final int VIC_LIGHTPEN_X_COORDS = 0x13;
    public  static final int VIC_LIGHTPEN_Y_COORDS = 0x14;

    public  static final int VIC_SPRITE_ENABLE = 0x15;

    /*
     * $D016  53270  22  Steuerregister, Einzelbedeutung der Bits (1 = an):
     *                       Bit 7..5: unbenutzt
     *                       Bit 4: Multicolor-Modus
     *                       Bit 3: 40 Spalten (an)/38 Spalten (aus)
     *                       Bit 2..0: Offset in Pixeln vom linken Bildschirmrand
     */
    public  static final int VIC_CTRL2 = 0x16;

    public  static final int VIC_SPRITE_DOUBLE_HEIGHT = 0x17;

    /*
     * $D018  53272  24  VIC-Speicherkontrollregister
     *
     *                       Bit 7..4: Basisadresse des Bildschirmspeichers in aktueller 16K-Bank des VIC = 64*(Bit 7…4)[2]
     *                       Bit 3..1: Basisadresse des Zeichensatzes = 1024*(Bit 3…1).
     */
    public  static final int VIC_MEMORY_MAPPING = 0x18;

    /*
     * $D019  53273  25  Interrupt Request, Bit = 1 = an
     *                  Lesen:
     *                  Bit 7: IRQ durch VIC ausgelöst
     *                  Bit 6..4: unbenutzt
     *                  Bit 3: Anforderung durch Lightpen
     *                  Bit 2: Anforderung durch Sprite-Sprite-Kollision (Reg. $D01E)
     *                  Bit 1: Anforderung durch Sprite-Hintergrund-Kollision (Reg. $D01F)
     *                  Bit 0: Anforderung durch Rasterstrahl (Reg. $D012)
     *                  Schreiben:
     *                  1 in jeweiliges Bit schreiben = zugehöriges Interrupt-Flag löschen
     */
    public  static final int VIC_IRQ_ACTIVE_BITS = 0x19;

    /*
     * $D01A  53274  26  Interrupt Request: Maske, Bit = 1 = an
     *                       Ist das entsprechende Bit hier und in $D019 gesetzt, wird ein IRQ ausgelöst und Bit 7 in $D019 gesetzt
     *                       Bit 7..4: unbenutzt
     *                       Bit 3: IRQ wird durch Lightpen ausgelöst
     *                       Bit 2: IRQ wird durch S-S-Kollision ausgelöst
     *                       Bit 1: IRQ wird durch S-H-Kollision ausgelöst
     *                       Bit 0: IRQ wird durch Rasterstrahl ausgelöst
     */
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

    public  static final int VIC_SPRITE_COLOR01_MULTICOLOR_MODE = 0x25;
    public  static final int VIC_SPRITE_COLOR11_MULTICOLOR_MODE = 0x26;

    public  static final int VIC_SPRITE0_COLOR10 = 0x27;
    public  static final int VIC_SPRITE1_COLOR10 = 0x28;
    public  static final int VIC_SPRITE2_COLOR10 = 0x29;
    public  static final int VIC_SPRITE3_COLOR10 = 0x2a;
    public  static final int VIC_SPRITE4_COLOR10 = 0x2b;
    public  static final int VIC_SPRITE5_COLOR10 = 0x2c;
    public  static final int VIC_SPRITE6_COLOR10 = 0x2d;
    public  static final int VIC_SPRITE7_COLOR10 = 0x2e;

    public static final int DISPLAY_WIDTH= 504;
    public static final int DISPLAY_HEIGHT = 312;

    // color palette taken from http://www.pepto.de/projects/colorvic/

    public static final Color Black     = color(  0,  0,  0);
    public static final Color White     = color(255,255,255);
    public static final Color Red       = color(104, 55, 43);
    public static final Color Cyan      = color(112,164,178);
    public static final Color Violet    = color(111, 61,134);
    public static final Color Green     = color( 88,141, 67);
    public static final Color Blue      = color( 53, 40,121);
    public static final Color Yellow    = color(184,199,111);
    public static final Color Orange    = color(111, 79, 37);
    public static final Color Brown     = color( 67, 57,  0);
    public static final Color Lightred  = color(154,103, 89);
    public static final Color Grey1     = color( 68, 68, 68);
    public static final Color Grey2     = color(108,108,108);
    public static final Color Lightgreen= color(154,210,132);
    public static final Color Lightblue = color(108, 94,181);
    public static final Color Lightgrey = color(149,149,149);

    private static Color color(int r,int g,int b) { return new Color(r,g,b); }

    public static final Color[] AWT_COLORS = { Black,White,Red,Cyan,Violet,Green,Blue,Yellow,Orange,Brown,Lightred,Grey1,Grey2,Lightgreen,Lightblue,Lightgrey};

    public static final int[] RGB_COLORS = new int[16];

    static
    {
        for ( int i = 0 ; i < AWT_COLORS.length ; i++ )
        {
            RGB_COLORS[i] = AWT_COLORS[i].getRGB();
        }
    }

    public static final int SPRITE_WIDTH = 24;
    public static final int SPRITE_HEIGHT = 21;

    public static final int SPRITE_DOUBLE_WIDTH = SPRITE_WIDTH*2;
    public static final int SPRITE_DOUBLE_HEIGHT = SPRITE_HEIGHT*2;

    // special marker color
    protected static final int SPRITE_TRANSPARENT_COLOR = 0xdeadbeef;

    /*
     *  The dimensions of the video display for the different VIC types are as
     *  follows:
     *
     *            | Video  | # of  | Visible | Cycles/ |  Visible
     *     Type   | system | lines |  lines  |  line   | pixels/line
     *   ---------+--------+-------+---------+---------+------------
     *     6569   |  PAL-B |  312  |   284   |   63    |    403
     *
     *            | First  |  Last  |              |   First    |   Last
     *            | vblank | vblank | First X coo. |  visible   |  visible
     *     Type   |  line  |  line  |  of a line   |   X coo.   |   X coo.
     *   ---------+--------+--------+--------------+------------+-----------
     *     6569   |  300   |   15   |  404 ($194)  | 480 ($1e0) | 380 ($17c)
     *
     *  If you are wondering why the first visible X coordinates seem to come after
     *  the last visible ones: This is because for the reference point to mark the
     *  beginning of a raster line, the occurrence of the raster IRQ has been
     *  chosen, which doesn't coincide with X coordinate 0 but with the coordinate
     *  given as "First X coo. of a line". The X coordinates run up to $1ff (only
     *  $1f7 on the 6569) within a line, then comes X coordinate 0. This is
     *  explained in more detail in the explanation of the structure of a raster
     *  line.
     *
     *                                            0
     *          |----------VVVVVVVVVVVVVVVVVVVVVV|VVVVVVVVVVVVVV----| $1f7
     *          ^ $194     ^ $1e0                ^ $1f7        ^$17c
     *
     * - Screen has 312 raster lines vertically and 504 pixels horizontally.
     * - Rendering happens in a 320x200 pixel area
     * - Horizontal border is 41.5 pixels wide
     * - Vertical border is 42 pixels high
     * - vblank area is 14 lines at start/end of screen
     * - hblank area is 50.5 pixels at start/end of each raster line
     *
     *  +---------------------------------------------------------+ <--- 0
     *  |                          VBLANK                         |
     *  +--------+------------------------------------------------+ <--- 14
     *  |        |               BORDER                  |        |
     *  | HBLANK |                                       | HBLANK |
     *  |        +------+-------------------------+------+        | <--- 56
     *  |        |      |                         |      |        |
     *  |        |      |                         |      |        |
     *  |        |      |                         |      |        |
     *  |        |      |                         |      |        |
     *  |        | BORD |                         | BORD |        |
     *  |        |      |                         |      |        |
     *  |        |      |                         |      |        |
     *  |        |      |                         |      |        |
     *  |        +------+-------------------------+------|        | <--- 256
     *  |        |               BORDER                  |        |
     *  | HBLANK |                                       | HBLANK |
     *  +--------+---------------------------------------+--------+ <--- 298
     *  |                          VBLANK                         |
     *  +---------------------------------------------------------+ <--- 313
     *  ^        ^      ^                         ^      ^        ^
     *  |        |      |                         |      |        |
     *  0      50.5    92                        412   453.5     504
     */

    protected static final int FIRST_VISIBLE_LINE = 14;

    protected static final int FIRST_DISPLAY_AREA_X = 92;
    protected static final int LAST_DISPLAY_AREA_X = 411;

    protected static final int FIRST_DISPLAY_AREA_Y = 56;
    protected static final int LAST_DISPLAY_AREA_Y = 255;

    protected static final int LAST_VISIBLE_LINE = 298;

    protected abstract class Sequencer
    {
        public abstract int getRGBColor();
        public abstract void onStartOfLine();
    }

    protected final class BackgroundSequencer extends Sequencer
    {
        private int currentGlyph;
        private int currentColor;
        private int currentVideoData; // video RAM data shift register

        private int dataPtr; // pointer into video/color RAM (relative to start of memory region)
        private int bitCounter; // counts pixels (bits) of current byte

        @Override
        public int getRGBColor()
        {
            if ( (bitCounter % 8) == 0 )
            {
            	// calculate Y offset relative to start of display area
            	final int displayY  = beamY - FIRST_DISPLAY_AREA_Y;
            	if ( displayY >= 0 )
            	{
            		currentVideoData  = videoData( displayY );
            		currentColor = colorData( displayY );
            		currentGlyph = glyphData( displayY );
            		dataPtr++;
            	}
            }
            bitCounter++;

            final int pixelColor;
            if ( isBitmapMode() )
            {
                if ( isExtendedColorMode() ) // there are not extended-color bitmap modes
                {
                    pixelColor = RGB_COLORS[ 0 ];
                    currentVideoData <<= 1;
                }
                else if ( isMultiColor() ) // multi-color bitmap mode
                {
                    switch( ( currentVideoData & 0b1100_0000) >>> 6 )
                    {
                        case 0b00: pixelColor = rgbBackgroundColor; break;
                        case 0b01: pixelColor = RGB_COLORS[ (currentColor >> 4) & 0b1111 ]; break;
                        case 0b10: pixelColor = RGB_COLORS[ currentColor & 0b1111 ] ; break;
                        case 0b11: pixelColor = RGB_COLORS[ currentGlyph & 0b1111 ] ; break;
                        default: throw new RuntimeException("Unreachable code reached");
                    }
                    if ( (bitCounter % 2) == 0 ) {
                        currentVideoData <<= 2;
                    }
                }
                else  // hi-res bitmap mode
                {
                    if ( ( currentVideoData & 1<<7) != 0 )
                    {
                        pixelColor = RGB_COLORS[ ( currentColor >> 4) & 0b1111 ];
                    } else {
                        pixelColor = RGB_COLORS[ currentColor & 0b1111 ];
                    }
                    currentVideoData <<= 1;
                }
            }
            else if ( isExtendedColorMode() ) // ECM text mode
            {
                if ( (currentVideoData & 1<<7) != 0 ) // => pixel is set
                {
                    pixelColor = RGB_COLORS[ currentColor & 0b1111 ];
                }
                else
                {
                    switch ( (currentGlyph & 0b11000000) >> 6 )
                    {
                        case 0b00: pixelColor = rgbBackgroundColor; break;
                        case 0b01: pixelColor = RGB_COLORS[ rgbBackgroundExt0Color ]; break;
                        case 0b10: pixelColor = RGB_COLORS[ rgbBackgroundExt1Color]; break;
                        case 0b11: pixelColor = RGB_COLORS[ rgbBackgroundExt2Color]; break;
                        default: throw new RuntimeException("Unreachable code reached");
                    }
                }
            }
            else  if ( isMultiColor() )  // MC text mode
            {
                /* This mode allows for displaying four-colored characters at the cost of
                 * horizontal resolution. If bit 11 of the c-data is zero, the character is
                 * displayed as in standard text mode with only the colors 0-7 available for
                 * the foreground. If bit 11 is set, each two adjacent bits of the dot matrix
                 * form one pixel. By this means, the resolution of a character is reduced to
                 * 4x8 (the pixels are twice as wide, so the total width of the characters
                 * doesn't change).
                 */
                if ( (currentColor & 1<<3) != 0 ) { // MC character mode, mc glyph

                    switch( (currentVideoData & 0b1100_0000) >>> 6)
                    {
                        case 0b00: pixelColor = rgbBackgroundColor;  break;
                        case 0b01: pixelColor = rgbBackgroundExt0Color; break;
                        case 0b10: pixelColor = rgbBackgroundExt1Color; break;
                        case 0b11: pixelColor = RGB_COLORS[ currentColor & 0b0111 ]; break;
                        default: throw new RuntimeException("Unreachable code reached");
                    }
                    if ( (bitCounter % 2) == 0 ) {
                        currentVideoData <<= 2;
                    }
                } else { // MC character mode , regular glyph (color <= 7)
                    pixelColor = (currentVideoData & 1<<7) != 0 ? RGB_COLORS[ currentColor & 0b0111 ] : rgbBackgroundColor;
                    currentVideoData <<= 1;
                }
            } else { // standard text mode
                pixelColor = (currentVideoData & 1<<7) != 0 ? RGB_COLORS[ currentColor & 0b1111 ] : rgbBackgroundColor;
                currentVideoData <<= 1;
            }
            return pixelColor;
        }

        private int videoData(int displayY)
        {
            // calculate row offset
            // video data is organized column-wise , so 8 successive bytes belong to 8 successive rows on the screen
            final int rowOffset = displayY & 0b111;

            if ( isTextMode() )
            {
            	final int rowStartOffset = (displayY/8)*40;
            	final int charPtr = videoRAMAdr+rowStartOffset+dataPtr;
                final int character = vicAddressView.readByte( charPtr );
                if ( isExtendedColorMode() )
                {
                    return vicAddressView.readByte( charROMAdr + ( character & 0b0011_1111) *8 + rowOffset );
                }
                return vicAddressView.readByte( charROMAdr + character*8 + rowOffset );
            }

            // bitmap mode
            final int bitmapRowStartOffset = (displayY/8)*40*8;
	        return vicAddressView.readByte( bitmapRAMAdr + bitmapRowStartOffset + dataPtr*8 + rowOffset );
        }

        private int glyphData(int displayY)
        {
        	if ( isTextMode() && isExtendedColorMode() )
        	{
        		final int rowStartOffset = (displayY/8)*40;
        		int charPtr = videoRAMAdr+rowStartOffset+dataPtr;
        		return vicAddressView.readByte( charPtr );
        	}
        	return 0;
        }

        private int colorData(int displayY)
        {
            final int rowStartOffset = (displayY/8)*40;

            // fetch data from color ram
            if ( isTextMode() )
            {
                return colorMemory.readByte( 0x800 + rowStartOffset + dataPtr );
            }

            // bitmap mode
            if ( isMultiColor() ) {
                return colorMemory.readByte( 0x800 + rowStartOffset + dataPtr );
            }
            return vicAddressView.readByte( videoRAMAdr + rowStartOffset + dataPtr );
        }

//        private void fetchVideoData()
//        {
//            // calculate Y offset relative to start of display area
//            final int displayY  = beamY - FIRST_DISPLAY_AREA_Y;
//            if ( displayY < 0 || displayY >= 200 ) {
//                return;
//            }
//            // calculate row offset
//            // video data is organized column-wise , so 8 successive bytes belong to 8 successive rows on the screen
//            final int rowOffset = displayY & 0b111;
//
//            final int rowStartOffset = (displayY/8)*40;
//            final int bitmapRowStartOffset = (displayY/8)*40*8;
//
//            final boolean isTextMode = isTextMode();
//            final boolean extendedColorTextMode = isTextMode && isExtendedColorMode();
//
//            int charPtr = videoRAMAdr+rowStartOffset;
//            for ( int col = 0 ; col < 40 ; col++ , charPtr++ )
//            {
//                if ( isTextMode )
//                {
//                    final int character = vicAddressView.readByte( charPtr );
//                    if ( extendedColorTextMode )
//                    {
//                        // in extended color text mode, the upper two bits of the character code are used as color indicator bits
//                        glyphData[col] = (byte) character;
//                        videoData[col] = vicAddressView.readByte( charROMAdr + ( character & 0b0011_1111) *8 + rowOffset );
//                    } else {
//                        videoData[col] = vicAddressView.readByte( charROMAdr + character*8 + rowOffset );
//                    }
//                }
//                else
//                {
//                    // bitmap mode
//                    videoData[col] = vicAddressView.readByte( bitmapRAMAdr + bitmapRowStartOffset + col*8 + rowOffset );
//                }
//            }
//
//            // fetch data from color ram
//            if ( isTextMode ) {
//                colorMemory.bulkRead( 0x800 + rowStartOffset , colorData , 40 );
//            }
//            else
//            {
//                if ( isMultiColor() ) {
//                    colorMemory.bulkRead( 0x800 + rowStartOffset , glyphData , 40 );
//                }
//                vicAddressView.bulkRead( videoRAMAdr + rowStartOffset , colorData , 40 );
//            }
//        }

        @Override
        public void onStartOfLine()
        {
            dataPtr = 0;
            bitCounter = 0;
        }
    }

    protected final Sequencer pixelSequencer = new BackgroundSequencer();

    // START: frame buffer

    // @GuardedBy( frontBuffer )
    private BufferedImage frontBuffer;

    // @GuardedBy( frontBuffer )
    private Graphics2D frontBufferGfx;

    // @GuardedBy( frontBuffer )
    private BufferedImage backBuffer;

    // @GuardedBy( frontBuffer )
    private Graphics2D backBufferGfx;

    // @GuardedBy( frontBuffer );
    private int[] imagePixelData;

    private int imagePixelPtr;

    private long previousFrameTimestamp; // TODO: DEBUG code
    private long frameCounter; // TODO: DEBUG code
    private long totalFrameTime; // TODO: DEBUG code
    private float fps; // TODO: DEBUG code

    // END: frame buffer

    protected boolean charROMHidden;
    protected int bankAdr;
    protected int videoRAMAdr;
    protected int bitmapRAMAdr;
    protected int builtinCharRomStart;
    protected int builtinCharRomEnd;
    protected int charROMAdr;

    protected final IMemoryRegion vicAddressView;
    protected final IMemoryRegion colorMemory;

    // VIC registers
    protected int memoryMapping;
    protected int enabledInterruptFlags;
    protected int triggeredInterruptFlags;

    protected int rasterIRQLine;

    private int vicCtrl1;
    private int vicCtrl2;

    protected int beamY;
    protected int beamX;

    protected int backgroundColor;
    protected int rgbBackgroundColor;

    protected int backgroundExt0Color;
    protected int rgbBackgroundExt0Color;

    protected int backgroundExt1Color;
    protected int rgbBackgroundExt1Color;

    protected int backgroundExt2Color;
    protected int rgbBackgroundExt2Color;

    protected int borderColor;
    protected int rgbBorderColor;

    public VIC(String identifier, AddressRange range,final MemorySubsystem mainMemory)
    {
        super(identifier, MemoryType.IOAREA ,range);

        this.vicAddressView = new IMemoryRegion("VIC memory view", MemoryType.IOAREA , AddressRange.range(0,0xffff))
        {
            private final RAMView ram = mainMemory.getRAMView();

            @Override public boolean isReadsReturnWrites(int offset) { throw new UnsupportedOperationException("not implemented"); }
            @Override public String dump(int offset, int len) { throw new UnsupportedOperationException("not implemented"); }
            @Override public void bulkWrite(int startingAddress, byte[] data, int datapos,int len) { throw new UnsupportedOperationException("not implemented"); }
            @Override public void writeWord(int offset, short value) { throw new UnsupportedOperationException("not implemented"); }
            @Override public void writeByte(int offset, byte value) { throw new UnsupportedOperationException("not implemented"); }
            @Override public void reset() { throw new UnsupportedOperationException("not implemented"); }
            @Override public int readWord(int offset) { throw new UnsupportedOperationException("not implemented"); }

            private boolean isNotWithinCharacterROM(int address)
            {
                return address < builtinCharRomStart || address >= builtinCharRomEnd;
            }

            @Override
            public int readByteNoSideEffects(int adr)
            {
                if ( charROMHidden || isNotWithinCharacterROM( adr ) )
                {
                    return ram.readByteNoSideEffects(adr);
                }
                return mainMemory.getCharacterROM().readByteNoSideEffects( adr - charROMAdr );
            }

            @Override
            public int readByte(int adr)
            {
                if ( charROMHidden || isNotWithinCharacterROM( adr ) )
                {
                    return ram.readByte(adr);
                }
                return mainMemory.getCharacterROM().readByte( adr - charROMAdr );
            }
        };

        this.colorMemory = mainMemory.getColorRAMBank();

        frontBuffer = new BufferedImage(DISPLAY_WIDTH,DISPLAY_HEIGHT,BufferedImage.TYPE_INT_RGB);
        backBuffer  = new BufferedImage(DISPLAY_WIDTH,DISPLAY_HEIGHT,BufferedImage.TYPE_INT_RGB);

        frontBufferGfx = frontBuffer.createGraphics();
        backBufferGfx = backBuffer.createGraphics();

        imagePixelData = ((DataBufferInt) backBuffer.getRaster().getDataBuffer()).getData();
    }

    public void tick(CPU cpu,boolean clockHigh)
    {
        // copy stuff to local variables, hoping that the compiler will put
        // these in registers and avoid memory accesses...
        int beamX = this.beamX;
        int beamY = this.beamY;
        int imagePixelPtr = this.imagePixelPtr;
        int[] pixelData = this.imagePixelData;
        final int borderColor = this.rgbBorderColor;
        
        /*
         * TODO: Nasty hack with rasterIRQLine+4 because I'm not honoring the cycle timings at all....fix this !!!
         */
        final int rasterPoint = isRasterIRQEnabled() ? rasterIRQLine+4 : -1;

        // we'll render 4 pixels per clock phase
        for ( int i = 0 ; i < 4 ; i++ )
        {
            if ( beamX == 0  )
            {
                pixelSequencer.onStartOfLine();
            } 
            else if ( beamX == RASTER_IRQ_X_COORDINATE && rasterPoint == beamY ) 
            { 
                    triggerRasterIRQ(cpu);
            }
            
            // check if we're inside border/vblank/hblank area
            if ( beamY < FIRST_DISPLAY_AREA_Y ||
                    beamY > LAST_DISPLAY_AREA_Y ||
                    beamX < FIRST_DISPLAY_AREA_X ||
                    beamX > LAST_DISPLAY_AREA_X)
            {
                pixelData[ imagePixelPtr++ ]  = borderColor;
            }
            else
            {
                pixelData[ imagePixelPtr++ ]  = pixelSequencer.getRGBColor();
            }

            // advance raster beam
            if ( ++beamX >= 504 )
            {
                // start of new raster line
                beamX = 0;
                if ( ++beamY >= 312 )
                {
                    // start of new frame
                    beamY = 0;
                    pixelData = swapBuffers();
                    imagePixelPtr = 0;
                }
            }
        }
        this.beamX = beamX;
        this.beamY = beamY;
        this.imagePixelPtr = imagePixelPtr;
    }

    private void triggerRasterIRQ(CPU cpu)
    {
        final int oldValue = triggeredInterruptFlags;
        this.triggeredInterruptFlags = (oldValue| IRQ_RASTER |1<<7);
        if ( (oldValue & IRQ_RASTER) == 0 ) // only trigger interrupt if IRQ is not already active
        {
            cpu.queueInterrupt( IRQType.REGULAR  );
        }
    }

    /**
     * CIA #2 , $DD00
     * Bit 0..1: Select the position of the VIC-memory
     * %00, 0: Bank 3: $C000-$FFFF, 49152-65535
     * %01, 1: Bank 2: $8000-$BFFF, 32768-49151
     * %10, 2: Bank 1: $4000-$7FFF, 16384-32767
     * %11, 3: Bank 0: $0000-$3FFF, 0-16383 (standard)
     *
     * Bank no. Bit pattern in 56576/$DD00        Character ROM available?
     * 0 xxxxxx00 49152–65535 $C000–$FFFF   No
     * 1 xxxxxx01 32768–49151 $8000–$BFFF   Yes, at 36864–40959 $9000–$9FFF
     * 2 xxxxxx10 16384–32767 $4000–$7FFF   No
     * 3 xxxxxx11 0–16383 $0000–$3FFF       Yes, at 4096–8192 $1000–$1FFF
     *
     * @param bankNo Bank no. (bits 0..1) from CIA2 PRA register
     */
    public void setCurrentBankNo(int bankNo)
    {
        switch( bankNo ) {
            case 0:
                bankAdr = 0xc000;
                break;
            case 1:
                bankAdr = 0x8000;
                break;
            case 2:
                bankAdr = 0x4000;
                break;
            case 3:
                bankAdr = 0x0000;
                break;
            default:
                throw new RuntimeException("Unreachable code reached");
        }
        updateMemoryMapping();
    }

    private void updateMemoryMapping() {
        /*
         * VIC: mem_mapping is %0000_100x
         *
         * Bit 7..4: Basisadresse des Bildschirmspeichers in aktueller 16K-Bank des VIC = 64*(Bit 7…4)[2]
         * Bit 3..1: Basisadresse des Zeichensatzes = 1024*(Bit 3…1).
         *
         * Bit 7..4: Basisadresse des Bildschirmspeichers in aktueller 16K-Bank des VIC = 64*(Bit 7…4)[2]
         * Bit 3..1: Basisadresse des Zeichensatzes = 1024*(Bit 3…1).
         *
         *  ( ) _oder_ im Bitmapmodus
         *  ( )
         *  ( )                       Bit 3: Basisadresse der Bitmap = 1024*8 (Bit 3)
         *  ( )                       (8kB-Schritte in VIC-Bank)
         *  ( )                       Bit 0 ist immer 1: nur 2 kB Schritte in VIC-Bank[3]
         *  ( )
         *  ( )                       Beachte: Das Character-ROM wird nur in VIC-Bank 0 und 2 ab 4096 eingeblendet
         */
        if ( DEBUG_MEMORY_LAYOUT ) {
            System.out.println("VIC: mem_mapping is "+HexDump.toBinaryString( (byte) memoryMapping ) );
        }
        final int videoRAMOffset = 1024 * ( ( memoryMapping & 0b1111_0000) >>> 4 ); // The four most significant bits form a 4-bit number in the range 0 thru 15: Multiplied with 1024 this gives the start address for the screen character RAM.
        final int glyphRAMOffset = 2048 * ( ( memoryMapping & 0b0000_1110) >>> 1); // Bits 1 thru 3 (weights 2 thru 8) form a 3-bit number in the range 0 thru 7: Multiplied with 2048 this gives the start address for the character set.

        bitmapRAMAdr = bankAdr + ( (memoryMapping & 0b1000) == 0 ? 0 : 0x2000 );
        videoRAMAdr = bankAdr + videoRAMOffset;
        charROMAdr  = bankAdr + glyphRAMOffset;

        // char ROM is only available in banks #1 (start: 0x8000) and #3 (0x0000)
        switch( bankAdr )
        {
            case 0x0000:
                builtinCharRomStart = 0x1000;
                charROMHidden  = false;
                break;
            case 0x8000:
                builtinCharRomStart = 0x9000;
                charROMHidden  = false;
                break;
            default:
                charROMHidden  = true;
        }
        builtinCharRomEnd = builtinCharRomStart+4096;

        if ( DEBUG_MEMORY_LAYOUT ) {
            System.out.println("VIC: Bank @ "+HexDump.toAdr( bankAdr )+"");
            System.out.println("VIC: Video RAM @ "+HexDump.toAdr( videoRAMAdr ) );
            System.out.println("VIC: Bitmap RAM @ "+HexDump.toAdr( bitmapRAMAdr ) );
            System.out.println("VIC: Character ROM available ? "+( ! charROMHidden ? "yes" :"no"));
            System.out.println("VIC: Char ROM @ "+HexDump.toAdr( charROMAdr )  );
        }
    }

    protected boolean isExtendedColorMode() {
        return ( vicCtrl1 & 1<< 6) != 0;
    }

    protected boolean isBitmapMode() {
        return ( vicCtrl1 & 1<< 5) != 0;
    }

    protected boolean isTextMode() {
        return ! isBitmapMode();
    }

    protected boolean isMultiColor() {
        return ( vicCtrl2 & 1<< 4) != 0;
    }

    @Override
    public void reset()
    {
        /* $D011  53265  17  Steuerregister, Einzelbedeutung der Bits (1 = an):
         *                       Bit 7: Bit 8 von $D012
         *                       Bit 6: Extended Color Modus
         *                       Bit 5: Bitmapmodus
         *                       Bit 4: Bildausgabe eingeschaltet (Effekt erst beim nächsten Einzelbild)
         *                       Bit 3: 25 Zeilen (sonst 24)
         *                       Bit 2..0: Offset in Rasterzeilen vom oberen Bildschirmrand
         */
        vicCtrl1 = 1<<4 | 1<<3; // Display enabled + 25 rows

        /* $D016  53270  22  Steuerregister, Einzelbedeutung der Bits (1 = an):
         *                       Bit 7..5: unbenutzt
         *                       Bit 4: Multicolor-Modus
         *                       Bit 3: 40 Spalten (an)/38 Spalten (aus)
         *                       Bit 2..0: Offset in Pixeln vom linken Bildschirmrand
         */
        vicCtrl2 = 1<<3; // 40 columns

        triggeredInterruptFlags = 0; // no interrupts triggered
        enabledInterruptFlags = 0; // all IRQs disabled

        rasterIRQLine=0;

        imagePixelPtr = 0;

        beamX = 0;
        beamY = 0;

        backgroundColor = 0;
        rgbBackgroundColor = RGB_COLORS[ backgroundColor ];

        backgroundExt0Color = 0;
        rgbBackgroundExt0Color = RGB_COLORS[backgroundExt0Color];

        backgroundExt1Color = 0;
        rgbBackgroundExt1Color = RGB_COLORS[backgroundExt1Color];

        backgroundExt2Color = 0;
        rgbBackgroundExt2Color = RGB_COLORS[backgroundExt2Color];

        borderColor = 0;
        rgbBorderColor = RGB_COLORS[ borderColor ];

        setCurrentBankNo( 0 );
    }

    private int[] swapBuffers()
    {
        synchronized( frontBuffer )
        {
            if ( DEBUG_RASTER_IRQ && isRasterIRQEnabled() )
            {
                final Graphics2D gfx = backBufferGfx;
                gfx.setColor(Color.RED);
                gfx.drawLine( 0 , rasterIRQLine , DISPLAY_WIDTH, rasterIRQLine );
            }

            if ( DEBUG_FPS )
            {
                final Graphics2D gfx = backBufferGfx;
                final long now = System.currentTimeMillis();
                if ( previousFrameTimestamp != 0 )
                {
                    long deltaMs = now - previousFrameTimestamp;
                    frameCounter++;
                    totalFrameTime += deltaMs;

                    if ( ( frameCounter % 50) == 0 )
                    {
                        fps = 1000f/ ((float) totalFrameTime / (float) frameCounter);
                        frameCounter = 0;
                        totalFrameTime = 0;
                    }
                }
                previousFrameTimestamp = now;

                gfx.setColor( Color.RED );
                gfx.drawString( "FPS: "+(int) fps , 15 ,15 );
            }
            
            final BufferedImage tmp = frontBuffer;
            final Graphics2D tmpGfx = frontBufferGfx;

            frontBuffer= backBuffer;
            frontBufferGfx = backBufferGfx;

            backBuffer = tmp;
            backBufferGfx = tmpGfx;

            final int[] result = ((DataBufferInt) backBuffer.getRaster().getDataBuffer()).getData();
            imagePixelData = result;
            return result;
        }
    }

    public void render(Graphics2D graphics,int width,int height)
    {
        synchronized( frontBuffer )
        {
            graphics.drawImage( frontBuffer , 0 , 0 , width, height , null );
        }
    }

    @Override
    public boolean isReadsReturnWrites(int offset) {
        return false; // not for all registers
    }

    @Override
    public int readByte(int offset) {
        return readByteNoSideEffects(offset);
    }

    @Override
    public int readByteNoSideEffects(int offset)
    {
        final int trimmed = ( offset & 0xffff);
        breakpointsContainer.read( trimmed );
        switch( trimmed )
        {
            case VIC_BACKGROUND0_EXT_COLOR:
                return backgroundExt0Color;
            case VIC_BACKGROUND1_EXT_COLOR:
                return backgroundExt1Color;
            case VIC_BACKGROUND2_EXT_COLOR:
                return backgroundExt2Color;
            case VIC_IRQ_ACTIVE_BITS:
                return triggeredInterruptFlags;
            case VIC_IRQ_ENABLE_BITS:
                return enabledInterruptFlags;
            case VIC_BORDER_COLOR:
                return borderColor;
            case VIC_BACKGROUND_COLOR:
                return backgroundColor;
            case VIC_CTRL1:
                return vicCtrl1 | ((beamY & 1<<8 ) >>> 1);
            case VIC_CTRL2:
                return vicCtrl2;
            case VIC_SCANLINE:
                return beamY & 0xff; // bit 8 is stored in bit 7 of VIC_CTRL1 ($d011)
            case VIC_MEMORY_MAPPING:
                return memoryMapping;
            default:
//                System.err.println("Unhandled read from "+HexDump.toAdr( getAddressRange().getStartAddress() + offset ) );
                // TODO: Throw exception here instead of returning 0
                return 0;
        }
    }

    @Override
    public void writeByte(int offset, byte value)
    {
        final int trimmed = ( offset & 0xffff);
        breakpointsContainer.write( trimmed );
        switch( trimmed )
        {
            case VIC_BACKGROUND0_EXT_COLOR:
                backgroundExt0Color = value & 0b1111;
                rgbBackgroundExt0Color = RGB_COLORS[ backgroundExt0Color ];
                break;
            case VIC_BACKGROUND1_EXT_COLOR:
                backgroundExt1Color = value & 0b1111;
                rgbBackgroundExt1Color = RGB_COLORS[ backgroundExt1Color ];
                break;
            case VIC_BACKGROUND2_EXT_COLOR:
                backgroundExt2Color = value & 0b1111;
                rgbBackgroundExt2Color = RGB_COLORS[ backgroundExt2Color ];
                break;
            case VIC_MEMORY_MAPPING:
                this.memoryMapping = value & 0xff;
                updateMemoryMapping();
                break;
            case VIC_IRQ_ACTIVE_BITS:
                /*
                 * $D019  53273  25  Interrupt Request, Bit = 1 = an
                 *                  Lesen:
                 *                  Bit 7: IRQ durch VIC ausgelöst
                 *                  Bit 6..4: unbenutzt
                 *                  Bit 3: Anforderung durch Lightpen
                 *                  Bit 2: Anforderung durch Sprite-Sprite-Kollision (Reg. $D01E)
                 *                  Bit 1: Anforderung durch Sprite-Hintergrund-Kollision (Reg. $D01F)
                 *                  Bit 0: Anforderung durch Rasterstrahl (Reg. $D012)
                 *                  Schreiben:
                 *                  1 in jeweiliges Bit schreiben = zugehöriges Interrupt-Flag löschen
                 */
                int mask = ~(value & 0xff);
                mask |= 1<<7;
                final int newValue = this.triggeredInterruptFlags & mask;
                if ( ( newValue & 0b0000_1111) == 0 ) { // clear bit 7 if all interrupts are cleared
                    this.triggeredInterruptFlags = newValue & 0b0111_1111;
                } else {
                    this.triggeredInterruptFlags = newValue;
                }
                break;
            case VIC_IRQ_ENABLE_BITS:
                this.enabledInterruptFlags = value;
                break;
            case VIC_CTRL1:
                if ( DEBUG_GRAPHIC_MODES )
                {
                    if ( ( vicCtrl1 & 0b0110_0000) != (value & 0b0110_0000) )
                    {
                        final GraphicsMode oldMode = new GraphicsMode( isBitmapMode() , isExtendedColorMode() , isMultiColor() );
                        this.vicCtrl1 = value & 0b0111_1111; // bit 7 is actually bit 8 of raster IRQ line
                        final GraphicsMode newMode = new GraphicsMode( isBitmapMode() , isExtendedColorMode() , isMultiColor() );
                        System.err.println("VIC_CTRL1 mode changed: "+oldMode+" -> "+newMode);
                    }
                }
                this.vicCtrl1 = value & 0b0111_1111; // bit 7 is actually bit 8 of raster IRQ line
                if ( (value & 1<<7) != 0 ) {
                    rasterIRQLine |= 1<<8;
                } else {
                    rasterIRQLine &= ~(1<<8);
                }
                break;
            case VIC_CTRL2:
                if ( DEBUG_GRAPHIC_MODES )
                {
                    if ( (vicCtrl2 & 1<<4)  != (value & 1<<4) )
                    {
                        final GraphicsMode oldMode = new GraphicsMode( isBitmapMode() , isExtendedColorMode() , isMultiColor() );
                        this.vicCtrl2 = value & 0xff;
                        final GraphicsMode newMode = new GraphicsMode( isBitmapMode() , isExtendedColorMode() , isMultiColor() );
                        System.err.println("VIC_CTRL2 mode changed: "+oldMode+" -> "+newMode);
                    }
                }
                this.vicCtrl2 = value & 0xff;
                break;
            case VIC_BORDER_COLOR:
                this.borderColor = value & 0b1111;
                this.rgbBorderColor = RGB_COLORS[ value & 0b1111 ];
                break;
            case VIC_BACKGROUND_COLOR:
                this.backgroundColor = value & 0b1111;
                this.rgbBackgroundColor = RGB_COLORS[ value & 0b1111 ];
                break;
            case VIC_SCANLINE:
                /*
                 * $D012  53266  18  Lesen    : Aktuelle Rasterzeile
                 *                   Schreiben: Rasterzeile, bei der ein IRQ ausgelöst wird (zugehöriges Bit 8 in $D011)
                 */
                rasterIRQLine &= ~0xff;
                rasterIRQLine |= (value & 0xff);
                break;
            default:
                // TODO: Throw exception on write to unmapped register
                // throw new RuntimeException("Unhandled write to "+HexDump.toAdr( getAddressRange().getStartAddress() + offset ) );
        }
    }

    @Override
    public void bulkWrite(int startingAddress, byte[] data, int datapos,int len) {
        throw new UnsupportedOperationException("bulkWrite() not implemented");
    }

    @Override
    public int readWord(int offset) {
        throw new UnsupportedOperationException("readWord() not implemented");
    }

    @Override
    public void writeWord(int offset, short value) {
        throw new UnsupportedOperationException("writeWord() not implemented");
    }

    @Override
    public String dump(int offset, int len) {
        return HexDump.INSTANCE.dump( (short) (getAddressRange().getStartAddress()+offset),this,offset,len);
    }

    private boolean isRasterIRQEnabled() {
        return (enabledInterruptFlags & IRQ_RASTER) != 0;
    }
}