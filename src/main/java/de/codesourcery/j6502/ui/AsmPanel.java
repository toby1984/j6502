package de.codesourcery.j6502.ui;

import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
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
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JDesktopPane;
import javax.swing.JFileChooser;
import javax.swing.JInternalFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.JTree;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;
import javax.swing.tree.DefaultTreeCellRenderer;
import javax.swing.tree.DefaultTreeModel;
import javax.swing.tree.TreeNode;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.assembler.Assembler;
import de.codesourcery.j6502.assembler.exceptions.ParseException;
import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Parser;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.assembler.parser.ast.CommentNode;
import de.codesourcery.j6502.assembler.parser.ast.IASTNode;
import de.codesourcery.j6502.assembler.parser.ast.InstructionNode;
import de.codesourcery.j6502.assembler.parser.ast.NumberLiteral;
import de.codesourcery.j6502.assembler.parser.ast.Statement;
import de.codesourcery.j6502.emulator.Emulator;
import de.codesourcery.j6502.ui.WindowLocationHelper.ILocationAware;
import de.codesourcery.j6502.utils.HexDump;
import de.codesourcery.j6502.utils.ITextRegion;
import de.codesourcery.j6502.utils.SourceHelper;
import de.codesourcery.j6502.utils.SourceHelper.TextLocation;

public abstract class AsmPanel extends JPanel implements ILocationAware 
{
	public static final String PANEL_TITLE = "ASM";
	protected static final int RECOMPILATION_MILLIS = 250;
	
	public static final int INDENT_SPACES = 4;
	protected static final String INDENT = StringUtils.repeat(" ", INDENT_SPACES );
	
	private volatile boolean editorDirtyFlag = false;
	private volatile boolean documentListenerEnabled = true;
	
	private boolean isShown = true;
	
	protected final StyleContext styleContext = new StyleContext();
	protected final DefaultStyledDocument styledDocument = new DefaultStyledDocument( styleContext ) 
	{
		public void insertString(int offs, String str, javax.swing.text.AttributeSet a) throws javax.swing.text.BadLocationException {
			
			final boolean atEndOfText = offs == getLength();
			if ( str.contains("\n" ) && atEndOfText ) 
			{
				super.insertString(offs, str.replace("\t",  INDENT )+INDENT, a);
			} else {
				super.insertString(offs, str.replace("\t",  INDENT ), a);
			}
		};
	};
	private JTextPane editor = new JTextPane(styledDocument);
	
	private JToolBar toolbar = new JToolBar();

	private final MyTableModel compilationMessageModel = new MyTableModel();
	private JTable compilationMessages = new JTable(compilationMessageModel);
	
	private final DefaultListModel<String> generalMessageModel = new DefaultListModel<String>();
	private final JList<String> generalMessages = new JList<String>(generalMessageModel);

	private Component locationPeer;
	private boolean displayed;
	
	private int binaryStartAddress;
	private byte[] binary;
	
	private File lastSavedFile;
	private File lastLoadedFile;
	
	private Emulator emulator;
	
	private final JDesktopPane desktop;
	
	private final ASTView astView = new ASTView();
	
	private final Style LITERAL_STYLE = createStyle("literal" , Color.YELLOW );
	private final Style INSTRUCTION_STYLE = createStyle("instruction" , Color.BLUE );
	private final Style COMMENT_STYLE = createStyle("comment" , Color.RED );
	
	private final RecompilationThread recompilationThread = new RecompilationThread();

	
	protected final class RecompilationThread extends Thread {
		
		private final AtomicBoolean recompilationNeeded = new AtomicBoolean(false);
		private final AtomicInteger timeout = new AtomicInteger(RECOMPILATION_MILLIS);
		
		public RecompilationThread() 
		{
			setName("recompilation-thread");
			setDaemon(true);
		}
		
		@Override
		public void run() 
		{
			while( true ) 
			{
				try {
					Thread.sleep( 50 );
				}
				catch(Exception e) { /* NOP */ }
				
				if ( recompilationNeeded.get() ) 
				{
					timeout.set( timeout.get() - 50 );
					if ( timeout.get() <= 0 ) 
					{
						recompilationNeeded.set(false);
						timeout.set( RECOMPILATION_MILLIS );
						SwingUtilities.invokeLater( () -> assemble() );
					}
				}
			}
		}
		
