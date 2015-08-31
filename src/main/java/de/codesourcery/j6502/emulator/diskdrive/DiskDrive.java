package de.codesourcery.j6502.emulator.diskdrive;

import java.util.function.Consumer;

import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.disassembler.Disassembler.Line;
import de.codesourcery.j6502.emulator.AddressRange;
import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.CPUImpl;
import de.codesourcery.j6502.emulator.IMemoryRegion;
import de.codesourcery.j6502.emulator.Memory;
import de.codesourcery.j6502.emulator.MemorySubsystem;
import de.codesourcery.j6502.emulator.WriteOnceMemory;
import de.codesourcery.j6502.utils.HexDump;

public class DiskDrive extends IMemoryRegion 
{
    protected static final boolean PRINT_DISASSEMBLY = false;

    private static final int ROM_START1 = 0x8000;
    private static final int ROM_START2 = 0xc000;

    private static final AddressRange ROM1_RANGE = AddressRange.range(ROM_START1,0xc000);
    private static final AddressRange ROM2_RANGE = AddressRange.range(ROM_START2,0xffff);

    private final Memory ram = new Memory( "RAM" , new AddressRange(0,2*1024) );
    private WriteOnceMemory rom1 = new WriteOnceMemory( "ROM" , ROM1_RANGE );
    private WriteOnceMemory rom2 = new WriteOnceMemory( "ROM" , ROM2_RANGE );

    private final CPU cpu = new CPU( this );
    private final CPUImpl cpuImpl = new CPUImpl( cpu , this );

    private final VIA busController = new VIA("BusController VIA 6522 #1", new AddressRange( 0x1800 , 0x1810 ) , cpu ) {

        protected String getNamePortB(int bit) {
            /*
             * VIA1:
             * 
             * PA0..7 - unused
             * 
             * PB0 - (IN) Data in
             * PB1 - (OUT) Data out
             * PB2 - (IN) Clock in
             * PB3 - (OUT) Clock out
             * PB4 - (OUT) ATN Acknowledge
             * PB5 - (IN) Device address
             * PB6 - (IN) Device address             
             */      
            switch( bit ) {
                case 0: return "Data IN";
                case 1: return "Data OUT";
                case 2: return "Clock IN";
                case 3: return "Clock OUT";
                case 4: return "ATN ack";
                case 5: return "Device adr #1";
                case 6: return "Device adr #0";
                case 7: return "ATN IN";
                default:
                    return super.getNamePortB(bit);
            }
        }
    };

    private final VIA diskController = new VIA("DiskController VIA 6522 #2", AddressRange.range( 0x1c00 , 0x1c10) , cpu ) {

        protected String getNamePortB(int bit) {
         /*
                  * PA0...8 - (IN/OUT) Read data/Write data 
     * 
     * PB0 - (OUT) Step 1  |   Sequence 00/01/10/11/00... moves inwards      | 
     * PB1 - (OUT) Step 0  |   Sequence 00/11/10/01/00... moves outwards     | 
     * PB2 - (OUT) Drive motor AND Step Motor on/off
     * PB3 - (OUT) LED
     * PB4 - (IN) Write-protect
     * PB5 - (OUT) bitrate select A (??)
     * PB6 - (OUT) bitrate select B (??)
     * PB7 - (IN)  SYNC detected
          */
            switch( bit ) {
                case 0: return "Step #1 (OUT)";
                case 1: return "Step #0 (OUT)";
                case 2: return "Motors (OUT)";
                case 3: return "LED (OUT)";
                case 4: return "Write-Protect (IN)";
                case 5: return "bitrate #1 (OUT)";
                case 6: return "bitrate #0 (OUT)";
                case 7: return "SYNC (IN)";
                default:
                    return super.getNamePortB(bit);
            }
        }
    }; 

    private final DiskHardware hardware;

    private final IMemoryRegion[] memoryMap;

    private final IMemoryRegion nonExistant = new IMemoryRegion("DUMMY",new AddressRange(0x800,0xb800 )) {

        @Override
        public void writeWord(int offset, short value) {
            throw new RuntimeException("Write to non-existant memory location "+HexDump.toAdr( offset ) );
        }

        @Override
        public void writeByte(int offset, byte value) {
            throw new RuntimeException("Write to non-existant memory location "+HexDump.toAdr( offset ) );            
        }

        @Override
        public void reset() {
        }

        @Override
        public int readWord(int offset) 
        {
            return 0;
        }

        @Override
        public int readByte(int offset) {
            return 0;
        }

        @Override
        public int readAndWriteByte(int offset) {
            throw new RuntimeException("Read from non-existant memory location "+HexDump.toAdr( offset ) );
        }

        @Override
        public boolean isReadsReturnWrites(int offset) {
            return false;
        }

        @Override
        public String dump(int offset, int len) {
            return "<non-existant memory>";
        }

        @Override
        public void bulkWrite(int startingAddress, byte[] data, int datapos,int len) {
            throw new RuntimeException("Write to non-existant memory location "+HexDump.toAdr( startingAddress ) );      
        }
    };    

