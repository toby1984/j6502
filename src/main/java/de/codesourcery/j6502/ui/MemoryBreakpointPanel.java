package de.codesourcery.j6502.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.KeyListener;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.MemoryBreakpointHelper;
import de.codesourcery.j6502.emulator.MemoryBreakpointHelper.MemoryBreakpoint;
import de.codesourcery.j6502.ui.WindowLocationHelper.IDebuggerView;

public class MemoryBreakpointPanel extends JPanel implements IDebuggerView 
{
	private static final int COL_ADDRESS = 0;
	private static final int COL_ENABLED = 1;
	private static final int COL_READ= 2;
	private static final int COL_WRITE = 3;

	private static final int COLUMN_COUNT = 4;

	private final Map<String,String> configProperties = new HashMap<>();
	private Component peer;
	private boolean isDisplayed;
	private final Emulator emulator;

	private final MyModel tableModel = new MyModel();

	protected static final class BreakpointEntry 
	{
		public final MemoryBreakpoint breakpoint;

		public boolean isHit;
		public boolean isEnabled; 

		public BreakpointEntry(MemoryBreakpoint breakpoint) {
			this.breakpoint = breakpoint;
			this.isEnabled = true;
		}

		public boolean hasBreakpoint(MemoryBreakpoint bp) {
			return bp.equals(breakpoint);
		}
	}

	protected final class MyModel extends AbstractTableModel {

		private final List<BreakpointEntry> model = new ArrayList<>();

		public void addNewBreakpoint(int address) 
		{
			if ( model.stream().anyMatch( bp -> bp.breakpoint.address == address ) ) {
				return;
			}
			add( new MemoryBreakpoint( address , MemoryBreakpointHelper.BREAKPOINT_TYPE_READ | MemoryBreakpointHelper.BREAKPOINT_TYPE_WRITE ) );
		}
		
		@Override
		public int getRowCount() {
			return model.size();
		}

		@Override
		public int getColumnCount() {
			return COLUMN_COUNT;
		}

		public void add(MemoryBreakpoint bp) 
		{
			int index = 0;
			while ( index < model.size() && model.get(index).breakpoint.address < bp.address ) {
				index++;
			}
			if ( index == model.size() ) {
				model.add( new BreakpointEntry(bp) );
			} else {
				model.add( index , new BreakpointEntry(bp) );
			}

			doWithMemoryBreakpointHelper( mbph -> mbph.setMemoryBreakpoint( bp ));

			fireTableRowsInserted( index,index );
		}

		private void doWithMemoryBreakpointHelper(Consumer<MemoryBreakpointHelper> consumer) 
		{
			synchronized(emulator) 
			{
				consumer.accept( emulator.getCPU().getMemoryBreakpointHelper() );
			}
		}

		public void remove(MemoryBreakpoint bp) 
		{
			int index = 0;
			while ( index < model.size() ) 
			{
				if ( model.get(index).hasBreakpoint( bp) ) 
				{
					model.remove(index);
					doWithMemoryBreakpointHelper( mbph -> mbph.removeMemoryBreakpoint( bp ) );
					if ( model.isEmpty() ) {
						fireTableStructureChanged();
					} else {
						fireTableRowsDeleted( index,index );
					}
					return;
				}
				index++;
			}
		}		

		@Override
		public String getColumnName(int columnIndex) 
		{
			switch(columnIndex) {
				case COL_ADDRESS:
					return "Address";
				case COL_ENABLED:
					return "Enabled?";					
				case COL_READ:
					return "Read?";
				case COL_WRITE:
					return "Write?";
				default:
					throw new RuntimeException("Invalid column index "+columnIndex);
			}
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			switch(columnIndex) {
				case COL_ADDRESS:
					return String.class;
				case COL_ENABLED:
					return Boolean.class;
				case COL_READ:
					return Boolean.class;
				case COL_WRITE:
					return Boolean.class;
				default:
					throw new RuntimeException("Invalid column index "+columnIndex);
			}
		}

		@Override
		public boolean isCellEditable(int rowIndex, int columnIndex) 
		{
			final BreakpointEntry entry = model.get(rowIndex);			
			switch(columnIndex) 
			{
				case COL_ADDRESS:
					return false;
				case COL_ENABLED:
				case COL_READ:
					return ! entry.breakpoint.isRead() || entry.breakpoint.isWrite();
				case COL_WRITE:
					return ! entry.breakpoint.isWrite() || entry.breakpoint.isRead();
				default:
					throw new RuntimeException("Invalid column index "+columnIndex);
			}			
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) 
		{
			final BreakpointEntry entry = model.get(rowIndex);
			switch(columnIndex) {
				case COL_ADDRESS:
					return "$"+StringUtils.leftPad( Integer.toHexString( entry.breakpoint.address) , 4 , '0');
				case COL_ENABLED:
					return entry.isEnabled; 
				case COL_READ:
					return entry.breakpoint.isRead();
				case COL_WRITE:
					return entry.breakpoint.isWrite();
				default:
					throw new RuntimeException("Invalid column index "+columnIndex);
			}
		}

