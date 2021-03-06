package de.codesourcery.j6502.emulator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.codesourcery.j6502.Constants;
import de.codesourcery.j6502.emulator.EmulationState.EmulationStateEntry;
import de.codesourcery.j6502.emulator.EmulationState.EntryType;
import de.codesourcery.j6502.emulator.MemoryBreakpointsContainer.MemoryBreakpoint;
import de.codesourcery.j6502.emulator.tapedrive.TapeDrive;
import de.codesourcery.j6502.utils.HexDump;
import de.codesourcery.j6502.utils.Misc;


public final class MemorySubsystem extends IMemoryRegion implements IStatefulPart
{
    private static final boolean DEBUG_READS = false;
    private static final boolean DEBUG_ROM_IMAGES = false;

    private final IMemoryRegion CART_ROM_HI_A000_BFFF =  new Memory("Cart ROM hi  $A000 - $BFFF" , MemoryType.ROM, Bank.BANK3.range );
    private final IMemoryRegion CART_ROM_HI_E000_FFFF =   new Memory("Cart ROM hi  $E000 - $FFFF" , MemoryType.ROM, Bank.BANK6.range );
    private final IMemoryRegion CART_ROM_LOW_8000_9FFFF = new Memory("Cart ROM low  $8000 - $9FFF" , MemoryType.ROM, Bank.BANK2.range );

    public static enum Bank
    {
        /**
         * $0000 - $0FFF 
         */
        BANK0(0,AddressRange.range(0x0000,0x0FFF) ),
        /**
         * $1000 - $7FFF
         */
        BANK1(1,AddressRange.range(0x1000,0x7FFF) ),
        /**
         * $8000 - $9FFF
         */
        BANK2(2,AddressRange.range(0x8000,0x9FFF) ),
        /**
         * $A000 - $BFFF
         */
        BANK3(3,AddressRange.range(0xA000,0xBFFF) ),
        /**
         * $C000 - $CFFF
         */
        BANK4(4,AddressRange.range(0xC000,0xCFFF) ),
        /**
         * $D000 - $DFFF
         */
        BANK5(5,AddressRange.range(0xD000,0xDFFF) ),
        /**
         * $E000 - $FFFF
         */
        BANK6(6,AddressRange.range(0xE000,0xFFFF) );

        public final int index;
        public final AddressRange range;

        private Bank(int index,AddressRange range)
        {
            this.index = index;
            this.range = range;
        }

        public static Bank getBank(int address) 
        {
            final int wrapped = address & 0xffff;
            final Bank[] values = values();
            for (int i = 0 , len = values.length ; i < len ; i++) 
            {
                final Bank b = values[i];
                if ( b.range.getStartAddress() <= wrapped && wrapped < b.range.getEndAddress() ) {
                    return b;
                }
            }
            throw new RuntimeException("Internal error, no memory bank contains address "+Misc.to16BitHex( wrapped ) );
        }
    }

    // line is LOW-ACTIVE
    private boolean exrom;

    // line is LOW-ACTIVE
    private boolean game;

    /* CPU on-chip data direction register.
     *
     * See http://unusedino.de/ec64/technical/project64/mapping_c64.html
     *
     * Bit 0: Direction of Bit 0 I/O on port at next address.  Default = 1 (output)
     * Bit 1: Direction of Bit 1 I/O on port at next address.  Default = 1 (output)
     * Bit 2: Direction of Bit 2 I/O on port at next address.  Default = 1 (output)
     * Bit 3: Direction of Bit 3 I/O on port at next address.  Default = 1 (output)
     * Bit 4: Direction of Bit 4 I/O on port at next address.  Default = 0 (input)
     * Bit 5: Direction of Bit 5 I/O on port at next address.  Default = 1 (output)
     * Bit 6: Direction of Bit 6 I/O on port at next address.  Not used.
     * Bit 7: Direction of Bit 7 I/O on port at next address.  Not used.
     */
    private byte plaDataDirection = 0b00101111; // port directional data register, address $00

