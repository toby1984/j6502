package de.codesourcery.j6502;

/**
 * Various constants for debugging the emulation.
 * 
 * Most of these constants have a large performance impact, tweak with care.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public class Constants 
{
    // various Memory implementations
    public static final boolean MEMORY_SUPPORT_BREAKPOINTS = false; /* PERFORMANCE RELEVANT */
    public static final boolean MEMORY_DEBUG_STATE_PERSISTENCE = true;
    
    // EmulatorDriver class constants
    public static final boolean EMULATORDRIVER_DEBUG_CMDS = false;
    public static final boolean EMULATORDRIVER_INVOKE_CALLBACK = true; /* PERFORMANCE RELEVANT */
    
    // Floppy drive
    public static final boolean DISKDRIVE_TRACK_JOBQUEUE = true; /* PERFORMANCE RELEVANT */
    
    /**
     * Careful, this interval is also used to re-calibrate the CPU's delay loop.
     * Setting it to a larger value will make the emulation worse at keeping
     * to the true clock speed.
     */
    public static final long EMULATORDRIVER_CALLBACK_INVOKE_CYCLES = 300_000; /* PERFORMANCE RELEVANT */
    public static final boolean EMULATORDRIVER_PRINT_SPEED = false;

    // CPUImpl constants
    public static final boolean CPUIMPL_TRACK_INSTRUCTION_DURATION = false;
    public static final boolean CPUIMPL_DEBUG_TAPE = false;       
    
    // CPU class constants
    public static final boolean CPU_RECORD_BACKTRACE = true; /* PERFORMANCE RELEVANT */
    public static final int CPU_BACKTRACE_RINGBUFFER_SIZE = 64; // MUST be a power of 2 , don't forget to update BACKTRACE_RINGBUFFER_SIZE_MASK !!!
    public static final int CPU_BACKTRACE_RINGBUFFER_SIZE_MASK = 0b111111;
    
    // VIC class constants
    public static final boolean VIC_DEBUG_INTERRUPTS = false;
    public static final boolean VIC_DEBUG_TRIGGERED_INTERRUPTS = false;
    public static final boolean VIC_DEBUG_FPS = true;
    public static final boolean VIC_DEBUG_GRAPHIC_MODES = true;
    public static final boolean VIC_DEBUG_DRAW_RASTER_IRQ_LINE = true;
    public static final boolean VIC_DEBUG_SPRITES = true;
    public static final boolean VIC_DEBUG_MEMORY_LAYOUT = true;
    
    public static final boolean VIC_SPRITE_SPRITE_COLLISIONS = true;
    public static final boolean VIC_SPRITE_BACKGROUND_COLLISIONS = true;
    
    // CIA class constants
    public static final boolean CIA_DEBUG = false;
    public static final boolean CIA_DEBUG_VERBOSE = false;
    public static final boolean CIA_DEBUG_TIMER_LOAD = false;
    public static final boolean CIA_DEBUG_TIMER_IRQS = false;
    public static final boolean CIA_DEBUG_TAPE_SLOPE = false;
    
    // Debugger class constants
    /**
     * Time between consecutive UI updates while trying to run emulation at full speed.
     */
    public static final int DEBUGGER_UI_REFRESH_MILLIS = 500;
    public static final boolean DEBUGGER_DEBUG_VIEW_PERFORMANCE = false;

    // IECBus class constants
    public static final boolean IEC_BUS_ENABLED = true; /* PERFORMANCE RELEVANT */
    
    public static final boolean IEC_CAPTURE_BUS_SNAPSHOTS = false;
    public static final boolean IEC_DEBUG_WIRE_LEVEL = false;
    public static final boolean IEC_DEBUG_DEVICE_LEVEL_VERBOSE = false;
    public static final boolean IEC_DEBUG_DEVICE_LEVEL = false;
}
