package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public final class BreakpointsController 
{
    private final Object LOCK = new Object();
    
    // @GuardedBy( LOCK )
    private Breakpoint oneShotBreakpoint = null;

    // @GuardedBy( LOCK )
    private final Breakpoint[] breakpoints = new Breakpoint[65536];

    // @GuardedBy( LOCK )
    private AtomicInteger breakpointsCount=new AtomicInteger(0);
    
    // @GuardedBy( breakpointListeners )
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
        final List<Breakpoint> removed = new ArrayList<>();
        synchronized(LOCK)
        {
            for ( int i = 0 ; i < breakpoints.length ; i++ )
            {
                Breakpoint bp = breakpoints[i];
                if ( bp != null ) {
                    removed.add( bp );
                    breakpoints[i] = null;
                }
            }
            oneShotBreakpoint = null;
            breakpointsCount.set(0);
        }
        // invoke listeners AFTER releasing lock
        visitBreakpointListeners( l -> removed.forEach( l::breakpointRemoved ) ); 
    }

    public void addBreakpointListener(IBreakpointLister l)
    {
        if (l == null) {
            throw new IllegalArgumentException("l must not be NULL");
        }
        synchronized(breakpointListeners) {
            this.breakpointListeners.add(l);
        }
    }

    public void removeBreakpointListener(IBreakpointLister l)
    {
        if (l == null) {
            throw new IllegalArgumentException("l must not be NULL");
        }
        synchronized(breakpointListeners) {
            this.breakpointListeners.remove(l);
        }
    }
    
    public void addBreakpoint(Breakpoint bp)
    {
        Runnable doAfterReleasingLock = null;
        synchronized(LOCK)
        {
            if (bp.isOneshot)
            {
                if ( this.oneShotBreakpoint == null ) {
                    breakpointsCount.incrementAndGet();
                }
                this.oneShotBreakpoint = bp;
            }
            else
            {
                Breakpoint old = this.breakpoints[ bp.address & 0xffff ];
                this.breakpoints[ bp.address & 0xffff ] = bp;
                if ( old == null )
                {
                    breakpointsCount.incrementAndGet();
                    doAfterReleasingLock = () -> visitBreakpointListeners( l -> l.breakpointAdded( bp ) );
                }
                else
                {
                    doAfterReleasingLock = () -> visitBreakpointListeners( l -> l.breakpointReplaced( old , bp ) );
                }
            }
        }
        if ( doAfterReleasingLock != null ) {
            doAfterReleasingLock.run();
        }
    }
    
    private void visitBreakpointListeners(Consumer<IBreakpointLister> visitor) 
    {
        synchronized(breakpointListeners) {
            breakpointListeners.forEach( visitor );
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
        Breakpoint removed = null;
        synchronized(LOCK)
        {
            if ( breakpoint.isOneshot )
            {
                if ( oneShotBreakpoint != null && oneShotBreakpoint.address == breakpoint.address ) {
                    oneShotBreakpoint = null;
                    breakpointsCount.decrementAndGet();
                }
            }
            else
            {
                Breakpoint existing = this.breakpoints[ breakpoint.address & 0xffff ];
                if ( existing != null )
                {
                    this.breakpoints[ breakpoint.address & 0xffff ] = null;
                    breakpointsCount.decrementAndGet();
                    removed = existing;
                }
            }
        }
        if ( removed != null ) 
        {
            final Breakpoint finalRemoved = removed;
            visitBreakpointListeners(  l -> l.breakpointRemoved( finalRemoved ) );
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
    
    public boolean checkIsAtBreakpoint()
    {
        if ( breakpointsCount.get() > 0 ) 
        {
            synchronized(LOCK)
            {
                if ( breakpointsCount.get() > 0 )
                {
                    Breakpoint bp = breakpoints[ cpu.pc() ];
                    if ( bp == null && ( oneShotBreakpoint != null && oneShotBreakpoint.address == cpu.pc() ) ) {
                        bp = oneShotBreakpoint;
                    }
                    if ( bp != null && bp.isTriggered( cpu ) )
                    {
                        if ( bp == oneShotBreakpoint ) 
                        {
                            oneShotBreakpoint = null;
                            breakpointsCount.decrementAndGet();
                        }
                        cpu.setBreakpointReached();
                        return true;
                    }
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