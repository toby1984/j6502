; Demonstrate that the V flag works as described
;
; Returns with $a000 = 0 if the test passes, $a000 = 1 if the test fails
;
; Five (additional) memory locations are used: $a000, $a000, $a001, $a002, and $a003
; which can be located anywhere convenient in RAM
;
;
; ERROR .equ $a000
; S1  .equ $a000
; S2  .equ $a001
; U1  .equ $a002
; U2  .equ $a003

 *=$1000
TEST: CLD       ; Clear decimal mode (just in case) for test
     LDA #1
     STA $a000 ; Store 1 in $a000 until test passes
     LDA #$80
     STA $a000    ; Initalize $a000 and $a001 to -128 ($80)
     STA $a001
     LDA #0
     STA $a002    ; Initialize $a002 and $a003 to 0
     STA $a003
     LDY #1    ; Initialize Y (used to set and clear the carry flag) to 1
LOOP: JSR ADD   ; Test ADC
     CPX #1
     BEQ DONE  ; End if V and unsigned result do not agree (X = 1)
     JSR SUB   ; Test SBC
     CPX #1
     BEQ DONE  ; End if V and unsigned result do not agree (X = 1)
     INC $a000
     INC $a002
     BNE LOOP  ; Loop until all 256 possibilities of $a000 and $a002 are tested
     INC $a001
     INC $a003
     BNE LOOP  ; Loop until all 256 possibilities of $a001 and $a003 are tested
     DEY
     BPL LOOP  ; Loop until both possiblities of the carry flag are tested
     LDA #0
     STA $a000 ; All tests pass, so store 0 in $a000
DONE: JMP DONE 
;
; Test ADC
;
; X is initialized to 0
; X is incremented when V = 1
; X is incremented when the unsigned result predicts an overflow
; Therefore, if the V flag and the unsigned result agree, X will be
; incremented zero or two times (returning X = 0 or X = 2), and if they do
; not agree X will be incremented once (returning X = 1)
;
ADD:  CPY #1   ; Set carry when Y = 1, clear carry when Y = 0
     LDA $a000   ; Test twos complement addition
     ADC $a001
     LDX #0   ; Initialize X to 0
     BVC ADD1
     INX      ; Increment X if V = 1
ADD1: CPY #1   ; Set carry when Y = 1, clear carry when Y = 0
     LDA $a002   ; Test unsigned addition
     ADC $a003
     BCS ADD3 ; Carry is set if $a002 + $a003 >= 256
     BMI ADD2 ; $a002 + $a003 < 256, A >= 128 if $a002 + $a003 >= 128
     INX      ; Increment X if $a002 + $a003 < 128
ADD2: RTS
ADD3: BPL ADD4 ; $a002 + $a003 >= 256, A <= 127 if $a002 + $a003 <= 383 ($17F)
     INX      ; Increment X if $a002 + $a003 > 383
ADD4: RTS
;
; Test SBC
;
; X is initialized to 0
; X is incremented when V = 1
; X is incremented when the unsigned result predicts an overflow
; Therefore, if the V flag and the unsigned result agree, X will be
; incremented zero or two times (returning X = 0 or X = 2), and if they do
; not agree X will be incremented once (returning X = 1)
;
SUB:  CPY #1   ; Set carry when Y = 1, clear carry when Y = 0
     LDA $a000   ; Test twos complement subtraction
     SBC $a001
     LDX #0   ; Initialize X to 0
     BVC SUB1
     INX      ; Increment X if V = 1
SUB1: CPY #1   ; Set carry when Y = 1, clear carry when Y = 0
     LDA $a002   ; Test unsigned subtraction
     SBC $a003
     PHA      ; Save the low byte of result on the stack
     LDA #$FF
     SBC #$00 ; result = (65280 + $a002) - $a003, 65280 = $FF00
     CMP #$FE
     BNE SUB4 ; Branch if result >= 65280 ($FF00) or result < 65024 ($FE00)
     PLA      ; Get the low byte of result
     BMI SUB3 ; result < 65280 ($FF00), A >= 128 if result >= 65152 ($FE80)
SUB2: INX      ; Increment X if result < 65152 ($FE80)
SUB3: RTS
SUB4: PLA      ; Get the low byte of result (does not affect the carry flag)
     BCC SUB2 ; The carry flag is clear if result < 65024 ($FE00)
     BPL SUB5 ; result >= 65280 ($FF00), A <= 127 if result <= 65407 ($FF7F)
     INX      ; Increment X if result > 65407 ($FF7F)
SUB5: RTS
