package de.codesourcery.j6502.emulator;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;

import de.codesourcery.j6502.emulator.CPU.IRQType;
import de.codesourcery.j6502.utils.HexDump;

public class VIC extends SlowMemory
{
    /* VIC I/O area is fixed at $D000 - $DFFF.
     *
     * VIC $d000 - $d02f
     * ---
     *
     * Adresse (hex)  Adresse (dez)  Register  Inhalt
     *  (s) $D000  53248  0  X-Koordinate für Sprite 0 (0..255)
     *  (s) $D001  53249  1  Y-Koordinate für Sprite 0 (0..255)
     *  (s) $D002  53250  2  X-Koordinate für Sprite 1 (0..255)
     *  (s) $D003  53251  3  Y-Koordinate für Sprite 1 (0..255)
     *  (s) $D004  53252  4  X-Koordinate für Sprite 2 (0..255)
     *  (s) $D005  53253  5  Y-Koordinate für Sprite 2 (0..255)
     *  (s) $D006  53254  6  X-Koordinate für Sprite 3 (0..255)
     *  (s) $D007  53255  7  Y-Koordinate für Sprite 3 (0..255)
     *  (s) $D008  53256  8  X-Koordinate für Sprite 4 (0..255)
     *  (s) $D009  53257  9  Y-Koordinate für Sprite 4 (0..255)
     *  (s) $D00A  53258  10  X-Koordinate für Sprite 5 (0..255)
     *  (s) $D00B  53259  11  Y-Koordinate für Sprite 5 (0..255)
     *  (s) $D00C  53260  12  X-Koordinate für Sprite 6 (0..255)
     *  (s) $D00D  53261  13  Y-Koordinate für Sprite 6 (0..255)
     *  (s) $D00E  53262  14  X-Koordinate für Sprite 7 (0..255)
     *  (s) $D00F  53263  15  Y-Koordinate für Sprite 7 (0..255)
     *  (s) $D010  53264  16  Bit 8 für die obigen X-Koordinaten (0..255) , jedes Bit steht für eins der Sprites 0..7
     *  ( )                       The least significant bit corresponds to sprite #0, and the most sigificant bit to sprite #7.
     *  ( ) $D011  53265  17  Steuerregister, Einzelbedeutung der Bits (1 = an):
     *  ( )                       Bit 7: Bit 8 von $D012
     *  ( )                       Bit 6: Extended Color Modus
     *  ( )                       Bit 5: Bitmapmodus
     *  ( )                       Bit 4: Bildausgabe eingeschaltet (Effekt erst beim nächsten Einzelbild)
     *  ( )                       Bit 3: 25 Zeilen (sonst 24)
     *  ( )                       Bit 2..0: Offset in Rasterzeilen vom oberen Bildschirmrand
     *  ( )
     *  ( ) $D012  53266  18  Lesen: Aktuelle Rasterzeile
     *  ( )                       Schreiben: Rasterzeile, bei der ein IRQ ausgelöst wird (zugehöriges Bit 8 in $D011)
     *  ( ) $D013  53267  19  Lightpen X-Koordinate (assoziiert mit Pin LP am VIC)
     *  ( ) $D014  53268  20  Lightpen Y-Koordinate
     *  (s) $D015  53269  21  Spriteschalter, Bit = 1: Sprite n an (0..255)
     *  ( ) $D016  53270  22  Steuerregister, Einzelbedeutung der Bits (1 = an):
     *  ( )                       Bit 7..5: unbenutzt
     *  ( )                       Bit 4: Multicolor-Modus
     *  ( )                       Bit 3: 40 Spalten (an)/38 Spalten (aus)
     *  ( )                       Bit 2..0: Offset in Pixeln vom linken Bildschirmrand
     *  ( )
     *  (s) $D017  53271  23  Spriteschalter, Bit = 1: Sprite n doppelt hoch (0..255)
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
     *  ( ) $D01A  53274  26  Interrupt Request: Maske, Bit = 1 = an
     *  ( )                       Ist das entsprechende Bit hier und in $D019 gesetzt, wird ein IRQ ausgelöst und Bit 7 in $D019 gesetzt
     *  ( )                       Bit 7..4: unbenutzt
     *  ( )                       Bit 3: IRQ wird durch Lightpen ausgelöst
     *  ( )                       Bit 2: IRQ wird durch S-S-Kollision ausgelöst
     *  ( )                       Bit 1: IRQ wird durch S-H-Kollision ausgelöst
     *  ( )                       Bit 0: IRQ wird durch Rasterstrahl ausgelöst
     *  (s) $D01B  53275  27  Priorität Sprite-Hintergrund, Bit = 1: Hintergrund liegt vor Sprite n (0..255)
     *  (s) $D01C  53276  28  Sprite-Darstellungsmodus, Bit = 1: Sprite n ist Multicolor
     *  (s) $D01D  53277  29  Spriteschalter, Bit = 1: Sprite n doppelt breit (0..255)
     *  (S) $D01E  53278  30  Sprite-Info: Bits = 1: Sprites miteinander kollidiert (0..255)
     *  ( )                       Wird durch Zugriff gelöscht
     *  (S) $D01F  53279  31  Sprite-Info: Bits = 1: Sprite n mit Hintergrund kollidiert (0..255)
     *  ( )                       Wird durch Zugriff gelöscht
     *  ( ) $D020  53280  32  Farbe des Bildschirmrands (0..15)
     *  ( ) $D021  53281  33  Bildschirmhintergrundfarbe (0..15)
     *  ( ) $D022  53282  34  Bildschirmhintergrundfarbe 1 bei Extended Color Modus (0..15)
     *  ( ) $D023  53283  35  Bildschirmhintergrundfarbe 2 bei Extended Color Mode (0..15)
     *  ( ) $D024  53284  36  Bildschirmhintergrundfarbe 3 bei Extended Color Mode (0..15)
     *  ( )
     *  (S) $D025  53285  37  gemeinsame Spritefarbe 0 im Sprite-Multicolormodus, Bitkombination %01 (0..15)
     *  (S) $D026  53286  38  gemeinsame Spritefarbe 1 im Sprite-Multicolormodus, Bitkombination %11 (0..15)
     *  ( )
     *  (S) $D027  53287  39  Farbe Sprite 0, Bitkombination %10 (0..15)
     *  (S) $D028  53288  40  Farbe Sprite 1, Bitkombination %10 (0..15)
     *  (S) $D029  53289  41  Farbe Sprite 2, Bitkombination %10 (0..15)
     *  (S) $D02A  53290  42  Farbe Sprite 3, Bitkombination %10 (0..15)
     *  (S) $D02B  53291  43  Farbe Sprite 4, Bitkombination %10 (0..15)
     *  (S) $D02C  53292  44  Farbe Sprite 5, Bitkombination %10 (0..15)
     *  (S) $D02D  53293  45  Farbe Sprite 6, Bitkombination %10 (0..15)
     *  (S) $D02E  53294  46  Farbe Sprite 7, Bitkombination %10 (0..15)
     *  (S) $D02F  53295  47  nur VIC IIe (C128)
     *  ( )                       Bit 7..3: unbenutzt
     *  ( )                       Bit 2..0: Status der 3 zusätzlichen Tastaturpins
     *  ( )                       $D030  53296  48  nur VIC IIe (C128)
     *  ( )
     *  ( )                       Bit 7..2: unbenutzt
     *  ( )                       Bit 1: Testmode
     *  ( )                       Bit 0: 0 => Slow-Mode (1 MHz), 1 => Fast-Mode (2 MHz)
     */

