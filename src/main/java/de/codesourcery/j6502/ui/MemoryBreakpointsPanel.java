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
import de.codesourcery.j6502.emulator.IMemoryRegion;
import de.codesourcery.j6502.emulator.IMemoryRegion.MemoryType;
import de.codesourcery.j6502.emulator.MemoryBreakpointsContainer;
import de.codesourcery.j6502.emulator.MemoryBreakpointsContainer.MemoryBreakpoint;
import de.codesourcery.j6502.ui.WindowLocationHelper.IDebuggerView;
import de.codesourcery.j6502.utils.Misc;

public class MemoryBreakpointsPanel extends JPanel implements IDebuggerView 
{
    private static final Pattern VALID_HEX_STRING = Pattern.compile("^\\$?([0-9a-fA-f]+)$");   

    private Component peer;
    private boolean isDisplayed;    

    private final Emulator emulator;

    private final MyTableModel tableModel = new MyTableModel();
    private final JTable table = new JTable( tableModel );

    private final class MyTableModel extends AbstractTableModel 
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
                case 1: return "Memory type";
                case 2: return "Enabled";
                case 3: return "Read";
                case 4: return "Write";
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
                case 1: // memory type
                    return bp.getMemoryType().identifier;
                case 2: // enabled
                    return bp.enabled;
                case 3: // read
                    return bp.isRead();
                case 4: // write
                    return bp.isWrite();
                default:
                    throw new RuntimeException("Invalid column index: "+columnIndex);
            }
        }

        @Override
        public void setValueAt(Object aValue, int rowIndex, int columnIndex) 
        {
            // careful, do NOT change the lock ordering here , otherwise a deadlock may occur
            synchronized( emulator ) 
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
                                oldBp.remove();
                                newBp = oldBp.container.addBreakpoint( oldBp.withAddress( address ) );
                            }
                            break;
                        case 1: // memory type
                            String identifier = (String) aValue;
                            final MemoryType type;
                            try {
                                type = IMemoryRegion.MemoryType.fromIdentifier( identifier );
                            } catch(Exception e) {
                                return;
                            }
                            if ( type != oldBp.getMemoryType() ) {
                                oldBp.remove();
                            }
                            final MemoryBreakpointsContainer container = getMemoryRegion( oldBp.address , type ).getBreakpointsContainer();
                            if ( oldBp.isReadWrite() ) {
                                newBp = container.addReadWriteBreakpoint( oldBp.address );
                            } else if ( oldBp.isRead() ) {
                                newBp = container.addReadBreakpoint( oldBp.address );
                            } else if ( oldBp.isWrite() ) {
                                newBp = container.addWriteBreakpoint( oldBp.address );
                            } else {
                                throw new RuntimeException("Don't know how to clone breakpoint "+oldBp);
                            }
                            break;
                        case 2: // enabled
                            final boolean isEnabled = (Boolean) aValue;
                            if ( isEnabled != oldBp.enabled ) {
                                newBp = oldBp.withEnabled( isEnabled );
                                oldBp.container.replace( newBp );                        
                            }
                            break;
                        case 3: // read
                            final boolean isRead = (Boolean) aValue;
                            if ( isRead != oldBp.isRead() ) {
                                newBp = oldBp.withRead( isRead );
                                oldBp.container.replace( newBp );
                            }
                            break;
                        case 4: // write
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
        }

        @Override
        public Class<?> getColumnClass(int columnIndex) {
            switch( columnIndex ) 
            {
                case 0: // address
                    return String.class;
                case 1: // memory type
                    return String.class;
                case 2: // enabled
                    return Boolean.class;
                case 3: // read
                    return Boolean.class;
                case 4: // write
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
                case 1: // memory type
                    return true;
                case 2: // enabled?
                    return true; 
                case 3: // read
                    return ! bp.isRead() || bp.isWrite();
                case 4: // write
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
            return 5;
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
        this.emulator = emulator;
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
                    final List<MemoryBreakpoint> toRemove = new ArrayList<>();
                    final boolean bpFound = tableModel.doWithBreakpoints( breakpoints -> 
                    {
                        if ( row >= 0 && row < breakpoints.size() ) {
                            // cannot directly call MemoryBreakpoint#remove() here as
                            // tableModel.doWithBreakpoints already locked 'data' and 
                            // we'd need to lock the Emulator instance before removing the breakpoint from
                            // the container and this would cause a lock-inversion ( the right
                            // locking order is first) emulator second) tableModel data
                            toRemove.add( breakpoints.get( row ) );
                            return true;
                        }
                        return false;
                    } );
                    if ( bpFound ) 
                    {
                        synchronized( emulator ) 
                        {
                            tableModel.doWithBreakpoints( breakpoints -> 
                            {
                                breakpoints.removeAll( toRemove );
                                toRemove.forEach( MemoryBreakpoint::remove );
                                return true;
                             });
                        }
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
                            if ( container.hasMemoryType( MemoryType.RAM) && container.getAddressRange().contains( address ) ) 
                            {
                                container.addReadWriteBreakpoint( address );
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
        synchronized(emulator) 
        {
            visitor.accept( emulator.getVIC().getBreakpointsContainer() );
            visitor.accept( emulator.getCIA1().getBreakpointsContainer() );
            visitor.accept( emulator.getCIA2().getBreakpointsContainer() );
            final IMemoryRegion[] regions = emulator.getMemory().getRAMRegions();
            for ( int i = 0 , len = regions.length ; i < len ; i++ ) 
            {
                visitor.accept( regions[i].getBreakpointsContainer() );
            }
        }
    }

    @Override
    public boolean isRefreshAfterTick() 
    {
        return false;
    }
    
    private IMemoryRegion getMemoryRegion(int address,MemoryType type) 
    {
        switch( type ) 
        {
            case IOAREA:
                synchronized(emulator) 
                {
                    if ( emulator.getVIC().getAddressRange().contains( address ) ) 
                    {
                        return emulator.getVIC();
                    } 
                    else if ( emulator.getCIA1().getAddressRange().contains( address ) ) 
                    {
                        return emulator.getCIA1();
                    } 
                    else  if ( emulator.getCIA2().getAddressRange().contains( address ) ) 
                    {
                        return emulator.getCIA2();
                    }
                }                    
                throw new RuntimeException("Unable to locate I/O region for address "+Misc.to16BitHex(address));
            case RAM:
                synchronized(emulator) {
                    return emulator.getMemory().getRAMRegion( address );
                }
            default:
                throw new RuntimeException("Unsupported memory type for breakpoint: "+type);
        }
    }    
}