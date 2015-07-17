package de.codesourcery.j6502.emulator;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.emulator.D64File.DirectoryEntry;
import de.codesourcery.j6502.utils.RingBuffer;

public class Floppy extends AbstractSerialDevice
{
	private static final int LOAD_CHANNEL = 0;
	
	// key is channel #
	private final Map<Integer,ActiveTransfer> activeTransfers = new HashMap<>();
	
	private D64File disk;
	private DriveMessage driveMessage=new DriveMessage( MessageCode.BOOT_MSG );
	
	public static enum MessageCode
	{
		OK("OK",0,false),
		BOOT_MSG("CBM DOS V2.6 1541",73),
		DRIVE_NOT_READY("DRIVE NOT READY",74);
		
		public final String msg;
		public final int errorCode;
		public final boolean isError;
		
		private MessageCode(String msg,int errorCode) {
			this(msg,errorCode,true);
		}
		
		private MessageCode(String msg,int errorCode,boolean isError) {
			this.msg = msg;
			this.errorCode = errorCode;
			this.isError = isError;
		}
	}
	
	public static final class DriveMessage 
	{
		public final MessageCode errorCode;
		public final String message;
		public final int track;
		public final int sector;
		
		public DriveMessage(MessageCode errorCode) 
		{
			this(errorCode,0,0);
		}
		
		public DriveMessage(MessageCode errorCode, int track, int sector) 
		{
			this.errorCode = errorCode;
			this.message = errorCode.msg;
			this.track = track;
			this.sector = sector;
		}

		public boolean isError() {
			return errorCode.isError;
		}
		
		@Override
		public String toString() 
		{
			return pad( errorCode.errorCode )+","+message+","+pad(track)+","+pad(sector);
		}
		
		private String pad(int i) 
		{
			return StringUtils.leftPad( Integer.toString(i) , 2 , "0" );
		}
	}

	protected abstract class ActiveTransfer
	{
		/**
		 *
		 * @param channel
		 * @return <code>true</code> if more bytes have been added to the channel's send buffer, <code>false</code> if EOF has been reached
		 */
		public abstract boolean populateSendBuffer(Channel channel);
		
		public abstract boolean isEOF();
	}

	protected final class EmptyTransfer extends ActiveTransfer 
	{
		@Override
		public boolean populateSendBuffer(Channel channel) {
			return false;
		}
		
		@Override
		public boolean isEOF() {
			return true;
		}
	}
	
	protected class SendTransfer extends ActiveTransfer
	{
		protected boolean eof;
		private final InputStream in;
		
		private int bytesWrittenToSendBuffer;
		private final int sectorsToSend;

		public SendTransfer(InputStream in) {
			this.in = in;
			this.sectorsToSend = -1; // unknown count
		}
		
		public SendTransfer(InputStream in,int sectorsToSend) {
			this.in = in;
			this.sectorsToSend = sectorsToSend;
		}
		
		@Override
		public boolean isEOF() {
			return eof;
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
				bytesWrittenToSendBuffer++;
				if ( (bytesWrittenToSendBuffer % 254) == 0 ) 
				{
					if ( sectorsToSend == -1 ) {
						System.out.println("Transferred "+(bytesWrittenToSendBuffer/254)+" sectors ...");
					} else {
						System.out.println("Transferred "+(bytesWrittenToSendBuffer/254)+" sectors of "+sectorsToSend+" ...");
					}
				}
			}
			return true;
		}
	};

	public Floppy(int primaryAddress)
	{
		super(primaryAddress);
		try {
			disk = new D64File("basic.d64");
		} catch (IOException e) {
			e.printStackTrace();
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

	@Override
	protected boolean getNextByte()
	{
		fillSendBuffer();
		currentByte = getActiveChannel().sendBuffer.read();
		return getActiveChannel().sendBuffer.isNotEmpty();
	}
	
	private ActiveTransfer getActiveTransfer() {
		return activeTransfers.get( getActiveChannel().channelNo );
	}

	private void fillSendBuffer()
	{
		ActiveTransfer transfer = getActiveTransfer();
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
		if ( getActiveChannel().channelNo == 15 && getDeviceState() == DeviceState.TALK_REQUESTED ) 
		{
			if ( IECBus.DEBUG_DEVICE_LEVEL ) {
				System.out.println("*** Caller is reading error channel: "+driveMessage);
			}
			return new SendTransfer( new ByteArrayInputStream( driveMessage.toString().getBytes() ) );
		}
		
		if ( data.length > 0 && data[0] == 0x24) { // '$' => return directory
			System.out.println("====> Sending directory");
			if ( ! isDiskInserted() ) 
			{
				error( MessageCode.DRIVE_NOT_READY );
				return new EmptyTransfer();
			}
			return new SendTransfer(disk.createDirectoryInputStream());
		}
		
		// TODO: Handle other commands as well...

		if ( ! isDiskInserted() ) 
		{
			error( MessageCode.DRIVE_NOT_READY );
			return new EmptyTransfer();
		}
		
		final Optional<DirectoryEntry> entry = disk.getDirectoryEntry( data );
		if ( entry.isPresent() )
		{
			System.out.println("====> Sending file >"+entry.get().getFileNameAsASCII()+" with "+entry.get().getFileSizeInSectors()+" sectors<");
			return new SendTransfer( entry.get().createInputStream() , entry.get().getFileSizeInSectors() );
		}
		return new EmptyTransfer();
	}
	
	private void error(MessageCode code) {
		driveMessage = new DriveMessage( code , 0 , 0 );
	}	
	
	protected void error(MessageCode code,int track,int sector) {
		driveMessage = new DriveMessage( code , track , sector );
	}
	
	protected void clearError() 
	{
		driveMessage=new DriveMessage( MessageCode.OK );
	}

	@Override
	protected void onCloseHook(int channelNo)
	{
		activeTransfers.remove( channelNo );
	}

	@Override
	protected boolean onOpenDataHook(int channelNo) 
	{
		if ( deviceState == DeviceState.TALK_REQUESTED )
		{
			fillSendBuffer();
			return getActiveChannel().sendBuffer.isNotEmpty();
		}
		return true;
	}

	@Override
	protected boolean onOpenChannelHook(int channelNo)
	{
		if ( deviceState == DeviceState.TALK_REQUESTED )
		{
			if ( channelNo == LOAD_CHANNEL ) { // LOAD channel
				ActiveTransfer t = getActiveTransfer();
				if ( t != null && t.isEOF() && getActiveChannel().sendBuffer.isEmpty() ) {
					activeTransfers.remove( channelNo );
				}
			}
			fillSendBuffer();
			return getActiveChannel().sendBuffer.isNotEmpty();
		}
		return true;
	}
	
	public boolean isDiskInserted() {
		return this.disk != null;
	}
	
	public void insertDisk(D64File file) 
	{
		if ( file == null ) {
			throw new IllegalArgumentException("file cannot be null");
		}
		if ( isBusy() ) {
			throw new IllegalStateException("Cannot insert disk, device is currently busy");
		}
		this.disk = file;
	}

	public D64File getDisk() {
		return disk;
	}

	public void ejectDisk() 
	{
		if ( disk != null && isBusy() ) {
			throw new IllegalStateException("Cannot eject disk, device is currently busy");
		}
		this.disk = null;
	}
	
	public boolean hasError() {
		return driveMessage != null && driveMessage.isError(); // TODO: Some 'errors' actually aren't
	}
	
	public DriveMessage getDriveMessage() {
		return driveMessage;
	}
	
	@Override
	protected void resetHook() 
	{
		driveMessage=new DriveMessage( MessageCode.BOOT_MSG );
	}
}