package de.codesourcery.j6502.assembler.parser;

import java.util.ArrayList;
import java.util.List;

import de.codesourcery.j6502.assembler.exceptions.ParseException;

public class Lexer {

    private final Scanner scanner;

    private final List<Token> tokens = new ArrayList<>();

    private final StringBuilder buffer = new StringBuilder();

    private boolean eof;

    private boolean skipWhitespace = true;

    public Lexer(Scanner scanner) {
        this.scanner = scanner;
    }

    public int currentOffset()
    {
        if ( eof() ) {
            return scanner.currentOffset();
        }
        return tokens.get(0).offset;
    }

    public void setSkipWhitespace(boolean skip)
    {
        this.skipWhitespace = skip;
        if ( skip ) {
            tokens.removeIf( token -> token.hasType(TokenType.WHITESPACE ) );
        }
    }

    public boolean eof()
    {
        if ( ! tokens.isEmpty() ) {
            return false;
        }
        if ( eof ) {
            return true;
        }
        parse();
        return tokens.isEmpty();
    }

    public Token peek()
    {
        if ( eof() ) {
            throw new IllegalStateException("Already at EOF");
        }
        return tokens.get(0);
    }

    public boolean peek(TokenType expected)
    {
        if ( eof() ) {
            return false;
        }
        return tokens.get(0).hasType(expected);
    }

    public Token next()
    {
        if ( eof() ) {
            throw new IllegalStateException("Already at EOF");
        }
        return tokens.remove(0);
    }

    private void parse()
    {
        int start = scanner.currentOffset();
        buffer.setLength(0);

        // consume whitespace
        while( ! scanner.eof() )
        {
            final char c = scanner.peek();
            if ( c == '\r' || c == '\n' || ! Character.isWhitespace( c ) ) {
                break;
            }
            if ( skipWhitespace ) {
                scanner.next();
            } else {
                buffer.append( scanner.next() );
            }
        }

        if ( ! skipWhitespace && buffer.length() > 0 )
        {
            addToken(TokenType.WHITESPACE,buffer.toString(), start );
            return;
        }

        start = scanner.currentOffset();
        while( ! scanner.eof() )
        {
            final char c = scanner.peek();

            if ( buffer.length() == 0 )
            {
                if ( Operator.isValidOperator( Character.toString( c ) ) )
                {
                    buffer.append( scanner.next() );
                    continue;
                }
            }
            else if ( Operator.isValidOperator( buffer.toString() ) )
            {
                if ( Operator.isValidOperator( buffer.toString()+Character.toString( c ) ) )
                {
                    buffer.append( scanner.next() );
                    continue;
                }
                // buffer contains a valid operator but <buffer+current char> no longer yields a valid operator
                parseBuffer(start);
                return;
            }
            else if ( Operator.isValidOperator( Character.toString( c ) ) )
            {
                // buffer.length >0 but no valid operator already in buffer
                parseBuffer(start);
                return;
            }

            switch( c )
            {
                case '%':
                    parseBuffer(start);
                    start = scanner.currentOffset();
                    addToken(TokenType.PERCENTAGE, scanner.next() , start );
                    return;
                case '\r':
                    scanner.next(); // consume CR
                    if ( ! scanner.eof() && scanner.peek() == '\n' ) {
                        scanner.next();
                        parseBuffer(start);
                        start = scanner.currentOffset();
                        addToken(TokenType.EOL, "\r\n" , start-2 );
                        return;
                    }
                    break;
                case '\n':
                    parseBuffer(start);
                    start = scanner.currentOffset();
                    addToken(TokenType.EOL, scanner.next() , start );
                    return;
                case ';':
                    parseBuffer(start);
                    start = scanner.currentOffset();
                    addToken(TokenType.SEMICOLON, scanner.next() , start );
                    return;
                case '=':
                    parseBuffer(start);
                    start = scanner.currentOffset();
                    addToken(TokenType.EQUALS, scanner.next() , start );
                    return;
                case '(':
                    parseBuffer(start);
                    start = scanner.currentOffset();
                    addToken(TokenType.PARENS_OPEN, scanner.next() , start );
                    return;
                case ')':
                    parseBuffer(start);
                    start = scanner.currentOffset();
                    addToken(TokenType.PARENS_CLOSE, scanner.next() , start );
                    return;
                case '$':
                    parseBuffer(start);
                    start = scanner.currentOffset();
                    addToken(TokenType.DOLLAR, scanner.next() , start );
                    return;
                case '#':
                    parseBuffer(start);
                    start = scanner.currentOffset();
                    addToken(TokenType.HASH, scanner.next() , start );
                    return;
                case ':':
                    parseBuffer(start);
                    start = scanner.currentOffset();
                    addToken(TokenType.COLON, scanner.next() , start );
                    return;
                case ',':
                    parseBuffer(start);
                    start = scanner.currentOffset();
                    addToken(TokenType.COMMA, scanner.next() , start );
                    return;
                case '.':
                    parseBuffer(start);
                    start = scanner.currentOffset();
                    addToken(TokenType.DOT, scanner.next() , start );
                    return;                    
                case '\'':
                    parseBuffer(start);
                    start = scanner.currentOffset();
                    addToken(TokenType.SINGLE_QUOTE, scanner.next() , start );
                    return;
                case '\"':
                    parseBuffer(start);
                    start = scanner.currentOffset();
                    addToken(TokenType.DOUBLE_QUOTE, scanner.next() , start );
                    return;
                default:
                    // fall-through
            }

            if ( Character.isWhitespace( c ) ) {
                break;
            }
            buffer.append( scanner.next() );
        }

        parseBuffer(start);

        if ( scanner.eof() )
        {
            eof = true;
            addToken(TokenType.EOF,"",scanner.currentOffset());
        }
    }

    private void parseBuffer(int bufferStartOffset) {

        String s = buffer.toString();
        if ( s.length() == 0 ) {
            return;
        }
        
        if ( Operator.isValidOperator( s ) )
        {
            final List<Operator> operators = Operator.getMatchingOperators( s );
            if ( operators.size() != 1 ) {
                throw new RuntimeException("Internal error,expected exactly 1 operator for symbol '"+s+"' but got "+operators);
            }
            addToken(TokenType.OPERATOR , s , bufferStartOffset );
            return;
        }

        boolean isNumber = true;
        for ( int i = 0 , len = s.length() ; i < len ; i++ ) {
            if ( ! Character.isDigit( s.charAt(i) ) )
            {
                isNumber = false;
                break;
            }
        }
        if ( isNumber ) {
            addToken(TokenType.DIGITS , s , bufferStartOffset );
            return;
        }

        if ( s.length() > 0 ) {
            addToken(TokenType.CHARACTERS, s , bufferStartOffset );
        }
    }

    private void addToken(TokenType t,char c,int offset) {
        this.tokens.add( new Token(t,Character.toString(c) ,offset) );
    }

    private void addToken(TokenType t,String text,int offset) {
        this.tokens.add( new Token(t,text,offset) );
    }

    public Token next(TokenType expected)
    {
        if ( ! peek().hasType( expected ) ) {
            throw new ParseException("Expected token type "+expected+" but got "+peek(), currentOffset() );
        }
        return next();
    }

    @Override
    public String toString()
    {
        return tokens.isEmpty() ? "<no token>" : tokens.get(0).toString();
    }

    public void push(Token tok) {
        if (tok == null) {
            throw new IllegalArgumentException("token must not be NULL");
        }
        tokens.add( 0 , tok );
    }
}