    public DiskDrive(int driveAddress) 
    {
        super("1541", AddressRange.range(0,0xffff) );
        this.hardware = new DiskHardware(this,busController,diskController, driveAddress);

        memoryMap = new IMemoryRegion[65536];

        /* 1541 memory layout
         * 
         * $0000     RAM
         * $07ff
         * ..
         * $1800     Bus controller
         * $180f
         * ..
         * $1c00     Disk controller
         * $1c0f
         * ..
         * $c000     ROM     
         * $ffff
         */
        for ( int i = 0 ; i < 65536 ; i++ ) {
            if ( i < 2048 ) {
                memoryMap[i] = ram;
            } else if ( i >= 0x1800 && i < 0x1810 ) {
                memoryMap[i] = busController;
            } else if ( i >= 0x1c00 && i < 0x1c10 ) {
                memoryMap[i] = diskController;
            } else if ( i < 0xc000 && i >= 0x8000 ) {
                memoryMap[i] = rom1;
            } else if ( i >= 0xc000 ) {
                memoryMap[i] = rom2;
            } else {
                memoryMap[i] = nonExistant;
            }
        }
    }

    @Override
    public void reset() 
    {
        busController.reset();
        diskController.reset();
        ram.reset();

        final String romFile = "1541-II.251968-03.bin";
        if ( ! rom1.isWriteProtected() ) {
            MemorySubsystem.loadROM( romFile , rom1 );
        }
        if ( ! rom2.isWriteProtected() ) {
            MemorySubsystem.loadROM( romFile , rom2 );
        }

        cpu.reset();
    }

    public void executeOneCPUCycle()
    {
        if ( cpu.cycles > 0 ) // wait until current command has 'finished' executing
        {
            cpu.cycles--;
        }
        else
        {
            cpu.handleInterrupt();

            if ( PRINT_DISASSEMBLY )
            {
                System.out.println("=====================");
                final Disassembler dis = new Disassembler();
                dis.setAnnotate( true );
                dis.setWriteAddresses( true );
                dis.disassemble( this , cpu.pc() , 3 , new Consumer<Line>()
                {
                    private boolean linePrinted = false;

                    @Override
                    public void accept(Line line)
                    {
                        if ( ! linePrinted ) {
                            System.out.println( line );
                            linePrinted = true;
                        }
                    }
                });
            }

            cpuImpl.executeInstruction();

            if ( PRINT_DISASSEMBLY ) {
                System.out.println( cpu );
            }
        }        
    }

    @Override
    public int readByte(int offset) 
    {
        final IMemoryRegion region = memoryMap[offset];
        final int translated = offset - region.getAddressRange().getStartAddress();
        try {
            return region.readByte( translated );
        } catch(ArrayIndexOutOfBoundsException e) {
            System.err.println("Failed to read from "+region+" with offset "+HexDump.toAdr( offset )+" [ translated: "+HexDump.toAdr( translated ) );
            throw e;
        }
    }

    @Override
    public int readAndWriteByte(int offset) 
    {
        final IMemoryRegion region = memoryMap[offset];
        final int translated = offset - region.getAddressRange().getStartAddress();
        return region.readAndWriteByte( translated );
    }

    @Override
    public int readWord(int offset) 
    {
        final IMemoryRegion region = memoryMap[offset];
        final int translated = offset - region.getAddressRange().getStartAddress();
        return region.readWord( translated );
    }

    @Override
    public void writeWord(int offset, short value) {
        final IMemoryRegion region = memoryMap[offset];
        final int translated = offset - region.getAddressRange().getStartAddress();
        region.writeWord( translated , value );
    }

    @Override
    public void writeByte(int offset, byte value) {
        final IMemoryRegion region = memoryMap[offset];
        final int translated = offset - region.getAddressRange().getStartAddress();
        region.writeByte( translated , value );      
    }

    @Override
    public void bulkWrite(int startingAddress, byte[] data, int datapos, int len) 
    {
        for ( int i = 0 ; i < len ; i++ ) {
            writeByte( startingAddress+i , data[ datapos+i ] );
        }
    }    

    @Override
    public String dump(int offset, int len) {
        return HexDump.INSTANCE.dump( (short) (getAddressRange().getStartAddress()+offset),this,offset,len);
    }

    @Override
    public boolean isReadsReturnWrites(int offset) {
        return true;
    }

    public DiskHardware getHardware() {
        return hardware;
    }

    public CPU getCPU() {
        return cpu;
    }
}