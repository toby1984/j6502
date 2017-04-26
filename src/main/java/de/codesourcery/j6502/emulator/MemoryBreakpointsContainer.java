package de.codesourcery.j6502.emulator;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.Constants;
import de.codesourcery.j6502.emulator.IMemoryRegion.MemoryType;

public final class MemoryBreakpointsContainer 
{
    public static enum Flag 
    {
        READ(1),WRITE(2);
        
        public final int bitmask;
        
        private Flag(int bitmask) {
            this.bitmask = bitmask;
        }
        
        public static int combine(Flag f1,Flag...additional) 
        {
            int result = f1.set( 0 );
            if ( additional != null ) 
            {
                for ( Flag f : additional ) 
                {
                    result = f.set( result );
                }
            }
            return result;
        }
        
        public int setOrClear(int input,boolean set) 
        {
            if ( set ) {
                return set( input );
            }
            return clear( input );
        }
        
        public boolean isSet(int input) {
            return (input & bitmask) != 0;
        }
        
        public int set(int input) {
            return input | bitmask;
        }
        
        public int clear(int input) {
            return input & ~bitmask;
        }
    }
    
    public static final int MAX_BREAKPOINTS = 20;
    
    private final MemoryBreakpoint[] breakpoints = new MemoryBreakpoint[ MAX_BREAKPOINTS+1 ]; // element #0 is always NULL 
    
    public final String identifier;
    public final IMemoryRegion memoryRegion;
    private int breakpointCount;
    private final int[] addressSpace;
    
    private BiConsumer<MemoryBreakpointsContainer,MemoryBreakpoint> callback = (a,b) -> {};
    
    public MemoryBreakpointsContainer(String identifier,IMemoryRegion memoryRegion) 
    {
        if ( identifier == null || identifier.trim().length() == 0 ) {
            throw new IllegalArgumentException("Identifier must not be NULL or blank");
        }
        Validate.notNull(memoryRegion, "memoryRegion must not be NULL");
        
        this.identifier = identifier;
        this.memoryRegion = memoryRegion;
        this.addressSpace = new int[ memoryRegion.getAddressRange().getSizeInBytes() ];
    }
  
    public boolean hasMemoryType(MemoryType t) {
        return t.equals( getMemoryType() );
    }
    
    public MemoryType getMemoryType() {
        return memoryRegion.getType();
    }
    
    @Override
    public String toString() {
        return "MemoryBreakpoints[ "+getMemoryType()+" , "+identifier+" , "+getAddressRange()+" ]";
    }
    
    public AddressRange getAddressRange() {
        return memoryRegion.getAddressRange();
    }
    
    public void replace(MemoryBreakpoint replacement) 
    {
        Validate.notNull(replacement, "breakpoint must not be NULL");
        final MemoryBreakpoint copy = replacement.copy(); 
        if ( ! getAddressRange().contains( copy.address ) ) {
            throw new IllegalArgumentException( copy+" is out of range for "+this);          
        }        

        final int translated = copy.address - getAddressRange().getStartAddress();
        final int index = addressSpace[ translated ];
        if ( index == 0 ) {
            throw new IllegalArgumentException("Replacement failed for "+copy+" because there is no already existing breakpoint");
        }
        breakpoints[ index ] = copy;
    }
    
    public MemoryBreakpoint addReadBreakpoint(int address) {
        return addBreakpoint( new MemoryBreakpoint(address,Flag.READ));
    }
    
    public MemoryBreakpoint addWriteBreakpoint(int address) 
    {
        return addBreakpoint( new MemoryBreakpoint(address,Flag.WRITE) );
    }
    
    public MemoryBreakpoint addReadWriteBreakpoint(int address) {
        return addBreakpoint( new MemoryBreakpoint(address,Flag.READ,Flag.WRITE) );
    }   
    
