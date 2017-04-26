package de.codesourcery.j6502.emulator;

import de.codesourcery.j6502.emulator.CPU.Flag;
import de.codesourcery.j6502.emulator.exceptions.HLTException;
import de.codesourcery.j6502.utils.HexDump;

/**
 * Ported from C code in http://codegolf.stackexchange.com/questions/12844/emulate-a-mos-6502-cpu
 *
 * @author tobias.gierke@voipfuture.com
 */
public final class CPUImpl
{
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
		return memory.readAndWriteByte( address );
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

	protected final Runnable imm = () -> { //immediate
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
		if (addrtable[opcode] == acc) {
			return cpu.getAccumulator();
		}
		return read6502(ea);
	}

	protected int getvaluereadwrite()
	{
		if (addrtable[opcode] == acc) {
			return cpu.getAccumulator();
		}
		return readAndWrite6502(ea);
	}

	protected void putvalue(int saveval) {
		if (addrtable[opcode & 0xff] == acc)
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
		value = getvaluereadwrite();

		if ( cpu.isSet( Flag.DECIMAL_MODE) )
		{
		    // TODO: Implement BCD mode according to http://www.6502.org/tutorials/decimal_mode.html
		    throw new RuntimeException("ADC in BCD mode not implemented yet :(");
		}
		adc( cpu.getAccumulator() , value , cpu.isSet(Flag.CARRY) ? 1 : 0 );
	};

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
		value = getvaluereadwrite();
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
		value = getvaluereadwrite();

		int a = cpu.getAccumulator();

