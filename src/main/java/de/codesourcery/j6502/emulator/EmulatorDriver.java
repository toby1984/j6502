package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

import de.codesourcery.j6502.assembler.SourceMap;
import de.codesourcery.j6502.utils.SourceHelper;

public abstract class EmulatorDriver extends Thread
{
	private static final AtomicLong CMD_ID = new AtomicLong(0);
	private static final long CALLBACK_INVOKE_CYCLES = 300_000;

	public volatile Throwable lastException;

	public static final boolean DELAY_LOOP_ENABLED = true;
	public static final boolean PRINT_SPEED = false;

	public static enum Mode { SINGLE_STEP , CONTINOUS; }

	private final AtomicReference<Mode> currentMode = new AtomicReference<Mode>(Mode.SINGLE_STEP);

	private final Emulator emulator;

	public volatile SourceMap sourceMap = null;
	public volatile SourceHelper sourceHelper = null;

	private Breakpoint oneShotBreakpoint = null;
	private final Breakpoint[] breakpoints = new Breakpoint[65536];

	private final ArrayBlockingQueue<Cmd> requestQueue = new ArrayBlockingQueue<>(10);
	private final ArrayBlockingQueue<Cmd> ackQueue = new ArrayBlockingQueue<>(10);

	private final List<IBreakpointLister> breakpointListeners = new ArrayList<>();
	
	protected Cmd startCommand(boolean ackRequired) {
		return new Cmd(CmdType.START,ackRequired);
	}

	protected Cmd stopCommand(boolean ackRequired) {
		return new Cmd(CmdType.STOP,ackRequired);
	}

	protected static enum CmdType { START , STOP }
	
