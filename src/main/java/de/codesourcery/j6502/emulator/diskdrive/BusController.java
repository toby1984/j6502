package de.codesourcery.j6502.emulator.diskdrive;

import de.codesourcery.j6502.emulator.AddressRange;

public class BusController extends VIA 
{

    /* 
     * 1541 memory layout
     * 
     * $0000
     * $0800
     * ...
     * $1800  Buscontroller VIA 6522
     * $1c00  Diskcontroller VIA 6522
     * $2000   ???
     * $c000  ROM  
     * $ffff  ROM
     */
    public BusController() {
        super("BusController VIA 6522", new AddressRange( 0x1800 , 0x1810 ) );
    }
}