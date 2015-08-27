package de.codesourcery.j6502.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
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
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.swing.JPanel;

import de.codesourcery.j6502.emulator.G64File;
import de.codesourcery.j6502.emulator.G64File.TrackData;
import de.codesourcery.j6502.emulator.G64File.TrackPart;
import de.codesourcery.j6502.emulator.G64File.TrackZoneSpeeds;

public class G64ViewPanel extends JPanel
{
    /*
Track Range  Storage in Bytes    Speed Zone
-----------  ----------------    ----------
   1-17           7820               3  (slowest writing speed)
  18-24           7170               2
  25-30           6300               1
  31-4x           6020               0  (fastest writing speed)
     */
    protected static final Color COLOR_SPEED_00 = Color.RED;
    protected static final Color COLOR_SPEED_01 = Color.ORANGE;
    protected static final Color COLOR_SPEED_10 = Color.YELLOW;
    protected static final Color COLOR_SPEED_11 = Color.BLUE;

    protected static final Color COLOR_SPEED_UNKNOWN = Color.GRAY;

    protected static final int SPEED_UNKNOWN = 5;

    protected static final Color COLOR_DATA =  Color.GREEN;
    protected static final Color COLOR_HEADER = Color.BLACK;
    protected static final Color COLOR_GAP = Color.WHITE;
    protected static final Color COLOR_SYNC = Color.MAGENTA;
    protected static final Color COLOR_EMPTY = Color.GRAY;
    protected static final Color COLOR_UNRECOGNIZED = Color.RED;
    protected static final Color COLOR_HIGHLIGHT = Color.BLUE;
    protected static final Color COLOR_HAS_ERRORS = Color.YELLOW;

    protected static final AffineTransform IDENTITY = new AffineTransform();

    protected static final double CENTER_HOLE_RADIUS = 10;

    protected static final int HORIZIONTAL_BORDER = 15;
    protected static final int VERTICAL_BORDER = 15;

    protected static final Point2D ORIGIN = new Point2D.Double(0,0);

    protected static final int TOTAL_TRACKS = 84; // 42 tracks + 42 half-tracks

    protected static final List<LegendItem> LEGEND_BITRATE = Arrays.asList(
            legend( "Speed 00 (slowest)" , COLOR_SPEED_00 ),
            legend( "Speed 01" , COLOR_SPEED_01 ),
            legend( "Speed 10" , COLOR_SPEED_10 ),
            legend( "Speed 11 (fastest)" , COLOR_SPEED_11 ),
            legend( "Unknown"  , COLOR_SPEED_UNKNOWN )
            );

    protected static final List<LegendItem> LEGEND_DATA = Arrays.asList(
            legend( "Data" , COLOR_DATA ),
            legend( "Header" , COLOR_HEADER ),
            legend( "Gap" , COLOR_GAP ),
            legend( "Sync" , COLOR_SYNC ),
            legend( "Empty" , COLOR_EMPTY ),
            legend( "Unknown" , COLOR_UNRECOGNIZED ),
            legend( "Selected" , COLOR_HIGHLIGHT ),
            legend( "Has errors" , COLOR_HAS_ERRORS )
    );

    protected static final class LegendItem {
        public final Color color;
        public final String label;

        public LegendItem(String label, Color color) {
            this.color = color;
            this.label = label;
        }
    }

    protected final class Legend {

        private final List<LegendItem> items;

        public Legend(List<LegendItem> items) {
            this.items = items;
        }

        public void render(int x,int y,Graphics2D g)
        {
            g.setTransform( IDENTITY );

            final FontMetrics fm = g.getFontMetrics();
            final int heightPerRow = (int) (Math.max( fm.getHeight()  , fm.getHeight() )*1.1f);

            final int widestRow = (int) (items.stream().mapToInt( item -> fm.stringWidth( item.label ) ).max().orElse( 15 ) * 1.3f);

            final Rectangle r = new Rectangle(x,y,widestRow, heightPerRow*items.size());

            g.setColor( Color.WHITE );
            g.fill( r );

            g.setColor( Color.BLACK );
            g.draw( r );

            int currentY = 0;
            for ( LegendItem item : items ) {
                renderItem( item , new Rectangle( x , y + currentY , widestRow , heightPerRow ) , g );
                currentY+= heightPerRow;
            }
        }

