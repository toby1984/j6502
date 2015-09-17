package de.codesourcery.j6502.emulator;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.assembler.Assembler;
import de.codesourcery.j6502.assembler.AssemblerTest;
import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Parser;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.disassembler.Disassembler;
import de.codesourcery.j6502.disassembler.Disassembler.Line;
import de.codesourcery.j6502.disassembler.DisassemblerTest;
import de.codesourcery.j6502.emulator.CPU.Flag;
import de.codesourcery.j6502.emulator.EmulatorDriver.IEmulationListener;
import de.codesourcery.j6502.emulator.EmulatorDriver.Mode;
import de.codesourcery.j6502.emulator.IMemoryRegion.MemoryType;
import de.codesourcery.j6502.emulator.exceptions.HLTException;
import de.codesourcery.j6502.emulator.exceptions.InvalidOpcodeException;
import de.codesourcery.j6502.utils.HexDump;
import de.codesourcery.j6502.utils.SourceHelper;
import junit.framework.TestCase;

public class EmulatorTest  extends TestCase
{
    private static final String ILLEGAL_OPCODE = ".byte $02\n"; // HLT

    public static final int PRG_LOAD_ADDRESS = MemorySubsystem.Bank.BANK1.range.getStartAddress(); // = $1000

    public static final int PERFORMANCE_INSTRUCTIONS = 10000000;

    public static final boolean TEST_PERFORMANCE = false;

    public void testCPUPerformance() throws IOException 
    {
        System.out.println("Performance 'Other CPU'");

        final Memory memory = new Memory("test" , MemoryType.RAM , new AddressRange(0,65536));

        final Assembler asm = new Assembler();
        final CPU actualCPU = prepareTest( asm , memory );
        final CPUImpl cpu = new CPUImpl( actualCPU , memory );

        final Supplier<Long> toTest = () -> 
        {
            actualCPU.reset();
            actualCPU.pc( asm.getOrigin() );
            for ( int i = PERFORMANCE_INSTRUCTIONS ; i > 0 ; i-- ) {
                cpu.executeInstruction();
            }
            return actualCPU.cycles;
        };        

        benchmark( toTest , 200 , 100 );
    }

    private CPU prepareTest(Assembler a, Memory memory) throws IOException 
    {
        String source = "";
        try ( InputStream in = getClass().getResourceAsStream("/endless_test.asm") ) 
        {
            java.util.Scanner s = new java.util.Scanner(in).useDelimiter("\\A");
            source = s.hasNext() ? s.next() : "";
        }

        final byte[] executable = compile( a , source );
        System.out.println("Origin: "+a.getOrigin()+" , executable has "+executable.length+" bytes");
        final CPU cpu = new CPU(memory);
        cpu.pc( a.getOrigin() );
        memory.bulkWrite( a.getOrigin() , executable , 0 , executable.length );
        return cpu;
    }

    private void benchmark(Supplier<Long> r,int warmup,int iterations) {

        System.out.println("\n#\n# Warmup...\n#");
        for ( int i = 0 ; i < warmup ; i++ ) {
            r.get();
        }

        System.out.println("\n#\n# Actual tests...\n#");
        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;
        long  sum=0;

        for ( int i = 0 ; i < iterations ; i++ ) {

            long time = -System.currentTimeMillis();
            final long cycles = r.get();
            time += System.currentTimeMillis();
            float cyclesPerSecond = cycles / (time/1000f);
            float khz = cyclesPerSecond/1000000f;
            System.out.println( "Emulated CPU ran at "+khz+"Mhz");
            sum += time;
            if ( time < min ) {
                min = time;
            }
            if ( time > max ) {
                max = time;
            }
        }
        float avg = sum/(float) iterations;
        System.out.println("execution time (min/avg/max) in millseconds: "+min+"/"+avg+"/"+max);
    }

    public void testCMP() {
        /*
MODE           SYNTAX       HEX LEN TIM
Immediate     CMP #$44      $C9  2   2
Zero Page     CMP $44       $C5  2   3
Zero Page,X   CMP $44,X     $D5  2   4
Absolute      CMP $4400     $CD  3   4
Absolute,X    CMP $4400,X   $DD  3   4+
Absolute,Y    CMP $4400,Y   $D9  3   4+
Indirect,X    CMP ($44,X)   $C1  2   6
Indirect,Y    CMP ($44),Y   $D1  2   5+

   t = A - M
  P.N = t.7
  P.C = (A>=M) ? 1:0
  P.Z = (t==0) ? 1:0
         */

        execute("LDA #$01\n CMP #$02").assertFlags(CPU.Flag.NEGATIVE);
        execute("LDA #$02\n CMP #$01").assertFlags(CPU.Flag.CARRY);
        execute("LDA #$02\n CMP #$02").assertFlags(CPU.Flag.CARRY,CPU.Flag.ZERO);

        execute("LDA #$02\n STA $44\n LDA #$01\n CMP $44").assertFlags(CPU.Flag.NEGATIVE);
        execute("LDX #$0a\n LDA #$02\n STA $44,X\n LDA #$01\n CMP $44,X").assertFlags(CPU.Flag.NEGATIVE);
        execute("LDA #$02\n STA $4000\n LDA #$01\n CMP $4000").assertFlags(CPU.Flag.NEGATIVE);
        execute("LDX #$0a\n LDA #$02\n STA $4000,X\n LDA #$01\n CMP $4000,X").assertFlags(CPU.Flag.NEGATIVE);
        execute("LDY #$0a\n LDA #$02\n STA $4000,Y\n LDA #$01\n CMP $4000,Y").assertFlags(CPU.Flag.NEGATIVE);
        execute("LDX #$0a\n LDA #$02\n STA ($44,X)\n LDA #$01\n CMP ($44,X)").assertFlags(CPU.Flag.NEGATIVE);
        execute("LDY #$0a\n LDA #$02\n STA ($44),Y\n LDA #$01\n CMP ($44),Y").assertFlags(CPU.Flag.NEGATIVE);
    }

    public void testSBC() {

        // SEC ; 0 - 1 = -1, returns V = 0
        // LDA #$00
        // SBC #$01
        execute("SEC\n LDA #0\n SBC #1").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE); // carry = 1 => NO BORROW
        /*
Inputs	       Outputs		    Example
M7 	N7 	C6     C7 	B	S7 	V	Borrow / Overflow	                    Hex	Unsigned	             Signed
0	1	0       0   1	0	0	Unsigned borrow but no signed overflow	0x50-0xf0=0x60	80-240=96	 80--16=96
0	1	1       0   1	1	1	Unsigned borrow and signed overflow	    0x50-0xb0=0xa0	80-176=160	 80--80=-96
0	0	0       0   1	1	0	Unsigned borrow but no signed overflow	0x50-0x70=0xe0	80-112=224	 80-112=-32
0	0	1       1   0	0	0	No unsigned borrow or signed overflow	0x50-0x30=0x120	80-48=32	 80-48=32
1	1	0       0   1	1	0	Unsigned borrow but no signed overflow	0xd0-0xf0=0xe0	208-240=224	-48--16=-32
1	1	1       1   0	0	0	No unsigned borrow or unsigned overflow	0xd0-0xb0=0x120	208-176=32	-48--80=32
1	0	0       1   0	0	1	No unsigned borrow but signed overflow	0xd0-0x70=0x160	208-112=96	-48-112=96

1	0	1       1   0	1	0	No unsigned borrow or signed overflow	0xd0-0x30=0x1a0	208-48=160	-48-48=-96
         */
        // carry clear => borrow    => CLC
        // carry set   => no borrow => SEC
        execute("SEC\n LDA #80\n SBC #240").assertA( 96 ).assertFlags(); // carry = 1 => NO BORROW
        execute("SEC\n LDA #80\n SBC #176").assertA( 160 ).assertFlags(CPU.Flag.NEGATIVE,CPU.Flag.OVERFLOW); // carry = 0 => BORROW
        execute("SEC\n LDA #80\n SBC #112").assertA( 224 ).assertFlags(CPU.Flag.NEGATIVE); // carry = 0 => BORROW
        execute("SEC\n LDA #80\n SBC #48").assertA( 32 ).assertFlags(CPU.Flag.CARRY); // carry = 0 => BORROW

        execute("SEC\n LDA #208\n SBC #240").assertA( 224 ).assertFlags(CPU.Flag.NEGATIVE); // carry = 0 => BORROW
        execute("SEC\n LDA #208\n SBC #176").assertA( 32 ).assertFlags(CPU.Flag.CARRY); // carry = 0 => BORROW

