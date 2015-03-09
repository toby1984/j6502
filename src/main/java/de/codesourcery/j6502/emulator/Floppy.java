package de.codesourcery.j6502.emulator;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.emulator.D64File.DirectoryEntry;
import de.codesourcery.j6502.utils.CharsetConverter;
import de.codesourcery.j6502.utils.HexDump;
import de.codesourcery.j6502.utils.RingBuffer;

public final class Floppy extends SerialDevice
{
	protected static final boolean DEBUG_VERBOSE = true;

	private Map<Integer,FileDesc> openFileDescriptors = new HashMap<>();

	protected final RingBuffer receiveBuffer = new RingBuffer();

	private D64File media;

	protected long cycle;

	protected long waitingStartedAtCycle;
	protected long cyclesWaited = 0;

	protected int activeSecondaryAddress = 0;

	protected ActiveTransfer activeTransfer;
	protected Map<Integer,ActiveTransfer> activeTransfers = new HashMap<>();

	private FloppyState previousState;
	private FloppyState floppyState;

	// states

	protected final FloppyState SEND_UNTALK;

	protected final FloppyState SEND_FILE;

	protected final FloppyState TALKING;

	protected final FloppyState OPEN_UNLISTEN;
	protected final FloppyState OPEN;

	protected final FloppyState LISTENING;
	protected final FloppyState WAITING_FOR_CMD;

	/**
	 * File descriptor.
	 */
	protected final class FileDesc
	{
		public final int channel;
		public final byte[] name;

		public FileDesc(int channel,byte[] name)
		{
			this.channel = channel;
			this.name = new byte[ name.length ];
			System.arraycopy( name , 0 , this.name , 0 ,name.length );
		}

		public InputStream getInputStream() {
			if ( media == null ) {
				throw new IllegalStateException("getInputStream() called although no media present ?");
			}
			
			if ( name.length == 1 && name[0] == 0x24 ) {
				return media.createDirectoryInputStream();
			}
			return media.getDirectoryEntry( name ).orElseThrow( () -> new RuntimeException("File not found: "+this)).createInputStream();
		}
	}

	/**
	 * Internal state.
	 */
	protected abstract class FloppyState {

		private String identifier;

		public FloppyState(String identifier) { this.identifier = identifier; }

		public void onEnter() { }

		protected final boolean isOpenChannel(byte data) { return ( data & 0b1111_0000) == 0x60; } // OPEN CHANNEL + secondary address
		protected final boolean isOpen(byte data) { return ( data     & 0b1111_0000) == 0xf0; } // OPEN + secondary address
		protected final boolean isClose(byte data) { return ( data    & 0b1111_0000) == 0xe0; } // CLOSE + secondary address
		protected final boolean isTalk(byte data) { return ( ( data   & 0b1110_0000) == 0x40 ) && (( data & 0b0001_1111) == getPrimaryAddress()); } // TALK + primary address
		protected final boolean isUntalk(byte data) { return ( data   & 0xff) == 0x5f; } // UNTALK
		protected final boolean isListen(byte data) { return ( ( data & 0b1111_0000) == 0x20) && (( data  & 0b0000_1111) == getPrimaryAddress()); } // LISTEN + primary address
		protected final boolean isUnlisten(byte data) { return ( data & 0xff) == 0x3f; } // UNLISTEN

		public abstract void receive(byte data,boolean eoi);

		protected final void startWaiting()
		{
			waitingStartedAtCycle = cycle;
			cyclesWaited = 0;
		}

		public final void tick(IECBus bus) 
		{ 
			final long delta = Math.abs( cycle - waitingStartedAtCycle );
			cyclesWaited += delta;
			waitingStartedAtCycle = cycle;
			tickHook(bus);
		}
		
		protected void tickHook(IECBus bus) {
			
		}

		@Override
		public String toString() { return identifier; }
	}

	/**
	 *
	 */
	protected abstract class ActiveTransfer
	{
		private final RingBuffer sendBuffer = new RingBuffer();

		private final RingBuffer tmpBuffer = new RingBuffer();

		public final FileDesc fileDesc;

		private boolean eof;
		private InputStream in;

		private final byte[] tmp = new byte[1024];

		public ActiveTransfer(FileDesc desc) {
			Validate.notNull(desc, "desc must not be NULL");
			this.fileDesc = desc;
		}

		public void close() {
			if ( in != null ) {
				IOUtils.closeQuietly(in);
			}
		}

		private void fillInternalBuffer() throws IOException
		{
			if ( in == null ) {
				in = fileDesc.getInputStream();
			}

			final int bytesToRead = Math.min( tmp.length , sendBuffer.getRemainingBytesFree() );
			final int bytesRead = in.read( tmp , 0 , bytesToRead);
			if ( bytesRead <= 0 ) {
				IOUtils.closeQuietly( in );
				eof = true;
				return;
			}
			for ( int i = 0 ; i < bytesRead ; i++ ) {
				tmpBuffer.write( tmp[i] );
			}
		}

