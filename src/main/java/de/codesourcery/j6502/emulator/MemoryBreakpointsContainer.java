package de.codesourcery.j6502.emulator;

import java.util.function.BiConsumer;
import java.util.function.Consumer;

import org.apache.commons.lang.Validate;

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
    
    public final AddressRange addressRange;
    public final String identifier;
    public final MemoryType memoryType;
    private int breakpointCount;
    private final int[] addressSpace;
    
    private BiConsumer<MemoryBreakpointsContainer,MemoryBreakpoint> callback = (a,b) -> {};
    
    public MemoryBreakpointsContainer(String identifier,MemoryType memoryType,AddressRange addressRange) 
    {
        if ( identifier == null || identifier.trim().length() == 0 ) {
            throw new IllegalArgumentException("Identifier must not be NULL or blank");
        }
        if ( memoryType == null ) {
            throw new IllegalArgumentException("memory type must not be NULL or blank");
        }        
        if ( addressRange == null ) {
            throw new IllegalArgumentException("Address range must not be NULL or blank");
        }              
        this.addressRange = addressRange;
        this.identifier = identifier;
        this.memoryType = memoryType;
        this.addressSpace = new int[ addressRange.getSizeInBytes() ];
    }
  
    public boolean hasMemoryType(MemoryType t) {
        return t.equals( this.memoryType );
    }
    
    public MemoryType getMemoryType() {
        return memoryType;
    }
    
    @Override
    public String toString() {
        return "MemoryBreakpoints[ "+memoryType+" , "+identifier+" , "+addressRange+" ]";
    }
    
    public void replace(MemoryBreakpoint replacement) 
    {
        Validate.notNull(replacement, "breakpoint must not be NULL");
        final MemoryBreakpoint copy = replacement.copy(); 
        if ( ! addressRange.contains( copy.address ) ) {
            throw new IllegalArgumentException( copy+" is out of range for "+this);          
        }        

        final int translated = copy.address - addressRange.getStartAddress();
        final int index = addressSpace[ translated ];
        if ( index == 0 ) {
            throw new IllegalArgumentException("Replacement failed for "+copy+" because there is no already existing breakpoint");
        }
        breakpoints[ index ] = copy;
    }
    
    public MemoryBreakpoint addReadBreakpoint(int address) {
        return add( new MemoryBreakpoint(address,Flag.READ));
    }
    
    public MemoryBreakpoint addWriteBreakpoint(int address) {
        return add( new MemoryBreakpoint(address,Flag.WRITE) );
    }
    
    public MemoryBreakpoint addReadWriteBreakpoint(int address) {
        return add( new MemoryBreakpoint(address,Flag.READ,Flag.WRITE) );
    }   
    
    public MemoryBreakpoint add(final MemoryBreakpoint toAdd) 
    {
        if ( toAdd == null ) {
            throw new IllegalArgumentException("Breakpoint must not be NULL");
        }
        final MemoryBreakpoint newBP = toAdd.copy();
        if ( ! addressRange.contains( newBP.address ) ) {
            throw new IllegalArgumentException( newBP+" is out of range for "+this);          
        }
        final int translatedAddress = newBP.address - addressRange.getStartAddress();
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
    
    public boolean remove(MemoryBreakpoint toRemove) {
        if ( toRemove == null ) {
            throw new IllegalArgumentException("Breakpoint must not be NULL");
        }
        if ( ! addressRange.contains( toRemove.address ) ) {
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
                    breakpoints[i] = null;
                    addressSpace[ toRemove.address - addressRange.getStartAddress() ] = 0;
                    breakpointCount--;
                } 
                else {
                    existing.flags = newFlags;
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
        if ( hasBreakpoints() ) {
            MemoryBreakpoint bp = getReadBreakpoint( address );
            if ( bp != null && bp.enabled ) {
                System.out.println("Read from $"+Integer.toHexString( address )+" triggers breakpoint: "+bp);                
                maybeTriggered( bp );
            }
        }
    }
    
    public void write(int address) {
        if ( hasBreakpoints() ) {
            MemoryBreakpoint bp = getWriteBreakpoint( address );
            if ( bp != null && bp.enabled ) {
                System.out.println("Write to $"+Integer.toHexString( address )+" triggers breakpoint: "+bp);                   
                maybeTriggered( bp );
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
            return "Memory breakpoint $"+Integer.toHexString( this.address )+" ( read: "+isRead()+" , write: "+isWrite()+")";
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