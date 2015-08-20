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

public class Mainboard extends IMemoryRegion 
{
    protected static final boolean PRINT_DISASSEMBLY = false;
    
    private static final int ROM_START = 0xc000;
    private static final AddressRange ROM_RANGE = AddressRange.range(ROM_START,0x10000);
    
    private final Memory ram = new Memory( "RAM" , new AddressRange(0,2*1024) );
    private WriteOnceMemory rom = new WriteOnceMemory( "ROM" , ROM_RANGE );

    private final CPU cpu = new CPU( this );
    private final CPUImpl cpuImpl = new CPUImpl( cpu , this );
    
    private final VIA busController = new BusController();
    private final VIA diskController = new DiskController();
    
    private final IMemoryRegion[] memoryMap;
    
    private final IMemoryRegion nonExistant = new IMemoryRegion("DUMMY",new AddressRange(0,65536 )) {
        
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
        public int readWord(int offset) {
            throw new RuntimeException("Read from non-existant memory location "+HexDump.toAdr( offset ) );
        }
        
        @Override
        public int readByte(int offset) {
            throw new RuntimeException("Read from non-existant memory location "+HexDump.toAdr( offset ) );
        }
        
        @Override
        public int readAndWriteByte(int offset) {
            throw new RuntimeException("Read from non-existant memory location "+HexDump.toAdr( offset ) );
        }
        
        @Override
        public boolean isReadsReturnWrites(int offset) {
            return true;
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
    
    public Mainboard() 
    {
        super("1541", AddressRange.range(0,65536) );
        
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
            } else if ( i >= 0xc000 ) {
                memoryMap[i] = rom;
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
        
        final WriteOnceMemory newRom = new WriteOnceMemory( "ROM" , ROM_RANGE );
        MemorySubsystem.loadROM( "1541-c000.325302-01.bin" , rom );        
        for ( int i = 0 ; i < 65536 ; i++ ) {
            if ( memoryMap[i] == rom ) {
                memoryMap[i] = newRom;
            }
        }
        rom = newRom;
        
        cpu.reset();
    }
    
    public void tick()
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
        return region.readByte( translated );
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
}