    public static final boolean DEBUG_RASTER_IRQ = true;
    protected static final boolean DEBUG_MEMORY_LAYOUT = true;
    protected static final boolean DEBUG_SET_GRAPHICS_MODE = true;

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

    protected static final int DISPLAY_WIDTH= 504;
    protected static final int DISPLAY_HEIGHT = 312;

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

    private static final GraphicsMode[] GRAPHIC_MODES =
        {
                GraphicsMode.MODE_0,GraphicsMode.MODE_1,
                GraphicsMode.MODE_2,GraphicsMode.MODE_3,
                GraphicsMode.MODE_4,GraphicsMode.MODE_5,
                GraphicsMode.MODE_6,GraphicsMode.MODE_7
        };

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

    /**
     * Wrapper to return a text-mode pixel color and the fact whether
     * this color should be considered to be 'foreground'
     * or 'background' with regards to sprite display priority.
     *
     * @author tobias.gierke@code-sourcery.de
     */
    protected static final class ColorAndKind
    {
        public int rgbColor;
        public boolean hasForegroundColor;

        public void foreground(int rgbColor) {
            this.rgbColor = rgbColor;
            this.hasForegroundColor = true;
        }

        public void background(int rgbColor) {
            this.rgbColor = rgbColor;
            this.hasForegroundColor = false;
        }
    }

    /**
     * VIC graphics mode.
     *
     * The VIC is capable of 8 different graphics modes that
     * are selected by the bits
     *
     * ECM (Extended Color Mode)
     * BMM (Bit Map Mode)
     * MCM (Multi Color Mode)
     *
     * in the registers $d011 and $d016.
     *
     * Of the 8 possible bit combinations, 3 are "invalid" and generate the
     * same output, the color black.
     *
     * $D011  53265  17  Steuerregister, Einzelbedeutung der Bits (1 = an):
     *                      Bit 7: Bit 8 von $D012
     *                      Bit 6: Extended Color Modus
     *                      Bit 5: Bitmapmodus
     *                      Bit 4: Bildausgabe eingeschaltet (Effekt erst beim nächsten Einzelbild)
     *                      Bit 3: 25 Zeilen (sonst 24)
     *                      Bit 2..0: Offset in Rasterzeilen vom oberen Bildschirmrand
     *
     * $D016  53270  22  Steuerregister, Einzelbedeutung der Bits (1 = an):
     *                      Bit 7..5: unbenutzt
     *                      Bit 4: Multicolor-Modus
     *                      Bit 3: 40 Spalten (an)/38 Spalten (aus)
     *                      Bit 2..0: Offset in Pixeln vom linken Bildschirmrand
     *
     * @author tobias.gierke@code-sourcery.de
     */
    protected static enum GraphicsMode
    {
        MODE_0(false,false,false), // text mode
        MODE_1(false,false,true), // multi-color text mode
        MODE_2(false,true ,false), // hi-res bitmap mode
        MODE_3(false,true ,true),  // low-res bitmap mode
        MODE_4(true ,false,false), // extended color mode
        MODE_5(true ,false,true),
        MODE_6(true ,true ,false),
        MODE_7(true ,true ,true);

        public final boolean extendedColorMode;
        public final boolean bitMapMode;
        public final boolean multiColorMode;

        private GraphicsMode(boolean extendedColorMode, boolean bitMapMode, boolean multiColorMode)
        {
            this.extendedColorMode = extendedColorMode;
            this.bitMapMode = bitMapMode;
            this.multiColorMode = multiColorMode;
        }

        @Override
        public String toString() {
            return "extendedColor: "+extendedColorMode+" | bitMap: "+bitMapMode+" | multiColor: "+multiColorMode;
        }
    }

    protected GraphicsMode graphicsMode = GraphicsMode.MODE_0;
    protected final Sprite[] sprites;

    public static final class Sprite
    {
        public boolean isEnabled;

        public int x;
        public int y;

        public final int spriteNo;

        public int color; // this is the C64 color (0...f) and NOT the actual RGB color
        public int rgbColor;

        public boolean multiColor;
        public boolean behindForeground;

        private int spriteWidth=SPRITE_WIDTH;
        private int spriteHeight=SPRITE_HEIGHT;

        public Sprite(int no)  {
            this.spriteNo = no;
            reset();
        }

        public Sprite(Sprite other)
        {
            this.isEnabled = other.isEnabled;
            this.x = other.x;
            this.y = other.y;
            this.spriteNo = other.spriteNo;
            this.color = other.color;
            this.rgbColor = other.rgbColor;
            this.multiColor = other.multiColor;
            this.behindForeground = other.behindForeground;
            this.spriteHeight = other.spriteHeight;
            this.spriteWidth = other.spriteWidth;
        }

        public Sprite createCopy() {
            return new Sprite(this);
        }

