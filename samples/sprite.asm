SCREENRAMADR .equ $0400
colorram .equ $d800
 
;*** Startadresse BASIC-Zeile

*=$c000
 
main:
; 10 poke 2040,13
LDA #13
STA $7f8

; 20 for i=0 to 62:read a:poke 832+i,a:next i
   LDX #0
loop:
   LDA spriteData,X
   STA $340,x
   INX
   CPX #63
   BNE loop
   
; set color sprite #0
LDA #1
STA $d027 
; enable sprite #0
LDA #1
STA $d015 
; set X coordinate sprite #0
LDA #0
STA $d000
; set y coordinate sprite #0
LDA #0
STA $d001
; clear MSB X coordinate sprite #0
LDA #0
STA $d010
RTS

spriteData:

.byte 0,126,0
.byte 3,255,192
.byte 7,255,224
.byte 31,255,248
.byte 28,255,56
.byte 62,255,124
.byte 127,247,254
.byte 127,247,254
.byte 255,251,255
.byte 255,253,255
.byte 255,253,255
.byte 255,243,255
.byte 255,255,255
.byte 127,255,254
.byte 127,255,254
.byte 63,126,252
.byte 31,189,248
.byte 31,195,248
.byte 7,255,224
.byte 3,255,192
.byte 0,126,0
                               