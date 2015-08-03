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
import java.util.Map;
import java.util.Map.Entry;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import de.codesourcery.j6502.emulator.Emulator;

public class WindowLocationHelper
{
	protected static final String SIZE_PREFIX = "window_size";
	protected static final String LOCATION_PREFIX = "window_location";
	protected static final String IS_SHOWN_PREFIX = "window_enabled";

	// syntax for each line of the properties file is: DebuggerViewCLASS[KEY]=VALUE
	protected static final Pattern CONFIG_LINE_PATTERN = Pattern.compile("^([\\.\\$a-zA-Z0-9]+)\\[(.+?)\\]=(.+?)$");

	private Map<String,ViewConfiguration> configs = null;

	private final File file;

	public interface IDebuggerView
	{
		public default Map<String,String> getConfigProperties() {
			return new HashMap<>();
		}

		public default String propertyName(String key) {
			return getClass().getName()+"."+key;
		}

		public default void setConfigProperties(Map<String,String> properties)
		{
		}

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

	protected interface IConfigFileWriter
	{
		public void write(String key,String value);
	}

	protected static String toConfigLine(String clazz,String key,String value)
	{
		return clazz+"["+key+"]="+value+"\n";
	}

	protected static final class ViewConfiguration
	{
		private final IDebuggerView view;
		private final Map<String,String> configProperties;

		protected ViewConfiguration(Map<String,String> configProperties) {
			this(null,configProperties);
		}

		protected ViewConfiguration(IDebuggerView view,Map<String,String> configProperties)
		{
			this.view = view;
			this.configProperties = new HashMap<>( configProperties );
		}

		public ViewConfiguration withView(IDebuggerView view)
		{
			return new ViewConfiguration( view , view.getConfigProperties() );
		}

		public static ViewConfiguration valueOf(IDebuggerView view )
		{
			return new ViewConfiguration( view  , view.getConfigProperties() );
		}

		public void apply(IDebuggerView loc)
		{
			loc.getLocationPeer().setLocation( new Point( getLocation() ) );
			loc.getLocationPeer().setPreferredSize( new Dimension( getSize() ) );
			loc.setDisplayed( isShown() );
			loc.setConfigProperties( this.configProperties );
		}

		public void write(IConfigFileWriter writer)
		{
			for ( Entry<String, String> entry : getConfigProperties().entrySet() )
			{
				writer.write( entry.getKey(),entry.getValue() );
			}

			final Point loc = getLocation();
			final Dimension size = getSize();

			writer.write( LOCATION_PREFIX , loc.x+","+loc.y );
			writer.write( SIZE_PREFIX , size.width+","+size.height );
			writer.write( IS_SHOWN_PREFIX , Boolean.toString( isShown() ) );
		}

		@Override
		public String toString() {
			return "SizeAndLocation [location=" + getLocation() + ", size=" + getSize()+ "]";
		}

		public Point getLocation()
		{
			if ( view != null ) {
				return view.getLocationPeer().getLocation();
			}
			return getOrElse( LOCATION_PREFIX , string ->
			{
				final String[] parts = string.split(",");
				return new Point( Integer.valueOf( parts[0] ) , Integer.valueOf( parts[1] )  );
			} , new Point(0,0) );
		}

		private <T> T getOrElse(String key,Function<String,T> conversion,T defaultValue)
		{
			final String value = getConfigProperties().get( key );
			return value == null ? defaultValue : conversion.apply( value );
		}

		public Dimension getSize()
		{
			if ( view != null ) {
				return view.getLocationPeer().getSize();
			}
			return getOrElse( SIZE_PREFIX , string ->
			{
				final String[] parts = string.split(",");
				return new Dimension( Integer.valueOf( parts[0] ) , Integer.valueOf( parts[1] )  );
			} , new Dimension(100,100) );
		}

		public boolean isShown()
		{
			if ( view != null ) {
				return view.isDisplayed();
			}
			return getOrElse( IS_SHOWN_PREFIX , string -> Boolean.valueOf( string ) , Boolean.TRUE );
		}

		public Map<String, String> getConfigProperties()
		{
			if ( view != null ) {
				return view.getConfigProperties();
			}
			return configProperties;
		}
	}

	public WindowLocationHelper()
	{
		final File homeDir = new File( (String) System.getProperties().get("user.home") );
		file = new File( homeDir , ".c64_windowlocations" );
	}

	private void loadLocations()
	{
		if ( configs != null ) {
			return;
		}

		final Map<String,Map<String,String>> config = new HashMap<>();

		java.io.BufferedReader reader = null;
		try
		{
			if ( file.exists() )
			{
				reader = new java.io.BufferedReader( new FileReader( file ) );
				String line;
				while ( ( line = reader.readLine()) != null )
				{
					final Matcher matcher = CONFIG_LINE_PATTERN.matcher( line );
					if ( ! matcher.matches() )
					{
						System.err.println("WARNING: Ignoring unparseable config file line '"+line+"'");
						continue;
					}
					final String clazz = matcher.group(1);
					final String key = matcher.group(2);
					final String value = matcher.group(3);

					Map<String,String> existing = config.get( clazz );
					if ( existing == null ) {
						existing = new HashMap<>();
						config.put(clazz, existing);
					}
					existing.put( key, value );
				}
			}
		}
		catch(Exception e) {
			e.printStackTrace();
		}
		finally {
			if ( reader != null ) {
				try { reader.close(); } catch(IOException e) {};
			}
		}

		final HashMap<String,ViewConfiguration> tmp = new HashMap<>();

		for ( Entry<String, Map<String, String>> entry : config.entrySet() )
		{
			final String clazz = entry.getKey();
			final Map<String,String> clazzProperties = entry.getValue();
			tmp.put( clazz , new ViewConfiguration( clazzProperties ) );
		}
		configs = tmp;
	}

	public void applyLocation(IDebuggerView window)
	{
		loadLocations();

		final String key = window.getClass().getName();

		final ViewConfiguration loc = configs.get( key );
		if ( loc != null )
		{
			loc.apply( window );

			// link view instance to ViewConfiguration
			// so we can later save the currently active configuration to a file
			configs.put( window.getClass().getName() , loc.withView( window ) );
		}
	}

	public void updateConfiguration(IDebuggerView window)
	{
		loadLocations();
		final ViewConfiguration viewConfig = ViewConfiguration.valueOf(window);
		configs.put( window.getClass().getName() , viewConfig );
	}

	public void saveAll() throws IOException
	{
		loadLocations();

		try ( BufferedWriter fileWriter = new BufferedWriter( new FileWriter( file ) ) )
		{
			for ( Entry<String, ViewConfiguration> entry : configs.entrySet() )
			{
				final String clazz = entry.getKey();
				final IConfigFileWriter configWriter = (String key, String value) ->
				{
					final String line = toConfigLine( clazz , key, value );
					try {
						fileWriter.write(line );
					}
					catch (IOException e)
					{
						throw new RuntimeException(e);
					}
				};

				try {
					entry.getValue().write( configWriter );
				}
				catch(RuntimeException e)
				{
					if ( e.getCause() instanceof IOException )
					{
						throw (IOException) e.getCause();
					}
					throw e;
				}
			}
		}
	}
}