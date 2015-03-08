package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.utils.RingBuffer;


public abstract class SerialDevice 
{
	private boolean listening = false;
	private boolean talking = false;
	private final int deviceAddress;
	
	private IECBus bus;

	private final RingBuffer receiveBuffer = new RingBuffer();

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
	
	protected final IECBus getBus() {
		return bus;
	}
	
	public final void onAttach(IECBus bus) 
	{
		if (bus == null) {
			throw new IllegalArgumentException("bus must not be NULL");
		}
		this.bus = bus;
	}

	public SerialDevice(int deviceAddress) 
	{
		this.deviceAddress = deviceAddress;
	}	

	public void reset() 
	{
		this.listening = false;
		this.talking = false;
		receiveBuffer.reset();
	}

	public int getPrimaryAddress() {
		return deviceAddress;
	}

	public final void receive(byte data) 
	{
		if ( data == 0x3f ) // UNLISTEN
		{
			if ( listening ) 
			{
				unlisten();
			}
			return;
		}
		if ( data == 0x5f ) // UNTALK
		{
			if ( talking ) {
				untalk();
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
			receiveBuffer.write( data );
		}
	}
	
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

	public final void unlisten() {
		assertListening();
		
		listening = false;
		
		try {
			onUnlisten( receiveBuffer );
		} 
		finally {
			receiveBuffer.reset();
		}
	}

	public final void talk(int deviceAdress) 
	{
		if ( this.deviceAddress != deviceAdress ) {
			throw new IllegalArgumentException("Don't try talking to me as if I was device #"+deviceAdress+" , I'm #"+this.deviceAddress);
		}
		assertNotTalking();
		talking = true;
	}

	public final void untalk() 
	{
		assertTalking();
		talking = false;
		
		try {
			onUntalk( receiveBuffer );
		} 
		finally {
			receiveBuffer.reset();
		}
	}	

	public final boolean isListening() {
		return listening;
	}

	public final boolean isTalking() {
		return talking;
	}
	
	public abstract void onATN();
	
	public abstract void tick();
	
	protected abstract void onUntalk(RingBuffer receiveBuffer);
	
	protected abstract void onUnlisten(RingBuffer receiveBuffer);
}