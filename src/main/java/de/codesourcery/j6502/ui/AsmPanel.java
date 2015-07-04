package de.codesourcery.j6502.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.file.Files;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Optional;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JEditorPane;
import javax.swing.JFileChooser;
import javax.swing.JInternalFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JToolBar;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;

import de.codesourcery.j6502.assembler.Assembler;
import de.codesourcery.j6502.assembler.exceptions.ParseException;
import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Parser;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.ui.WindowLocationHelper.ILocationAware;
import de.codesourcery.j6502.utils.HexDump;
import de.codesourcery.j6502.utils.SourceHelper;
import de.codesourcery.j6502.utils.SourceHelper.TextLocation;

public abstract class AsmPanel extends JPanel implements ILocationAware 
{
	public static final String PANEL_TITLE = "ASM";
	
	private boolean editorDirtyFlag = false;
	private JEditorPane editor = new JEditorPane();
	
	private JToolBar toolbar = new JToolBar();

	private final MyTableModel compilationMessageModel = new MyTableModel();
	private JTable compilationMessages = new JTable(compilationMessageModel);
	
	private final DefaultListModel<String> generalMessageModel = new DefaultListModel<String>();
	private final JList<String> generalMessages = new JList<String>(generalMessageModel);

	private Component locationPeer;
	
	private int binaryStartAddress;
	private byte[] binary;
	
	private File lastSavedFile;
	private File lastLoadedFile;
	
	private Emulator emulator;

	protected static enum Severity { INFO,WARN,ERROR }

	protected static final class CompilationMessage 
	{
		public final Severity severity;
		public final String message;
		public final int lineNo;
		public final int colNo;

		public CompilationMessage(String message, Severity severity,int lineNo, int colNo) {
			this.severity = severity;
			this.message = message;
			this.lineNo = lineNo;
			this.colNo = colNo;
		}
	}
	
	protected final class MyTableModel extends AbstractTableModel 
	{
		private final List<CompilationMessage> modelData = new ArrayList<>();

		@Override
		public int getRowCount() {
			return modelData.size();
		}

		@Override
		public String getColumnName(int column)
		{
			switch(column) 
			{
				case 0: return "Severity";
				case 1: return "Message";
				case 2: return "Location";
				default:
					throw new IllegalArgumentException("Unhandled column: "+column);
			}
		}

		@Override
		public Class<?> getColumnClass(int columnIndex) {
			return String.class;
		}

		@Override
		public int getColumnCount() {
			return 3;
		}

		@Override
		public Object getValueAt(int rowIndex, int columnIndex) 
		{
			final CompilationMessage msg = modelData.get(rowIndex);
			switch(columnIndex) 
			{
				case 0: return msg.severity.toString();
				case 1: return msg.message;
				case 2: return "line "+msg.lineNo+", column "+msg.colNo;
				default:
					throw new IllegalArgumentException("Unhandled column: "+columnIndex);
			}
		}

		public void addMessage(CompilationMessage msg) 
		{
			int row = this.modelData.size();
			this.modelData.add( msg );
			fireTableRowsInserted( row , row );
		}

		public void clearMessages() {
			this.modelData.clear();
			fireTableDataChanged();
		}
	}

