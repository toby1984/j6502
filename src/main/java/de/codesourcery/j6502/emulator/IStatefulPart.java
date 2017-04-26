package de.codesourcery.j6502.emulator;

public interface IStatefulPart
{
    public void restoreState(EmulationState state);
    
    public void saveState(EmulationState state);
}