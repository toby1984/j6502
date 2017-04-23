package de.codesourcery.j6502.ui;

import java.awt.Component;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.AbstractTableModel;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.MemoryBreakpointsContainer;
import de.codesourcery.j6502.emulator.MemoryBreakpointsContainer.MemoryBreakpoint;
import de.codesourcery.j6502.ui.WindowLocationHelper.IDebuggerView;

public class MemoryBreakpointsPanel extends JPanel implements IDebuggerView 
{
    private static final Pattern VALID_HEX_STRING = Pattern.compile("^\\$?([0-9a-fA-f]+)$");   

    private Component peer;
    private boolean isDisplayed;    

    private final MyTableModel tableModel = new MyTableModel();
    private final JTable table = new JTable( tableModel );

    private static final class MyTableModel extends AbstractTableModel 
    {
        private final List<MemoryBreakpoint> data = new ArrayList<>();

        public boolean doWithBreakpoints(Function<List<MemoryBreakpoint>,Boolean> visitor) 
        {
            synchronized( data ) {
                return visitor.apply( data );
            }
        }

        @Override
        public String getColumnName(int column) {
            switch( column ) {
                case 0: return "Address";
                case 1: return "Enabled";
                case 2: return "Read";
                case 3: return "Write";
                default:
                    throw new RuntimeException("Invalid column index: "+column);
            }
        }

        @Override
        public Object getValueAt(int rowIndex, int columnIndex) 
        {
            final MemoryBreakpoint bp = item(rowIndex);
            switch( columnIndex ) {
                case 0: // address
                    return "$"+StringUtils.leftPad( Integer.toHexString( bp.address ) , 4 , '0' );
                case 1: // enabled
                    return bp.enabled;
                case 2: // read
                    return bp.isRead();
                case 3: // write
                    return bp.isWrite();
                default:
                    throw new RuntimeException("Invalid column index: "+columnIndex);
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) 
        {
            synchronized(data) 
            {            
                final MemoryBreakpoint oldBp = item(rowIndex);
                MemoryBreakpoint newBp = oldBp;
                switch( columnIndex ) 
                {
                    case 0: // address
                        final Matcher matcher = VALID_HEX_STRING.matcher( (String) aValue );
                        if ( matcher.matches() ) 
                        {
                            final int address = Integer.parseInt( matcher.group(1).toLowerCase() , 16 ); 
                            oldBp.container.remove( oldBp );
                            newBp = oldBp.container.add( oldBp.withAddress( address ) );
                        }
                        break;
                    case 1: // enabled
                        final boolean isEnabled = (Boolean) aValue;
                        if ( isEnabled != oldBp.enabled ) {
                            newBp = oldBp.withEnabled( isEnabled );
                            oldBp.container.replace( newBp );                        
                        }
                        break;
                    case 2: // read
                        final boolean isRead = (Boolean) aValue;
                        if ( isRead != oldBp.isRead() ) {
                            newBp = oldBp.withRead( isRead );
                            oldBp.container.replace( newBp );
                        }
                        break;
                    case 3: // write
                        final boolean isWrite= (Boolean) aValue;
                        if ( isWrite != oldBp.isWrite() ) {
                            newBp = oldBp.withWrite( isWrite );
                            oldBp.container.replace( newBp );
                        }
                        break;
                    default:
                        throw new RuntimeException("Invalid column index: "+columnIndex);
                }

                if ( newBp != oldBp ) 
                {
                    data.set(rowIndex,newBp);
                }
            }
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch( columnIndex ) 
            {
                case 0: // address
                    return String.class;
                case 1: // enabled
                    return Boolean.class;
                case 2: // read
                    return Boolean.class;
                case 3: // write
                    return Boolean.class;
                default:
                    throw new RuntimeException("Invalid column index: "+columnIndex);
            }
        }

        private MemoryBreakpoint item(int idx) 
        {
            synchronized(data) {
                return data.get(idx);
            }
        }

        @Override
        public boolean isCellEditable(int rowIndex, int columnIndex) 
        {
            final MemoryBreakpoint bp = item(rowIndex);            
            switch( columnIndex ) 
            {
                case 0: // address
                    return false;
                case 1: // enabled?
                    return true; 
                case 2: // read
                    return ! bp.isRead() || bp.isWrite();
                case 3: // write
                    return ! bp.isWrite() || bp.isRead();
                default:
                    throw new RuntimeException("Invalid column index: "+columnIndex);
            }
        }

        @Override
        public int getRowCount() {
            synchronized(data) {
                return data.size();
            }
        }

        @Override
        public int getColumnCount() {
            return 4;
        }
    }