	public AsmPanel() {

		Debugger.setup( this );
		
		final JButton compile = new JButton("compile");
		compile.addActionListener( ev -> assemble()  );
		
		final JButton load = new JButton("Load");
		load.addActionListener( ev -> loadSource()  );
		
		final JButton save = new JButton("Save");
		save.addActionListener( ev -> saveSource()  );
		
		final JButton upload = new JButton("Upload");
		upload.addActionListener( ev -> upload()  );
		
		toolbar.add( compile );		
		toolbar.add( load );
		toolbar.add( save );
		toolbar.add( upload );		
		
		compilationMessages.setFillsViewportHeight( true );
		generalMessages.setVisibleRowCount( 5 );
		
		editor.getDocument().addDocumentListener( new DocumentListener() {

			@Override
			public void insertUpdate(DocumentEvent e) {
				setEditorDirty( true );
				binary = null;
			}

			@Override
			public void removeUpdate(DocumentEvent e) {
				setEditorDirty( true );
				binary = null;
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				setEditorDirty( true );
				binary = null;
			}
		});
		
		// add key listener
		editor.addKeyListener( new KeyAdapter() 
		{
			@Override
			public void keyReleased(KeyEvent e) 
			{
				if ( e.getKeyCode() == KeyEvent.VK_S && ( e.getModifiersEx() & KeyEvent.CTRL_DOWN_MASK) != 0 ) 
				{
					if ( lastSavedFile != null ) 
					{
						saveSourceFile( lastSavedFile );
					} else {
						saveSource();
					}
					assemble();
				}
			}
		});		
		
		Debugger.setColors(toolbar);
		Debugger.setup(editor);
		Debugger.setup(compilationMessages);
		Debugger.setup( generalMessages );
		
		setLayout( new GridBagLayout() );
		
		// add toolbar
		GridBagConstraints cnstrs = new GridBagConstraints();
		cnstrs.gridx = 0; cnstrs.gridy = 0;
		cnstrs.gridwidth=2;
		cnstrs.fill = GridBagConstraints.HORIZONTAL;
		cnstrs.weightx = 1 ; cnstrs.weighty = 0.0;
		add( toolbar , cnstrs );
		
		// add editor
		editor.setPreferredSize( new Dimension(400,200 ) );
		final JScrollPane editorPane = new JScrollPane(editor);
		Debugger.setup( editorPane );
		
		cnstrs = new GridBagConstraints();
		cnstrs.gridx = 0; cnstrs.gridy = 1;
		cnstrs.gridwidth=2;
		cnstrs.fill = GridBagConstraints.BOTH;
		cnstrs.weightx = 1 ; cnstrs.weighty = 0.8;
		add( editorPane , cnstrs );
		
		// add compilation and general messages
		final JScrollPane pane1 = new JScrollPane(compilationMessages);
		Debugger.setup( pane1 );
		final JScrollPane pane2 = new JScrollPane(generalMessages);
		Debugger.setup( pane2 );
		
		cnstrs = new GridBagConstraints();
		cnstrs.gridx = 0; cnstrs.gridy = 2;
		cnstrs.fill = GridBagConstraints.BOTH;
		cnstrs.weightx = 0.5 ; cnstrs.weighty = 0.2;
		add( pane1 , cnstrs );
		
		cnstrs = new GridBagConstraints();
		cnstrs.gridx = 1; cnstrs.gridy = 2;
		cnstrs.fill = GridBagConstraints.BOTH;
		cnstrs.weightx = 0.5 ; cnstrs.weighty = 0.2;
		add( pane2 , cnstrs );

		setPreferredSize( new Dimension(400,200 ) );
	}
	
	private void setEditorDirty(boolean dirty) 
	{
		final boolean stateChanged = editorDirtyFlag != dirty;
		editorDirtyFlag = dirty;
		if ( stateChanged && locationPeer instanceof JInternalFrame) 
		{
			if ( dirty ) {
				((JInternalFrame) locationPeer).setTitle("*"+PANEL_TITLE);
			} else {
				((JInternalFrame) locationPeer).setTitle( PANEL_TITLE );
			}
		} 
	}
	
	private void upload() 
	{
		if ( binary == null ) 
		{
			if ( ! assemble() ) 
			{
				return;
			}
		}
		
		synchronized(emulator) 
		{
			emulator.getMemory().bulkWrite( binaryStartAddress , binary , 0 , binary.length );
		}
		info("Binary uploaded to "+HexDump.toHexBigEndian( binaryStartAddress ) );
		binaryUploadedToEmulator();
	}

	private void loadSource() 
	{
		selectFile(false).ifPresent( file -> 
		{
			try {
				editor.setText( new String(Files.readAllBytes( file.toPath() ) ) );
				info("Source loaded from "+file.getAbsolutePath());
			} 
			catch (IOException e) 
			{
				e.printStackTrace();
			}
		});
	}
	
