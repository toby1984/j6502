package de.codesourcery.j6502.utils;

import java.util.List;

/**
 * A text region.
 *
 * @author tobias.gierke@voipfuture.com
 */
public class TextRegion implements ITextRegion
{
    private int startingOffset;
    private int length;

    public TextRegion(List<? extends ITextRegion> ranges)
    {
    	if (ranges == null) {
			throw new IllegalArgumentException("ranges must not be NULL");
		}
    	if ( ranges.isEmpty() ) {
    		throw new IllegalArgumentException("ranges cannot be empty");
    	}
    	final ITextRegion first = ranges.get(0);
    	this.startingOffset = first.getStartingOffset();
    	this.length = first.getLength();

    	if ( ranges.size() > 1 ) {
    		merge( ranges.subList( 1 , ranges.size() ) );
    	}
    }

    @Override
    public boolean equals(Object obj) {
    	if ( obj instanceof ITextRegion) {
    		return this.startingOffset == ((ITextRegion) obj).getStartingOffset() &&
    			   this.length == ((ITextRegion) obj).getLength();
    	}
    	return false;
    }

    public static int hashCode(ITextRegion range)
    {
    	if ( range == null ) {
    		return 0;
    	}
    	return range.getStartingOffset()*13 + range.getLength();
    }

    public TextRegion(ITextRegion range) {
    	this( range.getStartingOffset() , range.getLength() );
    }

    public TextRegion(int startingOffset, int length)
    {
        if ( startingOffset < 0 ) {
            throw new IllegalArgumentException("startingOffset must not be >= 0");
        }
        if ( length < 0 ) {
            throw new IllegalArgumentException("length must not be >= 0");
        }
        this.startingOffset = startingOffset;
        this.length = length;
    }

    @Override
    public int getStartingOffset()
    {
        return startingOffset;
    }

    @Override
    public int getLength()
    {
        return length;
    }

    @Override
    public ITextRegion merge(ITextRegion other)
    {
    	// order of calculations is IMPORTANT here, otherwise it's yielding wrong results!
        final int newEnd = this.getEndOffset() > other.getEndOffset() ? this.getEndOffset() : other.getEndOffset();
        this.startingOffset = this.getStartingOffset() < other.getStartingOffset() ? this.getStartingOffset() : other.getStartingOffset();
        this.length = newEnd - this.startingOffset;
        return this;
    }

    @Override
    public void subtract(ITextRegion other)
    {
        if ( isSame( other ) )
        {
        	this.length =  0;
            return;
        }

        if ( other.getStartingOffset() == this.getStartingOffset() ) {
            // both ranges starts share the same starting offset
            final int length = this.getLength() - other.getLength();
            if ( length < 0 ) {
                throw new IllegalArgumentException("Cannot subtract range "+other+" that is longer than "+this);
            }
            this.startingOffset = other.getEndOffset();
            this.length = length;
            return;
        }
        else if ( other.getEndOffset() == this.getEndOffset() )
        {
            final int length = this.getLength() - other.getLength();
            if ( length < 0 ) {
                throw new IllegalArgumentException("Cannot subtract range "+other+" that starts before "+this);
            }
            this.length = length;
            return;
        }
        else if ( ! this.contains( other ) ) {
        	return;
        }
        throw new UnsupportedOperationException("Cannot calculate "+this+" MINUS "+other+" , would yield two non-adjactent ranges");
    }

    @Override
    public boolean contains(ITextRegion other)
    {
        return other.getStartingOffset() >= this.getStartingOffset() && other.getEndOffset() <= this.getEndOffset();
    }

    @Override
    public boolean overlaps(ITextRegion other)
    {
        return this.contains( other ) || other.contains( this ) ||
           ( ! this.contains( other.getStartingOffset() ) && this.contains( other.getEndOffset() ) ) ||
           ( this.contains( other.getStartingOffset() ) && ! this.contains( other.getEndOffset() ) );
    }

    @Override
	public void intersect(ITextRegion other) {

    	/*    |-- this --|
    	 * |-other-|
    	 */
    	if ( ! contains( other.getStartingOffset() ) && contains( other.getEndOffset()-1 ) ) {
    		// ORDER of calculations is important here!
    		this.length = other.getEndOffset() - this.getStartingOffset();
    		this.startingOffset = other.getStartingOffset();
    		return;
    	}

    	/*   |-- this --|
    	 *     |-other-|
    	 */
    	if ( contains( other.getStartingOffset() ) && contains( other.getEndOffset() ) ) {
    		this.startingOffset = other.getStartingOffset();
    		this.length = other.getLength();
    		return;
    	}

    	/*   |-- this --|
    	 *         |-other-|
    	 */
    	if ( contains( other.getStartingOffset() ) && ! contains( other.getEndOffset() ) ) {
    		this.length = getEndOffset() - other.getStartingOffset();
    		this.startingOffset = other.getStartingOffset();
    		return;
    	}

    	/*   |-- this --|
    	 * |-----other-----|
    	 */
    	if ( other.contains( getStartingOffset() ) && other.contains( getEndOffset() ) ) {
    		// this range already is the intersection
    		return;
    	}
    	throw new IllegalArgumentException( this+" has no intersection with "+other);
    }

    @Override
    public int getEndOffset()
    {
        return startingOffset+length;
    }

    @Override
    public boolean contains(int offset)
    {
        return offset >= this.getStartingOffset() && offset < this.getEndOffset();
    }

    @Override
    public boolean isSame(ITextRegion other)
    {
        return this == other || ( this.getStartingOffset() == other.getStartingOffset() && this.getLength() == other.getLength() );
    }

    @Override
    public String apply(String string)
    {
    	try {
    		return string.substring( getStartingOffset() , getEndOffset() );
    	} catch(final StringIndexOutOfBoundsException e) {
    		throw new StringIndexOutOfBoundsException("TextRegion out of bounds, cannot apply "+this+" to "+
    				" string of length "+string.length());
    	}
    }

    @Override
    public String toString()
    {
        return "["+getStartingOffset()+","+getEndOffset()+"[";

    }

	@Override
	public void merge(List<? extends ITextRegion> ranges)
	{
		if ( ranges.isEmpty() ) {
			return;
		}
		for ( final ITextRegion r : ranges ) {
			merge( r );
		}
	}
}