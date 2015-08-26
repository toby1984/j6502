package de.codesourcery.j6502.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;

import de.codesourcery.j6502.emulator.G64File;
import de.codesourcery.j6502.ui.G64ViewPanel.ViewMode;

public class G64Viewer extends JPanel
{
    private final G64ViewPanel viewerPanel = new G64ViewPanel();
    private final JComboBox<ViewMode> viewModeSelector = new JComboBox<>( ViewMode.values() );
    private ViewMode viewMode = ViewMode.DATA;
    
    public static void main(String[] args) throws IOException, InvocationTargetException, InterruptedException {

        SwingUtilities.invokeAndWait( () -> 
        {
            try {
                final JFrame frame = new JFrame("test");
                frame.setDefaultCloseOperation( JFrame.EXIT_ON_CLOSE );

                final InputStream in = G64File.class.getResourceAsStream( "/disks/pitfall.g64" );
                final G64File file = new G64File( in );

                frame.getContentPane().add( new G64Viewer( file ) );

                frame.pack();
                frame.setLocationRelativeTo( null );
                frame.setVisible( true );
            } catch (Exception e) {
                e.printStackTrace();
                System.exit(0);;
            }
        });
    }
    
    public G64Viewer() {
        this(null);
    }
    
    public G64Viewer(G64File file) 
    {
        setPreferredSize(new Dimension(640,480) );

        viewerPanel.setDisk( file );
        
        viewModeSelector.setRenderer( new DefaultListCellRenderer() 
        {
            @Override
            public Component getListCellRendererComponent(JList<?> list,Object value, int index, boolean isSelected,boolean cellHasFocus) 
            {
                final Component result = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);
                if ( value instanceof ViewMode ) 
                {
                    setText( ((ViewMode) value).getLabel() );
                }
                return result;
            }
        });
        viewModeSelector.setSelectedItem( viewMode );
        viewModeSelector.addActionListener( ev -> 
        {
            setViewMode( (ViewMode) viewModeSelector.getSelectedItem() );
        });        

        setLayout( new GridBagLayout() );

        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.NONE;
        cnstrs.gridx =0; cnstrs.gridy =0;
        cnstrs.weightx = 0 ; cnstrs.weighty = 0;
        cnstrs.gridwidth =1 ; cnstrs.gridheight =1;

        add( viewModeSelector , cnstrs );

        cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.gridx =0; cnstrs.gridy =1;
        cnstrs.gridwidth =1 ; cnstrs.gridheight =1;        
        cnstrs.weightx = 1.0 ; cnstrs.weighty = 1.0;
        add( viewerPanel , cnstrs );                

    }
    
    public void setViewMode(ViewMode viewMode) 
    {
        viewerPanel.setViewMode( viewMode );
        viewerPanel.repaint();
    }
    
    public void setDisk(G64File disk) 
    {
        viewerPanel.setDisk( disk );
        viewerPanel.repaint();
    }
}