    /* CPU on-chip data register
     *
     * See http://unusedino.de/ec64/technical/project64/mapping_c64.html
     *
     * Bit 0: LORAM signal.  Selects ROM or RAM at 40960 ($A000).  1=BASIC, 0=RAM
     * Bit 1: HIRAM signal.  Selects ROM or RAM at 57344 ($E000).  1=Kernal, 0=RAM
     * Bit 2: CHAREN signal.  Selects character ROM or I/O devices.  1=I/O, 0=ROM
     * Bit 3: Cassette Data Output line.
     * Bit 4: Cassette Switch Sense.  Reads 0 if a button is pressed, 1 if not.
     * Bit 5: Cassette Motor Switch Control.  A 1 turns the motor on, 0 turns it off.
     * Bits 6-7: Not connected--no function presently defined.
     */
    private byte plaLatchBits = 0; // address $01

    private final IMemoryRegion ram0= new Memory("RAM #0",MemoryType.RAM,Bank.BANK0.range) {

        @Override
        public void reset()
        {
            for ( int i = 2 , len = getAddressRange().getSizeInBytes() ; i < len ; i++ )
            {
                writeByte(i,(byte) 0);
            }
        }

        public int readByteNoSideEffects(int offset) 
        {
            switch( offset & 0xffff) 
            {
                case 0:
                case 1:
                    return readByte(offset);
                default:
                    return super.readByteNoSideEffects(offset);
            }
        }

        @Override
        public int readByte(int offset)
        {
            final int wrappedOffset = offset & 0xffff;
            switch(wrappedOffset)
            {
                case 0:
                    return plaDataDirection & 0xff;
                case 1:
                    int result = plaLatchBits & 0xff;
                    if ( ! tapeDrive.isKeyPressed() ) {
                        result |= 1<<4;
                    } else {
                        result &= ~(1<<4);
                    }
                    return result;
                default:
                    return super.readByte( wrappedOffset );
            }
        }

        @Override
        public void writeByte(int offset, byte value)
        {
            final int wrappedOffset = offset & 0xffff;
            switch(wrappedOffset)
            {
                case 0:
                    /* TODO: See behaviour of writes to $00 / $01
                     * http://sourceforge.net/p/vice-emu/code/HEAD/tree/testprogs/general/ram0001/
                     */
                    plaDataDirection = value;
                    break;
                case 1:
                    /* TODO: See behaviour of writes to $00 / $01
                     * http://sourceforge.net/p/vice-emu/code/HEAD/tree/testprogs/general/ram0001/
                     */
                    super.writeByte(wrappedOffset,value);
                    int oldValue = plaLatchBits & 0xff;
                    plaLatchBits = value;
                    if ( oldValue != (plaLatchBits & 0xff) ) {
                        setupMemoryLayout();
                    }
                    if ( ( plaDataDirection & 1<<5) != 0 ) {
                        tapeDrive.setMotorOn( (plaLatchBits & 1 << 5) == 0); // I/O line is inverted
                    }
                    break;
                default:
                    super.writeByte( wrappedOffset , value );
            }
        }
    };

    private final IMemoryRegion ram1= new Memory("RAM #1",MemoryType.RAM,Bank.BANK1.range);
    private final IMemoryRegion ram2= new Memory("RAM #2",MemoryType.RAM,Bank.BANK2.range);
    private final IMemoryRegion ram3= new Memory("RAM #3",MemoryType.RAM,Bank.BANK3.range);
    private final IMemoryRegion ram4= new Memory("RAM #4",MemoryType.RAM,Bank.BANK4.range);

    private final IMemoryRegion ram5= new Memory("RAM #5",MemoryType.RAM,Bank.BANK5.range) { // $D000 - $DFFF = 4096 bytes
        @Override
        public int readByte(int offset)
        {
            final int set = offset & 0xffff;
            if ( set >= 0x800 && set < 0x800+1024) { // color RAM
                return super.readByte(offset) | 0b11110000; // color RAM is actually a 4-bit ram and always returns 0b1111 for the hi nibble
            }
            return super.readByte(offset);
        }
    };

