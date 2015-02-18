package de.codesourcery.j6502.assembler.parser;

public final class Scanner {

	private final String input;
	private int index;

	public Scanner(String input) {
		this.input = input;
	}

	public boolean eof() {
		return index >= input.length();
	}

	public int currentOffset() {
		return index;
	}

	private void assertNotEOF() {
		if ( eof() ) {
			throw new IllegalStateException("Already at EOF");
		}
	}

	public char peek() {
		assertNotEOF();
		return input.charAt(index);
	}

	public char next() {
		assertNotEOF();
		return input.charAt(index++);
	}
}
