package de.codesourcery.j6502.emulator.diskdrive;

import java.util.Optional;

import de.codesourcery.j6502.emulator.CPU;
import de.codesourcery.j6502.emulator.CPU.Flag;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.emulator.G64File;
import de.codesourcery.j6502.emulator.G64File.TrackData;
import de.codesourcery.j6502.emulator.IECBus;
import de.codesourcery.j6502.emulator.IMemoryRegion;
import de.codesourcery.j6502.emulator.SerialDevice;
import de.codesourcery.j6502.emulator.diskdrive.VIA.Port;
import de.codesourcery.j6502.emulator.diskdrive.VIA.PortName;
import de.codesourcery.j6502.emulator.diskdrive.VIA.VIAChangeListener;
import de.codesourcery.j6502.utils.BitStream;

public class DiskHardware implements SerialDevice
{
    protected static final int SPEED00_CYCLES_PER_BYTE = 32;
    protected static final int SPEED01_CYCLES_PER_BYTE = 30;
    protected static final int SPEED10_CYCLES_PER_BYTE = 28;
    protected static final int SPEED11_CYCLES_PER_BYTE = 26;

    public abstract class DriveMode
    {
        private long cycles = SPEED10_CYCLES_PER_BYTE;

        public final void tick()
        {
            cycles--;
            if ( cycles == 0 )
            {
                restartCycleCounter();
                processByte();
            }
        }

        protected final void restartCycleCounter() {
            cycles = cyclesPerByte;
        }
        
        public abstract void processByte();

        public final void onEnter() {
            cycles = cyclesPerByte;
        }

        public abstract void trackChanged();

        public abstract void onEnterHook();
    }

    protected static enum SyncMode {
        WAIT_FOR_SYNC,READING_SYNC,READING_DATA
    }

    public final class ReadMode extends DriveMode
    {
        private int oneBits=0;

        private SyncMode syncState = SyncMode.WAIT_FOR_SYNC;

        private void syncFound() 
        {
            restartCycleCounter();
            via2.getPortB().setInputPin(7, false ); // sync found (PB7 expects INVERTED signal !!)
        }

        private void noSyncFound() {
            via2.getPortB().setInputPin(7, true ); // no sync found (PB7 expects INVERTED signal !!)
        }        

        private void byteNotReady() {
            via2.getPortB().setControlLine1( false , false ); // byte not ready
        }

        private void byteReady() 
        {
            // final boolean byteLost = cpu.isSet( Flag.OVERFLOW );
            cpu.setFlag( Flag.OVERFLOW ); // byte ready
            
            restartCycleCounter();

            /* Das Byte Ready Signal kann nur mit SOE abgestellt werden,ansonsten kommt es regelmässig nach 8 Bits.
             * Das ist unabhängig davon ob sich das Laufwerk dreht oder nicht. Das ist ein Zähler auf der Platine der vom SYNC Signal
             * mit 0 geladen wird und immer wenn die Zeit für ein Bit abgelaufen ist eins hoch zählt.
             * Wenn sich nichts tut (Kein Flusswechel am Lesekopf) denkt das Laufwerk, dass es ein "0" Bit ist.
             */

            if ( via2.getPortA().getControlLine2() ) // Trigger byte read signal ?
            {
                via2.getPortB().setControlLine1( true , false ); // byte ready
            }            
        }
        
        @Override
        public void processByte()
        {
            if ( ! motorsRunning )
            {
                return;
            }

            noSyncFound();

            byteNotReady();

            int value = 0;
            for ( int i = 0 ; i < 8 ; i++ )
            {
                final int bit = bitStream.readBit();
                value = value << 1;
                value = value | bit;
                if ( bit == 0 )
                {
                    oneBits = 0;
                    if ( syncState == SyncMode.READING_SYNC )
                    {
                        syncFound();
                        syncState = SyncMode.READING_DATA;
                        bitStream.rewind(1); /* align bitstream */
                        return;
                    }
                } else {
                    oneBits++;
                    if ( oneBits >= 10 ) {
                        syncState = SyncMode.READING_SYNC;
                    }
                }
            }          

            via2.getPortA().setInputPins( value );
            byteReady();
        }
        