		public void textChanged() 
		{
			recompilationNeeded.set( true );
			timeout.set( RECOMPILATION_MILLIS );
		}
	}

	protected final class ASTView extends JInternalFrame 
	{
		private AST ast;
		
		private final DefaultTreeModel treeModel = new DefaultTreeModel( createEmptyTree() );

		private final JTree tree = new JTree(treeModel);
		
		public ASTView() 
		{
			super("AST view",true);
			
			tree.setRootVisible( false );
			tree.setVisibleRowCount( 5 );
			tree.setPreferredSize( new Dimension(200,400 ) );
			tree.setCellRenderer( new DefaultTreeCellRenderer() {
				@Override
				public Component getTreeCellRendererComponent(JTree tree,
						Object value, boolean sel, boolean expanded,
						boolean leaf, int row, boolean hasFocus) 
				{
					final Component result = super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row,hasFocus);
					if ( value instanceof WrappedASTNode) 
					{
						final IASTNode delegate = ((WrappedASTNode) value).delegate;
						final ITextRegion region = delegate.getClass() == Statement.class ? delegate.getTextRegionIncludingChildren() : delegate.getTextRegion();
						String label = delegate.getClass().getSimpleName();
						if ( region != null )
						{
							try {
								label += ": "+editor.getText().substring( region.getStartingOffset() , region.getEndOffset() );
							} 
							catch(Exception e) { /* can't help it */ }
						}
						setText( label );
					}
					return result;
				}
			});
			final JScrollPane pane = new JScrollPane( tree );
			getContentPane().add( pane );
			pack();
		}
		
		public void setAST(AST ast) 
		{
			this.ast = ast;
			if ( ast != null ) 
			{
				treeModel.setRoot( createTree( null , ast ) ); 
			} 
			else 
			{
				treeModel.setRoot( createEmptyTree() );
			}
		}
		
		private WrappedASTNode createEmptyTree() {
			return createTree( null , new AST() ); 
		}
		
