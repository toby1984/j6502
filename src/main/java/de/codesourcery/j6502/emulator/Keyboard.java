package de.codesourcery.j6502.emulator;

import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class Keyboard
{
    /*
     * Array that has ASCII character code as index
     * and
     * (keyboard row<<8) | keyboard column
     *
     *  was values.
     *
     *  ROWS: \             COLUMNS:    peek($DC01)
     *  poke   \
     *              PA0     PA1      PA2    PA3     PA4     PA5     PA6     PA7
     *  $DC00   \   128      64      32      16      8       4       2       1
     *           +-------+-------+-------+-------+-------+-------+-------+-------+
     *    255-1  | DOWN  |   F5  |   F3  |   F1  |   F7  | RIGHT | RETURN| DELETE| PB7
     *           +-------+-------+-------+-------+-------+-------+-------+-------+
     *    255-2  |LEFT-SH|   E   |   S   |   Z   |   4   |   A   |   W   |   3   | PB6
     *           +-------+-------+-------+-------+-------+-------+-------+-------+
     *    255-4  |   X   |   T   |   F   |   C   |   6   |   D   |   R   |   5   | PB5
     *           +-------+-------+-------+-------+-------+-------+-------+-------+
     *    255-8  |   V   |   U   |   H   |   B   |   8   |   G   |   Y   |   7   | PB4
     *           +-------+-------+-------+-------+-------+-------+-------+-------+
     *   255-16  |   N   |   O   |   K   |   M   |   0   |   J   |   I   |   9   | PB3
     *           +-------+-------+-------+-------+-------+-------+-------+-------+
     *   255-32  |   ,   |   @   |   :   |   .   |   -   |   L   |   P   |   +   | PB2
     *           +-------+-------+-------+-------+-------+-------+-------+-------+
     *   255-64  |   /   |   ^   |   =   |RGHT-SH|  HOME |   ;   |   *   |   \   | PB1
     *           +-------+-------+-------+-------+-------+-------+-------+-------+
     *  255-128  | STOP  |   Q   |COMMODR| SPACE |   2   |CONTROL|   _   |   1   | PB0
     *           +-------+-------+-------+-------+-------+-------+-------+-------+
     */

    public static enum Key
    {
        // row #0 (idx , rowBit / PRB , columnBit / PRA )
        KEY_DOWN  (0,7,  0),
        KEY_F5    (1,6,  0),
        KEY_F3    (2,5,  0),
        KEY_F1    (3,4,  0),
        KEY_F7    (4,3,  0),
        KEY_RIGHT (5,2,  0),
        KEY_RETURN((char) 10,1,0),
        KEY_DELETE(7,0,  0),
        // row #1 (idx , rowBit / PRB , columnBit / PRA )
        KEY_LEFT_SHIFT(8 ,7,1),
        KEY_E         ('e',6 ,1),
        KEY_S         ('s',5,1),
        KEY_Z         ('z',4,1),
        KEY_4         ('4',3,1),
        KEY_A         ('a',2,1),
        KEY_W         ('w',1,1),
        KEY_3         ('3',0,1),
        // row #2 (idx , rowBit / PRB , columnBit / PRA )
        KEY_X('x',7,2),
        KEY_T('t',6,2),
        KEY_F('f',5,2),
        KEY_C('c',4,2),
        KEY_6('6',3,2),
        KEY_D('d',2,2),
        KEY_R('r',1,2),
        KEY_5('5',0,2),
        // row #3 (idx , rowBit / PRB , columnBit / PRA )
        KEY_V('v',7,3),
        KEY_U('u',6,3),
        KEY_H('h',5,3),
        KEY_B('b',4,3),
        KEY_8('8',3,3),
        KEY_G('g',2,3),
        KEY_Y('y',1,3),
        KEY_7('7',0,3),
        // row #4 (idx , rowBit / PRB , columnBit / PRA )
        KEY_N('n',7, 4),
        KEY_O('o',6, 4),
        KEY_K('k',5, 4),
        KEY_M('m',4, 4),
        KEY_0('0',3, 4),
        KEY_J('j',2, 4),
        KEY_I('i',1, 4),
        KEY_9('9',0, 4),
        // row #5 (idx , rowBit / PRB , columnBit / PRA )
        KEY_COMMA (',',7, 5),
        KEY_AT    ('@',6, 5),
        KEY_COLON (':',5, 5),
        KEY_PERIOD('.',4, 5),
        KEY_MINUS ('-',3, 5),
        KEY_L     ('l',2, 5),
        KEY_P     ('p',1, 5),
        KEY_PLUS  ('+',0, 5),
        // row #6 (idx , rowBit / PRB , columnBit / PRA )
        KEY_SLASH       ('/',7, 6) {
            @Override
            public boolean clearShift() {
                return true;
            }
        },
        KEY_CIRCUMFLEX  ('^',6, 6),
        KEY_EQUALS      ('=',5, 6),
        KEY_RIGHT_SHIFT (51, 4,6),
        KEY_HOME        (52, 3,6),
        KEY_SEMICOLON   (';',2, 6)
        {
            @Override
            public boolean clearShift() {
                return true;
            }
        },
        KEY_MULTIPLY    ('*',1, 6),
        KEY_POUND  ('\\',0, 6),
        // row #7 (idx , rowBit / PRB , columnBit / PRA )
        KEY_STOP      (56, 7,7),
        KEY_Q         ('q',6, 7),
        KEY_COMMODORE (58, 5,7),
        KEY_SPACE     (' ',4, 7),
        KEY_2         ('2',3, 7),
        KEY_CONTROL   (61, 2,7),
        KEY_UNDERSCORE('_',1, 7),
        KEY_1         ('1',0, 7),
        // VIRTUAL KEYS (These don't actually exist on the C64 keyboard)
        KEY_BACKSLASH('\\',4, 4) // actually SHIFT+M
        {
            @Override
            public boolean fakeLeftShift() {
                return true;
            }
        },
        KEY_LEFT  (5,2,  0) { // not represented on keyboard matrix, actually a combination of SHIFT + KEY_RIGHT
            @Override
            public boolean fakeLeftShift() {
                return true;
            }
        },
        KEY_UP (0,7,  0) { // not represented on keyboard matrix, actually a combination of SHIFT + KEY_DOWN
            @Override
            public boolean fakeLeftShift() {
                return true;
            }
        };

        public final char c;
        public final int rowBitNo;
        public final int colBitNo;

        private Key(int idx,int rowBitNo,int colBitNo) {
            this((char) 0,rowBitNo,colBitNo);
        }

        private Key(char c,int rowBitNo,int colBitNo) {
            this.c = c;
            this.rowBitNo = rowBitNo;
            this.colBitNo = colBitNo;
        }

        public boolean clearShift() {
            return false;
        }

        public boolean fakeLeftShift() {
            return false;
        }
    }

    public static enum KeyLocation {
        LEFT,RIGHT,STANDARD;
    }

    public static enum Modifier {
        SHIFT,CONTROL,NONE,ALT_GR,ALT;
    }

    public static Key keyCodeToKey(int extendedKeyCode,KeyLocation location,Set<Modifier> modifiers)
    {
        return internalKeyCodeToKey(extendedKeyCode, location, modifiers);
    }

    private static Key internalKeyCodeToKey(int extendedKeyCode,KeyLocation location,Set<Modifier> modifiers)
    {
        switch( extendedKeyCode )
        {
            // row #0
            case KeyEvent.VK_DOWN:   return Key.KEY_DOWN;
            case KeyEvent.VK_UP:   return Key.KEY_UP;
            case KeyEvent.VK_F5:     return Key.KEY_F5;
            case KeyEvent.VK_F3:     return Key.KEY_F3;
            case KeyEvent.VK_F1:     return Key.KEY_F1;
            case KeyEvent.VK_F7:     return Key.KEY_F7;
            case KeyEvent.VK_RIGHT:  return Key.KEY_RIGHT;
            case KeyEvent.VK_LEFT:  return Key.KEY_LEFT;
            case KeyEvent.VK_ENTER:  return Key.KEY_RETURN;
            case KeyEvent.VK_BACK_SPACE: return Key.KEY_DELETE;
            case KeyEvent.VK_DELETE: return Key.KEY_DELETE;
            // row #1
            case KeyEvent.VK_SHIFT:
                if ( location == KeyLocation.LEFT ) {
                    return Key.KEY_LEFT_SHIFT;
                }
                return Key.KEY_RIGHT_SHIFT;
            case KeyEvent.VK_E:     return Key.KEY_E;
            case KeyEvent.VK_S:     return Key.KEY_S;
            case KeyEvent.VK_Z:     return Key.KEY_Z;
            case KeyEvent.VK_4:     return Key.KEY_4;
            case KeyEvent.VK_A:     return Key.KEY_A;
            case KeyEvent.VK_W:     return Key.KEY_W;
            case KeyEvent.VK_3:     return Key.KEY_3;
            // row #2
            case KeyEvent.VK_X:     return Key.KEY_X;
            case KeyEvent.VK_T:     return Key.KEY_T;
            case KeyEvent.VK_F:     return Key.KEY_F;
            case KeyEvent.VK_C:     return Key.KEY_C;
            case KeyEvent.VK_6:     return Key.KEY_6;
            case KeyEvent.VK_D:     return Key.KEY_D;
            case KeyEvent.VK_R:     return Key.KEY_R;
            case KeyEvent.VK_5:     return Key.KEY_5;
            // row #3
            case KeyEvent.VK_V:     return Key.KEY_V;
            case KeyEvent.VK_U:     return Key.KEY_U;
            case KeyEvent.VK_H:     return Key.KEY_H;
            case KeyEvent.VK_B:     return Key.KEY_B;
            case KeyEvent.VK_8:     return Key.KEY_8;
            case KeyEvent.VK_G:     return Key.KEY_G;
            case KeyEvent.VK_Y:     return Key.KEY_Y;
            case KeyEvent.VK_7:
                if ( modifiers.contains( Modifier.SHIFT ) ) {
                    return Key.KEY_SLASH;
                }
                return Key.KEY_7;
                // row #4
            case KeyEvent.VK_N:     return Key.KEY_N;
            case KeyEvent.VK_O:     return Key.KEY_O;
            case KeyEvent.VK_K:     return Key.KEY_K;
            case KeyEvent.VK_M:     return Key.KEY_M;
            case KeyEvent.VK_0:
                if ( modifiers.contains( Modifier.SHIFT ) ) {
                    return Key.KEY_EQUALS;
                }
                return Key.KEY_0;
            case KeyEvent.VK_J:     return Key.KEY_J;
            case KeyEvent.VK_I:     return Key.KEY_I;
            case KeyEvent.VK_9:     return Key.KEY_9;
            // row #5
            case KeyEvent.VK_COMMA:
                if ( modifiers.contains( Modifier.SHIFT ) ) {
                    return Key.KEY_SEMICOLON;
                }
                return Key.KEY_COMMA;
            case KeyEvent.VK_AT:    return Key.KEY_AT;
            case KeyEvent.VK_COLON: return Key.KEY_COLON;
            case KeyEvent.VK_PERIOD:return Key.KEY_PERIOD;
            case KeyEvent.VK_MINUS: return Key.KEY_MINUS;
            case KeyEvent.VK_L:     return Key.KEY_L;
            case KeyEvent.VK_P:     return Key.KEY_P;
            case KeyEvent.VK_PLUS:  return Key.KEY_PLUS;
            // row #6
            case KeyEvent.VK_SLASH: return Key.KEY_SLASH;
            case KeyEvent.VK_CIRCUMFLEX:    return Key.KEY_CIRCUMFLEX;
            case KeyEvent.VK_EQUALS: return Key.KEY_EQUALS;
            case KeyEvent.VK_HOME: return Key.KEY_HOME;
            case KeyEvent.VK_SEMICOLON:     return Key.KEY_SEMICOLON;
            case KeyEvent.VK_MULTIPLY:     return Key.KEY_MULTIPLY;
            case KeyEvent.VK_BACK_SLASH:
                return Key.KEY_BACKSLASH;
                // row #7
            case KeyEvent.VK_PAUSE: return Key.KEY_STOP;
            case KeyEvent.VK_Q:    return Key.KEY_Q;
            case KeyEvent.VK_WINDOWS: return Key.KEY_COMMODORE;
            case KeyEvent.VK_SPACE:return Key.KEY_SPACE;
            case KeyEvent.VK_2: return Key.KEY_2;
            case KeyEvent.VK_CONTROL:     return Key.KEY_CONTROL;
            case KeyEvent.VK_UNDERSCORE:     return Key.KEY_UNDERSCORE;
            case KeyEvent.VK_1:  return Key.KEY_1;
            default:
                return null;
        }
    }

    /**
     * Tries to convert UTF-8 character to key presses.
     *
     * @param character
     * @return Keys that need to be pressed at the same time to input the character or an empty list if this character is not available on the keyboard
     */
    public static List<Key> charToKeys(char character)
    {
        final char c = Character.toLowerCase( character );
        final List<Key> result = new ArrayList<>();

        // TODO: Lots of character mappings missing here...there are obviously many more characters that require modifiers
        switch(c)
        {
            case '#':
                result.add( Key.KEY_LEFT_SHIFT );
                result.add( Key.KEY_3 );
                return result;
            case '(':
                result.add( Key.KEY_LEFT_SHIFT );
                result.add( Key.KEY_8 );
                return result;
            case ')':
                result.add( Key.KEY_LEFT_SHIFT );
                result.add( Key.KEY_9 );
                return result;
            case '$':
                result.add( Key.KEY_LEFT_SHIFT );
                result.add( Key.KEY_4 );
                return result;
            default:
                // $$FALL-THROUGH$$
        }

        for ( int j = 0 , len = Key.values().length ; j < len ; j++ )
        {
            if ( Key.values()[j].c == c ) {
                result.add( Key.values()[j]);
                return result;
            }
        }
        System.out.println("Failed to convert char to key press: '"+c+"' ("+(int) c+")");
        return result;
    }
}