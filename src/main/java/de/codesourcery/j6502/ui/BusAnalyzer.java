package de.codesourcery.j6502.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Arrays;
import java.util.Random;

import javax.swing.JFrame;
import javax.swing.JPanel;

import de.codesourcery.j6502.emulator.Bus;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.ui.WindowLocationHelper.IDebuggerView;

public class BusAnalyzer extends JPanel implements IDebuggerView
{
    protected static final int STATES = 256;

    private final Object LOCK = new Object();

    private static final float STROKE_LINE_WIDTH = 4f;
    private static final int STROKE_GAP_WIDTH = 8;

    private static final Stroke DASHED_LINE = new BasicStroke(1.0f,                      // Width
            BasicStroke.CAP_SQUARE,    // End cap
            BasicStroke.JOIN_MITER,    // Join style
            10.0f,                     // Miter limit
            new float[] {STROKE_LINE_WIDTH,STROKE_GAP_WIDTH}, // Dash pattern
            0.0f);                     // Dash phase

    private Bus bus;

    private long cycles = 0;
    private int oldestStatePtr;
    private int latestStatePtr;

    private final long[] timestamps = new long[ STATES ];
    private final int[] wireStates = new int[ STATES ];

    private int[] wireNamesWidthPixels;
    private int maxWireNameWidthPixels;
    private String[] wireNames;

    private int stateCount;

    private int windowOffset = 0; // offset relative to oldestStatePtr
    private int windowSize; // number of states to render

    public static void main(String[] args) 
    {
        final JFrame frame = new JFrame("test");
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

        final BusAnalyzer analyzer = new BusAnalyzer();
        frame.getContentPane().add( analyzer );

        frame.pack();
        frame.setLocationRelativeTo( null );
        frame.setVisible(true);

        final int[] value = {0};
        final Bus bus = new Bus() {

            private final String[] wires = { "ATN" , "CLK" , "DATA" };
            @Override
            public int getWidth() {
                return 3;
            }

            @Override
            public String[] getWireNames() {
                return wires;
            }

            @Override
            public int read() {
                return value[0];
            }
        };
        analyzer.setBus( bus );

        final Random rnd = new Random(0xdeadbeef);
        for ( int i = 0 ; i < 10 ; i++ ) 
        {
            int state = 0;
            for ( int j = 0 ; j < bus.getWidth() ; j++ ) 
            {
                if ( rnd.nextBoolean() ) {
                    state |= (1<<j);
                }
            }
            value[0] = state;
            for ( int delay = 0 , len = 1 + rnd.nextInt( 1500 ) ; delay < len ; delay++) 
            {
                analyzer.tick();
            }
        }
    }

    public BusAnalyzer() 
    {
        setBackground( Color.BLACK );
        setForeground( Color.GREEN );
        setPreferredSize( new Dimension(400,200 ) );
    }

    public void setBus(Bus bus) 
    {
        synchronized( LOCK ) 
        {
            this.bus = bus;
            reset();
            if ( bus != null ) 
            {
                final Graphics2D g = (Graphics2D) getGraphics();
                final FontMetrics fm = g.getFontMetrics();
                wireNames = bus.getWireNames();
                int maxWidth = 0;
                wireNamesWidthPixels = new int[ bus.getWidth() ];
                for ( int i = 0 ; i < bus.getWidth() ; i++ ) 
                {
                    wireNamesWidthPixels[i] = fm.stringWidth( wireNames[i] );
                    maxWidth = Math.max( maxWidth ,  wireNamesWidthPixels[i]  );
                }
                this.maxWireNameWidthPixels = maxWidth;
            }
        }
    }

    public void reset() 
    {
        synchronized( LOCK ) 
        {
            windowOffset = 0;
            windowSize = STATES;
            stateCount = 0;
            oldestStatePtr = 0;
            latestStatePtr = 0;
            cycles = 0;
            Arrays.fill( timestamps,0);
            Arrays.fill( wireStates,0);
        }
    }

    public void tick() 
    {
        synchronized( LOCK )
        {
            if ( bus != null ) 
            {
                final int newValue = bus.read();

                if ( stateCount != 0 && wireStates[ (latestStatePtr-1) % STATES ] == newValue ) 
                {
                    return; // wire state did not change
                } 

                timestamps[latestStatePtr] = cycles;
                wireStates[latestStatePtr] = newValue;

                latestStatePtr = (latestStatePtr+1) % STATES;
                if ( stateCount < STATES ) 
                {
                    stateCount++;
                } else {
                    oldestStatePtr = (oldestStatePtr+1) % STATES;
                }
            }
            cycles++;
        }
    }

    @Override
    protected void paintComponent(Graphics g) 
    {
        super.paintComponent(g);

        synchronized( LOCK ) 
        {
            if ( bus == null ) {
                return;
            }

            final Graphics2D graphics = (Graphics2D) g;
            final int wireCount = bus.getWidth();
            final int heightPerWire = getHeight() / wireCount;

            final Rectangle r = new Rectangle();
            r.width = getWidth();
            r.height = heightPerWire;
            for ( int i = 0 ; i < wireCount ; i++ ) 
            {
                renderWire(i,r, graphics);
                r.y += heightPerWire;
            }        
        }
    }

