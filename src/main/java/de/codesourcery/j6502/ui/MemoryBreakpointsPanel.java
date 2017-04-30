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

import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.AbstractTableModel;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.EmulatorDriver;
import de.codesourcery.j6502.emulator.EmulatorDriver.CallbackWithResult;
import de.codesourcery.j6502.emulator.IMemoryRegion;
import de.codesourcery.j6502.emulator.IMemoryRegion.MemoryType;
import de.codesourcery.j6502.emulator.MemoryBreakpointsContainer;
import de.codesourcery.j6502.emulator.MemoryBreakpointsContainer.MemoryBreakpoint;
import de.codesourcery.j6502.ui.WindowLocationHelper.IDebuggerView;
import de.codesourcery.j6502.utils.Misc;

public class MemoryBreakpointsPanel extends JPanel implements IDebuggerView 
{
    private Component peer;
    private boolean isDisplayed;    

    private final EmulatorDriver driver;

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
            final MemoryBreakpoint oldBp;
            synchronized(data) 
            {            
                oldBp = item(rowIndex);
            }
            // careful, do NOT change the lock ordering here , otherwise a deadlock may occur
            driver.invokeAndWait( emulator -> 
            {
                MemoryBreakpoint newBp = oldBp;
                synchronized(oldBp) 
                {            
                    switch( columnIndex ) 
                    {
                        case 0: // address
                            if ( Misc.isValidHexAddress( (String) aValue ) ) 
                            {
                                final int address = Misc.parseHexAddress( (String) aValue );
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
                }

                if ( newBp != oldBp ) 
                {
                    synchronized( data ) {
                        data.set(rowIndex,newBp);
                    }
                }                
            });
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

    public MemoryBreakpointsPanel(final EmulatorDriver driver) 
    {
        this.driver = driver;
        setLayout( new GridBagLayout() );

        driver.invokeAndWait( emulator -> 
        {
            final BiConsumer<MemoryBreakpointsContainer, MemoryBreakpoint> callback = (container,breakpoint) -> 
            {
                emulator.getCPU().setBreakpointReached();
            };
            emulator.visitBreakpointContainers( container -> container.setCallback( callback ) );
        } );

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
                        tableModel.doWithBreakpoints( breakpoints -> 
                        {
                            breakpoints.removeAll( toRemove );
                            toRemove.forEach( MemoryBreakpoint::remove );
                            return true;
                        });
                        driver.invokeLater( emulator -> refresh( ) );                        
                    }
                }
                else if ( e.getKeyCode() == KeyEvent.VK_INSERT ) 
                {
                    final Integer address = Misc.parseHexAddress( JOptionPane.showInputDialog("Create breakpoint" , "$dc00" ) );
                    if ( address != null ) 
                    {
                        driver.invokeAndWait( emulator -> 
                        {
                            emulator.visitBreakpointContainers( container -> 
                            {
                                if ( container.hasMemoryType( MemoryType.RAM) && container.getAddressRange().contains( address ) ) 
                                {
                                    container.addReadWriteBreakpoint( address );
                                }
                            });
                        });
                        driver.invokeLater( emulator -> refresh( ) );
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
    public void refresh() {
        
        driver.invokeLater(emulator-> 
        {
            final List<MemoryBreakpoint> tmpBreakpoints = new ArrayList<>();
            emulator.visitBreakpointContainers(  container -> container.visitBreakpoints( tmpBreakpoints::add ) );
            
            tableModel.doWithBreakpoints( breakpoints -> 
            {
                breakpoints.clear();
                breakpoints.addAll( tmpBreakpoints );
                return true;
            });
            SwingUtilities.invokeLater( () -> tableModel.fireTableDataChanged() );            
            
        });
    }

    @Override
    public boolean isRefreshAfterTick() 
    {
        return false;
    }

    private IMemoryRegion getMemoryRegion(int address,MemoryType type) 
    {
        final CallbackWithResult<IMemoryRegion> cb = new CallbackWithResult<>( emulator -> 
        {
            switch( type ) 
            {
                case IOAREA:
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
                    throw new RuntimeException("Unable to locate I/O region for address "+Misc.to16BitHex(address));
                case RAM:
                    return emulator.getMemory().getRAMRegion( address );
                default:
                    throw new RuntimeException("Unsupported memory type for breakpoint: "+type);
            }            
        });
        driver.invokeAndWait( cb );
        return cb.getResult();
    }    
}