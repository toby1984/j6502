package de.codesourcery.j6502.emulator.tapedrive;

import org.apache.commons.lang.Validate;

public class WavePeriod
{
    /*
     * http://c64tapes.org/dokuwiki/doku.php?id=loaders:rom_loader
     * http://www.atarimagazines.com/compute/issue57/turbotape.html
     * http://c64tapes.org/dokuwiki/doku.php?id=analyzing_loaders 
     *         
     * CPU freq: 985248 Hz
     * 
     * (S)hort  : 2840 Hz
     * (M)edium : 1953 Hz
     * (L)ong   : 1488 Hz
     * 
     * Default timing thresholds at start of algorithm (speed constant $B0 contains 0)
     * 
     * => short pulse = 240-432 cycles , avg. 336 cycles .... 2280 Hz - 4105 Hz  
     * => medium pulse = 432-584 cycles , avg. 508 cycles ...1687 Hz - 2280 Hz
     * => long pulse = 584-760 cycles, avg. 672 cycles .. 1296 Hz - 1687  
     * 
     * C64 TAP files:
     * 
     * (S)hort  : TAP value $30 (48)
     * (M)edium : TAP value $42 (66)
     * (L)ong   : TAP value $56 (86)
     * 
     *  (48 * 8) / 985248 =  2565,75 Hz   =  384 cycles
     *  (66 * 8) / 985248 =  1866 Hz      =  528 cycles
     *  (86 * 8) / 985248 =  1432,0465 Hz =  688 cycles
     */
    
    public static final WavePeriod SHORT = new WavePeriod(Type.SHORT, 384 );
    public static final WavePeriod MEDIUM = new WavePeriod(Type.SHORT, 528 );
    public static final WavePeriod LONG = new WavePeriod(Type.SHORT, 688 );
    public static final WavePeriod SILENCE_SHORT = new WavePeriod(Type.SILENCE, 325131 );
    public static final WavePeriod SILENCE_LONG = new WavePeriod(Type.SILENCE, 325131*3 );
    
    public enum Type 
    {
        SHORT,LONG,MEDIUM,SILENCE,CUSTOM;
    }

    public final Type type;
    public final int durationInTicks;
    
    public WavePeriod(Type type,int ticks) 
    {
        Validate.notNull(type, "type must not be NULL");
        this.type = type;
        this.durationInTicks = ticks;
    }

    @Override
    public String toString() {
        return type+" ("+durationInTicks+" cycles)";
    }
    
    @Override
    public boolean equals(Object obj) {
        throw new RuntimeException("Equals() called?");
    }
    
    @Override
    public int hashCode() {
        throw new RuntimeException("hashCode() called?");
    }
    
    public boolean hasType(Type t) {
        return t.equals(this.type);
    }

    public void onEnter(SquareWaveGenerator state) 
    {
        if ( type == Type.SILENCE ) {
            state.currentTicks = durationInTicks;
        } else {
            state.currentTicks = durationInTicks/2; // square wave with 50% duty cycle
            state.signalAtStartOfWave = state.currentSignal;            
        }
    }

    public boolean tick(SquareWaveGenerator state)
    {
        // just wait without toggling signal line
        if ( type == Type.SILENCE ) {
            state.currentTicks--;
            if ( state.currentTicks <= 0 ) 
            {
                return true; // advance to next state
            }
            return false;
        }
        
        state.currentTicks--;
        if ( state.currentTicks == 0 ) 
        {
            state.currentSignal = ! state.currentSignal;
            state.currentTicks = durationInTicks/2; // square wave with 50% duty cycle
            if ( state.currentSignal == state.signalAtStartOfWave ) 
            {
                return true; // advance to next state
            }
        }
        return false;        
    }
}
