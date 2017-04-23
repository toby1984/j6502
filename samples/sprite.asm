SCREENRAMADR .equ $0400
colorram .equ $d800
 
;*** Startadresse BASIC-Zeile

*=$c000
 
main:
; 10 poke 2040,13
LDA #13
STA 2040

; 20 for i=0 to 62:read a:poke 832+i,a:next i
   LDX #0
loop:
   LDA spriteData,X
   STA 832,x
   INX
   CPX #63
   BNE loop
   
; 30 poke 53287,1
LDA #1
STA 53287
; 40 poke 53269,1
LDA #1
STA 53269
; 50 poke 53248,160
LDA #160
STA 53248
; 60 poke 53249,100
LDA #100
STA 53249
; 70 poke 53264,0
LDA #0
STA 53264
RTS

spriteData:

.byte 1,255,192
.byte 7,0,96
.byte 12,0,48
.byte 8,0,28
.byte 24,0,4
.byte 16,34,6
.byte 32,0,2
.byte 32,0,2
.byte 32,0,2
.byte 32,0,2
.byte 32,4,2
.byte 35,0,68
.byte 49,129,132
.byte 24,254,8
.byte 8,0,24
.byte 4,0,48
.byte 3,0,96
.byte 1,128,192
.byte 0,249,128
.byte 0,7,0
.byte 0,0,0
                   