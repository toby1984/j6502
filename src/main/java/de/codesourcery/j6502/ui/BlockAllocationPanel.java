package de.codesourcery.j6502.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Point;
import java.awt.event.MouseAdapter;
import java.util.List;

import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.ToolTipManager;

import de.codesourcery.j6502.emulator.D64File;
import de.codesourcery.j6502.emulator.D64File.BAMEntry;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.ui.WindowLocationHelper.IDebuggerView;

public class BlockAllocationPanel extends JPanel implements IDebuggerView {

	private Component peer;
	private boolean isDisplayed;
	private D64File disk;
	
	private static final int TRACKS_TO_DISPLAY = 35;
	private static final int MAX_SECTORS_PER_TRACK = 21;
	
	private static final int MARGIN = 10;
	
	private int w;
	private int h;
	private int xScale;
	private int yScale;
	
	
	private final Point lastMousePosition = new Point();
	
	private final MouseAdapter mouseListener = new MouseAdapter()
	{
		public void mouseMoved(java.awt.event.MouseEvent e) 
		{
			lastMousePosition.setLocation( e.getPoint() );
			repaint();
		}
	};
	
	public BlockAllocationPanel() 
	{
		setPreferredSize( new Dimension(200, 200 ) );
		addMouseMotionListener( mouseListener );
		addMouseListener( mouseListener );
		ToolTipManager.sharedInstance().registerComponent( this );
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
	
	public void setDisk(D64File disk) 
	{
		this.disk = disk;
		if ( peer instanceof JInternalFrame)
		{
			final JInternalFrame frame = (JInternalFrame) peer;
			final String diskName = disk.getDiskNameInASCII();
			final String title = diskName+" - "+( disk == null ? "<no disk>" : disk.getFreeSectorCount()+" blocks free."); 
			SwingUtilities.invokeLater( () -> frame.setTitle( title ) );
		}
		repaint();
	}
	
	@Override
	public void refresh(Emulator emulator) {
	}
	
	@Override
	public boolean isRefreshAfterTick() {
		return false;
	}
	
	private void update() 
	{
		w = getWidth()-2*MARGIN;
		h = getHeight()-2*MARGIN;
		
		xScale = (int) Math.max( 1f , w/ (float) MAX_SECTORS_PER_TRACK ); 
		yScale = (int) Math.max( 1f , h/ (float) TRACKS_TO_DISPLAY );
	}
	
	@Override
	protected void paintComponent(Graphics g) 
	{
		update();
		
		g.setColor( Color.GRAY );
		g.fillRect(0,0,getWidth(),getHeight());
		
		if ( disk == null ) {
			return;
		}
		
		final List<BAMEntry> list = disk.getAllocationMap();
		list.sort( (a,b) -> Integer.compare( a.trackNo , b.trackNo  ) );
		
		String tooltipText = null;
		for ( BAMEntry entry : list ) 
		{
			for ( int i = 0 , max = entry.sectorsOnTrack() ; i < max ; i++ ) 
			{
				final int row = (entry.trackNo-1);
				final int column = i;
				
				final int x0 = MARGIN + column*xScale;
				final int y0 = MARGIN + row*yScale;
				
				if ( lastMousePosition.x >= x0 && lastMousePosition.x < (x0+xScale ) &&
					  lastMousePosition.y >= y0 && lastMousePosition.y < (y0+yScale ) ) 
				{
					tooltipText = "Track "+entry.trackNo+" , sector "+(i+1);
				} 
				
				if ( entry.isAllocated( i ) ) 
				{
					g.setColor(Color.RED);
				} else {
					g.setColor(Color.GREEN);
				}
				g.fillRect( x0 , y0 , xScale , yScale );
				g.setColor(Color.WHITE);
				g.drawRect( x0 , y0 , xScale , yScale );				
			}
		}
		setToolTipText( tooltipText );
		
		g.setColor( Color.WHITE );
	}
}