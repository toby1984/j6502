

*=$c000

main:
      JSR init
      JSR install_isr
forever:
      JMP forever      

; ============================
; Setup sprites
; ============================
init:
; screen memory in first 1024 bytes of the current bank ($8000-$83e8)
; => sprite pointers start at $07f8
; => sprite #0 data starts at 128 * 64 = $2000
      LDX #0
      LDA #128
set_ptrs:
      STA $07f8,X
      CLC
      ADC #1
      INX
      CPX #8
      BNE set_ptrs
; === copy sprites ===
      LDA #<sprite0
      STA $fb
      LDA #>sprite0
      STA $fc
      LDA #<$2000
      STA $fd
      LDA #>$2000
      STA $fe
      LDX #8 ; 8 sprites to copy
copy_sprites:
      LDY #63
copy_one_sprite:
      LDA ($fb),Y
;      LDA #$ff
      EOR #$ff
      STA ($fd),Y
      DEY
      BPL copy_one_sprite
; inc src ptr
      LDA $fb
      CLC
      ADC #64
      STA $fb
      LDA $fc
      ADC #0
      STA $fc
; inc dst ptr
      LDA $fd
      CLC
      ADC #64
      STA $fd
      LDA $fe
      ADC #0
      STA $fe
;
      DEX
      BNE copy_sprites
; setup colors
      LDA #8
      LDX #8
set_colors:
      STA $d027,X
      CLC
      ADC #1
      DEX
      BNE set_colors
; enable all sprites
      LDA #$ff
;      LDA #%00000011
      STA $D015
; background in front of sprites
      LDA #$ff
      STA $D01b
      RTS

; ================
; Register interrupt handler
; ================

install_isr:
        SEI   
; disable kernal rom so that
; CPU can actually see the
; updated IRQ vectors
        LDA #$35
        STA $01
; avoid stray CIA interrupts
        lda #$7f
        sta $dc0d 
        sta $dd0d

        lda $dc0d 
        lda $dd0d 
; update IRQ vector
        LDA #<isr
        STA $fffe
        LDA #>isr
        STA $ffff
; configure VIC raster IRQ in line 298
        LDA #<298
        STA $d012 ; set lsb
        LDA $d011
        ORA #%10000000
        STA $d011 ; set msb (bit 9)
; activate VIC interrupts
        LDA $d01a
        ORA #%111
        STA $d01a
; resume irqs
        CLI
        RTS

; ================
; ISR
; ================
isr:
        PHA ; push accu
        TXA
        PHA ; push X
        TYA
        PHA ; push Y

        LDA #%10000000 ; check for global VIC
        BIT $d019 ; check interrupt flag register
        BNE vic_irq
; no VIC irq
        LDA #1
        STA $d020
        JMP end_irq
; VIC irq
vic_irq
        LDA #%00000001
        BIT $d019 ; check interrupt flag register
        BEQ skip1
        JSR raster_irq
skip1:
        LDA #%00000010
        BIT $d019 ; check interrupt flag register
        BEQ skip2
        JSR sprite_bg_irq        
skip2:
        LDA #%00000100
        BIT $d019 ; check interrupt flag register
        BEQ skip3
        JSR sprite_sprite_irq      
skip3:
        STX $d020
reset_irq_flags:
; clear VIC interrupt flags
        LDA $d019
        STA $d019
end_irq:
; END IRQ HANDLING
        PLA ; pop Y
        TAY
        PLA ; pop X
        TAX
        PLA ; pop accu
        RTI

; ===================
; VIC raster IRQ handler
; ===================
raster_irq:
        JSR move
        LDX #1
        RTS

; ===================
; VIC sprite-bg IRQ handler
; ===================
sprite_bg_irq:
        LDX #3        
        RTS

; ===================
; VIC sprite-sprite IRQ handler
; ===================
sprite_sprite_irq:
        LDX #2
        RTS

; ============================
; Move sprites
; ============================
move:
      LDX #0
      LDY #8
loop:
; ==== Update X coordinate ====
      LDA coords,X
      CLC
      ADC deltas,x
      STA coords,X
      CMP #24
      BNE not_left
      LDA #1
      STA deltas,x
      JMP not_right
not_left:
      CMP #255
      BNE not_right
      LDA #-1
      STA deltas,x
not_right:
      INX
; ==== Update Y coordinate ====
      LDA coords,X
      CLC
      ADC deltas,X
      STA coords,X
      CMP #50
      BNE not_top
      LDA #1
      STA deltas,x
      JMP not_bottom
not_top:
      CMP #179
      BNE not_bottom
      LDA #-1
      STA deltas,x
not_bottom:
      INX
      DEY
      BNE loop
; == copy coordinates ==
      LDX #0
copyloop:
      LDA coords,X
      STA $d000,X
      INX
      CPX #16
      BNE copyloop
      rts