    private final IMemoryRegion ram6= new Memory("RAM #6",MemoryType.RAM,Bank.BANK6.range);

    private final TapeDrive tapeDrive;

    public final IMemoryRegion[] ramRegions = { ram0,ram1,ram2,ram3,ram4,ram5,ram6 };

    private final RAMView ramView = new RAMView();

    public final WriteOnceMemory kernelROM;
    public final WriteOnceMemory charROM;
    public final WriteOnceMemory basicROM;

    public final IOArea ioArea;

    private IMemoryRegion cartROMLow;
    private IMemoryRegion cartROMHi;

    // regions used when reading from an address
    private IMemoryRegion[] readRegions = new IMemoryRegion[ Bank.values().length ];

    // regions used when writing to an address
    private IMemoryRegion[] writeRegions = new IMemoryRegion[ Bank.values().length ];

    // mapping from memory addresses to readRegions
    private final int[] readMap = new int[65536];

    // mapping from memory addresses to writeRegions
    private final int[] writeMap = new int[65536];

    public MemorySubsystem(TapeDrive tapeDrive)
    {
        super("main memory" , MemoryType.RAM , new AddressRange(0,65536 ) );

        // kernel ROM
        kernelROM = new WriteOnceMemory("Kernel ROM" , Bank.BANK6.range );
        loadROM("kernel_v2.rom" , kernelROM );

        // char ROM
        charROM = loadCharacterROM();

        // basic ROM
        basicROM = new WriteOnceMemory("Basic ROM" , Bank.BANK3.range );
        loadROM( "basic_v2.rom" , basicROM );   		

        this.tapeDrive = tapeDrive;
        this.ioArea = new IOArea("I/O area", Bank.BANK5.range , this , ram5 , tapeDrive );

        // setup memory from addresses to different memory banks
        for ( final Bank bank : Bank.values() )
        {
            for ( int start = bank.range.getStartAddress() , len = bank.range.getSizeInBytes() ; len > 0 ; start++,len-- )
            {
                readMap [start] = bank.index;
                writeMap[start] = bank.index;
            }
        }
        reset();
    }

    /**
     * Returns RAM starting at $d000
     * @return
     */
    public IMemoryRegion getColorRAMBank() {
        return ram5;
    }

    @Override
    public void reset()
    {
        exrom = true; // line is LOW = ACTIVE so setting it to 'true' means disabled/not present
        game = true;  // line is LOW = ACTIVE so setting it to 'true' means disabled/not present

        plaDataDirection = 0b00101111;
        plaLatchBits = 0b00110111; // BASIC ROM , KERNEL ROM , I/O AREA

        setupMemoryLayout();

        ioArea.reset();

        ram0.reset();
        ram1.reset();
        ram2.reset();
        ram3.reset();
        ram4.reset();
        ram5.reset();
        ram6.reset();
    }

    public void setMemoryLayout( byte latchBits) {
        this.plaLatchBits = latchBits;
        setupMemoryLayout();
    }

