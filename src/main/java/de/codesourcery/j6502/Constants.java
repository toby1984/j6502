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
    public static final boolean MEMORY_SUPPORT_BREAKPOINTS = true;
    
    // EmulatorDriver class constants
    public static final boolean EMULATORDRIVER_DEBUG_CMDS = true;
    public static final boolean EMULATORDRIVER_INVOKE_CALLBACK = true;
    public static final long EMULATORDRIVER_CALLBACK_INVOKE_CYCLES = 300_000;
    public static final boolean EMULATORDRIVER_PRINT_SPEED = false;

    // CPUImpl constants
    public static final boolean CPUIMPL_TRACK_INS_DURATION = false;
    public static final boolean CPUIMPL_DEBUG_TAPE = false;       
    
    // CPU class constants
    public static final boolean CPU_RECORD_BACKTRACE = true;
    public static final int CPU_BACKTRACE_RINGBUFFER_SIZE = 64; // MUST be a power of 2 , don't forget to update BACKTRACE_RINGBUFFER_SIZE_MASK !!!
    public static final int CPU_BACKTRACE_RINGBUFFER_SIZE_MASK = 0b111111;
    
    // VIC class constants
    public static final boolean VIC_DEBUG_INTERRUPTS = false;
    public static final boolean VIC_DEBUG_TRIGGERED_INTERRUPTS = false;
    public static final boolean VIC_DEBUG_FPS = true;
    public static final boolean VIC_DEBUG_GRAPHIC_MODES = true;
    public static final boolean VIC_DEBUG_DRAW_RASTER_IRQ_LINE = true;
    public static final boolean VIC_DEBUG_SPRITES = false;
    public static final boolean VIC_DEBUG_MEMORY_LAYOUT = true;
    
    // CIA class constants
    public static final boolean CIA_DEBUG = false;
    public static final boolean CIA_DEBUG_VERBOSE = false;
    public static final boolean CIA_DEBUG_TIMER_LOAD = false;
    public static final boolean CIA_DEBUG_TAPE_SLOPE = true;
    
    // Debugger class constants
    /**
     * Time between consecutive UI updates while trying to run emulation at full speed.
     */
    public static final int DEBUGGER_UI_REFRESH_MILLIS = 500;
    public static final boolean DEBUGGER_DEBUG_VIEW_PERFORMANCE = false;
}
