package de.codesourcery.j6502.assembler;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import de.codesourcery.j6502.assembler.exceptions.DuplicateSymbolException;
import de.codesourcery.j6502.assembler.exceptions.UnknownSymbolException;
import de.codesourcery.j6502.assembler.parser.Identifier;

public class SymbolTable implements ISymbolTable {

	private final Map<Identifier,ISymbol<?>> globalSymbols = new HashMap<>();
	private final Map<Identifier,Map<Identifier,ISymbol<?>>> localSymbols = new HashMap<>();

	@Override
	public Iterable<ISymbol<?>> getGlobalSymbols()
	{
		return new ArrayList<>( globalSymbols.values() );
	}

	@Override
	public Iterable<ISymbol<?>> getLocalSymbols(Identifier globalSybol)
	{
		final Map<Identifier, ISymbol<?>> map = localSymbols.get( globalSybol );
		if ( map == null ) {
			return new ArrayList<ISymbol<?>>();
		}
		return new ArrayList<>( map.values() );
	}

	@Override
	public String toString() {
		StringBuilder buffer = new StringBuilder("=== Symbol table ===\n\n");

		final List<Identifier> globalKeys = globalSymbols.keySet().stream().sorted( Comparator.comparing( n -> n.value ) ).collect(Collectors.toList());
		for ( Identifier globalIdentifier : globalKeys )
		{
			ISymbol<?> symbol = globalSymbols.get( globalIdentifier );
			buffer.append("\n").append( globalIdentifier ).append(" : ").append( symbol == null ? "<undefined>" : symbol.toString() );
			Map<Identifier, ISymbol<?>> localSyms = localSymbols.get( globalIdentifier );
			if ( localSyms != null && ! localSyms.isEmpty() )
			{
				final List<Identifier> localKeys = localSyms.keySet().stream().sorted( Comparator.comparing( n -> n.value ) ).collect(Collectors.toList());
				for ( Identifier localIdentifier : localKeys )
				{
					symbol = localSyms.get( localIdentifier );
					buffer.append("\n    ").append( localIdentifier ).append(" : ").append( symbol == null ? "<undefined>" : symbol.toString() );
				}
			}
			buffer.append("\n");
		}

		return buffer.toString();
	}

	@Override
	public void declareSymbol(Identifier identifier, Identifier parentIdentifier)
	{
		if ( parentIdentifier== null ) { // global symbol
			if ( globalSymbols.containsKey( parentIdentifier ) ) // already declared/defined
			{
				return;
			}
			globalSymbols.put( identifier , null );
			return;
		}

		// local symbol
		if ( ! globalSymbols.containsKey( parentIdentifier ) ) {
			globalSymbols.put( parentIdentifier , null );
		}
		Map<Identifier, ISymbol<?>> syms = localSymbols.get( parentIdentifier );
		if ( syms == null ) {
			syms = new HashMap<>();
			localSymbols.put( parentIdentifier , syms );
		}
		if ( ! syms.containsKey( identifier ) ) {
			syms.put( identifier , null );
		}
	}

	@Override
	public void defineSymbol(ISymbol<?> id)
	{
		if ( id.getParentIdentifier() == null ) { // global symbol
			if ( globalSymbols.containsKey( id.getIdentifier() ) )
			{
				if ( globalSymbols.get( id.getIdentifier() ) != null ) {
					throw new DuplicateSymbolException( id );
				}
			}
			globalSymbols.put( id.getIdentifier() , id );
			return;
		}

		// local symbol
		final ISymbol<?> parent = globalSymbols.get( id.getParentIdentifier() );
		if ( parent == null ) {
			throw new UnknownSymbolException( id.getParentIdentifier() , null );
		}
		Map<Identifier, ISymbol<?>> syms = localSymbols.get( parent.getIdentifier() );
		if ( syms == null ) {
			syms = new HashMap<>();
			localSymbols.put( parent.getIdentifier() , syms );
		}
		if ( syms.containsKey( id.getIdentifier() ) )
		{
			if ( syms.get( id.getIdentifier() ) != null ) {
				throw new DuplicateSymbolException( id );
			}
		}
		syms.put( id.getIdentifier() , id );
	}

	@Override
	public ISymbol<?> getSymbol(Identifier identifier, Identifier parentIdentifier)
	{
		if ( parentIdentifier == null ) {
			final ISymbol<?> result = globalSymbols.get( identifier );
			if ( result == null ) {
				throw new UnknownSymbolException( identifier ,parentIdentifier);
			}
			return result;
		}
		final ISymbol<?> parent = globalSymbols.get( parentIdentifier );
		if ( parent == null ) {
			throw new UnknownSymbolException( parentIdentifier , null);
		}
		final Map<Identifier,ISymbol<?>> syms = localSymbols.get( parentIdentifier );
		if ( syms != null )
		{
			final ISymbol<?> result = syms.get( identifier );
			if ( result != null ) {
				return result;
			}
		}
		throw new UnknownSymbolException( identifier , parentIdentifier );
	}

	@Override
	public boolean isDefined(Identifier identifier,Identifier parentIdentifier)
	{
		if ( parentIdentifier == null ) {
			return globalSymbols.get( identifier ) != null;
		}
		final Map<Identifier, ISymbol<?>> map = localSymbols.get( parentIdentifier );
		if ( map == null ) {
			return false;
		}
		return map.get( identifier ) != null;
	}
}