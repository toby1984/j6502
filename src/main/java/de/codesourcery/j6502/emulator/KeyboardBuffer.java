package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.j6502.emulator.Keyboard.Key;

/**
 * Keyboard input buffer.
 * 
 * <p>This class needs to be thread-safe since it gets called asynchronously from the Swing EDT while
 * the emulation itself is running in a separate thread.</p> 
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class KeyboardBuffer 
{
	protected static final int CYCLES_PER_MS = 1000; // TODO: assuming  1 Mhz CPU frequency 

	protected static final int KEYPRESS_DURATION_CYCLES = 45*CYCLES_PER_MS; 
	protected static final int KEYRELEASE_DURATION_CYCLES = 25*CYCLES_PER_MS; 

	protected static final int NOT_RELEASED = -1;

	protected static final class KeyEvent 
	{
		public final long pressInCycle;
		public final Key key;

		public long releaseInCycle=NOT_RELEASED;
		public boolean isPressed;

		public KeyEvent(Key key,long pressInCycle) 
		{
			this.key = key;
			this.pressInCycle = pressInCycle;
		}

		@Override
		public String toString() {
			return "KeyEvent[ key="+key+", pressed="+isPressed+" , pressInCycle="+pressInCycle+" , releaseInCycle="+releaseInCycle+" ]";
		}
	}

	// @GuardedBy( buffer )
	protected long cycleCounter = 0;

	// @GuardedBy( buffer )
	private final List<KeyEvent> buffer = new ArrayList<>(128);

	// @GuardedBy( buffer )
	private final List<String> fakeInput = new ArrayList<>(128);

	public void tick(IOArea area) 
	{
		synchronized( buffer ) 
		{
			if ( ! fakeInput.isEmpty() ) 
			{
				processFakeKeyboardInput( area );
			}

			int len = buffer.size();
			for ( int i = 0 ; i < len ; i++ ) 
			{
				final KeyEvent ev = buffer.get(i);
				if ( ! ev.isPressed ) 
				{
					if ( ev.pressInCycle <= cycleCounter ) 
					{
						area.handleKeyPress( ev.key );
						ev.isPressed = true;
					}
				} 
				else if ( ev.releaseInCycle != NOT_RELEASED && ev.releaseInCycle <= cycleCounter ) 
				{
					area.handleKeyRelease( ev.key );
					buffer.remove( i );
					i--;
					len--;
				}
			}
			
			cycleCounter++;
		}
	}

	/**
	 * Flushed the keyboard buffer while releasing all keys that are currently pressed.
	 *  
	 * @param area
	 */
	private void flushBuffer(IOArea area) 
	{
		synchronized( buffer ) 
		{		
			for (int i = 0,len = buffer.size() ; i < len ; i++) 
			{
				final KeyEvent ev = buffer.get(i);
				if ( ev.isPressed ) 
				{
					// pressed but not released yet, fake key release
					area.handleKeyRelease( ev.key );
				}
			}
			buffer.clear();
		}
	}	

	public void reset() 
	{
		synchronized( buffer ) 
		{		
			cycleCounter=0;
			buffer.clear();
		}
	}

	/**
	 * Generate fake keyboard input from UTF-8 text.
	 *
	 * This method tries to perform a best-effort conversion from UTF-8
	 * characters to key presses on a C64 keyboard. Any characters that
	 * cannot be mapped to keys will be ignored.
	 * 
	 * The actual key presses will be queued on the next {@link #tick(IOArea)}.
	 * 
	 * @param text text to convert to keyboard input, must not be <code>NULL</code>
	 */
	public void fakeKeyboardInput(String text) 
	{
		if ( text == null ) {
			throw new IllegalArgumentException("text must not be NULL");
		}
		synchronized( buffer ) 
		{	
			fakeInput.add( text );
		}
	}

	/**
	 * Queue key press.
	 * 
	 * @param key key to be pressed
	 * @param pressInCycle CPU cycle in which the key should be pressed
	 */
	public void keyPressed(Key key,long pressInCycle) 
	{
	    System.out.println("PRESSED: "+key);
		synchronized( buffer ) 
		{	
			buffer.add( new KeyEvent(key , pressInCycle ) );
		}
	}	
	
	/**
	 * Queue key release. 
	 * 
	 * Releasing a key more than once or releasing a key that has not been
	 * {@link #keyPressed(Key, long) pressed} before does nothing.
	 * 
	 * @param key key to be released
	 * @param releaseInCycle CPU cycle in which to release the key
	 */
	public void keyReleased(Key key,long releaseInCycle) 
	{
		synchronized( buffer ) 
		{	
			for ( int i = 0 , len=buffer.size() ; i < len ; i++ ) 
			{
				final KeyEvent ev = buffer.get(i);
				if ( ev.key == key && ev.releaseInCycle == NOT_RELEASED ) 
				{
					ev.releaseInCycle = releaseInCycle;
					return;
				}
			}
		}
	}

	private void processFakeKeyboardInput(IOArea ioArea) 
	{
		flushBuffer( ioArea );

		long currentCycle = cycleCounter;

		for (int k = 0, len2 = this.fakeInput.size() ; k < len2 ; k++) 
		{
			final String text = fakeInput.get(k);
			for ( int i = 0, len = text.length() ; i < len ; i++ ) 
			{
				final char c = text.charAt( i );
				final List<Key> keys = Keyboard.charToKeys( c );

				// fake key press
				for (int j = 0; j < keys.size(); j++) {
					keyPressed( keys.get(j) , currentCycle );
				}
				currentCycle += KEYPRESS_DURATION_CYCLES;

				// fake key release
				for (int j = 0; j < keys.size(); j++) 
				{
					keyReleased( keys.get(j) , currentCycle );
				}
				currentCycle += KEYRELEASE_DURATION_CYCLES;
			}
		}
		fakeInput.clear();
	}		
}