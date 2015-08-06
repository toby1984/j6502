package de.codesourcery.j6502.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JFrame;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.SwingUtilities;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import de.codesourcery.j6502.assembler.parser.Lexer;
import de.codesourcery.j6502.assembler.parser.Parser;
import de.codesourcery.j6502.assembler.parser.Scanner;
import de.codesourcery.j6502.assembler.parser.ast.AST;
import de.codesourcery.j6502.assembler.parser.ast.IValueNode;
import de.codesourcery.j6502.assembler.parser.ast.InstructionNode;
import de.codesourcery.j6502.assembler.parser.ast.LabelNode;
import de.codesourcery.j6502.utils.ITextRegion;

public class Main
{
	protected static final String STYLE_DEFAULT = "default";
	protected static final String STYLE_LABEL = "label";
	protected static final String STYLE_NUMBER = "number";
	protected static final String STYLE_OPCODE = "opcode";

	protected final String source = "    NOP\n"+
			"    LDA #$10\n"+
			"test:\n"+
			"    NOP";

	protected final JTextPane editor = new JTextPane();
	protected final StyledDocument document = editor.getStyledDocument();

	protected volatile boolean documentListenerEnabled = true;

	private final AtomicReference<Long> lastRefreshRequest = new AtomicReference<>(null);

	private final Thread refreshThread;

	public Main()
	{
		refreshThread = new Thread()
		{
			@Override
			public void run()
			{
				while( true )
				{
					long now = System.currentTimeMillis();
					Long lastRequest = lastRefreshRequest.get();
					if ( lastRequest != null && (now - lastRequest ) > 500 ) {
						try {
							SwingUtilities.invokeAndWait( () -> renderDocument() );
						} catch (Exception e) {
							e.printStackTrace();
						}
						lastRefreshRequest.compareAndSet( lastRequest , null );
					}
					try {
						Thread.sleep( 250 );
					} catch(Exception e) {}
				}
			}
		};
		refreshThread.setName("refresh");
		refreshThread.setDaemon(true);
		refreshThread.start();
	}

	private void queueRefresh()
	{
		lastRefreshRequest.set( System.currentTimeMillis() );
	}

	public static void main(String[] args)
	{
		SwingUtilities.invokeLater( () ->
		{
			try {
				new Main().run();
			}
			catch (Exception e)
			{
				throw new RuntimeException(e);
			}
		});
	}

	private void renderDocument()
	{
		System.out.println("---refresh---");
		documentListenerEnabled = false;
		try {
			final String text;
			try {
				text = document.getText( 0 , document.getLength() );
			} catch (BadLocationException e) {
				e.printStackTrace();
				return;
			}

			final Parser p = new Parser( new Lexer( new Scanner( text ) ) );

			final AST ast;
			try {
				ast = p.parse();
			}
			catch(Exception e)
			{
				e.printStackTrace();
				return;
			}

			document.setCharacterAttributes(  0 , document.getLength() , defaultStyle , true );
			ast.visitParentFirst( node ->
			{
				ITextRegion region = node.getTextRegion();
				if ( region != null )
				{
					Style style = null;
					if ( node instanceof InstructionNode) {
						style = opcodeStyle;
					} else if ( node instanceof IValueNode) {
						style = numberStyle;
					} else if ( node instanceof LabelNode) {
						style = labelStyle;
					}
					if ( style != null ) {
						document.setCharacterAttributes( region.getStartingOffset() , region.getLength() , style , true );
					}
				}
			});
		} finally {
			documentListenerEnabled = true;
		}
	}

	private Style labelStyle;
	private Style numberStyle;
	private Style opcodeStyle;
	private Style defaultStyle;

	private void setupStyles(StyledDocument doc)
	{
		labelStyle = doc.addStyle(STYLE_LABEL, null);
		StyleConstants.setForeground(labelStyle, Color.GREEN);

		numberStyle = doc.addStyle(STYLE_NUMBER, null);
		StyleConstants.setForeground(numberStyle, Color.PINK );

		opcodeStyle = doc.addStyle(STYLE_OPCODE, null);
		StyleConstants.setForeground(opcodeStyle, Color.BLUE);

		defaultStyle = doc.addStyle(STYLE_DEFAULT, null);
		StyleConstants.setForeground(defaultStyle, Color.BLACK );
	}

	public void run() throws BadLocationException
	{
		final JFrame frame = new JFrame("");

		setupStyles( document );

		document.insertString( 0 , source , defaultStyle );

		document.addDocumentListener( new DocumentListener() {

			@Override
			public void removeUpdate(DocumentEvent e) {
				if ( documentListenerEnabled ) { queueRefresh(); }
			}

			@Override
			public void insertUpdate(DocumentEvent e) {
				if ( documentListenerEnabled ) { queueRefresh(); }
			}

			@Override
			public void changedUpdate(DocumentEvent e) {
				if ( documentListenerEnabled ) { queueRefresh(); }
			}
		});
		renderDocument();

		frame.getContentPane().setLayout( new BorderLayout() );
		editor.setPreferredSize( new Dimension(640,480 ) );
		frame.getContentPane().add( new JScrollPane( editor ) , BorderLayout.CENTER );
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		frame.pack();
		frame.setVisible( true );
	}
}