		@Override
		public void setValueAt(Object aValue, int rowIndex, int columnIndex) 
		{
			final BreakpointEntry oldEntry = model.get(rowIndex);
			final MemoryBreakpoint bp = oldEntry.breakpoint;

			final boolean isEnabled = columnIndex == COL_ENABLED ? (Boolean) aValue : oldEntry.isEnabled;
			boolean isRead = bp.isRead();
			boolean isWrite = bp.isWrite();
			switch(columnIndex) 
			{
				case COL_ENABLED: // handled above already
					break;
				case COL_READ:
					isRead = (Boolean) aValue;
					break;
				case COL_WRITE:
					isWrite = (Boolean) aValue;
					break;
				default:
					throw new RuntimeException("Invalid column index "+columnIndex);
			}

			final int mask = ( isRead  ? MemoryBreakpointHelper.BREAKPOINT_TYPE_READ  : 0 ) | 
					( isWrite ? MemoryBreakpointHelper.BREAKPOINT_TYPE_WRITE : 0 );

			final MemoryBreakpoint newBp = new MemoryBreakpoint( bp.address , mask );

			doWithMemoryBreakpointHelper( mbph -> 
			{
				mbph.removeMemoryBreakpoint( bp );
				if ( isEnabled ) {
					mbph.setMemoryBreakpoint( newBp );
				}				
			});

			final BreakpointEntry newEntry = new BreakpointEntry(newBp);
			newEntry.isHit = oldEntry.isHit;
			newEntry.isEnabled = isEnabled;
			model.set( rowIndex , newEntry );
		}

		public void updateBreakpointsHit(List<MemoryBreakpoint> hit) 
		{
			int lowChanged = -1;
			int hiChanged = -1;
			for ( int j = 0 , len2 = this.model.size() ; j < len2 ; j++ ) 
			{
				final BreakpointEntry candidate= this.model.get(j);
				boolean isHit = false;
				for ( int i = 0 , len = hit.size() ; i < len ; i++ ) 
				{
					final MemoryBreakpoint hitBreakpoint = hit.get(i);
					if ( candidate.breakpoint.equals( hitBreakpoint ) ) {
						isHit = true;
						break;
					}
				}
				if ( isHit != candidate.isHit ) 
				{
					candidate.isHit = isHit;
					if (lowChanged == -1 || j < lowChanged ) {
						lowChanged = j;
					}
					if (hiChanged == -1 || j > hiChanged ) {
						hiChanged = j;
					}					
				}
			}
			if ( lowChanged != -1 ) 
			{
				fireTableRowsUpdated( lowChanged , hiChanged );
			}
		}
	}

	private final JTable table = new JTable(tableModel);

	private final KeyListener keyListener =new KeyAdapter() 
	{
		@Override
		public void keyReleased(java.awt.event.KeyEvent e)
		{
			if ( e.getKeyCode() == KeyEvent.VK_G ) 
			{			
				final Short address = Debugger.queryAddress();
				if ( address != null ) 
				{
					tableModel.addNewBreakpoint( address.intValue() & 0xffff);
				}
			} 
			else if ( e.getKeyCode() == KeyEvent.VK_DELETE ) 
			{		
				final int[] viewRows = table.getSelectedRows();
				Arrays.stream( viewRows )
				.map( table::convertRowIndexToModel )
				.mapToObj( modelRowIdx -> tableModel.model.get( modelRowIdx ).breakpoint )
				.forEach( tableModel::remove );
			}
		}
	};

	public MemoryBreakpointPanel(Emulator emulator) 
	{
		this.emulator = emulator;

		setFocusable( true );
		addKeyListener( keyListener );
		table.addKeyListener( keyListener );
		
        setBackground( Color.BLACK );
        setForeground( Color.GREEN );
        
        table.setBackground( Color.BLACK );
        table.setForeground( Color.GREEN );        
        
		setLayout( new BorderLayout() );
		table.setFillsViewportHeight( true );
		add( new JScrollPane( table ) , BorderLayout.CENTER );
		
		table.setDefaultRenderer(String.class , new DefaultTableCellRenderer()  
		{
			public Component getTableCellRendererComponent(JTable table, Object value, boolean isSelected, boolean hasFocus, int row, int column) 
			{
				final BreakpointEntry entry = tableModel.model.get(row);
				if ( entry.isEnabled && entry.isHit ) 
				{
					setBackground( Color.RED );
				} else {
					setBackground( Color.BLACK );
				}
				
				return super.getTableCellRendererComponent(table, value, isSelected, hasFocus, row, column);
			};
		});
	}

	@Override
	public void setLocationPeer(Component frame) {
		this.peer = frame;
	}

	@Override
	public Component getLocationPeer() {
		return this.peer;
	}

	@Override
	public boolean isDisplayed() {
		return isDisplayed;
	}

	@Override
	public void setDisplayed(boolean yesNo) {
		this.isDisplayed = yesNo;
	}

	private final List<MemoryBreakpoint> hit = new ArrayList<>();

	@Override
	public void refresh(Emulator emulator) 
	{
		synchronized(emulator) {
			emulator.getCPU().getMemoryBreakpointHelper().getMemoryBreakpointHits( hit );
			emulator.getCPU().getMemoryBreakpointHelper().clearAllBreakpointHits();
		}
		tableModel.updateBreakpointsHit( hit );
	}

	@Override
	public boolean isRefreshAfterTick() {
		return false;
	}

	@Override
	public void setConfigProperties(Map<String, String> properties) {
		this.configProperties.clear();
		this.configProperties.putAll(properties);
	}

	@Override
	public Map<String, String> getConfigProperties() 
	{
		return configProperties;
	}
}