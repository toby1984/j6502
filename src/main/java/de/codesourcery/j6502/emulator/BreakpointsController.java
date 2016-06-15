package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.List;

public final class BreakpointsController 
{
    private final Object LOCK = new Object();
    
    // @GuardedBy( LOCK )
    private Breakpoint oneShotBreakpoint = null;

    // @GuardedBy( LOCK )
    private final Breakpoint[] breakpoints = new Breakpoint[65536];

    // @GuardedBy( LOCK )
    private int breakpointsCount=0;
    
    private final List<IBreakpointLister> breakpointListeners = new ArrayList<>();
    
    private final CPU cpu;
    private final IMemoryRegion memory;
    
    public interface IBreakpointLister
    {
        public void breakpointAdded(Breakpoint bp);
        public void breakpointRemoved(Breakpoint bp);
        public void breakpointReplaced(Breakpoint old,Breakpoint newBp);
        public void allBreakpointsChanged();
    }
    
    public BreakpointsController(CPU cpu, IMemoryRegion memory) {
        this.cpu = cpu;
        this.memory = memory;
    }

    public void removeAllBreakpoints()
    {
        synchronized(LOCK)
        {
            for ( int i = 0 ; i < breakpoints.length ; i++ )
            {
                Breakpoint bp = breakpoints[i];
                if ( bp != null ) {
                    breakpoints[i] = null;
                    breakpointListeners.forEach( l -> l.breakpointRemoved(bp) );
                }
            }
            oneShotBreakpoint = null;
            breakpointsCount = 0;
        }
    }

    public void addBreakpointListener(IBreakpointLister l)
    {
        if (l == null) {
            throw new IllegalArgumentException("l must not be NULL");
        }
        synchronized(LOCK) {
            this.breakpointListeners.add(l);
        }
    }

    public void removeBreakpointListener(IBreakpointLister l)
    {
        if (l == null) {
            throw new IllegalArgumentException("l must not be NULL");
        }
        synchronized(LOCK) {
            this.breakpointListeners.remove(l);
        }
    }
    
    public void addBreakpoint(Breakpoint bp)
    {
        synchronized(LOCK)
        {
            if (bp.isOneshot)
            {
                if ( this.oneShotBreakpoint == null ) {
                    breakpointsCount++;
                }
                this.oneShotBreakpoint = bp;
            }
            else
            {
                Breakpoint old = this.breakpoints[ bp.address & 0xffff ];
                this.breakpoints[ bp.address & 0xffff ] = bp;
                if ( old == null )
                {
                    breakpointsCount++;
                    breakpointListeners.forEach( l -> l.breakpointAdded( bp ) );
                }
                else
                {
                    breakpointListeners.forEach( l -> l.breakpointReplaced( old , bp ) );
                }
            }
        }
    }

    public Breakpoint getBreakpoint(short address)
    {
        synchronized(LOCK) {
            return breakpoints[ address & 0xffff ];
        }
    }

    public List<Breakpoint> getBreakpoints()
    {
        List<Breakpoint> result = new ArrayList<>();
        synchronized(LOCK) {
            for ( int i = 0 , len = breakpoints.length ; i < len ; i++ ) {
                Breakpoint breakpoint = breakpoints[i];
                if ( breakpoint != null && ! breakpoint.isOneshot ) {
                    result.add( breakpoint );
                }
            }
        }
        return result;
    }

    public void removeBreakpoint(Breakpoint breakpoint)
    {
        synchronized(LOCK)
        {
            if ( breakpoint.isOneshot )
            {
                if ( oneShotBreakpoint != null && oneShotBreakpoint.address == breakpoint.address ) {
                    oneShotBreakpoint = null;
                    breakpointsCount--;
                }
            }
            else
            {
                Breakpoint existing = this.breakpoints[ breakpoint.address & 0xffff ];
                if ( existing != null )
                {
                    this.breakpoints[ breakpoint.address & 0xffff ] = null;
                    breakpointsCount--;
                    breakpointListeners.forEach( l -> l.breakpointRemoved( existing ) );
                }
            }
        }
    }    
    
    public boolean stepReturn(IMemoryRegion memory , CPU cpu)
    {
        if ( canStepOver(memory,cpu) ) // check whether PC is at a JSR $xxxx instruction
        {
            addBreakpoint( Breakpoint.oneShotBreakpoint( cpu.pc()+3 ) ); // JSR $xxxx occupies 3 bytes
            return true;
        }
        return false;
    }
    
    public boolean isAtBreakpoint()
    {
        synchronized(LOCK)
        {
            if ( breakpointsCount > 0 )
            {
                Breakpoint bp = breakpoints[ cpu.pc() ];
                if ( bp == null && ( oneShotBreakpoint != null && oneShotBreakpoint.address == cpu.pc() ) ) {
                    bp = oneShotBreakpoint;
                }
                if ( bp != null && bp.isTriggered( cpu ) )
                {
                    if ( bp == oneShotBreakpoint ) {
                        oneShotBreakpoint = null;
                    }
                    return true;
                }
            }
        }
        return false;
    }

    public boolean canStepOver(IMemoryRegion memory , CPU cpu)
    {
        final int op = memory.readByte( cpu.pc() );
        return op == 0x20; // JSR $xxxx
    }
    
    public CPU getCPU() {
        return cpu;
    }
    
    public IMemoryRegion getMemory() {
        return memory;
    }
}
