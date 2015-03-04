package de.codesourcery.j6502.emulator;

import java.awt.event.KeyEvent;
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
		KEY_UP (0,7,  0) { // not represented on keyboard matrix, actually a combination of SHIFT + KEY_DOWN
			@Override
			public boolean fakeLeftShift() {
				return true;
			}
		},
		KEY_F5    (1,6,  0),
		KEY_F3    (2,5,  0),
		KEY_F1    (3,4,  0),
		KEY_F7    (4,3,  0),
		KEY_RIGHT (5,2,  0),
		KEY_LEFT  (5,2,  0) { // not represented on keyboard matrix, actually a combination of SHIFT + KEY_RIGHT
			@Override
			public boolean fakeLeftShift() {
				return true;
			}
		},		
		KEY_RETURN(6,1,  0),
		KEY_DELETE(7,0,  0),	
		// row #1 (idx , rowBit / PRB , columnBit / PRA )
		KEY_LEFT_SHIFT(8 ,7,1),
		KEY_E         (9 ,6,1),
		KEY_S         (10,5,1),
		KEY_Z         (11,4,1),
		KEY_4         (12,3,1),
		KEY_A         (13,2,1),
		KEY_W         (14,1,1),
		KEY_3         (15,0,1),
		// row #2 (idx , rowBit / PRB , columnBit / PRA )
		KEY_X(16,7,2),
		KEY_T(17,6,2),
		KEY_F(18,5,2),
		KEY_C(19,4,2),
		KEY_6(20,3,2),
		KEY_D(21,2,2),
		KEY_R(22,1,2),
		KEY_5(23,0,2),
		// row #3 (idx , rowBit / PRB , columnBit / PRA )
		KEY_V(24,7,3),
		KEY_U(25,6,3),
		KEY_H(26,5,3),
		KEY_B(27,4,3),
		KEY_8(28,3,3),
		KEY_G(29,2,3),
		KEY_Y(30,1,3),
		KEY_7(31,0,3),
		// row #4 (idx , rowBit / PRB , columnBit / PRA )
		KEY_N(32, 7,4),
		KEY_O(33, 6,4),
		KEY_K(34, 5,4),
		KEY_M(35, 4,4),
		KEY_0(36, 3,4),
		KEY_J(37, 2,4),
		KEY_I(38, 1,4),
		KEY_9(39, 0,4),
		// row #5 (idx , rowBit / PRB , columnBit / PRA )
		KEY_COMMA (40, 7,5),
		KEY_AT    (41, 6,5),
		KEY_COLON (42, 5,5),
		KEY_PERIOD(43, 4,5),
		KEY_MINUS (44, 3,5),
		KEY_L     (45, 2,5),
		KEY_P     (46, 1,5),
		KEY_PLUS  (47, 0,5),
		// row #6 (idx , rowBit / PRB , columnBit / PRA )
		KEY_SLASH       (48, 7,6),
		KEY_CIRCUMFLEX  (49, 6,6),
		KEY_EQUALS      (50, 5,6),
		KEY_RIGHT_SHIFT (51, 4,6),
		KEY_HOME        (52, 3,6),
		KEY_SEMICOLON   (53, 2,6) {
			@Override
			public boolean clearShift() {
				return true;
			}
		},
		KEY_MULTIPLY    (54, 1,6),
		KEY_BACK_SLASH  (55, 0,6),
		// row #7 (idx , rowBit / PRB , columnBit / PRA )
		KEY_STOP      (56, 7,7),
		KEY_Q         (57, 6,7),
		KEY_COMMODORE (58, 5,7),
		KEY_SPACE     (59, 4,7),
		KEY_2         (60, 3,7),
		KEY_CONTROL   (61, 2,7),
		KEY_UNDERSCORE(62, 1,7),
		KEY_1         (63, 0,7);			
		
		public final int index;
		public final int rowBitNo;
		public final int colBitNo;
		
		private Key(int idx,int rowBitNo,int colBitNo) {
			this.index = idx;
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
		SHIFT,CONTROL,NONE;
	}

	public static Key keyCodeToKey(int keyCode,KeyLocation location,Set<Modifier> modifiers) 
	{
		System.out.println("Modifiers: "+modifiers);
		switch( keyCode ) 
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
			case KeyEvent.VK_7:     return Key.KEY_7;	
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
			case KeyEvent.VK_BACK_SLASH:  return Key.KEY_BACK_SLASH;
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
}