package de.codesourcery.j6502.emulator;

import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.util.List;

import javax.swing.JFrame;

import de.codesourcery.j6502.emulator.IECBus.StateSnapshot;
import de.codesourcery.j6502.ui.BusPanel;

public class IEBusSimulator 
{
	public static void main(String[] args) 
	{
		IECBus sender = new IECBus("sender");
		IECBus receiver = new IECBus("receiver");
//		receiver.DEBUG_VERBOSE = false;
		
		sender.setOutputWire( receiver.getInputWire() );		
		sender.setInputWire(  receiver.getOutputWire() );
		
		// receiver analyzer
		JFrame recFrame = new JFrame("Bus analyzer receiver");
		recFrame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );
		recFrame.setPreferredSize( new Dimension(640,480 ) );
		
		final BusPanel busPanel = new BusPanel("Receiver") {
			
			@Override
			protected List<StateSnapshot> getBusSnapshots() {
				return receiver.getSnapshots();
			}
		};
		
		// sender frame
		
		final BusPanel busPanel2 = new BusPanel("Sender") {
			
			@Override
			protected List<StateSnapshot> getBusSnapshots() {
				return sender.getSnapshots();
			}
		};
		
		recFrame.getContentPane().setLayout(new GridBagLayout());
		GridBagConstraints cnstrs = new GridBagConstraints();
		cnstrs.gridx=0;
		cnstrs.gridy=0;
		cnstrs.weightx=1;
		cnstrs.weighty=0.5;
		cnstrs.gridheight=1;
		cnstrs.gridwidth=1;
		cnstrs.fill = GridBagConstraints.BOTH;
		recFrame.getContentPane().add( busPanel , cnstrs );
		
		cnstrs = new GridBagConstraints();
		cnstrs.gridx=0;
		cnstrs.gridy=1;
		cnstrs.weightx=1;
		cnstrs.weighty=0.5;
		cnstrs.gridheight=1;
		cnstrs.gridwidth=1;
		cnstrs.fill = GridBagConstraints.BOTH;
		recFrame.getContentPane().add( busPanel2 , cnstrs );
		recFrame.pack();
		recFrame.setVisible( true );
		
		sender.send( (byte) 0x28 , false , true ); // LISTEN #8
		sender.send( (byte) 0xf0 , false , false ); // OPEN CHANNEL #0
		sender.send( (byte) 0x24 , true , false ); // '$'
		sender.send( (byte) 0x3f , false , false ); // UNLISTEN
		sender.send( (byte) 0x48 , false , false ) ; // TALK #8
		sender.send( (byte) 0x60 , false , false ); // OPEN CHANNEL #0

//		sender.CAPTURE_BUS_SNAPSHOTS = false;
//		receiver.CAPTURE_BUS_SNAPSHOTS = true;
		int cycles = 5000;
		while ( cycles-- > 0 ) 
		{
			sender.tick();
			receiver.tick();
			busPanel.repaint();
		}
		if ( sender.isSendBufferEmpty() ) {
			System.out.println("All bytes sent !");
		} else {
			System.err.println("Not all bytes sent ?");
		}
	}
}
