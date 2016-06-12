package de.codesourcery.j6502.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.ActionEvent;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.AbstractAction;
import javax.swing.JFileChooser;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.JViewport;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.DefaultStyledDocument;
import javax.swing.text.Style;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyleContext;
import javax.swing.text.StyledDocument;

import de.codesourcery.j6502.emulator.Emulator;

public class CommentedCodeViewer extends JPanel implements WindowLocationHelper.IDebuggerView  
{
    private final StyleContext styleContext = new StyleContext();    
    private final JTextPane editor = new JTextPane( new DefaultStyledDocument( styleContext ) );
    private final JScrollPane scrollPane = new JScrollPane(editor);
    
    private final Style defaultStyle; 
    private final Style commentLineStyle; 
    private final Style highlightStyle;
    
    private File file;
    private Component peer;
    private boolean displayed;
    
    private final StringBuilder text = new StringBuilder();
    private final Map<Integer,Integer> pcToTextOffset = new HashMap<>();
    
    private Highlight currentHighlight;
    private final Map<String, String> configProperties = new HashMap<>();
    
    protected static final class Range 
    {
        public final int offset;
        public final int len;
        
        public Range(int offset, int len) {
            if ( offset < 0 ) {
                throw new IllegalArgumentException("Offset must be >= 0");
            }
            if ( len < 0 ) {
                throw new IllegalArgumentException("len must be >= 0");
            }
            this.offset = offset;
            this.len = len;
        }
        
        public int endOffset() {
            return offset+len;
        }
    }
    
    protected static final class Highlight 
    {
        public final Range range;
        public final Style style;
        
        public Highlight(int offset, int len,Style style) {
            this.range = new Range(offset,len);
            this.style = style;
        }
    }
    
