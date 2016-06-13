 Interrupt-Routine für Band lesen
 
 ; $B1 => speed correction factor
 ; $A3 => Bit counter
 ; $B4 => Aktuelle IRQ mask für Timer A , entweder $81 (=> Timer A Underflow IRQ enabled) oder 0 (Timer A IRQ disabled)

.,F92C AE 07 DC LDX $DC07       Timer B HIGH laden
.,F92F A0 FF    LDY #$FF        Y-Register mit $FF laden (um verstrichene Cycles zu berechnen)
.,F931 98       TYA             in Akku schieben
.,F932 ED 06 DC SBC $DC06       Timer B von $FF abziehen
.,F935 EC 07 DC CPX $DC07       Timer B mit altem Wert vergleichen
.,F938 D0 F2    BNE $F92C       verzweige, falls vermindert
.,F93A 86 B1    STX $B1         Timer B HIGH ablegen
.,F93C AA       TAX             und in Akku schieben
.,F93D 8C 06 DC STY $DC06       Timer B LOW und
.,F940 8C 07 DC STY $DC07       Timer B HIGH auf $FF setzen
.,F943 A9 19    LDA #$19        One-shot mode für Timer B
.,F945 8D 0F DC STA $DC0F       festlegen und starten
.,F948 AD 0D DC LDA $DC0D       Interrupt Control Register
.,F94B 8D A3 02 STA $02A3       laden und nach $02A3
.,F94E 98       TYA             Y-REG in Akku ($FF)
.,F94F E5 B1    SBC $B1         Errechnung von vergangener Zeit seit letzter Flanke
.,F951 86 B1    STX $B1         vergangene Zeit LOW nach $B1
.,F953 4A       LSR             vergangene Zeit
.,F954 66 B1    ROR $B1         (lo & hi)
.,F956 4A       LSR             geteilt
.,F957 66 B1    ROR $B1         durch vier

; Default timing thresholds at start of algorithm (speed constant $B0 contains 0)
; no. 1 => 240 cycles
;       => short pulse = 336 cycles
; no. 2 => 432 cycles
        => medium pulse = 508 cycles
; no. 3 => 584 cycles
        => long pulse = 672 cycles
; no. 4 => 760 cycles
;
; threshold check #1
.,F959 A5 B0    LDA $B0         Timingkonstante laden
.,F95B 18       CLC             und mit
.,F95C 69 3C    ADC #$3C        $3C addiert (=$3c*4 = 240 cycles)
.,F95E C5 B1    CMP $B1         verstrichene Zeite größer als die Zeit bei letzten Flanken ?
.,F960 B0 4A    BCS $F9AC       jump if elapsed <= threshold , Default am Start des Algorithmus ist 240 cycles (B1 = 0 , 4*$3c = 240 cycles )

.,F962 A6 9C    LDX $9C         Anzahl empfangener Bytes laden
.,F964 F0 03    BEQ $F969       verzweige falls Null (No kein start-of-byte marker gefunden)

; mindestens ein start-of-byte marker gefunden und pulse kürzer als threshold #1
.,F966 4C 60 FA JMP $FA60       

; -----
; $B1 contains low-byte of (elapsed cycles/4)

.,F969 A6 A3    LDX $A3         Byte vollständig gelesen
.,F96B 30 1B    BMI $F988       verzweige, falls ja

.,F96D A2 00    LDX #$00        X = 0 => Angenommen es wurde ein short pulse empfangen

; Accu ist hier: ($B0) timing constant + $3C
; threshold check #2
.,F96F 69 30    ADC #$30        zu errechneter Zeit mit $30 
.,F971 65 B0    ADC $B0         und mit Zeitkonstante addieren

; Accu ist jetzt: 2 * ($B0) timing constant + $3C + $30 =432 cycles
; threshold check #3
.,F973 C5 B1    CMP $B1         elapsed < threshold ?
; --------------
; SHORT PULSE DETECTED
; --------------
.,F975 B0 1C    BCS $F993       ja, SHORT PULSE RECEIVED (elapsed < threshold) , X = 0
; 
.,F977 E8       INX             X = 1 => medium pulse empfangen

; threshold check #4
.,F978 69 26    ADC #$26        und wieder $26 ($26*4 = +152 cycles) zu
.,F97A 65 B0    ADC $B0         Zeitkonstanten addieren
; Accu ist jetzt: 3 * ($B0) timing constant + $3C + $30 + $26 = 584 cycles
.,F97C C5 B1    CMP $B1         elapsed < threshold ?
; --------------
; MEDIUM PULSE DETECTED
; --------------
.,F97E B0 17    BCS $F997       (elapsed < threshold) , MEDIUM PULSE RECEIVED , X = 1

