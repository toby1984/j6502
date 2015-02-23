package de.codesourcery.j6502.ui;

import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.ui.Debugger.Mode;

public abstract class EmulatorDriver extends Thread
{
	private static final AtomicLong CMD_ID = new AtomicLong(0);
	private static final long CALLBACK_INVOKE_CYCLES = 1000;

	public volatile Throwable lastException;

	private final AtomicReference<Mode> currentMode = new AtomicReference<Mode>(Mode.SINGLE_STEP);

	private final Emulator emulator;

	private final ArrayBlockingQueue<Cmd> requestQueue = new ArrayBlockingQueue<>(10);
	private final ArrayBlockingQueue<Cmd> ackQueue = new ArrayBlockingQueue<>(10);

	protected Cmd startCommand(boolean ackRequired) {
		return new Cmd(CmdType.START,ackRequired);
	}

	protected Cmd stopCommand(boolean ackRequired) {
		return new Cmd(CmdType.STOP,ackRequired);
	}

	protected Cmd stepReturnCommand(boolean ackRequired) {
		return new Cmd(CmdType.STEP_OVER,true);
	}

	protected static enum CmdType { START , STOP , STEP_OVER }

	protected final class Cmd
	{
		public final long id = CMD_ID.incrementAndGet();
		private final CmdType type;
		private final boolean ackRequired;

		public Cmd(CmdType type,boolean ackRequired) {
			this.type = type;
			this.ackRequired = ackRequired;
		}

		public boolean isAckRequired() {
			return ackRequired;
		}

		public boolean isStepReturn() {
			return this.type.equals( CmdType.STEP_OVER );
		}

		public boolean isStartCmd() {
			return this.type.equals( CmdType.START );
		}

		public boolean isStopCmd() {
			return this.type.equals( CmdType.STOP );
		}

		public void enqueue()
		{
			try {
				System.out.println("ENQUEUE: "+this);
				requestQueue.put( this );
			} catch (final InterruptedException e) {
				throw new RuntimeException(e);
			}
		}

		public void ack()
		{
			if ( ackRequired ) {
				try {
					System.out.println("ENQUEUE ACK: "+this);
					ackQueue.put( this );
				} catch (final InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}

		@Override
		public String toString()
		{
			final String sCmd;
			if ( isStartCmd() ) {
				sCmd = "START_CMD";
			} else if ( isStopCmd() ) {
				sCmd = "STOP_CMD";
			} else {
				sCmd = "STEP_OVER";
			}
			return sCmd+"{"+id+"} , ack required: "+ackRequired;
		}
	}

	public EmulatorDriver(Emulator emulator)
	{
		this.emulator = emulator;
		setDaemon(true);
		setName("emulator-thread");
	}

	public Mode getMode()
	{
		return currentMode.get();
	}

	public void stepReturn()
	{
		if ( canStepOver() )
		{
			sendCmd( stepReturnCommand(true) );
		}
	}

	public void setMode(Mode newMode)
	{
		final Cmd cmd;
		if ( Mode.CONTINOUS.equals( newMode ) )
		{
			cmd = startCommand(true);
		} else {
			cmd = stopCommand(true);
		}
		sendCmd( cmd );
	}

	private void sendCmd(Cmd cmd)
	{
		cmd.enqueue();

		while ( cmd.isAckRequired() )
		{
			System.out.println("Waiting for ack of "+this);
			try {
				final Cmd acked = ackQueue.take();
				System.out.println("Got ack for "+acked);
				if ( acked.id == cmd.id ) {
					break;
				}
				ackQueue.put( acked );
			} catch (final InterruptedException e) {
				e.printStackTrace();
			}
		}
	}

	protected final void onStart()
	{
		System.out.println("*** onStart()");
		this.currentMode.set( Mode.CONTINOUS );
		onStartHook();
	}

	protected abstract void onStartHook();

	protected final void onStop(Throwable t)
	{
		System.out.println("*** onStop()");
		this.currentMode.set( Mode.SINGLE_STEP );
		onStopHook( t );
	}

	protected abstract void onStopHook(Throwable t);

	protected abstract void tick();

	@Override
	public void run()
	{
		final CPU cpu = emulator.getCPU();

		boolean justStarted = true;
		boolean isRunnable = false;

		long cyclesRemaining = CALLBACK_INVOKE_CYCLES;
		Short stopAtPc = null;
		while( true )
		{
			if ( isRunnable )
			{
				final Cmd cmd = requestQueue.poll();
				if ( cmd != null )
				{
					System.out.println("Thread received request: "+cmd);
					cmd.ack();
					if ( cmd.isStopCmd() ) {
						isRunnable = false;
						continue;
					}
				}
			}
			else
			{
				stopAtPc = null;
				if ( ! justStarted ) {
					onStop( null );
				}
				justStarted = false;
				while ( ! isRunnable )
				{
					System.out.println("Thread stopped,waiting for cmd");
					try {
						final Cmd cmd = requestQueue.take();
						System.out.println("Thread received request: "+cmd);
						cmd.ack();

						if ( cmd.isStartCmd() || cmd.isStepReturn() )
						{
							isRunnable = true;
							if ( cmd.isStepReturn() )
							{
								synchronized( emulator ) {
									stopAtPc = (short) (emulator.getCPU().pc+3);
								}
							} else {
								stopAtPc = null;
							}
						}
					} catch (final InterruptedException e) {
						continue;
					}
				}
				lastException = null;
				cyclesRemaining = CALLBACK_INVOKE_CYCLES;
				onStart();
			}

			Throwable exception = null;
			synchronized( emulator )
			{
				final long cycles = cpu.cycles;
				try {
					lastException = null;
					emulator.singleStep();
					final long elapsedCycles = cpu.cycles - cycles;
					cyclesRemaining -= elapsedCycles;
					if ( stopAtPc != null && cpu.pc == stopAtPc.shortValue() )
					{
						isRunnable = false;
						sendCmd( stopCommand( false ) );
					}
				}
				catch(final Throwable e)
				{
					e.printStackTrace();
					exception = e;
					stopAtPc = null;
				}
			}

			if ( cyclesRemaining <= 0 )
			{
				tick();
			}

			if ( exception != null )
			{
				lastException = exception;
				cyclesRemaining = 0;
				sendCmd( stopCommand( false ) );
			}
		}
	}

	public void singleStep() throws RuntimeException
	{
		setMode(Mode.SINGLE_STEP);

		synchronized( emulator )
		{
			lastException = null;
			emulator.singleStep();
		}
	}

	public Throwable getLastException() {
		return lastException;
	}

	public boolean canStepOver()
	{
		if ( getMode() == Mode.SINGLE_STEP )
		{
			synchronized( emulator )
			{
				final int op = emulator.getMemory().readByte( emulator.getCPU().pc ) & 0xff;
				return op == 0x20; // JSR $xxxx
			}
		}
		return false;
	}
}