        public void trackChanged() 
        {
            noSyncFound();
            byteNotReady();
            oneBits = 0;
            syncState = SyncMode.WAIT_FOR_SYNC;
        }        

        @Override
        public void onEnterHook()
        {
            oneBits = 0;
            syncState = SyncMode.WAIT_FOR_SYNC;
        }
    }

    public final class WriteMode extends DriveMode
    {
        @Override
        public void processByte() {
            // TODO: Implement writing
            throw new RuntimeException("Write mode not implemented yet");
        }

        @Override
        public void onEnterHook() {
            // TODO: Implement writing
            throw new RuntimeException("Write mode not implemented yet");
        }

        @Override
        public void trackChanged() {
            // TODO Auto-generated method stub

        }
    }

    protected final CPU cpu;
    protected final DiskDrive diskDrive;
    protected final int driveAddress;
    protected int cyclesPerByte = SPEED10_CYCLES_PER_BYTE;
    protected float headPosition = 18;
    protected boolean driveLED;

    protected boolean motorsRunning;

    protected boolean writeProtectOn;

    protected G64File disk;
    protected Optional<TrackData> currentTrackData;
    protected BitStream bitStream = new BitStream( new byte[] {0x55} , 8 );

    protected final ReadMode READ = new ReadMode();
    protected final WriteMode WRITE = new WriteMode();

    protected static enum HeadMovement { 
        TO_TRACK_1(-0.5f) , 
        TO_TRACK_35(0.5f), 
        NONE(0);

        private final float inc;

        private HeadMovement(float inc) { this.inc = inc; }

        public float move(float currentPos) {
            return currentPos+inc;
        }
    }

    private HeadMovement headMovement=HeadMovement.NONE;
    private int previousStepMotorCycle=3;
    private boolean warmupFinished = false;

    private DriveMode driveMode = READ;

    /*
     * 1541 uses 16 Mhz base clock.
     *
     * Tracks Clock Frequency Divide By
     * 1-17    1.2307 MHz 13
     * 18-24  1.1428 MHz  14
     * 15-30  1.0666 MHz  15
     * 31-35  1 MHz       16
     *
     * ------
     *
     * taken from http://www.forum64.de/wbb3/board2-c64-alles-rund-um-den-brotkasten/board4-hardware/board104-diskettenlaufwerke/17633-1541-steppersteuerung-und-datentransport/index3.html
     *
     * Der Takt für die Bitrate wird erzeugt, indem ein Zähler entweder von
     *
     * 00 (slowest) => 0 bis 15 oder von
     * 01           => 1 bis 15 oder von
     * 10           => 2 bis 15 oder von
     * 11 (fastest) => 3 bis 15 zählt
     *
     * (Je nachdem was die VIA2 and den Clock Schaltkreis anlegt). Das sind zwei Leitungen die das bestimmen.
     *
     * Wenn der Zähler auf 15 steht wird ein Puls generiert. Ein Bit ist genau 4 solcher Pulse lang.
     *
     * Wenn bei 16 Mhz der Zähler von 0 bis 15 zählt das ist das genau 1 µs. 4 Pulse sind dann 4 µs pro Bit = 32 1Mhz cycles per byte
     *
     * 16/16*4 = 4.00 µs = 32 1Mhz cycles per byte
     * 15/16*4 = 3.75 µs = 29.92
     * 14/16*4 = 3.50 µs = 28
     * 13/16*4 = 3.25 µs = 26 cycles per byte
     */

