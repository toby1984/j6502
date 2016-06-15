package de.codesourcery.j6502.emulator;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.commons.lang.StringUtils;

import de.codesourcery.j6502.emulator.CPU.Flag;
import de.codesourcery.j6502.utils.HexDump;

public final class Breakpoint {

    protected static final Pattern CPU_FLAG_PATTERN = Pattern.compile("^([czidbon]+})$");
    protected static final Pattern REG_EQUALS_PATTERN = Pattern.compile("^([axy]{1})[ ]*=[ ]*(\\$[0-9a-z]+|[0-9]+)$");
    protected static final Pattern REG_NOT_EQUALS_PATTERN = Pattern.compile("^([axy]{1})[ ]*!=[ ]*(\\$[0-9a-z]+|[0-9]+)$");
    protected static final Pattern REG_LESS_THAN_PATTERN = Pattern.compile("^([axy]{1})[ ]*<=[ ]*(\\$[0-9a-z]+|[0-9]+)$");
    protected static final Pattern REG_GREATER_THAN_PATTERN = Pattern.compile("^([axy]{1})[ ]*>=[ ]*(\\$[0-9a-z]+|[0-9]+)$");
    
    protected static final List<Pattern> VALID_PATTERNS = Arrays.asList( CPU_FLAG_PATTERN , REG_EQUALS_PATTERN, REG_NOT_EQUALS_PATTERN,REG_LESS_THAN_PATTERN,REG_GREATER_THAN_PATTERN);
    
	public final int address;
	public final boolean isOneshot;
	public final boolean isEnabled;
	public final String pattern;
	public final IBreakpointMatcher matcher;
	
	@FunctionalInterface
	protected interface IBreakpointMatcher {
	    
	    public boolean matches(CPU cpu);
	}
	
    @FunctionalInterface
    protected interface IntProvider {
        
        public int get(CPU cpu);
    }	
	
    private Breakpoint(int address, boolean isOneshot,String pattern,IBreakpointMatcher matcher,boolean isEnabled)
    {
        this.address = address & 0xffff;
        this.isOneshot = isOneshot;
        this.isEnabled = isEnabled;
        this.pattern = pattern;
        this.matcher = matcher;
    }	
    
    public static Breakpoint oneShotBreakpoint(int address) 
    {
        return new Breakpoint(address,true,null,cpu -> true , true);
    }
    
    public static Breakpoint unconditionalBreakpoint(int address) 
    {
        return new Breakpoint(address,false,null,cpu -> true , true);
    }    

	public static boolean isValidExpression(String s) 
	{
	    if ( StringUtils.isBlank( s ) ) {
	        return false;
	    }
	    final String input = s.trim().toLowerCase();
	    for ( Pattern p : VALID_PATTERNS ) {
	        if ( p.matcher( input ).matches() ) {
	            return true;
	        }
	    }
	    return false;
	}
	
	public static Breakpoint toBreakpoint(int address, boolean isOneshot,String pattern,boolean isEnabled) {
	    
	       final String input = pattern.trim().toLowerCase();
	        Matcher matcher = CPU_FLAG_PATTERN.matcher(input);
            if ( matcher.matches() ) 
	        {
	            final String reg = matcher.group(1);
	            int mask = 0;
	            for ( char c : reg.toCharArray() ) 
	            {
	                mask |= Flag.fromSymbol( c ).value;
	            }
	            final byte finalMask = (byte) mask;
                final IBreakpointMatcher bpMatcher = cpu -> ( cpu.getFlagBits() & finalMask ) != 0;
                return new Breakpoint(address, isOneshot,pattern,bpMatcher,isEnabled);	            
	        }
            
            Pattern pat = null;
            for ( Pattern p : VALID_PATTERNS ) {
                matcher = p.matcher( input );
                if ( matcher.matches() ) {
                    pat = p;
                    break;
                }
            }
            if ( pat == null ) {
                throw new RuntimeException("Invalid pattern: >"+pattern+"<");                
            }
            
            // parse number
            final String numberString = matcher.group(2);
            final int value;
            if ( numberString.startsWith("$" ) ) {
                value = Integer.parseUnsignedInt( numberString.substring(1) , 16 );
            } else {
                value = Integer.parseInt( numberString );
            }
            
            // parse register
            final char register = Character.toLowerCase( matcher.group(1).charAt(0) );
            final IntProvider intProvider;
            switch( register ) 
            {
                case 'a' : intProvider = cpu -> cpu.getAccumulator(); break;
                case 'x' : intProvider = cpu -> cpu.getX(); break;
                case 'y' : intProvider = cpu -> cpu.getY(); break;
                default: throw new RuntimeException("Unhandled register: "+register);
            }            
            
            final IBreakpointMatcher bpMatcher;
            if ( pat == REG_EQUALS_PATTERN ) {
                bpMatcher = cpu -> intProvider.get(cpu) == value;
            } else if ( pat == REG_GREATER_THAN_PATTERN ) {
                bpMatcher = cpu -> intProvider.get(cpu) > value;
            } else if ( pat == REG_LESS_THAN_PATTERN ) {
                bpMatcher = cpu -> intProvider.get(cpu) < value;
            } else if ( pat == REG_NOT_EQUALS_PATTERN ) {
                bpMatcher = cpu -> intProvider.get(cpu) != value;
            } else {
                throw new RuntimeException("Unreachable code reached");
            }
            return new Breakpoint(address, isOneshot,pattern,bpMatcher,isEnabled);
	}
	
	
	@Override
	public String toString() {
		return "breakpoint @ "+HexDump.toAdr( address )+" (one-shot: "+isOneshot+")";
	}
	
	public Breakpoint withEnabled(boolean yesNo) 
	{
	    return new Breakpoint(address,isOneshot,pattern,matcher,yesNo);
	}
	
	public boolean isTriggered(CPU cpu) 
	{
	    return isEnabled && matcher.matches( cpu );
	}
}