		private void fillBuffer() throws IOException
		{
			if ( eof || sendBuffer.isFull() ) {
				return;
			}

			if ( tmpBuffer.getBytesAvailable() <2 )
			{
				fillInternalBuffer();
			}

			while ( ! ( tmpBuffer.isEmpty() || sendBuffer.isFull() ) )
			{
				sendBuffer.write( tmpBuffer.read() );
			}
		}

		public void onTick(IECBus bus)
		{
			if ( ! bus.isSendBufferFull() )
			{
				try {
					fillBuffer();
				} catch (IOException e) {
					throw new RuntimeException("Failed to read from "+fileDesc,e);
				}

				final boolean sendEOI = sendBuffer.getBytesAvailable() == 1; // EOI : marks the last data byte before a UNLISTEN / UNTALK command is being sent
				final byte data = sendBuffer.read();
				bus.send( data , sendEOI , false );

				if ( sendEOI ) {
					allBytesQueued();
				}
			}
		}

		protected abstract void allBytesQueued();
	}

	public Floppy(int deviceAddress)
	{
		super(deviceAddress);
		
		try {
			this.media = new D64File( "test.d64" );
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		SEND_UNTALK = new FloppyState("SEND_UNTALK")
		{
			@Override
			public void receive(byte data, boolean eoi)
			{
				if ( getBus().isSendBufferEmpty() )
				{
					if ( DEBUG_VERBOSE ) {
						System.out.println( "["+floppyState+"]: Sending UNTALK");
					}					
					getBus().send( (byte) 0x5f , false , false ); // UNTALK
					setState( WAITING_FOR_CMD );
				}
			}
		};

		SEND_FILE = new FloppyState("SEND_FILE")
		{
			@Override
			public void receive(byte data, boolean eoi)
			{
				// someone else is talking while we're sending ??
				throw new IllegalStateException("This state is not supposed to receive data");
			}

			@Override
			public void tickHook(IECBus bus)
			{
				activeTransfer.onTick( bus ); // will advance state to SEND_UNTALK when all bytes have been queued to the buses sendBuffer
			}
			
			@Override
			public void onEnter() {

				if ( activeTransfers.containsKey( activeSecondaryAddress ) ) {
					throw new IllegalStateException("There still is an active transfer (send) on channel #"+activeSecondaryAddress);
				}
				final FileDesc fileDesc = openFileDescriptors.get( activeSecondaryAddress );
				if ( fileDesc == null ) {
					throw new IllegalStateException("Internal error, no open file descriptor on channel #"+activeSecondaryAddress);
				}

				if ( DEBUG_VERBOSE ) {
					System.out.println( "["+floppyState+"]: Initiating SEND for channel #"+activeSecondaryAddress+" , file: "+fileDesc);
				}
				activeTransfer =  new ActiveTransfer( fileDesc )
				{
					@Override
					protected void allBytesQueued()
					{
						if ( DEBUG_VERBOSE ) {
							System.out.println( "["+floppyState+"]: Finished SEND for channel #"+activeSecondaryAddress+" , file: "+fileDesc+", queueing UNTALK");
						}
						setState( SEND_UNTALK );
					}
				};
				activeTransfers.put( activeSecondaryAddress , activeTransfer );
			}
		};

		TALKING= new FloppyState("TALK")
		{
			@Override
			public void receive(byte data, boolean eoi)
			{
				if ( isOpenChannel( data ) )
				{
					int secondaryAddress = data & 0b0000_1111;

					if ( DEBUG_VERBOSE ) {
						System.out.println( "["+floppyState+"]: OPEN CHANNEL #"+secondaryAddress);
					}

					if ( ! openFileDescriptors.containsKey( secondaryAddress ) ) {
						throw new IllegalStateException("Failed to OPEN CHANNEL #"+secondaryAddress+" , not open");
					}
					activeSecondaryAddress = secondaryAddress;
					// I expected LISTEN -> OPEN "...." -> UNLISTEN at this point
					if ( ! openFileDescriptors.containsKey( activeSecondaryAddress ) ) {
						throw new IllegalStateException("OPEN CHANNEL #"+activeSecondaryAddress+" after TALK but no previous OPEN for this descriptor - don't know what to send");
					}
					setState( SEND_FILE );
				} else {
					throw new RuntimeException("Unrecognized command byte after TALK: "+HexDump.toHex( data ) );
				}
			}
		};

		OPEN_UNLISTEN= new FloppyState("OPEN_UNLISTEN") {

			@Override
			public void receive(byte data, boolean eoi)
			{
				if ( isUnlisten( data ) )
				{
					final byte[] fileName = drainBuffer( receiveBuffer );
					if ( DEBUG_VERBOSE ) {
						System.out.println( "["+floppyState+"]: Received file name '"+CharsetConverter.petToString( fileName , 0 , fileName.length));
					}
					openFileDesc( activeSecondaryAddress , fileName );
					setState( WAITING_FOR_CMD );
				} else {
					throw new IllegalStateException(this+" received unrecognized command byte after LISTEN: "+HexDump.toHex( data ) );
				}
			}
		};

		OPEN  = new FloppyState("OPEN")
		{
			@Override
			public void receive(byte data,boolean eoi)
			{
				receiveBuffer.write( data );
				if ( eoi )
				{
					setState( OPEN_UNLISTEN );
				}
			}

			@Override
			public void onEnter()
			{
				receiveBuffer.reset();
			}
		};

		LISTENING  = new FloppyState("LISTENING") {

			@Override
			public void receive(byte data,boolean eoi)
			{
				if ( isOpen( data ) ) { // 0xf0 + secondary address
					activeSecondaryAddress = data & 0b0000_1111;
					if ( DEBUG_VERBOSE ) {
						System.out.println( "["+floppyState+"]: OPEN #"+activeSecondaryAddress);
					}
					setState( OPEN );
				}
				else if ( isClose( data ) ) { // 0xe0 + secondary address
					final int secondaryAddress = data & 0b0000_1111;

					if ( DEBUG_VERBOSE ) {
						System.out.println( "["+floppyState+"]: CLOSE #"+activeSecondaryAddress);
					}
					final ActiveTransfer transfer = activeTransfers.remove( secondaryAddress );
					if ( transfer != null ) {
						if ( activeTransfer == transfer ) {
							activeTransfer.close();
							activeTransfer = null;
						}
					}
					openFileDescriptors.remove( secondaryAddress );
				}
				else if ( isUnlisten( data ) ) // 0x3f
				{
					setState( WAITING_FOR_CMD );
				} else {
					throw new RuntimeException(this+" received unrecognized command byte after LISTEN: "+HexDump.toHex( data ) );
				}
			}
		};

		WAITING_FOR_CMD = new FloppyState("WAITING_FOR_CMD") {

			@Override
			public void receive(byte data,boolean eoi)
			{
				if ( isListen( data ) ) {
					setState( LISTENING );
				} else if ( isTalk( data ) ) {
					setState( TALKING );
				}
			}
		};
		this.previousState = this.floppyState = WAITING_FOR_CMD;
	}

	@Override
	public void onATN() {
		// TODO: Suspend sending & current state if the device is currently sending & switch to state LISTENING,
		// TODO: Resuming operation after realizing that the command is not intended for this device
	}

	protected void setState(FloppyState s)
	{
		if ( this.previousState != s )
		{
			if ( DEBUG_VERBOSE ) {
				System.out.println("FLOPPY: Transitioning "+this.previousState+" -> "+s);
			}
			this.previousState = this.floppyState;
			this.floppyState = s;
			this.floppyState.onEnter();
		}
	}

	protected void openFileDesc( int secondaryAddress , byte[] name )
	{
		if ( openFileDescriptors.containsKey( secondaryAddress ) ) {
			throw new IllegalStateException("File descriptor "+openFileDescriptors.get( secondaryAddress )+" still open?");
		}
		final FileDesc desc = new FileDesc( secondaryAddress , name );
		openFileDescriptors.put( secondaryAddress , desc );
	}

	protected byte[] drainBuffer(RingBuffer b) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		while ( ! b.isEmpty() ) {
			out.write( b.read() );
		}
		return out.toByteArray();
	}