coords:
    .byte 10,10,40,40,70,70,100,100,130,130,160,160,190,190,220,220
deltas:
    .byte 1,1,-1,1,1,-1,-1,-1,1,1,-1,1,1,-1,-1,-1

sprite0:
.byte $00,$7f,$c0,$00,$40,$40,$00,$40,$40,$00,$40,$40,$00,$40,$40,$00
.byte $40,$40,$00,$40,$40,$00,$40,$40,$00,$40,$40,$00,$40,$40,$00,$40
.byte $40,$00,$40,$40,$00,$40,$40,$00,$40,$40,$00,$40,$40,$00,$40,$40
.byte $00,$40,$40,$00,$40,$40,$00,$40,$40,$00,$40,$40,$00,$7f,$c0,$00 ; '0'
sprite1:
.byte $00,$0c,$00,$00,$1c,$00,$00,$3c,$00,$00,$6c,$00,$00,$cc,$00,$01
.byte $8c,$00,$01,$0c,$00,$00,$0c,$00,$00,$0c,$00,$00,$0c,$00,$00,$0c
.byte $00,$00,$0c,$00,$00,$0c,$00,$00,$0c,$00,$00,$0c,$00,$00,$0c,$00
.byte $00,$0c,$00,$00,$0c,$00,$00,$0c,$00,$00,$0c,$00,$00,$0c,$00,$00 ; '1'
sprite2:
.byte $00,$fe,$00,$00,$03,$00,$00,$01,$00,$00,$01,$00,$00,$01,$00,$00
.byte $01,$00,$00,$01,$00,$00,$01,$00,$00,$03,$00,$00,$02,$00,$00,$06
.byte $00,$00,$04,$00,$00,$0c,$00,$00,$08,$00,$00,$18,$00,$00,$30,$00
.byte $00,$60,$00,$00,$40,$00,$00,$c0,$00,$01,$80,$00,$01,$ff,$00,$00 ; '2'
sprite3:
.byte $00,$fc,$00,$00,$07,$00,$00,$01,$00,$00,$01,$80,$00,$00,$80,$00
.byte $00,$80,$00,$01,$80,$00,$01,$00,$00,$03,$00,$00,$fe,$00,$00,$fe
.byte $00,$00,$ff,$00,$00,$01,$00,$00,$01,$00,$00,$01,$00,$00,$01,$00
.byte $00,$01,$00,$00,$03,$00,$00,$06,$00,$00,$0c,$00,$00,$f8,$00,$00 ; '3'
sprite4:
.byte $00,$88,$00,$00,$88,$00,$00,$88,$00,$00,$88,$00,$00,$88,$00,$00
.byte $88,$00,$00,$88,$00,$00,$88,$00,$00,$88,$00,$00,$88,$00,$00,$88
.byte $00,$00,$88,$00,$00,$ff,$00,$00,$08,$00,$00,$08,$00,$00,$08,$00
.byte $00,$08,$00,$00,$08,$00,$00,$08,$00,$00,$08,$00,$00,$00,$00,$00 ; '4'
sprite5:
.byte $00,$ff,$00,$00,$ff,$00,$00,$c0,$00,$00,$c0,$00,$00,$c0,$00,$00
.byte $c0,$00,$00,$c0,$00,$00,$f8,$00,$00,$fc,$00,$00,$0e,$00,$00,$07
.byte $00,$00,$03,$00,$00,$03,$00,$00,$01,$00,$00,$01,$00,$00,$01,$00
.byte $00,$01,$00,$00,$01,$00,$00,$03,$00,$00,$1e,$00,$00,$f0,$00,$00 ; '5'
sprite6:
.byte $00,$0e,$00,$00,$18,$00,$00,$30,$00,$00,$20,$00,$00,$60,$00,$00
.byte $40,$00,$00,$80,$00,$00,$80,$00,$00,$80,$00,$00,$80,$00,$01,$f8
.byte $00,$01,$0c,$00,$01,$06,$00,$01,$02,$00,$01,$03,$00,$01,$01,$00
.byte $01,$81,$00,$00,$81,$00,$00,$c1,$00,$00,$73,$00,$00,$1e,$00,$00 ; '6'
sprite7:
.byte $00,$ff,$c0,$00,$00,$40,$00,$00,$c0,$00,$00,$80,$00,$00,$80,$00
.byte $01,$80,$00,$01,$00,$00,$03,$00,$00,$02,$00,$00,$06,$00,$00,$3f
.byte $80,$00,$0c,$00,$00,$08,$00,$00,$18,$00,$00,$10,$00,$00,$10,$00
.byte $00,$30,$00,$00,$60,$00,$00,$40,$00,$00,$c0,$00,$00,$80,$00,$00 ; '7'



















                            