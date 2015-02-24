package de.codesourcery.j6502.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.assembler.SourceMap;
import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.disassembler.Disassembler.Line;
import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.utils.SourceHelper;

public abstract class EmulatorDriver extends Thread
{
	private static final AtomicLong CMD_ID = new AtomicLong(0);
	private static final long CALLBACK_INVOKE_CYCLES = 100000;

	public volatile Throwable lastException;

	public static enum Mode { SINGLE_STEP , CONTINOUS; }

	private final AtomicReference<Mode> currentMode = new AtomicReference<Mode>(Mode.SINGLE_STEP);

	private final Emulator emulator;

	// FIXME: Remove debug code when done
	public volatile boolean logEachStep = false;
	// FIXME: Remove debug code when done
	public volatile SourceMap sourceMap = null;
	public volatile SourceHelper sourceHelper = null;

	private Breakpoint oneShotBreakpoint = null;
	private final Breakpoint[] breakpoints = new Breakpoint[65536];

	private final ArrayBlockingQueue<Cmd> requestQueue = new ArrayBlockingQueue<>(10);
	private final ArrayBlockingQueue<Cmd> ackQueue = new ArrayBlockingQueue<>(10);

	protected Cmd startCommand(boolean ackRequired) {
		return new Cmd(CmdType.START,ackRequired);
	}

	protected Cmd stopCommand(boolean ackRequired) {
		return new Cmd(CmdType.STOP,ackRequired);
	}

	protected static enum CmdType { START , STOP }

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
			System.out.println("Breakpoints: "+getBreakpoints());
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

						if ( cmd.isStartCmd() )
						{
							isRunnable = true;
						}
					} catch (final InterruptedException e) {
						continue;
					}
				}
				lastException = null;
				cyclesRemaining = CALLBACK_INVOKE_CYCLES;
				onStart();
			}

			synchronized( emulator )
			{
				final long cycles = cpu.cycles;
				try {
					lastException = null;

					/*
					 * Here the magic happens....
					 */
					final short oldAdr = cpu.pc;

					if ( logEachStep )
					{
						Disassembler dis = new Disassembler();
						final AtomicReference<Line> line = new AtomicReference<>(null);
						boolean printed = false;
						if ( sourceHelper != null && sourceMap != null ) {
							Optional<Integer> lineNo = sourceMap.getLineNumberForAddress( oldAdr );
							if ( lineNo.isPresent() )
							{
								String lineText = sourceHelper.getLineText( lineNo.get() );
								final String col0 = "(line "+lineNo.get()+")";
								final String col1 = lineText;
								System.out.print( StringUtils.rightPad( col0 , 15 )+"  "+StringUtils.rightPad( col1 ,  20 )+" ; ");
								printed = true;
							}
						}
						if ( ! printed )
						{
							dis.disassemble( emulator.getMemory() , oldAdr , 3 , l -> line.compareAndSet( null , l ) );
							System.out.println( line.get() );
						}
					}
					emulator.singleStep();

					if ( logEachStep )
					{
						System.out.println( cpu );
					}

					final long elapsedCycles = cpu.cycles - cycles;
					cyclesRemaining -= elapsedCycles;
					Breakpoint bp = breakpoints[ cpu.pc & 0xffff ];
					if ( bp == null && ( oneShotBreakpoint != null && oneShotBreakpoint.address == cpu.pc ) ) {
						bp = oneShotBreakpoint;
					}
					if ( bp != null )
					{
						isRunnable = false;
						if ( bp.isOneshot ) {
							oneShotBreakpoint = null;
						}
						sendCmd( stopCommand( false ) );
					}

					if ( cyclesRemaining <= 0 )
					{
						tick();
					}
				}
				catch(final Throwable e)
				{
					e.printStackTrace();
					isRunnable = false;
					lastException = e;
					cyclesRemaining = 0;
					sendCmd( stopCommand( false ) );
				}
			}
		}
	}

	public void addBreakpoint(Breakpoint bp)
	{
		if (bp.isOneshot) {
			this.oneShotBreakpoint = bp;
		} else {
			this.breakpoints[ bp.address & 0xffff ] = bp;
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

	public Breakpoint getBreakpoint(short address)
	{
		return breakpoints[ address & 0xffff ];
	}

	public List<Breakpoint> getBreakpoints()
	{
		List<Breakpoint> result = new ArrayList<>();
		for ( int i = 0 , len = breakpoints.length ; i < len ; i++ ) {
			Breakpoint breakpoint = breakpoints[i];
			if ( breakpoint != null && ! breakpoint.isOneshot ) {
				result.add( breakpoint );
			}
		}
		return result;
	}

	public void stepReturn()
	{
		boolean breakpointAdded = false;
		synchronized( emulator )
		{
			if ( canStepOver() )
			{
				addBreakpoint( new Breakpoint( (short) (emulator.getCPU().pc+3) , true  ) );
				breakpointAdded = true;
			}
		}
		if ( breakpointAdded ) {
			setMode(Mode.CONTINOUS);
		}
	}

	public void removeBreakpoint(Breakpoint breakpoint)
	{
		if ( breakpoint.isOneshot ) {
			if ( oneShotBreakpoint != null && oneShotBreakpoint.address == breakpoint.address ) {
				oneShotBreakpoint = null;
			}
		} else {
			this.breakpoints[ breakpoint.address & 0xffff ] = null;
		}
	}

	public void removeAllBreakpoints() {
		for ( int i = 0 ; i < breakpoints.length ; i++ ) {
			breakpoints[i] = null;
		}
		oneShotBreakpoint = null;
	}
}