	@Override
	protected void tickHook(IECBus bus)
	{
		floppyState.tick( bus );
		cycle++;
	}

	@Override
	public void receive(byte data,boolean eoi)
	{
		if ( DEBUG_VERBOSE ) {
			System.out.println("[" + this.floppyState+"] Received: "+HexDump.toHex( data )+" [ EOI: "+eoi+" ]");
		}
		this.floppyState.receive( data , eoi );
	}

	@Override
	public void reset()
	{
		this.previousState = this.floppyState = WAITING_FOR_CMD;
		receiveBuffer.reset();
	}

	protected InputStream openFileForReading(FileDesc desc)
	{
		if ( media == null ) {
			throw new RuntimeException("Cannot open "+desc+" - no media");
		}
		if ( desc.name.length == 1 && ( desc.name[0] & 0xff) == 0x24 ) // special case: OPEN "$"
		{
			return media.createDirectoryInputStream();
		}
		final DirectoryEntry dirEntry = media.getDirectoryEntry( desc.name ).orElseThrow( () -> new RuntimeException("File not found: "+desc ) );
		return dirEntry.createInputStream();
	}

	public void insertMedia(D64File media) {
		Validate.notNull(media, "media must not be NULL");
		this.media = media;
	}

	public void ejectMedia() {
		this.media = null;
	}
}