        execute("SEC\n LDA #208\n SBC #112").assertA( 96 ).assertFlags(CPU.Flag.OVERFLOW, CPU.Flag.CARRY ); // carry = 0 => BORROW
        execute("SEC\n LDA #208\n SBC #48").assertA( 160 ).assertFlags(CPU.Flag.NEGATIVE, CPU.Flag.CARRY ); // carry = 0 => BORROW
    }

    public void testADC3() {
        execute("CLC\n LDA #0\n ADC #$f0\n").assertA( 0xf0 ).assertFlags(CPU.Flag.NEGATIVE);
    }

    public void testADC2() {
        /*
Inputs	        Outputs		    Example
M7 	N7 	C6 	    C7 	S7 	V	Carry / Overflow	                    Hex	            Unsigned	Signed
0	0	0	    0	0	0	No unsigned carry or signed overflow	0x50+0x10=0x60	80+16=96	80+16=96
0	0	1	    0	1	1	No unsigned carry but signed overflow	0x50+0x50=0xa0	80+80=160	80+80=-96
0	1	0	    0	1	0	No unsigned carry or signed overflow	0x50+0x90=0xe0	80+144=224	80+-112=-32
0	1	1	    1	0	0	Unsigned carry, but no signed overflow	0x50+0xd0=0x120	80+208=288	80+-48=32
1	0	0	    0	1	0	No unsigned carry or signed overflow	0xd0+0x10=0xe0	208+16=224	-48+16=-32
1	0	1	    1	0	0	Unsigned carry but no unsigned overflow	0xd0+0x50=0x120	208+80=288	-48+80=32

1	1	0	    1	0	1	Unsigned carry and signed overflow	    0xd0+0x90=0x160	208+144=352	-48+-112=96

1	1	1	    1	1	0	Unsigned carry, but no signed overflow	0xd0+0xd0=0x1a0	208+208=416	-48+-48=-96
         */
        execute("CLC\n LDA #80\n ADC #16\n").assertA( 96 ).assertFlags();
        execute("CLC\n LDA #80\n ADC #80\n").assertA( 160 ).assertFlags(CPU.Flag.OVERFLOW,CPU.Flag.NEGATIVE);
        execute("CLC\n LDA #80\n ADC #144\n").assertA( 224 ).assertFlags(CPU.Flag.NEGATIVE);
        execute("CLC\n LDA #80\n ADC #208\n").assertA( 288 ).assertFlags(CPU.Flag.CARRY);

        execute("CLC\n LDA #208\n ADC #16\n").assertA( 224 ).assertFlags(CPU.Flag.NEGATIVE);
        execute("CLC\n LDA #208\n ADC #80\n").assertA( 288 ).assertFlags(CPU.Flag.CARRY);

        execute("CLC\n LDA #208\n ADC #144\n").assertA( 352 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.OVERFLOW);
        execute("CLC\n LDA #208\n ADC #208\n").assertA( 416 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.NEGATIVE);
    }

    public void testSKW() {
        execute("SKW\n HLT\n HLT\n LDA #$0a\n").assertA( 10 ).assertFlags();
    }

    public void testHLT()
    {
        try {
            execute("HLT\n").rethrowHltException(true).maybeExecute();
            fail("Should've thrown exception");
        } catch(HLTException e) {
            // ok
        }
    }

    public void testADCDecimalMode()
    {
        /// TAKEN FROM http://www.6502.org/tutorials/decimal_mode.html

        /*
CLD      ; Binary mode (binary addition: 88 + 70 + 1 = 159)
SEC      ; Note: carry is set, not clear!
LDA #$58 ; 88
ADC #$46 ; 70 (after this instruction, C = 0, A = $9F = 159)         
         */
        execute("CLD\n SEC\n LDA #$58\n ADC #$46\n").assertA( 0x9f ).assertFlags(CPU.Flag.NEGATIVE,CPU.Flag.OVERFLOW);

        /*
SED      ; Decimal mode (BCD addition: 58 + 46 + 1 = 105)
SEC      ; Note: carry is set, not clear!
LDA #$58
ADC #$46 ; After this instruction, C = 1, A = $05         
         */
        execute("SED\n SEC\n LDA #$58\n ADC #$46\n").assertA( 0x05 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.OVERFLOW,CPU.Flag.DECIMAL_MODE);

        /*
SED      ; Decimal mode (BCD addition: 12 + 34 = 46)
CLC
LDA #$12
ADC #$34 ; After this instruction, C = 0, A = $46         
         */
        execute("SED\n CLC\n LDA #$12\n ADC #$34\n").assertA( 0x46 ).assertFlags(CPU.Flag.DECIMAL_MODE);

        /*
SED      ; Decimal mode (BCD addition: 15 + 26 = 41)
CLC
LDA #$15
ADC #$26 ; After this instruction, C = 0, A = $41         
         */
        execute("SED\n CLC\n LDA #$15\n ADC #$26\n").assertA( 0x41 ).assertFlags(CPU.Flag.DECIMAL_MODE);

        /*
SED      ; Decimal mode (BCD addition: 81 + 92 = 173)
CLC
LDA #$81
ADC #$92 ; After this instruction, C = 1, A = $73         
         */
        execute("SED\n CLC\n LDA #$81\n ADC #$92\n").assertA( 0x73 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.DECIMAL_MODE,CPU.Flag.OVERFLOW);

        /*
SED      ; Decimal mode (BCD subtraction: 46 - 12 = 34)
SEC
LDA #$46
SBC #$12 ; After this instruction, C = 1, A = $34)         
         */
        execute("SED\n CLC\n LDA #$46\n SBC #$12\n").assertA( 0x34 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.DECIMAL_MODE,CPU.Flag.OVERFLOW);

        /*
SED      ; Decimal mode (BCD subtraction: 40 - 13 = 27)
SEC
LDA #$40
SBC #$13 ; After this instruction, C = 1, A = $27)         
         */
        
        /*
SED      ; Decimal mode (BCD subtraction: 32 - 2 - 1 = 29)
CLC      ; Note: carry is clear, not set!
LDA #$32
SBC #$02 ; After this instruction, C = 1, A = $29)     
         */
        execute("SED\n CLC\n LDA #$32\n SBC #$02\n").assertA( 0x29 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.DECIMAL_MODE,CPU.Flag.OVERFLOW);
    }

    public void testADC() {
        // b4( %10110100 ) = 180
        execute("SEC\n LDA #$03\n ADC #$03").assertA( 0x07 ).assertFlags();

        execute("LDA #$01\n CLC\n ADC #$01").assertA( 0x02 ).assertFlags();
        execute("LDA #$ff\n CLC\n ADC #$01").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO,CPU.Flag.CARRY);
        execute("LDA #$7f\n CLC\n ADC #$01").assertA( 0x80 ).assertFlags(CPU.Flag.OVERFLOW , CPU.Flag.NEGATIVE );

        execute("LDA #$01\n STA $44\n CLC\n LDA #$01\n ADC $44").assertA( 0x02 ).assertFlags();
        execute("LDX #$10\n LDA #$01\n STA $44,X\n CLC\n LDA #$01\n ADC $44,X").assertA( 0x02 ).assertFlags();
        execute("LDA #$01\n STA $4000\n CLC\n LDA #$01\n ADC $4000").assertA( 0x02 ).assertFlags();

        execute("LDX #$10\n LDA #$01 STA $4000,X CLC\n LDA #$01\n ADC $4000,X").assertA( 0x02 ).assertFlags();
        execute("LDY #$10\n LDA #$01 STA $4000,Y CLC\n LDA #$01\n ADC $4000,Y").assertA( 0x02 ).assertFlags();

        execute("LDX #$10\n LDA #$01 STA ($44,X) CLC\n LDA #$01\n ADC ($44,X)").assertA( 0x02 ).assertFlags();
        execute("LDY #$10\n LDA #$01 STA ($44),Y CLC\n LDA #$01\n ADC ($44),Y").assertA( 0x02 ).assertFlags();
    }

    public void testCPX()
    {
        execute("LDX #$01\n CPX #$02").assertFlags(CPU.Flag.NEGATIVE);
        execute("LDX #$02\n CPX #$01").assertFlags(CPU.Flag.CARRY);
        execute("LDX #$02\n CPX #$02").assertFlags(CPU.Flag.CARRY,CPU.Flag.ZERO);

        execute("LDA #$02\n STA $44\n LDX #$01\n CPX $44").assertFlags(CPU.Flag.NEGATIVE);
        execute("LDA #$02\n STA $4000\n LDX #$01\n CPX $4000").assertFlags(CPU.Flag.NEGATIVE);
    }

    public void testCPY()
    {
        execute("LDY #$01\n CPY #$02").assertFlags(CPU.Flag.NEGATIVE);
        execute("LDY #$02\n CPY #$01").assertFlags(CPU.Flag.CARRY);
        execute("LDY #$02\n CPY #$02").assertFlags(CPU.Flag.CARRY,CPU.Flag.ZERO);

        execute("LDA #$02\n STA $44\n LDY #$01\n CPY $44").assertFlags(CPU.Flag.NEGATIVE);
        execute("LDA #$02\n STA $4000\n LDY #$01\n CPY $4000").assertFlags(CPU.Flag.NEGATIVE);
    }

    public void testLDA() {
        /*
MODE           SYNTAX       HEX LEN TIM
Immediate     LDA #$44      $A9  2   2
Zero Page     LDA $44       $A5  2   3
Zero Page,X   LDA $44,X     $B5  2   4
Absolute      LDA $4400     $AD  3   4
Absolute,X    LDA $4400,X   $BD  3   4+
Absolute,Y    LDA $4400,Y   $B9  3   4+
Indirect,X    LDA ($44,X)   $A1  2   6
Indirect,Y    LDA ($44),Y   $B1  2   5+
         */
        execute("LDA #$00").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);
        execute("LDA #$ff").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
        execute("LDA #$12").assertA( 0x12 ).assertFlags();
        execute("LDA $44").writeByte( 0x44 , 23 ).assertA( 23 ).assertFlags();
        execute("LDA 40 , X ").setX(10).writeByte( 50 , 0x12 ).assertA( 0x12 ).assertFlags();
        execute("LDA $4000 , X ").setX(0x10).writeByte( 0x4010 , 0x12 ).assertA( 0x12 ).assertFlags();
        execute("LDA $4000 , Y ").setY(0x10).writeByte( 0x4010 , 0x12 ).assertA( 0x12 ).assertFlags();
        execute("LDA (40,X)").setX(10).writeWord( 50 , 0x1234 ).writeByte( 0x1234 , 0x12 ).assertA( 0x12 ).assertFlags();
        execute("LDA (40),Y").setY(0x10).writeWord( 40 , 0x1200 ).writeByte( 0x1210 , 0x12 ).assertA( 0x12 ).assertFlags();
    }

    public void testAND() {

        // Zero Page     AND $44       $25  2   2
        execute("LDA #$ff\n STA $44\n LDA #$ff\n AND $44").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Immediate     AND #$44      $29  2   2
        execute("LDA #$ff\n AND #$ff").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
        execute("LDA #$ff\n AND #$00").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);
        execute("LDA #$01\n AND #$01").assertA( 0x01 ).assertFlags();

        // Zero Page,X   AND $44,X     $35  2   3
        execute("LDX #$05\n LDA #$ff\n STA $44,X\n LDA #$ff\n AND $44,X").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Absolute      AND $4400     $2D  3   4
        execute("LDA #$ff\n STA $1234\n LDA #$ff\n AND $1234").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Absolute,X    AND $4400,X   $3D  3   4+
        execute("LDX #$05\n LDA #$ff\n STA $1234,X\n LDA #$ff\n AND $1234,X").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Absolute,Y    AND $4400,Y   $39  3   4+
        execute("LDY #$05\n LDA #$ff\n STA $1234,Y\n LDA #$ff\n AND $1234,Y").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Indirect,X    AND ($44,X)   $21  2   6
        execute("LDA #<$1234\n STA $0a+5\n LDA #>$1234\n STA $0a+6\n LDX #$05\n LDA #$0f\n STA ($0a,X)\n LDA #$ff\n AND ($0a,X)").assertA( 0x0f ).assertFlags();

        // Indirect,Y    AND ($44),Y   $31  2   5+
        execute("LDX #$05\n LDA #$ff\n STA ($44),Y\n LDA #$ff\n AND ($44),Y").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
    }

    public void testROL() {
        /*
 Affects Flags: S Z C

MODE           SYNTAX       HEX LEN TIM
Accumulator   ROL A         $2A  1   2
Zero Page     ROL $44       $26  2   5
Zero Page,X   ROL $44,X     $36  2   6
Absolute      ROL $4400     $2E  3   6
Absolute,X    ROL $4400,X   $3E  3   7

ROL shifts all bits left one position. The Carry is shifted into bit 0 and the original bit 7 is shifted into the Carry.
         */
        final byte pattern1         = 0b0101_0101;
        final byte pattern2 = (byte) 0b1010_1010;
        execute("CLC\n LDA #"+hex(pattern1)+"\n ROL").assertA( pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
        execute("SEC\n LDA #"+hex(pattern2)+"\n ROL").assertA( pattern1 ).assertFlags(CPU.Flag.CARRY);

        execute("CLC\n LDA #"+hex((byte) 0b1000_0000 )+"\n ROL").assertA( 0 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.ZERO);
        execute("CLC\n LDA #"+hex((byte) 0b0100_0000 )+"\n ROL").assertA( 0x80 ).assertFlags(CPU.Flag.NEGATIVE);

        execute("CLC\n LDA #"+hex(pattern1)+"\n STA $44\n ROL $44").assertMemoryContains( 0x44 , pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
        execute("CLC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $40,X\n ROL $40,X").assertMemoryContains( 0x50, pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);

        execute("CLC\n LDA #"+hex(pattern1)+"\n STA $4000\n ROL $4000").assertMemoryContains( 0x4000 , pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
        execute("CLC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $4000,X\n ROL $4000,X").assertMemoryContains( 0x4010, pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
    }

    public void testASL() {
        /*
 Affects Flags: S Z C

MODE           SYNTAX       HEX LEN TIM
Accumulator   ASL A         $2A  1   2
Zero Page     ASL $44       $26  2   5
Zero Page,X   ASL $44,X     $36  2   6
Absolute      ASL $4400     $2E  3   6
Absolute,X    ASL $4400,X   $3E  3   7

ASL shifts all bits left one position. The Carry is shifted into bit 0 and the original bit 7 is shifted into the Carry.
         */
        final byte pattern1         = 0b0101_0101;
        final byte pattern2 = (byte)  0b1010_1010;
        final byte pattern2s = (byte) 0b0101_0100;
        execute("CLC\n LDA #"+hex(pattern1)+"\n ASL").assertA( pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
        execute("SEC\n LDA #"+hex(pattern2)+"\n ASL").assertA( pattern2s ).assertFlags(CPU.Flag.CARRY);

        execute("CLC\n LDA #"+hex((byte) 0b1000_0000 )+"\n ASL").assertA( 0 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.ZERO);
        execute("CLC\n LDA #"+hex((byte) 0b0100_0000 )+"\n ASL").assertA( 0x80 ).assertFlags(CPU.Flag.NEGATIVE);

        execute("CLC\n LDA #"+hex(pattern1)+"\n STA $44\n ASL $44").assertMemoryContains( 0x44 , pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
        execute("CLC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $40,X\n ASL $40,X").assertMemoryContains( 0x50, pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);

        execute("CLC\n LDA #"+hex(pattern1)+"\n STA $4000\n ASL $4000").assertMemoryContains( 0x4000 , pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
        execute("CLC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $4000,X\n ASL $4000,X").assertMemoryContains( 0x4010, pattern2 ).assertFlagsNotSet(CPU.Flag.CARRY);
    }

    public void testShift()
    {
        // $4b = 1001011
        String source = "LDA #$4B\n "
                + "LSR\n " // 0100101 , Carry = 1
                + "ASL"; // 1001010 , Carry = 0
        execute(source).assertA( 0b1001010 ).assertFlagsNotSet(CPU.Flag.CARRY);
    }

    public void testShift2() {
        final String source="LDA #$b4\n"+ // 10110100
                "STA $70  \n"+
                "LDX $70  \n"+
                "ORA #$03 \n"+ // 10110111
                "STA $0C,X\n"+
                "ROL $C0  \n"+
                "ROR $C0  \n"+
                "ROR $C0  \n"+
                "LDA $0C,X";

        final int[] pc = { 0 };
        execute(source)
        .beforeEachStep( emulator -> {
            pc[0] = emulator.getCPU().pc();
        })
        .afterEachStep(emulator ->
        {
            System.out.println("------------");
            final int end = emulator.getCPU().pc();
            Disassembler dis = new Disassembler();
            dis.disassemble( emulator.getMemory() , pc[0] , end - pc[0] , line -> System.out.println(line) );
            final byte value = (byte) emulator.getMemory().readByte( 0xc0 );
            System.out.println( emulator.getCPU() );
            System.out.println("$c0 = $"+HexDump.byteToString( value )+" %"+HexDump.toBinaryString( value) );
        })
        .assertMemoryContains( 0xc0 ,  0b01011011 ).assertFlags(CPU.Flag.CARRY);
    }

    public void testLSR() {
        /*
		Affects Flags: S Z C

		MODE           SYNTAX       HEX LEN TIM
		Accumulator   LSR A         $4A  1   2
		Zero Page     LSR $44       $46  2   5
		Zero Page,X   LSR $44,X     $56  2   6
		Absolute      LSR $4400     $4E  3   6
		Absolute,X    LSR $4400,X   $5E  3   7

		LSR shifts all bits right one position. 0 is shifted into bit 7 and the original bit 0 is shifted into the Carry.
         */

        final byte pattern1                = 0b01010101;
        final byte pattern1_shifted = (byte) 0b00101010;

        final byte pattern2         = (byte) 0b10101010;

        execute("CLC\n LDA #"+hex(pattern2)+"\n LSR").assertA( pattern1 ).assertFlagsNotSet(CPU.Flag.CARRY);
        execute("SEC\n LDA #"+hex(pattern1)+"\n LSR").assertA( pattern1_shifted ).assertFlags(CPU.Flag.CARRY);

        execute("CLC\n LDA #"+hex((byte) 0b0000_0001 )+"\n LSR").assertA( 0 ).assertFlags(CPU.Flag.ZERO,CPU.Flag.CARRY);

        execute("SEC\n LDA #"+hex(pattern1)+"\n STA $44\n LSR $44").assertMemoryContains( 0x44 , pattern1_shifted ).assertFlags(CPU.Flag.CARRY);
        execute("SEC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $40,X\n LSR $40,X").assertMemoryContains( 0x50, pattern1_shifted ).assertFlags(CPU.Flag.CARRY);

        execute("SEC\n LDA #"+hex(pattern1)+"\n STA $4000\n LSR $4000").assertMemoryContains( 0x4000 , pattern1_shifted ).assertFlags(CPU.Flag.CARRY);
        execute("SEC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $4000,X\n LSR $4000,X").assertMemoryContains( 0x4010, pattern1_shifted ).assertFlags(CPU.Flag.CARRY);
    }

    public void testROR() {
        /*
 Affects Flags: S Z C

MODE           SYNTAX       HEX LEN TIM
Accumulator   ROR A         $6A  1   2
Zero Page     ROR $44       $66  2   5
Zero Page,X   ROR $44,X     $76  2   6
Absolute      ROR $4400     $6E  3   6
Absolute,X    ROR $4400,X   $7E  3   7

ROR shifts all bits right one position. The Carry is shifted into bit 7 and the original bit 0 is shifted into the Carry.
         */
        final byte pattern1        = 0b0101_0101;
        final byte pattern2 = (byte) 0b1010_1010;
        execute("CLC\n LDA #"+hex(pattern2)+"\n ROR").assertA( pattern1 ).assertFlagsNotSet(CPU.Flag.CARRY);
        execute("SEC\n LDA #"+hex(pattern1)+"\n ROR").assertA( pattern2 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.NEGATIVE);

        execute("CLC\n LDA #"+hex((byte) 0b0000_0001 )+"\n ROR").assertA( 0 ).assertFlags(CPU.Flag.ZERO,CPU.Flag.CARRY);
        execute("SEC\n LDA #"+hex((byte) 0b0000_0000 )+"\n ROR").assertA( 0b1000_0000 ).assertFlags(CPU.Flag.NEGATIVE);

        execute("SEC\n LDA #"+hex(pattern1)+"\n STA $44\n ROR $44").assertMemoryContains( 0x44 , pattern2 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.NEGATIVE);
        execute("SEC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $40,X\n ROR $40,X").assertMemoryContains( 0x50, pattern2 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.NEGATIVE);

        execute("SEC\n LDA #"+hex(pattern1)+"\n STA $4000\n ROR $4000").assertMemoryContains( 0x4000 , pattern2 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.NEGATIVE);
        execute("SEC\n LDX #$10\n LDA #"+hex(pattern1)+"\n STA $4000,X\n ROR $4000,X").assertMemoryContains( 0x4010, pattern2 ).assertFlags(CPU.Flag.CARRY,CPU.Flag.NEGATIVE);
    }

    public void testEOR()
    {
        // Zero Page     AND $44       $25  2   2
        execute("LDA #$ff\n STA $44\n LDA #$ff\n EOR $44").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);

        // Immediate     EOR #$44      $29  2   2
        execute("LDA #$ff\n EOR #$ff").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);
        execute("LDA #$ff\n EOR #$00").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
        execute("LDA #$00\n EOR #$ff").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
        execute("LDA #$00\n EOR #$00").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);

        // Zero Page,X   EOR $44,X     $35  2   3
        execute("LDX #$05\n LDA #$ff\n STA $44,X\n LDA #$00\n EOR $44,X").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Absolute      EOR $4400     $2D  3   4
        execute("LDA #$ff\n STA $1234\n LDA #$00\n EOR $1234").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Absolute,X    EOR $4400,X   $3D  3   4+
        execute("LDX #$05\n LDA #$ff\n STA $1234,X\n LDA #$00\n EOR $1234,X").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Absolute,Y    EOR $4400,Y   $39  3   4+
        execute("LDY #$05\n LDA #$ff\n STA $1234,Y\n LDA #$00\n EOR $1234,Y").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Indirect,X    EOR ($44,X)   $21  2   6
        execute("LDA #<$1234\n STA $0a+5\n LDA #>$1234\n STA $0a+6\n LDX #$05\n LDA #$ff\n STA ($0a,X)\n LDA #$00\n EOR ($0a,X)").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Indirect,Y    EOR ($44),Y   $31  2   5+
        execute("LDX #$05\n LDA #$ff\n STA ($44),Y\n LDA #$00\n EOR ($44),Y").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
    }

    public void testORA() {

        // Indirect,X    ORA ($44,X)   $21  2   6

        /*
         * Executing $58 @ $0fff
         * Executing $a9 @ $1000 ; LDA #
         * Executing $85 @ $1002 ; STA ZP
         * Executing $a9 @ $1004 ; LDA #
         * Executing $85 @ $1006 ; STA ZP
         * Executing $a2 @ $1008 ; LDX #
         * Executing $a9 @ $100a ; LDA #
         * Executing $81 @ $100c ; STA (,X)
         */
        // Absolute,X    ORA $4400,X   $3D  3   4+
        execute("LDX #$05\n LDA #$ff\n STA $1234,X\n LDA #$00\n ORA $1234,X").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        execute("LDA #<$1234\n STA $0a+5\n LDA #>$1234\n STA $0a+6\n LDX #$05\n LDA #$f0\n STA ($0a,X)\n LDA #$0f\n ORA ($0a,X)").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Zero Page     ORA $44       $25  2   2
        execute("LDA #$ff\n STA $44\n LDA #$00\n ORA $44").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Immediate     ORA #$44      $29  2   2
        execute("LDA #$00\n ORA #$ff").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
        execute("LDA #$00\n ORA #$00").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);
        execute("LDA #$00\n ORA #$01").assertA( 0x01 ).assertFlags();

        // Zero Page,X   ORA $44,X     $35  2   3
        execute("LDX #$05\n LDA #$00\n STA $44,X\n LDA #$ff\n ORA $44,X").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Absolute      ORA $4400     $2D  3   4
        execute("LDA #$ff\n STA $1234\n LDA #$00\n ORA $1234").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Absolute,Y    ORA $4400,Y   $39  3   4+
        execute("LDY #$05\n LDA #$ff\n STA $1234,Y\n LDA #$00\n ORA $1234,Y").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // Indirect,Y    ORA ($44),Y   $31  2   5+
        execute("LDX #$05\n LDA #$ff\n STA ($44),Y\n LDA #$00\n ORA ($44),Y").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
    }

    public void testBRK() {

        execute("LDX #$00\n BRK\n HLT\n LDX #$12",false)
        .writeWord( CPU.BRK_VECTOR_LOCATION , PRG_LOAD_ADDRESS+4 )
        .assertFlagsNotSet(CPU.Flag.BREAK)
        .assertX( 0x12 );
    }

    public void testSanity2() throws Exception {

        final String source = loadTestProgram( "/overflow_test.asm");

        final AST ast;
        try {
            ast = new Parser( new Lexer( new Scanner( source ) ) ).parse();
        }
        catch(Exception e)
        {
            DisassemblerTest.maybePrintError( source , e );
            throw e;
        }

        final SourceHelper helper = new SourceHelper( source );
        final Assembler a = new Assembler();
        final byte[] binary = a.assemble( ast , helper );
        final int origin = a.getOrigin();

        final IMemoryProvider provider = new IMemoryProvider() {

            @Override
            public void loadInto(IMemoryRegion region) {
                region.bulkWrite( (short) origin , binary , 0 , binary.length );
            }
        };

        final Emulator e = new Emulator();
        e.setMemoryProvider( provider );

        final BreakpointsController ctrl = new BreakpointsController(e.getCPU() , e.getMemory() );
        CountDownLatch stopped = new CountDownLatch(1);

        final EmulatorDriver driver = new EmulatorDriver( e ) {

            @Override
            protected void tick() {
            }

            @Override
            protected BreakpointsController getBreakPointsController() {
                return ctrl;
            }
        };
        driver.addEmulationListener( new IEmulationListener() {

            @Override
            public void emulationStarted() {
                
            }

            @Override
            public void emulationStopped(Throwable t,boolean stoppedOnBreakpoint) {
                stopped.countDown();                
            }
            
        } );

        e.reset();
        e.getCPU().pc( origin );

        driver.start();
        ctrl.addBreakpoint( new Breakpoint( (short) 0x103e , false , true ) );
        driver.setMode( Mode.CONTINOUS );

        if ( ! stopped.await( 5 , TimeUnit.SECONDS ) )
        {
            driver.setMode(Mode.SINGLE_STEP);
            fail("Test failed to complete after 5 seconds");
        }
        byte outcome = (byte) e.getMemory().readByte( 0xa000 );
        assertEquals( "Test failed" , (byte) 0 , outcome );
    }

    public void testSanity() throws IOException, InterruptedException
    {
        int value = (byte) 0x8e;
        System.out.println("result: "+(value+value));
        //		fail("abort");

        final String source = loadTestProgram( "/test.asm");

        final AST ast;
        try {
            ast = new Parser( new Lexer( new Scanner( source ) ) ).parse();
        }
        catch(Exception e)
        {
            DisassemblerTest.maybePrintError( source , e );
            throw e;
        }

        final SourceHelper helper = new SourceHelper( source );
        final Assembler a = new Assembler();
        final byte[] binary = a.assemble( ast , helper );
        final int origin = a.getOrigin();

        final IMemoryProvider provider = new IMemoryProvider() {

            @Override
            public void loadInto(IMemoryRegion region) {
                region.bulkWrite( (short) origin , binary , 0 , binary.length );
            }
        };

        final Emulator e = new Emulator();
        e.setMemoryProvider( provider );

        CountDownLatch stopped = new CountDownLatch(1);

        final BreakpointsController ctrl = new BreakpointsController(e.getCPU() , e.getMemory() );
        final EmulatorDriver driver = new EmulatorDriver( e ) {

            @Override
            protected void tick() {
            }

            @Override
            protected BreakpointsController getBreakPointsController() {
                return ctrl;
            }
        };
        
        driver.addEmulationListener( new IEmulationListener() {

            @Override
            public void emulationStarted() {
                
            }

            @Override
            public void emulationStopped(Throwable t,boolean stoppedOnBreakpoint) {
                stopped.countDown();                
            }
            
        } );        

        e.reset();
        e.getCPU().pc( origin );

        driver.start();
        ctrl.addBreakpoint( new Breakpoint( (short) 0x45c0 , false , true ) );
        driver.setMode( Mode.CONTINOUS );

        if ( ! stopped.await( 5 , TimeUnit.SECONDS ) )
        {
            driver.setMode(Mode.SINGLE_STEP);
            fail("Test failed to complete after 5 seconds");
        }
        byte outcome = (byte) e.getMemory().readByte( 0x210 ); // EXPECTED FINAL RESULTS: $0210 = FF
        assertEquals( "Test failed , failed test no. "+(outcome & 0xff), (byte) 0xff , outcome );
    }

    public void testAXS()
    {
        /*
         * AXS: Akku AND X-Register+Stored to memory (alternatives Mnemonic: SAX)
         * AXS absolut ($8F, 3B, 4T, <keine>)
         *
         * AXS funktioniert so: Die Inhalte von Akku und X-Register werden UND-Verknüpft, aber OHNE eines der beiden Register zu ändern!
         * Das Ergbnis wird dann an der angegebenen Adresse abgelegt. Die Flags im Statusregister (SR) bleiben ebenfalls unverändert!
         *
         * Adressierung | OpCode | Bytes | TZ
         * absolut      |  $8F   |   3   |  4
         * Zero-Page    |  $87   |   2   |  3
         * Zero-Page,Y  |  $97   |   2   |  4
         * indirekt X   |  $83   |   2   |  6
         */
        execute("LDA #<$c000 STA $2+$7e LDA #>$c000 STA $2+$7e+1 LDA #$ff LDX #$7e AXS ($2,x)").assertA(0xff).assertX(0x7e) .assertMemoryContains( 0x80 , 0x00,0xc0 ) .assertMemoryContains( 0xc000 , 0x7e );
        execute("LDA #$ff\n LDX #$f0\n AXS $c000").assertA(0xff).assertX(0xf0).assertMemoryContains( 0xc000 , 0xf0 );
        execute("LDA #$ff\n LDX #$f0\n AXS $a0").assertA(0xff).assertX(0xf0).assertMemoryContains( 0xa0 , 0xf0 );
        execute("LDY #$7e LDA #$ff LDX #$7e AXS $2,Y").assertA(0xff).assertX(0x7e).assertMemoryContains( 0x80 , 0x7e );
    }

    public static String loadTestProgram(String classpath) throws IOException
    {
        InputStream in = EmulatorTest.class.getResourceAsStream( classpath );
        assertNotNull( in );

        final StringBuilder buffer = new StringBuilder();
        IOUtils.readLines( in ).forEach( line -> buffer.append( line ).append("\n") );
        final String source = buffer.toString();
        return source;
    }

    public void testSTX() {
        /*
STX (STore X register)

Affects Flags: none

MODE           SYNTAX       HEX LEN TIM
Zero Page     STX $44       $86  2   3
Zero Page,Y   STX $44,Y     $96  2   4
Absolute      STX $4400     $8E  3   4
         */
        execute("STX $0a").setX(0x12).assertMemoryContains( 0x0a , 0x12 );
        execute("STX $40,Y").setX(0x12).setY(0x10).assertMemoryContains( 0x50 , 0x12 );
        execute("STX $1234").setX(0xab).assertMemoryContains( 0x1234 , 0xab );
    }

    public void testJSR()
    {
        final int returnAdr = PRG_LOAD_ADDRESS+2; // JSR instruction has 3 bytes but return address stored on stack needs to be (returnAdr-1)
        final byte lo = (byte) (returnAdr & 0xff);
        final byte hi = (byte) ((returnAdr>>8) & 0xff);
        System.out.println("expecting return address "+HexDump.toAdr( returnAdr ) );
        execute("JSR label\n HLT\n label: \nHLT").assertPC(0x1005).assertOnStack( lo, hi);
    }

    public void testDec() {

        /*
MODE           SYNTAX       HEX LEN TIM
Zero Page     DEC $44       $C6  2   5
Zero Page,X   DEC $44,X     $D6  2   6
Absolute      DEC $4400     $CE  3   6
Absolute,X    DEC $4400,X   $DE  3   7
         */
        // zero page
        execute("LDA #$02\n STA $02\n DEC $02").assertMemoryContains( 0x02 , 0x01 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
        execute("LDA #$01\n STA $02\n DEC $02").assertMemoryContains( 0x02 , 0x00 ).assertFlags( CPU.Flag.ZERO );
        execute("LDA #$00\n STA $02\n DEC $02").assertMemoryContains( 0x02 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

        // zero page , X
        execute("LDA #$02\n STA $03\n LDX #$01\n DEC $02,X").assertMemoryContains( 0x03 , 0x01 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
        execute("LDA #$01\n STA $03\n LDX #$01\n DEC $02,X").assertMemoryContains( 0x03 , 0x00 ).assertFlags( CPU.Flag.ZERO );
        execute("LDA #$00\n STA $03\n LDX #$01\n DEC $02,X").assertMemoryContains( 0x03 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

        // zero page , X with wraparound
        execute("LDA #$01\n STA $03\n LDX #$04\n DEC $ff,X").assertMemoryContains( 0x03 , 0x00 ).assertFlags( CPU.Flag.ZERO );

        // absolute
        execute("LDA #$02\n STA $1234\n DEC $1234").assertMemoryContains( 0x1234 , 0x01 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
        execute("LDA #$01\n STA $1234\n DEC $1234").assertMemoryContains( 0x1234 , 0x00 ).assertFlags( CPU.Flag.ZERO );
        execute("LDA #$00\n STA $1234\n DEC $1234").assertMemoryContains( 0x1234 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

        // absolute , X
        execute("LDA #$02\n STA $1235\n LDX #$01\n DEC $1234,X").assertMemoryContains( 0x1235 , 0x01 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
        execute("LDA #$01\n STA $1235\n LDX #$01\n DEC $1234,X").assertMemoryContains( 0x1235 , 0x00 ).assertFlags( CPU.Flag.ZERO );
        execute("LDA #$00\n STA $1235\n LDX #$01\n DEC $1234,X").assertMemoryContains( 0x1235 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

        // absolute with wrap-around
        execute("LDA #$00\n STA $03\n LDX #$04\n DEC $ffff,X").assertMemoryContains( 0x03 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );
    }

    public void testBIT() {

        // accu & memory

        // 0xff & 0xff = 0xff
        execute("LDA #$ff\n LDX #$ff\n STX $05\n BIT $05").assertFlags( CPU.Flag.NEGATIVE , CPU.Flag.OVERFLOW);

        // 0xff & 0x00 = 0x00
        execute("LDA #$ff\n LDX #$00\n STX $05\n BIT $05").assertFlags( CPU.Flag.ZERO);

        // 0xff & 0x40 = 0x40
        execute("LDA #$ff\n LDX #$40\n STX $05\n BIT $05").assertFlags( CPU.Flag.OVERFLOW );
    }

    public void testInc() {

        /*
 		 Affects Flags: S Z

MODE           SYNTAX       HEX LEN TIM
Zero Page     INC $44       $E6  2   5
Zero Page,X   INC $44,X     $F6  2   6
Absolute      INC $4400     $EE  3   6
Absolute,X    INC $4400,X   $FE  3   7

         */
        // zero page
        execute("LDA #$02\n STA $02\n INC $02").assertMemoryContains( 0x02 , 0x03 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
        execute("LDA #$ff\n STA $02\n INC $02").assertMemoryContains( 0x02 , 0x00 ).assertFlags( CPU.Flag.ZERO );
        execute("LDA #$fe\n STA $02\n INC $02").assertMemoryContains( 0x02 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

        // zero page , X
        execute("LDA #$02\n STA $03\n LDX #$01\n INC $02,X").assertMemoryContains( 0x03 , 0x03 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
        execute("LDA #$ff\n STA $03\n LDX #$01\n INC $02,X").assertMemoryContains( 0x03 , 0x00 ).assertFlags( CPU.Flag.ZERO );
        execute("LDA #$fe\n STA $03\n LDX #$01\n INC $02,X").assertMemoryContains( 0x03 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

        // zero page , X with wraparound
        execute("LDA #$ff\n STA $03\n LDX #$04\n INC $ff,X").assertMemoryContains( 0x03 , 0x00 ).assertFlags( CPU.Flag.ZERO );

        // absolute
        execute("LDA #$02\n STA $1234\n INC $1234").assertMemoryContains( 0x1234 , 0x03 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
        execute("LDA #$ff\n STA $1234\n INC $1234").assertMemoryContains( 0x1234 , 0x00 ).assertFlags( CPU.Flag.ZERO );
        execute("LDA #$fe\n STA $1234\n INC $1234").assertMemoryContains( 0x1234 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

        // absolute , X
        execute("LDA #$02\n STA $1235\n LDX #$01\n INC $1234,X").assertMemoryContains( 0x1235 , 0x03 ).assertFlagsNotSet( CPU.Flag.ZERO,CPU.Flag.NEGATIVE );
        execute("LDA #$ff\n STA $1235\n LDX #$01\n INC $1234,X").assertMemoryContains( 0x1235 , 0x00 ).assertFlags( CPU.Flag.ZERO );
        execute("LDA #$fe\n STA $1235\n LDX #$01\n INC $1234,X").assertMemoryContains( 0x1235 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );

        // absolute with wrap-around
        execute("LDA #$fe\n STA $03\n LDX #$04\n INC $ffff,X").assertMemoryContains( 0x03 , 0xff ).assertFlags( CPU.Flag.NEGATIVE );
    }

    public void testRTI() {

        final int returnAdr = PRG_LOAD_ADDRESS+14;
        final byte lo = low(returnAdr);
        final byte hi = high(returnAdr);

        final byte flags = CPU.Flag.CARRY.set((byte)0);

        final String source = "LDX #$00\n"+ // 2 bytes , clear X so we are sure that its value was set by the subroutine
                "LDA #"+hex(hi)+"\nPHA\n"+ // 3 bytes , push return address hi
                "LDA #"+hex(lo)+"\nPHA\n"+ // 3 bytes , push return address lo
                "LDA #"+hex(flags)+"\n PHA\n"+ // 3 bytes , push processor flags
                "CLC\n"+ // 1 byte , clear carry so we know that processor flags got restored by RTI
                "RTI\n"+ // 1 byte , return , invokes our subroutine
                ILLEGAL_OPCODE+ // 1 byte
                "sub: LDX #$11\n"+ // set X so we know the sub routine got executed
                ILLEGAL_OPCODE;

        execute(source).assertX(0x11).assertFlags( CPU.Flag.CARRY );
    }

    public void testRTS()
    {
        final String source = "JSR sub\n"+
                "LDA #$12\n"+
                ILLEGAL_OPCODE+
                "sub: LDX #$11\n"+
                "     RTS";
        execute(source).assertA(0x12).assertX(0x11);
    }

    public void testStackOperations() {

        /*
 		TXS (Transfer X to Stack ptr)   $9A  2
TSX (Transfer Stack ptr to X)   $BA  2
PHA (PusH Accumulator)          $48  3
PLA (PuLl Accumulator)          $68  4
PHP (PusH Processor status)     $08  3
PLP (PuLl Processor status)     $28  4
         */

        execute("LDX #$12\n TXS").assertSP( 0x12 );
        execute("LDX #$12\n TXS\n LDX #$00\n TSX").assertSP( 0x12 ).assertX( 0x12 ).assertFlags();
        execute("LDX #$00\n TXS\n LDX #$aa\n TSX").assertSP( 0x00 ).assertX( 0x00 ).assertFlags( CPU.Flag.ZERO );
        execute("LDX #$ff\n TXS\n LDX #$aa\n TSX").assertSP( 0xff ).assertX( 0xff ).assertFlags( CPU.Flag.NEGATIVE);

        execute("LDA #$12\n PHA").assertOnStack( (byte) 0x12);
        execute("LDA #$12\n PHA\n LDA #$00\n PLA").assertA( 0x12 ).assertFlags();
        execute("LDA #$00\n PHA\n LDA #$ff\n PLA").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);
        execute("LDA #$ff\n PHA\n LDA #$00\n PLA").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        execute("LDA #$ff\n PHP\n LDA #$00\n PLP").assertA( 0x00 ).assertFlags(CPU.Flag.NEGATIVE);
        execute("LDA #$00\n PHP\n LDA #$ff\n PLP").assertA( 0xff ).assertFlags(CPU.Flag.ZERO);
    }

    public void testChangeProcessorFlags()
    {
        execute("SEC").assertFlags(CPU.Flag.CARRY);
        execute("SEI").assertFlags(CPU.Flag.IRQ_DISABLE);
        execute("SED").assertFlags(CPU.Flag.DECIMAL_MODE);

        // FIXME: Set carry flag before executing CLV
        execute("CLC").assertFlagsNotSet(CPU.Flag.CARRY);
        execute("SEI \nCLI").assertFlagsNotSet(CPU.Flag.IRQ_DISABLE);
        execute("SED \nCLD").assertFlagsNotSet(CPU.Flag.DECIMAL_MODE);

        // FIXME: Set overflow flag before executing CLV
        execute("CLV").assertFlagsNotSet(CPU.Flag.OVERFLOW);
    }

    public void testRegisterIns() {
        /*
			case 0xAA: // TAX (Transfer A to X)    $AA
			case 0x8A: // TXA (Transfer X to A)    $8A
			case 0xA8: // TAY (Transfer A to Y)    $A8
			case 0x98: // TYA (Transfer Y to A)    $98

			case 0xCA: // DEX (DEcrement X)        $CA
			case 0x88: // DEY (DEcrement Y)        $88
			case 0xE8: // INX (INcrement X)        $E8
			case 0xC8: // INY (INcrement Y)        $C8
         */
        execute("LDA #$12\n LDX #$00\n TAX").assertX( 0x12 ).assertFlagsNotSet(CPU.Flag.ZERO, CPU.Flag.NEGATIVE );
        execute("LDX #$12\n LDA #$00\n TXA").assertA( 0x12 ).assertFlagsNotSet(CPU.Flag.ZERO, CPU.Flag.NEGATIVE );

        execute("LDA #$00\n LDY #$ff\n TAX").assertX( 0x00 ).assertFlags(CPU.Flag.ZERO);
        execute("LDX #$00\n LDY #$ff TXA").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);

        execute("LDA #$ff\n LDY #$00\n TAX").assertX( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
        execute("LDX #$ff\n LDY #$0 TXA").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        execute("LDA #$12\n LDY #$ff\n TAY").assertY( 0x12 ).assertFlagsNotSet(CPU.Flag.ZERO, CPU.Flag.NEGATIVE );
        execute("LDY #$12\n LDA #$00\n TYA").assertA( 0x12 ).assertFlagsNotSet(CPU.Flag.ZERO, CPU.Flag.NEGATIVE );

        execute("LDA #$00\n LDY #$ff\n TAY").assertY( 0x00 ).assertFlags(CPU.Flag.ZERO);
        execute("LDY #$00\n LDA #$ff\n TYA").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);

        execute("LDA #$ff\n LDY #$00\n TAY").assertY( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
        execute("LDY #$ff\n LDA #$00\n TYA").assertA( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        execute("LDA #$12\n LDY #$00\n TAY").assertY( 0x12 ).assertFlagsNotSet(CPU.Flag.ZERO, CPU.Flag.NEGATIVE );
        execute("LDY #$12\n LDA #$00 TYA").assertA( 0x12 ).assertFlagsNotSet(CPU.Flag.ZERO, CPU.Flag.NEGATIVE );

        // dec
        execute("LDX #$02\n DEX").assertX( 0x01 ).assertFlagsNotSet(CPU.Flag.NEGATIVE,CPU.Flag.ZERO);
        execute("LDY #$02\n DEY").assertY( 0x01 ).assertFlagsNotSet(CPU.Flag.NEGATIVE,CPU.Flag.ZERO);

        execute("LDX #$01\n DEX").assertX( 0x00 ).assertFlags(CPU.Flag.ZERO);
        execute("LDY #$01\n DEY").assertY( 0x00 ).assertFlags(CPU.Flag.ZERO);

        execute("LDX #$00\n DEX").assertX( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
        execute("LDY #$00\n DEY").assertY( 0xff ).assertFlags(CPU.Flag.NEGATIVE);

        // inc
        execute("LDX #$00\n INX").assertX( 0x01 ).assertFlagsNotSet(CPU.Flag.NEGATIVE,CPU.Flag.ZERO);
        execute("LDY #$00\n INY").assertY( 0x01 ).assertFlagsNotSet(CPU.Flag.NEGATIVE,CPU.Flag.ZERO);

        execute("LDX #$ff\n INX").assertX( 0x00 ).assertFlags(CPU.Flag.ZERO);
        execute("LDY #$ff\n INY").assertY( 0x00 ).assertFlags(CPU.Flag.ZERO);

        execute("LDX #$7f\n INX").assertX( 0x80 ).assertFlags(CPU.Flag.NEGATIVE);
        execute("LDY #$7f\n INY").assertY( 0x80 ).assertFlags(CPU.Flag.NEGATIVE);
    }

    public void testSTY()
    {
        /*
		MODE           SYNTAX       HEX LEN TIM
		Zero Page     STY $44       $84  2   3
		Zero Page,X   STY $44,X     $94  2   4
		Absolute      STY $4400     $8C  3   4
         */
        execute("STY $0a").setY(0x12).assertMemoryContains( 0x0a , 0x12 );
        execute("STY $40,X").setY(0x12).setX(0x10).assertMemoryContains( 0x50 , 0x12 );
        execute("STY $1234").setY(0xab).assertMemoryContains( 0x1234 , 0xab );
    }

    public void testSTA() {
        /*
MODE           SYNTAX       HEX LEN TIM
Zero Page     STA $44       $85  2   3
Zero Page,X   STA $44,X     $95  2   4
Absolute      STA $4400     $8D  3   4
Absolute,X    STA $4400,X   $9D  3   5
Absolute,Y    STA $4400,Y   $99  3   5
Indirect,X    STA ($44,X)   $81  2   6
Indirect,Y    STA ($44),Y   $91  2   6
         */
        execute("STA $0a").setA(0x12).assertMemoryContains( 0x0a , 0x12 );
        execute("STA $40,X").setA(0x12).setX(0x10).assertMemoryContains( 0x50 , 0x12 );
        execute("STA $1234").setA(0xab).assertMemoryContains( 0x1234 , 0xab );
        execute("STA $1200 , X ").setA(0xab).setX(0x10).assertMemoryContains( 0x1210 , 0xab );
        execute("STA $1200 , Y ").setA(0xab).setY(0x10).assertMemoryContains( 0x1210 , 0xab );
        execute("STA (40,X) ").setA(0xab).setX(10).writeWord( 50 , 0x1234 ).assertMemoryContains( 0x1234 , 0xab );
        execute("STA (40),Y ").setA(0xab).setY(0x10).writeWord( 40 , 0x1200 ).assertMemoryContains( 0x1210 , 0xab );
    }

    public void testLDX() {
        /*
MODE           SYNTAX       HEX LEN TIM
Immediate     LDX #$44      $A2  2   2
Zero Page     LDX $44       $A6  2   3
Zero Page,Y   LDX $44,Y     $B6  2   4
Absolute      LDX $4400     $AE  3   4
Absolute,Y    LDX $4400,Y   $BE  3   4+
         */
        execute("LDX #$00").assertX( 0x00 ).assertFlags(CPU.Flag.ZERO);
        execute("LDX #$ff").assertX( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
        execute("LDX #$12").assertX( 0x12 ).assertFlags();
        execute("LDX $44").writeByte( 0x44 , 23 ).assertX( 23 ).assertFlags();
        execute("LDX 40 , Y ").setY(10).writeByte( 50 , 0x12 ).assertX( 0x12 ).assertFlags();
        execute("LDX $4000").writeByte( 0x4000 , 0x12 ).assertX( 0x12 ).assertFlags();
        execute("LDX $4000 , Y ").setY(0x10).writeByte( 0x4010 , 0x12 ).assertX( 0x12 ).assertFlags();
    }

    public void testLDY() {
        /*
MODE           SYNTAX       HEX LEN TIM
Immediate     LDY #$44      $A0  2   2
Zero Page     LDY $44       $A4  2   3
Zero Page,X   LDY $44,X     $B4  2   4
Absolute      LDY $4400     $AC  3   4
Absolute,X    LDY $4400,X   $BC  3   4+
         */
        execute("LDY #$00").assertY( 0x00 ).assertFlags(CPU.Flag.ZERO);
        execute("LDY #$ff").assertY( 0xff ).assertFlags(CPU.Flag.NEGATIVE);
        execute("LDY #$12").assertY( 0x12 ).assertFlags();
        execute("LDY $44").writeByte( 0x44 , 23 ).assertY( 23 ).assertFlags();
        execute("LDY 40 , X ").setX(10).writeByte( 50 , 0x12 ).assertY( 0x12 ).assertFlags();
        execute("LDY $4000").writeByte( 0x4000 , 0x12 ).assertY( 0x12 ).assertFlags();
        execute("LDY $4000 , X ").setX(0x10).writeByte( 0x4010 , 0x12 ).assertY( 0x12 ).assertFlags();
    }

    public void testJMP() {
        execute("JMP label\n HLT\n label: HLT\n").assertPC( PRG_LOAD_ADDRESS+4+1); // +1 because executed HLT instruction will increment the PC by one as well
        // write $02 == HLT instruction to $1234 so the emulation terminates there
        execute("LDA #$02\n STA $1234\n JMP ($2000)").writeWord(0x2000, 0x1234 ).assertPC( 0x1234+1 ); // +1 because executed HLT instruction will increment the PC by one as well
    }

    public void testNOP() {
        execute("NOP").assertPC( PRG_LOAD_ADDRESS+1+1 ); // +1 because of HLT instruction appended after NOP
    }

    public void testBranchOnFlagSet() {

        final int dest = PRG_LOAD_ADDRESS+100;
        final String destination = HexDump.toAdr( dest );

        execute("BMI "+destination).writeByte(dest, 0x02 ).setFlags(CPU.Flag.NEGATIVE).assertPC( dest+1 ); // +1 because of HLT ins appended to the src
        execute("BVS "+destination).writeByte(dest, 0x02 ).setFlags(CPU.Flag.OVERFLOW).assertPC( dest+1 ); // +1 because of HLT ins appended to the src
        execute("BCS "+destination).writeByte(dest, 0x02 ).setFlags(CPU.Flag.CARRY).assertPC( dest+1 ); // +1 because of HLT ins appended to the src
        execute("BEQ "+destination).writeByte(dest, 0x02 ).setFlags(CPU.Flag.ZERO).assertPC( dest+1 ); // +1 because of HLT ins appended to the src

        execute("BPL "+destination).writeByte(dest, 0x02 ).clearFlags(CPU.Flag.NEGATIVE).assertPC( dest+1 ); // +1 because of HLT ins appended to the src
        execute("BVC "+destination).writeByte(dest, 0x02 ).clearFlags(CPU.Flag.OVERFLOW).assertPC( dest+1 ); // +1 because of HLT ins appended to the src
        execute("BCC "+destination).writeByte(dest, 0x02 ).clearFlags(CPU.Flag.CARRY).assertPC( dest+1 ); // +1 because of HLT ins appended to the src
        execute("BNE "+destination).writeByte(dest, 0x02 ).clearFlags(CPU.Flag.ZERO).assertPC( dest+1 ); // +1 because of HLT ins appended to the src
    }

    public void testLDAImmediateNonZero()
    {
        execute("LDA #$44").assertA( 0x44 ).assertFlags();
    }

    public void testLDAImmediateZero()
    {
        execute("LDA #$00").assertA( 0x00 ).assertFlags(CPU.Flag.ZERO);
    }

    public void testLDAImmediateNegative()
    {
        execute("LDA #$80").assertA( 0x80 ).assertFlags(CPU.Flag.NEGATIVE);
    }

    // ============ helper ================

    protected final class Helper
    {
        public final Emulator emulator = new Emulator();
        public final String source;
        private boolean executed = false;

        private boolean addHltInstruction = true;
        private boolean rethrowHltException = false;

        private final List<Runnable> blocks = new ArrayList<>();
        private final List<Consumer<Emulator>> afterEachStep = new ArrayList<>();
        private final List<Consumer<Emulator>> beforeEachStep = new ArrayList<>();

        private long cyclesExecuted;

        private final boolean failOnBreak;

        public Helper(String source) {
            this.source = source;
            this.failOnBreak = true;
        }

        public Helper afterEachStep(Consumer<Emulator> r) {
            afterEachStep.add(r);
            return this;
        }

        public Helper rethrowHltException(boolean yesNo) {
            this.rethrowHltException = yesNo;
            return this;
        }

        public Helper beforeEachStep(Consumer<Emulator> r) {
            beforeEachStep.add(r);
            return this;
        }

        public Helper(String source, boolean failOnBreak ) {
            this.source = source;
            this.failOnBreak = failOnBreak;
        }

        public Helper assertFlags(CPU.Flag... flags)
        {
            maybeExecute();

            final Set<Flag> enabledFlags = emulator.getCPU().getFlags();
            enabledFlags.remove( CPU.Flag.EXTENSION); // discard, it's always set
            final int expectedCount = flags == null ? 0 : flags.length;
            assertEquals( "Expected flags "+ArrayUtils.toString( flags )+" but got "+enabledFlags , expectedCount , enabledFlags.size() );
            if ( flags != null ) {
                for ( final Flag exp : flags ) {
                    if ( ! enabledFlags.contains( exp ) ) {
                        fail( "Expected flags "+ArrayUtils.toString( flags )+" but got "+enabledFlags );
                    }
                }
            }
            return this;
        }

        public Helper assertFlagsNotSet(CPU.Flag flag1,CPU.Flag... flags2)
        {
            maybeExecute();

            final Set<Flag> enabledFlags = emulator.getCPU().getFlags();
            final Set<Flag> clearedFlags = new HashSet<>();
            clearedFlags.add( flag1 );
            if ( flags2 != null ) {
                clearedFlags.addAll(Arrays.asList(flags2));
            }
            for ( final Flag exp : clearedFlags)
            {
                if ( enabledFlags.contains( exp ) ) {
                    fail( "Expected CPU flag  "+exp+" to be cleared but wasn't");
                }
            }
            return this;
        }

        public Helper setA(int value) {
            return block( () ->
            {
                emulator.getCPU().setAccumulator((byte) value);
            });
        }

        public Helper setX(int value) {
            return block( () ->
            {
                emulator.getCPU().setX( (byte) value );
            });
        }

        public Helper setY(int value) {
            return block( () ->
            {
                emulator.getCPU().setY( (byte) value );
            });
        }

        public Helper setFlags(CPU.Flag flag1,CPU.Flag... flags2)
        {
            return block( () ->
            {
                emulator.getCPU().setFlag( flag1 );
                if ( flags2 != null ) {
                    for ( final CPU.Flag f : flags2 ) {
                        emulator.getCPU().setFlag( f );
                    }
                }
            });
        }

        public Helper clearFlags(CPU.Flag flag1,CPU.Flag... flags2)
        {
            return block( () ->
            {
                emulator.getCPU().clearFlag( flag1 );
                if ( flags2 != null ) {
                    for ( final CPU.Flag f : flags2 ) {
                        emulator.getCPU().clearFlag( f );
                    }
                }
            });
        }

        public Helper writeByte(int address,int value)
        {
            return block( () ->
            {
                emulator.getMemory().writeByte( (short) address , (byte) value );
            });
        }

        public Helper writeWord(int address,int value)
        {
            return block( () ->
            {
                emulator.getMemory().writeWord( (short) address , (short) value );
            });
        }

        public Helper assertA(int value) {

            maybeExecute();

            final int expected = value;
            final int actual = emulator.getCPU().getAccumulator();
            assertEquals( "Expected accumulator to contain "+hex((byte)expected)+" ("+bin((byte) expected)+")"
                    + " but was "+hex((byte)actual)+" ("+bin((byte)actual)+")", expected & 0xff , actual & 0xff );
            return this;
        }

        // assertSP
        public Helper assertSP(int value) {

            maybeExecute();

            final short expected = (short) (value & 0xffff);
            final short actual = emulator.getCPU().sp;
            assertEquals( (byte) expected , (byte) (actual & 0xff) );
            return this;
        }

        public Helper assertX(int value) {

            maybeExecute();

            final int expected = value;
            final int actual = emulator.getCPU().getX();
            assertEquals( expected & 0xff , actual & 0xff );
            return this;
        }

        public Helper assertY(int value) {

            maybeExecute();

            final int expected = value;
            final int actual = emulator.getCPU().getY();
            assertEquals( expected & 0xff , actual & 0xff );
            return this;
        }

        public Helper assertPC(int value) {

            maybeExecute();

            final int expected = value & 0xffff;
            final int actual = emulator.getCPU().pc();
            assertEquals( "Expected PC = "+HexDump.toAdr(expected)+" but was "+HexDump.toAdr(actual) , expected , actual );
            return this;
        }

        public Helper assertOnStack(byte b1,byte... bytes)
        {
            maybeExecute();

            final int len = 1 + ( bytes != null ? bytes.length : 0 );
            final byte[] expected = new byte[len];
            expected[0]=b1;
            if ( bytes != null ) {
                for ( int i = 0 ; i < bytes.length ; i++ ) {
                    expected[1+i] = bytes[i];
                }
            }

            short adr = (short) (emulator.getCPU().sp+1); // +1 because pop() operation FIRST increments SP and then reads from where the SP is pointing to
            for ( int i = 0 ; i < expected.length ; i++ )
            {
                final byte actual = (byte) emulator.getMemory().readByte( adr );
                final byte exp = expected[i];
                assertEquals( "Expected byte $"+HexDump.byteToString(exp)+" on stack @ "+HexDump.toAdr(adr)+" but got $"+
                        HexDump.byteToString(actual) , exp , actual );
                adr += 1;
            }
            return this;
        }

        public Helper block(Runnable r)
        {
            assertFalse("Program already executed" , executed );
            blocks.add( r );
            return this;
        }

        public Helper assertMemoryContains(int offset,int expected1,int... expected2) {

            maybeExecute();

            final int len = 1 + ( expected2 == null ? 0 : expected2.length );
            final byte[] expected = new byte[len];
            expected[0] = (byte) expected1;
            if ( expected2 != null ) {
                for ( int i = 0 ;i < expected2.length ; i++ ) {
                    expected[i+1] = (byte) expected2[i];
                }
            }

            final byte[] actual = new byte[ expected.length ];
            for ( int i = 0 ; i < actual.length ; i++ )
            {
                actual[i] = (byte) emulator.getMemory().readByte( (short) (offset+i) );
            }

            AssemblerTest.assertArrayEquals( offset , expected , actual );
            return this;
        }

        public void maybeExecute()
        {
            if ( ! executed ) {
                run();
            }
        }

        public void run()
        {
            run(10000);
        }

        public void run(long maxCycles)
        {
            try
            {
                final int ADDITIONAL_BYTES = 1; // number of bytes we insert before executing the test payload, currently 1 because of the CLI ins we prepend

                final int realLoadAdr = PRG_LOAD_ADDRESS-ADDITIONAL_BYTES;
                String asm = "*= "+HexDump.toAdr( realLoadAdr )+"\nCLI\n"+source;

                if ( addHltInstruction ) {
                    asm += "\nHLT";
                }
                final Assembler a = new Assembler();
                final byte[] actual = compile(a,asm);

                final Disassembler d = new Disassembler();
                final int bytesToDisassemble = actual.length < 23 ? actual.length : 32;
                final List<Line> lines = d.disassemble( realLoadAdr , actual , 0 , bytesToDisassemble);
                System.out.println( lines.stream().map( l -> l.toString() ).collect( Collectors.joining("\n") ) );

                final IMemoryProvider provider = new IMemoryProvider()
                {
                    @Override
                    public void loadInto(IMemoryRegion region)
                    {
                        // switch to all-ram so overwritten RESET and BRK vectors
                        // actually take effect (otherwise the memory region would be mapped to kernel rom
                        // and thus reads would always return the ROM values)
                        emulator.getMemory().setMemoryLayout( (byte) 0 );

                        // fill memory with invalid instructions
                        // so the CPU stops when it runs off the compiled program
                        for ( int i = 2 ; i < 65536 ; i+=2) { // starting at 0x02 because of PLA data & control registers at 0x00 and 0x01
                            region.writeWord( (short) i , (short) 0x6464 );
                        }

                        region.bulkWrite( (short) a.getOrigin() , actual , 0 , actual.length );
                        region.writeWord( (short) CPU.RESET_VECTOR_LOCATION , (short) PRG_LOAD_ADDRESS );

                        System.out.println("At program start: ");
                        HexDump.INSTANCE.dump( (short) PRG_LOAD_ADDRESS , region , PRG_LOAD_ADDRESS , 1024 );
                    }
                };

                emulator.reset();

                emulator.setMemoryProvider( provider );
                emulator.getCPU().pc( PRG_LOAD_ADDRESS-ADDITIONAL_BYTES );

                blocks.forEach( b -> b.run() );
                
                final EmulatorDriver driver = new EmulatorDriver( emulator ) {
                    
                    private final BreakpointsController controller = new BreakpointsController( emulator.getCPU() , emulator.getMemory() ); 
                    @Override
                    protected void tick() {
                    }
                    
                    @Override
                    protected BreakpointsController getBreakPointsController() {
                        return controller;
                    }
                };                

                long cyclesExecuted = 0;
                RuntimeException lastException = null;
                while ( cyclesExecuted < maxCycles )
                {
                    try
                    {
                        final boolean previousInstructionFinished = emulator.getCPU().cycles == 0;
                        if ( previousInstructionFinished  ) {
                            beforeEachStep.forEach( r -> r.accept( emulator ) );
                        }
                        emulator.doOneCycle(driver);
                        cyclesExecuted++;
                        if ( previousInstructionFinished ) {
                            afterEachStep.forEach( r -> r.accept( emulator ) );
                        }
                    } 
                    catch(HLTException e) 
                    {
                        System.err.println("*** Stopped because of HLT , PC = "+HexDump.toAdr( emulator.getCPU().pc() ));

                        lastException = e;
                        break;
                    } catch(final InvalidOpcodeException e) {
                        System.err.println("*** Stopped because of illegal opcode, PC = "+HexDump.toAdr( emulator.getCPU().pc() ));
                        lastException = e;
                        break;
                    }
                }

                if ( lastException instanceof HLTException && rethrowHltException )
                {
                    throw lastException;
                }

                if ( cyclesExecuted >= maxCycles ) {
                    System.err.println("WARNING -- stopped execution after 10.000 cycles");
                }

                this.cyclesExecuted = cyclesExecuted;

                System.out.println("\n---------------------");
                System.out.println("Compiled: "+asm+"\n");
                System.out.println("Memory  :\n");
                System.out.println( HexDump.INSTANCE.dump((short) PRG_LOAD_ADDRESS  , emulator.getMemory() , PRG_LOAD_ADDRESS , 16 ) );
                System.out.println("\nCPU     : "+emulator.getCPU());
                System.out.println("Stack:\n");

                int sp =  emulator.getCPU().sp;
                System.out.println("SP: "+hex(emulator.getCPU().sp ) );
                sp = sp & 0xffff;
                System.out.println( HexDump.INSTANCE.dump( emulator.getCPU().sp  , emulator.getMemory() , sp , 16 ) );
            } finally {
                executed = true;
            }
        }
    }

    private static byte low(int value) {
        return (byte) value;
    }

    private static byte high(int value) {
        return (byte) (value>>8);
    }

    private Helper execute(String source)
    {
        return new Helper(source);
    }

    private Helper execute(String source,boolean failOnBreak)
    {
        return new Helper(source,failOnBreak);
    }

    private static String bin(byte b) {
        return "0b"+StringUtils.leftPad( Integer.toBinaryString( b & 0xff ) , 8 , '0' );
    }

    private static String hex(byte b) {
        return "$"+HexDump.byteToString( b );
    }

    private static String hex(short b) {
        return "$"+HexDump.toHex( b );
    }

    private static String hex(int b) {
        return "$"+HexDump.toHex( (short) b );
    }

    private static byte[] compile(Assembler a,String asm) 
    {
        final Parser p = new Parser(new Lexer(new Scanner( asm )));

        try {
            return a.assemble( p.parse() );
        }
        catch(final RuntimeException e)
        {
            DisassemblerTest.maybePrintError( asm , e );
            throw e;
        }
    }
}
