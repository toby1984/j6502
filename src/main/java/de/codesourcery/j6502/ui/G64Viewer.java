package de.codesourcery.j6502.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.Stroke;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.NoninvertibleTransformException;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.text.DecimalFormat;
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

public class G64Viewer extends JPanel
{
    private static final Color COLOR_DATA =  Color.GREEN;
    private static final Color COLOR_HEADER = Color.BLACK;
    private static final Color COLOR_GAP = Color.WHITE;
    private static final Color COLOR_SYNC = Color.MAGENTA;
    private static final Color COLOR_EMPTY = Color.GRAY;
    private static final Color COLOR_UNRECOGNIZED = Color.RED;

    private static final Color COLOR_HIGHLIGHT = Color.BLUE;

    private static final Color COLOR_HAS_ERRORS = Color.YELLOW;

    protected static final AffineTransform IDENTITY = AffineTransform.getTranslateInstance(0,0);

    protected static final Point2D ORIGIN = new Point2D.Double(0,0);

    private static final int TOTAL_TRACKS = 84; // 42 tracks + 42 half-tracks


    private G64File disk;

    private BufferedImage image;
    private Graphics2D graphics;

    private double trackWidth;
    private static final double CENTER_HOLE_RADIUS = 10;

    private boolean xorMode;
    private Point viewportP0;
    private Point viewportP1;

    private Segment toHighlight;

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