;; threshold check #5
.,F980 69 2C    ADC #$2C        sonst wieder $2C ($2c*4 = +176 cycles) zu
.,F982 65 B0    ADC $B0         Zeitkonstante addieren
; Accu ist jetzt: 4 * ($B0) timing constant + $3C + $30 + $26 + $2c =760 cycles
.,F984 C5 B1    CMP $B1         vergangene Zeit noch länger ?
.,F986 90 03    BCC $F98B       jump if elapsed > threshold ==> TIMEOUT ERROR

; --------------
; LONG PULSE DETECTED
; --------------
.,F988 4C 10 FA JMP $FA10       LONG PULSE RECEIVED (start of byte) , zu empfangenes Byte verarbeiten


; --------------
; Error handling für
; TIMEOUT ERROR:    LOW(elapsed cycles/4)  >  3* ($B0) + $3C + $30 + $26
; PARITY ERROR
; -------------- 
.,F98B A5 B4    LDA $B4         Timer A Underflow IRQ enabled?
.,F98D F0 1D    BEQ $F9AC       => IRQ disabled 
.,F98F 85 A8    STA $A8         Zeiger auf 'READ ERROR' setzen
.,F991 D0 19    BNE $F9AC       unbedingter Sprung

; ----------------
; short pulse received
; carry set by calling code, accu contains 2 * ($B0) timing constant + $3C + $30
; ----------------
.,F993 E6 A9    INC $A9         Zeiger auf Impulswechsel +1 (short pulse)
.,F995 B0 02    BCS $F999       unbedingter Sprung

; ----------------
; medium pulse received
; X Register = 1
; carry set by calling code, accu contains 3 * ($B0) timing constant + $3C + $30 + $26
; ----------------

.,F997 C6 A9    DEC $A9         Zeiger auf Impulswechsel -1 (medium pulse)

; 
.,F999 38       SEC             Carry für Subtraktion setzen
.,F99A E9 13    SBC #$13        Anfangswert ($13) und
.,F99C E5 B1    SBC $B1         vergangene Zeit subtrahieren
.,F99E 65 92    ADC $92         und mit Flag für Timing
                                Korrektur addieren
.,F9A0 85 92    STA $92         Ergebnis dort speichern (es wird nur das Vorzeichen ausgewertet, siehe $F9D5)

.,F9A2 A5 A4    LDA $A4         Flag für Empfang beider
.,F9A4 49 01    EOR #$01        Impulse invertieren
.,F9A6 85 A4    STA $A4         und abspeichern

.,F9A8 F0 2B    BEQ $F9D5       verzweige wenn zwei Impulse empfangen ($A4 is initialized with 0)
.,F9AA 86 D7    STX $D7         empfangenes Signal ( 0 = short pulse , 1 = medium pulse) speichern


; ----------------------------------

; pulse length is too short (below ($b0) + $3c (=$3c*4 = 240 cycles))
.,F9AC A5 B4    LDA $B4         Timer A Underflow IRQ enabled?
.,F9AE F0 22    BEQ $F9D2       => IRQ disabled
.,F9B0 AD A3 02 LDA $02A3       ICR in Akku
.,F9B3 29 01    AND #$01        Bit 0 isolieren
.,F9B5 D0 05    BNE $F9BC       verzweige wenn Interrupt von Timer A
.,F9B7 AD A4 02 LDA $02A4       Timer A abgelaufen
.,F9BA D0 16    BNE $F9D2       nein, dann zum Interruptende

; EOB empfangen

.,F9BC A9 00    LDA #$00        Impulszähler
.,F9BE 85 A4    STA $A4         löschen und
.,F9C0 8D A4 02 STA $02A4       Zeiger auf Timeout setzen
.,F9C3 A5 A3    LDA $A3         prüfe ob Byte vollständig gelesen
.,F9C5 10 30    BPL $F9F7       verzweige falls nein
.,F9C7 30 BF    BMI $F988       unbedingter Sprung

; --------------------
; 9tes bit (parity bit) wurde empfangen,
; prüfen
; --------------------

.,F9C9 A2 A6    LDX #$A6        Initialisierungswert für Timer A
.,F9CB 20 E2 F8 JSR $F8E2       Band zum Lesen vorbereiten
.,F9CE A5 9B    LDA $9B         Paritätsbyte in Akku
.,F9D0 D0 B9    BNE $F98B       verzweige falls parit. Fehler
.,F9D2 4C BC FE JMP $FEBC       Rückkehr vom Interrupt

; --------------------
; 2 pulses received
; $D7 contains first pulse: 0 = short pulse, 1 = medium pulse
; X reg. contains latest pulse: 0 = short pulse, 1 = medium pulse
; --------------------
.,F9D5 A5 92    LDA $92         Timing Korrekturzeiger laden
.,F9D7 F0 07    BEQ $F9E0       verzweige wenn Flag gelöscht
.,F9D9 30 03    BMI $F9DE       verzweige wenn kleiner Null
.,F9DB C6 B0    DEC $B0         Timing Konstante -1
.:F9DD 2C       .BYTE $2C       Skip zu $F9E0
.,F9DE E6 B0    INC $B0         Timing Konstante +1

