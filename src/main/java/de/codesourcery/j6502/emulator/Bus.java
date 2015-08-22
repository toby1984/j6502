package de.codesourcery.j6502.emulator;

public interface Bus 
{
    /**
     * Returns the bus width in bits.
     * 
     * @return
     */
    public int getWidth();
    
    /**
     * Returns the wire name for each of the bus bits.
     * 
     * @return wire names, first element belongs to bit 0, second to bit 1 etc.
     */
    public String[] getWireNames();
    
    /**
     * Returns the current bus state.
     * @return bus state, first bit belongs to wire 0, second bit belongs to wire 1
     */
    public int read();
}
