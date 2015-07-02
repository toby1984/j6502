package de.codesourcery.j6502.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Stroke;
import java.awt.event.ActionEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import javax.swing.AbstractAction;
import javax.swing.JComponent;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.KeyStroke;

import de.codesourcery.j6502.emulator.IECBus.StateSnapshot;
import de.codesourcery.j6502.ui.WindowLocationHelper.ILocationAware;

public abstract class BusPanel extends JPanel implements ILocationAware
{
	protected static final Stroke DASHED_FAT = new BasicStroke(3, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);
	protected static final Stroke DASHED_SLIM = new BasicStroke(1, BasicStroke.CAP_BUTT, BasicStroke.JOIN_BEVEL, 0, new float[]{9}, 0);

	private final int RIGHT_BORDER = 20; // right border in pixels
	private final int RESERVED_WIDTH = 100; // reserved width left of chart in pixels

	private final List<Lane> lanes = new ArrayList<>();

	private Component peer;

	private DisplayRange displayRange = null;

	private List<StateSnapshot> states = new ArrayList<>();
	private int firstSelectionX;
	private int currentSelectionX=RESERVED_WIDTH;
	private SelectionMode selectionMode = SelectionMode.NONE;

	protected Long cycleCountOverride;
	protected Long firstCycleOffset;
	private String title;

	protected final class DisplayRange
	{
		public int firstState;
		public int statesToDisplay;

		public DisplayRange(int firstState,int statesToDisplay) {
			if ( firstState < 0 ) {
				throw new IllegalArgumentException("First state must be >= 0");
			}
			if ( statesToDisplay < 1 ) {
				throw new IllegalArgumentException("statesToDisplay must be >= 1");
			}
			this.firstState = firstState;
			this.statesToDisplay = statesToDisplay;
		}
		
		public void zoomOut() {
			statesToDisplay++;
		}

		public void zoomIn()
		{
			if ( statesToDisplay > 1 ) {
				statesToDisplay--;
			}
		}

		public void rollLeft()
		{
			if ( firstState > 0 ) {
				firstState--;
			}
		}

		public void rollRight() {
			firstState++;
		}
	}

	protected static abstract class Lane
	{
		private final String title;

		public Lane(String title) {
			this.title = title;
		}

		protected abstract boolean getState(StateSnapshot state);

		public void renderTitle(int x,int y,int laneHeight,Graphics2D g,int maxX)
		{
			LineMetrics metrics = g.getFontMetrics().getLineMetrics( title , g );
			Rectangle2D bounds = g.getFontMetrics().getStringBounds( title , g );

			/*
			 * +---------------------------
			 *
			 *        1  --------------
			 * TITLE
			 *        0  --------------
			 *
			 * +---------------------------
			 */
			int third = laneHeight / 3;

			int cy = y + laneHeight/2;
			int fontCenterY = (int) (cy - bounds.getHeight()/2 + metrics.getAscent());
			g.drawString( title , x , fontCenterY );

			int hiLevelY = y + third;
			int loLevelY = y + 2*third;

			final int offset =  10 + (int) (x+bounds.getWidth());

			metrics = g.getFontMetrics().getLineMetrics( "1" , g );
			bounds = g.getFontMetrics().getStringBounds( "1" , g );
			fontCenterY = (int) (hiLevelY - bounds.getHeight()/2 + metrics.getAscent());
			g.drawString( "1" , offset , (int) (hiLevelY+metrics.getDescent()) );

			metrics = g.getFontMetrics().getLineMetrics( "0" , g );
			bounds = g.getFontMetrics().getStringBounds( "0" , g );
			fontCenterY = (int) (loLevelY - bounds.getHeight()/2 + metrics.getAscent());
			g.drawString( "0" , offset , fontCenterY );

			final Stroke oldStroke = g.getStroke();
			g.setStroke( DASHED_SLIM );
			Color oldColor = g.getColor();
			g.setColor(Color.BLUE);
			g.drawLine( offset+15,hiLevelY , maxX , hiLevelY );
			g.drawLine( offset+15,loLevelY , maxX , loLevelY );

			g.setColor(oldColor);
			g.setStroke(oldStroke);
		}

