SCREENRAMADR .equ $0400
colorram .equ $d800
 
;*** Startadresse BASIC-Zeile

*=$c000
 
main:
 lda #$00                       ;schwarz für
 sta $D021                      ;Hintergrund
 lda #$0E                       ;Hellblau
 sta $286                       ;als Zeichen-
 sta $D020                      ;und Rahmenfarbe
 lda #$05                       ;grün
 sta $D022                      ;für %01 im Zeichen
 lda #$02                       ;rot
 sta $D023                      ;für %10 im Zeichen
 lda $D016
 ora #%00010000                 ;Multicolor-Textmodus aktivieren
 sta $D016
 lda #$93                       ;Bildschirm über
 jsr $FFD2                      ;Kernalfunktion löschen
 jsr printtext                  ;Text ausgeben
 rts                            ;zurück zum BASIC
 
printtext:
 ldx #$00                       ;Nächstes Zeichen in X (0 = erstes)
.loop
 lda outtext,x                  ;Zeichen in den Akku
 beq exit                      ;0 = Textende -> @exit
 sta SCREENRAMADR,x             ;Zeichen ausgeben
 lda #$fe
 sta colorram,x
 inx                            ;Position für nächstes Zeichen erhöhen
 jmp loop                      ;nochmal
.exit
 rts                            ;zurück zum Aufrufer
 
outtext
 .byte 2,12,21,2,2
 .byte $00                       ;Textend            