	      if ( cpu.isSet( Flag.DECIMAL_MODE) )
	      {
	          // TODO: Implement BCD according to http://www.6502.org/tutorials/decimal_mode.html
	          throw new RuntimeException("SBC in BCD mode not implemented yet :(");
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

	protected final Runnable sax = () -> {
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

		opcode = read6502( initialPC );

		cpu.incPC();

		penaltyop = false;
		penaltyaddr = false;

		addrtable[opcode].run();
		optable[opcode].run();

		cpu.cycles += ticktable[opcode];

		if (penaltyop && penaltyaddr)
		{
			cpu.cycles++;
		}

	    cpu.previousPC = (short) initialPC;
	}

	protected final Runnable[] addrtable = new Runnable[]
			{
					/*        |  0  |  1  |  2  |  3  |  4  |  5  |  6  |  7  |  8  |  9  |  A  |  B  |  C  |  D  |  E  |  F  |     */
					/* 0 */     imp, indx,  imp, indx,   zp,   zp,   zp,   zp,  imp,  imm,  acc,  imm, abso, abso, abso, abso, /* 0 */
					/* 1 */     rel, indy,  imp, indy,  zpx,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, absx, absx, absx, absx, /* 1 */
					/* 2 */    abso, indx,  imp, indx,   zp,   zp,   zp,   zp,  imp,  imm,  acc,  imm, abso, abso, abso, abso, /* 2 */
					/* 3 */     rel, indy,  imp, indy,  zpx,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, absx, absx, absx, absx, /* 3 */
					/* 4 */     imp, indx,  imp, indx,   zp,   zp,   zp,   zp,  imp,  imm,  acc,  imm, abso, abso, abso, abso, /* 4 */
					/* 5 */     rel, indy,  imp, indy,  zpx,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, absx, absx, absx, absx, /* 5 */
					/* 6 */     imp, indx,  imp, indx,   zp,   zp,   zp,   zp,  imp,  imm,  acc,  imm,  ind, abso, abso, abso, /* 6 */
					/* 7 */     rel, indy,  imp, indy,  zpx,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, absx, absx, absx, absx, /* 7 */
					/* 8 */     imm, indx,  imm, indx,   zp,   zp,   zp,   zp,  imp,  imm,  imp,  imm, abso, abso, abso, abso, /* 8 */
					/* 9 */     rel, indy,  imp, indy,  zpx,  zpx,  zpy,  zpy,  imp, absy,  imp, absy, absx, absx, absy, absy, /* 9 */
					/* A */     imm, indx,  imm, indx,   zp,   zp,   zp,   zp,  imp,  imm,  imp,  imm, abso, abso, abso, abso, /* A */
					/* B */     rel, indy,  imp, indy,  zpx,  zpx,  zpy,  zpy,  imp, absy,  imp, absy, absx, absx, absy, absy, /* B */
					/* C */     imm, indx,  imm, indx,   zp,   zp,   zp,   zp,  imp,  imm,  imp,  imm, abso, abso, abso, abso, /* C */
					/* D */     rel, indy,  imp, indy,  zpx,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, absx, absx, absx, absx, /* D */
					/* E */     imm, indx,  imm, indx,   zp,   zp,   zp,   zp,  imp,  imm,  imp,  imm, abso, abso, abso, abso, /* E */
					/* F */     rel, indy,  imp, indy,  zpx,  zpx,  zpx,  zpx,  imp, absy,  imp, absy, absx, absx, absx, absx  /* F */
			};

	protected final Runnable[] optable= new Runnable[]
			{
					/*        |  0  |  1  |  2  |  3  |  4  |  5  |  6  |  7  |  8  |  9  |  A  |  B  |  C  |  D  |  E  |  F  |      */
					/* 0 */      brk,  ora,  hlt,  slo,  nop,  ora,  asl,  slo,  php,  ora,  asl,  nop,  nop,  ora,  asl,  slo, /* 0 */
					/* 1 */      bpl,  ora,  hlt,  slo,  nop,  ora,  asl,  slo,  clc,  ora,  nop,  slo,  nop,  ora,  asl,  slo, /* 1 */
					/* 2 */      jsr,  and,  hlt,  rla,  bit,  and,  rol,  rla,  plp,  and,  rol,  nop,  bit,  and,  rol,  rla, /* 2 */
					/* 3 */      bmi,  and,  hlt,  rla,  nop,  and,  rol,  rla,  sec,  and,  nop,  rla,  nop,  and,  rol,  rla, /* 3 */
					/* 4 */      rti,  eor,  hlt,  sre,  nop,  eor,  lsr,  sre,  pha,  eor,  lsr,  nop,  jmp,  eor,  lsr,  sre, /* 4 */
					/* 5 */      bvc,  eor,  hlt,  sre,  nop,  eor,  lsr,  sre,  cli,  eor,  nop,  sre,  nop,  eor,  lsr,  sre, /* 5 */
					/* 6 */      rts,  adc,  hlt,  rra,  nop,  adc,  ror,  rra,  pla,  adc,  ror,  nop,  jmp,  adc,  ror,  rra, /* 6 */
					/* 7 */      bvs,  adc,  hlt,  rra,  nop,  adc,  ror,  rra,  sei,  adc,  nop,  rra,  nop,  adc,  ror,  rra, /* 7 */
					/* 8 */      nop,  sta,  nop,  sax,  sty,  sta,  stx,  sax,  dey,  nop,  txa,  nop,  sty,  sta,  stx,  sax, /* 8 */
					/* 9 */      bcc,  sta,  hlt,  nop,  sty,  sta,  stx,  sax,  tya,  sta,  txs,  nop,  nop,  sta,  nop,  nop, /* 9 */
					/* A */      ldy,  lda,  ldx,  lax,  ldy,  lda,  ldx,  lax,  tay,  lda,  tax,  nop,  ldy,  lda,  ldx,  lax, /* A */
					/* B */      bcs,  lda,  hlt,  lax,  ldy,  lda,  ldx,  lax,  clv,  lda,  tsx,  lax,  ldy,  lda,  ldx,  lax, /* B */
					/* C */      cpy,  cmp,  nop,  dcp,  cpy,  cmp,  dec,  dcp,  iny,  cmp,  dex,  nop,  cpy,  cmp,  dec,  dcp, /* C */
					/* D */      bne,  cmp,  hlt,  dcp,  nop,  cmp,  dec,  dcp,  cld,  cmp,  nop,  dcp,  nop,  cmp,  dec,  dcp, /* D */
					/* E */      cpx,  sbc,  nop,  isb,  cpx,  sbc,  inc,  isb,  inx,  sbc,  nop,  sbc,  cpx,  sbc,  inc,  isb, /* E */
					/* F */      beq,  sbc,  hlt,  isb,  nop,  sbc,  inc,  isb,  sed,  sbc,  nop,  isb,  nop,  sbc,  inc,  isb  /* F */
			};

	protected static final int[] ticktable= new int[]
			{
					/*        |  0  |  1  |  2  |  3  |  4  |  5  |  6  |  7  |  8  |  9  |  A  |  B  |  C  |  D  |  E  |  F  |     */
					/* 0 */      7,    6,    2,    8,    3,    2,    5,    5,    3,    2,    2,    2,    4,    4,    6,    6,  /* 0 */
					/* 1 */      2,    5,    2,    8,    4,    3,    6,    6,    2,    4,    2,    7,    4,    4,    7,    7,  /* 1 */
					/* 2 */      6,    6,    2,    8,    3,    2,    5,    5,    4,    2,    2,    2,    4,    4,    6,    6,  /* 2 */
					/* 3 */      2,    5,    2,    8,    4,    3,    6,    6,    2,    4,    2,    7,    4,    4,    7,    7,  /* 3 */
					/* 4 */      6,    6,    2,    8,    3,    3,    5,    5,    3,    2,    2,    2,    3,    4,    6,    6,  /* 4 */
					/* 5 */      2,    5,    2,    8,    4,    4,    6,    6,    2,    4,    2,    7,    4,    4,    7,    7,  /* 5 */
					/* 6 */      6,    6,    2,    8,    3,    3,    5,    5,    4,    2,    2,    2,    5,    4,    6,    6,  /* 6 */
					/* 7 */      2,    5,    2,    8,    4,    4,    6,    6,    2,    4,    2,    7,    4,    4,    7,    7,  /* 7 */
					/* 8 */      2,    6,    2,    6,    3,    3,    3,    3,    2,    2,    2,    2,    4,    4,    4,    4,  /* 8 */
					/* 9 */      2,    6,    2,    6,    4,    4,    4,    4,    2,    5,    2,    5,    5,    5,    5,    5,  /* 9 */
					/* A */      2,    6,    2,    6,    3,    3,    3,    3,    2,    2,    2,    2,    4,    4,    4,    4,  /* A */
					/* B */      2,    5,    2,    5,    4,    4,    4,    4,    2,    4,    2,    4,    4,    4,    4,    4,  /* B */
					/* C */      2,    6,    2,    8,    3,    3,    5,    5,    2,    2,    2,    2,    4,    4,    6,    6,  /* C */
					/* D */      2,    5,    2,    8,    4,    4,    6,    6,    2,    4,    2,    7,    4,    4,    7,    7,  /* D */
					/* E */      2,    6,    2,    8,    3,    3,    5,    5,    2,    2,    2,    2,    4,    4,    6,    6,  /* E */
					/* F */      2,    5,    2,    8,    4,    4,    6,    6,    2,    4,    2,    7,    4,    4,    7,    7   /* F */
			};
}