    /*
     * Taken from http://c64preservation.com/protection
     *
     * Tracks on the disk are organized as concentric circles, and the drive's
     * stepper motor can stop at 84 different locations (tracks) on a disk.
     * However, the read/write head on the drive is too wide to use each one
     * separately, so every other track is skipped for a total of 42 theoretical
     * tracks. The common terminology for the step in between each track is a
     * "half-track" and a specific track would be referred to as (for example)
     * "35.5" instead of the actual track (which would be 71). Commodore limited
     * use to only the first 35 tracks in their standard DOS, but commercial
     * software isn't limited by this. Most floppy media is rated to use 40
     * tracks, and the drives usually have no trouble reading out to track 41,
     * although some will bump and not get past 40. Most software does not use
     * any track past 35 except for copy protection, but alternative DOS systems
     * like Speed-DOS used all 40 tracks in their own DOS implementation.
     *
     * Sectoring Tracks are further divided into sectors, which are sections of
     * each track divided by the aforementioned software-generated sync marks.
     * The drive motor spins at 300rpm and can store data at 4 different bit
     * densities (essentially 4 different clock speed rates of the read/write
     * hardware). The different densities are needed because being round and the
     * motor running at a constant speed, the disk surface travels over the head
     * at different speeds depending on whether the drive is accessing the
     * outermost or innermost tracks. Since the surface is moving faster on the
     * outermost tracks, they can store more data, so they use the highest
     * density setting. Consequently, the innermost tracks use the slowest
     * density setting. Because we're recording at a higher density, more
     * sectors are stored on the outer tracks, and fewer on the inner tracks.
     * There is nothing stopping the hardware from reading/writing at the
     * highest density across the entire disk surface, but it isn't generally
     * done due to media reliability, and slight speed differences between
     * drives. The media itself is only rated for a certain bit rate at a
     * certain speed.
     *
     * VIA1:
     *
     * PA0..7 - unused
     *
     * PB0 - (IN) Data in
     * PB1 - (OUT) Data out
     * PB2 - (IN) Clock in
     * PB3 - (OUT) Clock out
     * PB4 - (OUT) ATN Acknowledge
     * PB5 - (IN) Device address
     * PB6 - (IN) Device address
     *
     * CA1 - Unused
     * CA2 - Unused
     * CB1 - Unused
     * CB2 - (IN) ATN IN
     *
     * VIA2:
     *
     * PA0...8 - (IN/OUT) Read data/Write data
     *
     * PB0 - (OUT) Step 1  |   Sequence 00/01/10/11/00... moves inwards      |
     * PB1 - (OUT) Step 0  |   Sequence 00/11/10/01/00... moves outwards     |
     * PB2 - (OUT) Drive motor AND Step Motor on/off
     * PB3 - (OUT) LED
     * PB4 - (IN) Write-protect
     * PB5 - (OUT) bitrate select A (??)
     * PB6 - (OUT) bitrate select B (??)
     * PB7 - (IN)  SYNC detected
     * 
     * CA1 - (IN) Byte read / Byte written (to be read in read mode / to be written in write mode)
     *
     *                Das Byte Ready Signal kann nur mit SOE abgestellt werden,ansonsten kommt es regelmässig nach 8 Bits.
     *                Das ist unabhängig davon ob sich das Laufwerk dreht oder nicht. Das ist ein Zähler auf der Platine der vom SYNC Signal
     *                mit 0 geladen wird und immer wenn die Zeit für ein Bit abgelaufen ist eins hoch zählt.
     *                Wenn sich nichts tut (Kein Flusswechel am Lesekopf) denkt das Laufwerk, dass es ein "0" Bit ist.
     *                Wenn sich das Laufwerk nicht dreht kommen eben nur "0" Bits ...
     * CA2 - (OUT) SOE / Enable/disable Byte Ready signal (High = activate BYTE-READY)
     *
     * CB1 - Unused
     * CB2 - (OUT) Read/Write mode (Low = Write, High = Read))
     *
     * -----------
     *
     * Schrittmotorsteuerung
     *
     * Aber von der Reihenfolge haut das bei meiner Messung schon hin:
     *
     * STP0: 0 1 . 0 1 . 0 1 . 0 1
     * STP1: 0 0 . 1 1 . 0 0 . 1 1
     *
     * und
     * STP0: 0 1 . 0 1 . 0 1 . 0 1
     * STP1: 0 1 . 1 0 . 0 1 . 1 0
     * ----------
     *
     *  Also der Ausgang des Schieberegisters liegt am Eingang der VIA2. Die Byte Ready Leitung führt dazu dass die
     *  Daten die gerade am Eingang der VIA2 anliegen dort zwischengespeichert werden. Da kann sie der 6502 dann abholen.
     *
     *  Jedesmal wenn ein Byte von der VIA2 aus dem Schieberegister ausgelesen werden soll, zeigt das die
     *  Hardware (die 8 Bits zählt) mit Hilfe der Byte Ready Leitung an.
     *  Mit SOE schaltet man diese Leitung ein und aus.
     *  Ist sie ausgeschaltet, dann holt die VIA2 keine Daten vom Schieberegister ab. Es wird also nichts gelesen, obwohl das Schieberigister dauernd aktiv ist.
     *
     *  Für jedes empfangene Byte setzt die Hardware einmal die Byte Ready Leitung und die Daten werden zur VIA2 transferiert.
     *  Nebenbei liegt diese Leitung auch am 6502 so dass ein Programm festellen kann, ob die VIA2 gerade ein Byte zur Abholung bereit hat.
     *  Nachdem die Byte Ready Leitung gesetzt wurde hat man also 8 Bits Zeit dieses Byte abzuholen, bevor die Leitung das nächste mal aktiv wird und das vorige Byte verloren geht.
     *
     *  Auf diese Weise kann man ein Byte oder einen ganzen Sektor abholen, das macht dann das Programm bzw. das DOS.
     *
     *  ------------
     *
     *  - Lesen/Schreiben
     * In einem Intervall von ca. 26 uSec werden die Daten von/zu Port A gelesen/geschrieben.
     * Es gibt tatsächlich keine weitere Kommunikation zwischen dem 6522 und dem 325572-01.
     * Programmtechnisch ist die tiefste Ebene der 6522, d.h. der kann programmiert werden und auf den kann zugegriffen werden.
     * Der 325571-01 empfängt oder sendet einfach gesagt die Daten, die er bekommt weiter.
     *
     * zur Datenrichtung:
     * Sobald Pin 36 (OE) am 325572-01 auf L geht wird ein Byte geschrieben.
     * Wenn das geschehen ist, wird Pin 39 (Byte Ready) L und signalisiert dem Prozessor, dass das nächste Byte geschrieben werden kann. Ab
     * jetzt gibt es kein "Handshake" mehr. Es wird einfach eine bestimmte Zeit abgewartet (26 - 32 uSek., je nachdem auf welchem Track sich der Kopf befindet).
     *
     *  ------------
     *  Versuchen wir doch einfach mal das Ganze zusammenzufassen um das für Versuche mit dem AVR etwas klarer zu machen.
     *
     * LESEN
     * =====
     *
     * Ausgang PB2 MTR = H
     * der Motor läuft an, jetzt muss gewartet werden (~300ms(?))
     *
     * [ Wie man hier sehen kann dauert es 800 - 900 ms bis der Motor angelaufen ist und die ersten Signale für den Stepper gesendet werden.
     * (Ist aber auch möglich, dass vor den ersten Stepperimpulsen schon gelesen wird. Allerdings ist man nach einer Zeit von 800 - 900ms auf der sicheren Seite)]
     *
     * Ausgang STP0=L und STP1=L (wenn beide länger als 40ms (?) L sind bewegt sich der Schreib/Lesekopf nicht mehr)
     *
     * Ausgang CB2 MODE = H (H=lesen/L=schreiben)
     *
     * wenn ein Byte erfolgreich durchs Schieberegister ist, dann...
     * Ausgang CA2 Byte Sync Enable = H
     *
     * und das Byte kann bei PA0 - PA7 empfangen werden
     *
     *
     * SCHREIBEN
     * =========
     *
     *
     * Ausgang PB2 MTR = H
     * der Motor läuft an, jetzt muss gewartet werden (~300ms(?))
     *
     * Ausgang STP0=L und STP1=L (wenn beide länger als 40ms (?) L sind bewegt sich der Schreib/Lesekopf nicht mehr)
     *
     * Ausgang CB2 MODE = L (H=lesen/L=schreiben)
     *
     * jetzt wird ein Byte geschrieben (Dauer ca. 26 - 32µs)
     *
     * Eingang CA1 BYTE READY = L (?)
     *  	 */