    public MemoryBreakpoint addBreakpoint(final MemoryBreakpoint toAdd) 
    {
        if ( toAdd == null ) {
            throw new IllegalArgumentException("Breakpoint must not be NULL");
        }
        if ( ! toAdd.container.hasMemoryType( this.getMemoryType() ) ) {
            throw new IllegalArgumentException("Cannot add breakpoint with type "+toAdd.getMemoryType()+" to breakpoints container with type "+this.getMemoryType());
        }
        final MemoryBreakpoint newBP = toAdd.copy();
        if ( ! getAddressRange().contains( newBP.address ) ) {
            throw new IllegalArgumentException( newBP+" is out of range for "+this);          
        }
        System.out.println("Adding memory breakpoint: "+toAdd);
        final int translatedAddress = newBP.address - getAddressRange().getStartAddress();
        int freeIdx = -1;
        for ( int i = 1 ; i < breakpoints.length ; i++ ) // element #0 is skipped because it always has to be NULL 
        {
            final MemoryBreakpoint existingBp = breakpoints[i];
            if ( existingBp != null ) 
            {
                if ( existingBp.address == newBP.address ) 
                {
                    if ( existingBp.flags == newBP.flags ) {
                        return existingBp.copy();
                    }
                    existingBp.flags |= newBP.flags;
                    return existingBp.copy();
                }
            } 
            else if ( freeIdx == -1 ) 
            {
                freeIdx = i;
            }
        }
        
        if ( freeIdx == -1 ) {
            throw new IllegalStateException("Maximum number of supported memory breakpoints ("+MAX_BREAKPOINTS+") already reached. Re-compile to increase this number.");            
        }

        addressSpace[ translatedAddress ] = freeIdx;
        breakpoints[freeIdx]=newBP;
        breakpointCount++;
        return newBP;
    }
    
    public int getBreakpointCount() {
        return breakpointCount;
    }
    
    public boolean hasBreakpoints() {
        return breakpointCount != 0;
    }
    
    public void visitBreakpoints(Consumer<MemoryBreakpoint> visitor) 
    {
        if ( breakpointCount > 0 ) 
        {
            for ( int i = 1 ; i < breakpoints.length ; i++ ) 
            {
                final MemoryBreakpoint bp = breakpoints[i];
                if ( bp != null ) {
                    visitor.accept( bp.copy() );
                }
            }
        }
    }
    
    public boolean removeBreakpoint(MemoryBreakpoint toRemove) {
        if ( toRemove == null ) {
            throw new IllegalArgumentException("Breakpoint must not be NULL");
        }
        if ( ! getAddressRange().contains( toRemove.address ) ) {
            throw new IllegalArgumentException( toRemove+" is out of range for "+this);          
        }        
        
        for ( int i = 1 ; i < breakpoints.length ; i++ ) 
        {
            final MemoryBreakpoint existing = breakpoints[i];
            if ( existing != null && existing.address == toRemove.address ) 
            {
                final int newFlags = existing.flags & ~ toRemove.flags;
                if ( newFlags == 0 ) 
                {
                    System.out.println("Removed memory breakpoint: "+toRemove);                    
                    breakpoints[i] = null;
                    addressSpace[ toRemove.address - getAddressRange().getStartAddress() ] = 0;
                    breakpointCount--;
                } 
                else {
                    existing.flags = newFlags;
                    System.out.println("Updated memory breakpoint: "+toRemove+" => "+existing);
                }
                return true;
            }
        }
        return false;
    }
    
    private void maybeTriggered(MemoryBreakpoint bp) 
    {
        callback.accept(this , bp.copy() );
    }
    
    public void read(int address) 
    {
        if ( Constants.MEMORY_SUPPORT_BREAKPOINTS ) 
        {
            if ( hasBreakpoints() ) {
                MemoryBreakpoint bp = getReadBreakpoint( address );
                if ( bp != null && bp.enabled ) {
                    System.out.println("Read from $"+Integer.toHexString( address )+" triggers breakpoint: "+bp);                
                    maybeTriggered( bp );
                }
            }
        }
    }
    
    public void write(int address) 
    {
        if ( Constants.MEMORY_SUPPORT_BREAKPOINTS ) 
        {
            if ( hasBreakpoints() ) {
                MemoryBreakpoint bp = getWriteBreakpoint( address );
                if ( bp != null && bp.enabled ) {
                    System.out.println("Write to $"+Integer.toHexString( address+getAddressRange().getStartAddress() )+" triggers breakpoint: "+bp);                   
                    maybeTriggered( bp );
                }
            }
        }
    }    
    