        public int getPixelColor(VIC vic,int scrX,int scrY)
        {
            // check whether the current beam position overlaps with this sprite, sprite size is 24x21 pixels
            if ( x <= scrX && scrX < x+spriteWidth &&
                    y <= scrY && scrY < y+spriteHeight )
            {
                final int localX;
                if ( spriteWidth == SPRITE_DOUBLE_WIDTH ) {
                    localX = (scrX - x)/2;
                } else {
                    localX = scrX - x;
                }
                final int localY;
                if ( spriteHeight == SPRITE_DOUBLE_HEIGHT ) {
                    localY = (scrY - y)/2;
                } else {
                    localY = scrY - y;
                }

                /*
    The location of the sprite pointers follow that of the text screen, so that if the VIC-II has been "told" (through address 53272) that the
     text screen RAM begins at address S, the sprite pointers reside at addresses S+1016 thru S+1023.
     Since the default text screen RAM address is 1024, this puts the sprite pointers at addresses 2040 (for sprite #0)
     thru 2047 (for sprite #7), or $07F8–07FF in hexadecimal.
    To make a given sprite show the pattern that's stored in RAM at an address A (which must be divisible with 64), set the contents
    of the corresponding sprite pointer address to A divided by 64. For instance, if the sprite pattern begins at address 704, the pointer value will be 704 / 64 = 11.
                 */
                final int dataPtrLocation = vic.videoRAMAdr+1016+spriteNo;
                final int dataAdr = vic.bankAdr + 64 * vic.mainMemory.readByte( dataPtrLocation );

                final int byteOffset = localY*3 + localX / 8; // y*24 bits
                final int colorBits = vic.mainMemory.readByte( dataAdr+byteOffset );

                if ( multiColor )
                {
                    final int bitOffset = 3-((localX/2)%4);
                    final int mask = 0b11 << 2*bitOffset;

                    /*
                     * Bit-Paar  Farbe
                     *
                     * "00": Transparent                     | MxMC = 1
                     * "01": Sprite multicolor 0 ($d025)     |
                     * "10": Sprite color ($d027-$d02e)      |
                     * "11": Sprite multicolor 1 ($d026)
                     */
                    switch( (colorBits & mask) >> 2*bitOffset )
                    {
                        case 0: // %00
                            return SPRITE_TRANSPARENT_COLOR;
                        case 1: // %01
                            return RGB_COLORS[ vic.readByte( VIC_SPRITE_COLOR01_MULTICOLOR_MODE ) & 0b1111 ];
                        case 2: // %10
                            return rgbColor;
                        case 3: // %11
                            return RGB_COLORS[ vic.readByte( VIC_SPRITE_COLOR11_MULTICOLOR_MODE ) & 0b1111 ];
                    }
                    throw new RuntimeException("Unreachable code reached");
                }

                final int bitInByte = 7-localX%8;

                if ( (colorBits & (1<<bitInByte) ) != 0 )
                {
                    return rgbColor;
                }
                return SPRITE_TRANSPARENT_COLOR;
            }
            // there's no pixel of this sprite at the current location
            return SPRITE_TRANSPARENT_COLOR;
        }

        public void reset() {
            x = 0;
            y = 0;
            isEnabled = false;
            color = 1;
            rgbColor = RGB_COLORS[color];

            multiColor = false;
            behindForeground = false;

            spriteWidth=SPRITE_WIDTH;
            spriteHeight=SPRITE_HEIGHT;
        }

        @Override
        public String toString() {
            return "Sprite #"+spriteNo+" [enabled:" + isEnabled + ", x:" + x + ", y:" + y
                    +", multiColor=" + multiColor
                    + ", width=" + spriteWidth + ", height="+ spriteHeight
                    + ", behindBackground:" + behindForeground
                    + ", color:" + color +
                    ", rgbColor:"+ rgbColor +"]";
        }
    }

    // @GuardedBy( frontBuffer )
    private BufferedImage frontBuffer;

    // @GuardedBy( frontBuffer )
    private BufferedImage backBuffer;

    public int bankAdr;
    public int videoRAMAdr;
    public int bitmapRAMAdr;
    protected int charROMAdr;

    private int textColumnCount=40;
    private int textRowCount=25;

    private final MemorySubsystem mainMemory;

    protected int leftBorder;
    protected int rightBorder;
    protected int topBorder;
    protected int bottomBorder;

    protected int backgroundColor;
    protected int borderColor;

    private boolean rasterIRQEnabled = false;
    private int irqOnRaster = -1;

    private int beamX;
    private int beamY;

    private boolean displayEnabled; // aka 'DEN'

    private int xScroll;
    private int yScroll;

