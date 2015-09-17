package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.j6502.emulator.IMemoryRegion.MemoryType;
import de.codesourcery.j6502.emulator.MemoryBreakpointsContainer.MemoryBreakpoint;
import junit.framework.TestCase;

public class MemoryBreakpointsContainerTest extends TestCase {

    private MemoryBreakpointsContainer container;
    
    @Override
    protected void setUp() throws Exception {
        super.setUp();
        container = new MemoryBreakpointsContainer("dummy",MemoryType.RAM, AddressRange.range(0 , 0xffff) );
    }
    
    public void testAddReadBreakpoint() {
        
        MemoryBreakpoint breakpoint = container.addReadBreakpoint( 0x100 );
        assertEquals( 0x100 , breakpoint.address );
        assertTrue( breakpoint.enabled );
        assertEquals( MemoryBreakpointsContainer.Flag.READ.bitmask , breakpoint.flags );
        assertSame( container , breakpoint.container );
        assertEquals( 1 , container.getBreakpointCount() );
        assertTrue( container.hasBreakpoints() );
        
        final int[] hitCount = new int[] { 0 } ;
        container.setCallback( (container,bp) -> 
        {
            assertEquals( breakpoint , bp );
            hitCount[0]++;
        });
        
        for ( int i = 0 ; i <= 0xffff ; i++ ) 
        {
            container.read( i );
            container.write( i );
        }
        assertEquals( 1 , hitCount[0] );
    }
    
    public void testPromoteReadToReadWrite() {
        
        MemoryBreakpoint breakpoint = container.addReadBreakpoint( 0x100 );
        assertEquals( 0x100 , breakpoint.address );
        assertTrue( breakpoint.enabled );
        assertEquals( MemoryBreakpointsContainer.Flag.READ.bitmask , breakpoint.flags );
        assertSame( container , breakpoint.container );
        assertEquals( 1 , container.getBreakpointCount() );
        assertTrue( container.hasBreakpoints() );
        
        final MemoryBreakpoint breakpoint2 = container.addWriteBreakpoint( 0x100 );
        
        assertEquals( 1 , container.getBreakpointCount() );
        assertTrue( container.hasBreakpoints() );
        
        final int[] hitCount = new int[] { 0 } ;
        container.setCallback( (container,bp) -> 
        {
            assertEquals( breakpoint2 , bp );
            hitCount[0]++;
        });
        
        for ( int i = 0 ; i <= 0xffff ; i++ ) 
        {
            container.read( i );
            container.write( i );
        }
        assertEquals( 2 , hitCount[0] );
    }    
    
    public void testVisitBreakpoints() 
    {
        final MemoryBreakpoint breakpoint = container.addReadBreakpoint( 0x100 );
        
        final List<MemoryBreakpoint> visited = new ArrayList<>();
        container.visitBreakpoints( visited::add );
        assertEquals( 1 , visited.size() );
        assertTrue( visited.contains( breakpoint ) );
    }
    
    public void testDisableBreakpoint() {
        
        MemoryBreakpoint breakpoint = container.addReadBreakpoint( 0x100 );
        assertEquals( 0x100 , breakpoint.address );
        assertTrue( breakpoint.enabled );
        assertEquals( MemoryBreakpointsContainer.Flag.READ.bitmask , breakpoint.flags );
        assertSame( container , breakpoint.container );

        container.replace( breakpoint.withEnabled( false ) );
        
        assertEquals( 1 , container.getBreakpointCount() );
        assertTrue( container.hasBreakpoints() );
        
        final int[] hitCount = new int[] { 0 } ;
        container.setCallback( (container,bp) -> 
        {
            hitCount[0]++;
        });
        
        for ( int i = 0 ; i <= 0xffff ; i++ ) 
        {
            container.read( i );
            container.write( i );
        }
        assertEquals( 0 , hitCount[0] );
    }    
    
