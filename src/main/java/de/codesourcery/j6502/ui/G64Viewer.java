package de.codesourcery.j6502.ui;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;

import javax.swing.JFrame;
import javax.swing.JPanel;

import de.codesourcery.j6502.emulator.G64File;

public class G64Viewer extends JPanel {

	private static final float CENTER_HOLE_RADIUS = 10;
	private static final int TOTAL_TRACKS = 84; // 42 tracks + 42 half-tracks

	private G64File disk;

	private BufferedImage image;
	private Graphics2D graphics;

	private float trackWidth;

	private int centerX;
	private int centerY;

	private int trackToHighlight = -1;

	public static void main(String[] args) {

		final JFrame frame = new JFrame("test");
		frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

		frame.getContentPane().add( new G64Viewer() );

		frame.pack();
		frame.setLocationRelativeTo( null );
		frame.setVisible( true );
	}

	public G64Viewer()
	{
		setRequestFocusEnabled(true);
		requestFocus();
		addMouseMotionListener( new MouseAdapter()
		{
			@Override
			public void mouseMoved(MouseEvent e)
			{
				int newTrackNo = getTrackNo( e.getPoint() );

				if ( newTrackNo != trackToHighlight )
				{
					trackToHighlight = newTrackNo;
					repaint();
				}
			}
		});
	}

	private int getTrackNo(Point p)
	{
		double dx = p.x - centerX;
		double dy = p.y - centerY;
		double radius = Math.sqrt( dx*dx+dy*dy );
		radius -= CENTER_HOLE_RADIUS;

		final int result = (int) (radius / trackWidth);
		if ( result < 0 || result >= 84 ) {
			return -1;
		}
		return result;
	}

	public G64Viewer(G64File disk) {
		this();
		this.disk = disk;
	}

	public void setDisk(G64File disk) {
		this.disk = disk;
	}

	@Override
	protected void paintComponent(Graphics gxx)
	{
		if ( image == null || image.getWidth() != getWidth() || image.getHeight() != getHeight() ) {
			if ( graphics != null ) {
				graphics.dispose();
			}
			image = new BufferedImage(getWidth() , getHeight(), BufferedImage.TYPE_INT_RGB );
			graphics = image.createGraphics();
			graphics.getRenderingHints().put( RenderingHints.KEY_ANTIALIASING , RenderingHints.VALUE_ANTIALIAS_ON );
			graphics.getRenderingHints().put( RenderingHints.KEY_RENDERING , RenderingHints.VALUE_RENDER_QUALITY );
		}

		final Graphics2D graphics = this.graphics;

		graphics.setColor( Color.BLACK );
		graphics.fillRect( 0,0,getWidth() , getHeight() );

		final int xMargin = 15;
		final int yMargin = 15;

		int w = getWidth() - 2*xMargin;
		int h = getHeight() - 2*yMargin;

		centerX = getWidth() / 2 ;
		centerY = getHeight() / 2 ;

		// draw disk outline
		graphics.setColor( Color.BLUE );
		graphics.fillRect( xMargin,yMargin , w , h );

		final float radius = Math.min(w, h ) / 2f;

		trackWidth  = Math.max(1 ,  (radius-CENTER_HOLE_RADIUS) / 84f );

		for ( int i = 0 ; i < 84 ; i++ )
		{
			final Color currentColor;
			if ( i == trackToHighlight ) {
				currentColor = Color.PINK;
			} else {
				currentColor = i %2 == 0 ? Color.ORANGE : Color.GREEN;
			}
			renderTrack(i,currentColor);
		}
		gxx.drawImage( image, 0 , 0 , null );
	}

	private void renderTrack(int trackNo, Color currentColor)
	{
		final float rStart = CENTER_HOLE_RADIUS + trackNo*trackWidth;
		float end = trackWidth;

		for ( float ri = 0 ; ri < end ; ri++ )
		{
			drawCircle( centerX , centerY , rStart+ri , currentColor , 0 , 45 );
		}
	}

	private float normalizeAngle(float deg) {
		while ( deg < 0 ) {
			deg += 360;
		}
		while ( deg > 360 ) {
			deg -= 360;
		}
		return deg;
	}

	private void drawCircle(int centerX,int centerY,float radius,Color color,float startAngle,float endAngle)
	{
		startAngle = normalizeAngle( startAngle );
		endAngle = normalizeAngle( endAngle );

		float min = Math.min(startAngle, endAngle);
		float max = Math.max(startAngle, endAngle);

		min /= 360f;
		max /= 360f;

		final int steps = (int) Math.ceil( radius*20 );

		final double angleInc = 2*Math.PI / steps;

		final int rgb = color.getRGB();

		final int w = image.getWidth();
		final int h = image.getHeight();

		final int start = (int) (min * steps);
		final int end = (int) (max * steps);

		for ( int i = start ; i < end ; i++)
		{
			double angle = angleInc *i;
			int x = (int) Math.round( centerX + Math.cos( angle )*radius);
			int y = (int) Math.round( centerY - Math.sin( angle )*radius);

			x &= 0xffff;
			y &= 0xffff;
			if ( x < w && y < h ) {
				image.setRGB( x , y , rgb );
			}
		}
	}

	protected interface ArrayResizer
	{
		public int[] grow(int[] input);
	}
}