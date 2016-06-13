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
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Random;
import javax.swing.JFrame;
import javax.swing.JPanel;
import javax.swing.Timer;

import de.codesourcery.j6502.emulator.Bus;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.ui.WindowLocationHelper.IDebuggerView;

public class BusAnalyzer extends JPanel implements IDebuggerView
{
    protected static final int MAX_STATES = 256;

    protected static final int KEY_REPEAT_DELAY = 100;

    private final Object LOCK = new Object();

    private static final float STROKE_LINE_WIDTH = 4f;
    private static final int STROKE_GAP_WIDTH = 8;

    private static final Stroke DASHED_LINE = new BasicStroke(1.0f,                      // Width
            BasicStroke.CAP_SQUARE,    // End cap
            BasicStroke.JOIN_MITER,    // Join style
            10.0f,                     // Miter limit
            new float[] {STROKE_LINE_WIDTH,STROKE_GAP_WIDTH}, // Dash pattern
            0.0f);                     // Dash phase

    // @GuardedBy( LOCK )
    private BusStateContainer busStateContainer;

    private int maxWireNameWidthPixels;

    private int windowOffset = 0; // offset relative to oldestStatePtr
    private int windowSize = MAX_STATES; // number of states to render
    
    private boolean invert;

    public static void main(String[] args)
    {
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
        final BusStateContainer container = new BusStateContainer( bus );

        final JFrame frame = new JFrame("test");
        frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

        final BusAnalyzer analyzer = new BusAnalyzer();
        frame.getContentPane().add( analyzer );

        frame.pack();
        frame.setLocationRelativeTo( null );
        frame.setVisible(true);

        analyzer.setBusStateContainer( container );

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
                container.sampleBus();
            }
        }
        analyzer.repaint();
    }

    private final KeyAdapter keyAdapter = new MyKeyAdapter();

    protected final class MyKeyAdapter extends KeyAdapter
    {
    	private final Map<Integer,Long> pressedKeys = new HashMap<>();

    	private final Timer timer;

    	public MyKeyAdapter()
    	{
    		timer = new Timer( 20  , event ->
    		{
    			final long now = event.getWhen();
    			final Long nowL = now;
    			for ( Entry<Integer, Long> entry : pressedKeys.entrySet() )
    			{
    				if ( (now - entry.getValue() ) > KEY_REPEAT_DELAY )
    				{
    					entry.setValue( nowL );
    					typed( entry.getKey() );
    				}
    			}
    		});
    		timer.start();
    		Runtime.getRuntime().addShutdownHook( new Thread( timer::stop ) );
    	}

    	@Override
    	public void keyPressed(KeyEvent e)
    	{
    		if ( ! pressedKeys.containsKey( e.getKeyCode() ) ) {
    			pressedKeys.put( e.getKeyCode() , e.getWhen() );
    		}
    	}

    	@Override
    	public void keyReleased(KeyEvent e)
    	{
    		pressedKeys.remove( e.getKeyCode() );
    		typed( e.getKeyCode() );
    	}

    	private void typed(int vkKeyCode)
    	{
    		final int stateCount = busStateContainer.sampleCount();
    		boolean repaint = false;
    		switch( vkKeyCode )
    		{
    			case KeyEvent.VK_LEFT:
    				repaint = when( windowOffset > 0 ).then( () -> windowOffset-- );
    				break;
    			case KeyEvent.VK_RIGHT:
    				repaint = when( (windowOffset + windowSize) < stateCount ).then( () -> windowOffset++ );
    				break;
    			case KeyEvent.VK_PLUS:
    				repaint = when( windowSize > 3 ).then( () -> {
    					if ( windowSize > stateCount ) {
    						windowSize = stateCount-1;
    					} else {
    						windowSize--;
    					}
    				});
    				break;
    			case KeyEvent.VK_MINUS:
    				repaint = when( windowSize < stateCount ).then( () -> windowSize++ );
    				break;
    			case KeyEvent.VK_END:
    				repaint = when( (windowOffset+windowSize) < stateCount ).then( () -> windowOffset = stateCount - windowSize);
    				break;
    			case KeyEvent.VK_HOME:
    				repaint = when( (windowOffset+windowSize) < stateCount ).then( () -> windowOffset = stateCount - windowSize);
    				break;
    		}

    		if ( repaint ) {
    			BusAnalyzer.this.repaint();
    		}
    	}

    	private ConditionBuilder when(boolean test)
    	{
    		return new ConditionBuilder( test );
    	}
    }

    protected static final class ConditionBuilder
    {
    	private final boolean result;
    	public ConditionBuilder(boolean result) { this.result = result; }
    	public boolean then(Runnable r) { if ( result ) { r.run(); } return result; }
    }

    public BusAnalyzer()
    {
        setBackground( Color.BLACK );
        setForeground( Color.GREEN );
        setPreferredSize( new Dimension(10 , 10 ) );
        setFocusable(true);
        setRequestFocusEnabled( true );

        addKeyListener( keyAdapter );
    }

    public void setBusStateContainer(BusStateContainer busStateContainer)
    {
    	synchronized ( LOCK)
    	{
    		this.busStateContainer = busStateContainer;
    	}
    	repaint();
	}

    public void reset()
    {
        windowOffset = 0;
        windowSize = BusStateContainer.MAX_STATES;
        busStateContainer.reset();
    }

    @Override
    protected void paintComponent(Graphics graphics)
    {
    	final Graphics2D g = (Graphics2D) graphics;

        super.paintComponent(g);

        if ( busStateContainer != null )
        {
        	synchronized( busStateContainer )
        	{

        		final FontMetrics fm = getGraphics().getFontMetrics();
        		final String[] wireNames = busStateContainer.getBus().getWireNames();
        		int maxWidth = 0;
        		final int busWidth = busStateContainer.getBus().getWidth();
        		for ( int i = 0 ; i < busWidth; i++ )
        		{
        		    int thisWidth = fm.stringWidth( wireNames[i] );
        		    maxWidth = Math.max( maxWidth , thisWidth  );
        		}
        		this.maxWireNameWidthPixels = maxWidth;

        		final int wireCount = busStateContainer.getBus().getWidth();
        		final int heightPerWire = getHeight() / wireCount;

        		final Rectangle r = new Rectangle();
        		r.width = getWidth();
        		r.height = heightPerWire;
        		for ( int i = 0 ; i < wireCount ; i++ )
        		{
        			renderWire(i,r, g);
        			r.y += heightPerWire;
        		}

        		final String msg = "Displaying events "+windowOffset+" - "+(windowOffset+windowSize)+" from "+busStateContainer.sampleCount()+" total";
        		final int width = fm.stringWidth( msg );
        		final int x = Math.max(0 , getWidth()/2 - width/2 );
        		final Rectangle box = new Rectangle( x , 0 , width , 20 );
   				g.setColor( Color.WHITE );
        		g.fill(box);

        		g.setColor( Color.BLACK);
        		g.draw(box);
        		drawCentered( msg , box , g );
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
        drawCentered( busStateContainer.getBus().getWireNames()[ wireIndex ] , tmp , graphics );

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
        final BusStateContainer container = busStateContainer;
        final int stateCount = container.sampleCount();
        if ( stateCount > 1 )
        {
        	final int oldestStatePtr = container.firstPtr();

            graphics.setColor(Color.BLUE);

            final int mask = 1 << wireIndex;
            int previousState = container.state( (oldestStatePtr + windowOffset) % MAX_STATES ) & mask;
            long previousTimestamp = container.timestamp( (oldestStatePtr + windowOffset) % MAX_STATES );
            int previousY = previousState == 0 ? yLow : yHigh;
            double previousX = originX;

            final int len = windowSize >= stateCount ? stateCount : windowSize;
            final long totalCycles = Math.abs( container.timestamp( (oldestStatePtr + windowOffset + len - 1 ) % MAX_STATES ) - previousTimestamp );

            double xIncrement = xAxisWidth / (double) totalCycles;

            for ( int i = 1 , ptr = (oldestStatePtr + windowOffset+1) % MAX_STATES ; i < len ; i++ , ptr = (ptr+1) % MAX_STATES )
            {
                final int currentState = container.state( ptr ) & mask;
                final long currentTimestamp = container.timestamp( ptr );
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
    
    @Override
    public String getIdentifier() {
        return "IEC bus view";
    }    
}