        private void renderItem(LegendItem item,Rectangle r,Graphics2D g)
        {
            final FontMetrics fm = g.getFontMetrics();

            final int stringWidth = fm.stringWidth( item.label );
            final Rectangle2D stringBounds = fm.getStringBounds(item.label,g);

            final int leftColWidth = Math.max( 15 , (int) (r.width* 0.2f) );
            final int rightColWidth = Math.max( stringWidth , r.width - leftColWidth );

            final Rectangle leftColumn = new Rectangle( r.x , r.y , leftColWidth , r.height );
            final Rectangle rightColumn = new Rectangle( r.x+leftColWidth , r.y , rightColWidth , r.height );

            // draw box showing color
            int cx = leftColumn.x + leftColumn.width/2;
            int cy = leftColumn.y + leftColumn.height/2;

            g.setColor( item.color );
            g.fillRect( cx -5 , cy -5 , 10 , 10 );

            g.setColor( Color.BLACK );
            g.drawRect( cx -5 , cy -5 , 10 , 10 );

            // draw label
            cx = rightColumn.x + rightColumn.width/2;
            cy = rightColumn.y + rightColumn.height/2;

            g.setColor( Color.BLACK );
            final int y = ( rightColumn.height - (int) stringBounds.getHeight()) / 2 + fm.getAscent();
            g.drawString(item.label, r.x + leftColWidth , r.y +y );
        }
    }

    protected static LegendItem legend(String label,Color color) {
        return new LegendItem(label,color);
    }

    protected static enum ViewMode
    {
        DATA("Data"),
        BITRATE("Bitrate");

        private final String label;
        private ViewMode(String label) {
            this.label =label;
        }

        public String getLabel() { return label; }
    };

    private ViewMode viewMode = ViewMode.DATA;
    private G64File disk;

    private BufferedImage image;
    private Graphics2D graphics;

    private AffineTransform inverseTransform = IDENTITY;

    private double trackWidth;

    private Point viewportP0;
    private double viewportRadius;

    private Segment segmentToHighlight;

    private Rectangle currentViewPort;

    private final Map<Integer,List<Segment>> segments = new HashMap<>();

    private final MouseAdapter mouseListener = new MouseAdapter()
    {
        @Override
        public void mouseMoved(MouseEvent e)
        {
            final Segment newHighlight = getSegmentForPoint( e.getPoint() );
            if ( newHighlight != segmentToHighlight )
            {
                segmentToHighlight = newHighlight;
                if ( segmentToHighlight == null || segmentToHighlight.hasNoPart() ) {
                    setToolTipText(null);
                }
                else
                {
                    String desc = getDescription( segmentToHighlight );
                    if ( segmentToHighlight.part.hasErrors() )
                    {
                        String errors = segmentToHighlight.part.getErrors().stream().limit(15).map( s -> s.toString() ).collect( Collectors.joining("<BR/>" ) );
                        if ( segmentToHighlight.part.getErrors().size() > 15 )
                        {
                            final int delta = segmentToHighlight.part.getErrors().size() -15;
                            errors += "<BR/>&lt; "+delta+" more errors &gt;</BR>";
                        }
                        desc = "<HTML><BODY>"+desc+"<BR/>"+errors+"</BODY></HTML>";
                    }
                    setToolTipText( desc );
                }
                repaint();
            }
        }

        @Override
		public void mouseDragged(MouseEvent e)
        {
            if (viewportP0 != null )
            {
                viewportRadius = viewportP0.distance( e.getPoint() );
                repaint();
            }
        }

        @Override
		public void mousePressed(MouseEvent e)
        {
            if ( e.getButton() == MouseEvent.BUTTON1 && viewportP0 == null && currentViewPort.x == 0 )
            {
                viewportP0 = new Point( e.getPoint() );
            }
        }

        @Override
		public void mouseReleased(MouseEvent e)
        {
            if ( e.getButton() == MouseEvent.BUTTON1 && viewportP0 != null )
            {
                currentViewPort = toRectangle( viewportP0 , viewportRadius );
                viewportP0 = null;
                repaint();
            } else if ( e.getButton() == MouseEvent.BUTTON3 ) {
                viewportP0 = null;
                currentViewPort = new Rectangle(0,0,getWidth(),getHeight());
                repaint();
            }
        }
    };

