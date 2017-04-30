package de.codesourcery.j6502.emulator;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Function;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.Validate;

import de.codesourcery.j6502.Constants;
import de.codesourcery.j6502.emulator.EmulatorDriver.ThrowingConsumer;
import de.codesourcery.j6502.utils.Misc;

public abstract class EmulatorDriver extends Thread
{
    private static final AtomicLong CMD_ID = new AtomicLong(0);

    public final AtomicReference<Throwable> mostRecentException = new AtomicReference<Throwable>();

    public static enum Mode { SINGLE_STEP , CONTINOUS; }

    private final AtomicReference<Mode> currentMode = new AtomicReference<Mode>(Mode.SINGLE_STEP);

    protected final Emulator emulator;

    public double dummyValue; // just used to prevent the compiler from optimizing away our delay loop

    protected final ArrayBlockingQueue<Cmd> requestQueue = new ArrayBlockingQueue<>(10);

    public interface ThrowingConsumer<X> 
    {
        public void accept(X obj) throws Exception;
    }
    
    public static final class CallbackWithResult<T> implements ThrowingConsumer<Emulator>
    {
        private volatile T result;
        private volatile boolean called;
        private volatile boolean success;
        
        private final Function<Emulator,T> function;
        
        public CallbackWithResult(Function<Emulator,T> function) 
        {
            this.function = function;
        }
        
        public T getResult() 
        {
            if ( ! called ) {
                throw new IllegalStateException("getResult() invoked although no accept() not called yet ?");
            }            
            if ( ! success ) {
                throw new IllegalStateException("getResult() invoked although no success ?");
            }
            return result;
        }
        
        @Override
        public void accept(Emulator obj) throws Exception 
        {
            called = true;
            result = function.apply( obj );
            success = true;
        }
    }    
    
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

    protected static enum CmdType { START , STOP , MAX_SPEED, TRUE_SPEED, RUNNABLE , EXEC_SINGLE_STEP}

    protected final class StopCmd extends Cmd
    {
        public final boolean stoppedAtBreakpoint;

        public StopCmd(boolean ackRequired,boolean stoppedAtBreakpoint)
        {
            super(CmdType.STOP,ackRequired);
            this.stoppedAtBreakpoint = stoppedAtBreakpoint;
        }
    }

    protected final class TrueSpeedCommand extends Cmd 
    {
        public TrueSpeedCommand() {
            super(CmdType.TRUE_SPEED, false );
        }
    }	

    protected final class MaxSpeedCommand extends Cmd {

        public MaxSpeedCommand() {
            super(CmdType.MAX_SPEED, false );
        }
    }

    protected final class RunnableCmd extends Cmd {

        public final ThrowingConsumer<Emulator> visitor;

        public RunnableCmd(ThrowingConsumer<Emulator> visitor,boolean ackRequired)
        {
            super(CmdType.RUNNABLE, ackRequired);
            this.visitor = visitor;
        }
    }

    protected final class AckRunnable extends Cmd {

        public final Runnable r;

        public AckRunnable(Runnable r)
        {
            super(CmdType.RUNNABLE, true);
            this.r = r;
        }
    }	

    protected class Cmd
    {
        public final long id = CMD_ID.incrementAndGet();
        public final CmdType type;
        private final boolean ackRequired;
        private final Consumer<Emulator> ackCallback;
        
        private final CountDownLatch ackLatch = new CountDownLatch(1);

        public Cmd(CmdType type,boolean ackRequired) {
            this(type,ackRequired,null);
        }

        public Cmd(CmdType type,boolean ackRequired,Consumer<Emulator> ackCallback) {
            this.type = type;
            this.ackRequired = ackRequired;
            this.ackCallback = ackCallback;
        }
        
        public void awaitAck() throws InterruptedException {
            ackLatch.await();
        }

        public final boolean isAckRequired() {
            return ackRequired;
        }

        public final boolean isStartCmd() {
            return this.type.equals( CmdType.START );
        }

        public final boolean isStopCmd() {
            return this.type.equals( CmdType.STOP );
        }

        public final boolean hasType(CmdType t) {
            return this.type == t;
        }

