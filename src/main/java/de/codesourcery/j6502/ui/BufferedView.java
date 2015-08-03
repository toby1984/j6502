package de.codesourcery.j6502.ui;

import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.image.BufferedImage;

import javax.swing.JPanel;

/**
 *
 *
 * @author tobias.gierke@voipfuture.com
 */
public class BufferedView extends JPanel
{
	private final Object BUFFER_LOCK = new Object();

	// @GuardedBy( BUFFER_LOCK )
	private BufferedImage frontBuffer;

	// @GuardedBy( BUFFER_LOCK )
	private BufferedImage backBuffer;

	// @GuardedBy( BUFFER_LOCK )
	private Graphics2D frontGraphics;

	// @GuardedBy( BUFFER_LOCK )
	private Graphics2D backGraphics;

	public BufferedView()
	{
		addComponentListener( new ComponentAdapter()
		{
			@Override
			public void componentResized(ComponentEvent e)
			{
				System.out.println("Component resized: "+BufferedView.this.getClass().getName());
				if ( getWidth() > 0 && getHeight() > 0 ) {
					setupBuffers(getWidth(),getHeight());
				}
			}

			@Override
			public void componentShown(ComponentEvent e) {
				System.out.println("Component shown: "+BufferedView.this.getClass().getName());
				if ( getWidth() > 0 && getHeight() > 0 ) {
					setupBuffers(getWidth(),getHeight());
				}
			}
		});
		setupBuffers( 10 ,  10 );
	}

	protected final Graphics2D getBackBufferGraphics()
	{
		synchronized(BUFFER_LOCK)
		{
			return backGraphics;
		}
	}

	protected void setupBuffers(int width,int height)
	{
		synchronized(BUFFER_LOCK)
		{
			BufferedImage oldFrontBuffer=null;
			if ( frontGraphics != null )
			{
				oldFrontBuffer = frontBuffer;
				frontGraphics.dispose();
				backGraphics.dispose();
			}
			frontBuffer = new BufferedImage( width , height , BufferedImage.TYPE_INT_RGB );
			frontGraphics = frontBuffer.createGraphics();
			initGraphics( frontGraphics );

			backBuffer = new BufferedImage( width , height , BufferedImage.TYPE_INT_RGB );
			backGraphics = backBuffer.createGraphics();
			initGraphics( backGraphics );

			if ( oldFrontBuffer != null )
			{
				frontGraphics.drawImage( oldFrontBuffer , 0 , 0 , null );
			} else {
				clear( frontGraphics );
			}
			clear( backGraphics );
		}
	}

	protected void initGraphics(Graphics2D g) {

	}

	private void clear(Graphics2D g)
	{
		g.setColor( getBackground() );
		g.fillRect( 0 , 0 , getWidth() , getHeight() );
		g.setColor( getForeground() );
	}

	protected final void swapBuffers()
	{
		synchronized(BUFFER_LOCK)
		{
			Graphics2D tmpGraphics = frontGraphics;
			frontGraphics = backGraphics;
			backGraphics = tmpGraphics;

			BufferedImage tmpImage = frontBuffer;
			frontBuffer = backBuffer;
			backBuffer = tmpImage;

			clear( backGraphics );
		}
	}

	@Override
	protected final void paintComponent(Graphics g)
	{
		synchronized ( BUFFER_LOCK )
		{
			g.drawImage( frontBuffer , 0 , 0 , null );
		}
	}
}