                int dx = Math.abs( viewportP1.x - viewportP0.x );
                int dy = Math.abs( viewportP1.y - viewportP0.y );
                if ( dx != dy ) 
                {
                    final int delta = Math.abs( dx-dy );
                    if ( dx > dy ) 
                    {
                        if ( viewportP1.y > viewportP0.y ) {
                            viewportP0.y -= delta;
                        } else {
                            viewportP0.y += delta;
                        }
                    } else {
                        if ( viewportP1.x > viewportP0.x ) {
                            viewportP0.x -= delta;
                        } else {
                            viewportP0.x += delta;
                        }
                    }
                }
                repaint();
            } 
            else 
            {
                final Segment newHighlight = getSegmentForPoint( e.getPoint() );
                if ( newHighlight != toHighlight ) 
                {
                    toHighlight = newHighlight;
                    if ( toHighlight == null || toHighlight.part == null ) {
                        setToolTipText(null);
                    } 
                    else 
                    {
                        String desc = getDescription( toHighlight );
                        if ( toHighlight.part.hasErrors() ) 
                        {
                            final String errors = toHighlight.part.getErrors().stream().map( s -> s.toString() ).collect( Collectors.joining("<BR/>" ) );
                            desc = "<HTML><BODY>"+desc+"<BR/>"+errors+"</BODY></HTML>";
                        }
                        setToolTipText( desc );
                    }
                    repaint();
                }
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
                    viewportP1.setLocation( e.getPoint()  );
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

    private static boolean intersects(Rectangle r,Point center, float radius)
    {
        final Point[] points = new Point[] { new Point( r.x , r.y ),
                new Point( r.x+r.width , r.y ),
                new Point( r.x , r.y+r.height ),
                new Point( r.x+r.width , r.y+r.height ) };

        final double[] dist = new double[] {
                points[0].distanceSq( center ),
                points[1].distanceSq( center ),
                points[2].distanceSq( center ),
                points[3].distanceSq( center )
        };

        Point minp = null;
        Point maxp = null;
        double minDist = 0;
        double maxDist = 0;
        for ( int i = 0 ; i < 4 ; i++ )
        {
            double d = dist[i];
            if ( minp == null || d < minDist ) {
                minp = points[i];
                minDist = d;
            }
            if ( maxp == null || d > minDist ) {
                maxp = points[i];
                maxDist = d;
            }
        }
        minDist = Math.sqrt(minDist);
        maxDist = Math.sqrt(maxDist);
        return radius >= minDist && radius <= maxDist;
    }

    protected static final class Segment {

        public final TrackPart part;
        public final float floppyTrackNo; // 1.0 .. 42.0
        private double startRadius;
        private double endRadius;
        private double startAngle;
        private double endAngle;
        public final int trackNo; // trackno 0..83

        public Segment(int trackNo,double startRadius, double endRadius, double startAngle, double endAngle,TrackPart part)
        {
            this.trackNo = trackNo;
            this.startRadius = startRadius;
            this.endRadius = endRadius;
            this.startAngle = startAngle;
            this.endAngle = endAngle;
            this.part = part;
            this.floppyTrackNo = 1+ ( trackNo /2.0f);
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

        final InputStream in = G64File.class.getResourceAsStream( "/disks/wintergames.g64" );
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
        for ( int i = 0 ; i < TOTAL_TRACKS ; i++ )
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

        graphics.setTransform( AffineTransform.getTranslateInstance( 0, 0 ) );
        graphics.setColor( Color.WHITE);
        graphics.fillRect( 0,0,getWidth() , getHeight() );

        final int xMargin = 15;
        final int yMargin = 15;

        int w = getWidth() - 2*xMargin;
        int h = getHeight() - 2*yMargin;

        final double cx = getWidth()/2d;
        final double cy = getHeight()/2d;

        final double ratio; 
        final float radius;
        if ( w < h ) {
            radius = w / 2f;
            ratio = w  / (double) currentViewPort.width;
        } else {
            radius = h / 2f;
            ratio = h  / (double) currentViewPort.height;
        }

        final double dx = -currentViewPort.x+cx;
        final double dy = cy - currentViewPort.y;

        System.out.println("Translate: "+dx+" , "+dy);
        System.out.println("Scale: "+ratio);

        final AffineTransform activeTransform = AffineTransform.getTranslateInstance( dx*ratio , dy*ratio );
        final AffineTransform scaleInstance = AffineTransform.getScaleInstance( ratio, ratio );
        activeTransform.concatenate( scaleInstance );

        graphics.setTransform( activeTransform );

        trackWidth  = Math.max(1f ,  (radius - CENTER_HOLE_RADIUS) / TOTAL_TRACKS );

        final List<Segment> segmentsWithErrors = new ArrayList<>();
        for ( int i = TOTAL_TRACKS-1 ; i >= 0 ; i-- )
        {
            final List<Segment> segments = this.segments.get( i );

            final double rStart = CENTER_HOLE_RADIUS + i*trackWidth;
            final double rEnd = rStart + trackWidth;

            for ( Segment s : segments )
            {
                s.startRadius = rStart;
                s.endRadius = rEnd;

                Color currentColor = null;
                if ( s.part != null )
                {
                    if ( s.part.hasErrors() ) 
                    {
                        currentColor = COLOR_HAS_ERRORS;
                    } else {
                        switch( s.part.type )
                        {
                            case DATA: currentColor = COLOR_DATA ; break;
                            case GAP: currentColor = COLOR_GAP ; break;
                            case HEADER: currentColor = COLOR_HEADER ; break;
                            case SYNC: currentColor = COLOR_SYNC; break;
                            case UNKNOWN: currentColor = COLOR_UNRECOGNIZED; break;
                            default:
                                throw new RuntimeException("Unhandled part type "+s.part.type);
                        }
                    }
                } else {
                    currentColor = COLOR_EMPTY;
                }
                if ( toHighlight == s ) {
                    currentColor = COLOR_HIGHLIGHT;
                }
                renderTrack(i , currentColor , s.startAngle , s.endAngle );
                if ( s.part != null && s.part.hasErrors() ) {
                    segmentsWithErrors.add(s);
                }
            }
        }

        renderErrorMarkers( segmentsWithErrors );

        if ( viewportP0 != null && viewportP1 != null )
        {
            final Rectangle r = viewToModel( toRectangle( viewportP0 , viewportP1 ) );

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

        if ( toHighlight != null ) {
            final AffineTransform old = graphics.getTransform();
            graphics.setTransform( IDENTITY );
            graphics.setColor( Color.WHITE );

            final String msg = getDescription( toHighlight ) ;
            final int stringWidth = 15+graphics.getFontMetrics().stringWidth( msg );

            graphics.fillRect( 0, 0 , stringWidth, 25 );
            
            if ( toHighlight.part != null && toHighlight.part.hasErrors() ) {
                graphics.setColor( Color.RED );
            } else {
                graphics.setColor( Color.BLACK );
            }

            graphics.drawString( msg , 15, 15);
            graphics.setTransform( old );
        }
        g.drawImage( image, 0 , 0 , null );
    }

    private String getDescription(Segment s) 
    {
        final DecimalFormat DF = new DecimalFormat("0.0");
        String result = "Track "+DF.format( s.floppyTrackNo );
        if ( s.part == null ) {
            result += " , <unused>";
        } else {
            result += " , "+ s.part.toString();
        }
        return result;
    }

    private Segment getSegmentForPoint(Point point) 
    {
        final Point2D p = point;

        AffineTransform inverted;
        try {
            inverted = graphics.getTransform().createInverse();
        } catch (NoninvertibleTransformException e) {
            throw new RuntimeException(e);
        }

        final Point2D transformed = inverted.transform( p , new Point2D.Double() );
        final double distanceToCenter = ORIGIN.distance( transformed );
        double angleInDeg = Math.toDegrees(Math.atan2(transformed.getY(), transformed.getX()));
        if ( angleInDeg < 0 ) {
            angleInDeg = -angleInDeg;
        } else {
            angleInDeg = 360.0d - angleInDeg;
        }

        for ( int i = TOTAL_TRACKS-1 ; i >= 0 ; i-- )
        {
            final double rStart = CENTER_HOLE_RADIUS + i*trackWidth;
            final double rEnd = rStart + trackWidth;

            if ( distanceToCenter >= rStart && distanceToCenter < rEnd ) 
            {
                final List<Segment> segments = this.segments.get( i );
                for ( Segment s : segments )
                {
                    if ( angleInDeg >= s.startAngle && angleInDeg < s.endAngle ) {
                        return s;
                    }
                }
            }
        }
        return null;
    }

    private Rectangle viewToModel(Rectangle r) 
    {
        Point p0 = new Point(r.x , r.y );
        Point p1 = new Point(r.x+r.width , r.y );
        Point p2 = new Point(r.x , r.y+r.height );
        Point p3 = new Point(r.x+r.width , r.y+r.height );

        AffineTransform inverse;
        try {
            inverse = graphics.getTransform().createInverse();
            inverse.transform( p0, p0 );
            inverse.transform( p1, p1 );
            inverse.transform( p2, p2 );
            inverse.transform( p3, p3 );
            return new Rectangle( p0.x , p0.y , p1.x - p0.x , p2.y - p0.y );
        } catch (NoninvertibleTransformException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
        }
        return r;
    }

    private List<Segment> trackToSegments(int trackNo, Optional<TrackData> trackData)
    {
        final double rStart = CENTER_HOLE_RADIUS + trackNo*trackWidth;
        final double rEnd = rStart + trackWidth;

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
                    lastSegment = new Segment( trackNo , rStart , rEnd , startAngle , startAngle + angleInc , part );
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
        } else { // non-existent track
            result.add( new Segment( trackNo , rStart, rEnd , 0 , 360 , null ) );
        }
        return result;
    }

    private void renderTrack(int trackNo, Color currentColor,double startAngle,double endAngle)
    {
        final double rStart = CENTER_HOLE_RADIUS + trackNo*trackWidth;
        double rEnd = trackWidth;

        startAngle = normalizeAngle( startAngle );
        endAngle = normalizeAngle( endAngle );

        double min = Math.min(startAngle, endAngle);
        double max = Math.max(startAngle, endAngle);

        graphics.setColor( currentColor );
        final Arc2D.Double arc = new Arc2D.Double();
        for ( double ri = 0 ; ri < rEnd ; ri += 0.3d )
        {
            drawCircle( arc , rStart+ri , currentColor , min , max );
        }
    }

    private void renderErrorMarkers(List<Segment> segments) 
    {
        if ( segments.isEmpty() ) {
            return;
        }
        final Stroke stroke = graphics.getStroke();

        graphics.setStroke( new BasicStroke( 2f ) );
        graphics.setColor( Color.RED );

        final Arc2D.Double arc = new Arc2D.Double();
        for ( Segment s : segments) 
        {
            final double r = (s.startRadius + s.endRadius) /2d;
            final double dx = r * Math.cos( Math.toRadians( s.endAngle ) );
            final double dy = r * Math.sin( Math.toRadians( s.endAngle ) );

            final double rad = 10;

            arc.setArc( dx - rad , dy - rad, rad*2, rad*2, 0, 360, Arc2D.OPEN );
            graphics.draw( arc );
        }
        graphics.setStroke( stroke );
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

    private void drawCircle(Arc2D.Double arc,double radius,Color color,double startAngle,double endAngle)
    {
        arc.setArc( -radius , -radius , radius*2,radius*2, startAngle , endAngle - startAngle , Arc2D.OPEN );
        graphics.draw( arc );
    }

    protected interface ArrayResizer
    {
        public int[] grow(int[] input);
    }
}