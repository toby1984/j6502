package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.utils.RingBuffer;


public abstract class SerialDevice 
{
	private boolean listening = false;
	private boolean talking = false;
	private final int deviceAddress;

	private final RingBuffer buffer = new RingBuffer();

	public static enum CommandType {
		LISTEN,TALK,UNLISTEN,UNTALK,OPEN_CHANNEL,OPEN,CLOSE;
	}

	public static class Command 
	{
		public final CommandType type;
		public final byte[] payload;

		public Command(CommandType type,byte[] payload) {
			this.type = type;
			this.payload = payload;
		}

		public boolean hasType(CommandType t) {
			return t.equals( this.type );
		}
	}

	public SerialDevice(int deviceAddress) 
	{
		this.deviceAddress = deviceAddress;
	}	

	public void reset() 
	{
		this.listening = false;
		this.talking = false;
		buffer.reset();
	}

	public int getPrimaryAddress() {
		return deviceAddress;
	}

	public void receive(byte data,IECBus bus) 
	{
		if ( data == 0x3f ) // UNLISTEN
		{
			if ( listening ) 
			{
				listening = false;
			}
			return;
		}
		if ( data == 0x5f ) // UNTALK
		{
			if ( talking ) {
				talking = false;
			}
			return;
		}		
		
		if ( ( data & 0x80) == 0x80 ) { // LISTEN
			int adr = data & ~0x80;
			if ( adr == getPrimaryAddress() ) {
				assertNotListening();
				listening = true;
			}
			return;
		} 
		if ( ( data & 0x40) == 0x40 ) { // TALK
			int adr = data & ~0x40;
			if ( adr == getPrimaryAddress() ) {
				assertNotTalking();
				talking = true;
			}
			return;
		} 
		
		if ( listening || talking ) 
		{
			buffer.write( data );
		}
	}
	
	protected abstract void processCommand(RingBuffer buffer);

	private void assertListening() {
		if ( ! listening ) {
			throw new IllegalStateException("Not listening ?");
		}
	}

	private void assertNotListening() {
		if ( listening ) {
			throw new IllegalStateException("Listening ?");
		}
	}	

	private void assertTalking() {
		if ( ! talking ) {
			throw new IllegalStateException("Not talking ?");
		}
	}

	private void assertNotTalking() {
		if ( talking ) {
			throw new IllegalStateException("Talking ?");
		}
	}	

	public boolean listen(int deviceAdress) 
	{
		if ( this.deviceAddress == deviceAdress ) 
		{
			assertNotListening();
			listening = true;
			return true;
		}
		return false;
	}

	public void unlisten() {
		assertListening();
		listening = false;
	}

	public void talk(int deviceAdress) 
	{
		if ( this.deviceAddress != deviceAdress ) {
			throw new IllegalArgumentException("Don't try talking to me as if I was device #"+deviceAdress+" , I'm #"+this.deviceAddress);
		}
		assertNotTalking();
		talking = true;
	}

	public void untalk() 
	{
		assertTalking();
		talking = false;
	}	

	public boolean isListening() {
		return listening;
	}

	public boolean isTalking() {
		return talking;
	}
}