    private VIA via1;
    private VIA via2;

    private final VIAChangeListener via1Listener = new VIAChangeListener()
    {
        /*
         * VIA1:
         *
         * PA0..7 - unused
         * PB0 - (IN) Data in
         * PB1 - (OUT) Data out
         * PB2 - (IN) Clock in
         * PB3 - (OUT) Clock out
         * PB4 - (OUT) ATN Acknowledge
         * PB5 - (IN) Device address
         * PB6 - (IN) Device address
         *
         * CA1 - Unused
         * CA2 - Unused
         * CB1 - Unused
         * CB2 - (IN) ATN IN
         */
        @Override
        public void portChanged(VIA via, Port port) {
            // TODO Auto-generated method stub

        }

        @Override
        public void controlLine1Changed(VIA via, Port port) {
            // TODO Auto-generated method stub

        }

        @Override
        public void controlLine2Changed(VIA via, Port port) {
            // TODO Auto-generated method stub
        }
    };

    private final VIAChangeListener via2Listener = new VIAChangeListener() {

        /* VIA2:
         *
         * PA0...8 - (IN/OUT) Read data/Write data
         *
         *
         * PB0 - (OUT) Step 1  |   Sequence 00/01/10/11/00... moves inwards (towards track 35)      |
         * PB1 - (OUT) Step 0  |   Sequence 00/11/10/01/00... moves outwards (towards track 1)    |
         * PB2 - (OUT) Drive motor AND Step Motor on/off
         * PB3 - (OUT) LED
         * PB4 - (INT) Write-protect
         * PB5 - (OUT) bitrate select 1
         * PB6 - (OUT) bitrate select 0
         * PB7 - (IN)  SYNC detected
         *
         * CA1 - (IN) Byte ready (to be read in read mode / to be written in write mode)
         *
         *                Das Byte Ready Signal kann nur mit SOE abgestellt werden,ansonsten kommt es regelmässig nach 8 Bits.
         *                Das ist unabhängig davon ob sich das Laufwerk dreht oder nicht. Das ist ein Zähler auf der Platine der vom SYNC Signal
         *                mit 0 geladen wird und immer wenn die Zeit für ein Bit abgelaufen ist eins hoch zählt.
         *                Wenn sich nichts tut (Kein Flusswechel am Lesekopf) denkt das Laufwerk, dass es ein "0" Bit ist.
         *                Wenn sich das Laufwerk nicht dreht kommen eben nur "0" Bits ...
         * CA2 - (OUT) SOE / Enable/disable Byte Ready signal (High = activate BYTE-READY)
         *
         * CB1 - Unused
         * CB2 - (OUT) Read/Write mode (Low = Write, High = Read))
         */
        @Override
        public void portChanged(VIA via, Port port)
        {
            if ( port.getPortName() == PortName.B )
            {
                final int value = port.getPins();

                DiskHardware.this.driveLED = (value & 0b0000_1000) != 0;      

                final boolean oldMotor = DiskHardware.this.motorsRunning; 
                final boolean newState = (value & 0b0000_0100) != 0;
                DiskHardware.this.motorsRunning = newState;

                if ( oldMotor && ! newState ) {
                    warmupFinished = false;
                    previousStepMotorCycle = 3;
                }

                final int speed = value & 0b0110_0000;

                final int newCyclesPerByte;
                switch( speed )
                {
                    case 0b0000_0000:
                        newCyclesPerByte = SPEED00_CYCLES_PER_BYTE;
                        break;
                    case 0b0010_0000:
                        newCyclesPerByte = SPEED01_CYCLES_PER_BYTE;
                        break;
                    case 0b0100_0000:
                        newCyclesPerByte = SPEED10_CYCLES_PER_BYTE;
                        break;
                    case 0b0110_0000:
                        newCyclesPerByte = SPEED11_CYCLES_PER_BYTE;
                        break;
                    default:
                        throw new RuntimeException("Unreachable code reached");
                }

                cyclesPerByte = newCyclesPerByte;

                /*
                 * PB0 - (OUT) Step 1  |   Sequence 00/01/10/11/00... moves inwards (towards track 35)      |
                 * PB1 - (OUT) Step 0  |   Sequence 00/11/10/01/00... moves outwards (towards track 1)    |
                 */
                int step = value & 0b11;
                if ( step != previousStepMotorCycle && motorsRunning )
                {
                    if ( previousStepMotorCycle == 0 ) 
                    {
                        headMovement = step == 3 ? HeadMovement.TO_TRACK_1 : HeadMovement.TO_TRACK_35;
                        warmupFinished = true;
                    } else if ( step > previousStepMotorCycle ){
                        headMovement = HeadMovement.TO_TRACK_35;
                    } else {
                        headMovement = HeadMovement.TO_TRACK_1;
                    }

                    if ( warmupFinished ) 
                    {
                        float newTrack = headMovement.move( headPosition );
                        if ( newTrack < 1.0f ) {
                            newTrack = 1f;
                        } else if ( newTrack > 41.5f ) {
                            newTrack = 41.5f;
                        }
                        if ( newTrack != headPosition ) {
                            setTrack( newTrack );
                        }
                    }
                }
                previousStepMotorCycle = step;                
            }
        }

        @Override
        public void controlLine1Changed(VIA via, Port port) {
        }

        @Override
        public void controlLine2Changed(VIA via, Port port)
        {
            if ( port.getPortName() == PortName.B )
            {
                setDriveMode( port.getControlLine2() ? READ : WRITE );
            }
        }

    };