    public CommentedCodeViewer() 
    {
        editor.setEditable( false );
        
        defaultStyle = styleContext.getStyle(StyleContext.DEFAULT_STYLE);        
        highlightStyle = createStyle("highlighted", Color.LIGHT_GRAY );
        commentLineStyle = createStyle("comment", new Color(0f,0.8f,0f,0.5f)); 
        
        final JToolBar toolbar = new JToolBar(JToolBar.HORIZONTAL);
        toolbar.add( new AbstractAction("Set file") {

            @Override
            public void actionPerformed(ActionEvent e) 
            {
                try {
                    setFile( selectFile().orElse( null ) );
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        } );
        editor.setDocument( new DefaultStyledDocument(styleContext) );
        
        setLayout( new BorderLayout() );
        add( toolbar , BorderLayout.NORTH );
        
        add( scrollPane , BorderLayout.CENTER );
    }
    
    @Override
    public void setLocationPeer(Component frame) {
        this.peer = frame;
    }
    
    public void setFile(File file) throws IOException 
    {
        // TODO: hard-coded address prefix, make this configurable
        setFile( file  , ".," , "$" );
    }
    
    public void setFile(File file,String adrPrefix1,String... adrPrefix2) throws IOException 
    {
        this.file = file;
        
        setHighlight( null );
        
        text.setLength(0);
        pcToTextOffset.clear();
        if ( file != null ) 
        {
            final List<Pattern> patterns = new ArrayList<>();
            
            patterns.add( Pattern.compile("^"+Pattern.quote(adrPrefix1)+"([0-9a-fA-F]{4}).*") );
            if ( adrPrefix2 != null ) {
                for ( String prefix : adrPrefix2 ) {
                    patterns.add( Pattern.compile("^"+Pattern.quote(prefix)+"([0-9a-fA-F]{4}).*") );
                }
            }
            int offset = 0;
            final List<Highlight> commentLines = new ArrayList<>();
            for ( String line : Files.readAllLines( file.toPath() ) ) 
            {
                Matcher matched = null;
                for ( Pattern pattern : patterns ) {
                    Matcher m = pattern.matcher( line );
                    if ( m.matches() ) {
                        matched = m;
                        break;
                    }
                }
                if ( matched != null ) 
                {
                    final int adr = Integer.parseUnsignedInt( matched.group(1) , 16 );
                    pcToTextOffset.put( adr , offset );
                } 
                else 
                {
                    if ( isCommentLine( line ) ) {
                        commentLines.add( new Highlight( offset , line.length() , commentLineStyle ) );
                    }
                }
                text.append( line ).append( '\n' );
                offset += line.length()+1;
            }
            editor.setText( text.toString() );
            editor.setCaretPosition(0);
            commentLines.forEach( this::highlight );
        } else {
            editor.setText( "" );
        }
    }
    
    private boolean isCommentLine(String line) {
        return line.trim().startsWith(";");
    }
    
    private void setHighlight(Highlight hl) {
        
        if ( this.currentHighlight != null ) 
        {
            clearStyle( this.currentHighlight );
        }
        this.currentHighlight = hl;
        if ( hl != null ) {
            highlight( this.currentHighlight );
        }
    }
    
    private void clearStyle(Highlight hl) {
        highlight(hl  , defaultStyle );
    }
    
    private void highlight(Highlight hl) {
        highlight(hl,hl.style);
    }
    
    private void highlight(Highlight hl,Style style) 
    {
        ((StyledDocument) editor.getDocument()).setCharacterAttributes( hl.range.offset , hl.range.len , style , true );
    }
    
    private void highlightCurrentLine(Integer offset) {
        if ( offset == null ) 
        {
            setHighlight( null );
        } else 
        {
            setHighlight( new Highlight( offset , lineLength( offset ) , highlightStyle ) ); 
        }
    }
    
    private int lineLength(Integer startOffset) 
    {
        int len = 0;
        for ( int i = startOffset ; i < text.length() ; i++ ) {
            if ( text.charAt( i ) == '\n' ) {
                break;
            }
            len++;
        }
        return len;
    }
    
    private Style createStyle(String name,Color color)
    {
        final Style defaultStyle = styleContext.getStyle(StyleContext.DEFAULT_STYLE);
        final Style style = styleContext.addStyle( name , defaultStyle);
        StyleConstants.setBackground( style , color );
        return style;
    }    
    
    @Override
    public Component getLocationPeer() {
        return peer;
    }

    @Override
    public boolean isDisplayed() {
        return displayed;
    }

    @Override
    public void setDisplayed(boolean yesNo) {
        this.displayed = yesNo;
    }

    @Override
    public void refresh(Emulator emulator) 
    {
        final int pc = emulator.getCPU().pc();
        final Integer offset = pcToTextOffset.get( pc );
        
        highlightCurrentLine( offset );
        if ( offset == null ) {
            return;
        }
        
        scrollToVisible( offset );
//        editor.setCaretPosition( offset );
    }

    @Override
    public boolean isRefreshAfterTick() {
        return false;
    }
    
    private Optional<File> selectFile()
    {
        final File lastFile = getLastLoadedFile();

        final JFileChooser chooser;
        if ( lastFile != null && lastFile.getParentFile().exists() ) {
            chooser = new JFileChooser( lastFile );
        } else {
            chooser = new JFileChooser();
        }
        if ( lastFile != null && lastFile.exists() ) {
            chooser.setSelectedFile( lastFile );
        }
        final int result = chooser.showOpenDialog( null );
        if ( result != JFileChooser.APPROVE_OPTION) {
            return Optional.empty();
        }

        final File file = chooser.getSelectedFile();
        setLastLoadedFile( file );
        return Optional.of( file );
    }    
    
    private File getLastLoadedFile()
    {
        final String value = configProperties.get("last_loaded_commented_src");
        return value != null ? new File(value) : null;
    }    
    
    private void setLastLoadedFile(File file)
    {
        configProperties.put("last_loaded_commented_src", file.getAbsolutePath());
    }
    
    @Override
    public Map<String, String> getConfigProperties() {
        return this.configProperties;
    }
    
    private void scrollToVisible(int offset) 
    {
        if (editor.getText().length() > 0) 
        {
            final Rectangle rectangle;
            try {
                rectangle = editor.modelToView( offset );
            } catch (BadLocationException e) {
                e.printStackTrace();
                return;
            }

            final JViewport viewport = scrollPane.getViewport();
            final Rectangle viewRect = viewport.getViewRect();       
            viewRect.x = rectangle.x ;
            viewRect.y = rectangle.y ;
            
            editor.scrollRectToVisible( viewRect );
        }
    }
    
    @Override
    public void setConfigProperties(Map<String, String> properties) {
        this.configProperties.clear();
        this.configProperties.putAll( properties );
        
        if ( this.file == null ) 
        {
            File previousFile = getLastLoadedFile();
            if ( previousFile != null && previousFile.exists() && previousFile.isFile() && previousFile.canRead() ) 
            {
                try {
                    setFile( previousFile );
                }
                catch(IOException e) 
                {
                    e.printStackTrace();
                }
            }
        }
    }
}