	private void saveSource() 
	{
		selectFile(false).ifPresent(this::saveSourceFile);
	}
	
	private boolean saveSourceFile(File file) 
	{
		try 
		{
			 final FileWriter writer = new FileWriter( file ); 
			 writer.write( editor.getText() );
			 writer.close();
			 info("Source saved to "+file.getAbsolutePath());
			 lastSavedFile = file;
			 setEditorDirty( false );
			 return true;
		} catch(IOException e) {
			error("Failed to save to file "+file.getAbsolutePath(),e);
		}
		return false;
	}	
	
	private void info(String message) 
	{
		DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		generalMessageModel.add(0,df.format( new Date() )+" - "+message);
	}
	
	private void error(String message)
	{
		error(message,null);
	}
	
	private void error(String message,Throwable t) 
	{
		String msg = message;
		if ( t != null ) {
			msg += "\n";
			ByteArrayOutputStream out = new ByteArrayOutputStream();
			PrintWriter writer = new PrintWriter( out);
			t.printStackTrace( writer );
			writer.close();
			try {
				msg += out.toString( "UTF-8");
			} catch (UnsupportedEncodingException e) {
				e.printStackTrace();
			}
		}
		JOptionPane.showMessageDialog(null, msg, "ERROR", JOptionPane.ERROR_MESSAGE);
	}
	
	private Optional<File> selectFile(boolean showLoadDialog) 
	{
		final JFileChooser chooser;
		
		final File lastFile;
		if ( showLoadDialog ) {
			lastFile = lastLoadedFile;
		} else {
			lastFile = lastSavedFile;
		}
		
		if ( lastFile != null && lastFile.getParentFile().exists() ) {
			chooser = new JFileChooser( lastFile );
		} else {
			chooser = new JFileChooser();
		}
		int result;
		if ( showLoadDialog ) {
			result = chooser.showOpenDialog( null );
		} else {
			result = chooser.showSaveDialog( null );
		}
		if ( result != JFileChooser.APPROVE_OPTION) {
			return Optional.empty();
		}
		
		final File file = chooser.getSelectedFile();
		if ( showLoadDialog ) {
			lastLoadedFile = file;
		} else {
			lastSavedFile = file;
		}
		return Optional.of( file );
	}
	
	private boolean assemble() 
	{
		binary = null;
		binaryStartAddress = 0xffffffff;
		
		final String source = editor.getText();
		final Parser p = new Parser(new Lexer(new Scanner(source)));

		compilationMessageModel.clearMessages();
		
		final Assembler a = new Assembler();
		final SourceHelper sourceHelper = new SourceHelper(source);
		try {
			final AST ast = p.parse();
			binary = a.assemble( ast , sourceHelper );
			binaryStartAddress = a.getOrigin();
			compilationMessageModel.addMessage( new CompilationMessage("Code compiled ok ("+binary.length+" bytes)", Severity.INFO, 0, 0 ) );
			info("Compilation finished");
			return true;
		} 
		catch(ParseException e) {
			TextLocation location = sourceHelper.getLocation( e.offset );
			e.printStackTrace();
			if ( location == null ) {
				compilationMessageModel.addMessage( new CompilationMessage("Compilation failed: "+e.getMessage() , Severity.ERROR, 0, 0 ) );
			} else {
				compilationMessageModel.addMessage( new CompilationMessage("Compilation failed: "+e.getMessage() , Severity.ERROR, location.lineNumber , location.columnNumber ) );
			}
		}
		catch(Exception e) 
		{
			e.printStackTrace();
			compilationMessageModel.addMessage( new CompilationMessage("Compilation failed: "+e.getMessage() , Severity.ERROR, 0, 0 ) );
		}
		info("Compilation FAILED.");
		return false;
	}

	@Override
	public void setLocationPeer(Component frame) {
		this.locationPeer = frame;
	}

	@Override
	public Component getLocationPeer() {
		return this.locationPeer;
	}

	public void setEmulator(Emulator emulator) {
		this.emulator = emulator;
	}
	
	protected abstract void binaryUploadedToEmulator();
}