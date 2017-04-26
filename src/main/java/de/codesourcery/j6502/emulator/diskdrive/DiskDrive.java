package de.codesourcery.j6502.emulator.diskdrive;

import de.codesourcery.j6502.emulator.AddressRange;
import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.CPUImpl;
import de.codesourcery.j6502.emulator.IMemoryRegion;
import de.codesourcery.j6502.emulator.Memory;
import de.codesourcery.j6502.emulator.MemorySubsystem;
import de.codesourcery.j6502.emulator.SlowMemory;
import de.codesourcery.j6502.emulator.WriteOnceMemory;
import de.codesourcery.j6502.utils.HexDump;

public class DiskDrive extends IMemoryRegion 
{
    protected static final boolean TRACK_JOBQUEUE = false;

    private static final int ROM_START1 = 0x8000;
    private static final int ROM_START2 = 0xc000;

    private static final AddressRange ROM1_RANGE = AddressRange.range(ROM_START1,0xc000);
    private static final AddressRange ROM2_RANGE = AddressRange.range(ROM_START2,0xffff);

    private final IMemoryRegion ram;

    private WriteOnceMemory rom1 = new WriteOnceMemory( "ROM" , ROM1_RANGE );
    private WriteOnceMemory rom2 = new WriteOnceMemory( "ROM" , ROM2_RANGE );

    private final JobQueue[] queueEntries = new JobQueue[6];
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