    protected static final class Segment {

        public final TrackPart part;
        public final int trackNo; // trackno 0..83
        public final int speed;
        public double startRadius;
        public double endRadius;
        public double startAngleDeg;
        public double endAngleDeg;

        public Segment(int trackNo,double startRadius, double endRadius, double startAngleDeg, double endAngleDeg,int speed)
        {
            this(trackNo,startRadius,endRadius,startAngleDeg,endAngleDeg,null,speed);
        }

        public Segment(int trackNo,double startRadius, double endRadius, double startAngleDeg, double endAngleDeg,TrackPart part)
        {
            this(trackNo,startRadius,endRadius,startAngleDeg,endAngleDeg,part,SPEED_UNKNOWN);
        }

        public Segment(int trackNo,double startRadius, double endRadius, double startAngleDeg, double endAngleDeg,TrackPart part,int speed)
        {
            this.trackNo = trackNo;
            this.part = part;
            this.speed = speed;
            this.startRadius = startRadius;
            this.endRadius = endRadius;
            this.startAngleDeg = startAngleDeg;
            this.endAngleDeg = endAngleDeg;
        }

        public float floppyTrackNo() {
            return 1+ ( trackNo /2.0f);
        }

        public boolean hasPart() {
            return part != null;
        }

        public boolean hasNoPart() {
            return part == null;
        }

        public boolean contains(float angle,float radius)
        {
            return angle >= startAngleDeg && angle < endAngleDeg &&
                    radius >= startRadius && radius < endRadius;
        }

        @Override
        public String toString() {
            return "Segment[ radius: "+startRadius+"-"+endRadius+" , angle: "+startAngleDeg+"-"+endAngleDeg+" ]";
        }
    }

    public G64ViewPanel()
    {
        setRequestFocusEnabled(true);
        requestFocus();
        addMouseListener( mouseListener );
        addMouseMotionListener( mouseListener);
    }

    public G64ViewPanel(G64File disk) {
        this();
        setPreferredSize( new Dimension(640,480 ) );
        setDisk( disk );
    }

    public void setDisk(G64File disk)
    {
        this.disk = disk;
        setupSegments();
    }

    private Legend getLegend() {
        return viewMode.equals( ViewMode.DATA ) ? new Legend( LEGEND_DATA ) : new Legend( LEGEND_BITRATE );
    }

    private void setupSegments()
    {
        segments.clear();

        // Track 35 is nearest the center hub, and track 1 is the outermost
        for ( int i = 0 ; i < TOTAL_TRACKS ; i++ )
        {
            final float realTrack = 1+ ( (83-i)/2.0f);

            final Optional<TrackData> trackData = disk.getTrackData( realTrack );
            switch( viewMode ) {
                case DATA:
                    this.segments.put( Integer.valueOf( i) , createDataMap( i , trackData ) );
                    break;
                case BITRATE:
                    this.segments.put( Integer.valueOf( i) , createSpeedMap( i , realTrack , trackData ) );
                    break;
                default:
                    throw new RuntimeException("Unreachable code reached");
            }
        }
        if ( this.segments.size() != 84 ) {
            System.err.println("Internal error, bad segment count");
            System.exit(0);
        }
        forceRepaint();
    }

    private void forceRepaint()
    {
        image = null;
        repaint();
    }

