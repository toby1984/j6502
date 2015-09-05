package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.assembler.SourceMap;
import de.codesourcery.j6502.utils.SourceHelper;

public abstract class EmulatorDriver extends Thread
{
	private static final AtomicLong CMD_ID = new AtomicLong(0);
	private static final long CALLBACK_INVOKE_CYCLES = 300_000;

	public volatile Throwable lastException;

	public static final boolean PRINT_SPEED = false;

	public static enum Mode { SINGLE_STEP , CONTINOUS; }

	private final AtomicReference<Mode> currentMode = new AtomicReference<Mode>(Mode.SINGLE_STEP);

	protected final Emulator emulator;

	public volatile SourceMap sourceMap = null;
	public volatile SourceHelper sourceHelper = null;

	protected final ArrayBlockingQueue<Cmd> requestQueue = new ArrayBlockingQueue<>(10);
	protected final ArrayBlockingQueue<Cmd> ackQueue = new ArrayBlockingQueue<>(10);

	protected Cmd startCommand(boolean ackRequired) {
		return new Cmd(CmdType.START,ackRequired);
	}

	protected Cmd stopCommand(boolean ackRequired,boolean stopOnBreakpoint) {
		return new StopCmd(ackRequired,stopOnBreakpoint);
	}
	
	private final List<IEmulationListener> listeners = new ArrayList<>();
	
	public interface IEmulationListener 
	{
	    public void emulationStarted();
	    public void emulationStopped(Throwable t,boolean stoppedOnBreakpoint);
	}

	protected static enum CmdType { START , STOP }

	protected final class StopCmd extends Cmd
	{
		public final boolean stoppedAtBreakpoint;

		public StopCmd(boolean ackRequired,boolean stoppedAtBreakpoint)
		{
			super(CmdType.STOP,ackRequired);
			this.stoppedAtBreakpoint = stoppedAtBreakpoint;
		}
	}

	protected class Cmd
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

		public boolean isStartCmd() {
			return this.type.equals( CmdType.START );
		}

		public boolean isStopCmd() {
			return this.type.equals( CmdType.STOP );
		}

		public void enqueue()
		{
			try {
				requestQueue.put( this );
			} catch (final InterruptedException e) {
				throw new RuntimeException(e);
			}
		}

		public void ack()
		{
			if ( ackRequired ) {
				try {
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
		setName("emulator-driver-thread");
	}

	public Mode getMode()
	{
		return currentMode.get();
	}

	public void setMode(Mode newMode)
	{
		final Cmd cmd;
		if ( Mode.CONTINOUS.equals( newMode ) )
		{
			cmd = startCommand(true);
		} else {
			cmd = stopCommand(true,false);
		}
		sendCmd( cmd );
	}

	private void sendCmd(Cmd cmd)
	{
		cmd.enqueue();

		while ( cmd.isAckRequired() )
		{
			try {
				final Cmd acked = ackQueue.take();
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
		this.currentMode.set( Mode.CONTINOUS );
		onStartHook();
	}

	protected final void onStartHook() 
	{
	    synchronized (listeners) 
	    {
	        listeners.forEach( l -> l.emulationStarted() );
        }
	}

	protected final void onStop(Throwable t,boolean stoppedOnBreakpoint)
	{
		this.currentMode.set( Mode.SINGLE_STEP );
		onStopHook( t , stoppedOnBreakpoint );
	}

	protected final void onStopHook(Throwable t,boolean stoppedOnBreakpoint) 
	{
        synchronized (listeners) 
        {
            listeners.forEach( l -> l.emulationStopped( t , stoppedOnBreakpoint ) );
        }	    
	}

	protected abstract void tick();

	@Override
	public void run()
	{
		boolean justStarted = true;
		boolean isRunnable = false;

		float dummy = 0; // used to prevent the compiler from optimizing away the delay loop
		long startTime = System.currentTimeMillis();
		long cyclesUntilNextTick = CALLBACK_INVOKE_CYCLES;

		int delayIterationsCount=90;

		boolean adjustDelayLoop = currentMode.get() == Mode.CONTINOUS;

		Cmd cmd = null;
		while( true )
		{
			if ( isRunnable )
			{
				cmd = requestQueue.poll();
				if ( cmd != null )
				{
					cmd.ack();
					if ( cmd.isStopCmd() )
					{
						isRunnable = false;
						continue;
					}
				}
			}
			else
			{
				if ( ! justStarted )
				{
					onStop( null , cmd instanceof StopCmd && ((StopCmd) cmd).stoppedAtBreakpoint );
				}
				justStarted = false;
				while ( ! isRunnable )
				{
					try
					{
						cmd = requestQueue.take();
						cmd.ack();

						if ( cmd.isStartCmd() )
						{
							isRunnable = true;
						}
					} catch (final InterruptedException e) {
						continue;
					}
				}

				lastException = null;
				cyclesUntilNextTick = CALLBACK_INVOKE_CYCLES;
				startTime = System.currentTimeMillis();
				adjustDelayLoop = currentMode.get() == Mode.CONTINOUS;

				onStart();
			}

			synchronized( emulator )
			{
				try
				{
					// run at max. speed if floppy is transferring data
					for ( int i = delayIterationsCount ; i > 0 ; i-- ) {
						dummy += Math.sqrt( i );
					}

					lastException = null;

					emulator.doOneCycle();

					cyclesUntilNextTick--;

					if ( getBreakPointsController().isAtBreakpoint() )
					{
						isRunnable = false;
						cmd = stopCommand( false , true ); // assign to cmd so that next loop iteration will know why we stopped execution
						sendCmd( cmd );
					}
				}
				catch(final Throwable e)
				{
					e.printStackTrace();
					isRunnable = false;
					lastException = e;
					cyclesUntilNextTick = 0;
					sendCmd( stopCommand( false , false ) );
				}
			}
			if ( cyclesUntilNextTick <= 0 )
			{
				tick();
                final long now = System.currentTimeMillis();
                final float cyclesPerSecond = CALLBACK_INVOKE_CYCLES / ( (now - startTime ) / 1000f );
                final float khz = cyclesPerSecond / 1000f;
				if ( PRINT_SPEED )
				{
					System.out.println("CPU frequency: "+khz+" kHz (delay iterations: "+delayIterationsCount+") "+dummy);
				}
				if ( adjustDelayLoop )
				{
    				if ( khz >= 1000 ) {
    				    delayIterationsCount++;
    				}
    				else if ( khz < 950 )
    				{
    				    delayIterationsCount--;
    				}
				}
				startTime = now;
				cyclesUntilNextTick = CALLBACK_INVOKE_CYCLES;
				adjustDelayLoop = currentMode.get() == Mode.CONTINOUS;
			}
		}
	}

	public void singleStep(CPU cpu) throws RuntimeException
	{
		setMode(Mode.SINGLE_STEP);

		synchronized( emulator )
		{
			lastException = null;
            while ( cpu.cycles > 0 ) {
                emulator.doOneCycle();
            }
			do {
				emulator.doOneCycle();
			} while ( cpu.cycles > 0 );
		}
	}

	public Throwable getLastException() {
		return lastException;
	}

	protected abstract BreakpointsController getBreakPointsController();
	
	public void addEmulationListener(IEmulationListener l) 
	{
        Validate.notNull(l, "l must not be NULL");
	    synchronized (listeners) {
            listeners.add(l);
        }
	}
	
    public void removeEmulationListener(IEmulationListener l) 
    {
        Validate.notNull(l, "l must not be NULL");
        synchronized (listeners) {
            listeners.remove(l);
        }
    }
}