    private MemoryBreakpoint getReadBreakpoint(int address) 
    {
        final int index = addressSpace[address];
        if ( index == 0 ) {
            return null;
        }
        final MemoryBreakpoint existing = breakpoints[ index ];
        return existing.isRead() ? existing : null;
    }
    
    private MemoryBreakpoint getWriteBreakpoint(int address) {
        final int index = addressSpace[address];
        if ( index == 0 ) {
            return null;
        }
        final MemoryBreakpoint existing = breakpoints[ index ];
        return existing.isWrite() ? existing : null;
    }    
    
    public final class MemoryBreakpoint 
    {
        public final boolean enabled;
        public final int address;
        public final MemoryBreakpointsContainer container=MemoryBreakpointsContainer.this;        
        public int flags;
        
        private MemoryBreakpoint(int address,Flag flag1,Flag... additional) 
        {
            this( address , Flag.combine( flag1, additional ) , true );
        }
        
        private MemoryBreakpoint(int address,int flags,boolean enabled) 
        {
            if ( flags == 0 ) {
                throw new IllegalArgumentException("No flags set?");
            }
            if ( address < 0 || address > 0xffff ) {
                throw new IllegalArgumentException("Address out-of-range: "+address);
            }
            Validate.notNull(container, "breakpoint helper must not be NULL");
            this.address = address;
            this.flags = flags;
            this.enabled = enabled;
        }
        
        public MemoryType getMemoryType() {
            return container.getMemoryType();
        }
        
        public void remove() {
            container.removeBreakpoint( this );
        }
        
        public boolean hasMemoryType(MemoryType t) 
        {
            return t.equals( getMemoryType() );
        }
        
        public boolean equals(Object obj) 
        {
            if ( obj instanceof MemoryBreakpoint) 
            {
                final MemoryBreakpoint other = (MemoryBreakpoint) obj;
                return this.enabled == other.enabled &&
                        this.flags == other.flags &&
                        this.address == other.address &
                        this.container == other.container;
            }
            return false;
        }
        
        private MemoryBreakpoint(MemoryBreakpoint toCopy) {
            this.address = toCopy.address;
            this.flags = toCopy.flags;
            this.enabled = toCopy.enabled;
        }
        
        public MemoryBreakpoint withEnabled(boolean yesNo) 
        {
            if ( enabled == yesNo ) {
                return this;
            }
            return new MemoryBreakpoint(this.address,this.flags,yesNo);
        }
        
        public MemoryBreakpoint withAddress(int address) {
            return new MemoryBreakpoint( address , this.flags , this.enabled );
        }
        
        public MemoryBreakpoint withRead(boolean yesNo) 
        {
            if ( isRead() == yesNo ) {
                return this;
            }
            return new MemoryBreakpoint( this.address , calcFlags( Flag.READ , yesNo ) , this.enabled );
        }
        
        public MemoryBreakpoint withWrite(boolean yesNo) 
        {
            if ( isWrite() == yesNo ) {
                return this;
            }
            return new MemoryBreakpoint( this.address , calcFlags( Flag.WRITE , yesNo ) , this.enabled );
        }
        
        private int calcFlags(Flag flag,boolean set) {
            return flag.setOrClear( this.flags , set );
        }

        @Override
        public String toString() {
            return "Memory breakpoint $"+Integer.toHexString( this.address )+" , region "+container.memoryRegion+", ( read: "+isRead()+" , write: "+isWrite()+")";
        }
        
        public MemoryBreakpoint copy() {
            return new MemoryBreakpoint( this );
        }
        
        public boolean isRead() {
            return Flag.READ.isSet( flags );
        }
        
        public boolean isReadWrite() {
            return isRead() && isWrite();
        }        
        
        public boolean isWrite() {
            return Flag.WRITE.isSet( flags );
        }        
    }
    
    public void setCallback(BiConsumer<MemoryBreakpointsContainer, MemoryBreakpoint> callback) 
    {
        if ( callback == null ) 
        {
            throw new IllegalArgumentException("Callback must not be null");
        }
        this.callback = callback;
    }
}