		private WrappedASTNode createTree(final WrappedASTNode parent,IASTNode child) 
		{
			final WrappedASTNode result = new WrappedASTNode(child);
			result.parent = parent;
			child.getChildren().forEach( kid -> 
			{
				result.children.add( createTree( result , kid ) );
			});
			return result;
		}		
	}
	
	protected static final class WrappedASTNode implements TreeNode 
	{
		public final IASTNode delegate;
		public final List<TreeNode> children = new ArrayList<>();
		public TreeNode parent;
		
		public WrappedASTNode(IASTNode node) {
			this.delegate = node;
		}
		
		@Override public TreeNode getChildAt(int childIndex) { return children.get( childIndex ); }
		@Override public int getChildCount() { return children.size(); }
		@Override public TreeNode getParent() { return parent; }
		@Override public int getIndex(TreeNode child) { return children.indexOf( child ); }
		@Override public boolean getAllowsChildren() { return true; }
		@Override public boolean isLeaf() { return getChildCount() == 0; }

		@Override
		public Enumeration children() 
		{
			final Iterator<TreeNode> it = children.iterator();
			return new Enumeration<TreeNode>() 
			{
				@Override public boolean hasMoreElements() { return it.hasNext(); }
				@Override public TreeNode nextElement() { return it.next(); }
			};
		}
	}
	
	protected static enum Severity { INFO,WARN,ERROR }

	protected static final class CompilationMessage 
	{
		public final Severity severity;
		public final String message;
		public final int lineNo;
		public final int colNo;
		public final int offset;

		public CompilationMessage(String message, Severity severity) {
			this(message,severity,-1,-1,-1);
		}
		public CompilationMessage(String message, Severity severity,int lineNo, int colNo,int offset) {
			this.severity = severity;
			this.message = message;
			this.lineNo = lineNo;
			this.colNo = colNo;
			this.offset = offset;
		}
	}
	
	protected final class MyTableModel extends AbstractTableModel 
	{
		private final List<CompilationMessage> modelData = new ArrayList<>();

		@Override
		public int getRowCount() {
			return modelData.size();
		}
		
		public CompilationMessage getRow(int row) {
			return modelData.get( row );
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
			final CompilationMessage msg = getRow(rowIndex);
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

	public AsmPanel(JDesktopPane desktop) {

		this.desktop = desktop;
		astView.setVisible( false );
		this.desktop.add( astView );
		
		Debugger.setup( this );
		
		final JButton compile = new JButton("compile");
		compile.addActionListener( ev -> assemble()  );
		
		final JButton load = new JButton("Load");
		load.addActionListener( ev -> loadSource()  );
		
		final JButton save = new JButton("Save");
		save.addActionListener( ev -> saveSource()  );
		
		final JButton upload = new JButton("Upload");
		upload.addActionListener( ev -> upload()  );
		
		final JButton astView = new JButton("Toggle AST view");
		astView.addActionListener( ev -> toggleASTView()  );
		
		toolbar.add( compile );		
		toolbar.add( load );
		toolbar.add( save );
		toolbar.add( upload );		
		toolbar.add( astView );
		
		compilationMessages.setFillsViewportHeight( true );
		generalMessages.setVisibleRowCount( 5 );
		
		editor.getDocument().addDocumentListener( new DocumentListener() {

			private void markDirty() {
				if ( documentListenerEnabled ) {
					setEditorDirty( true );
					binary = null;
				}
			}
			
			@Override 
			public void insertUpdate(DocumentEvent e) 
			{ 
				markDirty(); 
			}
			@Override public void removeUpdate(DocumentEvent e) { markDirty(); }
			@Override public void changedUpdate(DocumentEvent e) { markDirty(); }
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
		
		compilationMessages.addMouseListener( new MouseAdapter() 
		{
			public void mouseClicked(java.awt.event.MouseEvent e) 
			{
				if ( e.getClickCount() == 2 && e.getButton() == MouseEvent.BUTTON1 ) {
					final int row = compilationMessages.rowAtPoint( e.getPoint() );
					if ( row != -1 ) 
					{
						final CompilationMessage entry = compilationMessageModel.getRow( row );
						if ( entry.offset > 0 ) 
						{
							try {
								editor.setCaretPosition( entry.offset );
								editor.requestFocusInWindow();
							} catch(IllegalArgumentException ex) {
								ex.printStackTrace();
							}
						}
					}
				}
			}
		});
		Debugger.setColors(toolbar);
		Debugger.setup(editor);
		editor.setCaretColor( Color.GREEN );
		Debugger.setup(compilationMessages);
		Debugger.setup( generalMessages );
		
		setLayout( new GridBagLayout() );
		
		// add toolbar
		final JPanel topPanel = new JPanel();
		topPanel.setLayout( new GridBagLayout() );
		
		GridBagConstraints cnstrs = new GridBagConstraints();
		cnstrs.gridx = 0; cnstrs.gridy = 0;
		cnstrs.fill = GridBagConstraints.HORIZONTAL;
		cnstrs.weightx = 1 ; cnstrs.weighty = 0.0;
		topPanel.add( toolbar , cnstrs );
		
		// add editor
		editor.setPreferredSize( new Dimension(400,200 ) );
		final JScrollPane editorPane = new JScrollPane(editor);
		Debugger.setup( editorPane );
		
		cnstrs = new GridBagConstraints();
		cnstrs.gridx = 0; cnstrs.gridy = 1;
		cnstrs.fill = GridBagConstraints.BOTH;
		cnstrs.weightx = 1 ; cnstrs.weighty = 0.8;
		topPanel.add( editorPane , cnstrs );
		
		// add compilation and general messages
		final JPanel messagePanel = new JPanel();
		messagePanel.setLayout( new GridBagLayout() );
		
		final JScrollPane pane1 = new JScrollPane(compilationMessages);
		Debugger.setup( pane1 );
		final JScrollPane pane2 = new JScrollPane(generalMessages);
		Debugger.setup( pane2 );
		
		cnstrs = new GridBagConstraints();
		cnstrs.gridx = 0; cnstrs.gridy = 0;
		cnstrs.fill = GridBagConstraints.BOTH;
		cnstrs.weightx = 0.5 ; cnstrs.weighty = 0.5;
		messagePanel.add( pane1 , cnstrs );
		
		cnstrs = new GridBagConstraints();
		cnstrs.gridx = 1; cnstrs.gridy = 0;
		cnstrs.fill = GridBagConstraints.BOTH;
		cnstrs.weightx = 0.5 ; cnstrs.weighty = 0.5;
		messagePanel.add( pane2 , cnstrs );
		
		// add message panel
		cnstrs = new GridBagConstraints();
		cnstrs.gridx = 0; cnstrs.gridy = 2;
		cnstrs.fill = GridBagConstraints.BOTH;
		cnstrs.weightx = 1 ; cnstrs.weighty = 0.2;
		
		final JSplitPane splitPane = new JSplitPane(JSplitPane.VERTICAL_SPLIT, topPanel , messagePanel );
		splitPane.setResizeWeight(0.9);
		messagePanel.setPreferredSize( new Dimension(400,50 ) );
		
		add( splitPane , cnstrs );

		setPreferredSize( new Dimension(400,200 ) );
		
		recompilationThread.start();		
	}
	
	private void toggleASTView() 
	{
		astView.setVisible( !  astView.isVisible() );
	}

	private void setEditorDirty(boolean dirty) 
	{
		final boolean stateChanged = editorDirtyFlag != dirty;
		editorDirtyFlag = dirty;
		if ( dirty ) {
			recompilationThread.textChanged();
		}
		
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
				info("Upload failed due to compilation error");
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
		final DateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
		generalMessageModel.add(0,df.format( new Date() )+" - "+message);
		if ( generalMessageModel.size() >= 50 ) 
		{
			generalMessageModel.remove( generalMessageModel.size()-1 ); 
		}
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
	
	private void doSyntaxHighlighting(AST ast) 
	{
		documentListenerEnabled = false;
		try {
			doSyntaxHighlighting( ast , editor.getStyledDocument() );
		} finally {
			documentListenerEnabled = true;
		}
	}
	
	private void doSyntaxHighlighting(IASTNode current,StyledDocument document) 
	{
		final ITextRegion region = current.getTextRegion();
		if ( region != null )
		{
			final Class<? extends IASTNode> clazz = current.getClass();
			final Style style;
			if ( clazz == NumberLiteral.class ) 
			{
				style = LITERAL_STYLE;
			} else if ( clazz == InstructionNode.class ) {
				style = INSTRUCTION_STYLE;
			} else if ( clazz == CommentNode.class ) {
				style = COMMENT_STYLE;				
			} else {
				style = styleContext.getStyle(StyleContext.DEFAULT_STYLE);
			}
			document.setCharacterAttributes( region.getStartingOffset() , region.getLength() , style , true );
		}
		
		for ( IASTNode child : current.getChildren() ) {
			doSyntaxHighlighting( child , document ); 
		}
	}
	
	private Style createStyle(String name,Color color) 
	{
		final Style defaultStyle = styleContext.getStyle(StyleContext.DEFAULT_STYLE);
		final Style style = styleContext.addStyle( name , defaultStyle);
		StyleConstants.setForeground( style , color );
		return style;
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
		try 
		{
			final AST ast = p.parse();
			astView.setAST( ast );
			doSyntaxHighlighting( ast );
			
			binary = a.assemble( ast , sourceHelper );
			binaryStartAddress = a.getOrigin();
			compilationMessageModel.addMessage( new CompilationMessage("Code compiled ok ("+binary.length+" bytes)", Severity.INFO ) );
			info("Compilation finished");
			return true;
		} 
		catch(ParseException e) {
			TextLocation location = sourceHelper.getLocation( e.offset );
			e.printStackTrace();
			if ( location == null ) {
				compilationMessageModel.addMessage( new CompilationMessage(e.getMessage() , Severity.ERROR, -1 , -1  , e.offset ) );
			} else {
				compilationMessageModel.addMessage( new CompilationMessage(e.getMessage() , Severity.ERROR, location.lineNumber , location.columnNumber , e.offset ) );
			}
		}
		catch(Exception e) 
		{
			e.printStackTrace();
			compilationMessageModel.addMessage( new CompilationMessage(e.getMessage() , Severity.ERROR ) );
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
	
	@Override
	public boolean isDisplayed() {
		return displayed;
	}
	
	@Override
	public void setDisplayed(boolean yesNo) {
		this.displayed = yesNo;
	}

	public void setEmulator(Emulator emulator) {
		this.emulator = emulator;
	}
	
	protected abstract void binaryUploadedToEmulator();
}