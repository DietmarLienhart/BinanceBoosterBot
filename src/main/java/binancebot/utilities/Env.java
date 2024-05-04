package binancebot.utilities;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;

public class Env {
	
	private static Properties env;
	
	/** load all properties from file */
	public static void loadProperties() {
		try (InputStream input = new FileInputStream(System.getProperty("user.dir") + "/environment.properties")) {
			env = new Properties();
            env.load(input);
        } catch (IOException ex) {
            ex.printStackTrace();
        }
	}
	
	/**
	 * return property
	 * @param property
	 */
	public static String getProperty(String property) {
		if(env == null) {
			loadProperties();
		}
		return env.getProperty(property);
	}
	
	/**
	 * set property, if not found, take the default
	 * @param property
	 * @return
	 */
	public static String getProperty(String prop, String defaultValue) {
		
		// load file
		if(env == null) {
			loadProperties();
		}
		
		// get from env file or choose default value instead
		String valFromProp = env.getProperty(prop);
		if(valFromProp == null) {
			return defaultValue;
		} else {
			return valFromProp;
		}
	}
	
	/**
	 * returns a list of properties via java list (splits "," and ";"
	 * @param property
	 * @return
	 */
	public static List<String> getProperties(String property) {
		String properties = getProperty(property);
		if(properties.contains(";")) {
			return Arrays.asList(properties.split("[;\\s]+"));
		} else {
			return Arrays.asList(properties.split("[,\\s]+"));
		}
	}

}