    public DiskHardware(DiskDrive diskDrive,VIA via1,VIA via2,int driveAddress)
    {
        if ( driveAddress < 8 || driveAddress > 11 ) {
            throw new IllegalArgumentException("Invalid drive address "+driveAddress);
        }
        this.cpu = diskDrive.getCPU();
        this.diskDrive = diskDrive;
        this.driveAddress = driveAddress;
        this.via1=via1;
        this.via2=via2;
        via1.setChangeListener( via1Listener );
        via2.setChangeListener( via2Listener );
    }
    
    @Override
    public void reset()
    {
        diskDrive.reset();

        setDriveMode( READ );
        
        driveLED = false;

        motorsRunning = false;

        writeProtectOn = true; // TODO: Set to 'false' once writing to disk is enabled
        
        warmupFinished = false;
        previousStepMotorCycle = 3;
        headPosition = 18f;
        headMovement = HeadMovement.NONE;

        cyclesPerByte = SPEED10_CYCLES_PER_BYTE; // default speed for track #18

        // setup VIA1 inputs

        via1.getPortB().setInputPins( 0 ); // clear input
        via1.getPortB().setControlLine2(false,false); // clear ATN

        // set drive address
        final int adr = this.driveAddress - 8;
        via1.getPortB().setInputPin( 5,  ( adr & 1 ) != 0 );
        via1.getPortB().setInputPin(  6 , ( adr & 2 ) != 0);

        // setup VIA2 inputs

        /*
         * PA0...8 - (IN/OUT) Read data/Write data
         *
         *
         * PB0 - (OUT) Step 1  |   Sequence 00/01/10/11/00... moves inwards      |
         * PB1 - (OUT) Step 0  |   Sequence 00/11/10/01/00... moves outwards     |
         * PB2 - (OUT) Drive motor AND Step Motor on/off
         * PB3 - (OUT) LED
         * PB4 - (IN) Write-protect
         * PB5 - (OUT) bitrate select 1
         * PB6 - (OUT) bitrate select 0
         * PB7 - (IN)  SYNC detected
         *
         * CA1 - (IN) Byte read / Byte written (to be read in read mode / to be written in write mode)
         *
         *                Das Byte Ready Signal kann nur mit SOE abgestellt werden,ansonsten kommt es regelmässig nach 8 Bits.
         *                Das ist unabhängig davon ob sich das Laufwerk dreht oder nicht. Das ist ein Zähler auf der Platine der vom SYNC Signal
         *                mit 0 geladen wird und immer wenn die Zeit für ein Bit abgelaufen ist eins hoch zählt.
         *                Wenn sich nichts tut (Kein Flusswechel am Lesekopf) denkt das Laufwerk, dass es ein "0" Bit ist.
         *                Wenn sich das Laufwerk nicht dreht kommen eben nur "0" Bits ...
         * CA2 - (OUT) SOE / Enable/disable Byte Ready signal (High = activate BYTE-READY)
         *
         * CB1 - Unused
         * CB2 - (OUT) Read/Write mode (Low = Write, High = Read))
         */
        via2.getPortB().setInputPin( 7 , false ); // clear sync detected signal
        via2.getPortB().setControlLine1(false,false); // clear 'Byte read/written' signal
        via2.getPortB().setInputPin( 4 , writeProtectOn ); // => disk is write protected
    }

