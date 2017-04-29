package de.codesourcery.j6502.emulator.exceptions;

public class HLTException extends RuntimeException {

	public HLTException(int opcode) {
		super("CPU halted, executed HLT instruction (opcode: $"+Integer.toHexString(opcode)+")");
	}
	
    public HLTException() {
        super("CPU executed HLT instruction");
    }	
}
