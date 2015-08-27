package de.codesourcery.j6502.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;

import javax.swing.DefaultListCellRenderer;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JPanel;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileFilter;

import de.codesourcery.j6502.emulator.G64File;
import de.codesourcery.j6502.ui.G64ViewPanel.ViewMode;

public class G64Viewer extends JPanel
{
    private final G64ViewPanel viewerPanel = new G64ViewPanel();
    private final JComboBox<ViewMode> viewModeSelector = new JComboBox<>( ViewMode.values() );
    private ViewMode viewMode = ViewMode.DATA;

    private File lastLoadedFile;
    private File lastSavedFile;

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

        final JButton loadDiskButton = new JButton("Open file...");
        loadDiskButton.addActionListener( ev -> loadDisk() );

        final JButton saveDiskButton = new JButton("Save as D64...");
        saveDiskButton.addActionListener( ev -> saveDisk() );

        setLayout( new GridBagLayout() );

        // view mode selection
        GridBagConstraints cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.NONE;
        cnstrs.insets = new Insets(10,10,10,10);
        cnstrs.anchor = GridBagConstraints.EAST;
        cnstrs.gridx =0; cnstrs.gridy =0;
        cnstrs.weightx = 0 ; cnstrs.weighty = 0;
        cnstrs.gridwidth =1 ; cnstrs.gridheight =1;

        add( viewModeSelector , cnstrs );

        // 'Load Disk'
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(10,10,10,10);
        cnstrs.anchor = GridBagConstraints.WEST;
        cnstrs.fill = GridBagConstraints.NONE;
        cnstrs.gridx =1; cnstrs.gridy =0;
        cnstrs.weightx = 0 ; cnstrs.weighty = 0;
        cnstrs.gridwidth =1 ; cnstrs.gridheight =1;

        add( loadDiskButton , cnstrs );

        // 'Save Disk'
        cnstrs = new GridBagConstraints();
        cnstrs.insets = new Insets(10,10,10,10);
        cnstrs.anchor = GridBagConstraints.WEST;
        cnstrs.fill = GridBagConstraints.NONE;
        cnstrs.gridx =2; cnstrs.gridy =0;
        cnstrs.weightx = 0 ; cnstrs.weighty = 0;
        cnstrs.gridwidth =1 ; cnstrs.gridheight =1;

        add( saveDiskButton , cnstrs );

        // disk viewer
        cnstrs = new GridBagConstraints();
        cnstrs.fill = GridBagConstraints.BOTH;
        cnstrs.gridx =0; cnstrs.gridy =1;
        cnstrs.gridwidth = 3 ; cnstrs.gridheight =1;
        cnstrs.weightx = 1.0 ; cnstrs.weighty = 1.0;
        add( viewerPanel , cnstrs );
    }

	private void saveDisk()
	{
		if ( viewerPanel.getDisk() == null ) {
			return;
		}
		final JFileChooser chooser = new JFileChooser();
		if ( lastSavedFile != null ) {
			chooser.setSelectedFile( lastSavedFile );
		}
		if ( chooser.showSaveDialog( null ) == JFileChooser.APPROVE_OPTION )
		{
			lastSavedFile = chooser.getSelectedFile();
			try ( FileOutputStream out = new FileOutputStream( chooser.getSelectedFile() ) )
			{
				viewerPanel.getDisk().toD64( out );
			}
			catch(IOException e) {
				e.printStackTrace();
			}
		}
	}

	private void loadDisk() {
		final JFileChooser chooser = new JFileChooser();
		if ( lastLoadedFile != null ) {
			chooser.setSelectedFile( lastLoadedFile );
		}
		chooser.setFileFilter( new FileFilter() {

			@Override
			public boolean accept(File f) {
				return f.isDirectory() || ( f.isFile() && f.getName().toLowerCase().endsWith("g64") );
			}

			@Override
			public String getDescription() {
				return ".g64";
			}

		});
		if ( chooser.showOpenDialog( null ) == JFileChooser.APPROVE_OPTION )
		{
			try ( FileInputStream in = new FileInputStream( chooser.getSelectedFile() ) )
			{
				viewerPanel.setDisk( new G64File( in ) );
				lastLoadedFile = chooser.getSelectedFile();
			} catch(IOException e) {
				e.printStackTrace();
			}
		}
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