    public VIC(String identifier, AddressRange range,MemorySubsystem mainMemory)
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
        sprites = new Sprite[8];
        for ( int i = 0 ; i < 8 ; i++ )
        {
            sprites[i] = new Sprite(i);
        }
    }

    public void tick(CPU cpu,boolean clockHigh)
    {
        int tmpBeamX = beamX;
        int tmpBeamY = beamY;

        final int[] imagePixelData = ((DataBufferInt) backBuffer.getRaster().getDataBuffer()).getData();

        final int top = topBorder;
        final int left = leftBorder;
        final int bottom = bottomBorder;
        final int right = rightBorder;
        final int borderColor = this.borderColor;

        // VIC renders 8 pixels per cycle => 4 pixels per clock phase
        for ( int i = 0 ; i < 4 ; i++ )
        {
            final int color;
            if ( DEBUG_RASTER_IRQ && rasterIRQEnabled && tmpBeamY == irqOnRaster )
            {
                color = Color.PINK.getRGB();
            }
            else if ( tmpBeamY < top || tmpBeamY >= bottom || tmpBeamX < left || tmpBeamX >= right )
            {
                color = borderColor;
            } else {
                color = getPixelColor(tmpBeamX,tmpBeamY);
            }
            imagePixelData[ tmpBeamY * DISPLAY_WIDTH + tmpBeamX ] = color;

            tmpBeamX++;
            if ( tmpBeamX >= DISPLAY_WIDTH )
            {
                tmpBeamX = 0;
                tmpBeamY++;
                if ( tmpBeamY >= DISPLAY_HEIGHT )
                {
                    tmpBeamY = 0;
                    if ( clockHigh )
                    {
                        swapBuffers();
                    }
                }
                if ( clockHigh && rasterIRQEnabled && irqOnRaster == tmpBeamY )
                {
                    triggerRasterIRQ(cpu);
                }
            }
        }

        beamX = tmpBeamX;
        beamY = tmpBeamY;
    }

    private void triggerRasterIRQ(CPU cpu)
    {
        /*
         *    $D019  53273  25  Interrupt Request, Bit = 1 = an
         *                          Lesen:
         *                          Bit 7: IRQ durch VIC ausgelöst
         *                          Bit 6..4: unbenutzt
         *                          Bit 3: Anforderung durch Lightpen
         *                          Bit 2: Anforderung durch Sprite-Sprite-Kollision (Reg. $D01E)
         *                          Bit 1: Anforderung durch Sprite-Hintergrund-Kollision (Reg. $D01F)
         *                          Bit 0: Anforderung durch Rasterstrahl (Reg. $D012)
         *                          Schreiben:
         *                          1 in jeweiliges Bit schreiben = zugehöriges Interrupt-Flag löschen
         */
        final int oldValue = super.readByte( VIC_IRQ_ACTIVE_BITS );
        super.writeByte( VIC_IRQ_ACTIVE_BITS , (byte) (oldValue|1<<0|1<<7) );
        if ( (oldValue & 1<<0) == 0 ) // only trigger interrupt if IRQ is not already active
        {            
            cpu.queueInterrupt( IRQType.REGULAR  );
        }
    }

    @Override
    public int readAndWriteByte(int offset)
    {
    	final int result = readByte(offset);
    	if ( (offset & 0xffff) == VIC_IRQ_ACTIVE_BITS ) {
    		writeByte( offset , (byte) result );
    	}
    	return result;
    }

    @Override
    public int readByte(int offset)
    {
        int result = super.readByte(offset);

        switch( offset )
        {
            case VIC_CTRL1: // $d011 , bit 7 is bit 8 of beamY
                int value = super.readByte( VIC_CTRL1 );
                if ( (beamY & 1<<8) != 0 ) {
                    return value | 0b1000_0000;
                }
                return value & 0b0111_1111;
            case VIC_SCANLINE:
                return (beamY & 0xff);

            case VIC_SPRITE0_X_COORD: return (byte) sprites[0].x;
            case VIC_SPRITE0_Y_COORD: return (byte) sprites[0].y;
            case VIC_SPRITE1_X_COORD: return (byte) sprites[1].x;
            case VIC_SPRITE1_Y_COORD: return (byte) sprites[1].y;
            case VIC_SPRITE2_X_COORD: return (byte) sprites[2].x;
            case VIC_SPRITE2_Y_COORD: return (byte) sprites[2].y;
            case VIC_SPRITE3_X_COORD: return (byte) sprites[3].x;
            case VIC_SPRITE3_Y_COORD: return (byte) sprites[3].y;
            case VIC_SPRITE4_X_COORD: return (byte) sprites[4].x;
            case VIC_SPRITE4_Y_COORD: return (byte) sprites[4].y;
            case VIC_SPRITE5_X_COORD: return (byte) sprites[5].x;
            case VIC_SPRITE5_Y_COORD: return (byte) sprites[5].y;
            case VIC_SPRITE6_X_COORD: return (byte) sprites[6].x;
            case VIC_SPRITE6_Y_COORD: return (byte) sprites[6].y;
            case VIC_SPRITE7_X_COORD: return (byte) sprites[7].x;
            case VIC_SPRITE7_Y_COORD: return (byte) sprites[7].y;
            case VIC_SPRITE_X_COORDS_HI_BIT:
            {
                // The least significant bit corresponds to sprite #0, and the most sigificant bit to sprite #7.
                int hibits = 0;
                for ( int i = 0 ; i < 8 ; i++ )
                {
                    hibits = hibits >> 1;
                if ( (sprites[i].x & 0b1_0000_0000) != 0 ) {
                    hibits |= 1<<7;
                }
                }
                return hibits;
            }
            case VIC_SPRITE_ENABLE:
            {
                //  The least significant bit refers to sprite #0, and the most sigificant bit to sprite #7.
                int enabledBits = 0;
                for ( int i = 0 ; i < 8 ; i++ )
                {
                    enabledBits = enabledBits >> 1;
                if ( sprites[i].isEnabled ) {
                    enabledBits |= 1<<7;
                }
                }
                return enabledBits;
            }
            case VIC_SPRITE_DOUBLE_HEIGHT:
            {
                //  The least significant bit refers to sprite #0, and the most sigificant bit to sprite #7.
                int heightBits = 0;
                for ( int i = 0 ; i < 8 ; i++ )
                {
                    heightBits = heightBits >> 1;
                if ( sprites[i].spriteHeight == SPRITE_DOUBLE_HEIGHT ) {
                    heightBits |= 1<<7;
                }
                }
                return heightBits;
            }
            case VIC_SPRITE_DOUBLE_WIDTH:
            {
                //  The least significant bit refers to sprite #0, and the most sigificant bit to sprite #7.
                int widthBits = 0;
                for ( int i = 0 ; i < 8 ; i++ )
                {
                    widthBits = widthBits >> 1;
                if ( sprites[i].spriteWidth == SPRITE_DOUBLE_WIDTH ) {
                    widthBits |= 1<<7;
                }
                }
                return widthBits;
            }
            case VIC_SPRITE_PRIORITY:
            {
                //   *    $D01B  53275  27  Priorität Sprite-Hintergrund, Bit = 1: Hintergrund liegt vor Sprite n (0..255)
                //  The least significant bit refers to sprite #0, and the most sigificant bit to sprite #7.
                int bgBits = 0;
                for ( int i = 0 ; i < 8 ; i++ )
                {
                    bgBits = bgBits >> 1;
                if ( sprites[i].behindForeground ) {
                    bgBits |= 1<<7;
                }
                }
                return bgBits;
            }
            case VIC_SPRITE_MULTICOLOR_MODE:
            {
                //   *    $D01B  53275  27  Priorität Sprite-Hintergrund, Bit = 1: Hintergrund liegt vor Sprite n (0..255)
                //  The least significant bit refers to sprite #0, and the most sigificant bit to sprite #7.
                int mcBits = 0;
                for ( int i = 0 ; i < 8 ; i++ )
                {
                    mcBits = mcBits >> 1;
                if ( sprites[i].multiColor ) {
                    mcBits |= 1<<7;
                }
                }
                return mcBits;
            }
            case VIC_SPRITE0_COLOR10: return sprites[0].color;
            case VIC_SPRITE1_COLOR10: return sprites[1].color;
            case VIC_SPRITE2_COLOR10: return sprites[2].color;
            case VIC_SPRITE3_COLOR10: return sprites[3].color;
            case VIC_SPRITE4_COLOR10: return sprites[4].color;
            case VIC_SPRITE5_COLOR10: return sprites[5].color;
            case VIC_SPRITE6_COLOR10: return sprites[6].color;
            case VIC_SPRITE7_COLOR10: return sprites[7].color;
            default:
                // $$FALL-THROUGH$$
        }
        return result;
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
        // video RAM location
        /*
         *    $D018  53272  24  VIC-Speicherkontrollregister
         *
         *                           vvvvggg
         *                          Bit 7..4: Basisadresse des Bildschirmspeichers in aktueller 16K-Bank des VIC = 64*(Bit 7…4)[2]
         *                          Bit 3..1: Basisadresse des Zeichensatzes = 1024*(Bit 3…1).
         *
         * When in TEXT SCREEN MODE, the VIC-II looks to 53272 for information on where the character set and text screen character RAM is located:
         */
        final int value = super.readByte(VIC_MEMORY_MAPPING);

        if ( DEBUG_MEMORY_LAYOUT ) {
            System.out.println("VIC: mem_mapping is "+HexDump.toBinaryString( (byte) value ) );
        }
        final int videoRAMOffset = 1024 * ( ( value & 0b1111_0000) >> 4 ); // The four most significant bits form a 4-bit number in the range 0 thru 15: Multiplied with 1024 this gives the start address for the screen character RAM.
        final int glyphRAMOffset = 2048 * ( ( value & 0b0000_1110) >> 1); // Bits 1 thru 3 (weights 2 thru 8) form a 3-bit number in the range 0 thru 7: Multiplied with 2048 this gives the start address for the character set.

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

        /*
         *  ( ) _oder_ im Bitmapmodus
         *  ( )
         *  ( )                       Bit 3: Basisadresse der Bitmap = 1024*8 (Bit 3)
         *  ( )                       (8kB-Schritte in VIC-Bank)
         *  ( )                       Bit 0 ist immer 1: nur 2 kB Schritte in VIC-Bank[3]
         *  ( )
         *  ( )                       Beachte: Das Character-ROM wird nur in VIC-Bank 0 und 2 ab 4096 eingeblendet
         */

        bitmapRAMAdr = bankAdr + ( (value & 0b1000) == 0 ? 0 : 0x2000 );
        videoRAMAdr = bankAdr + videoRAMOffset;
        charROMAdr  = bankAdr + glyphRAMOffset;

        if ( DEBUG_MEMORY_LAYOUT ) {
            System.out.println("VIC: Bank #"+bankNo+" ("+HexDump.toAdr( bankAdr )+")");
            System.out.println("VIC: Video RAM @ "+HexDump.toAdr( videoRAMAdr ) );
            System.out.println("VIC: Bitmap RAM @ "+HexDump.toAdr( bitmapRAMAdr ) );
            System.out.println("VIC: Char ROM @ "+HexDump.toAdr( charROMAdr )  );
        }
    }

    @Override
    public void writeByte(int offset, byte value)
    {
        super.writeByte(offset, value);

        switch( offset )
        {
            /*
             *    $D019  53273  25  Interrupt Request, Bit = 1 = an
             *                          Lesen:
             *                          Bit 7: IRQ durch VIC ausgelöst
             *                          Bit 6..4: unbenutzt
             *                          Bit 3: Anforderung durch Lightpen
             *                          Bit 2: Anforderung durch Sprite-Sprite-Kollision (Reg. $D01E)
             *                          Bit 1: Anforderung durch Sprite-Hintergrund-Kollision (Reg. $D01F)
             *                          Bit 0: Anforderung durch Rasterstrahl (Reg. $D012)
             *                          Schreiben:
             *                          1 in jeweiliges Bit schreiben = zugehöriges Interrupt-Flag löschen
             *    $D01A  53274  26  Interrupt Request: Maske, Bit = 1 = an
             *                          Ist das entsprechende Bit hier und in $D019 gesetzt, wird ein IRQ ausgelöst und Bit 7 in $D019 gesetzt
             *                          Bit 7..4: unbenutzt
             *                          Bit 3: IRQ wird durch Lightpen ausgelöst
             *                          Bit 2: IRQ wird durch S-S-Kollision ausgelöst
             *                          Bit 1: IRQ wird durch S-H-Kollision ausgelöst
             *                          Bit 0: IRQ wird durch Rasterstrahl ausgelöst
             */
            case VIC_IRQ_ACTIVE_BITS:
                final int mask = ~value;
                final int oldValue = super.readByte( VIC_IRQ_ACTIVE_BITS );
                final int newValue = oldValue & mask;
                System.out.println("VIC_IRQ_ACTIVE: "+HexDump.toBinaryString( (byte) oldValue )+" => "+HexDump.toBinaryString( (byte) newValue )+" (written: "+HexDump.toBinaryString( (byte) value));
                super.writeByte( VIC_IRQ_ACTIVE_BITS , (byte) newValue );
                break;
            case VIC_IRQ_ENABLE_BITS:
                rasterIRQEnabled = (value & 1<<0) != 0;
                break;
            case VIC_MEMORY_MAPPING:
                setCurrentBankNo( mainMemory.ioArea.cia2.readByte( 0 ) & 0b11); // read bank no. from CIA2 PRA bits 0..1
                break;
            case VIC_SCANLINE:
                // current scan line, lo-byte
                int newValue2 = irqOnRaster & 0b1_0000_0000;
                newValue2 |= (value & 0xff);
                irqOnRaster = newValue2;
                break;
            case VIC_CTRL1:

                /*
                 *    $D011  53265  17  Steuerregister, Einzelbedeutung der Bits (1 = an):
                 *                          Bit 7: Bit 8 von $D012
                 *                          Bit 6: Extended Color Modus
                 *                          Bit 5: Bitmapmodus
                 *                          Bit 4: Bildausgabe eingeschaltet (Effekt erst beim nächsten Einzelbild)
                 *                          Bit 3: 25 Zeilen (sonst 24)
                 *                          Bit 2..0: Offset in Rasterzeilen vom oberen Bildschirmrand
                 */

                if ( ( value & 0b1000_0000 ) != 0 ) {
                    irqOnRaster = ( 1<<8 | (irqOnRaster & 0xff) ); // set hi-bit
                } else {
                    irqOnRaster = (irqOnRaster & 0xff); // clear hi-bit
                }

                yScroll= ( value & 0x111);
                displayEnabled = (value & 0b10000) != 1 ? true : false;
                textRowCount   = (value & 0b01000) != 0 ? 25 : 24; // RSEL

                // update graphics mode
                final boolean extendedColorMode = ( value & 1<<6) != 0;
                final boolean bitMapMode = ( value & 1<<5) != 0;

                if ( extendedColorMode != graphicsMode.extendedColorMode || bitMapMode != graphicsMode.bitMapMode )
                {
                    setGraphicsMode( getGraphicsMode( extendedColorMode , bitMapMode , graphicsMode.multiColorMode ) );
                }

                // update border locations
                updateBorders();
                //    System.out.println("Write to $D011: value="+Integer.toBinaryString( value & 0xff)+" , text columns: "+textColumnCount+" , text rows: "+textRowCount);
                break;
            case VIC_CTRL2:
                /*
                 *    $D016  53270  22  Steuerregister, Einzelbedeutung der Bits (1 = an):
                 *                          Bit 7..5: unbenutzt
                 *                          Bit 4: Multicolor-Modus
                 *                          Bit 3: 40 Spalten (an)/38 Spalten (aus)
                 *                          Bit 2..0: Offset in Pixeln vom linken Bildschirmrand
                 */
                xScroll = value & 0b111;
                textColumnCount = ( value & 0b1000) != 0 ? 40 : 38; // CSEL

                // update graphics mode
                final boolean multiColorMode  = ( value & 1<<4) != 0;
                if ( multiColorMode != graphicsMode.multiColorMode )
                {
                    setGraphicsMode( getGraphicsMode( graphicsMode.extendedColorMode , graphicsMode.bitMapMode , multiColorMode ) );
                }

                // update border locations
                updateBorders();
                //    System.out.println("Write to $D016: value="+Integer.toBinaryString( value & 0xff)+" , text columns: "+textColumnCount+" , text rows: "+textRowCount);
                break;
            case VIC_BORDER_COLOR:
                borderColor = RGB_COLORS[ (value & 0xf) ];
                break;
            case VIC_BACKGROUND_COLOR:
                backgroundColor = RGB_COLORS[ (value & 0xf ) ];
                break;

                // =============== SPRITES ===============

            case VIC_SPRITE0_X_COORD: sprites[0].x=(value & 0xff); return;
            case VIC_SPRITE0_Y_COORD: sprites[0].y=(value & 0xff); return;
            case VIC_SPRITE1_X_COORD: sprites[1].x=(value & 0xff); return;
            case VIC_SPRITE1_Y_COORD: sprites[1].y=(value & 0xff); return;
            case VIC_SPRITE2_X_COORD: sprites[2].x=(value & 0xff); return;
            case VIC_SPRITE2_Y_COORD: sprites[2].y=(value & 0xff); return;
            case VIC_SPRITE3_X_COORD: sprites[3].x=(value & 0xff); return;
            case VIC_SPRITE3_Y_COORD: sprites[3].y=(value & 0xff); return;
            case VIC_SPRITE4_X_COORD: sprites[4].x=(value & 0xff); return;
            case VIC_SPRITE4_Y_COORD: sprites[4].y=(value & 0xff); return;
            case VIC_SPRITE5_X_COORD: sprites[5].x=(value & 0xff); return;
            case VIC_SPRITE5_Y_COORD: sprites[5].y=(value & 0xff); return;
            case VIC_SPRITE6_X_COORD: sprites[6].x=(value & 0xff); return;
            case VIC_SPRITE6_Y_COORD: sprites[6].y=(value & 0xff); return;
            case VIC_SPRITE7_X_COORD: sprites[7].x=(value & 0xff); return;
            case VIC_SPRITE7_Y_COORD: sprites[7].y=(value & 0xff); return;
            case VIC_SPRITE_X_COORDS_HI_BIT:
            {
                // The least significant bit corresponds to sprite #0, and the most sigificant bit to sprite #7.
                int hibits = value;
                for ( int i = 0 ; i < 8 ; i++ )
                {
                    if ( (hibits & 1 ) != 0 ) {
                        sprites[i].x |= 1<<8;
                    } else {
                        sprites[i].x &= ~(1<<8);
                    }
                    hibits = hibits >> 1;
                }
                return;
            }
            case VIC_SPRITE_ENABLE:
            {
                //  The least significant bit refers to sprite #0, and the most sigificant bit to sprite #7.
                int hibits = value;
                for ( int i = 0 ; i < 8 ; i++ )
                {
                    sprites[i].isEnabled = (hibits & 1 ) != 0;
                    hibits = hibits >> 1;
                }
                return;
            }
            case VIC_SPRITE_DOUBLE_HEIGHT:
            {
                //  The least significant bit refers to sprite #0, and the most sigificant bit to sprite #7.
                int hibits = value;
                for ( int i = 0 ; i < 8 ; i++ )
                {
                    if ( (hibits & 1 ) != 0 ) {
                        sprites[i].spriteHeight = SPRITE_DOUBLE_HEIGHT;
                    } else {
                        sprites[i].spriteHeight = SPRITE_HEIGHT;
                    }
                    hibits = hibits >> 1;
                }
                return;
            }
            case VIC_SPRITE_DOUBLE_WIDTH:
            {
                //  The least significant bit refers to sprite #0, and the most sigificant bit to sprite #7.
                int hibits = value;
                for ( int i = 0 ; i < 8 ; i++ )
                {
                    if ( (hibits & 1 ) != 0 ) {
                        sprites[i].spriteWidth = SPRITE_DOUBLE_WIDTH;
                    } else {
                        sprites[i].spriteWidth = SPRITE_WIDTH;
                    }
                    hibits = hibits >> 1;
                }
                return;
            }
            case VIC_SPRITE_PRIORITY:
            {
                //   *    $D01B  53275  27  Priorität Sprite-Hintergrund, Bit = 1: Hintergrund liegt vor Sprite n (0..255)
                //  The least significant bit refers to sprite #0, and the most sigificant bit to sprite #7.
                int hibits = value;
                for ( int i = 0 ; i < 8 ; i++ )
                {
                    sprites[i].behindForeground = (hibits & 1 ) != 0;
                    hibits = hibits >> 1;
                }
                return;
            }
            case VIC_SPRITE_MULTICOLOR_MODE:
            {
                //   *    $D01B  53275  27  Priorität Sprite-Hintergrund, Bit = 1: Hintergrund liegt vor Sprite n (0..255)
                //  The least significant bit refers to sprite #0, and the most sigificant bit to sprite #7.
                int hibits = value;
                for ( int i = 0 ; i < 8 ; i++ )
                {
                    sprites[i].multiColor = (hibits & 1 ) != 0;
                    hibits = hibits >> 1;
                }
                return;
            }
            case VIC_SPRITE0_COLOR10: sprites[0].color=value & 0xf; sprites[0].rgbColor = RGB_COLORS[ value & 0xf ]; return;
            case VIC_SPRITE1_COLOR10: sprites[1].color=value & 0xf; sprites[1].rgbColor = RGB_COLORS[ value & 0xf ]; return;
            case VIC_SPRITE2_COLOR10: sprites[2].color=value & 0xf; sprites[2].rgbColor = RGB_COLORS[ value & 0xf ]; return;
            case VIC_SPRITE3_COLOR10: sprites[3].color=value & 0xf; sprites[3].rgbColor = RGB_COLORS[ value & 0xf ]; return;
            case VIC_SPRITE4_COLOR10: sprites[4].color=value & 0xf; sprites[4].rgbColor = RGB_COLORS[ value & 0xf ]; return;
            case VIC_SPRITE5_COLOR10: sprites[5].color=value & 0xf; sprites[5].rgbColor = RGB_COLORS[ value & 0xf ]; return;
            case VIC_SPRITE6_COLOR10: sprites[6].color=value & 0xf; sprites[6].rgbColor = RGB_COLORS[ value & 0xf ]; return;
            case VIC_SPRITE7_COLOR10: sprites[7].color=value & 0xf; sprites[7].rgbColor = RGB_COLORS[ value & 0xf ]; return;
            default:
                // $$FALL-THROUGH$$
        }
    }

    protected final ColorAndKind calculatedBGColor = new ColorAndKind();

    private int getPixelColor(int beamX,int beamY)
    {
        final ColorAndKind bgPixel = calculatedBGColor;
        getBackgroundColor( graphicsMode , beamX, beamY, bgPixel );

        int colorFromSprites = SPRITE_TRANSPARENT_COLOR;
        int collisionColor = SPRITE_TRANSPARENT_COLOR;
        int collisionsMask = 0;
        for ( int i = 7 ; i >= 0 ; i-- ) //  draw order: Sprite 0 has the highest priority (drawn last) while Sprite 7 has the lowest priority (drawn first);
        {
            final Sprite sprite = sprites[i];
            if ( sprite.isEnabled )
            {
                final int col = sprite.getPixelColor( this , beamX , beamY );
                if ( col != SPRITE_TRANSPARENT_COLOR )
                {
                    if ( collisionColor != SPRITE_TRANSPARENT_COLOR ) {
                        collisionsMask |= 1<< i;
                    }
                    collisionColor = col;
                    if ( ! sprite.behindForeground || ! bgPixel.hasForegroundColor)
                    {
                        colorFromSprites = col;
                    }
                }
            }
        }

        // TODO: Handle sprite collision IRQ
        return colorFromSprites == SPRITE_TRANSPARENT_COLOR ? bgPixel.rgbColor : colorFromSprites;
    }

    private void getBackgroundColor(GraphicsMode graphicsMode,int beamX, int beamY,ColorAndKind out)
    {
        final int x = beamX - leftBorder;
        final int y = beamY - topBorder;

        switch( graphicsMode )
        {
            case MODE_0: // TEXT            -- extendedColor = false | bitmap = false | multiColor = false
            {
                final int byteOffset = (y/8) * textColumnCount + (x/8);
                final int character = mainMemory.readByte( videoRAMAdr + byteOffset );

                final int glyphAdr = character*8; // *8 bytes per glyph

                /*
                 * Bank no. Bit pattern in 56576/$DD00        Character ROM available?
                 * 0 xxxxxx00 49152–65535 $C000–$FFFF   No
                 * 1 xxxxxx01 32768–49151 $8000–$BFFF   Yes, at 36864–40959 $9000–$9FFF
                 * 2 xxxxxx10 16384–32767 $4000–$7FFF   No
                 * 3 xxxxxx11 0–16383 $0000–$3FFF       Yes, at 4096–8192 $1000–$1FFF
                 */
                final int word;
                switch( charROMAdr )
                {
                    case 0x1000:
                    case 0x9000:
                        word = mainMemory.getCharacterROM().readByte( glyphAdr + (y%8) );
                        break;
                    default:
                        word = mainMemory.readByte( charROMAdr + glyphAdr + (y%8) );
                }

                final int color =mainMemory.getColorRAMBank().readByte( 0x800 + byteOffset ) & 0b1111; // ignore upper 4 bits, color ram is actually a 4-bit static RAM
                final int bitOffset = 7-(x%8);
                final int mask = 1 << bitOffset;
                if ( (word & mask) != 0 )
                {
                    out.foreground( RGB_COLORS[ color ] );
                } else {
                    out.background( backgroundColor );
                }
                return;
            }
            case MODE_1: // MULT-COLOR TEXT -- extendedColor = false | bitmap = false | multiColor = true
            {
                final int byteOffset = (y/8) * textColumnCount + (x/8);
                final int character = mainMemory.readByte( videoRAMAdr + byteOffset );

                final int glyphAdr = character*8; // *8 bytes per glyph

                /*
                 * Bank no. Bit pattern in 56576/$DD00        Character ROM available?
                 * 0 xxxxxx00 49152–65535 $C000–$FFFF   No
                 * 1 xxxxxx01 32768–49151 $8000–$BFFF   Yes, at 36864–40959 $9000–$9FFF
                 * 2 xxxxxx10 16384–32767 $4000–$7FFF   No
                 * 3 xxxxxx11 0–16383 $0000–$3FFF       Yes, at 4096–8192 $1000–$1FFF
                 */
                final int word;
                switch( charROMAdr )
                {
                    case 0x1000:
                    case 0x9000:
                        word = mainMemory.getCharacterROM().readByte( glyphAdr + (y%8) );
                        break;
                    default:
                        word = mainMemory.readByte( charROMAdr + glyphAdr + (y%8) );
                }

                final int color =mainMemory.getColorRAMBank().readByte( 0x800 + byteOffset ) & 0b1111; // ignore upper 4 bits, color ram is actually a 4-bit static RAM

                /* Farb-Bits  Entsprechende Farbe  Speicheradresse
                 * 00          Bildschirmfarbe            53281   $D021
                 * 01          Multicolorfarbe 1            53282   $D022
                 * 10          Multicolorfarbe 2            53283   $D023
                 * 11          Farbspeicher (Zeichenfarbe)   55296-56295
                 *
                 *    $D021  53281  33  Bildschirmhintergrundfarbe (0..15)
                 *    $D022  53282  34  Bildschirmhintergrundfarbe 1 bei Extended Color Modus (0..15)
                 *    $D023  53283  35  Bildschirmhintergrundfarbe 2 bei Extended Color Mode (0..15)
                 *    $D024  53284  36  Bildschirmhintergrundfarbe 3 bei Extended Color Mode (0..15)
                 */
                if ( color <= 7) { // display as regular glyph
                    final int bitOffset = 7-(x%8);
                    final int mask = 1 << bitOffset;
                    if ( (word & mask) != 0 )
                    {
                        out.foreground( RGB_COLORS[ color ] );
                    } else {
                        out.background( backgroundColor );
                    }
                    return;
                }

                final int bitOffset = 3-((x/2)%4);
                final int mask = 0b11 << 2*bitOffset;

                /*
                 *              | MCM=0 |   MCM=1
                 *  ------------+-------+-----------
                 *  Bits/pixel  |   1   |     2
                 *  Pixels/byte |   8   |     4
                 *  Background  |  "0"  | "00", "01"
                 *  Foreground  |  "1"  | "10", "11"
                 *
                 * In multicolor mode (MCM=1), the bit combinations "00" and "01" belong to
                 * * the background and "10" and "11" to the foreground whereas in standard mode
                 * (MCM=0), cleared pixels belong to the background and set pixels to the
                 * foreground. It should be noted that this is also valid for the graphics
                 * generated in idle state.
                 */
                switch( (word & mask) >>> 2*bitOffset )
                {
                    case 0b00:
                        out.background( backgroundColor ); // In multicolor mode (MCM=1), the bit combinations "00" and "01" belong to the background and "10" and "11" to the foreground
                        break;
                    case 0b01:
                        out.background( RGB_COLORS[ readByte( VIC_BACKGROUND0_EXT_COLOR ) & 0b1111 ] ); // In multicolor mode (MCM=1), the bit combinations "00" and "01" belong to the background and "10" and "11" to the foreground
                        break;
                    case 0b10:
                        out.foreground( RGB_COLORS[ readByte( VIC_BACKGROUND1_EXT_COLOR ) & 0b1111 ] );
                        break;
                    case 0b11:
                        out.foreground( RGB_COLORS[ color & 0b0111 ] ); // clear upper bit
                        break;
                    default:
                        throw new RuntimeException("Unreachable code reached");
                }
                return;
            }
            case MODE_2: // HIRES -- extendedColor = false | bitmap = true | multiColor = false
            {
                final int row = y/8;
                final int col = x/8;

                final int cellByteOffset = row*40*8 + col*8+(y & 7);

                final int pixelSet = mainMemory.readByte( bitmapRAMAdr + cellByteOffset );

                final int scrOffset = (y/8)*40+(x/8);
                final int color = mainMemory.readByte( videoRAMAdr + scrOffset );

                final int bitOffset = 7-(x & 7);

                final int mask = 1 << bitOffset;
                if ( (pixelSet & mask) != 0 )
                {
                    out.foreground( RGB_COLORS[ (color >> 4) & 0b1111 ] );
                } else {
                    out.background( RGB_COLORS[ color & 0b1111 ] );
                }
                return;
            }
            case MODE_3: // LORES -- extendedColor = false | bitmap = true | multiColor = true
            {
                final int row = y/8;
                final int col = x/8;

                final int cellByteOffset = row*40*8 + col*8+(y & 7);

                final int pixelSet = mainMemory.readByte( bitmapRAMAdr + cellByteOffset );

                final int scrOffset = (y/8)*40+(x/8);
                final int color = mainMemory.readByte( videoRAMAdr + scrOffset );

                // multi-color mode (160x200)
                int bitOffset = 7-(x/2 & 7);
                bitOffset = bitOffset % 4;

                final int mask = 0b11 << 2*bitOffset;

                switch( ( pixelSet & mask ) >> 2*bitOffset )
                {
                    case 0b00:
                        out.background(  backgroundColor );
                        break;
                    case 0b01:
                        out.background( RGB_COLORS[ (color >> 4) & 0b1111 ] );
                        break;
                    case 0b10:
                        out.foreground( RGB_COLORS[ color & 0b1111 ] );
                        break;
                    case 0b11:
                        out.foreground( RGB_COLORS[ mainMemory.getColorRAMBank().readByte( 0x800 + scrOffset ) & 0b1111 ] );
                        break;
                    default:
                        throw new RuntimeException("Unreachable code reached");
                }
                return;
            }
            case MODE_4: // extended color text mode-- extendedColor = true | bitmap = false | multiColor = false
            {
                final int byteOffset = (y/8) * textColumnCount + (x/8);
                final int character = mainMemory.readByte( videoRAMAdr + byteOffset );

                final int glyphAdr = ( character & 0b0011_1111) *8; // *8 bytes per glyph, upper two bits indicate background color

                /*
                 * Bank no. Bit pattern in 56576/$DD00        Character ROM available?
                 * 0 xxxxxx00 49152–65535 $C000–$FFFF   No
                 * 1 xxxxxx01 32768–49151 $8000–$BFFF   Yes, at 36864–40959 $9000–$9FFF
                 * 2 xxxxxx10 16384–32767 $4000–$7FFF   No
                 * 3 xxxxxx11 0–16383 $0000–$3FFF       Yes, at 4096–8192 $1000–$1FFF
                 */
                final int word;
                switch( charROMAdr )
                {
                    case 0x1000:
                    case 0x9000:
                        word = mainMemory.getCharacterROM().readByte( glyphAdr + (y%8) );
                        break;
                    default:
                        word = mainMemory.readByte( charROMAdr + glyphAdr + (y%8) );
                }

                final int bitOffset = 7-(x%8);
                final int mask = 1 << bitOffset;
                if ( (word & mask) != 0 )
                {
                    final int foregroundColor =mainMemory.getColorRAMBank().readByte( 0x800 + byteOffset ) & 0b1111; // ignore upper 4 bits, color ram is actually a 4-bit static RAM
                    out.foreground( RGB_COLORS[ foregroundColor ] );
                }
                else
                {
                    final int backgroundColor = (character & 0b11000000) >> 6;
                switch ( backgroundColor )
                {
                    case 0b00:
                        out.background( backgroundColor );
                        break;
                    case 0b01:
                        out.background( RGB_COLORS[ readByte( VIC_BACKGROUND0_EXT_COLOR ) & 0b1111 ]  );
                        break;
                    case 0b10:
                        out.background( RGB_COLORS[ readByte( VIC_BACKGROUND1_EXT_COLOR ) & 0b1111 ] );
                        break;
                    case 0b11:
                        out.background( RGB_COLORS[ readByte( VIC_BACKGROUND2_EXT_COLOR ) & 0b1111 ] );
                        break;
                    default:
                        throw new RuntimeException("Unreachable code reached");
                }
                }
                return;
            }
            default:
                out.foreground( RGB_COLORS[ beamX%2 ] ); // display checkered pattern to indicate an unsupported/invalid graphics mode
                // TODO: VIC outputs black on invalid color modes
                return;
        }
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

        rasterIRQEnabled = false;
        irqOnRaster = -1;

        beamX = 0;
        beamY = 0;

        for ( Sprite s : sprites ) {
            s.reset();
        }

        setCurrentBankNo( 0 );

        setGraphicsMode( GraphicsMode.MODE_0 );
    }

    private void setGraphicsMode(GraphicsMode newMode)
    {
        this.graphicsMode = newMode;
        if ( DEBUG_SET_GRAPHICS_MODE ) {
            System.out.println( "VIC: Setting graphics mode "+newMode);
        }
        if ( newMode.extendedColorMode ) {
            System.err.println( "VIC: Setting NOT IMPLEMENTED graphics mode "+newMode);
        }
    }

    private void swapBuffers()
    {
        synchronized( frontBuffer )
        {
            BufferedImage tmp = frontBuffer;
            frontBuffer= backBuffer;
            backBuffer = tmp;
        }
    }

    public void render(Graphics2D graphics,int width,int height)
    {
        synchronized( frontBuffer )
        {
            graphics.drawImage( frontBuffer , 0 , 0 , width, height , null );
        }
    }

    private static GraphicsMode getGraphicsMode(boolean extendedColorMode, boolean bitMapMode, boolean multiColorMode)
    {
        int index = 0;
        if ( extendedColorMode ) {
            index |= 1<<2;
        }
        if ( bitMapMode ) {
            index |= 1<<1;
        }
        if ( multiColorMode ) {
            index |= 1<<0;
        }
        return GRAPHIC_MODES[ index ];
    }

    public Sprite getSprite(int no)
    {
        return sprites[no].createCopy();
    }

    public int getSpriteDataAddr(Sprite sprite)
    {
        final int dataPtrLocation = videoRAMAdr+1016+sprite.spriteNo;
        return bankAdr + 64 * mainMemory.readByte( dataPtrLocation );
    }

    @Override
    public boolean isReadsReturnWrites(int offset) {
        return false; // not for all registers
    }
}