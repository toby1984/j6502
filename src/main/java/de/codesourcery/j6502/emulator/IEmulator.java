package de.codesourcery.j6502.emulator;

public interface IEmulator
{
	public void setMemoryProvider(IMemoryProvider provider);

	public void reset();

	public void singleStep();

	public void start();

	public void stop();
}
