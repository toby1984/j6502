package de.codesourcery.j6502.ui;

import java.awt.Component;
import java.awt.Dimension;
import java.awt.Point;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import de.codesourcery.j6502.emulator.Emulator;

public class WindowLocationHelper
{
	protected static final String SIZE_PREFIX = "size.";
	protected static final String LOCATION_PREFIX = "loc.";
	protected static final String IS_SHOWN_PREFIX = "enabled.";
	private Map<String,SizeAndLocation> locations = null;

	private final File file;

	public interface IDebuggerView
	{
		public void setLocationPeer(Component frame);

		public Component getLocationPeer();
		
		public boolean isDisplayed();
		
		public void setDisplayed(boolean yesNo);
		
		public void refresh(Emulator emulator);
		
		public boolean isRefreshAfterTick();
		
		public default boolean canBeDisplayed() {
			return true;
		}
	}

	protected static final class SizeAndLocation
	{
		public final Point location;
		public final Dimension size;
		public final boolean isShown;

		public SizeAndLocation(Point location, Dimension size,boolean isShown) {
			this.location = location;
			this.size = size;
			this.isShown = isShown; 
		}

		public static SizeAndLocation valueOf(IDebuggerView loc)
		{
			final Component frame = loc.getLocationPeer();
			return new SizeAndLocation( new Point( frame.getLocation() ) , new Dimension( frame.getSize() ) , loc.isDisplayed() );
		}

		public void apply(IDebuggerView loc)
		{
			loc.getLocationPeer().setLocation( new Point( location ) );
			loc.getLocationPeer().setPreferredSize( new Dimension( this.size ) );
			loc.setDisplayed( this.isShown );
		}

		public void write(String clazz,BufferedWriter writer) throws IOException {
			writer.write( LOCATION_PREFIX+clazz+"="+location.x+","+location.y+"\n");
			writer.write( SIZE_PREFIX+clazz+"="+size.width+","+size.height+"\n");
			writer.write( IS_SHOWN_PREFIX+clazz+"="+isShown+"\n");
		}

		@Override
		public String toString() {
			return "SizeAndLocation [location=" + location + ", size=" + size+ "]";
		}
	}

	public WindowLocationHelper()
	{
		final File homeDir = new File( (String) System.getProperties().get("user.home") );
		file = new File( homeDir , ".c64_windowlocations" );
	}

	private void loadLocations()
	{
		if ( locations != null ) {
			return;
		}

		final Map<String,Boolean> isShown = new HashMap<>();
		final Map<String,Point> locs = new HashMap<>();
		final Map<String,Point> dims = new HashMap<>();

		java.io.BufferedReader reader = null;
		try
		{
			if ( file.exists() )
			{
				reader = new java.io.BufferedReader( new FileReader( file ) );
				String line;
				while ( ( line = reader.readLine()) != null )
				{
					if ( line.startsWith( IS_SHOWN_PREFIX ) )
					{
						line = line.substring( IS_SHOWN_PREFIX.length() );
						String[] split = line.split("=");
						if ( split.length == 2 )
						{
							String clazz = split[0];
							boolean state = Boolean.valueOf( split[1] );
							isShown.put( clazz ,  state );
						}						
					} 
					else  if ( line.startsWith( LOCATION_PREFIX ) )
					{
						line = line.substring( LOCATION_PREFIX.length() );
						String[] split = line.split("=");
						if ( split.length == 2 )
						{
							String clazz = split[0];
							Point loc = readPoint( split[1]);
							locs.put( clazz ,  loc );
						}
					} else if ( line.startsWith( SIZE_PREFIX ) ) {
						line = line.substring( SIZE_PREFIX.length() );
						String[] split = line.split("=");
						if ( split.length == 2 )
						{
							String clazz = split[0];
							Point size = readPoint( split[1]);
							dims.put( clazz , size );
						}
					}
				}
			}
		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			if ( reader != null ) {
				try { reader.close(); } catch(IOException e) {};
			}
		}
		final HashMap<String,SizeAndLocation> tmp = new HashMap<>();

		final Set<String> allKeys = new HashSet<>();
		allKeys.addAll( locs.keySet() );
		allKeys.addAll( dims.keySet() );
		for ( String key : allKeys ) {
			Point l = locs.get( key );
			Point d = dims.get(key);
			Boolean state = isShown.get(key);
			if ( state == null ) {
				state = true;
			}
			if ( l != null && d != null ) {
				tmp.put( key , new SizeAndLocation(l, new Dimension(d.x , d.y ) , state ) );
			}
		}
		locations = tmp;
	}

	private Point readPoint(String s)
	{
		String[] parts = s.split(",");
		if ( parts.length == 2 ) {
			return new Point( Integer.valueOf( parts[0] ) , Integer.valueOf( parts[1] ) );
		}
		return null;
	}

	public void applyLocation(IDebuggerView window)
	{
		loadLocations();
		final SizeAndLocation loc = locations.get( window.getClass().getName() );
		if ( loc != null )
		{
			loc.apply( window );
		}
	}

	public void saveLocation(IDebuggerView window)
	{
		loadLocations();
		SizeAndLocation valueOf = SizeAndLocation.valueOf(window);
		locations.put( window.getClass().getName() , valueOf );
	}

	public void saveAll() throws IOException
	{
		loadLocations();

		try ( BufferedWriter writer = new BufferedWriter( new FileWriter( file ) ) )
		{
			for ( Entry<String, SizeAndLocation> entry : locations.entrySet() )
			{
				entry.getValue().write( entry.getKey() ,  writer );
			}
		}
	}
}