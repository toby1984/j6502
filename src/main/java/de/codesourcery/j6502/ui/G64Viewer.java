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
import java.awt.geom.Arc2D;
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

    private boolean xorMode; 
    private Point viewportP0;
    private Point viewportP1;
    
    private Rectangle currentViewPort;
    
    private final Map<Integer,List<Segment>> segments = new HashMap<>();

    private final MouseAdapter mouseListener = new MouseAdapter()
    {
        @Override
        public void mouseMoved(MouseEvent e)
        {
            if ( viewportP0 != null ) 
            {
                if ( viewportP1 != null ) 
                {
                    repaint();
                } else {
                    viewportP1 = new Point();
                }
                viewportP1.setLocation( e.getPoint() );
                repaint();
            }
        }
        
        @Override
        public void mouseClicked(MouseEvent e) 
        {
            if ( e.getButton() == MouseEvent.BUTTON1 ) 
            {
                if ( viewportP0 == null ) { 
                    viewportP0 = new Point( e.getPoint() );
                } 
                else 
                {
                    viewportP1.setLocation( e.getPoint() );
                    currentViewPort = toRectangle( viewportP0 , viewportP1 );
                    viewportP0 = viewportP1 = null;
                    xorMode = false;
                    repaint();
                }
            } 
            else if ( e.getButton() == MouseEvent.BUTTON3 ) 
            {
                viewportP0 = viewportP1 = null;
                xorMode = false;
                currentViewPort = new Rectangle(0,0,getWidth(),getHeight());
                repaint();
            }
        }
    };
    
    private static Rectangle toRectangle(Point viewportP0,Point viewportP1) {
        int xmin = Math.min(viewportP0.x,viewportP1.x );
        int xmax = Math.max(viewportP0.x,viewportP1.x );
        int ymin = Math.min(viewportP0.y,viewportP1.y );
        int ymax = Math.max(viewportP0.y,viewportP1.y );
        return new Rectangle(xmin,ymin, xmax-xmin,ymax-ymin);
    }
    
    protected static final class Segment {

        public final TrackPart part;
        private double startRadius;
        private double endRadius;
        private double startAngle;
        private double endAngle;
        
        public Segment(double startRadius, double endRadius, double startAngle, double endAngle,TrackPart part) 
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
        addMouseListener( mouseListener );
        addMouseMotionListener( mouseListener);
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
            final List<Segment> segments = trackToSegments( i , trackData );
            System.out.println( segments.stream().map( p -> p.toString() ).collect( Collectors.joining( "," ) ) );
            this.segments.put( Integer.valueOf(i) , segments );
        }
    }
    
    @Override
    protected void paintComponent(Graphics g)
    {
        if ( image == null || image.getWidth() != getWidth() || image.getHeight() != getHeight() ) {
            if ( graphics != null ) {
                graphics.dispose();
            }
            image = new BufferedImage(getWidth() , getHeight(), BufferedImage.TYPE_INT_RGB );
            graphics = image.createGraphics();
            graphics.getRenderingHints().put( RenderingHints.KEY_ANTIALIASING , RenderingHints.VALUE_ANTIALIAS_ON );
            graphics.getRenderingHints().put( RenderingHints.KEY_RENDERING , RenderingHints.VALUE_RENDER_QUALITY );
            currentViewPort = new Rectangle(0,0,getWidth(),getHeight() );
        }
        
        final Graphics2D graphics = this.graphics;

        graphics.setColor( Color.BLUE );
        graphics.fillRect( 0,0,getWidth() , getHeight() );

        final int xMargin = 15;
        final int yMargin = 15;

        int w = getWidth() - 2*xMargin;
        int h = getHeight() - 2*yMargin;
        
        final int xOffset = currentViewPort.x+currentViewPort.width/2;
        final int yOffset = currentViewPort.y+currentViewPort.height/2;

        if ( currentViewPort.equals( new Rectangle(0,0,getWidth(),getHeight() ) ) ) {
            centerX = (getWidth() / 2);
            centerY = ( getHeight() / 2 );
        } else {
            centerX = (getWidth() / 2)    + xOffset;
            centerY = ( getHeight() / 2 ) - yOffset;
        }

        float xRatio = getWidth() / (float) currentViewPort.width;
        float yRatio = getHeight() / (float) currentViewPort.height;

        w = (int) ( getWidth() * xRatio );
        h = (int) ( getHeight() * yRatio );
        
        final float radius = Math.min(w, h ) / 2f;
        final float centerHoleRadius = w < h ? xRatio * 10 : yRatio*10 ;

        trackWidth  = Math.max(1f ,  (radius-centerHoleRadius) / 84f );

        for ( int i = 83 ; i >= 0 ; i-- )
        {
            final List<Segment> segments = this.segments.get( i );

            final float rStart = centerHoleRadius + i*trackWidth;
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
                        case HEADER: currentColor = Color.WHITE; break;
                        case SYNC: currentColor = Color.MAGENTA; break;
                        default:
                            throw new RuntimeException("Unhandled part type "+s.part.type);
                    }
                } else {
                    currentColor = Color.GRAY;
                }
                renderTrack(i , centerHoleRadius , currentColor , s.startAngle , s.endAngle ); 
            }
        }
        
        if ( viewportP0 != null && viewportP1 != null ) 
        {
            final Rectangle r = toRectangle( viewportP0 , viewportP1 );
            if ( xorMode ) 
            {
                graphics.setXORMode( Color.BLACK );
                graphics.draw( r );
                graphics.setPaintMode();
            } else {
                graphics.draw(r);
            }
            xorMode = ! xorMode;
        }
        g.drawImage( image, 0 , 0 , null );
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
    
                double startAngle = 0;
                Segment lastSegment = null;
                for ( TrackPart part : parts ) 
                {
                    double angleInc = ( part.getLengthInBits()/totalLength) * 360.0d;
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

    private void renderTrack(int trackNo, float centerHoleRadius, Color currentColor,double startAngle,double endAngle)
    {
        final float rStart = centerHoleRadius + trackNo*trackWidth;
        float end = trackWidth;
        
        startAngle = normalizeAngle( startAngle );
        endAngle = normalizeAngle( endAngle );

        double min = Math.min(startAngle, endAngle);
        double max = Math.max(startAngle, endAngle);        

        graphics.setColor( currentColor );
        final Arc2D.Double arc = new Arc2D.Double();
        for ( float ri = 0 ; ri < end ; ri += 0.3f )
        {
            drawCircle( arc , centerX , centerY , rStart+ri , currentColor , min , max );
        }
    }	

    private double normalizeAngle(double deg) 
    {
        while ( deg < 0 ) {
            deg += 360;
        }
        while ( deg > 360 ) {
            deg -= 360;
        }
        return deg;
    }

    private void drawCircle(Arc2D.Double arc,int centerX,int centerY,float radius,Color color,double startAngle,double endAngle)
    {
        arc.setArc( centerX - radius , centerY - radius , radius*2,radius*2, startAngle , endAngle - startAngle , Arc2D.OPEN );
        graphics.draw( arc );
    }

    protected interface ArrayResizer
    {
        public int[] grow(int[] input);
    }
}