        public final void enqueue()
        {
            if ( Constants.EMULATORDRIVER_DEBUG_CMDS ) {
                System.out.println("EmulatorDriver: enqueing "+this);
            }
            while( true ) 
            {
                try {
                    requestQueue.put( this );
                    break;
                } catch (final InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }

        public final void ackIfNecessary()
        {
            if ( ackRequired ) 
            {
                if ( Constants.EMULATORDRIVER_DEBUG_CMDS ) {
                    System.out.println("EmulatorDriver: Acknowledging "+this);
                }				
                ackLatch.countDown();
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
        start();
    }

    public Mode getMode()
    {
        return currentMode.get();
    }

    public void setMode(Mode newMode)
    {
        setMode(newMode,true);
    }

    private void setMode(Mode newMode,boolean waitForAck)
    {
        final Cmd cmd;
        if ( Mode.CONTINOUS.equals( newMode ) )
        {
            cmd = startCommand(waitForAck);
        } else {
            cmd = stopCommand(waitForAck,false);
        }
        sendCmd( cmd );
    }

    public void invoke(ThrowingConsumer<Emulator> visitor) 
    {
        sendCmd( new RunnableCmd( visitor , false ) );
    }

    public void invokeAndWait(ThrowingConsumer<Emulator> visitor) 
    {
        sendCmd( new RunnableCmd( visitor , true ) );
    }	

    public void setMaxSpeed() {
        sendCmd( new MaxSpeedCommand() );
    }

    public void setTrueSpeed() {
        sendCmd( new TrueSpeedCommand() );
    }    

    public void hardwareBreakpointReached() {
        final Cmd cmd = stopCommand(false,true);
        sendCmd( cmd );
    }

    private void sendCmd(Cmd cmd)
    {
        cmd.enqueue();

        while ( cmd.isAckRequired() )
        {
            if ( Constants.EMULATORDRIVER_DEBUG_CMDS && cmd.isAckRequired() ) {
                System.out.println("EmulatorDriver: Awaiting ack for "+cmd);
            }               
            try 
            {
                cmd.awaitAck();
                break;
            }
            catch (final InterruptedException e) 
            {
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
        boolean isRunnable = false;

        long startTime = System.currentTimeMillis();
        long cyclesUntilNextTick = Constants.EMULATORDRIVER_CALLBACK_INVOKE_CYCLES;

        int delayIterationsCount=90;

        boolean adjustDelayLoop = currentMode.get() == Mode.CONTINOUS;

        boolean runAtTrueSpeed = true;

        Cmd cmd = null;

        BreakpointsController brkCtrl = getBreakPointsController();
        while( true )
        {
            try 
            {
                if ( isRunnable )
                {
                    cmd = requestQueue.poll();
                    if ( cmd != null )
                    {
                        if ( Constants.EMULATORDRIVER_DEBUG_CMDS ) {
                            System.out.println("EmulatorDriver: (runnable) Received cmd "+cmd);
                        }
                        try 
                        {
                            switch( cmd.type )
                            {
                                case MAX_SPEED:
                                    runAtTrueSpeed = false;
                                    continue;
                                case STOP:
                                    isRunnable = false;                            
                                    continue;
                                case TRUE_SPEED:
                                    runAtTrueSpeed = true;
                                    continue;
                                case RUNNABLE:
                                    ((RunnableCmd) cmd).visitor.accept( emulator );
                                    continue; // start over as the Runnable might've issued a new command that needs processing
                                case START:
                                    break;
                                default:
                                    throw new RuntimeException("Unreachable code reached");
                            }       
                        } finally {
                            cmd.ackIfNecessary();
                        }
                    }
                }
                else
                {
                    onStop( null , cmd instanceof StopCmd && ((StopCmd) cmd).stoppedAtBreakpoint );                    
                    while ( ! isRunnable )
                    {
                        try
                        {
                            cmd = requestQueue.take();
                        } 
                        catch (final InterruptedException e) 
                        {
                            e.printStackTrace();
                            continue;
                        }                        
                        if ( Constants.EMULATORDRIVER_DEBUG_CMDS ) {
                            System.out.println("EmulatorDriver: (not runnable) Received cmd "+cmd);
                        }					
                        try 
                        {
                            switch( cmd.type )
                            {
                                case MAX_SPEED:
                                    runAtTrueSpeed = false;
                                    break;
                                case START:
                                    isRunnable = true;
                                    break;
                                case TRUE_SPEED:
                                    runAtTrueSpeed = true;
                                    break;
                                case RUNNABLE:
                                    ((RunnableCmd) cmd).visitor.accept( emulator );
                                    continue; // start over as the Runnable might've enqueued a new command that needs processing                                
                                case STOP: 
                                    break; // nothing to do here
                                default:
                                    throw new RuntimeException("Unreachable code reached");
                            }
                        } finally {
                            cmd.ackIfNecessary();
                        }
                    }

                    mostRecentException.set(null);                
                    cyclesUntilNextTick = Constants.EMULATORDRIVER_CALLBACK_INVOKE_CYCLES;
                    startTime = System.currentTimeMillis();
                    adjustDelayLoop = currentMode.get() == Mode.CONTINOUS;
                    brkCtrl = getBreakPointsController();

                    onStart();
                }

                // delay loop
                if ( runAtTrueSpeed ) 
                {
                    double dummy = 0;
                    for ( int i = delayIterationsCount ; i > 0 ; i-- ) {
                        dummy += Math.sqrt( i );
                    }
                    this.dummyValue=dummy;
                }

                emulator.doOneCycle(this);

                cyclesUntilNextTick--;

                if ( brkCtrl.checkIsAtBreakpoint() ) 
                {
                    isRunnable = false;
                    cmd = stopCommand( false , true ); // assign to cmd so that next loop iteration will know why we stopped execution
                    // TODO: Deadlock prone ?? We're already holding the emulator lock here ....                        
                    sendCmd( cmd );                        
                }

                if ( cyclesUntilNextTick < 0 )
                {
                    if ( Constants.EMULATORDRIVER_INVOKE_CALLBACK ) {
                        tick();
                    }

                    // adjust delay loop
                    final long now = System.currentTimeMillis();
                    final float cyclesPerSecond = Constants.EMULATORDRIVER_CALLBACK_INVOKE_CYCLES / ( (now - startTime ) / 1000f );
                    final float khz = cyclesPerSecond / 1000f;
                    if ( Constants.EMULATORDRIVER_PRINT_SPEED )
                    {
                        System.out.println("CPU frequency: "+khz+" kHz (delay iterations: "+delayIterationsCount+") "+this.dummyValue);
                    }
                    if ( adjustDelayLoop && runAtTrueSpeed )
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
                    cyclesUntilNextTick = Constants.EMULATORDRIVER_CALLBACK_INVOKE_CYCLES;
                    adjustDelayLoop = currentMode.get() == Mode.CONTINOUS;
                    brkCtrl = getBreakPointsController();
                }
            }
            catch(final Throwable e)
            {
                e.printStackTrace();
                if ( Constants.CPU_RECORD_BACKTRACE ) 
                {
                    final int[] lastPCs = emulator.getCPU().getBacktrace();
                    System.err.println("\n**********\n"
                            + "Backtrace\n"+
                            "**********\n");
                    for ( int i = 0 , no = lastPCs.length ; i < lastPCs.length ; i++ , no-- ) 
                    {
                        System.err.println( StringUtils.leftPad( Integer.toString( no ) , 3 , ' ' )+": "+Misc.to16BitHex( lastPCs[i] ));
                    }
                }

                isRunnable = false;
                mostRecentException.set(e);
                cyclesUntilNextTick = -1; // trigger UI callback invocation further below
                // TODO: Deadlock prone ?? We're already holding the emulator lock here ....                    
                sendCmd( stopCommand( false , false ) );
            }             
        } // end while (true)
    }

    public void singleStep(CPU cpu) throws RuntimeException
    {
        setMode(Mode.SINGLE_STEP);
        invokeAndWait( emulator ->
        {
            mostRecentException.set(null);
            try 
            {
                long cycles = cpu.cycles;
                while ( cycles > 0 ) {
                    emulator.doOneCycle(this);
                    cycles--;
                }
                emulator.doOneCycle(this);
            } 
            catch(RuntimeException e) 
            {
                mostRecentException.set(e);
                throw e;
            }
        });
    }

    public Throwable getMostRecentException() {
        return mostRecentException.get();
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