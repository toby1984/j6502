package de.codesourcery.j6502.emulator.diskdrive;

import de.codesourcery.j6502.emulator.AddressRange;

public class DiskController extends VIA {

    public DiskController() {
        super("DiskController VIA 6522", AddressRange.range( 0x1c00 , 0x1c10) ); 
        // TODO Auto-generated constructor stub
    }

}