    private final IMemoryRegion nonExistant = new IMemoryRegion("DUMMY",MemoryType.RAM, new AddressRange(0x800,0xb800 )) {

        @Override
        public void writeWord(int offset, short value) {
            throw new RuntimeException("Write to non-existant memory location "+HexDump.toAdr( offset ) );
        }

        @Override
        public void writeByte(int offset, byte value) {
            throw new RuntimeException("Write to non-existant memory location "+HexDump.toAdr( offset ) );            
        }
        
        @Override
        public void writeByteNoSideEffects(int offset, byte value) {
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

        public int readByteNoSideEffects(int offset) {
            return 0;
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
        super("1541", MemoryType.RAM, AddressRange.range(0,0xffff) );

        for ( int i = 0 ; i < queueEntries.length ; i++ ) {
            queueEntries[i]=new JobQueue(i);
        }
        
        this.ram = createRAM();
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

    private IMemoryRegion createRAM() 
    {
        final AddressRange adrRange = new AddressRange(0,2*1024);
        if ( ! TRACK_JOBQUEUE ) 
        {
            return new Memory( "RAM" , MemoryType.RAM, adrRange );
        }
        return new SlowMemory( "RAM" , MemoryType.RAM, adrRange ) {

            public void writeByte(int offset, byte value) 
            {
                super.writeByte(offset,value);
                int index = -1;
                switch( offset ) {
                    case 0x00:
                    case 0x06:
                    case 0x07:
                        index = 0;
                        break;
                    case 0x01:
                    case 0x08:
                    case 0x09:
                        index = 1;
                        break;
                    case 0x02:
                    case 0x0a:
                    case 0x0b:
                        index = 2;
                        break;      
                    case 0x03:
                    case 0x0c:
                    case 0x0d:
                        index = 3;
                        break;   
                    case 0x04:
                    case 0x0e:
                    case 0x0f:
                        index = 4;
                        break;      
                    case 0x05:
                    case 0x10:
                    case 0x11:
                        index = 5;
                        break;                       
                    default:
                        return;
                }
                queueEntries[index].update( this );
//                System.out.println("JOB QUEUE #"+index+" changed: "+queueEntries[index]);
            };
        };
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

    /**
     * 
     * @return false if HW breakpoint reached, otherwise true
     */
    public boolean executeOneCPUCycle()
    {
        if ( --cpu.cycles == 0 ) // wait until current command has 'finished' executing
        {
            cpu.handleInterrupt();

            cpuImpl.executeInstruction();
        }   
        return cpu.isBreakpointReached() ? false:true; 
    }

    @Override
    public int readByte(int offset) 
    {
        final IMemoryRegion region = memoryMap[offset];
        final int translated = offset - region.getAddressRange().getStartAddress();
        return region.readByte( translated );
    }

    @Override
    public int readByteNoSideEffects(int offset) {
        final IMemoryRegion region = memoryMap[offset];
        final int translated = offset - region.getAddressRange().getStartAddress();
        return region.readByteNoSideEffects( translated );
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
    public void writeByteNoSideEffects(int offset, byte value) {
        final IMemoryRegion region = memoryMap[offset];
        final int translated = offset - region.getAddressRange().getStartAddress();
        region.writeByteNoSideEffects( translated , value );      
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


    public static enum JobStatus 
    {
        /*
     |  $80  | READ    | Read sector                   |
     |  $90  | WRITE   | Write sector (includes $A0)   |
     |  $A0  | VERIFY  | Verify sector                 |
     |  $B0  | SEEK    | Find sector                   |
     |  $C0  | BUMP    | Bump, Find track 1            |
     |  $D0  | JUMP    | Execute program in buffer     |
     |  $E0  | EXECUTE | Execute program, first switch |
     |       |         | drive on and find track       |         

     | $01  | Everything OK                  | 00, OK               |
     | $02  | Header block not found         | 20, READ ERROR       |
     | $03  | SYNC not found                 | 21, READ ERROR       |
     | $04  | Data block not found           | 22, READ ERROR       |
     | $05  | Checksum error in data block   | 23, READ ERROR       |
     | $07  | Verify error                   | 25, WRITE ERROR      |
     | $08  | Disk write protected           | 26, WRITE PROTECT ON |
     | $09  | Checksum error in header block | 27, READ ERROR       |
     | $0B  | Id mismatch                    | 29, DISK ID MISMATCH |
     | $0F  | Disk not inserted              | 74, DRIVE NOT READY  |     
         */
        IDLE(0x00),
        READ    (0x80),
        WRITE   (0x90),
        VERIFY  (0xA0),
        SEEK    (0xB0),
        BUMP    (0xC0),
        JUMP    (0xD0),
        EXECUTE (0xE0),
        UNKNOWN(0xE1), // not used by 1541, just my internal marker        
        // error codes
        STATUS_OK(0x01),
        STATUS_HDR_BLOCK_NOT_FOUND(0x02),
        STATUS_SYNC_NOT_FOUND(0x03),
        STATUS_DATA_BLOCK_NOT_FOUND(0x04),
        STATUS_CHECKSUM_ERR_IN_DATA_BLOCK(0x05),
        STATUS_VERIFY_ERROR(0x07),
        STATUS_DISK_WRITE_PROTECTED(0x08),
        STATUS_CHECKSUM_ERR_IN_HEADER_BLOCK(0x09),
        STATUS_ID_MISMATCH(0x0b),
        STATUS_NO_DISK(0x0f);

        private int code;

        private JobStatus(int code) {
            this.code = code;
        }

        public boolean isError() 
        {
            return this != IDLE && (code & 0b1000_0000) == 0;
        }

        public boolean isIdle() {
            return this == IDLE;
        }

        public static JobStatus fromCode(int code) {
            for ( JobStatus s : values() ) {
                if ( s.code == code ) {
                    return s;
                }
            }
            return JobStatus.UNKNOWN;
        }
    }

    public static final class JobQueue {

        public final int index;
        public JobStatus status = JobStatus.IDLE;
        public int track;
        public int sector;

        public JobQueue(int index) {
            this.index = index;
        }

        public boolean update(IMemoryRegion region) 
        {
            JobStatus newStatus = JobStatus.fromCode( region.readByte( index ) );
            int newTrack  = region.readByte( 0x06 + index*2 );
            int newSector = region.readByte( 0x07 + index*2 );
            boolean changed = newStatus != status || newTrack != track || newSector != sector;
            this.status = newStatus;
            this.track = newTrack;
            this.sector = newSector;
            return changed;
        }

        public void copyTo(JobQueue other) 
        {
            if (other.index != this.index ) {
                throw new IllegalArgumentException("Queue index mismatch: "+this.index+" <-> "+other.index);
            }
            other.status = this.status;
            other.track = this.track;
            other.sector = this.sector;
        }

        @Override
        public String toString() {
            return status.name()+" - track "+track+" , sector "+sector;
        }        
    }

    public JobQueue[] getQueueEntries() {
        return queueEntries;
    }
}