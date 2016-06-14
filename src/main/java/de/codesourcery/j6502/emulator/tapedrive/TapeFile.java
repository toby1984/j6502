package de.codesourcery.j6502.emulator.tapedrive;

import java.io.File;
import java.io.IOException;

public interface TapeFile 
{
    public static TapeFile load(File file) throws IOException 
    {
        final String name = file.getName().toLowerCase();
        if ( name.endsWith(".t64" ) ) {
            return new T64File( file );
        }
        if ( name.endsWith(".tap" ) ) {
            return new TAPFile( file );
        }
        throw new IllegalArgumentException("Unrecognized file extension: "+file.getName());
    }
}