.,F9E0 A9 00    LDA #$00        Timing
.,F9E2 85 92    STA $92         Korrekturzeiger löschen
.,F9E4 E4 D7    CPX $D7         Vergleiche empfangenen Impuls mit vorherigem
.,F9E6 D0 0F    BNE $F9F7       => 2 verschiedene Pulse empfangen

; ---------------------------
; 2 gleiche pulse empfangen
; ---------------------------

.,F9E8 8A       TXA             Prüfe ob beide impulse short (=0) => sync/gap 
.,F9E9 D0 A0    BNE $F98B       => entweder (short,medium) oder (medium,short)
 
 ; ---------------------------
; 2 short pulses received
; short pulses increase $A9
; medium pulses decrease $A9
; ---------------------------
.,F9EB A5 A9    LDA $A9         Impulswechselzeiger laden
.,F9ED 30 BD    BMI $F9AC       verzweige wenn negativ (=mehr medium als short empfangen)
.,F9EF C9 10    CMP #$10        vergleiche mit $10
.,F9F1 90 B9    BCC $F9AC       => weniger als 16 short pulses empfangen
.,F9F3 85 96    STA $96         ACCU >= $10 , setze flag für EOB (cassette block synchronization number) (mehr als 16 short pulses hintereinander) empfangen
.,F9F5 B0 B5    BCS $F9AC       unbedingter Sprung

; ---------------------------
; 2 ungleiche pulse empfangen
; ; X register contains type of latest (second pulse): 0 = short pulse, 1 = medium pulse
; ---------------------------
.,F9F7 8A       TXA             Empfangenes Bit in Akku
.,F9F8 45 9B    EOR $9B         mit Band-Parität verknüpfen
.,F9FA 85 9B    STA $9B         in Band-Parität speichern
.,F9FC A5 B4    LDA $B4         Timer A Underflow IRQ enabled?
.,F9FE F0 D2    BEQ $F9D2       => IRQ disabled
.,FA00 C6 A3    DEC $A3         Speicher für Bitzähler -1
.,FA02 30 C5    BMI $F9C9       verzweige wenn Paritätsbit empfangen
.,FA04 46 D7    LSR $D7         gelesenes Bit ins Carry und
.,FA06 66 BF    ROR $BF         dann in $BF rollen
.,FA08 A2 DA    LDX #$DA        Initialisierungswert für Timer A ins X-Register
.,FA0A 20 E2 F8 JSR $F8E2       zur Kassettensynchronisation
.,FA0D 4C BC FE JMP $FEBC       Rückkehr vom Interrupt

; -----------------------------------------
; called after long pulse has been received
; -----------------------------------------
.,FA10 A5 96    LDA $96         Prüfe ob EOB ( ( >16 short pulses) , cassette block synchronization number) empfangen ( ($96) != 0 )
.,FA12 F0 04    BEQ $FA18       falls nein, verzweige
.,FA14 A5 B4    LDA $B4         Timer A Underflow IRQ enabled flag
.,FA16 F0 07    BEQ $FA1F       IRQ disabled => überspringe Bit Zähler Test
                                
; ---------------                               
; long pulse has been received , no EOB received
; ---------------                               
.,FA18 A5 A3    LDA $A3         Bitzähler laden
.,FA1A 30 03    BMI $FA1F       verzweige falls negativ (=parity bit received)
.,FA1C 4C 97 F9 JMP $F997       ???? treat as medium pulse ???? langen Impuls verarbeiten

; ----------------
; long pulse has been received , no EOB received , bit counter >= 0
; ----------------
.,FA1F 46 B1    LSR $B1         vergangene Zeit seit letzter Flanke halbieren
.,FA21 A9 93    LDA #$93        und diesen Wert
.,FA23 38       SEC             von $93
.,FA24 E5 B1    SBC $B1         abziehen
.,FA26 65 B0    ADC $B0         dazu dann Timing-Konstante addieren
.,FA28 0A       ASL             und Ergebnis verdoppeln
.,FA29 AA       TAX             Ergebnis ins X-Register
.,FA2A 20 E2 F8 JSR $F8E2       Timing initialisieren
.,FA2D E6 9C    INC $9C         Flag für Byte empfangen setzen
.,FA2F A5 B4    LDA $B4         Timer A Underflow IRQ enabled?
.,FA31 D0 11    BNE $FA44       IRQ enabled => Timer A läuft
.,FA33 A5 96    LDA $96         wurde EOB emfangen ?
.,FA35 F0 26    BEQ $FA5D       return from interrupt

