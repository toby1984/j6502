package de.codesourcery.j6502.ui;

import java.awt.Component;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.Transferable;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.Reader;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.Keyboard;
import de.codesourcery.j6502.emulator.Keyboard.Key;
import de.codesourcery.j6502.emulator.Keyboard.KeyLocation;
import de.codesourcery.j6502.emulator.Keyboard.Modifier;

/**
 * AWT <code>KeyListener</code> that translates AWT <code>KeyEvent</code>s into
 * key presses on the emulator's virtual C64 keyboard. 
 *
 * <p>Note that you <b>must</b> attach this listener using {@link #attach(Component)} and
 * <b>not</b> by just using {@link Component#addKeyListener(java.awt.event.KeyListener)}.</p>
 * 
 * @author tobias.gierke@voipfuture.com
 */
public class KeyboardInputListener extends KeyAdapter 
{
	private final Emulator emulator;
	private Component peer;
	
	private volatile boolean pasteEnabled = true;
	
	public KeyboardInputListener(Emulator emulator) 
	{
		this.emulator = emulator;
	}
	
	/**
	 * Attach this listener to an AWT component.
	 * 
	 * @param peer
	 */
	public void attach(Component peer) 
	{
		peer.addKeyListener( this );
		this.peer = peer;
	}
	
	/**
	 * Enable/disable pasting text from clipboard when CTRL-V is pressed.
	 * 
	 * @param pasteEnabled
	 */
	public void setPasteEnabled(boolean pasteEnabled) {
		this.pasteEnabled = pasteEnabled;
	}
	
	@Override
	public void keyPressed(java.awt.event.KeyEvent e)
	{
		final KeyLocation location = getLocation(e);
		final Set<Modifier> modifiers = getModifiers( e );
		
		if ( e.getKeyCode() == KeyEvent.VK_V && modifiers.contains( Modifier.CONTROL ) && pasteEnabled)  // replay clipboard contents as keyboard input
		{
			final String text = getClipboardContentAsText();
			if ( text != null )
			{
				synchronized( emulator ) 
				{
					emulator.getKeyboardBuffer().fakeKeyboardInput( text );
				}
			}
			return;
		}
		
		final Key pressed = Keyboard.keyCodeToKey( e.getKeyCode() , location , modifiers);
		if ( pressed != null ) 
		{
			synchronized( emulator ) 
			{
				emulator.getKeyboardBuffer().keyPressed( pressed , emulator.getCPU().cycles );
			}
		}
	}

	private Set<Keyboard.Modifier> getModifiers(KeyEvent e)
	{
		int mask = e.getModifiersEx();
		boolean shiftPressed = false;
		boolean controlPressed = false;
		if ( ( (mask & KeyEvent.SHIFT_DOWN_MASK) != 0 ) ||
				( ( mask & KeyEvent.SHIFT_MASK ) != 0 )
				)
		{
			shiftPressed = true;
		}

		if ( ( (mask & KeyEvent.CTRL_DOWN_MASK) != 0 ) ||
				( ( mask & KeyEvent.CTRL_MASK ) != 0 )
				)
		{
			controlPressed = true;
		}
		if ( ! controlPressed && ! shiftPressed ) {
			return Collections.emptySet();
		}
		Set<Keyboard.Modifier> result = new HashSet<>();
		if ( controlPressed ) {
			result.add( Keyboard.Modifier.CONTROL );
		}
		if ( shiftPressed ) {
			result.add( Keyboard.Modifier.SHIFT );
		}
		return result;
	}
	
	/**
	 * Try to read text from clipboard.
	 * 
	 * @return clipboard contents as text or <code>NULL</code> if the clipboard was either empty or its contents were not convertible to text.  
	 */
	private String getClipboardContentAsText() 
	{
		final Clipboard clipboard = peer.getToolkit().getSystemClipboard();
		
		Transferable contents = clipboard.getContents( this );
		if ( contents != null ) 
		{
			for ( DataFlavor flavor : contents.getTransferDataFlavors() ) 
			{
				if ( "text".equals( flavor.getPrimaryType() ) ) 
				{
					try 
					{
						final StringBuilder buffer = new StringBuilder();
						final Reader reader = DataFlavor.getTextPlainUnicodeFlavor().getReaderForText( contents );
						for ( int c = -1 ; ( c = reader.read() ) != -1 ; ) {
							buffer.append( (char) c );
						}
						return buffer.toString();
					} 
					catch (Exception e1) 
					{
						System.err.println("Failed to read from clipboard ("+e1.getMessage()+")");
						e1.printStackTrace();
						return null;
					}
				}
			}
			System.err.println("Clipboard contains no transferable with a flavor that's understood by me :(");
			System.err.println("Got MIME types: "+Arrays.stream( contents.getTransferDataFlavors() ).map( f -> f.getMimeType() ).collect(Collectors.joining("," ) ));
		} else {
			System.out.println("Clipboard is empty");
		}
		return null;
	}

	private KeyLocation getLocation(KeyEvent e)
	{
		final int keyLocation = e.getKeyLocation();
		if (keyLocation == KeyEvent.KEY_LOCATION_LEFT) {
			return KeyLocation.LEFT;
		}
		if (keyLocation == KeyEvent.KEY_LOCATION_RIGHT) {
			return KeyLocation.RIGHT;
		}
		return KeyLocation.STANDARD;
	}
	
	@Override
	public void keyReleased(java.awt.event.KeyEvent e)
	{
		final Key released = Keyboard.keyCodeToKey( e.getKeyCode() , getLocation(e) , getModifiers(e) );
		if ( released != null ) 
		{
			synchronized( emulator ) 
			{
				emulator.getKeyboardBuffer().keyReleased( released , emulator.getCPU().cycles );
			}
		}
	}	
}