    @Override
    protected void paintComponent(Graphics g)
    {
        if ( image == null || image.getWidth() != getWidth() || image.getHeight() != getHeight() )
        {
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

        graphics.setTransform( IDENTITY );
        graphics.setColor( Color.WHITE);
        graphics.fillRect( 0,0,getWidth() , getHeight() );

        if ( ! segments.isEmpty() )
        {
            int w = getWidth() - 2*HORIZIONTAL_BORDER;
            int h = getHeight() - 2*VERTICAL_BORDER;

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

            final double dx = cx - currentViewPort.x;
            final double dy = cy - currentViewPort.y;

            final AffineTransform activeTransform = AffineTransform.getTranslateInstance( dx*ratio , dy*ratio );
            final AffineTransform scaleInstance = AffineTransform.getScaleInstance( ratio, ratio );
            activeTransform.concatenate( scaleInstance );

            graphics.setTransform( activeTransform );

            try {
                inverseTransform = activeTransform.createInverse();
            }
            catch (NoninvertibleTransformException e)
            {
                throw new RuntimeException(e);
            }

            trackWidth  = Math.max(1f ,  (radius - CENTER_HOLE_RADIUS) / TOTAL_TRACKS );

            // need to gather segments that require marking for deferred rendering ecause
            // the way i'm drawing the circle segments would partially
            // overdraw the markings if I'd do it in one go
            final List<Segment> segmentsWithErrors = new ArrayList<>();
            for ( int i = TOTAL_TRACKS-1 ; i >= 0 ; i-- )
            {
                final List<Segment> segments = this.segments.get( i );
                if ( segments == null ) {
                    throw new RuntimeException("No segments in track "+i+"?");
                }
                final double rStart = CENTER_HOLE_RADIUS + i*trackWidth;
                final double rEnd = rStart + trackWidth;

                for ( Segment segment : segments )
                {
                    segment.startRadius = rStart;
                    segment.endRadius = rEnd;

                    Color currentColor = null;

                    if ( viewMode.equals( ViewMode.BITRATE ) )
                    {
                        switch( segment.speed ) {
                            case 0: currentColor = COLOR_SPEED_00; break;
                            case 1: currentColor = COLOR_SPEED_01; break;
                            case 2: currentColor = COLOR_SPEED_10; break;
                            case 3: currentColor = COLOR_SPEED_11; break;
                            default:
                                currentColor = COLOR_SPEED_UNKNOWN;
                        }
                    }
                    else
                    {
                        if ( segment.part != null )
                        {
                            if ( segment.part.hasErrors() )
                            {
                                currentColor = COLOR_HAS_ERRORS;
                            } else {
                                switch( segment.part.type )
                                {
                                    case DATA: currentColor = COLOR_DATA ; break;
                                    case GAP: currentColor = COLOR_GAP ; break;
                                    case HEADER: currentColor = COLOR_HEADER ; break;
                                    case SYNC: currentColor = COLOR_SYNC; break;
                                    case UNKNOWN: currentColor = COLOR_UNRECOGNIZED; break;
                                    default:
                                        throw new RuntimeException("Unhandled part type "+segment.part.type);
                                }
                            }
                        } else {
                            currentColor = COLOR_EMPTY;
                        }
                        if ( segmentToHighlight == segment ) {
                            currentColor = COLOR_HIGHLIGHT;
                        }
                    }

                    renderTrack( segment , currentColor , segment.startAngleDeg , segment.endAngleDeg );

                    if ( segment.part != null && segment.part.hasErrors() ) {
                        segmentsWithErrors.add(segment);
                    }
                }
            }

            renderErrorMarkers( segmentsWithErrors );
        }

        if ( viewportP0 != null )
        {
            final Rectangle r = viewToModel( toRectangle( viewportP0 , viewportRadius ) );
            final Arc2D.Double arc = new Arc2D.Double( r , 0 , 360 , Arc2D.OPEN );

            Stroke oldStroke = graphics.getStroke();
            graphics.setColor( Color.BLACK );
            graphics.setStroke( new BasicStroke( 2f ) );
            graphics.draw( arc );
            graphics.setStroke(oldStroke);
        }

        if ( segmentToHighlight != null )
        {
            final AffineTransform old = graphics.getTransform();
            graphics.setTransform( IDENTITY );
            graphics.setColor( Color.WHITE );

            final String msg = getDescription( segmentToHighlight ) ;
            final int stringWidth = 15+graphics.getFontMetrics().stringWidth( msg );

            graphics.fillRect( 0, 0 , stringWidth, 25 );

            if ( segmentToHighlight.part != null && segmentToHighlight.part.hasErrors() ) {
                graphics.setColor( Color.RED );
            } else {
                graphics.setColor( Color.BLACK );
            }

            graphics.drawString( msg , 15, 15);
            graphics.setTransform( old );
        }

        getLegend().render( 15,25, graphics );

        // render image
        g.drawImage( image, 0 , 0 , null );
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
            final double r = (s.startRadius+s.endRadius)/2d;

            final double x0 = r * Math.cos( Math.toRadians( (360 - s.startAngleDeg) ) );
            final double y0 = r * Math.sin( Math.toRadians( (360 - s.startAngleDeg) ) );

            final double rad = 10;

            arc.setArc( x0 - rad , y0 - rad, rad*2, rad*2, 0, 360, Arc2D.OPEN );
            graphics.draw( arc );
        }
        graphics.setStroke( stroke );
    }

