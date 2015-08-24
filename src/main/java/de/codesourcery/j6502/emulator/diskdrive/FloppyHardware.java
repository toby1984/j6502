package de.codesourcery.j6502.emulator.diskdrive;

public class FloppyHardware {

	/*
	 * 1541 uses 16 Mhz base clock.
	 *
	 * Tracks Clock Frequency Divide By
     * 1-17    1.2307 MHz 13
     * 18-24  1.1428 MHz  14
     * 15-30  1.0666 MHz  15
     * 31-35  1 MHz       16
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
	 */
}
