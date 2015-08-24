package de.codesourcery.j6502.ui;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.JFrame;
import javax.swing.JPanel;

import de.codesourcery.j6502.emulator.G64File;
import de.codesourcery.j6502.emulator.G64File.TrackData;
import de.codesourcery.j6502.emulator.G64File.TrackPart;

public class G64Viewer extends JPanel {

    private static final float CENTER_HOLE_RADIUS = 10;
    private static final int TOTAL_TRACKS = 84; // 42 tracks + 42 half-tracks

    private G64File disk;

    private BufferedImage image;
    private Graphics2D graphics;

    private float trackWidth;

    private int centerX;
    private int centerY;

    private Rectangle currentViewport;
    private int trackToHighlight = -1;

    private final Map<Integer,List<Segment>> segments = new HashMap<>();

    public static void main(String[] args) throws IOException {

        final JFrame frame = new JFrame("test");
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

        final InputStream in = G64File.class.getResourceAsStream( "/disks/pitfall.g64" );
        final G64File file = new G64File( in );

        frame.getContentPane().add( new G64Viewer( file ) );

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
        setPreferredSize( new Dimension(640,480 ) );
        setDisk( disk );
    }

    public void setDisk(G64File disk) 
    {
        this.disk = disk;

        segments.clear();
        for ( int i = 0 ; i < 84 ; i++ ) 
        {
            final float realTrack = 1+ (i/2.0f);

            final Optional<TrackData> trackData = disk.getTrackData( realTrack );
            System.out.println("Track "+realTrack+": ");
            //	         if ( trackData.isPresent() ) 
            //	         {
            //	             System.out.println( trackData.get().getParts().stream().map( p -> p.toString() ).collect( Collectors.joining( "," ) ) );
            //	         } else {
            //	             System.out.println("<EMPTY>");
            //	         }
            final List<Segment> segments = trackToSegments( i , trackData );
            System.out.println( segments.stream().map( p -> p.toString() ).collect( Collectors.joining( "," ) ) );
            this.segments.put( Integer.valueOf(i) , segments );
        }
    }
    
    protected static final class Segment {

        public final TrackPart part;
        private float startRadius;
        private float endRadius;
        private final float startAngle;
        private float endAngle;
        
        private Rectangle currentViewPort;

        public Segment(float startRadius, float endRadius, float startAngle, float endAngle,TrackPart part) 
        {
            this.startRadius = startRadius;
            this.endRadius = endRadius;
            this.startAngle = startAngle;
            this.endAngle = endAngle;
            this.part = part;
        }

        public boolean contains(float angle,float radius) 
        {
            return angle >= startAngle && angle < endAngle &&
                    radius >= startRadius && radius < endRadius;
        }

        @Override
        public String toString() {
            return "Segment[ radius: "+startRadius+"-"+endRadius+" , angle: "+startAngle+"-"+endAngle+" ]";
        }
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
        
        if ( currentViewport == null ) {
            currentViewport = new Rectangle(0,0,getWidth(),getHeight() );
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
            final List<Segment> segments = this.segments.get( i );

            final float rStart = CENTER_HOLE_RADIUS + i*trackWidth;
            final float rEnd = rStart + trackWidth;

            for ( Segment s : segments ) 
            {
                s.startRadius = rStart;
                s.endRadius = rEnd;

                final Color currentColor;
                if ( s.part != null ) 
                {
                    switch( s.part.type ) 
                    {
                        case DATA: currentColor = Color.GREEN; break;
                        case GAP: currentColor = Color.BLACK; break;
                        case HEADER: currentColor = Color.MAGENTA; break;
                        case SYNC: currentColor = Color.YELLOW; break;
                        default:
                            throw new RuntimeException("Unhandled part type "+s.part.type);
                    }
//                    System.out.println("Track "+i+": "+s);
                } else {
//                    System.out.println("Track "+i+": empty segment");
                    currentColor = Color.GRAY;
                }
                renderTrack(i , currentColor , s.startAngle , s.endAngle ); 
            }
        }
        gxx.drawImage( image, 0 , 0 , null );
    }

    private List<Segment> trackToSegments(int trackNo, Optional<TrackData> trackData) {

        final float rStart = CENTER_HOLE_RADIUS + trackNo*trackWidth;
        final float rEnd = rStart + trackWidth;

        final List<Segment> result = new ArrayList<Segment>(); 
        if ( trackData.isPresent() ) 
        {
            final List<TrackPart> parts = trackData.get().getParts();
            if ( ! parts.isEmpty() ) 
            {
                final float totalLength = parts.stream().mapToInt( p -> p.getLengthInBits() ).sum();
    
                float startAngle = 0;
                Segment lastSegment = null;
                for ( TrackPart part : parts ) 
                {
                    float angleInc = ( part.getLengthInBits()/totalLength) * 360.0f;
                    lastSegment = new Segment( rStart , rEnd , startAngle , startAngle + angleInc , part );
                    result.add( lastSegment  );
                    startAngle += angleInc;
                }
                if ( Math.abs( 360 - startAngle ) < 0.1 ) {
                    lastSegment.endAngle = 360;
                }
                if ( startAngle < 360 ) {
                    System.out.println("Track "+trackNo+" falls short");
                }
            } 
        } else { // non-existant track
            result.add( new Segment(rStart, rEnd , 0 , 360 , null ) );
        }
        return result;
    }

    private void renderTrack(int trackNo, Color currentColor)
    {
        renderTrack(trackNo,currentColor,0,360);
    }

    private void renderTrack(int trackNo, Color currentColor,float startAngle,float endAngle)
    {
        final float rStart = CENTER_HOLE_RADIUS + trackNo*trackWidth;
        float end = trackWidth;

        for ( float ri = 0 ; ri < end ; ri++ )
        {
            drawCircle( centerX , centerY , rStart+ri , currentColor , startAngle , endAngle );
        }
    }	

    private float normalizeAngle(float deg) 
    {
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