; EOB received?
; Accu != 0 
.,FA37 85 A8    STA $A8         Flag für Lesefehler setzen
.,FA39 A9 00    LDA #$00        Flag für
.,FA3B 85 96    STA $96         EOB (cassette block synchronization number) rücksetzen

; enable Timer A underflow IRQ
.,FA3D A9 81    LDA #$81        Underflow Interrupt für
.,FA3F 8D 0D DC STA $DC0D       Timer A freigeben
.,FA42 85 B4    STA $B4         remember Timer A underflow IRQ enabled

.,FA44 A5 96    LDA $96         Flag für EOB laden
.,FA46 85 B5    STA $B5         und nach $B5 kopieren
.,FA48 F0 09    BEQ $FA53       verzweige wenn kein EOB

; disable Timer A underflow IRQ
.,FA4A A9 00    LDA #$00        Flag für Timer A
.,FA4C 85 B4    STA $B4         remember TimerA underflow IRQ disabled
.,FA4E A9 01    LDA #$01        Interruptflag
.,FA50 8D 0D DC STA $DC0D       wieder löschen

; 
.,FA53 A5 BF    LDA $BF         Shift Register für Read laden
.,FA55 85 BD    STA $BD         und nach $BD bringen
.,FA57 A5 A8    LDA $A8         Flag für Lesefehler laden
.,FA59 05 A9    ORA $A9         mit Impulswechselzeiger
.,FA5B 85 B6    STA $B6         verknüpfen und in Fehlercode des Bytes ablegen
;                                 
.,FA5D 4C BC FE JMP $FEBC       Rückkehr vom Interrupt

