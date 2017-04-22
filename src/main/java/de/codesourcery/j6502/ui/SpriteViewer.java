package de.codesourcery.j6502.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.util.Arrays;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.VIC;
import de.codesourcery.j6502.emulator.VIC.Sprite;
import de.codesourcery.j6502.utils.HexDump;

public class SpriteViewer extends JPanel implements WindowLocationHelper.IDebuggerView {

	private boolean isDisplayed=false;
	private Component peer;

	protected final JComboBox<Integer> selectedSpriteNo = new JComboBox<Integer>( Arrays.asList(0,1,2,3,4,5,6,7 ).toArray(new Integer[0] ) );

	private final SpriteView view = new SpriteView();

	private final Emulator emulator;

	protected volatile int selectedSprite = 0;

	public SpriteViewer(Emulator emulator)
	{
		this.emulator = emulator;

		setPreferredSize( new Dimension(200,300 ));
		setMinimumSize( new Dimension(200,300 ));
		selectedSpriteNo.setRenderer( new DefaultListCellRenderer()
		{
			@Override
			public Component getListCellRendererComponent(javax.swing.JList<?> list, Object value, int index, boolean isSelected, boolean cellHasFocus)
			{
				final Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
				if ( value instanceof Integer)
				{
					setText( "Sprite #"+value );
				}
				return result;
			};
		});

		selectedSpriteNo.addActionListener( ev ->
		{
			selectedSprite = (Integer) selectedSpriteNo.getSelectedItem();
			repaint(200);
		});

		setLayout( new BorderLayout() );

		add( selectedSpriteNo , BorderLayout.NORTH );
		add( view , BorderLayout.CENTER );

		view.setPreferredSize( new Dimension(200,300 ));
		view.setMinimumSize( new Dimension(200,300 ));

		Debugger.setup( this );
		Debugger.setup( view );
	}

	protected final class SpriteView extends BufferedView
	{
		public Sprite sprite;
		public int spriteDataAdr;
		public final byte[] spriteData = new byte[ 63 ]; // 24 pixels wide x 21 pixel height = 3 bytes * 21

		public void refresh(Emulator emulator)
		{
			final int selectedSprite = SpriteViewer.this.selectedSprite;
			if ( selectedSprite != -1 )
			{
				final VIC vic = emulator.getVIC();
				sprite = vic.getSprite( selectedSprite ); // returns a COPY that is safe to access later
				spriteDataAdr = vic.getSpriteDataAddr( sprite );

				for ( int i = 0 ; i < 63 ; i++ )
				{
					spriteData[i] = (byte) emulator.getMemory().readByte( spriteDataAdr + i );
				}

				final Graphics2D graphics = getBackBufferGraphics();
				final int MARGIN = 5;
				final int lineHeight = 15;

				int x = MARGIN;
				int y = lineHeight;

				graphics.setColor(Color.GREEN );
				graphics.drawString( sprite.toString() , x , y );
				y += lineHeight;

				graphics.drawString( "Data address: "+HexDump.toAdr( spriteDataAdr ) , x , y );
				y += lineHeight;

				int remainingHeight = getHeight() - y - 2*MARGIN;
				int remainingWidth = getWidth () - 2*MARGIN;

				int xScale = remainingWidth/24;
				int yScale = remainingHeight/21;

				if ( sprite.multiColor )
				{
					// multi-color sprite
					for ( int y1 = y ,j=0 ; j < 21 ; j++ ,y1 += yScale )
					{
						for ( int x1 = MARGIN , i=0 ;  i < 24 ; i++ , x1 += xScale  )
						{
							final int byteOffset = 3*j + i/8;
							final int colorBits = spriteData[ byteOffset ];
							final int bitOffset = 3-(i/2)%4;

							final int mask = 0b11 << 2*bitOffset;

							final Color color;
							switch( (colorBits & mask ) >> 2*bitOffset )
							{
								/*
								 * Bit-Paar  Farbe
								 *
								 * "00": Transparent                     | MxMC = 1
								 * "01": Sprite multicolor 0 ($d025)     |
								 * "10": Sprite color ($d027-$d02e)      |
								 * "11": Sprite multicolor 1 ($d026)
								 */
								case 0b00: color = Color.BLACK; break;
								case 0b01: color = VIC.AWT_COLORS[ vic.readByte( VIC.VIC_SPRITE_COLOR01_MULTICOLOR_MODE ) & 0b1111 ]; break;
								case 0b10: color = VIC.AWT_COLORS[ sprite.color ]; break;
								case 0b11: color = VIC.AWT_COLORS[ vic.readByte( VIC.VIC_SPRITE_COLOR11_MULTICOLOR_MODE ) & 0b1111 ]; break;
								default:
									throw new RuntimeException("Internal error");
							}

							graphics.setColor( color );
							graphics.fillRect( x1 , y1 , xScale , yScale );
						}
					}
				}
				else
				{
					// single-color sprite
					for ( int y1 = y ,j=0 ,byteOffset = 0 ; j < 21 ; j++ ,y1 += yScale )
					{
						int bitOffset = 7;
						for ( int x1 = MARGIN , i=0 ;  i < 24 ; i++ , x1 += xScale  )
						{
							final int colorBits = spriteData[ byteOffset ];

							final Color color;
							if ( ( colorBits & ( 1<< bitOffset ) ) != 0 )
							{
								color = VIC.AWT_COLORS[ sprite.color ];
							} else {
								color = Color.BLACK;
							}

							graphics.setColor( color );
							graphics.fillRect( x1 , y1 , xScale , yScale );

							bitOffset--;
							if ( bitOffset < 0 ) {
								byteOffset++;
								bitOffset = 7;
							}
						}
					}
				}

				// draw grid
				for ( int y1 = y ,j=0 ; j < 21 ; j++ ,y1 += yScale )
				{
					graphics.drawLine( MARGIN , y1 , getWidth() - MARGIN , y1 );
				}

				for ( int x1 = MARGIN , i=0 ;  i < 24 ; i++ , x1 += xScale )
				{
					graphics.drawLine( x1 , y , x1 , getHeight()-MARGIN );
				}
			}
			swapBuffers();

			SwingUtilities.invokeLater( () -> repaint() );
		}
	}

	@Override
	public void setLocationPeer(Component frame) {
		this.peer = frame;
	}

	@Override
	public Component getLocationPeer() {
		return peer;
	}

	@Override
	public boolean isDisplayed() {
		return isDisplayed;
	}

	@Override
	public void setDisplayed(boolean yesNo) {
		this.isDisplayed = yesNo;
	}

	@Override
	public void refresh(Emulator emulator)
	{
		view.refresh( emulator );
	}

	@Override
	public boolean isRefreshAfterTick()
	{
		return true;
	}

	@Override
	protected void paintComponent(Graphics g)
	{
		super.paintComponent(g);
		synchronized(emulator)
		{
			view.refresh( emulator );
		}
	}
}