    public static Integer parseHexAddress(String input) 
    {
        if ( input != null ) 
        {
            final Matcher matcher = VALID_HEX_STRING.matcher( input );
            if ( matcher.matches() ) {
                return Integer.parseInt( matcher.group(1).toLowerCase() , 16 );
            }
        }
        return null;
    }

    public MemoryBreakpointsPanel(final Emulator emulator) 
    {
        setLayout( new GridBagLayout() );

        final BiConsumer<MemoryBreakpointsContainer, MemoryBreakpoint> callback = (container,breakpoint) -> 
        {
            emulator.getCPU().setBreakpointReached();
        };
        visitBreakpointContainers( emulator , container -> container.setCallback( callback ) );
        
        final GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.weightx =1 ;
        cnstrs.weighty = 1;
        cnstrs.gridheight = 1;
        cnstrs.gridwidth = 1;
        final JScrollPane pane = new JScrollPane(table);
        add( pane, cnstrs );
        setFocusable( true );

        final KeyAdapter keyboardListener = new KeyAdapter() 
        {
            @Override
            public void keyReleased(KeyEvent e) 
            {
                if ( e.getKeyCode() ==  KeyEvent.VK_DELETE ) 
                {
                    final int row  = table.getSelectedRow();
                    final boolean  dataChanged = tableModel.doWithBreakpoints( breakpoints -> 
                    {
                        if ( row >= 0 && row < breakpoints.size() ) {
                            final MemoryBreakpoint bp = breakpoints.get( row );
                            bp.container.remove( bp );
                            return true;
                        }
                        return false;
                    } );
                    if ( dataChanged ) {
                        refresh( emulator );                        
                    }
                }
                else if ( e.getKeyCode() == KeyEvent.VK_INSERT ) 
                {
                    final Integer address = parseHexAddress( JOptionPane.showInputDialog("Create breakpoint" , "$dc00" ) );
                    if ( address != null ) 
                    {
                        visitBreakpointContainers( emulator , container -> 
                        {
                            if ( container.addressRange.contains( address ) ) 
                            {
                                container.addReadBreakpoint( address );
                            }
                        });
                        refresh( emulator );
                    }
                }
            }
        };
        addKeyListener( keyboardListener);
        table.addKeyListener( keyboardListener);

        Debugger.setup( this );
        Debugger.setup( table );
        Debugger.setup( pane );        
    }

    @Override
    public String getIdentifier() {
        return "memoryBreakPointsView";
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

    @Override
    public void refresh(Emulator emulator) {

        tableModel.doWithBreakpoints( breakpoints -> 
        {
            breakpoints.clear();
            final Consumer<MemoryBreakpoint> visitor = breakpoints::add;
            visitBreakpointContainers( emulator , container -> container.visitBreakpoints( visitor ) );
            return true;
        });
        tableModel.fireTableDataChanged();
    }

    private void visitBreakpointContainers(Emulator emulator,Consumer<MemoryBreakpointsContainer> visitor)
    {
        synchronized(emulator) {
            visitor.accept( emulator.getVIC().getBreakpointsContainer() );
            visitor.accept( emulator.getCIA1().getBreakpointsContainer() );
            visitor.accept( emulator.getCIA2().getBreakpointsContainer() );
        }
    }

    @Override
    public boolean isRefreshAfterTick() 
    {
        return false;
    }
}