		public void render(int x,int y,int laneHeight,double cycleWidthInPixels,StateSnapshot previousState,StateSnapshot state,Graphics2D g)
		{
			int third = laneHeight / 3;
			int hiLevelY = y + third;
			int loLevelY = y + 2*third;

			final boolean stateNow = getState( state );
			final boolean stateBefore = previousState == null ? false : getState(previousState);

			final int yNow = stateNow ? hiLevelY : loLevelY;

			final Color oldColor = g.getColor();

			g.setColor(Color.RED);
			g.drawLine(x,yNow,(int) (x+cycleWidthInPixels),yNow);

			if ( previousState != null )
			{
				if (  stateNow != stateBefore  ) {
					final int previousY = stateBefore ? hiLevelY : loLevelY;
					g.drawLine( x , previousY , x , yNow );
				}
				final int yPrevious = stateBefore ? hiLevelY : loLevelY;
				long cycleDelta = Math.abs( state.cycle - previousState.cycle );
				g.drawLine( (int) (x - cycleDelta*cycleWidthInPixels) , yPrevious , x , yPrevious );
			}
			g.setColor( oldColor );
		}
	}

	protected static enum SelectionMode {
		MIN,MAX,NONE;
	}

	public BusPanel(String title)
	{
		this.title = title;

		lanes.add( new Lane("ATN") { @Override protected boolean getState(StateSnapshot state) { return state.atn; } });
		lanes.add( new Lane("CLOCK") { @Override protected boolean getState(StateSnapshot state) { return state.clk; } });
		lanes.add( new Lane("DATA") { @Override protected boolean getState(StateSnapshot state) { return state.data; } });
		setMinimumSize( new Dimension(RESERVED_WIDTH+100*3 , 6*30 ) );
		setFocusable(true);
		setRequestFocusEnabled( true );

		onKey( KeyStroke.getKeyStroke( 'z') , () -> {
			String result = JOptionPane.showInputDialog("Number of cycles to display", "0" );
			try {
				if ( result != null ) {
					cycleCountOverride = Long.parseLong( result.trim() );
				} else {
					cycleCountOverride = null;
				}
			} catch(Exception ex) {
				cycleCountOverride = null;
			}
			repaint();
		});

		onKey( KeyStroke.getKeyStroke( 'a') , () -> {
			String result = JOptionPane.showInputDialog("Enter cycle", "0" );
			try {
				if ( result != null ) {
					firstCycleOffset = Long.parseLong( result.trim() );
				} else {
					firstCycleOffset = null;
				}
			} catch(Exception ex) {
				firstCycleOffset = null;
			}
			repaint();
		});

		onKey( KeyStroke.getKeyStroke( '+') , () -> {
			maybeCreateDisplayRange();
			if ( displayRange != null ) {
				displayRange.zoomOut();
			}
			repaint();
		});

		onKey( KeyStroke.getKeyStroke( '-') , () -> {
			maybeCreateDisplayRange();
			if ( displayRange != null ) {
				displayRange.zoomIn();
			}
			repaint();
		});
		
		onKey( KeyStroke.getKeyStroke( 'n') , () -> {
			maybeCreateDisplayRange();
			if ( displayRange != null ) {
				displayRange.rollRight();
			}
			repaint();
		});		
		
		onKey( KeyStroke.getKeyStroke( 'p') , () -> {
			maybeCreateDisplayRange();
			if ( displayRange != null ) {
				displayRange.rollLeft();
			}
			repaint();
		});			


		addKeyListener( new KeyAdapter()
		{
			@Override
			public void keyReleased(KeyEvent e)
			{
				if ( e.getKeyCode() == KeyEvent.VK_ESCAPE )
				{
					displayRange = null;
				}
				else if ( e.getKeyCode() == KeyEvent.VK_LEFT )
				{
					maybeCreateDisplayRange();
					if ( displayRange != null ) {
						displayRange.rollLeft();
					}
					repaint();
				} else if ( e.getKeyCode() == KeyEvent.VK_RIGHT ) {
					maybeCreateDisplayRange();
					if ( displayRange != null ) {
						displayRange.rollRight();
					}
					repaint();
				}
			}

		});
		addMouseListener( new MouseAdapter()
		{
			@Override
			public void mouseClicked(MouseEvent e)
			{
				if ( e.getButton() == MouseEvent.BUTTON3 ) {
					if ( selectionMode == SelectionMode.NONE ) {
						return;
					}
					selectionMode = SelectionMode.NONE;
					repaint();
				}

				if ( e.getButton() == MouseEvent.BUTTON1 )
				{
				 if ( selectionMode == SelectionMode.NONE) {
					selectionMode = SelectionMode.MIN;
				 } else if ( selectionMode == SelectionMode.MIN ) {
					 firstSelectionX = currentSelectionX;
					 selectionMode = SelectionMode.MAX;
					 currentSelectionX = e.getX();
					 repaint();
				 }
				 else if ( selectionMode == SelectionMode.MAX )
				 {
					 selectionMode = SelectionMode.NONE;
					 int first = firstSelectionX-RESERVED_WIDTH;
					 int second = currentSelectionX-RESERVED_WIDTH;
					 int min = Math.min(first,second);
					 int max = Math.max(first,second);
					 double percentageOne = min / (double) ( getWidth() - RESERVED_WIDTH - RIGHT_BORDER); // 0...1
					 double percentageTwo = max / (double) ( getWidth() - RESERVED_WIDTH - RIGHT_BORDER); // 0...1
					 System.out.println("Selected percentage: "+percentageOne+" - "+percentageTwo);
					 repaint();
				 }
				}
			}
		});
		addMouseMotionListener( new MouseMotionListener() {

			@Override
			public void mouseMoved(MouseEvent e)
			{
				if ( selectionMode != SelectionMode.NONE )
				{
					final int mouseX = e.getX();
					if ( mouseX >= RESERVED_WIDTH && mouseX <= (getWidth()-RIGHT_BORDER ) )
					{
						currentSelectionX = mouseX;
					}
					repaint();
				}
			}

			@Override
			public void mouseDragged(MouseEvent e) { }
		});
	}

