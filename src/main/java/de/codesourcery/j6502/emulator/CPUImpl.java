package de.codesourcery.j6502.emulator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import de.codesourcery.j6502.emulator.CPU.Flag;
import de.codesourcery.j6502.emulator.exceptions.HLTException;
import de.codesourcery.j6502.utils.HexDump;

/**
 * Ported from C code in http://codegolf.stackexchange.com/questions/12844/emulate-a-mos-6502-cpu
 *
 * @author tobias.gierke@code-sourcery.de
 */
public final class CPUImpl
{
    protected static final boolean TRACK_INS_DURATION = true;
    
	protected IMemoryRegion memory;
	protected CPU cpu;

	protected boolean penaltyop, penaltyaddr;

	protected int ea, reladdr, value, result;
	protected int opcode;
	protected byte oldstatus;

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

	protected void setoverflow() { cpu.setFlag(Flag.OVERFLOW); }

	protected void clearoverflow() { cpu.clearFlag(Flag.OVERFLOW); }

	protected void setsign() { cpu.setFlag(Flag.NEGATIVE); }

	protected void clearsign() { cpu.clearFlag(Flag.NEGATIVE); }

	protected int read6502(int address)
	{
		//	    if ( (address & 0xffff) == 0xd019) {
		//	        System.out.println("Read from PC "+HexDump.toAdr( cpu.pc() ) );
		//	    }
		return memory.readByte( address );
	}

	protected int readAndWrite6502(int address) {
	    int value = memory.readByte( address );
	    memory.writeByte( address , (byte) value );
		return value;
	}