    /*
     * LORAM (bit 0, weight 1)
     * Is a control line which banks the 8 kByte BASIC ROM in or out of the CPU address space.
     * Normally, this line is logically high (set to 1) for BASIC operation.
     * If this line is logically low (cleared to 0), the BASIC ROM will typically disappear
     * from the memory map and be replaced by 8 kBytes of RAM from $A000-$BFFF.
     * Some exceptions to this rule exist; see the table below for a full overview.
     *
     * HIRAM (bit 1, weight 2) is a control line which banks the 8 kByte KERNAL ROM in or out of the CPU address space.
     * Normally, this line is logically high (set to 1) for KERNAL ROM operation.
     * If this line is logically low (cleared to 0), the KERNAL ROM will typically disappear from the memory map and
     * be replaced by 8 kBytes of RAM from $E000-$FFFF.
     * Some exceptions to this rule exist; see the table below for a full overview.
     *
     * CHAREN (bit 2, weight 4) is a control line which banks the 4 kByte character generator ROM in or out of the CPU address space.
     * From the CPU point of view, the character generator ROM occupies the same address space as the I/O devices ($D000-$DFFF).
     * When the CHAREN line is set to 1 (as is normal), the I/O devices appear in the CPU address space,
     * and the character generator ROM is not accessible.
     * When the CHAREN bit is cleared to 0, the character generator ROM appears in the CPU address space, and the I/O devices are not accessible.
     * The CPU only needs to access the character generator ROM when downloading the character set from ROM to RAM.
     * CHAREN can be overridden by other control lines in certain memory configurations.
     * CHAREN will have no effect on any memory configuration without I/O devices. RAM will appear from $D000-$DFFF instead.
     */
    private void setupMemoryLayout()
    {
        // create table index from EXROM, EXGAME and PLA latch bits 0-3
        int index = plaLatchBits & 0b111;
        if ( exrom ) {
            index |= 1 << 4;
        }
        if ( game ) {
            index |= 1 << 3;
        }

        createRegions( index );

        // setup memory mappings (see http://www.c64-wiki.com/index.php/Bank_Switching)
        switch(index)
        {
            case 0:
            case 1:
            case 4:
            case 8:
            case 12:
            case 24:
            case 28:
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3 , ram4 , ram5 , ram6 };
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3 , ram4 , ram5 , ram6 };
                break;
            case 2:
                //  RAM 	RAM 	RAM 	CART_ROM_HI 	RAM 	CHAR_ROM 	KERNAL_ROM
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , ram2 , cartROMHi , ram4 , charROM , kernelROM };
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3      , ram4 , ram5    , ram6 };
                break;
            case 3:
                // RAM 	RAM 	CART_ROM_LO 	CART_ROM_HI 	RAM 	CHAR_ROM 	KERNAL_ROM
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , cartROMLow , cartROMHi, ram4 , charROM, kernelROM };
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2       , ram3     , ram4 , ram5   , ram6 };

                break;
            case 5:
                // RAM 	RAM 	RAM 	RAM 	RAM 	I/O 	RAM
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3 , ram4 , ioArea , ram6 };
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3 , ram4 , ioArea , ram6 };
                break;
            case 6:
                // RAM 	RAM 	RAM 	CART_ROM_HI 	RAM 	I/O 	KERNAL_ROM
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , ram2 , cartROMHi , ram4 , ioArea , kernelROM };
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3      , ram4 , ioArea , ram6 };
                break;
            case 7:
                // RAM 	RAM 	CART_ROM_LO 	CART_ROM_HI 	RAM 	I/O 	KERNAL_ROM
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , cartROMLow , cartROMHi , ram4 , ioArea , kernelROM };
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2       , ram3      , ram4 , ioArea , ram6 };
                break;
            case 9:
            case 25:
                // RAM 	RAM 	RAM 	RAM 	RAM 	CHAR ROM 	RAM
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3 , ram4 , charROM , ram6 };
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3 , ram4 , ram5    , ram6 };
                break;
            case 10:
            case 26:
                // RAM 	RAM 	RAM 	RAM 	RAM 	CHAR ROM 	KERNAL ROM
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3 , ram4 , charROM , kernelROM };
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3 , ram4 , ram5    , ram6 };
                break;
            case 11:
                // RAM 	RAM 	CART ROM LO 	BASIC ROM 	RAM 	CHAR ROM 	KERNAL ROM
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , cartROMLow , basicROM , ram4 , charROM , kernelROM };
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2       , ram3     , ram4 , ram5    , ram6 };
                break;
            case 13:
            case 29:
                // RAM 	RAM 	RAM 	RAM 	RAM 	I/O 	RAM
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3 , ram4 , ioArea , ram6 };
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3 , ram4 , ioArea , ram6 };
                break;
            case 14:
            case 30:
                // RAM 	RAM 	RAM 	RAM 	RAM 	I/O 	KERNAL ROM
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3 , ram4 , ioArea , kernelROM};
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3 , ram4 , ioArea , ram6 };
                break;
            case 15:
                // RAM 	RAM 	CART ROM LO 	BASIC ROM 	RAM 	I/O 	KERNAL
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , cartROMLow , basicROM , ram4 , ioArea , kernelROM };
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2       , ram3     , ram4 , ioArea , ram6 };
                break;
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
                // RAM 	- 	CART ROM LO 	- 	- 	I/O 	CART ROM HI
                readRegions  = new IMemoryRegion[] { ram0 , null , cartROMLow, null , null , ioArea, cartROMHi};
                writeRegions = new IMemoryRegion[] { ram0 , null , ram2      , null , null , ioArea, ram6 };
                break;
            case 27:
                // RAM 	RAM 	RAM 	BASIC ROM 	RAM 	CHAR ROM 	KERNAL ROM
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , ram2 , basicROM , ram4 , charROM , kernelROM };
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3     , ram4 , ram5 , ram6 };
                break;
            case 31: /* >>>>> DEFAULT MEMORY LAYOUT AFTER RESET if no cartridges are present <<<<<< */
                // RAM 	RAM 	RAM 	BASIC ROM 	RAM 	I/O 	KERNAL ROM
                readRegions  = new IMemoryRegion[] { ram0 , ram1 , ram2 , basicROM , ram4 , ioArea , kernelROM };
                writeRegions = new IMemoryRegion[] { ram0 , ram1 , ram2 , ram3     , ram4 , ioArea , ram6 };
                break;
            default:
                throw new RuntimeException("memory layout unknown, unhandled combination of PLA latch bits: 0b"+Integer.toBinaryString( index ));
        }

        // assertions: make sure memory regions are continuous
        if ( readRegions.length != writeRegions.length) {
            throw new RuntimeException("Memory region lengths do not match");
        }
        IMemoryRegion previous = null;
        for (final IMemoryRegion next : readRegions)
        {
            if ( next != null)
            {
                if ( previous != null && next.getAddressRange().getStartAddress() != previous.getAddressRange().getEndAddress() )
                {
                    throw new RuntimeException("Internal error,non-continous memory mapping for reads: "+previous+" | "+next);
                }
                previous = next;
            }
        }
        previous = null;
        for (final IMemoryRegion next : writeRegions)
        {
            if ( next != null)
            {
                if ( previous != null && next.getAddressRange().getStartAddress() != previous.getAddressRange().getEndAddress() )
                {
                    throw new RuntimeException("Internal error,non-continous memory mapping for writes: "+previous+" | "+next);
                }
                previous = next;
            }
        }
        System.out.println( this );
    }

    public void tick(Emulator emulator,CPU cpu,boolean clockHigh)
    {
        ioArea.tick( emulator , cpu , clockHigh );
    }

    private void createRegions(int index)
    {
        // I/O area
        cartROMLow = null;
        cartROMHi = null;

        switch( index )
        {
            case 2:
            case 6:
                cartROMHi = CART_ROM_HI_A000_BFFF;
                break;
            case 3:
            case 7:
                cartROMLow = CART_ROM_LOW_8000_9FFFF;
                cartROMHi = CART_ROM_HI_A000_BFFF;
                break;
            case 11:
            case 15:
                cartROMLow = CART_ROM_LOW_8000_9FFFF;
                break;
            case 16:
            case 17:
            case 18:
            case 19:
            case 20:
            case 21:
            case 22:
            case 23:
                cartROMLow = CART_ROM_LOW_8000_9FFFF;
                cartROMHi = CART_ROM_HI_E000_FFFF;
                break;
        }
    }

    private static WriteOnceMemory loadCharacterROM() {
        WriteOnceMemory rom = new WriteOnceMemory("Char ROM" , Bank.BANK5.range );
        loadROM("character.rom", rom );
        return rom;
    }

    public WriteOnceMemory getCharacterROM() {
        return charROM;
    }

    public static void loadROM(String string, WriteOnceMemory memory)
    {
        final String path ="/roms/"+string;
        if ( DEBUG_ROM_IMAGES ) {
            System.out.println("Loading ROM: "+string);
        }
        final InputStream in = MemorySubsystem.class.getResourceAsStream( path );
        if ( in == null ) {
            throw new RuntimeException("Failed to load ROM from classpath: "+string);
        }
        try
        {
            short offset = 0;
            final byte[] buffer = new byte[1024];
            int bytesRead = 0;
            while ( (bytesRead= in.read(buffer) ) > 0 )
            {
                memory.bulkWrite( offset , buffer , 0 , bytesRead );
                offset += bytesRead;
            }
            if ( offset > memory.getAddressRange().getSizeInBytes() ) {
                throw new RuntimeException("ROM file '"+string+"' ("+offset+" bytes) does not fit into "+memory);
            }
        } catch (final IOException e) {
            throw new RuntimeException("Failed to load ROM from classpath: "+string,e);
        }
        finally
        {
            try { in.close(); } catch(final Exception e) {}
        }

        if ( DEBUG_ROM_IMAGES ) {
            System.out.println("Loaded "+string+" @ "+memory.getAddressRange() );
            //			System.out.println( region.dump( 0 , 128 ) );
            System.out.println( memory.dump( memory.getAddressRange().getSizeInBytes() - 128 , 128 ) );
        }
        memory.writeProtect();
    }

    @Override
    public String dump(int offset, int len)
    {
        return HexDump.INSTANCE.dump( (short) (getAddressRange().getStartAddress()+offset),this,offset,len);
    }

    @Override
    public int readWord(int offset)
    {
        final int low = readByte(offset);
        final int hi = readByte( offset+1 );

        final int result = (hi<<8|low);
        if ( DEBUG_READS ) {
            System.out.println("readWord(): Read word "+HexDump.toAdr(result)+" at "+HexDump.toAdr( offset ) );
        }
        return result;
    }

    @Override
    public int readByte(int offset)
    {
        final int wrappedOffset = offset & 0xffff;
        final IMemoryRegion region = readRegions[ readMap[ wrappedOffset ] ];
        final int translatedOffset = wrappedOffset - region.getAddressRange().getStartAddress();
        return region.readByte( translatedOffset );
    }

    public IMemoryRegion getRAMRegion(int address) 
    {
        return ramRegions[ Bank.getBank( address ).index ];
    }

    public IMemoryRegion[] getRAMRegions() 
    {
        return ramRegions;
    }	

    public void removeRAMBreakpoint(MemoryBreakpoint bp) {
        final IMemoryRegion region = getRAMRegion(bp.address);
        region.getBreakpointsContainer().removeBreakpoint( bp );                 
    }

    public MemoryBreakpoint addRAMBreakpoint(MemoryBreakpoint bp) 
    {
        final IMemoryRegion region = getRAMRegion(bp.address);
        return region.getBreakpointsContainer().addBreakpoint( bp ); 
    }            

    public MemoryBreakpoint addRAMReadBreakpoint(int address) 
    {
        final IMemoryRegion region = getRAMRegion(address);
        return region.getBreakpointsContainer().addReadBreakpoint( address ); 
    }

    public MemoryBreakpoint addRAMReadWriteBreakpoint(int address) {
        final IMemoryRegion region = getRAMRegion(address);
        return region.getBreakpointsContainer().addReadWriteBreakpoint( address ); 
    }	

    public MemoryBreakpoint addRAMWriteBreakpoint(int address) {
        final IMemoryRegion region = getRAMRegion(address);
        return region.getBreakpointsContainer().addWriteBreakpoint( address ); 
    }            

    @Override
    public int readByteNoSideEffects(int offset) {
        final int wrappedOffset = offset & 0xffff;
        final IMemoryRegion region = readRegions[ readMap[ wrappedOffset ] ];
        final int translatedOffset = wrappedOffset - region.getAddressRange().getStartAddress();
        return region.readByteNoSideEffects( translatedOffset );
    }

    public int readAndWriteByte(int offset)
    {
        final int wrappedOffset = offset & 0xffff;
        final IMemoryRegion readRegion = readRegions[ readMap[ wrappedOffset ] ];
        final int translatedOffset = wrappedOffset - readRegion.getAddressRange().getStartAddress();
        final IMemoryRegion writeRegion = writeRegions[ writeMap[ wrappedOffset ] ];
        final int result = readRegion.readByte( translatedOffset );
        writeRegion.writeByte( translatedOffset , (byte) result );
        return result;
    }

    @Override
    public void writeByte(int offset, byte value)
    {
        final int wrappedOffset = offset & 0xffff;
        final IMemoryRegion region = writeRegions[ writeMap[ wrappedOffset ] ];
        final int realOffset = wrappedOffset - region.getAddressRange().getStartAddress();
        region.writeByte( realOffset , value );
    }

    @Override
    public void writeByteNoSideEffects(int offset, byte value) {
        final int wrappedOffset = offset & 0xffff;
        final IMemoryRegion region = writeRegions[ writeMap[ wrappedOffset ] ];
        final int realOffset = wrappedOffset - region.getAddressRange().getStartAddress();
        region.writeByteNoSideEffects( realOffset , value );
    }

    @Override
    public void writeWord(int offset,short value)
    {
        final byte low = (byte) value;
        final byte hi = (byte) (value>>8);

        writeByte( offset , low );
        writeByte( offset+1 , hi );
    }

    @Override
    public void bulkWrite(int startingAddress, byte[] data, int datapos, int len)
    {
        for ( int dstAdr = (startingAddress & 0xffff), bytesLeft = len , src = datapos ; bytesLeft > 0 ; bytesLeft-- )
        {
            writeByte( dstAdr , data[ src++ ] );
            dstAdr = (dstAdr+1) & 0xffff;
        }
    }

    @Override
    public String toString()
    {
        final StringBuilder buffer = new StringBuilder("=== Memory layout ===\n");
        for ( int i = 0 ; i < readRegions.length ; i++ )
        {
            final IMemoryRegion readBank = readRegions[i];
            final IMemoryRegion writeBank = writeRegions[i];

            buffer.append( readBank.getAddressRange().toString() ).append(": ");
            if ( readBank == writeBank ) {
                buffer.append( readBank.getIdentifier() );
            } else {
                buffer.append( "READ:" ).append( readBank.getIdentifier() );
                buffer.append( " WRITE:" ).append( writeBank.getIdentifier() );
            }
            if ( (i+1) < readRegions.length )
            {
                buffer.append("\n");
            }
        }
        return buffer.toString();
    }

    protected final class RAMView extends IMemoryRegion
    {
        public RAMView()
        {
            super("linear RAM", MemoryType.RAM,AddressRange.range(0,0xffff) );
        }

        @Override
        public void reset() {
            // NOP
        }

        @Override
        public void bulkWrite(int startingAddress, byte[] data, int datapos, int len)
        {
            for ( ; len > 0 ; len--)
            {
                writeByte( ( startingAddress++ & 0xffff) ,data[datapos++] );
            }
        }

        @Override
        public int readByte(int offset)
        {
            final int wrappedOffset = offset & 0xffff;
            final IMemoryRegion region = ramRegions[ readMap[ wrappedOffset ] ];
            final int translatedOffset = wrappedOffset - region.getAddressRange().getStartAddress();
            return region.readByte( translatedOffset );
        }

        @Override
        public int readWord(int offset)
        {
            final int low = readByte(offset);
            final int hi = readByte( offset+1 );
            return (hi<<8|low);
        }

        @Override
        public void writeWord(int offset, short value)
        {
            final byte low = (byte) value;
            final byte hi = (byte) (value>>8);

            writeByte( offset , low );
            writeByte( offset+1 , hi );
        }

        @Override
        public void writeByteNoSideEffects(int offset, byte value) {
            final int wrappedOffset = offset & 0xffff;
            final IMemoryRegion region = ramRegions[ writeMap[ wrappedOffset ] ];
            final int realOffset = wrappedOffset - region.getAddressRange().getStartAddress();
            region.writeByteNoSideEffects( realOffset , value );
        }

        @Override
        public void writeByte(int offset, byte value)
        {
            final int wrappedOffset = offset & 0xffff;
            final IMemoryRegion region = ramRegions[ writeMap[ wrappedOffset ] ];
            final int realOffset = wrappedOffset - region.getAddressRange().getStartAddress();
            region.writeByte( realOffset , value );
        }

        @Override
        public String dump(int offset, int len)
        {
            return HexDump.INSTANCE.dump( (short) (getAddressRange().getStartAddress()+offset),this,offset,len);
        }

        @Override
        public int readByteNoSideEffects(int offset) {
            final int wrappedOffset = offset & 0xffff;
            final IMemoryRegion region = ramRegions[ readMap[ wrappedOffset ] ];
            final int translatedOffset = wrappedOffset - region.getAddressRange().getStartAddress();
            return region.readByte( translatedOffset );
        }
    }

    /**
     * Returns a continuous view of all RAM for use by the VIC.
     * @return
     */
    public RAMView getRAMView() {
        return ramView;
    }

    public int saveRAM(AddressRange range,OutputStream out) throws IOException 
    {
        try 
        {
            int bytesSaved = 0;
            for ( int i = range.getStartAddress() , end = range.getEndAddress() ; i < end ; i++ ) 
            {
                final IMemoryRegion region = getRAMRegion( i );
                // System.out.println( Misc.to16BitHex( i ) +" => "+region);
                final int translated = i - region.getAddressRange().getStartAddress();
                int data = region.readByteNoSideEffects( translated );
                // System.out.println( Misc.to16BitHex( i ) +" "+region+" => "+Misc.to8BitHex( data ) );
                out.write( data );
                bytesSaved++;
            }
            return bytesSaved;
        } finally {
            out.close();
        }
    }

    public int restoreRAM(InputStream in,int address) throws IOException 
    {
        try 
        {
            int ptr = address;
            int data;
            int bytesLoaded = 0;
            while ( (data = in.read() ) != -1 ) 
            {
                final IMemoryRegion region = getRAMRegion( ptr );
                final int translated = ptr - region.getAddressRange().getStartAddress();
                region.writeByteNoSideEffects( translated , (byte) data);
                ptr = (ptr+1) & 0xffff;
                bytesLoaded++;
                if ( bytesLoaded == 65537 ) {
                    throw new IOException("File larger than 64kb ?");
                }
            }
            return bytesLoaded;
        } finally {
            in.close();
        }
    }

    @Override
    public void restoreState(EmulationState state)
    {
        try 
        {
            final EmulationStateEntry entry = state.getEntry( EntryType.RAM );
            if ( Constants.MEMORY_DEBUG_STATE_PERSISTENCE ) {
                System.out.println("Restoring "+entry.payloadLength()+" bytes to RAM...");
            }
            restoreRAM( new ByteArrayInputStream( entry.getPayload() ) , 0 );
            ioArea.restoreState( state );
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void saveState(EmulationState state)
    {
        final ByteArrayOutputStream out = new ByteArrayOutputStream();
        try 
        {
            saveRAM( AddressRange.range( 0, 0xffff ) , out );
            // save RAM
            final EmulationStateEntry entry = new EmulationStateEntry( EntryType.RAM , (byte) 1 , out.toByteArray() );
            if ( Constants.MEMORY_DEBUG_STATE_PERSISTENCE ) {
                System.out.println("Saving "+entry.payloadLength()+" bytes of RAM...");
            }            
            state.add( entry );

            // save I/O area
            ioArea.saveState( state );
        } 
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }            
}