    public void testRemoveReadBreakpoint() {
        
        final MemoryBreakpoint breakpoint = container.addReadBreakpoint( 0x100 );
        assertEquals( 1 , container.getBreakpointCount() );
        assertTrue( container.hasBreakpoints() );
        
        container.remove( breakpoint );
        assertEquals( 0 , container.getBreakpointCount() );
        assertFalse( container.hasBreakpoints() );        
        
        final int[] hitCount = new int[] { 0 } ;
        container.setCallback( (container,bp) -> 
        {
            hitCount[0]++;
        });
        
        for ( int i = 0 ; i <= 0xffff ; i++ ) 
        {
            container.read( i );
            container.write( i );
        }
        assertEquals( 0 , hitCount[0] );
    }   
    
    public void testRemoveWriteFromReadWriteBreakpoint() {
        
        final MemoryBreakpoint breakpoint = container.addReadWriteBreakpoint( 0x100 );
        assertEquals( 1 , container.getBreakpointCount() );
        assertTrue( container.hasBreakpoints() );
        
        final MemoryBreakpoint toRemove = breakpoint.withWrite( false );
        container.remove( toRemove );
        assertEquals( 1 , container.getBreakpointCount() );
        assertTrue( container.hasBreakpoints() );        
        
        final int[] hitCount = new int[] { 0 } ;
        container.setCallback( (container,bp) -> 
        {
            hitCount[0]++;
        });
        
        for ( int i = 0 ; i <= 0xffff ; i++ ) 
        {
            container.read( i );
            container.write( i );
        }
        assertEquals( 1 , hitCount[0] );
    } 

    public void testRemoveReadFromReadWriteBreakpoint() {
        
        final MemoryBreakpoint breakpoint = container.addReadWriteBreakpoint( 0x100 );
        assertEquals( 1 , container.getBreakpointCount() );
        assertTrue( container.hasBreakpoints() );
        
        final MemoryBreakpoint toRemove = breakpoint.withRead( false );
        container.remove( toRemove );
        assertEquals( 1 , container.getBreakpointCount() );
        assertTrue( container.hasBreakpoints() );        
        
        final int[] hitCount = new int[] { 0 } ;
        container.setCallback( (container,bp) -> 
        {
            hitCount[0]++;
        });
        
        for ( int i = 0 ; i <= 0xffff ; i++ ) 
        {
            container.read( i );
            container.write( i );
        }
        assertEquals( 1 , hitCount[0] );
    }     
    
    public void testAddWriteBreakpoint() {
        
        MemoryBreakpoint breakpoint = container.addWriteBreakpoint( 0x100 );
        assertEquals( 0x100 , breakpoint.address );
        assertEquals( MemoryBreakpointsContainer.Flag.WRITE.bitmask , breakpoint.flags );
        assertSame( container , breakpoint.container );
        assertEquals( 1 , container.getBreakpointCount() );
        assertTrue( container.hasBreakpoints() );
        
        final int[] hitCount = new int[] { 0 } ;
        container.setCallback( (container,bp) -> 
        {
            assertEquals( breakpoint , bp );
            hitCount[0]++;
        });
        
        for ( int i = 0 ; i <= 0xffff ; i++ ) 
        {
            container.read( i );
            container.write( i );
        }
        assertEquals( 1 , hitCount[0] );
    }    
    
    public void testAddReadWriteBreakpoint() {
        
        MemoryBreakpoint breakpoint = container.addReadWriteBreakpoint( 0x100 );
        assertEquals( 0x100 , breakpoint.address );
        assertEquals( MemoryBreakpointsContainer.Flag.combine(MemoryBreakpointsContainer.Flag.READ,MemoryBreakpointsContainer.Flag.WRITE) , breakpoint.flags );
        assertSame( container , breakpoint.container );
        assertEquals( 1 , container.getBreakpointCount() );
        assertTrue( container.hasBreakpoints() );
        
        final int[] hitCount = new int[] { 0 } ;
        container.setCallback( (container,bp) -> 
        {
            assertEquals( breakpoint , bp );
            hitCount[0]++;
        });
        
        for ( int i = 0 ; i <= 0xffff ; i++ ) 
        {
            container.read( i );
            container.write( i );
        }
        assertEquals( 2 , hitCount[0] );
    }      
}