	protected void write6502(int address,int value)
	{
		memory.writeByte( address , (byte) value );
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
	protected final Runnable imp = () -> {}; //implied

	protected final Runnable  acc = () -> {};  //accumulator

	protected Runnable imm = () -> { //immediate
		ea = cpu.pc();
		cpu.incPC();
	};

	protected final Runnable zp = () -> { //zero-page
		ea = read6502( cpu.pc() );
		cpu.incPC();
	};

	protected final Runnable zpx = () -> { //zero-page,X
		ea = (read6502( cpu.pc() ) + cpu.getX() ) & 0xFF; //zero-page wraparound
		cpu.incPC();
	};

	protected final Runnable zpy = () -> { //zero-page,Y
		ea = (read6502( cpu.pc() ) + cpu.getY() ) & 0xFF; //zero-page wraparound
		cpu.incPC();
	};

	protected final Runnable  rel = () -> { //relative for branch ops (8-bit immediate value, sign-extended)
		reladdr = read6502(cpu.pc());
		cpu.incPC();
		if ( (reladdr & 0x80) != 0 ) // negative branch offset ?
		{
			reladdr |= 0xFFFFFF00; // sign extent byte -> int
		}
	};

	protected final Runnable abso= () -> { //absolute
		ea = memory.readWord( cpu.pc() );
		cpu.incPC(2);
	};

	protected final Runnable absx = () -> { //absolute,X
		ea = memory.readWord( cpu.pc() );
		final int startpage = ea & 0xFF00;
		ea += cpu.getX();

		if (startpage != (ea & 0xFF00)) { //one cycle penlty for page-crossing on some opcodes
			penaltyaddr = true;
		}

		cpu.incPC(2);
	};

	protected final Runnable absy = () -> { //absolute,Y
		ea = memory.readWord( cpu.pc() );
		final int startpage = ea & 0xFF00;
		ea += cpu.getY();

		if (startpage != (ea & 0xFF00)) { //one cycle penlty for page-crossing on some opcodes
			penaltyaddr = true;
		}
		cpu.incPC(2);
	};

	protected final Runnable igb = () ->
	{
		cpu.incPC();
	};

	protected final Runnable igw = () ->
	{
		cpu.incPC(2);
	};

	protected final Runnable ind = () -> { //indirect
		int eahelp = memory.readWord( cpu.pc() );
		int eahelp2 = (eahelp & 0xFF00) | ((eahelp + 1) & 0x00FF); //replicate 6502 page-boundary wraparound bug
		ea = read6502(eahelp) | (read6502(eahelp2) << 8);
		cpu.incPC(2);
	};

	protected final Runnable indx = () -> { // (indirect,X)

		int eahelp = ( read6502( cpu.pc() ) + cpu.getX() ) & 0xFF; //zero-page wraparound for table pointer
		cpu.incPC();
		ea = read6502(eahelp & 0x00FF) | read6502((eahelp+1) & 0x00FF) << 8;
	};

	protected final Runnable indy = () -> { // (indirect),Y
		final int eahelp = read6502(cpu.pc());
		cpu.incPC();

		final int eahelp2 = (eahelp & 0xFF00) | ((eahelp + 1) & 0x00FF); //zero-page wraparound
		ea = read6502(eahelp) | (read6502(eahelp2) << 8);
		final int startpage = ea & 0xFF00;
		ea += cpu.getY();

		if (startpage != (ea & 0xFF00)) { //one cycle penalty for page-crossing on some opcodes
			penaltyaddr = true;
		}
	};

	protected int getvalue()
	{
		if (adrModeTable[opcode] == acc) {
			return cpu.getAccumulator();
		}
		return read6502(ea);
	}

	protected int getvaluereadwrite()
	{
		if (adrModeTable[opcode] == acc) {
			return cpu.getAccumulator();
		}
		return readAndWrite6502(ea);
	}

	protected void putvalue(int saveval) {
		if (adrModeTable[opcode & 0xff] == acc)
		{
			cpu.setAccumulator(saveval);
		} else {
			write6502(ea, saveval);
		}
	}

	//instruction handler functions
	protected final Runnable adc = () ->
	{
		penaltyop = true;
		value = getvalue();

		if ( cpu.isSet( Flag.DECIMAL_MODE) )
		{
			/*
			 * - When the carry is clear, ADC NUM performs the calculation A = A + NUM
			 * - When the carry is set, ADC NUM performs the calculation A = A + NUM + 1
			 * - When the carry is clear, SBC NUM performs the calculation A = A - NUM - 1
			 * - When the carry is set, SBC NUM performs the calculation A = A - NUM
			 *
			 * The only difference is that ADC and SBC perform a binary calculation in binary mode, and perform a BCD calculation in decimal mode.
			 *
			 * The ADC and SBC instructions affect the accumulator and the C, N, V, and Z flags.
			 *  In decimal mode, the accumulator contains the result of the addition or subtraction, as expected.
			 *  In decimal mode, after an ADC, the carry is set if the result was greater than 99 ($99)
			 *  and clear otherwise, and after a SBC, the carry is clear if the result was less than 0 and set otherwise. A few examples are in order:
			 *
			 *  In binary mode, subtraction has a wraparound effect.
			 *  For example $00 - $01 = $FF (and the carry is clear).
			 *  In decimal mode, there is a similar wraparound effect: $00 - $01 = $99, and the carry is clear.
			 *
			 *  In binary mode, the Z flag simply indicates where the result of the instruction is non-zero (Z flag clear) or zero (Z flag set).
			 *  In decimal mode, the Z flag has the same meaning.
			 *
			 *  A common (and correct) interpretation of the N flag is that it contains the value of the high bit (i.e. bit 7) of the result of the instruction.
			 *  This interpretation is the meaning of the N flag in decimal mode.
			 */

			// TODO: Implement BCD mode according to http://www.6502.org/tutorials/decimal_mode.html

			/*
1a. AL = (A & $0F) + (B & $0F) + C
1b. If AL >= $0A, then AL = ((AL + $06) & $0F) + $10
1c. A = (A & $F0) + (B & $F0) + AL
1d. Note that A can be >= $100 at this point
1e. If (A >= $A0), then A = A + $60
1f. The accumulator result is the lower 8 bits of A
1g. The carry result is 1 if A >= $100, and is 0 if A < $100
			 */

			int a = cpu.getAccumulator();
			int b = value;
			int c = ( cpu.isSet( Flag.CARRY ) ? 1 : 0 );

			int al = ( a & 0x0f ) + ( b & 0x0f ) + c;
			if ( al >= 0x0a ) {
				al = (( al + 0x06 ) & 0x0f ) + 0x10;
			}
			a = ( a & 0xf0 ) + ( b & 0xf0 ) + al;
			if ( a >= 0xa0 ) {
				a = a + 0x60;
			}
			cpu.setAccumulator( a & 0xff );
			cpu.setFlag( Flag.CARRY , a >= 0x100 );

			/*
2a. AL = (A & $0F) + (B & $0F) + C
2b. If AL >= $0A, then AL = ((AL + $06) & $0F) + $10
2c. A = (A & $F0) + (B & $F0) + AL, using signed (twos complement) arithmetic
2e. The N flag result is 1 if bit 7 of A is 1, and is 0 if bit 7 if A is 0
2f. The V flag result is 1 if A < -128 or A > 127, and is 0 if -128 <= A <= 127
			 */

			byte t1 = (byte) (a & 0xf0);
			byte t2 = (byte) (b & 0xf0);
			int tmpA = t1 + t2 + al;
			cpu.setFlag(CPU.Flag.NEGATIVE , (tmpA & 0b1000_0000) != 0 );
			cpu.setFlag(CPU.Flag.OVERFLOW , (a < -128 || a > 127 ) );

			return;
		}
		adc( cpu.getAccumulator() , value , cpu.isSet(Flag.CARRY) ? 1 : 0 );
	};

        if ( (value & 0b1111) > 9 || ( ( value >>> 4 ) & 0b1111 ) > 9 ) {
            throw new RuntimeException("Invalid BCD: "+Integer.toHexString( value ) );
        }
        
	
    private int binaryToBcd(int binary ) {

        if ( binary < 0 || binary > 99 ) {
            throw new RuntimeException("Binary value out of BCD range: "+Integer.toHexString( binary ) );
        }
        
        int hi = binary/10;
        int lo = binary - (hi*10);
        return hi << 4 | lo;
    }	

	protected void adc(int a,int b,int carry)
	{
		int result = (a & 0xff) + (b & 0xff) + carry;
		boolean c6 = ( ( (a & 0b0111_1111) + (b & 0b0111_1111) + carry) & 0x80 ) != 0;

		boolean m7 = (a & 0b1000_0000) != 0;
		boolean n7 = (b & 0b1000_0000) != 0;

		cpu.setFlag( Flag.CARRY , ((result & 0x100) != 0) );
		cpu.setFlag( Flag.OVERFLOW, (!m7&!n7&c6) | (m7&n7&!c6));
		cpu.setFlag( Flag.NEGATIVE , ( result & 0b1000_0000) != 0 );
		cpu.setFlag( Flag.ZERO , ( result & 0xff) == 0 );
		cpu.setAccumulator(result);
	}

	protected final Runnable and = () ->
	{
		penaltyop = true;
		value = getvalue();
		result = cpu.getAccumulator() & value;

		zerocalc(result);
		signcalc(result);

		saveaccum(result);
	};

	protected final Runnable asl = () ->
	{
		value = getvaluereadwrite();

		/* http://dustlayer.com/c64-coding-tutorials/2013/4/8/episode-2-3-did-i-interrupt-you
		 * Now as coders are looking for optimization all the time somebody found out that the decrement command dec
		 * can be used to do both operations with just one single command.
		 * This works because dec is a so-called Read-Modify-Write command that writes back the original value during the modify cycle.
		 */
		putvalue(value);

		result = value << 1;

		carrycalc(result);
		zerocalc(result);
		signcalc(result);

		putvalue(result);
	};

	protected final Runnable bcc = () ->
	{
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
	};

	protected final Runnable bcs = () ->
	{
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
	};

	protected final Runnable beq = () ->
	{
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
	};

	protected final Runnable bit = () -> {
		value = getvalue();
		result = cpu.getAccumulator() & value;

		cpu.setFlag( CPU.Flag.ZERO , (result & 0xff) == 0);
		cpu.setFlag( Flag.NEGATIVE , (value & (1<<7)) != 0 );
		cpu.setFlag( Flag.OVERFLOW , (value & (1<<6)) != 0 );
	};

	protected final Runnable bmi = () ->
	{
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
	};

	protected final Runnable bne = () ->
	{
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
	};

	protected final Runnable bpl = () ->
	{
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
	};

	protected final Runnable brk = () ->
	{
		cpu.queueInterrupt( CPU.IRQType.BRK );
	};

	protected final Runnable bvc = () -> {
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
	};

	protected final Runnable bvs = () ->
	{
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
	};

	protected final Runnable clc = () -> clearcarry();

	protected final Runnable cld = () -> cleardecimal();

	protected final Runnable cli = () ->  clearinterrupt();

	protected final Runnable clv = () -> clearoverflow();

	protected final Runnable cmp = () ->
	{
		penaltyop = true;
		value = getvalue();
		result = cpu.getAccumulator() - value;

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
	};

	protected final Runnable cpy = () ->
	{
		value = getvalue();
		result = cpu.getY() - value;

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
	};

	protected final Runnable cpx= () ->
	{
		value = getvalue();

		final int x = cpu.getX();
		result = x - value;

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
	};

	protected final Runnable dec = () -> {
		value = getvaluereadwrite();
		result = value - 1;

		zerocalc(result);
		signcalc(result);

		putvalue(result);
	};

	protected final Runnable dex = () ->
	{
		cpu.setX( cpu.getX() -1 );

		zerocalc( cpu.getX() );
		signcalc( cpu.getX() );
	};

	protected final Runnable dey=() ->
	{
		cpu.setY( cpu.getY() -1 );

		zerocalc( cpu.getY() );
		signcalc( cpu.getY() );
	};

	protected final Runnable eor=() ->
	{
		penaltyop = true;
		value = getvalue();
		result = cpu.getAccumulator() ^ value;

		zerocalc(result);
		signcalc(result);

		saveaccum(result);
	};

	protected final Runnable inc = () ->
	{
		value = getvaluereadwrite();

		/* http://dustlayer.com/c64-coding-tutorials/2013/4/8/episode-2-3-did-i-interrupt-you
		 * Now as coders are looking for optimization all the time somebody found out that the decrement command dec
		 * can be used to do both operations with just one single command.
		 * This works because dec is a so-called Read-Modify-Write command that writes back the original value during the modify cycle.
		 */
		putvalue(value);

		result = value + 1;

		zerocalc(result);
		signcalc(result);

		putvalue(result);
	};

	protected final Runnable inx = () ->
	{
		cpu.setX( cpu.getX()+1 );

		zerocalc( cpu.getX() );
		signcalc( cpu.getX() );
	};

	protected final Runnable iny = ()->
	{
		cpu.setY( cpu.getY()+1 );

		zerocalc( cpu.getY() );
		signcalc(  cpu.getY() );
	};

	protected final Runnable jmp = ()->
	{
		cpu.pc( ea );
	};

	protected final Runnable jsr = ()->
	{
		final int retAdr = cpu.pc()-1;
		push16( retAdr );
		cpu.pc( ea );
	};

	protected final Runnable lda = () ->
	{
		penaltyop = true;
		value = getvalue();

		final int a = value & 0x00FF;
		cpu.setAccumulator( a );

		zerocalc(a);
		signcalc(a);
	};

	protected final Runnable ldx=()->
	{
		penaltyop = true;

		value = getvalue();
		final int x = value & 0x00FF;

		cpu.setX( x );

		zerocalc(x);
		signcalc(x);
	};

	protected final Runnable ldy = () ->
	{
		penaltyop = true;
		value = getvalue();
		final int y = (value & 0x00FF);
		cpu.setY(y);

		zerocalc(y);
		signcalc(y);
	};

	protected final Runnable lsr = () ->
	{
		value = getvaluereadwrite();
		result = value >> 1;

		if ( (value & 1) != 0 ) {
			setcarry();
		}
		else {
			clearcarry();
		}
		zerocalc(result);
		signcalc(result);

		putvalue(result);
	};

	protected final Runnable nop = () ->
	{
		switch (opcode) {
			case 0x1C:
			case 0x3C:
			case 0x5C:
			case 0x7C:
			case 0xDC:
			case 0xFC:
				penaltyop = true;
				break;
		}
	};

	protected final Runnable ora = () ->
	{
		penaltyop = true;
		value = getvalue();
		result = cpu.getAccumulator() | value;

		zerocalc(result);
		signcalc(result);

		saveaccum(result);
	};

	protected final Runnable pha = () ->
	{
		push8( cpu.getAccumulator() );
	};

	protected final Runnable php = () ->
	{
		final byte flags = cpu.getFlagBits();
		push8( flags  & 0xff );
	};

	protected final Runnable pla = () ->
	{
		final int a = cpu.pop( memory );
		cpu.setAccumulator(a);

		zerocalc(a);
		signcalc(a);
	};

	protected final Runnable plp = () ->
	{
		byte value = (byte) cpu.pop( memory ) ;
		value = CPU.Flag.BREAK.clear( value );
		cpu.setFlagBits( value );
	};

	protected final Runnable rol = () ->
	{
		value = getvaluereadwrite();
		result = (value << 1) | ( cpu.isSet( Flag.CARRY) ? 1 : 0 );

		carrycalc(result);
		zerocalc(result);
		signcalc(result);

		putvalue(result);
	};

	protected final Runnable ror = () ->
	{
		value = getvaluereadwrite();
		if (  cpu.isSet(Flag.CARRY ) ) {
			result = (value >> 1) | 1 << 7;
		} else {
			result = (value >> 1);
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
	};

	protected final Runnable rti = () ->
	{
		final int status = cpu.pop(memory);
		cpu.setFlagBits( (byte) status );
		value = pull16();
		cpu.pc( value );
	};

	protected final Runnable rts = () ->
	{
		value = pull16();
		cpu.pc( value + 1 );
	};

	protected final Runnable sbc = () ->
	{
		penaltyop = true;
		value = getvalue();

		int a = cpu.getAccumulator();

		if ( cpu.isSet( Flag.DECIMAL_MODE) )
		{
			// TODO: Implement BCD according to http://www.6502.org/tutorials/decimal_mode.html
		    
		    /*
In decimal mode, like binary mode, the carry (the C flag) affects the ADC and SBC instructions. Specifically:

    When the carry is clear, SBC NUM performs the calculation A = A - NUM - 1
    When the carry is set, SBC NUM performs the calculation A = A - NUM
		     
		     */
            int valueBin = bcdToBinary( value );
            if ( cpu.isNotSet( Flag.CARRY ) ) {
                valueBin++;
            }
            int accBin = bcdToBinary( a );
		    
            boolean carry = true;
            for ( int i = 0 ; i < valueBin ; i++ ) {
                accBin--;
                if ( accBin == -1 ) {
                    // after a SBC, the carry is clear if the result was less than 0 and set otherwise
                    carry = false;
                    accBin = 99;
                }
            }
	        cpu.setFlag( Flag.CARRY , carry);
	        cpu.setFlag( Flag.ZERO , accBin == 0 );
	        cpu.setFlag( Flag.NEGATIVE , (accBin & 1<<7) != 0 );
		    cpu.setAccumulator( binaryToBcd( accBin ) );
		    return;
		}

		/*
		 * ADC: Carry set   = +1 ,
		 * SBC: Carry clear = -1
		 */
		final int carry = cpu.isSet( CPU.Flag.CARRY) ? 1: 0;
		adc( a , ( ~value & 0xff ) , carry );
	};

	protected final Runnable sec = () -> {
		setcarry();
	};

	protected final Runnable sed = () -> {
		setdecimal();
	};

	protected final Runnable sei = () -> {
		setinterrupt();
	};

	protected final Runnable sta = () -> {
		putvalue( cpu.getAccumulator() );
	};

	protected final Runnable stx = () -> {
		putvalue( cpu.getX() );
	};

	protected final Runnable sty = () -> {
		putvalue( cpu.getY() );
	};

	protected final Runnable tax = () ->
	{
		cpu.setX( cpu.getAccumulator() );

		zerocalc( cpu.getX() );
		signcalc( cpu.getX() );
	};

	protected final Runnable tay = () ->
	{
		cpu.setY( cpu.getAccumulator() );

		zerocalc( cpu.getY() );
		signcalc( cpu.getY() );
	};

	protected final Runnable tsx= () ->
	{
		cpu.setX( cpu.getSP() );

		zerocalc( cpu.getX() );
		signcalc( cpu.getX() );
	};

	protected final Runnable txa = () ->
	{
		cpu.setAccumulator( cpu.getX() );

		zerocalc( cpu.getAccumulator() );
		signcalc( cpu.getAccumulator() );
	};

	protected final Runnable txs = () ->
	{
		cpu.setSP( cpu.getX() );
	};

	protected final Runnable tya = () ->
	{
		cpu.setAccumulator( cpu.getY() );

		zerocalc( cpu.getAccumulator() );
		signcalc( cpu.getAccumulator() );
	};

	//undocumented instructions
	protected final Runnable lax = () ->
	{
		lda.run();
		ldx.run();
	};

	protected final Runnable skb = () -> {};

	protected final Runnable skw = () -> {};

	protected final Runnable axs = () -> {
		sta.run();
		stx.run();
		putvalue(cpu.getAccumulator() & cpu.getX() );
		if (penaltyop && penaltyaddr) {
			cpu.cycles--;
		}
	};

	protected final Runnable dcp = () ->
	{
		dec.run();
		cmp.run();
		if (penaltyop && penaltyaddr) {
			cpu.cycles--;
		}
	};

	protected final Runnable isb = () -> {
		inc.run();
		sbc.run();
		if (penaltyop && penaltyaddr) {
			cpu.cycles--;
		}
	};

	protected final Runnable slo = () ->
	{
		asl.run();
		ora.run();
		if (penaltyop && penaltyaddr) {
			cpu.cycles--;
		}
	};

	protected final Runnable rla = () -> {
		rol.run();
		and.run();
		if (penaltyop && penaltyaddr) {
			cpu.cycles--;
		}
	};

	protected final Runnable sre = () -> {
		lsr.run();
		eor.run();
		if (penaltyop && penaltyaddr) {
			cpu.cycles--;
		}
	};

	protected final Runnable hlt = () -> {
		throw new HLTException(opcode);
	};

	protected final Runnable rra = () -> {
		ror.run();
		adc.run();
		if (penaltyop && penaltyaddr) {
			cpu.cycles--;
		}
	};

	public void executeInstruction()
	{
		final int initialPC = cpu.pc();
		
		// TODO: Remove tape debug code
		
		if ( initialPC == 0xf9aa ) {
			System.out.println("BIT: "+cpu.getX());
		} else if ( initialPC == 0xF9AC ) {
			System.out.println("Cycle too short (bit: "+memory.readByte( 0xa3 )+" , phase: "+memory.readByte( 0xa4 )+" , sync: "+memory.readByte( 0xb4 )+")" );
        } else if ( initialPC == 0xF92C ) {
            MemorySubsystem mem = (MemorySubsystem) memory;
            final CIA cia = mem.ioArea.cia1;
            final int value = ( cia.readByte( CIA.CIA_TBHI ) << 8 ) | cia.readByte( CIA.CIA_TBLO );
            System.out.println("TimerB: $"+Integer.toHexString( value ));
		} else if ( initialPC == 0xFA42 ) {
			System.out.println("SYNC established");
		} else if ( initialPC == 0xF93A ) {
			/*
$F92C  AE  07  DC    LDX $DC07       ; X = TBHi1
$F92F  A0  FF        LDY #$FF
$F931  98            TYA             ; 
$F932  ED  06  DC    SBC $DC06       ; A = complement of TBLo1 (time elapsed)
$F935  EC  07  DC    CPX $DC07       ;if high byte not steady,
$F938  D0  F2        BNE $F92C       ;repeat
$F93A  86  B1        STX $B1         ;else save high byte
$F93C  AA            TAX             ; X = $FF - TBL1			 
			 */
			System.out.println("TA1H = "+cpu.getX()+" , $FF - TA1L = "+cpu.getAccumulator());
		}

		opcode = read6502( initialPC );

		cpu.incPC();

		penaltyop = false;
		penaltyaddr = false;

		adrModeTable[opcode].run();
		optable[opcode].run();

		if ( TRACK_INS_DURATION ) 
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

		cpu.previousPC = (short) initialPC;
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
		final Runnable adrMode = adrModeTable[ opcode ];

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
		if ( adrMode == ind  ) {
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

	protected final Runnable[] adrModeTable = new Runnable[]
			{
					/*        |  0  |  1  |  2  |  3  |  4  |  5  |  6  |  7  |  8  |  9  |  A  |  B  |  C  |  D  |  E  |  F  |     */
					/* 0 */     imp, indx,  imp, indx,  igb,   zp,   zp,   zp,  imp,  imm,  acc,  imm, igw , abso, abso, abso, /* 0 */
					/* 1 */     rel, indy,  imp, indy,  igb,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, igw , absx, absx, absx, /* 1 */
					/* 2 */    abso, indx,  imp, indx,   zp,   zp,   zp,   zp,  imp,  imm,  acc,  imm, abso, abso, abso, abso, /* 2 */
					/* 3 */     rel, indy,  imp, indy,  igb,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, igw , absx, absx, absx, /* 3 */
					/* 4 */     imp, indx,  imp, indx,  igb,   zp,   zp,   zp,  imp,  imm,  acc,  imm, abso, abso, abso, abso, /* 4 */
					/* 5 */     rel, indy,  imp, indy,  igb,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, igw , absx, absx, absx, /* 5 */
					/* 6 */     imp, indx,  imp, indx,  igb,   zp,   zp,   zp,  imp,  imm,  acc,  imm,  ind, abso, abso, abso, /* 6 */
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

	protected final Runnable[] optable= new Runnable[]
			{
					/*        |  0  |  1  |  2  |  3  |  4  |  5  |  6  |  7  |  8  |  9  |  A  |  B  |  C  |  D  |  E  |  F  |      */
					/* 0 */      brk,  ora,  hlt,  slo,  skb,  ora,  asl,  slo,  php,  ora,  asl,  nop,  skw,  ora,  asl,  slo, /* 0 */
					/* 1 */      bpl,  ora,  hlt,  slo,  skb,  ora,  asl,  slo,  clc,  ora,  nop,  slo,  skw,  ora,  asl,  slo, /* 1 */
					/* 2 */      jsr,  and,  hlt,  rla,  bit,  and,  rol,  rla,  plp,  and,  rol,  nop,  bit,  and,  rol,  rla, /* 2 */
					/* 3 */      bmi,  and,  hlt,  rla,  skb,  and,  rol,  rla,  sec,  and,  nop,  rla,  skw,  and,  rol,  rla, /* 3 */
					/* 4 */      rti,  eor,  hlt,  sre,  skb,  eor,  lsr,  sre,  pha,  eor,  lsr,  nop,  jmp,  eor,  lsr,  sre, /* 4 */
					/* 5 */      bvc,  eor,  hlt,  sre,  skb,  eor,  lsr,  sre,  cli,  eor,  nop,  sre,  skw,  eor,  lsr,  sre, /* 5 */
					/* 6 */      rts,  adc,  hlt,  rra,  skb,  adc,  ror,  rra,  pla,  adc,  ror,  nop,  jmp,  adc,  ror,  rra, /* 6 */
					/* 7 */      bvs,  adc,  hlt,  rra,  skb,  adc,  ror,  rra,  sei,  adc,  nop,  rra,  skw,  adc,  ror,  rra, /* 7 */
					/* 8 */      skb,  sta,  skb,  axs,  sty,  sta,  stx,  axs,  dey,  nop,  txa,  nop,  sty,  sta,  stx,  axs, /* 8 */
					/* 9 */      bcc,  sta,  hlt,  nop,  sty,  sta,  stx,  axs,  tya,  sta,  txs,  nop,  nop,  sta,  nop,  nop, /* 9 */
					/* A */      ldy,  lda,  ldx,  lax,  ldy,  lda,  ldx,  lax,  tay,  lda,  tax,  nop,  ldy,  lda,  ldx,  lax, /* A */
					/* B */      bcs,  lda,  hlt,  lax,  ldy,  lda,  ldx,  lax,  clv,  lda,  tsx,  lax,  ldy,  lda,  ldx,  lax, /* B */
					/* C */      cpy,  cmp,  skb,  dcp,  cpy,  cmp,  dec,  dcp,  iny,  cmp,  dex,  nop,  cpy,  cmp,  dec,  dcp, /* C */
					/* D */      bne,  cmp,  hlt,  dcp,  skb,  cmp,  dec,  dcp,  cld,  cmp,  nop,  dcp,  skw,  cmp,  dec,  dcp, /* D */
					/* E */      cpx,  sbc,  skb,  isb,  cpx,  sbc,  inc,  isb,  inx,  sbc,  nop,  sbc,  cpx,  sbc,  inc,  isb, /* E */
					/* F */      beq,  sbc,  hlt,  isb,  skb,  sbc,  inc,  isb,  sed,  sbc,  nop,  isb,  skw,  sbc,  inc,  isb  /* F */
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