	protected void maybeCreateDisplayRange() {
		if ( displayRange == null && ! states.isEmpty() )
		{
			displayRange = new DisplayRange(0,states.size());
		}
	}

	protected static final AtomicLong KEY_ID = new AtomicLong(1);

	private void onKey(KeyStroke key,Runnable action)
	{
		final String text = KEY_ID.incrementAndGet()+"";
		getInputMap(JComponent.WHEN_FOCUSED).put(key, text );

		AbstractAction aa = new AbstractAction() {

			@Override
			public void actionPerformed(ActionEvent e) {
				action.run();
			}
		};
		getActionMap().put( text ,aa);
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
	protected void paintComponent(Graphics graphics)
	{
		// clear screen
		final Graphics2D g = (Graphics2D) graphics;
		super.paintComponent(g);

		final int w = (int) (getWidth()*0.9f);
		final int h = getHeight();

		// calculate width of display area
		final int remainingWidth = w - RESERVED_WIDTH - RIGHT_BORDER;

		// get state snapshots from bus
		states = getBusSnapshots();

		// trim down the list of bus state objects to those that fall within the
		// firstCycle/lastCycle range

		final List<StateSnapshot> statesInRange = new ArrayList<>( states.size() );

		int firstStateOffset=displayRange ==null ? 0 : displayRange.firstState;
		for (int i = 0; i < states.size(); i++) {
			StateSnapshot s = states.get(i);
			if ( displayRange == null ) {
				statesInRange.add( s );
			} else if ( i >= displayRange.firstState && i <= (displayRange.firstState+displayRange.statesToDisplay) ) {
				statesInRange.add( s );
			}
		}

		final StateSnapshot firstSnapshot = statesInRange.isEmpty() ? null : statesInRange.get(0);
		final StateSnapshot lastSnapshot = statesInRange.isEmpty() ? null : statesInRange.get( statesInRange.size()-1 );

		final long firstCycle = firstSnapshot != null ? firstSnapshot.cycle : 0;
		final long lastCycle = lastSnapshot != null ? lastSnapshot.cycle : 0;

		final long cycleDelta = Math.max( lastCycle - firstCycle , 1 );

		// calculate width/length in pixels for an individual cycle
		double cycleWidth;
		if ( cycleCountOverride != null ) {
			cycleWidth = remainingWidth/ (double) cycleCountOverride;
		} else {
			cycleWidth = remainingWidth/ (double) cycleDelta;
		}
		cycleWidth = Math.max( cycleWidth , 0.00001);
		// render vertical lines every 100 cycles
		g.setColor(Color.GRAY);
//		g.setStroke( DASHED_SLIM );

		final long delta;
		if ( firstCycleOffset != null && firstSnapshot != null ) {
			delta = firstCycleOffset- firstSnapshot.cycle;
		} else {
			delta = 0;
		}
		long currentCycle = firstCycle + delta;
		if ( 100*cycleWidth >= 10 ) // only draw cycle tick lines if there's a reasonable distance between them 
		{
			for ( double x = RESERVED_WIDTH ; x < w ; ) {
				g.drawLine( (int) x , 0 , (int) x , h );
				g.drawString( Long.toString(currentCycle) , (int) x , 35 );
				currentCycle += 100;
				x += (100*cycleWidth);
			}
		}

		// render title
		final double timeInSeconds = cycleDelta*1/1000000f;
		g.setColor(Color.RED);
		final DecimalFormat DF = new DecimalFormat("##0.0######");
		final String titleString = title+": "+DF.format(timeInSeconds)+"s ("+cycleDelta+" cycles ("+statesInRange.size()+" out of "+states.size()+" states starting @ "+firstStateOffset+" , first: "+firstCycle+" , last: "+lastCycle+")";
		Rectangle2D stringBounds = g.getFontMetrics().getStringBounds( titleString , g );
		g.drawString( titleString , (int) ( w/2- stringBounds.getWidth()/2) , 10 );

		// render lanes plus some free space below the last one
		// so we can output the state names there
		final int laneHeight = h/(lanes.size()+1); // +1 lane for state labels

		int y = 0;
		for ( Lane l : lanes ) {
			l.renderTitle( 3 , y , laneHeight , g, w );
			y += laneHeight;
		}

		final int stateLineY = (lanes.size() * laneHeight);

		// we'll stagger the state names so they don't overlap
		// too easily
		int stateLineIdx = 0;
		final int[] stateLineOffset = new int[] {
				stateLineY ,
				stateLineY + 13 ,
				stateLineY + 26 ,
				stateLineY + 39 ,
				stateLineY + 52
		};
		
		StateSnapshot previousState = null;

		for ( final StateSnapshot currentState : statesInRange )
		{
			long offset = currentState.cycle - firstCycle + delta ;
			final int x = (int) (RESERVED_WIDTH + (offset * cycleWidth));
			// mark state changes with vertical, dashed line
			if ( previousState == null || ! previousState.equals( currentState) )
			{
				Color oldColor = g.getColor();
				Stroke oldStroke = g.getStroke();
				g.setColor( Color.GREEN);
				
				if ( currentState.msg != null ) {
					g.drawString( currentState.msg, x , stateLineOffset[ stateLineIdx++] );
				}
				
				g.setStroke( DASHED_SLIM );
				g.drawLine( x , getHeight() , x , 0 );
				stateLineIdx = stateLineIdx % stateLineOffset.length;
				g.setColor(oldColor);
				g.setStroke( oldStroke );
			}
			y = 0;
			for ( Lane l : lanes )
			{
				l.render( x , y , laneHeight , cycleWidth, previousState, currentState , g );
				y += laneHeight;
			}
			previousState=currentState;
		}

		switch( selectionMode )
		{
			case MIN:
				g.setColor(Color.RED);
				g.drawLine( currentSelectionX , 0 , currentSelectionX , getHeight() );
				break;
			case MAX:
				g.setColor(Color.RED);
				g.setXORMode( Color.BLACK );
				int min = Math.min(firstSelectionX,currentSelectionX);
				int max = Math.max(firstSelectionX,currentSelectionX);
				g.fillRect( min , 0 , max-min, getHeight() );
				g.setPaintMode();

				g.drawLine( firstSelectionX , 0 , firstSelectionX , getHeight() );
				g.drawLine( currentSelectionX , 0 , currentSelectionX , getHeight() );
				break;
			default:
		}
	}

	protected abstract List<StateSnapshot> getBusSnapshots();
}