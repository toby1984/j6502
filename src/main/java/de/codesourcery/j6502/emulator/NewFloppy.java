package de.codesourcery.j6502.emulator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.IOUtils;

import de.codesourcery.j6502.emulator.D64File.DirectoryEntry;
import de.codesourcery.j6502.utils.RingBuffer;

public class NewFloppy extends AbstractSerialDevice
{
	private static final int LOAD_CHANNEL= 0; // special channel used by kernel to issue LOAD commands to the floppy
	private static final int SAVE_CHANNEL= 1; // special channel used by kernel to issue SAVE commands to the floppy

	// key is channel #
	private final Map<Integer,ActiveTransfer> activeTransfers = new HashMap<>();

	private D64File disk;

	protected abstract class ActiveTransfer
	{
		/**
		 *
		 * @param channel
		 * @return <code>true</code> if more bytes have been added to the channel's send buffer, <code>false</code> if EOF has been reached
		 */
		public abstract boolean populateSendBuffer(Channel channel);
	}

	protected class SendTransfer extends ActiveTransfer
	{
		protected boolean eof;
		private final InputStream in;

		public SendTransfer(InputStream in) {
			this.in = in;
		}

		@Override
		public boolean populateSendBuffer(Channel channel)
		{
			if ( eof ) {
				return false;
			}

			final RingBuffer sendBuffer = channel.sendBuffer;
			while ( ! sendBuffer.isFull() )
			{
				int data;
				try
				{
					data = in.read();
				}
				catch (IOException e)
				{
					eof = true;
					IOUtils.closeQuietly( in );
					throw new RuntimeException("Failed to read from "+disk,e);
				}
				if ( data == -1 )
				{
					eof = true;
					IOUtils.closeQuietly( in );
					break;
				}
				sendBuffer.write( (byte) data );
			}
			return true;
		}
	};

	public NewFloppy(int primaryAddress)
	{
		super(primaryAddress);
		try {
			disk = new D64File("test.d64");
		}
		catch (IOException e)
		{
			throw new RuntimeException("Failed to open .d64 disk file: test.d64",e);
		}
	}

	/*
	 * ########## COMMAND: 0x28 => LISTEN #8
	 * ########## COMMAND: 0xf0 => OPEN #0
	 * ########## DATA: 0x24
	 * ########## COMMAND: 0x3f => UNLISTEN
	 * ########## COMMAND: 0x48 => TALK #8
	 * ########## COMMAND: 0x60 => OPEN_CHANNEL #0
	 */

	@Override
	protected boolean hasDataToSend()
	{
		fillSendBuffer();
		return getActiveChannel().sendBuffer.isNotEmpty();
	}

	private int bytesSent = 0; // TODO: Remove debug code

	@Override
	protected boolean getNextByte()
	{
		fillSendBuffer();
		currentByte = getActiveChannel().sendBuffer.read();
		if ( IECBus.DEBUG_DEVICE_LEVEL_VERBOSE )
		{
			bytesSent++;
			System.out.println("Sending byte no. "+bytesSent);
		}
		return getActiveChannel().sendBuffer.isNotEmpty();
	}

	private void fillSendBuffer()
	{
		ActiveTransfer transfer = activeTransfers.get( getActiveChannel().channelNo );
		if ( transfer == null )
		{
			transfer = processCommand( getActiveChannel().receiveBuffer.readFully() );
			activeTransfers.put( getActiveChannel().channelNo , transfer );
			transfer.populateSendBuffer( getActiveChannel() );
		}
		else
		{
			transfer.populateSendBuffer( getActiveChannel() );
		}
	}

	private ActiveTransfer processCommand(byte[] data)
	{
		if ( data.length > 0 && data[0] == 0x24) { // '$' => return directory
			System.out.println("====> Sending directory");
			return new SendTransfer(disk.createDirectoryInputStream());
		}

		final Optional<DirectoryEntry> entry = disk.getDirectoryEntry( data );
		if ( entry.isPresent() )
		{
			System.out.println("====> Sending file >"+entry.get().getFileNameAsASCII()+"<");
			return new SendTransfer( entry.get().createInputStream() );
		}
		return new SendTransfer( new ByteArrayInputStream(new byte[0] ) );
	}

	@Override
	protected void onCloseHook(int channelNo)
	{
		activeTransfers.remove( channelNo );
	}

	@Override
	protected boolean onOpenDataHook(int channelNo) {
		return true;
	}

	@Override
	protected boolean onOpenChannelHook(int channelNo)
	{
		if ( deviceState == DeviceState.TALK_REQUESTED )
		{
			fillSendBuffer();
			return getActiveChannel().sendBuffer.isNotEmpty();
		}
		return true;
	}

}