	public interface IBreakpointLister 
	{
		public void breakpointAdded(Breakpoint bp);
		public void breakpointRemoved(Breakpoint bp);
		public void breakpointReplaced(Breakpoint old,Breakpoint newBp);		
	}
	
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
//				System.out.println("ENQUEUE: "+this);
				requestQueue.put( this );
			} catch (final InterruptedException e) {
				throw new RuntimeException(e);
			}
		}

		public void ack()
		{
			if ( ackRequired ) {
				try {
//					System.out.println("ENQUEUE ACK: "+this);
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
//			System.out.println("Breakpoints: "+getBreakpoints());
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
//			System.out.println("Waiting for ack of "+this);
			try {
				final Cmd acked = ackQueue.take();
//				System.out.println("Got ack for "+acked);
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

	protected abstract void onStartHook();

	protected final void onStop(Throwable t)
	{
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

		@SuppressWarnings("unused")
		float dummy = 0; // used to prevent the compiler from optimizing away the delay loop
		long startTime = System.currentTimeMillis();
		long cyclesRemaining = CALLBACK_INVOKE_CYCLES;
		while( true )
		{
			if ( isRunnable )
			{
				final Cmd cmd = requestQueue.poll();
				if ( cmd != null )
				{
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
					try {
						final Cmd cmd = requestQueue.take();
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
				if ( PRINT_SPEED ) {
					startTime = System.currentTimeMillis();
				}
				onStart();
			}
			
			if ( DELAY_LOOP_ENABLED ) {
				for ( int i = 139 ; i > 0 ; i-- ) {
					dummy += Math.sqrt( i );
				}
			}

			synchronized( emulator )
			{
				try
				{
					lastException = null;

					emulator.doOneCycle();
					cyclesRemaining--;

					Breakpoint bp = breakpoints[ cpu.pc() ];
					if ( bp == null && ( oneShotBreakpoint != null && oneShotBreakpoint.address == cpu.pc() ) ) {
						bp = oneShotBreakpoint;
					}
					if ( bp != null && bp.isTriggered( cpu ) )
					{
						isRunnable = false;
						if ( bp.isOneshot ) {
							oneShotBreakpoint = null;
						}
						sendCmd( stopCommand( false ) );
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
			if ( cyclesRemaining <= 0 )
			{
				tick();
				if ( PRINT_SPEED )
				{
					long now = System.currentTimeMillis();
					float cyclesPerSecond = CALLBACK_INVOKE_CYCLES / ( (now - startTime ) / 1000f );
					float khz = cyclesPerSecond / 1000f;
					System.out.println("CPU frequency: "+khz+" kHz "+dummy);
					startTime = now;
				}
				cyclesRemaining = CALLBACK_INVOKE_CYCLES;
			}			
		}
	}
	
	public void singleStep() throws RuntimeException
	{
		setMode(Mode.SINGLE_STEP);

		synchronized( emulator )
		{
			lastException = null;
			do {
				emulator.doOneCycle();
			} while ( emulator.getCPU().cycles > 0 );
		}
	}

	public Throwable getLastException() {
		return lastException;
	}
	
	public void stepReturn()
	{
		boolean breakpointAdded = false;
		synchronized( emulator )
		{
			if ( canStepOver() )
			{
				addBreakpoint( new Breakpoint( emulator.getCPU().pc()+3 , true  ) );
				breakpointAdded = true;
			}
		}
		if ( breakpointAdded ) {
			setMode(Mode.CONTINOUS);
		}
	}	

	public boolean canStepOver()
	{
		if ( getMode() == Mode.SINGLE_STEP )
		{
			synchronized( emulator )
			{
				final int op = emulator.getMemory().readByte( emulator.getCPU().pc() );
				return op == 0x20; // JSR $xxxx
			}
		}
		return false;
	}	

	public void addBreakpoint(Breakpoint bp)
	{
		synchronized(emulator)
		{
			if (bp.isOneshot) {
				this.oneShotBreakpoint = bp;
			} else {
				Breakpoint old = this.breakpoints[ bp.address & 0xffff ];
				this.breakpoints[ bp.address & 0xffff ] = bp;
				if ( old == null ) {
					breakpointListeners.forEach( l -> l.breakpointAdded( bp ) );
				} else {
					breakpointListeners.forEach( l -> l.breakpointReplaced( old , bp ) );					
				}
			}
		}
	}

	public Breakpoint getBreakpoint(short address)
	{
		synchronized(emulator) {
			return breakpoints[ address & 0xffff ];
		}
	}

	public List<Breakpoint> getBreakpoints()
	{
		List<Breakpoint> result = new ArrayList<>();
		synchronized(emulator) {
			for ( int i = 0 , len = breakpoints.length ; i < len ; i++ ) {
				Breakpoint breakpoint = breakpoints[i];
				if ( breakpoint != null && ! breakpoint.isOneshot ) {
					result.add( breakpoint );
				}
			}
		}
		return result;
	}

	public void removeBreakpoint(Breakpoint breakpoint)
	{
		synchronized(emulator)
		{
			if ( breakpoint.isOneshot ) {
				if ( oneShotBreakpoint != null && oneShotBreakpoint.address == breakpoint.address ) {
					oneShotBreakpoint = null;
				}
			} else {
				Breakpoint existing = this.breakpoints[ breakpoint.address & 0xffff ];
				if ( existing != null ) 
				{
					this.breakpoints[ breakpoint.address & 0xffff ] = null;
					breakpointListeners.forEach( l -> l.breakpointRemoved( existing ) );
				}
			}
		}
	}

	public void removeAllBreakpoints()
	{
		synchronized(emulator)
		{
			for ( int i = 0 ; i < breakpoints.length ; i++ ) 
			{
				Breakpoint bp = breakpoints[i];
				if ( bp != null ) {
					breakpoints[i] = null;
					breakpointListeners.forEach( l -> l.breakpointRemoved(bp) );
				}
			}
			oneShotBreakpoint = null;
		}
	}
	
	public void addBreakpointListener(IBreakpointLister l) 
	{
		if (l == null) {
			throw new IllegalArgumentException("l must not be NULL");
		}
		synchronized(emulator) {
			this.breakpointListeners.add(l);
		}
	}
	
	public void removeBreakpointListener(IBreakpointLister l) 
	{
		if (l == null) {
			throw new IllegalArgumentException("l must not be NULL");
		}
		synchronized(emulator) {
			this.breakpointListeners.remove(l);
		}
	}		
}