    private String getDescription(Segment s)
    {
        final DecimalFormat DF = new DecimalFormat("0.0");
        String result = "Track "+DF.format( s.floppyTrackNo() );
        if ( s.part == null ) {
            return result + " , <no data available>";
        }
        return result + " , "+ s.part.toString();
    }

    private Segment getSegmentForPoint(Point point)
    {
        final Point2D p = point;

        final Point2D transformed = inverseTransform.transform( p , new Point2D.Double() );
        final double distanceToCenter = ORIGIN.distance( transformed );
        double angleInDeg = Math.toDegrees(Math.atan2(transformed.getY(), transformed.getX()));
        if ( angleInDeg < 0 ) { // transform angle so 0° degrees is at 3 o'clock just like in Arc2D / drawArc()
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
                    if ( angleInDeg >= s.startAngleDeg && angleInDeg < s.endAngleDeg ) {
                        return s;
                    }
                }
            }
        }
        return null;
    }

    private Rectangle viewToModel(Rectangle r)
    {
        final Point p0 = new Point(r.x , r.y );
        final Point p1 = new Point(r.x+r.width , r.y );
        final Point p2 = new Point(r.x , r.y+r.height );
        final Point p3 = new Point(r.x+r.width , r.y+r.height );

        inverseTransform.transform( p0, p0 );
        inverseTransform.transform( p1, p1 );
        inverseTransform.transform( p2, p2 );
        inverseTransform.transform( p3, p3 );
        return new Rectangle( p0.x , p0.y , p1.x - p0.x , p2.y - p0.y );
    }

    private List<Segment> createDataMap(int trackNo, Optional<TrackData> trackData)
    {
        final double rStart = CENTER_HOLE_RADIUS + trackNo*trackWidth;
        final double rEnd = rStart + trackWidth;

        final List<Segment> result = new ArrayList<Segment>();
        if ( trackData.isPresent() )
        {
            final List<TrackPart> parts = trackData.get().getParts();
            if ( ! parts.isEmpty() )
            {
                final int totalLength = parts.stream().mapToInt( p -> p.getLengthInBits() ).sum();

                if ( totalLength != trackData.get().lengthInBytes*8 ) {
                    throw new RuntimeException("Track parts on track "+trackNo+" do not add up, track has "+(trackData.get().lengthInBytes*8 )+" bits but sum(parts) only has "+totalLength);
                }
                double startAngle = 0;
                Segment lastSegment = null;
                for ( TrackPart part : parts )
                {
                    double angleInc = ( part.getLengthInBits()/ (double) totalLength) * 360.0d;
                    lastSegment = new Segment( 83-trackNo , rStart , rEnd , startAngle , startAngle + angleInc , part );
                    result.add( lastSegment  );
                    startAngle += angleInc;
                }
                if ( Math.abs( 360 - startAngle ) < 0.1 ) {
                    lastSegment.endAngleDeg = 360;
                }
                if ( startAngle < 360 ) {
                    System.err.println("Track "+trackNo+" falls short");
                }
            }
        } else { // non-existent track
            result.add( new Segment( 83-trackNo , rStart, rEnd , 0 , 360 , null ) );
        }
        return result;
    }

    private List<Segment> createSpeedMap(int trackNo,float realFloppyTrack , Optional<TrackData> trackData)
    {
        final double rStart = CENTER_HOLE_RADIUS + (83-trackNo)*trackWidth;
        final double rEnd = rStart + trackWidth;

        final TrackZoneSpeeds zoneSpeeds = disk.getSpeedZonesMap().getSpeedZone( realFloppyTrack );

        final List<Segment> result = new ArrayList<Segment>();
        if ( trackData.isPresent() )
        {
            if ( zoneSpeeds.alwaysSameSpeed )
            {
                result.add( new Segment( 83-trackNo , rStart, rEnd , 0 , 360 , null , zoneSpeeds.speed ) );
            }
            else
            {
                final List<TrackPart> parts = trackData.get().getParts();
                final int totalLengthInBytes = (int) Math.ceil( parts.stream().mapToInt( p -> p.getLengthInBits() ).sum() / 8f );
                final double angleIncrement = 360.0d / totalLengthInBytes;

                int previousSpeed = zoneSpeeds.getSpeedForByte( 0 );
                int currentSpeed = previousSpeed;

                double previousAngle = 0;
                int previousOffset = 0;
                int currentOffset = 1;
                int lastOffset = -1;
                for ( ; currentOffset < totalLengthInBytes ; currentOffset++ )
                {
                    currentSpeed = zoneSpeeds.getSpeedForByte( currentOffset );
                    if ( currentSpeed != previousSpeed )
                    {
                        final int len = currentOffset - previousOffset;
                        result.add( new Segment( 83-trackNo , rStart , rEnd , previousAngle , previousAngle + len*angleIncrement , previousSpeed ) );
                        lastOffset = currentOffset;
                        previousOffset = currentOffset;
                        previousSpeed = currentSpeed;
                    }
                }

                if ( lastOffset != -1 && lastOffset != currentOffset ) {
                    final int len = currentOffset - previousOffset;
                    result.add( new Segment( 83-trackNo , rStart , rEnd , previousAngle , previousAngle + len*angleIncrement , currentSpeed ) );
                }
            }
        }

        if ( result.isEmpty() ) {
            result.add( new Segment( 83-trackNo , rStart, rEnd , 0 , 360 , null , SPEED_UNKNOWN ) );
        }
        return result;
    }

    private void renderTrack(Segment segment, Color currentColor,double startAngle,double endAngle)
    {
        final double rStart = segment.startRadius;
        final double rEnd = trackWidth;

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

    public void setViewMode(ViewMode viewMode)
    {
        if ( this.viewMode == viewMode ) {
            return;
        }
        this.viewMode = viewMode;
        setupSegments();
    }

    private void drawCircle(Arc2D.Double arc,double radius,Color color,double startAngleDeg,double endAngleDeg)
    {
        arc.setArc( -radius , -radius , radius*2,radius*2, startAngleDeg , endAngleDeg - startAngleDeg , Arc2D.OPEN );
        graphics.draw( arc );
    }

    protected static Rectangle toRectangle(Point viewportP0, double radius) {
        final int r = (int) Math.ceil( radius );
        return new Rectangle( viewportP0.x - r , viewportP0.y - r, 2*r , 2*r );
    }

    public G64File getDisk() {
        return disk;
    }
}