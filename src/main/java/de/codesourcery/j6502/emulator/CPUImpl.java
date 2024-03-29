package de.codesourcery.j6502.emulator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.codesourcery.j6502.Constants;
import de.codesourcery.j6502.emulator.CPU.Flag;
import de.codesourcery.j6502.emulator.exceptions.HLTException;
import de.codesourcery.j6502.utils.HexDump;

/**
 * Ported from C code in http://codegolf.stackexchange.com/questions/12844/emulate-a-mos-6502-cpu 
 * & heavily modified.
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class CPUImpl
{
    protected IMemoryRegion memory;
    protected CPU cpu;
    protected boolean penaltyop, penaltyaddr;

    protected int ea, reladdr, value;

    protected abstract class AbstractRunnable 
    {
        public abstract void run();
    }

    protected final NOPRunnable NOP = new NOPRunnable();

    protected final class NOPRunnable extends AbstractRunnable 
    {
        @Override
        public void run() {
        }
    }

    public CPUImpl(CPU cpu,IMemoryRegion region)
    {
        this.cpu = cpu;
        this.memory = region;
    }

    protected void saveaccum(int n) {
        cpu.setAccumulator( n );
    }

    //flag modifier macros
    protected void setcarry() {
        cpu.setFlag( Flag.CARRY );
    }

    protected void clearcarry() {
        cpu.clearFlag( Flag.CARRY );
    }

    protected void setzero() {
        cpu.setFlag( Flag.ZERO );
    }

    protected void clearzero() {
        cpu.clearFlag(Flag.ZERO);
    }

    protected void setinterrupt()
    {
        cpu.setFlag( Flag.IRQ_DISABLE );
    }

    protected void clearinterrupt()
    {
        cpu.clearFlag( Flag.IRQ_DISABLE );
    }

    protected void setdecimal() {
        cpu.setFlag(Flag.DECIMAL_MODE);
    }

    protected void cleardecimal() {
        cpu.clearFlag(Flag.DECIMAL_MODE);
    }

    protected void setoverflow() { 
        cpu.setFlag(Flag.OVERFLOW); 
    }

    protected void clearoverflow() { 
        cpu.clearFlag(Flag.OVERFLOW); 
    }

    protected int readAndWrite6502(int address) {
        int value = memory.readByte( address );
        memory.writeByte( address , (byte) value );
        return value;
    }
    protected void setsign() { 
        cpu.setFlag(Flag.NEGATIVE); 
    }

    protected void clearsign() { 
        cpu.clearFlag(Flag.NEGATIVE); 
    }

    //flag calculation macros
    protected void zerocalc(int n)
    {
        if ( (n & 0xff) != 0 ) {
            clearzero();
        } else {
            setzero();
        }
    }

    protected void signcalc(int n)
    {
        if ( (n & 0x80 ) != 0 )
        {
            setsign();
        } else {
            clearsign();
        }
    }

    protected void carrycalc(int n) {
        if (( n & 0xFF00) != 0 ) {
            setcarry();
        } else {
            clearcarry();
        }
    }

    protected void overflowcalc(int n, int m, int o) { /* n = result, m = accumulator, o = memory */

        final int term1 = n ^ (m & 0xffff);
        final int term2 = n ^ o & 0x0080;
        if ( (term1 & term2) != 0 )
        {
            setoverflow();
        } else {
            clearoverflow();
        }
    }

    //a few general functions used by various other functions
    protected void push16(int pushval)
    {
        cpu.pushWord( (short) pushval , memory );
    }

    protected void push8(int pushval)
    {
        cpu.pushByte( (byte) pushval , memory );
    }

    protected int pull16()
    {
        return cpu.pop( memory ) | ( cpu.pop( memory ) << 8 );
    }

    protected int pull8() {
        return cpu.pop( memory );
    }

    //addressing mode functions, calculates effective addresses
    protected final AbstractRunnable imp = NOP; //implied

    protected final AbstractRunnable  acc = NOP;  //accumulator

    protected AbstractRunnable imm = new AbstractRunnable() 
    {
        @Override
        public void run() { //immediate
            ea = cpu.pc();
            cpu.incPC();
        }
    };

    protected final AbstractRunnable zp = new AbstractRunnable() {
        @Override
        public void run() { //zero-page
            ea = read6502( cpu.pc() );
            cpu.incPC();
        }
    };

    protected final AbstractRunnable zpx = new AbstractRunnable() {
        @Override
        public void run() { //zero-page,X
            ea = (read6502( cpu.pc() ) + cpu.getX() ) & 0xFF; //zero-page wraparound
            cpu.incPC();
        }
    };

    protected final AbstractRunnable zpy = new AbstractRunnable() {
        @Override
        public void run() { //zero-page,Y
            ea = (read6502( cpu.pc() ) + cpu.getY() ) & 0xFF; //zero-page wraparound
            cpu.incPC();
        }
    };

    protected final AbstractRunnable  rel = new AbstractRunnable() {
        @Override
        public void run() { //relative for branch ops (8-bit immediate value, sign-extended)
            reladdr = read6502(cpu.pc());
            cpu.incPC();
            if ( (reladdr & 0x80) != 0 ) // negative branch offset ?
            {
                reladdr |= 0xFFFFFF00; // sign extent byte -> int
            }
        }
    };

    protected final AbstractRunnable abso= new AbstractRunnable() {
        @Override
        public void run() { //absolute
            ea = memory.readWord( cpu.pc() );
            cpu.incPC(2);
        }
    };

    protected final AbstractRunnable absx = new AbstractRunnable() {
        @Override
        public void run() { //absolute,X
            ea = memory.readWord( cpu.pc() );
            final int startpage = ea & 0xFF00;
            ea += cpu.getX();

            if (startpage != (ea & 0xFF00)) { //one cycle penalty for page-crossing
                penaltyaddr = true;
            }

            cpu.incPC(2);
        }
    };

    protected final AbstractRunnable absy = new AbstractRunnable() {
        @Override
        public void run() { //absolute,Y
            ea = memory.readWord( cpu.pc() );
            final int startpage = ea & 0xFF00;
            ea += cpu.getY();

            if (startpage != (ea & 0xFF00)) { //one cycle penalty for page-crossing
                penaltyaddr = true;
            }
            cpu.incPC(2);
        }
    };

    protected final AbstractRunnable igb = new AbstractRunnable() {
        @Override
        public void run() {
            cpu.incPC();
        }
    };

    protected final AbstractRunnable igw = new AbstractRunnable() {
        @Override
        public void run() {
            cpu.incPC(2);
        }
    };

    protected final AbstractRunnable ind = new AbstractRunnable() {
        @Override
        public void run() { //indirect
            int eahelp = memory.readWord( cpu.pc() );
            int eahelp2 = (eahelp & 0xFF00) | ((eahelp + 1) & 0x00FF); //replicate 6502 page-boundary wraparound bug
            ea = read6502(eahelp) | (read6502(eahelp2) << 8);
            cpu.incPC(2);
        }
    };

    protected final AbstractRunnable indJmp = new AbstractRunnable() { // indirect (JMP)
        @Override
        public void run() 
        {
            /* 6502 quirk:
             * The 6502's memory indirect jump instruction, JMP (<address>), is partially broken. 
             * If <address> is hex xxFF (i.e., any word ending in FF), 
             * the processor will not jump to the address stored in xxFF and xxFF+1 as expected, 
             * but rather the one defined by xxFF and xx00.
             * For example, JMP ($10FF) would jump to the address stored in 10FF and 1000, 
             * instead of the one stored in 10FF and 1100). 
             */
            final int adr = memory.readWord( cpu.pc() );
            if ( ( adr & 0xff) == 0xff )
            {
                final int low = memory.readByte( adr );
                final int hi = memory.readByte( adr & 0xff00 );
                ea = memory.readWord( hi<<8 | low );
            } 
            else {
                ea = memory.readWord( adr );
            }
            cpu.incPC(2);
        }
    };    

    protected final AbstractRunnable indx = new AbstractRunnable() {
        @Override
        public void run() { // (indirect,X)

            int eahelp = ( read6502( cpu.pc() ) + cpu.getX() ) & 0xFF; //zero-page wraparound for table pointer
            cpu.incPC();
            ea = read6502(eahelp & 0x00FF) | read6502((eahelp+1) & 0x00FF) << 8;
        }
    };

    protected final AbstractRunnable indy = new AbstractRunnable() {
        @Override
        public void run() { // (indirect),Y
            final int eahelp = read6502(cpu.pc());
            cpu.incPC();

            final int eahelp2 = (eahelp & 0xFF00) | ((eahelp + 1) & 0x00FF); //zero-page wraparound
            ea = read6502(eahelp) | (read6502(eahelp2) << 8);
            final int startpage = ea & 0xFF00;
            ea += cpu.getY();

            if (startpage != (ea & 0xFF00)) { //one cycle penalty for page-crossing
                penaltyaddr = true;
            }
        }
    };

    protected int getvalue()
    {
        if ( isAccumulatorImmediate ) {
            return cpu.getAccumulator();
        }
        return read6502(ea);
    }

    protected int getvaluereadwrite()
    {
        if ( isAccumulatorImmediate ) {
            return cpu.getAccumulator();
        }
        return readAndWrite6502(ea);
    }

    protected void putvalue(int saveval) {
        if ( isAccumulatorImmediate )
        {
            cpu.setAccumulator(saveval);
        } else {
            write6502(ea, saveval);
        }
    }

    protected int read6502(int address)
    {
        return memory.readByte( address );
    }

    protected void write6502(int address,int value)
    {
        memory.writeByte( address , (byte) value );
    }    

    //instruction handler functions
    protected final AbstractRunnable adcNew = new AbstractRunnable() 
    {
        @Override
        public void run() 
        {
            penaltyop = true;
            value = getvalue();
            final int accu = cpu.getAccumulator();
            final int b = value;
            
            int result;
            if ( cpu.isSet( Flag.DECIMAL_MODE ) ) 
            {
                result = bcdAdd( accu , b );
            } else {
                final int carry = cpu.isSet( Flag.CARRY) ? 1 : 0;
                final int resultUnsigned = accu + b + carry;
                result = signExtend( accu ) + signExtend( b ) + carry;
                cpu.setFlag(Flag.CARRY , resultUnsigned > 255 );
            }
            saveaccum(result);
            
            cpu.setFlag(Flag.NEGATIVE , (result & 0b1000_0000) != 0 );
            cpu.setFlag(Flag.ZERO , (result & 0xff) == 0 );
            cpu.setFlag(Flag.OVERFLOW , result < -128 || result > 127);
        }
    };
    
    /*
1a. AL = (A & $0F) + (B & $0F) + C
1b. If AL >= $0A, then AL = ((AL + $06) & $0F) + $10
1c. A = (A & $F0) + (B & $F0) + AL
1d. Note that A can be >= $100 at this point
1e. If (A >= $A0), then A = A + $60
1f. The accumulator result is the lower 8 bits of A
1g. The carry result is 1 if A >= $100, and is 0 if A < $100     
     */
    private int bcdAdd(int a,int b) 
    {
        int resultLow = (a & 0xf) + ( b & 0xf ) + ( cpu.isSet( Flag.CARRY ) ? 1 : 0 );
        if ( resultLow > 0x09 ) {
            resultLow = ((resultLow + 0x06) & 0x0F) + 0x10;
        }
        int result = ( a & 0xf0 ) + ( b & 0xf0 ) + resultLow;
        if ( result >= 0xa0 ) {
            result += 0x60;
        }
        cpu.setFlag(Flag.CARRY , result >= 0x100 );
        return result & 0xff;
    }
    
    private int bcdSub(int a,int b) 
    {
        /*
3a. AL = (A & $0F) - (B & $0F) + C-1
3b. If AL < 0, then AL = ((AL - $06) & $0F) - $10
3c. A = (A & $F0) - (B & $F0) + AL
3d. If A < 0, then A = A - $60
3e. The accumulator result is the lower 8 bits of A         
         */
        int resultLow = (a & 0xf) - ( b & 0xf ) + ( cpu.isSet(Flag.CARRY) ? 0 : 1 );
        boolean isCarry = resultLow < 0;
        cpu.setFlag(Flag.CARRY , isCarry );
        if ( isCarry ) {
            resultLow = ( ( resultLow - 0x06 ) & 0x0f) - 0x10;
        }
        int resultHi = (a & 0xf0) - ( b & 0xf0 ) + resultLow;
        isCarry = resultHi < 0 ;
        cpu.setFlag( Flag.CARRY , isCarry );
        if ( isCarry ) {
            resultHi -= 0x60;
        }
        return resultHi & 0xff;
    } 
    
    private static int signExtend(int a) 
    {
        final byte v = (byte) a;
        return v;
    }
    
    protected final AbstractRunnable sbcNew = new AbstractRunnable() {

        @Override
        public void run() 
        {
            penaltyop = true;
            value = getvalue();
            final int accu = cpu.getAccumulator();
            final int b = value;
            
            final int result;
            if ( cpu.isSet( Flag.DECIMAL_MODE ) ) 
            {
                result = bcdSub( accu , b );
            } 
            else 
            {
                final int carry = cpu.isSet( Flag.CARRY) ? 0 : 1;
                final int resultUnsigned = accu - b - carry;
                result = signExtend( accu ) - signExtend( b ) - carry;
                cpu.setFlag(Flag.CARRY , (resultUnsigned & 0b1_0000_0000) == 0 );
            }
            
            cpu.setFlag(Flag.NEGATIVE , (result & 0b1000_0000) != 0 );
            cpu.setFlag(Flag.ZERO , (result & 0xff) == 0 );
            cpu.setFlag(Flag.OVERFLOW , result < -128 || result > 127);
            saveaccum(result);
        }
    };    
    
    protected final AbstractRunnable and = new AbstractRunnable() {
        @Override
        public void run() {
            penaltyop = true;
            value = getvalue();
            int result = cpu.getAccumulator() & value;

            zerocalc(result);
            signcalc(result);

            saveaccum(result);
        }
    };

    protected final AbstractRunnable asl = new AbstractRunnable() {
        @Override
        public void run() {
            value = getvaluereadwrite();

            /* http://dustlayer.com/c64-coding-tutorials/2013/4/8/episode-2-3-did-i-interrupt-you
             * Now as coders are looking for optimization all the time somebody found out that the decrement command dec
             * can be used to do both operations with just one single command.
             * This works because dec is a so-called Read-Modify-Write command that writes back the original value during the modify cycle.
             */
            putvalue(value);

            int result = value << 1;

            carrycalc(result);
            zerocalc(result);
            signcalc(result);

            putvalue(result);
        }
    };

    protected final AbstractRunnable bcc = new AbstractRunnable() {
        @Override
        public void run() {
            if ( cpu.isNotSet( CPU.Flag.CARRY) )
            {
                final int oldpc = cpu.pc();
                cpu.incPC( reladdr );
                if ((oldpc & 0xFF00) != ( cpu.pc() & 0xFF00))
                {
                    cpu.cycles += 2; //check if jump crossed a page boundary
                }
                else {
                    cpu.cycles++;
                }
            }
        }
    };

    protected final AbstractRunnable bcs = new AbstractRunnable() {
        @Override
        public void run() {
            if (cpu.isSet( CPU.Flag.CARRY ) )
            {
                final int oldpc = cpu.pc();
                cpu.incPC( reladdr );
                if ((oldpc & 0xFF00) != ( cpu.pc() & 0xFF00)) {
                    cpu.cycles += 2; //check if jump crossed a page boundary
                }
                else {
                    cpu.cycles++;
                }
            }
        }
    };

    protected final AbstractRunnable beq = new AbstractRunnable() {
        @Override
        public void run() {
            if ( cpu.isSet(Flag.ZERO ) ) {
                final int oldpc = cpu.pc();
                cpu.incPC( reladdr );
                if ((oldpc & 0xFF00) != (cpu.pc() & 0xFF00)) {
                    cpu.cycles += 2; //check if jump crossed a page boundary
                }
                else {
                    cpu.cycles++;
                }
            }
        }
    };

    protected final AbstractRunnable bit = new AbstractRunnable() {
        @Override
        public void run() {
            value = getvalue();
            int result = cpu.getAccumulator() & value;

            cpu.setFlag( CPU.Flag.ZERO , (result & 0xff) == 0);
            cpu.setFlag( Flag.NEGATIVE , (value & (1<<7)) != 0 );
            cpu.setFlag( Flag.OVERFLOW , (value & (1<<6)) != 0 );
        }
    };

    protected final AbstractRunnable bmi = new AbstractRunnable() {
        @Override
        public void run() {
            if ( cpu.isSet(Flag.NEGATIVE) )
            {
                final int oldpc = cpu.pc();
                cpu.incPC( reladdr );

                if ((oldpc & 0xFF00) != (cpu.pc() & 0xFF00))
                {
                    cpu.cycles += 2; //check if jump crossed a page boundary
                }
                else {
                    cpu.cycles++;
                }
            }
        }
    };

    protected final AbstractRunnable bne = new AbstractRunnable() {
        @Override
        public void run() {
            if ( cpu.isNotSet(Flag.ZERO) )
            {
                final int oldpc = cpu.pc();
                cpu.incPC( reladdr );
                if ((oldpc & 0xFF00) != (cpu.pc() & 0xFF00)) {
                    cpu.cycles += 2; //check if jump crossed a page boundary
                }
                else {
                    cpu.cycles++;
                }
            }
        }
    };

    protected final AbstractRunnable bpl = new AbstractRunnable() {
        @Override
        public void run() {
            if ( cpu.isNotSet(Flag.NEGATIVE) )
            {
                final int oldpc = cpu.pc();
                cpu.incPC( reladdr );
                if ((oldpc & 0xFF00) != (cpu.pc() & 0xFF00)) {
                    cpu.cycles += 2; //check if jump crossed a page boundary
                }
                else {
                    cpu.cycles++;
                }
            }
        }
    };

    protected final AbstractRunnable brk = new AbstractRunnable() {
        @Override
        public void run() {
            cpu.queueInterrupt( CPU.IRQType.BRK );
        }
    };

    protected final AbstractRunnable bvc = new AbstractRunnable() {
        @Override
        public void run() {
            if ( cpu.isNotSet( Flag.OVERFLOW ) )
            {
                final int oldpc = cpu.pc();
                cpu.incPC( reladdr );
                if ((oldpc & 0xFF00) != (cpu.pc() & 0xFF00)) {
                    cpu.cycles += 2; //check if jump crossed a page boundary
                }
                else {
                    cpu.cycles++;
                }
            }
        }
    };

    protected final AbstractRunnable bvs = new AbstractRunnable() {
        @Override
        public void run() {
            if ( cpu.isSet(Flag.OVERFLOW) )
            {
                final int oldpc = cpu.pc();
                cpu.incPC( reladdr );
                if ((oldpc & 0xFF00) != (cpu.pc() & 0xFF00))
                {
                    cpu.cycles += 2; //check if jump crossed a page boundary
                }
                else {
                    cpu.cycles++;
                }
            }
        }
    };

    protected final AbstractRunnable clc = new AbstractRunnable() {
        @Override
        public void run() {
            clearcarry();
        }
    };

    protected final AbstractRunnable cld = new AbstractRunnable() {
        @Override
        public void run() {
            cleardecimal();
        }
    };

    protected final AbstractRunnable cli = new AbstractRunnable() {
        @Override
        public void run() {
            clearinterrupt();
        }
    };

    protected final AbstractRunnable clv = new AbstractRunnable() {
        @Override
        public void run() {
            clearoverflow();
        }
    };

    protected final AbstractRunnable cmp = new AbstractRunnable() {
        @Override
        public void run() {
            penaltyop = true;
            value = getvalue();
            int result = cpu.getAccumulator() - value;

            if ( cpu.getAccumulator() >= (value & 0x00FF)) {
                setcarry();
            }
            else {
                clearcarry();
            }
            if ( result == 0) {
                setzero();
            }
            else {
                clearzero();
            }
            signcalc(result);
        }
    };

    protected final AbstractRunnable cpy = new AbstractRunnable() {
        @Override
        public void run() {
            value = getvalue();
            int result = cpu.getY() - value;

            if ( cpu.getY() >= (value & 0x00FF)) {
                setcarry();
            }
            else {
                clearcarry();
            }

            if ( result == 0 ) {
                setzero();
            }
            else {
                clearzero();
            }
            signcalc(result);
        }
    };

    protected final AbstractRunnable cpx= new AbstractRunnable() {
        @Override
        public void run() {
            value = getvalue();

            final int x = cpu.getX();
            int result = x - value;

            if (x >= (value & 0x00FF) ) {
                setcarry();
            }
            else {
                clearcarry();
            }
            if ( result == 0) {
                setzero();
            }
            else {
                clearzero();
            }
            signcalc(result);
        }
    };

    protected final AbstractRunnable dec = new AbstractRunnable() {
        @Override
        public void run() {
            value = getvaluereadwrite();
            int result = value - 1;

            zerocalc(result);
            signcalc(result);

            putvalue(result);
        }
    };

    protected final AbstractRunnable dex = new AbstractRunnable() {
        @Override
        public void run() {
            cpu.setX( cpu.getX() -1 );

            zerocalc( cpu.getX() );
            signcalc( cpu.getX() );
        }
    };

    protected final AbstractRunnable dey=new AbstractRunnable() {
        @Override
        public void run() {
            cpu.setY( cpu.getY() -1 );

            zerocalc( cpu.getY() );
            signcalc( cpu.getY() );
        }
    };

    protected final AbstractRunnable eor=new AbstractRunnable() {
        @Override
        public void run() {
            penaltyop = true;
            value = getvalue();
            int result = cpu.getAccumulator() ^ value;

            zerocalc(result);
            signcalc(result);

            saveaccum(result);
        }
    };

    protected final AbstractRunnable inc = new AbstractRunnable() {
        @Override
        public void run() {
            value = getvaluereadwrite();

            /* http://dustlayer.com/c64-coding-tutorials/2013/4/8/episode-2-3-did-i-interrupt-you
             * Now as coders are looking for optimization all the time somebody found out that the decrement command dec
             * can be used to do both operations with just one single command.
             * This works because dec is a so-called Read-Modify-Write command that writes back the original value during the modify cycle.
             */
            putvalue(value);

            int result = value + 1;

            zerocalc(result);
            signcalc(result);

            putvalue(result);
        }
    };

    protected final AbstractRunnable inx = new AbstractRunnable() {
        @Override
        public void run() {
            cpu.setX( cpu.getX()+1 );

            zerocalc( cpu.getX() );
            signcalc( cpu.getX() );
        }
    };

    protected final AbstractRunnable iny = new AbstractRunnable() {
        @Override
        public void run() {
            cpu.setY( cpu.getY()+1 );

            zerocalc( cpu.getY() );
            signcalc(  cpu.getY() );
        }
    };

    protected final AbstractRunnable jmp = new AbstractRunnable() {
        @Override
        public void run() {
            cpu.pc( ea );
        }
    };

    protected final AbstractRunnable jsr = new AbstractRunnable() {
        @Override
        public void run() {
            final int retAdr = cpu.pc()-1;
            push16( retAdr );
            cpu.pc( ea );
        }
    };

    protected final AbstractRunnable lda = new AbstractRunnable() {
        @Override
        public void run() {
            penaltyop = true;
            value = getvalue();

            final int a = value & 0x00FF;
            cpu.setAccumulator( a );

            zerocalc(a);
            signcalc(a);
        }
    };

    protected final AbstractRunnable ldx=new AbstractRunnable() {
        @Override
        public void run() {
            penaltyop = true;

            value = getvalue();
            final int x = value & 0x00FF;

            cpu.setX( x );

            zerocalc(x);
            signcalc(x);
        }
    };

    protected final AbstractRunnable ldy = new AbstractRunnable() {
        @Override
        public void run() {
            penaltyop = true;
            value = getvalue();
            final int y = (value & 0x00FF);
            cpu.setY(y);

            zerocalc(y);
            signcalc(y);
        }
    };

    protected final AbstractRunnable lsr = new AbstractRunnable() {
        @Override
        public void run() {
            value = getvaluereadwrite();
            int result = value >>> 1;

            if ( (value & 1) != 0 ) {
                setcarry();
            }
            else {
                clearcarry();
            }
            zerocalc(result);
            signcalc(result);

            putvalue(result);
        }
    };

    protected final AbstractRunnable nop = new AbstractRunnable() {
        @Override
        public void run() {
            // According to https://en.wikipedia.org/wiki/MOS_Technology_6502#Bugs_and_quirks
            // and https://retrocomputing.stackexchange.com/questions/145/why-does-6502-indexed-lda-take-an-extra-cycle-at-page-boundaries
            // the additional cycle penalty on page boundary crossings applies only to address calculations
            // Since NOP does none of them I don't think this can be a penalty operation.

            //        switch (opcode) {
            //            case 0x1C:
            //            case 0x3C:
            //            case 0x5C:
            //            case 0x7C:
            //            case 0xDC:
            //            case 0xFC:
            //                penaltyop = true;
            //                break;
            //        }
        }
    };

    protected final AbstractRunnable ora = new AbstractRunnable() {
        @Override
        public void run() {
            penaltyop = true;
            value = getvalue();
            int result = cpu.getAccumulator() | value;

            zerocalc(result);
            signcalc(result);

            saveaccum(result);
        }
    };

    protected final AbstractRunnable pha = new AbstractRunnable() {
        @Override
        public void run() {
            push8( cpu.getAccumulator() );
        }
    };

    protected final AbstractRunnable php = new AbstractRunnable() {
        @Override
        public void run() {
            final byte flags = cpu.getFlagBits();
            // quirk: The status bits pushed on the stack by PHP have the breakpoint bit set.
            // see http://nesdev.com/6502bugs.txt
            push8( CPU.Flag.BREAK.set( flags ) & 0xff );
        }
    };

    protected final AbstractRunnable pla = new AbstractRunnable() {
        @Override
        public void run() {
            final int a = cpu.pop( memory );
            cpu.setAccumulator(a);

            zerocalc(a);
            signcalc(a);
        }
    };

    protected final AbstractRunnable plp = new AbstractRunnable() {
        @Override
        public void run() {
            byte value = (byte) cpu.pop( memory ) ;
            value = CPU.Flag.BREAK.clear( value );
            cpu.setFlagBits( value );
        }
    };

    protected final AbstractRunnable rol = new AbstractRunnable() {
        @Override
        public void run() {
            value = getvaluereadwrite();
            int result = (value << 1) | ( cpu.isSet( Flag.CARRY) ? 1 : 0 );

            carrycalc(result);
            zerocalc(result);
            signcalc(result);

            putvalue(result);
        }
    };

    protected final AbstractRunnable ror = new AbstractRunnable() {
        @Override
        public void run() {
            value = getvaluereadwrite();
            int result;
            if (  cpu.isSet(Flag.CARRY ) ) {
                result = (value >>> 1) | 1 << 7;
            } else {
                result = (value >>> 1);
            }

            if ((value & 1) != 0 ) {
                setcarry();
            }
            else {
                clearcarry();
            }
            zerocalc(result);
            signcalc(result);

            putvalue(result);
        }
    };

    protected final AbstractRunnable rti = new AbstractRunnable() {
        @Override
        public void run() {
            final int status = cpu.pop(memory);
            cpu.setFlagBits( (byte) status );
            value = pull16();
            cpu.pc( value );
        }
    };

    protected final AbstractRunnable rts = new AbstractRunnable() {
        @Override
        public void run() {
            value = pull16();
            cpu.pc( value + 1 );
        }
    };

    protected final AbstractRunnable sec = new AbstractRunnable() {
        @Override
        public void run() {
            setcarry();
        }
    };

    protected final AbstractRunnable sed = new AbstractRunnable() {
        @Override
        public void run() {
            setdecimal();
        }
    };

    protected final AbstractRunnable sei = new AbstractRunnable() {
        @Override
        public void run() {
            setinterrupt();
        }
    };

    protected final AbstractRunnable sta = new AbstractRunnable() {
        @Override
        public void run() {
            putvalue( cpu.getAccumulator() );
        }
    };

    protected final AbstractRunnable stx = new AbstractRunnable() {
        @Override
        public void run() {
            putvalue( cpu.getX() );
        }
    };

    protected final AbstractRunnable sty = new AbstractRunnable() {
        @Override
        public void run() {
            putvalue( cpu.getY() );
        }
    };

    protected final AbstractRunnable tax = new AbstractRunnable() {
        @Override
        public void run() {
            cpu.setX( cpu.getAccumulator() );

            zerocalc( cpu.getX() );
            signcalc( cpu.getX() );
        }
    };

    protected final AbstractRunnable tay = new AbstractRunnable() {
        @Override
        public void run() {
            cpu.setY( cpu.getAccumulator() );

            zerocalc( cpu.getY() );
            signcalc( cpu.getY() );
        }
    };

    protected final AbstractRunnable tsx= new AbstractRunnable() {
        @Override
        public void run() {
            cpu.setX( cpu.getSP() );

            zerocalc( cpu.getX() );
            signcalc( cpu.getX() );
        }
    };

    protected final AbstractRunnable txa = new AbstractRunnable() {
        @Override
        public void run() {
            cpu.setAccumulator( cpu.getX() );

            zerocalc( cpu.getAccumulator() );
            signcalc( cpu.getAccumulator() );
        }
    };

    protected final AbstractRunnable txs = new AbstractRunnable() {
        @Override
        public void run() {
            cpu.setSP( cpu.getX() );
        }
    };

    protected final AbstractRunnable tya = new AbstractRunnable() {
        @Override
        public void run() {
            cpu.setAccumulator( cpu.getY() );

            zerocalc( cpu.getAccumulator() );
            signcalc( cpu.getAccumulator() );
        }
    };

    //undocumented instructions
    protected final AbstractRunnable lax = new AbstractRunnable() {
        @Override
        public void run() {
            lda.run();
            ldx.run();
        }
    };

    protected final AbstractRunnable skb = NOP;

    protected final AbstractRunnable skw = NOP;

    protected final AbstractRunnable axs = new AbstractRunnable() {
        @Override
        public void run() {
            sta.run();
            stx.run();
            putvalue(cpu.getAccumulator() & cpu.getX() );
            if (penaltyop && penaltyaddr) {
                cpu.cycles--;
            }
        }
    };

    protected final AbstractRunnable dcp = new AbstractRunnable() {
        @Override
        public void run() {
            dec.run();
            cmp.run();
            if (penaltyop && penaltyaddr) {
                cpu.cycles--;
            }
        }
    };

    protected final AbstractRunnable isb = new AbstractRunnable() {
        @Override
        public void run() {
            inc.run();
            sbcNew.run();
            if (penaltyop && penaltyaddr) {
                cpu.cycles--;
            }
        }
    };

    protected final AbstractRunnable slo = new AbstractRunnable() {
        @Override
        public void run() {
            asl.run();
            ora.run();
            if (penaltyop && penaltyaddr) {
                cpu.cycles--;
            }
        }
    };

    protected final AbstractRunnable rla = new AbstractRunnable() {
        @Override
        public void run() {
            rol.run();
            and.run();
            if (penaltyop && penaltyaddr) {
                cpu.cycles--;
            }
        }
    };

    protected final AbstractRunnable sre = new AbstractRunnable() {
        @Override
        public void run() {
            lsr.run();
            eor.run();
            if (penaltyop && penaltyaddr) {
                cpu.cycles--;
            }
        }
    };

    protected final AbstractRunnable hlt = new AbstractRunnable() {
        @Override
        public void run() {
            throw new HLTException();
        }
    };

    protected final AbstractRunnable rra = new AbstractRunnable() {
        @Override
        public void run() {
            ror.run();
            adcNew.run();
            if (penaltyop && penaltyaddr) {
                cpu.cycles--;
            }
        }
    };

    public void executeInstruction()
    {
        final int initialPC;

        if ( Constants.CPU_RECORD_BACKTRACE ) {
            initialPC = cpu.recordPC();
        } else {
            initialPC = cpu.pc();            
        }

        // TODO: Remove tape debug code

        //        if ( initialPC == 0xF92C) 
        //        {
        //            final int correction = memory.readByte( 0xb0 );
        //            System.out.println("Speed correction: "+correction+" (%"+StringUtils.leftPad( Integer.toBinaryString(correction ) , 8 , '0' )+")" );
        //        } 
        //        else if ( initialPC == 0xF9D5) 
        //        {
        //            final WavePeriod first = memory.readByte( 0xd7 ) == 0 ? WavePeriod.SHORT : WavePeriod.MEDIUM;
        //            final WavePeriod second = cpu.getX() == 0 ? WavePeriod.SHORT : WavePeriod.MEDIUM;
        //            System.out.println("Detected pair ("+first+","+second+")");
        //        } 
        //        else if ( initialPC == 0xFA10 ) 
        //        {
        //            System.out.println("Long pulse received");
        //        } 
        //        else if ( initialPC == 0xF98B ) 
        //        {
        //            System.out.println("parity error");
        //        } 
        //        else if ( initialPC == 0xFA8F2 ) 
        //        {
        //            System.out.println("Read error (parity)");
        //        } 
        //        else if ( initialPC == 0xFA57 ) 
        //        {
        //            System.out.println("Read error");
        //        } 
        //        else if ( initialPC == 0xF959 ) 
        //        {
        //            final int elapsedLo = memory.readByte( 0xb1 ) & 0xff;
        //            final int elapsedHi = cpu.getAccumulator() & 0xff;
        //            final int elapsedCycles = (elapsedHi << 8 | elapsedLo)*4;
        //            System.out.println("Elapsed cycles: "+elapsedCycles);
        //        }
        //        else if ( initialPC == 0xFA08 ) 
        //        {
        //            final int value = memory.readByte( 0xbf ); 
        //            final int bit = memory.readByte( 0xa3 ); 
        //            System.out.println("Byte is now: %"+StringUtils.leftPad( Integer.toBinaryString( value ) , 8 , "0" )+" , bit "+bit+" , ($"+Integer.toHexString(value) );
        //        }

        final int opcode = read6502( initialPC ) & 0xff;

        cpu.incPC();

        penaltyop = false;
        penaltyaddr = false;

        final AbstractRunnable adrMode = adrModeTable[opcode];
        isAccumulatorImmediate = adrMode == acc;
        adrMode.run();
        optable[opcode].run();

        if ( Constants.CPUIMPL_TRACK_INSTRUCTION_DURATION ) 
        {
            int tmp = TICK_TABLE[opcode];
            if (penaltyop && penaltyaddr)
            {
                tmp++;
            }
            cpu.lastInsDuration = tmp;
            cpu.cycles += tmp;
        } 
        else 
        {
            cpu.cycles += TICK_TABLE[opcode];

            if (penaltyop && penaltyaddr)
            {
                cpu.cycles++;
            }
        }
    }

    public interface ByteProvider
    {
        public int readByte();
        public int readWord();
        public int availableBytes();
        public void mark();
        public int getMark();
        public int currentOffset();
        public int toAbsoluteAddress(int relativeAddress);
    }

    private interface InstructionPrinter {
        public String getOperand(ByteProvider provider);
    }

    protected static final  InstructionPrinter pacc = provider -> "";
    protected static final  InstructionPrinter prel = provider ->
    {
        if ( provider.availableBytes() >= 1 )
        {
            byte offset = (byte) provider.readByte();
            return toWordString( provider.toAbsoluteAddress( offset+2 ) & 0xffff ); // +2 bytes for opcode + branch distance byte
        }
        return "< 1 byte missing >";
    };

    protected static final  InstructionPrinter pigb  = provider -> { toByteString( provider ); return ""; };
    protected static final  InstructionPrinter pigw  = provider -> { toWordString( provider ); return ""; };

    protected static final  InstructionPrinter pimm  = provider -> "#"+toByteString( provider );
    protected static final  InstructionPrinter pzp   = provider -> toByteString( provider );
    protected static final  InstructionPrinter pzpx  = provider -> toByteString( provider )+" , X";
    protected static final  InstructionPrinter pzpy  = provider -> toByteString( provider )+" , Y";
    protected static final  InstructionPrinter pabs  = provider -> toWordString( provider );
    protected static final  InstructionPrinter pabsx = provider -> toWordString( provider )+" , X";
    protected static final  InstructionPrinter pabsy = provider -> toWordString( provider )+" , Y";
    protected static final  InstructionPrinter pindx = provider -> "("+toByteString( provider )+" , X)";
    protected static final  InstructionPrinter pindy = provider -> "("+toByteString( provider )+") , Y";
    protected static final  InstructionPrinter pimp  = provider -> "";
    protected static final  InstructionPrinter pind  = provider -> "("+toWordString(provider)+")";

    public boolean disassemble(  StringBuilder operandBuffer,StringBuilder argsBuffer , ByteProvider byteProvider)
    {
        if ( byteProvider.availableBytes() > 0 )
        {
            final int op = byteProvider.readByte() & 0xff;
            final String mnemonic = MNEMONICS_TABLE[ op ];
            final String operand = getInstructionPrinter( op ).getOperand( byteProvider );

            operandBuffer.append( mnemonic );
            argsBuffer.append( operand );
        }
        return byteProvider.availableBytes()>0;
    }

    public boolean disassembleWithCycleTiming( StringBuilder operandBuffer,StringBuilder argsBuffer , ByteProvider byteProvider)
    {
        if ( byteProvider.availableBytes() > 0 )
        {
            final int op = byteProvider.readByte() & 0xff;
            final String mnemonic = MNEMONICS_TABLE[ op ];
            final String operand = getInstructionPrinter( op ).getOperand( byteProvider );
            operandBuffer.append("[").append( getMinimumCycles( op ) ).append("] ").append( mnemonic );
            argsBuffer.append( operand );
        }
        return byteProvider.availableBytes()>0;
    }	

    private InstructionPrinter getInstructionPrinter(int opcode)
    {
        final AbstractRunnable adrMode = adrModeTable[ opcode ];

        if ( adrMode == igb ) {
            return pigb;
        }
        if ( adrMode == igw ) {
            return pigw;
        }
        if ( adrMode == imm ) {
            return pimm;
        }
        if ( adrMode == rel ) {
            return prel;
        }
        if ( adrMode == acc ) {
            return pacc;
        }
        if ( adrMode == zp   ) {
            return pzp;
        }
        if ( adrMode == zpx  ) {
            return pzpx;
        }
        if ( adrMode == zpy  ) {
            return pzpy;
        }
        if ( adrMode == abso  )
        {
            return pabs;
        }
        if ( adrMode == absx ) {
            return pabsx;
        }
        if ( adrMode == absy ) {
            return pabsy;
        }
        if ( adrMode == indx ) {
            return pindx;
        }
        if ( adrMode == indy ) {
            return pindy;
        }
        if ( adrMode == imp  ) {
            return pimp;
        }
        if ( adrMode == ind || adrMode == indJmp  ) {
            return pind;
        }
        throw new RuntimeException("Unhandled addressing mode for opcode $"+HexDump.toHex( (byte) opcode ) );
    }

    protected static String toByteString(ByteProvider provider)
    {
        if ( provider.availableBytes() < 1 ) {
            return "< 1 byte missing>";
        }
        final int v = provider.readByte() & 0xff;
        String result = Integer.toHexString( v ).toLowerCase();
        while ( result.length() < 2 ) {
            result = "0"+result;
        }
        return "$"+result;
    }

    protected static String toWordString(int v)
    {
        String result = Integer.toHexString( v ).toLowerCase();
        while ( result.length() < 4 ) {
            result = "0"+result;
        }
        return "$"+result;
    }

    protected static String toWordString(ByteProvider provider)
    {
        if ( provider.availableBytes() < 2 ) {
            return "<"+(2 - provider.availableBytes())+" byte(s) missing>";
        }
        return toWordString( provider.readWord() & 0xffff );
    }

    protected boolean isAccumulatorImmediate;
    protected final AbstractRunnable[] adrModeTable = new AbstractRunnable[]
            {
                    /*        |  0  |  1  |  2  |  3  |  4  |  5  |  6  |  7  |  8  |  9  |  A  |  B  |  C  |  D  |  E  |  F  |     */
                    /* 0 */     imp, indx,  imp, indx,  igb,   zp,   zp,   zp,  imp,  imm,  acc,  imm, igw , abso, abso, abso, /* 0 */
                    /* 1 */     rel, indy,  imp, indy,  igb,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, igw , absx, absx, absx, /* 1 */
                    /* 2 */    abso, indx,  imp, indx,   zp,   zp,   zp,   zp,  imp,  imm,  acc,  imm, abso, abso, abso, abso, /* 2 */
                    /* 3 */     rel, indy,  imp, indy,  igb,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, igw , absx, absx, absx, /* 3 */
                    /* 4 */     imp, indx,  imp, indx,  igb,   zp,   zp,   zp,  imp,  imm,  acc,  imm, abso, abso, abso, abso, /* 4 */
                    /* 5 */     rel, indy,  imp, indy,  igb,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, igw , absx, absx, absx, /* 5 */
                    /* 6 */     imp, indx,  imp, indx,  igb,   zp,   zp,   zp,  imp,  imm,  acc,  imm,indJmp,abso, abso, abso, /* 6 */
                    /* 7 */     rel, indy,  imp, indy,  igb,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, igw , absx, absx, absx, /* 7 */
                    /* 8 */     igb, indx,  igb, indx,   zp,   zp,   zp,   zp,  imp,  imm,  imp,  imm, abso, abso, abso, abso, /* 8 */
                    /* 9 */     rel, indy,  imp, indy,  zpx,  zpx,  zpy,  zpy,  imp, absy,  imp, absy, absx, absx, absy, absy, /* 9 */
                    /* A */     imm, indx,  imm, indx,   zp,   zp,   zp,   zp,  imp,  imm,  imp,  imm, abso, abso, abso, abso, /* A */
                    /* B */     rel, indy,  imp, indy,  zpx,  zpx,  zpy,  zpy,  imp, absy,  imp, absy, absx, absx, absy, absy, /* B */
                    /* C */     imm, indx,  igb, indx,   zp,   zp,   zp,   zp,  imp,  imm,  imp,  imm, abso, abso, abso, abso, /* C */
                    /* D */     rel, indy,  imp, indy,  igb,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, igw , absx, absx, absx, /* D */
                    /* E */     imm, indx,  igb, indx,   zp,   zp,   zp,   zp,  imp,  imm,  imp,  imm, abso, abso, abso, abso, /* E */
                    /* F */     rel, indy,  imp, indy,  igb,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, igw , absx, absx, absx  /* F */
            };

    protected static final String[]  MNEMONICS_TABLE=new String[]
            {
                    /*          |  0  |    1  |    2  |    3  |    4  |    5  |    6  |    7  |    8  |    9  |    A  |    B  |    C  |    D  |    E  |    F  |      */
                    /* 0 */      "BRK",  "ORA",  "HLT",  "SLO",  "SKB",  "ORA",  "ASL",  "SLO",  "PHP",  "ORA",  "ASL",  "NOP",  "SKW",  "ORA",  "ASL",  "SLO", /* 0 */
                    /* 1 */      "BPL",  "ORA",  "HLT",  "SLO",  "SKB",  "ORA",  "ASL",  "SLO",  "CLC",  "ORA",  "NOP",  "SLO",  "SKW",  "ORA",  "ASL",  "SLO", /* 1 */
                    /* 2 */      "JSR",  "AND",  "HLT",  "RLA",  "BIT",  "AND",  "ROL",  "RLA",  "PLP",  "AND",  "ROL",  "NOP",  "BIT",  "AND",  "ROL",  "RLA", /* 2 */
                    /* 3 */      "BMI",  "AND",  "HLT",  "RLA",  "SKB",  "AND",  "ROL",  "RLA",  "SEC",  "AND",  "NOP",  "RLA",  "SKW",  "AND",  "ROL",  "RLA", /* 3 */
                    /* 4 */      "RTI",  "EOR",  "HLT",  "SRE",  "SKB",  "EOR",  "LSR",  "SRE",  "PHA",  "EOR",  "LSR",  "NOP",  "JMP",  "EOR",  "LSR",  "SRE", /* 4 */
                    /* 5 */      "BVC",  "EOR",  "HLT",  "SRE",  "SKB",  "EOR",  "LSR",  "SRE",  "CLI",  "EOR",  "NOP",  "SRE",  "SKW",  "EOR",  "LSR",  "SRE", /* 5 */
                    /* 6 */      "RTS",  "ADC",  "HLT",  "RRA",  "SKB",  "ADC",  "ROR",  "RRA",  "PLA",  "ADC",  "ROR",  "NOP",  "JMP",  "ADC",  "ROR",  "RRA", /* 6 */
                    /* 7 */      "BVS",  "ADC",  "HLT",  "RRA",  "SKB",  "ADC",  "ROR",  "RRA",  "SEI",  "ADC",  "NOP",  "RRA",  "SKW",  "ADC",  "ROR",  "RRA", /* 7 */
                    /* 8 */      "SKB",  "STA",  "SKB",  "AXS",  "STY",  "STA",  "STX",  "AXS",  "DEY",  "NOP",  "TXA",  "NOP",  "STY",  "STA",  "STX",  "AXS", /* 8 */
                    /* 9 */      "BCC",  "STA",  "HLT",  "NOP",  "STY",  "STA",  "STX",  "AXS",  "TYA",  "STA",  "TXS",  "NOP",  "NOP",  "STA",  "NOP",  "NOP", /* 9 */
                    /* A */      "LDY",  "LDA",  "LDX",  "LAX",  "LDY",  "LDA",  "LDX",  "LAX",  "TAY",  "LDA",  "TAX",  "NOP",  "LDY",  "LDA",  "LDX",  "LAX", /* A */
                    /* B */      "BCS",  "LDA",  "HLT",  "LAX",  "LDY",  "LDA",  "LDX",  "LAX",  "CLV",  "LDA",  "TSX",  "LAX",  "LDY",  "LDA",  "LDX",  "LAX", /* B */
                    /* C */      "CPY",  "CMP",  "SKB",  "DCP",  "CPY",  "CMP",  "DEC",  "DCP",  "INY",  "CMP",  "DEX",  "NOP",  "CPY",  "CMP",  "DEC",  "DCP", /* C */
                    /* D */      "BNE",  "CMP",  "HLT",  "DCP",  "SKB",  "CMP",  "DEC",  "DCP",  "CLD",  "CMP",  "NOP",  "DCP",  "SKW",  "CMP",  "DEC",  "DCP", /* D */
                    /* E */      "CPX",  "SBC",  "SKB",  "ISB",  "CPX",  "SBC",  "INC",  "ISB",  "INX",  "SBC",  "NOP",  "SBC",  "CPX",  "SBC",  "INC",  "ISB", /* E */
                    /* F */      "BEQ",  "SBC",  "HLT",  "ISB",  "SKB",  "SBC",  "INC",  "ISB",  "SED",  "SBC",  "NOP",  "ISB",  "SKW",  "SBC",  "INC",  "ISB"  /* F */
            };

    protected final AbstractRunnable[] optable= new AbstractRunnable[]
            {
                    /*        |  0  |  1  |  2  |  3  |  4  |  5  |  6  |  7  |  8  |  9  |  A  |  B  |  C  |  D  |  E  |  F  |      */
                    /* 0 */      brk,  ora,  hlt,  slo,  skb,  ora,  asl,  slo,  php,  ora,  asl,  nop,  skw,  ora,  asl,  slo, /* 0 */
                    /* 1 */      bpl,  ora,  hlt,  slo,  skb,  ora,  asl,  slo,  clc,  ora,  nop,  slo,  skw,  ora,  asl,  slo, /* 1 */
                    /* 2 */      jsr,  and,  hlt,  rla,  bit,  and,  rol,  rla,  plp,  and,  rol,  nop,  bit,  and,  rol,  rla, /* 2 */
                    /* 3 */      bmi,  and,  hlt,  rla,  skb,  and,  rol,  rla,  sec,  and,  nop,  rla,  skw,  and,  rol,  rla, /* 3 */
                    /* 4 */      rti,  eor,  hlt,  sre,  skb,  eor,  lsr,  sre,  pha,  eor,  lsr,  nop,  jmp,  eor,  lsr,  sre, /* 4 */
                    /* 5 */      bvc,  eor,  hlt,  sre,  skb,  eor,  lsr,  sre,  cli,  eor,  nop,  sre,  skw,  eor,  lsr,  sre, /* 5 */
                    /* 6 */      rts,  adcNew,  hlt,  rra,  skb,  adcNew,  ror,  rra,  pla,  adcNew,  ror,  nop,  jmp,  adcNew,  ror,  rra, /* 6 */
                    /* 7 */      bvs,  adcNew,  hlt,  rra,  skb,  adcNew,  ror,  rra,  sei,  adcNew,  nop,  rra,  skw,  adcNew,  ror,  rra, /* 7 */
                    /* 8 */      skb,  sta,  skb,  axs,  sty,  sta,  stx,  axs,  dey,  nop,  txa,  nop,  sty,  sta,  stx,  axs, /* 8 */
                    /* 9 */      bcc,  sta,  hlt,  nop,  sty,  sta,  stx,  axs,  tya,  sta,  txs,  nop,  nop,  sta,  nop,  nop, /* 9 */
                    /* A */      ldy,  lda,  ldx,  lax,  ldy,  lda,  ldx,  lax,  tay,  lda,  tax,  nop,  ldy,  lda,  ldx,  lax, /* A */
                    /* B */      bcs,  lda,  hlt,  lax,  ldy,  lda,  ldx,  lax,  clv,  lda,  tsx,  lax,  ldy,  lda,  ldx,  lax, /* B */
                    /* C */      cpy,  cmp,  skb,  dcp,  cpy,  cmp,  dec,  dcp,  iny,  cmp,  dex,  nop,  cpy,  cmp,  dec,  dcp, /* C */
                    /* D */      bne,  cmp,  hlt,  dcp,  skb,  cmp,  dec,  dcp,  cld,  cmp,  nop,  dcp,  skw,  cmp,  dec,  dcp, /* D */
                    /* E */      cpx,  sbcNew,  skb,  isb,  cpx,  sbcNew,  inc,  isb,  inx,  sbcNew,  nop,  sbcNew,  cpx,  sbcNew,  inc,  isb, /* E */
                    /* F */      beq,  sbcNew,  hlt,  isb,  skb,  sbcNew,  inc,  isb,  sed,  sbcNew,  nop,  isb,  skw,  sbcNew,  inc,  isb  /* F */
            };

    protected static final int[] TICK_TABLE= new int[]
            {
                    /*        |  0  |  1  |  2  |  3  |  4  |  5  |  6  |  7  |  8  |  9  |  A  |  B  |  C  |  D  |  E  |  F  |     */
                    /* 0 */      7,    6,    2,    8,    3,    3,    5,    5,    3,    2,    2,    2,    4,    4,    6,    6,  /* 0 */
                    /* 1 */      2,    5,    2,    8,    4,    4,    6,    6,    2,    4,    2,    7,    4,    4,    7,    7,  /* 1 */
                    /* 2 */      6,    6,    2,    8,    3,    3,    5,    5,    4,    2,    2,    2,    4,    4,    6,    6,  /* 2 */
                    /* 3 */      2,    5,    2,    8,    4,    3,    6,    6,    2,    4,    2,    7,    4,    4,    7,    7,  /* 3 */
                    /* 4 */      6,    6,    2,    8,    3,    3,    5,    5,    3,    2,    2,    2,    3,    4,    6,    6,  /* 4 */
                    /* 5 */      2,    5,    2,    8,    4,    4,    6,    6,    2,    4,    2,    7,    4,    4,    7,    7,  /* 5 */
                    /* 6 */      6,    6,    2,    8,    3,    3,    5,    5,    4,    2,    2,    2,    5,    4,    6,    6,  /* 6 */
                    /* 7 */      2,    5,    2,    8,    4,    4,    6,    6,    2,    4,    2,    7,    4,    4,    7,    7,  /* 7 */
                    /* 8 */      2,    6,    2,    6,    3,    3,    3,    3,    2,    2,    2,    2,    4,    4,    4,    4,  /* 8 */
                    /* 9 */      2,    6,    2,    6,    4,    4,    4,    4,    2,    5,    2,    5,    5,    5,    5,    5,  /* 9 */
                    /* A */      2,    6,    2,    6,    3,    3,    3,    3,    2,    2,    2,    2,    4,    4,    4,    4,  /* A */
                    /* B */      2,    5,    2,    5,    4,    4,    4,    4,    2,    4,    2,    4,    4,    4,    4,    4,  /* B */
                    /* C */      2,    6,    2,    8,    3,    3,    5,    5,    2,    2,    2,    2,    4,    4,    3,    6,  /* C */
                    /* D */      2,    5,    2,    8,    4,    4,    6,    6,    2,    4,    2,    7,    4,    4,    7,    7,  /* D */
                    /* E */      2,    6,    2,    8,    3,    3,    5,    5,    2,    2,    2,    2,    4,    4,    6,    6,  /* E */
                    /* F */      2,    5,    2,    8,    4,    4,    6,    6,    2,    4,    2,    7,    4,    4,    7,    7   /* F */
            };

    public static int getMinimumCycles(int opcode) {
        return TICK_TABLE[ opcode ];
    }

    public static void main(String[] args) {

        final Set<String> mnemonics = Arrays.stream( MNEMONICS_TABLE ).collect( Collectors.toSet() );
        final Map<String,Set<Integer> > opcodesByMnemonic = new HashMap<>();

        mnemonics.forEach( mnemonic ->
        {
            Set<Integer> opcodes = opcodesByMnemonic.get( mnemonic );
            if ( opcodes == null ) {
                opcodes = new HashSet<>();
                opcodesByMnemonic.put( mnemonic , opcodes );
            }
            for ( int i = 0 ; i < 256 ; i++ )
            {
                if ( MNEMONICS_TABLE[i].equals( mnemonic ) )
                {
                    opcodes.add( i );
                }
            }
        });

        final List<String> sorted = mnemonics.stream().sorted().collect( Collectors.toList() );
        sorted.forEach( mnemonic ->
        {
            opcodesByMnemonic.get( mnemonic ).stream().sorted().forEach( opcode ->
            {
                final String padded = "$"+HexDump.toHex( (byte) opcode.intValue() );
                System.out.println( padded+": "+mnemonic+" ("+TICK_TABLE[opcode.intValue()]+")");
            });
            System.out.println("----------------");
        });
    }
}