    public void setDriveMode(DriveMode mode)
    {
        if ( mode != this.driveMode )
        {
            this.driveMode = mode;
            mode.onEnter();
        }
    }

    public void ejectDisk() {
        currentTrackData = Optional.empty();
        bitStream = new BitStream(new byte[0] , 0 );
    }

    private void setTrack(float track)
    {
        this.headPosition = track;

        if ( disk != null )
        {
            currentTrackData = disk.getTrackData( track );
        }
        else {
            currentTrackData = Optional.empty();
        }

        if ( currentTrackData.isPresent() )
        {
            final byte[] data = currentTrackData.get().getRawBytes();
            bitStream = new BitStream( data , data.length*8 );
        } else {
            bitStream = new BitStream(new byte[0x55] , 8 ); // fake bitstream
        }
        driveMode.trackChanged();
    }

    public void loadDisk(G64File disk) 
    {
        System.out.println("Loaded disk "+(disk==null?"<no disk>" : disk.getSource() ) );
        this.disk = disk;
        setTrack( 18 );
    }

    // ====== SerialDevice interface implementation ==========

    @Override
    public void tick(Emulator emulator,IECBus bus)
    {
        /* VIA1:
         *
         * PB0 - (IN) Data in
         * PB1 - (OUT) Data out
         * PB2 - (IN) Clock in
         * PB3 - (OUT) Clock out
         * PB4 - (OUT) ATN Acknowledge
         * PB5 - (IN) Device address bit #1
         * PB6 - (IN) Device address bit #0
         * PB7 - (IN) ATN IN
         *
         * CA1 - (IN) ATN IN
         * CA2 - Unused
         * CB1 - Unused
         * CB2 - Unused
         */

        final Port portB = via1.getPortB();

        final boolean newData = ! bus.getData();
        final boolean newClk  = ! bus.getClk();
        final boolean newATN  = ! bus.getATN();

        final boolean atnXor= ( via1.getPortB().getPin(7) ^ via1.getPortB().getPin(4) );
        portB.setInputPin( 0 , newData | atnXor );
        portB.setInputPin( 2 , newClk );

        portB.setInputPin( 7 , newATN );
        via1.getPortA().setControlLine1( newATN , false );

        driveMode.tick();

        this.via1.tick();
        this.via2.tick();

        if ( ! diskDrive.executeOneCPUCycle() ) {
            emulator.hardwareBreakpointReached();
        }        
    }

    @Override
    public boolean getData()
    {
        final boolean atn = via1.getPortB().getPin(7) ^ via1.getPortB().getPin(4);
        return ! ( via1.getPortB().getPin(1) | atn );
    }

    @Override
    public boolean getClock()
    {
        return ! via1.getPortB().getPin(3);
    }

    @Override
    public boolean getATN() {
        throw new RuntimeException("Should not been called, only used on CPU");
    }

    @Override
    public int getPrimaryAddress() {
        return driveAddress;
    }

    @Override
    public boolean isDataTransferActive()
    {
        return this.motorsRunning;
    }

    public Optional<G64File> getDisk() {
        return Optional.ofNullable( disk );
    }

    public CPU getCPU() {
        return diskDrive.getCPU();
    }

    public IMemoryRegion getMemory() {
        return diskDrive;
    }

    public DiskDrive getDiskDrive() {
        return diskDrive;
    }

    public float getHeadPosition() {
        return headPosition;
    }

    public DriveMode getDriveMode() {
        return driveMode;
    }

    public boolean getDriveLED() {
        return driveLED;
    }

    public boolean getMotorStatus() {
        return this.motorsRunning;
    }
}