; ---------------------
; pulse empfangen, mindestens short pulse
; (elapsed größer als threshold #1)
; ---------------------

; *** # store character
.,FA60 20 97 FB JSR $FB97       new tape byte setup
.,FA63 85 9C    STA $9C         clear byte received flag (accu is still 0 here from $FB97 subroutine)
.,FA65 A2 DA    LDX #$DA        set timing max byte
.,FA67 20 E2 F8 JSR $F8E2       set timing
.,FA6A A5 BE    LDA $BE         get copies count
.,FA6C F0 02    BEQ $FA70       
.,FA6E 85 A7    STA $A7         save receiver input bit temporary storage
.,FA70 A9 0F    LDA #$0F        Maskenwert für Zählung vor dem Lesen

; $AA kann folgende Werte annehmen:
; $0  => ???
; $1 - $0f => sync byte counter (???) 
; $40 => bit 6 (V flag)
; $80 => bit 7 (N flag)

.,FA72 24 AA    BIT $AA         sets ZERO flag according to 0x0f (accu) & ($AA) and N and V flags according to bits 7 and 6 of $(AA) ; prüfe Zeiger für Lesen von Band
.,FA74 10 17    BPL $FA8D       verzweige wenn alle Zeichen empfangen (=bit 7 of $aa not set) => Ende

.,FA76 A5 B5    LDA $B5         Flag für EOB laden
.,FA78 D0 0C    BNE $FA86       verzweige wenn gültiges EOB empfangen
.,FA7A A6 BE    LDX $BE         Anzahl der verbliebenen Blöcke laden
.,FA7C CA       DEX             Anzahl -1
.,FA7D D0 0B    BNE $FA8A       verzweige wenn nicht Null

; ---------------------
; 'LONG BLOCK' error , no EOB but still more data received
; ---------------------
.,FA7F A9 08    LDA #$08        'LONG BLOCK' error
.,FA81 20 1C FE JSR $FE1C       Status setzen
.,FA84 D0 04    BNE $FA8A       unbedingter Sprung zum normalen IRQ

; ----------
; pulse empfangen, mindestens short pulse
; (elapsed größer als threshold #1) 
; EOB empfangen
; ----------
.,FA86 A9 00    LDA #$00        Flag für Lesen vom Band auf
.,FA88 85 AA    STA $AA         Abtastung setzen
.,FA8A 4C BC FE JMP $FEBC       Rückkehr vom Interrupt

; --------------------------------
; invoked after BIT $AA instruction
; bit 7 of $AA is NOT set (noch nicht alle Zeichen empfangen??)
; --------------------------------

.,FA8D 70 31    BVS $FAC0       (bit 6 of $AA is set) verzweige wenn Bandzeiger auf lesen
.,FA8F D0 18    BNE $FAA9       verzweige wenn Bandzeiger auf Zählen
.,FA91 A5 B5    LDA $B5         Flag für EOB laden
.,FA93 D0 F5    BNE $FA8A       verzweige wenn EOB empfangen
.,FA95 A5 B6    LDA $B6         Flag für Lesefehler laden
.,FA97 D0 F1    BNE $FA8A       verzweige falls Fehler aufgetreten
                                
.,FA99 A5 A7    LDA $A7         Anzahl der noch zu lesenden Blöcke holen
.,FA9B 4A       LSR             Bit 0 ins Carry schieben

; 
.,FA9C A5 BD    LDA $BD         hole gelesenes Byte
.,FA9E 30 03    BMI $FAA3       hi-bit set => first sync

; hi-bit not set, second sync
.,FAA0 90 18    BCC $FABA       verzweige wenn mehr als ein Block zu lesen
.,FAA2 18       CLC             lösche Carry um nicht zu verzweigen

;
.,FAA3 B0 15    BCS $FABA       verzweige falls nur ein Block zu lesen
.,FAA5 29 0F    AND #$0F        Bits 0 bis 3 isolieren
.,FAA7 85 AA    STA $AA         und für Zählung speichern

.,FAA9 C6 AA    DEC $AA         ein Synchronisierungsbyte empfangen
.,FAAB D0 DD    BNE $FA8A       noch nicht alle empfangen => return from IRQ

; alle synchronisierungsbytes empfangen

.,FAAD A9 40    LDA #$40        Bandzeiger auf 
.,FAAF 85 AA    STA $AA         lesen stellen (bit 6 gesetzt => V flag true bei BIT test)
.,FAB1 20 8E FB JSR $FB8E       Ein/Ausgabe Adresse kopieren
.,FAB4 A9 00    LDA #$00        
.,FAB6 85 AB    STA $AB         Leseprüfsumme löschen
.,FAB8 F0 D0    BEQ $FA8A       unbedingter Sprung

; --------------------------------

.,FABA A9 80    LDA #$80        Bandzeiger
.,FABC 85 AA    STA $AA         auf Ende (bit 7 gesetzt => N flag true bei BIT test) stellen
.,FABE D0 CA    BNE $FA8A       unbedingter Sprung

;
.,FAC0 A5 B5    LDA $B5         Flag für EOB laden
.,FAC2 F0 0A    BEQ $FACE       verzweige wenn nicht gesetzt
.,FAC4 A9 04    LDA #$04        'SHORT BLOCK’ error
.,FAC6 20 1C FE JSR $FE1C       Status setzen
.,FAC9 A9 00    LDA #$00        Code für Lesezeiger auf"Abtasten"
.,FACB 4C 4A FB JMP $FB4A       setzen, unbedingter Sprung

; -------------------------------
; ??? verify ???

.,FACE 20 D1 FC JSR $FCD1       Endadresse schon erreicht ?
.,FAD1 90 03    BCC $FAD6       nein dann verzweige
.,FAD3 4C 48 FB JMP $FB48       zu Read Ende für Block
.,FAD6 A6 A7    LDX $A7         nur noch
.,FAD8 CA       DEX             ein Block zu lesen
.,FAD9 F0 2D    BEQ $FB08       verzweige wenn ja (Pass 2)
.,FADB A5 93    LDA $93         Load/Verify-Flag
.,FADD F0 0C    BEQ $FAEB       verzweige wenn Load
.,FADF A0 00    LDY #$00        Zähler auf Null setzen
.,FAE1 A5 BD    LDA $BD         gelesenes Byte
.,FAE3 D1 AC    CMP ($AC),Y     vergleichen
.,FAE5 F0 04    BEQ $FAEB       verzweige wenn Übereinstimmung
.,FAE7 A9 01    LDA #$01        Fehlerflag
.,FAE9 85 B6    STA $B6         setzen
.,FAEB A5 B6    LDA $B6         Fehlerflag laden
.,FAED F0 4B    BEQ $FB3A       verzweige wenn kein Fehler
                                aufgetreten
.,FAEF A2 3D    LDX #$3D        bereits 31 Fehler
.,FAF1 E4 9E    CPX $9E         aufgetreten
.,FAF3 90 3E    BCC $FB33       verzweige wenn weniger Fehler
.,FAF5 A6 9E    LDX $9E         Index für Lesefehler
.,FAF7 A5 AD    LDA $AD         laufender Adressbyte HIGH
.,FAF9 9D 01 01 STA $0101,X     im Stack speichern
.,FAFC A5 AC    LDA $AC         Adressbyte LOW
.,FAFE 9D 00 01 STA $0100,X     für spätere Korrektur
                                ebenfalls im Stack speichern
.,FB01 E8       INX             Zeiger auf nachfolgende
.,FB02 E8       INX             freie Stelle setzen
.,FB03 86 9E    STX $9E         und abspeichern
.,FB05 4C 3A FB JMP $FB3A       weitermachen
.,FB08 A6 9F    LDX $9F         bereits alle Lesefehler
.,FB0A E4 9E    CPX $9E         korrigiert ?
.,FB0C F0 35    BEQ $FB43       verzweige falls ja
.,FB0E A5 AC    LDA $AC         Adressbyte LOW laden
.,FB10 DD 00 01 CMP $0100,X     mit fehlerhaftem Adressbyte LOW vergleichen
.,FB13 D0 2E    BNE $FB43       verzweige falls nicht gefunden
.,FB15 A5 AD    LDA $AD         Adressbyte HIGH laden
.,FB17 DD 01 01 CMP $0101,X     mit fehlerhaftem Adressbyte HIGH vergleichen
.,FB1A D0 27    BNE $FB43       verzweige wenn nicht gefunden
.,FB1C E6 9F    INC $9F         Korrekturzähler
.,FB1E E6 9F    INC $9F         Pass 2 um zwei erhöhen
.,FB20 A5 93    LDA $93         Verify-Flag gesetzt
.,FB22 F0 0B    BEQ $FB2F       verzweige wenn nicht gesetzt
.,FB24 A5 BD    LDA $BD         gelesenes Byte laden
.,FB26 A0 00    LDY #$00        Zähler auf Null setzen
.,FB28 D1 AC    CMP ($AC),Y     mit Speicherinhalt vergleichen
.,FB2A F0 17    BEQ $FB43       verzweige wenn gleich, dann nächstes Byte
.,FB2C C8       INY             Flag für
.,FB2D 84 B6    STY $B6         Fehler setzen
.,FB2F A5 B6    LDA $B6         Fehlerflag testen
.,FB31 F0 07    BEQ $FB3A       verzweige wenn kein Fehler
.,FB33 A9 10    LDA #$10        'SECOND PASS' error
.,FB35 20 1C FE JSR $FE1C       Status setzen
.,FB38 D0 09    BNE $FB43       und nächstes Byte verarbeiten
.,FB3A A5 93    LDA $93         Verify-Flag laden
.,FB3C D0 05    BNE $FB43       verzweige wenn gesetzt
.,FB3E A8       TAY             Zeiger löschen
.,FB3F A5 BD    LDA $BD         gelesenes Byte
.,FB41 91 AC    STA ($AC),Y     speichern
.,FB43 20 DB FC JSR $FCDB       Adresszeiger erhöhen
.,FB46 D0 43    BNE $FB8B       Rückkehr vom Interrupt
.,FB48 A9 80    LDA #$80        Flag für Lesen
.,FB4A 85 AA    STA $AA         auf Ende

;
.,FB4C 78       SEI             Interrupt verhindern
.,FB4D A2 01    LDX #$01        IRQ vom
.,FB4F 8E 0D DC STX $DC0D       Timer A verhindern
.,FB52 AE 0D DC LDX $DC0D       IRQ request flags löschen
.,FB55 A6 BE    LDX $BE         Pass-Zähler
.,FB57 CA       DEX             erniedrigen
.,FB58 30 02    BMI $FB5C       verzweige wenn Null gewesen
.,FB5A 86 BE    STX $BE         Passzähler merken

;
.,FB5C C6 A7    DEC $A7         Blockzähler vermindern
.,FB5E F0 08    BEQ $FB68       verzweige wenn Null
.,FB60 A5 9E    LDA $9E         Fehler in Pass 1 aufgetreten ?
.,FB62 D0 27    BNE $FB8B       ja, Rückkehr vom Interrupt
.,FB64 85 BE    STA $BE         kein Block mehr zu verarbeiten
.,FB66 F0 23    BEQ $FB8B       Rückkehr vom Interrupt
.,FB68 20 93 FC JSR $FC93       ein Pass beendet
.,FB6B 20 8E FB JSR $FB8E       Adresse wieder auf Programmanfang
.,FB6E A0 00    LDY #$00        Zähler auf Null setzen
.,FB70 84 AB    STY $AB         Checksumme löschen
.,FB72 B1 AC    LDA ($AC),Y     Programm
.,FB74 45 AB    EOR $AB         Checksumme berechnen
.,FB76 85 AB    STA $AB         und speichern
.,FB78 20 DB FC JSR $FCDB       Adresszeiger erhöhen
.,FB7B 20 D1 FC JSR $FCD1       Endadresse schon erreicht ?
.,FB7E 90 F2    BCC $FB72       nein, weiter vergleichen
.,FB80 A5 AB    LDA $AB         berechnete Checksumme
.,FB82 45 BD    EOR $BD         mit Checksumme vom Band vergleichen
.,FB84 F0 05    BEQ $FB8B       Checksumme gleich , dann ok
.,FB86 A9 20    LDA #$20        'CHECKSUM' error
.,FB88 20 1C FE JSR $FE1C       Status setzen
.,FB8B 4C BC FE JMP $FEBC       Rückkehr vom Interrupt

;---------------------------------

.,FB8E A5 C2    LDA $C2         Startadresse
.,FB90 85 AD    STA $AD         $C1/$C2
.,FB92 A5 C1    LDA $C1         nach $AC/$AD
.,FB94 85 AC    STA $AC         speichern
.,FB96 60       RTS             Rücksprung


;---------------------------------
;
;read a block from cassette
;
$F841  A9  00        LDA #$00
$F843  85  90        STA $90         ;clear ST
$F845  85  93        STA $93         ;set load/verify switch to load
$F847  20  D7  F7    JSR $F7D7       ;set tape buffer to I/O area
$F84A  20  17  F8    JSR $F817       ;handle msgs and test sense for read
$F84D  B0  1F        BCS $F86E
$F84F  78            SEI             ;disable IRQ
$F850  A9  00        LDA #$00
$F852  85  AA        STA $AA         ;set gap
$F854  85  B4        STA $B4         ;set no sync estabilished
$F856  85  B0        STA $B0         ;set no special speed correction yet
$F858  85  9E        STA $9E         ;initialize error log index for pass 1
$F85A  85  9F        STA $9F         ;and pass2
$F85C  85  9C        STA $9C         ;set no byte available yet
$F85E  A9  90        LDA #$90        ;set Flag mask
$F860  A2  0E        LDX #$0E        ;index for cassette read IRQ address
$F862  D0  11        BNE $F875       ;JMP

;
;write a block to cassette
;
$F864  20  D7  F7    JSR $F7D7       ;initialize tape buffer pointer
$F867  A9  14        LDA #$14
$F869  85  AB        STA $AB         ;20 sync patterns
$F86B  20  38  F8    JSR $F838       ;test sense and display msgs for output
$F86E  B0  6C        BCS $F8DC
$F870  78            SEI
$F871  A9  82        LDA #$82        ;mask for ICR1 to honor TB1
$F873  A2  08        LDX #$08        ;IRQ index for cassette write, part 1

;
;common code for cassette read & write
;
$F875  A0  7F        LDY #$7F
$F877  8C  0D  DC    STY $DC0D       ;clear any pending mask in ICR1
$F87A  8D  0D  DC    STA $DC0D       ;then set mask for TB1
$F87D  AD  0E  DC    LDA $DC0E
$F880  09  19        ORA #$19        ; 0b0001_1001 , +force load, one shot and TB1 to CRA1
$F882  8D  0F  DC    STA $DC0F       ;to form CRB1
$F885  29  91        AND #$91        ; 0b1001_0001 , only keep TOD register handling , force-load and timer enable/disable
$F887  8D  A2  02    STA $02A2       ;and CRB1 activity register
$F88A  20  A4  F0    JSR $F0A4       ;condition flag bit in ICR2
$F88D  AD  11  D0    LDA $D011
$F890  29  EF        AND #$EF
$F892  8D  11  D0    STA $D011       ;disable the screen

;save standard IRQ vector
$F895  AD  14  03    LDA $0314       
$F898  8D  9F  02    STA $029F       ; save lo
$F89B  AD  15  03    LDA $0315
$F89E  8D  A0  02    STA $02A0       ; save hi

$F8A1  20  BD  FC    JSR $FCBD       ;set new IRQ for cassette depending on X
$F8A4  A9  02        LDA #$02
$F8A6  85  BE        STA $BE         ; number of blocks to read = 0x02
$F8A8  20  97  FB    JSR $FB97       ;initialize cassette I/O variables
$F8AB  A5  01        LDA $01
$F8AD  29  1F        AND #$1F
$F8AF  85  01        STA $01         ;start cassette motor
$F8B1  85  C0        STA $C0         ;set tape motor interlock
$F8B3  A2  FF        LDX #$FF
$F8B5  A0  FF        LDY #$FF
$F8B7  88            DEY
$F8B8  D0  FD        BNE $F8B7       ;delay 0.3 seconds
$F8BA  CA            DEX
$F8BB  D0  F8        BNE $F8B5
$F8BD  58            CLI
$F8BE  AD  A0  02    LDA $02A0       ;test high byte of IRQ save area
$F8C1  CD  15  03    CMP $0315       ;to determine if end of I/O
$F8C4  18            CLC
$F8C5  F0  15        BEQ $F8DC       ;exit if so
$F8C7  20  D0  F8    JSR $F8D0       ;else test Stop key
$F8CA  20  BC  F6    JSR $F6BC       ;scan keyboard
$F8CD  4C  BE  F8    JMP $F8BE       ;repeat

;---------------------------------

; new tape byte setup

$FB97 A9 08    LDA #$08        eight bits to do
$FB99 85 A3    STA $A3         set bit count
$FB9B A9 00    LDA #$00        clear A
$FB9D 85 A4    STA $A4         clear tape bit cycle phase
$FB9F 85 A8    STA $A8         clear start bit first cycle done flag
$FBA1 85 9B    STA $9B         clear byte parity
$FBA3 85 A9    STA $A9         clear start bit check flag, set no start bit yet
$FBA5 60       RTS

.,FB97 A9 08    LDA #$08        Zähler für 8 Bits
.,FB99 85 A3    STA $A3         Nach $A3
.,FB9B A9 00    LDA #$00        Akku mit $00 laden
.,FB9D 85 A4    STA $A4         Bit-Impuls-Flag löschen
.,FB9F 85 A8    STA $A8         Lesefehler Byte löschen
.,FBA1 85 9B    STA $9B         Parity-Byte löschen
.,FBA3 85 A9    STA $A9         Impulswechsel-Flag löschen
.,FBA5 60       RTS             Rücksprung

;---------------------------------

; set tape vector

$FCBD BD 93 FD LDA $FD93,X     get tape IRQ vector low byte
$FCC0 8D 14 03 STA $0314       set IRQ vector low byte
$FCC3 BD 94 FD LDA $FD94,X     get tape IRQ vector high byte
$FCC6 8D 15 03 STA $0315       set IRQ vector high byte
$FCC9 60       RTS

;---------------------------------

;set IRQ vector depending upon X
;
$FCDB  BD  93  FD    LDA $FD9B-8,X   ;move low byte of address
$FCDE  8D  14  03    STA $0314       ;into low byte of IRQ vector
$FCE1  BD  94  FD    LDA $FD9B-7,X   ;then do high byte
$FCE4  8D  15  03    STA $0315
$FCE7  60            RTS

;---------------------------------

;IRQ vectors
; see routine @ $FCDB that sets IRQ vector depending on X register

$FD9B .WORD $FC6A ; X = $08 write tape leader IRQ routine
$FD9D .WORD $FBCD ; X = $0A tape write IRQ routine
$FD9F .WORD $EA31 ; X = $0C normal IRQ vector
$FDA1 .WORD $F92C ; X = $0E read tape bits IRQ routine

; ===========================
; schedule CIA1 Timer A depending on parameter in X
; ===========================

; >>> $B1 is just temporary storage <<<

; Possible values for X are:
;
; X = $DA
; X = $A6
; 
; X = 2 * ( $93 - (elapsed_cycles/4 / 2) + $B0 ) 
;
$F8E2  86  B1        STX $B1         ;save entry parameter

; $B1 = ($B0) * 4 + ($B0) + ($B1)

$F8E4  A5  B0        LDA $B0         ;get speed correction
$F8E6  0A            ASL             ;* 2
$F8E7  0A            ASL             ;* 4
$F8E8  18            CLC
$F8E9  65  B0        ADC $B0         ;add speed correction
$F8EB  18            CLC
$F8EC  65  B1        ADC $B1         ;add parameter from X register

; Carry flag now holds MSB
$F8EE  85  B1        STA $B1         ;save low order


$F8F0  A9  00        LDA #$00
$F8F2  24  B0        BIT $B0         ; test speed correction
$F8F4  30  01        BMI $F8F7       ; => MSB is already set
 
$F8F6  2A            ROL             ; shift MSB from $B1 into accumulator

$F8F7  06  B1        ASL $B1         ; shift bit 7 into CARRY
$F8F9  2A            ROL             ; shift carry into accumulator
$F8FA  06  B1        ASL $B1         ; shift bit 7 into carry
$F8FC  2A            ROL             ; shift carry into accumulator
$F8FD  AA            TAX             ; Accu contains (2/3) MSB bits from $B1 , save to X

; wait until TBL1 >= $16 (22)
$F8FE  AD  06  DC    LDA $DC06       ; fetch TBL1
$F901  C9  16        CMP #$16        ; 
$F903  90  F9        BCC $F8FE       ; loop while TBl1 < $16 (22)

$F905  65  B1        ADC $B1         : ACCU = TBL1 + $B1 ; add low order offset to TBL1
$F907  8D  04  DC    STA $DC04       ;and store in TAL1
$F90A  8A            TXA             ; (2/3) high-order bits to Accumulator
$F90B  6D  07  DC    ADC $DC07       ;add high order offset to TBH1
$F90E  8D  05  DC    STA $DC05       ;and store in TAH1

$F911  AD  A2  02    LDA $02A2
$F914  8D  0E  DC    STA $DC0E       ;set CRA1 from CRB1 activity register (%10001)
$F917  8D  A4  02    STA $02A4       ;and save it

$F91A  AD  0D  DC    LDA $DC0D       ; ICR
$F91D  29  10        AND #$10        ; test bit 4 (~FLAG pin IRQ)
$F91F  F0  09        BEQ $F92A       ; => no Flag pin IRQ

; FLAG pin IRQ detected
$F921  A9  F9        LDA #$F9        ;set exit address on stack
$F923  48            PHA
$F924  A9  2A        LDA #$2A
$F926  48            PHA
$F927  4C  43  FF    JMP $FF43       ;and simulate an IRQ

; allow IRQs and return
$F92A  58            CLI             
$F92B  60            RTS
