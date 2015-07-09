package de.codesourcery.j6502.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Graphics;

import javax.swing.JInternalFrame;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import de.codesourcery.j6502.emulator.D64File;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.D64File.BAMEntry;
import de.codesourcery.j6502.ui.WindowLocationHelper.IDebuggerView;
import de.codesourcery.j6502.utils.CharsetConverter;

public class BlockAllocationPanel extends JPanel implements IDebuggerView {

	private Component peer;
	private boolean isDisplayed;
	private D64File disk;
	
	public BlockAllocationPanel() 
	{
		setPreferredSize( new Dimension(200, 200 ) );
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
	
	public void setDisk(D64File disk) {
		this.disk = disk;
		if ( peer instanceof JInternalFrame)
		{
			final JInternalFrame frame = (JInternalFrame) peer;
			final String diskName = CharsetConverter.petToASCII( disk.getBAM().getDiskName() );
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
	
	@Override
	protected void paintComponent(Graphics g) 
	{
		g.setColor( Color.GRAY );
		g.fillRect(0,0,getWidth(),getHeight());
		
		if ( disk == null ) {
			return;
		}
		
		final int margin = 10;
		final int w = getWidth()-2*margin;
		final int h = getHeight()-2*margin;
		
		final int tracks = 35;
		final int maxSectorsPerTrack = 21;
		
		final int xScale = (int) Math.max( 1f , w/ (float) maxSectorsPerTrack ); 
		final int yScale = (int) Math.max( 1f , h/ (float) tracks );
		
		for ( BAMEntry entry : disk.getBAM().getAllocationMap() ) 
		{
			final int firstSectorNo = (entry.trackNo-1)*maxSectorsPerTrack;
			final boolean[] allocationMap = entry.freeSectorsMap;
			for ( int i = 0 ; i < allocationMap.length ; i++ ) 
			{
				if ( i == entry.sectorsOnTrack ) {
					break;
				}
				final int sectorNo = firstSectorNo+i;
				final int row = sectorNo/maxSectorsPerTrack;
				final int column = sectorNo - row*maxSectorsPerTrack;
				
				final int x0 = margin + column*xScale;
				final int y0 = margin + row*yScale;
				
				if ( allocationMap[i] ) {
					g.setColor(Color.RED);
				} else {
					g.setColor(Color.GREEN);
				}
				g.fillRect( x0 , y0 , xScale , yScale );
				g.setColor(Color.WHITE);
				g.drawRect( x0 , y0 , xScale , yScale );				
			}
		}
		
		g.setColor( Color.WHITE );
	}
}