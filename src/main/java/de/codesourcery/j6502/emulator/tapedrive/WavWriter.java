package de.codesourcery.j6502.emulator.tapedrive;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;

/**
 * This class handles the reading, writing, and playing of wav files. It is
 * also capable of converting the file to its raw byte [] form.
 *
 * based on code by Evan Merz modified by Dan Vargo
 * @author dvargo
 */
public class WavWriter 
{
	public static void write(File out,byte[] samples) throws IOException 
	{
		AudioFormat format = new AudioFormat(
				AudioFormat.Encoding.PCM_UNSIGNED, // the audio encoding technique                                                                                              
                8000f,                             // the number of samples per second                                                                                          
                8,                                 // the number of bits in each sample                                                                                         
                1,                                 // the number of channels (1 for mono, 2 for stereo, and so on)                                                              
                1,                                 // the number of bytes in each frame                                                                                         
                8000f,                             // the number of frames per second                                                                                           
                true);		                       // indicates whether the data for a single sample is stored in big-endian byte order (<code>false</code> means little-endian)

	
	  final AudioInputStream audioInputStream = new AudioInputStream( new ByteArrayInputStream( samples) , format , samples.length );
	  final AudioFileFormat.Type fileType = AudioFileFormat.Type.WAVE; 
	  try ( FileOutputStream fileOutputStream = new FileOutputStream( out ) ) {
		AudioSystem.write(audioInputStream, fileType, fileOutputStream);
	  }
	}
	
    public static void main(String[] args) throws IOException 
    {
    	final T64File file = new T64File( new File("/home/tobi/mars_workspace/j6502/tapes/choplifter.t64") );
    	
    	final SquareWaveDriver driver = new SquareWaveDriver();
    	driver.insert( file );
    	
    	final ByteArrayOutputStream out = new ByteArrayOutputStream();
    	System.out.println("Writing "+driver.wavesRemaining()+" samples...");
    	
    	int current;
    	long tick = 0;
    	while ( (current = driver.wavesRemaining() ) > 0 ) 
    	{
			if ( ( current % 100000 ) == 0 ) {
				System.out.println("Remaining: "+current);
			}
			driver.tick();
			System.out.println( tick+" : "+driver.currentSignal());
			tick++;
			if ( driver.currentSignal() ) {
				out.write( 0xff );
			} else {
				out.write( 0x00);
			}
    	}
    	WavWriter.write( new File("/home/tgierke/out.wav") , out.toByteArray() );
    	System.out.println("Done.");
	}	
}