    private void renderWire(int wireIndex,Rectangle r,Graphics2D graphics) 
    {
        /*
         *                |
         *             1 -+- . . . . . . . . 
         *   Wirename     |
         *             0 -+- . . . . . . . . 
         *                |
         *                +-----------------
         */
        final int yMargin = 5;
        final int yAxisWidth = 25; // width of y axis including '0' and '1' labels
        final int tickLength = 4;
        final int yAxisHeight = r.height - 2 * yMargin;
        final int xAxisWidth = (int) (r.width*0.95 - maxWireNameWidthPixels - yAxisWidth);

        // render wire label
        graphics.setColor(Color.GREEN);
        final Rectangle tmp = new Rectangle( r.x , r.y + yMargin , maxWireNameWidthPixels , yAxisHeight );
        drawCentered( wireNames[ wireIndex ] , tmp , graphics );

        // render X axis
        final int originX = r.x + maxWireNameWidthPixels + yAxisWidth;
        final int originY = r.y + r.height - yMargin;

        graphics.drawLine( originX  , originY , originX + xAxisWidth ,  originY );

        // render y axis
        graphics.drawLine( originX , originY , originX , r.y + yMargin );

        // draw Y-axis labels & tick marks
        final int tickOffsetsY = (int) ( (yAxisHeight - yAxisHeight*0.6)/2 );
        final int yLow = originY - tickOffsetsY ;
        final int yHigh = originY - yAxisHeight + tickOffsetsY ;

        graphics.drawLine( originX - tickLength , yLow , originX  , yLow );
        tmp.setBounds( originX - yAxisWidth , yLow - 5 , yAxisWidth , 10 );
        drawCentered( "0" , tmp , graphics );

        graphics.drawLine( originX - tickLength , yHigh , originX , yHigh );
        tmp.setBounds( originX - yAxisWidth , yHigh - 5 , yAxisWidth , 10 );
        drawCentered( "1" , tmp , graphics );

        // render dashed level indicator lines
        final Stroke oldStroke = graphics.getStroke();
        graphics.setStroke( DASHED_LINE );
        graphics.setColor( Color.LIGHT_GRAY );
        graphics.drawLine( originX + STROKE_GAP_WIDTH , yHigh , originX + xAxisWidth , yHigh );
        graphics.drawLine( originX + STROKE_GAP_WIDTH , yLow , originX + xAxisWidth , yLow );
        graphics.setStroke(oldStroke);

        // render signal levels
        if ( stateCount > 0 ) {
            graphics.setColor(Color.BLUE);

            final int mask = 1 << wireIndex;
            int previousState = wireStates[ (oldestStatePtr + windowOffset) % STATES ] & mask;
            long previousTimestamp = timestamps[ (oldestStatePtr + windowOffset) % STATES ];
            int previousY = previousState == 0 ? yLow : yHigh;
            double previousX = originX;

            final int len = windowSize >= stateCount ? stateCount : windowSize;
            final long totalCycles = Math.abs( timestamps[ (oldestStatePtr + windowOffset + len -1 ) % STATES ] - previousTimestamp );

            double xIncrement = xAxisWidth / (double) totalCycles;

            for ( int i = 1 , ptr = (oldestStatePtr + windowOffset+1) % STATES ; i < len ; i++ , ptr = (ptr+1) % STATES ) 
            {
                final int currentState = wireStates[ ptr ] & mask;
                final long currentTimestamp = timestamps[ptr];
                final long cycleDelta = Math.abs( currentTimestamp - previousTimestamp );            
                final double currentX = previousX + (cycleDelta*xIncrement);

                graphics.drawLine( (int) previousX , previousY , (int) currentX , previousY );

                final int currentY;
                if ( currentState == previousState ) 
                {
                    currentY = previousY;

                } else 
                {
                    currentY = ( currentState == 0 ) ? yLow : yHigh;
                    graphics.drawLine( (int) currentX, previousY , (int) currentX , currentY ); 
                }
                previousState = currentState;
                previousTimestamp = currentTimestamp;
                previousX = currentX;
                previousY = currentY;
            }
        }
    }

    private void drawCentered(String text,Rectangle rectangle,Graphics2D graphics)
    {
        final FontMetrics fm = graphics.getFontMetrics();
        final int x = rectangle.x + (rectangle.width - fm.stringWidth(text)) / 2;
        final int y = rectangle.y + fm.getAscent() + ( rectangle.height - fm.getHeight() )/ 2;
        graphics.drawString(text, x,y);
    }

    private boolean isDisplayed;
    private Component locationPeer;

    @Override
    public void setLocationPeer(Component frame) {
        this.locationPeer = frame;
    }

    @Override
    public Component getLocationPeer() {
        return locationPeer;
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
        repaint();
    }

    @Override
    public boolean isRefreshAfterTick() {
        return isDisplayed;
    }    
}