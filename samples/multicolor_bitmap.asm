VICBANKNO               .equ 0                             ;Nr. (0 - 3) der 16KB Bank                              | Standard: 0
VICSCREENBLOCKNO        .equ 1                             ;Nr. (0 -15) des 1KB-Blocks für den Textbildschirm      | Standard: 1
VICCHARSETBLOCKNO       .equ 2                             ;Nr. (0 - 7) des 2KB-Blocks für den Zeichensatz         | Standard: 2
VICBITMAPBBLOCKNO       .equ 1                             ;Nr. (0 - 1) des 8KB-Blocks für die BITMAP-Grafik       | Standard: 0
VICBASEADR              .equ VICBANKNO*16384               ;Startadresse der gewählten VIC-Bank                    | Standard: $0000
; VICCHARSETADR           .equ VICCHARSETBLOCKNO*2048        ;Adresse des Zeichensatzes                              | Standard: $1000 ($D000)
VICCHARSETADR           .equ ( VICCHARSETBLOCKNO * 2048 ) + VICBASEADR
; VICSCREENRAM            .equ          ;Adresse des Bildschirmspeichers
VICSCREENRAM            .equ (VICSCREENBLOCKNO*1024) +VICBASEADR
; VICBITMAPADR            .equ VICBITMAPBBLOCKNO * 8192      ;Adresse der BITMAP
VICBITMAPADR            .equ (VICBITMAPBBLOCKNO * 8192 ) + VICBASEADR
VICCOLORRAM             .equ $D800
 
;*** Startadresse BASIC-Zeile
*=$b600
 
main
 ;Bitmap-Modus aktivieren
 lda $D011                      ;VIC-II Register 17 in den Akku
 ora #%00100000                 ;Bitmap-Modus
 sta $D011                      ;aktivieren
 
 lda $D016                      ;VIC-II Register 22 in den Akku
 ora #%00010000                 ;Multi-Color über BIT-4
 sta $D016                      ;aktivieren
 
 ;*** Start des Bitmapspeichers festlegen
 lda $D018                      ;VIC-II Register 24 in den Akku holen
 and #%11110111                 ;Mit BIT-3
 ora #VICBITMAPBBLOCKNO*8       ;den Beginn des
 sta $D018                      ;Bitmapspeichers festlegen
 
 jsr copyimg
 jsr showKoala                  ;Koala-Bild anzeigen
 
forever:
 jmp forever                         ;Endlosschleife
 
;*** Unkomprimiertes Koalabild anzeigen
;*** Offset (hex): Inhalt
;*** 0000 - 1F3F : Bitmap 8000 Bytes
;*** 1F40 - 2327 : Bildschirmspeicher 1000 Bytes
;*** 2328 - 270F : Farb-RAM 1000 Bytes
;*** 2710        : Hintergrundfarbe 1 Byte
showKoala:
 ldx #$00
loop:
 lda VICBITMAPADR+$1F40,X       ;Farbe für Bildschirm-Speicher lesen
 sta VICSCREENRAM,X             ;und schreiben
 lda VICBITMAPADR+$2328,X       ;Farbe für COLOR-RAM lesen
 sta VICCOLORRAM,X              ;und schreiben
 lda VICBITMAPADR+$2040,X       ;für die nächsten drei Pages wiederholen
 sta VICSCREENRAM+256,X
 lda VICBITMAPADR+$2428,X
 sta VICCOLORRAM+256,X
 lda VICBITMAPADR+$2140,X
 sta VICSCREENRAM+512,X
 lda VICBITMAPADR+$2528,X
 sta VICCOLORRAM+512,X
 lda VICBITMAPADR+$2240,X
 sta VICSCREENRAM+768,X
 lda VICBITMAPADR+$2628,X
 sta VICCOLORRAM+768,X
 dex                            ;Schleifenzähler verringern
 bne loop                      ;solange ungleich 0 -> @loop
 lda VICBITMAPADR+2710          ;Hintergrundfarbe holen
 sta $D021                      ;und setzen
 rts                            ;zurück zum Aufrufer
 
copyimg:
; src
   lda #<imagestart
   sta  $fc
   lda #>imagestart
   sta $fe    
   lda #<$2000
   sta $fe
   lda #>$2000
   sta $ff
   ldy #0
copyloop:
   lda ($fc),y
   sta ($fe),y
   inc $fc
   bne noover1
   inc $fd
noover1:
   inc $fe
   bne noover2
   inc $ff
noover2:
; decrease byte count
   dec imagelen
   bne noover3
   dec imagelen+1
   bne copyloop   
   rts
    
imagestart:
 .incbin "/home/tobi/tmp/Pic0.koa"              ;Bild einbinden    
imageend:

imagelen:
   .word imageend-imagestart



                