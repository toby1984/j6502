package de.codesourcery.j6502.assembler;

public enum AddressingMode
{
	/**
	 * LDA ($44,X)
	 */
	INDEXED_INDIRECT_X, // 000	(zero page,X)
	/**
	 * LDA $44
	 */
	ZERO_PAGE, // 001	zero page
	/**
	 * LDA #$44
	 */
	IMMEDIATE, // 010	#immediate
	/**
	 * LDA $0D00
	 */
	ABSOLUTE,	// 011	absolute
	/**
	 * LDA ( $44 ) , Y
	 */
	INDIRECT_INDEXED_Y, // 100	(zero page),Y
	/**
	 * LDA $44 , X
	 */
	ZERO_PAGE_X, // 101	zero page,X
	/**
	 * LDA $4400,Y
	 */
	ABSOLUTE_INDEXED_Y, // 110	absolute,Y
	/**
	 * LDA $da00,X
	 */
	ABSOLUTE_INDEXED_X; // 111	absolute,X
}