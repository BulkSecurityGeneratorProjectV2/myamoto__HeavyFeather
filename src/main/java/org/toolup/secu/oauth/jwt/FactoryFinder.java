package org.toolup.secu.oauth.jwt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Properties;

import org.toolup.secu.oauth.OAuthException;

public class FactoryFinder {
	
	static Object find(String factId) throws OAuthException{
		final ClassLoader classLoader;

		final String factoryId = factId;

		try {
			classLoader = Thread.currentThread().getContextClassLoader();

		} catch (Exception x) {
			throw new OAuthException(x, 500);
		}
		// Use the system property first
		try {
			String systemProp = System.getProperty( factoryId );

			if( systemProp!=null) {

				return newInstance(systemProp, classLoader);

			}
		} catch (SecurityException se) {

		}

		// try to read from $java.home/lib/jaxm.properties
		try {

			String javah=System.getProperty( "java.home" );

			String configFile = javah + File.separator + "lib" + File.separator + "jaxm.properties";

			final File f=new File( configFile );

			if( f.exists()) {
				Properties props=new Properties();

				props.load( new FileInputStream(f));


				String factoryClassName = props.getProperty(factoryId);

				return newInstance(factoryClassName, classLoader);
			}
		} catch(Exception ex ) {


		}

		String serviceId = "META-INF/services/" + factoryId;

		// try to find services in CLASSPATH
		try {
			InputStream is=null;
			if (classLoader == null) {
				is=ClassLoader.getSystemResourceAsStream(serviceId);
			} else {
				is=classLoader.getResourceAsStream(serviceId);
			}

			if( is!=null ) {
				BufferedReader rd = new BufferedReader(new InputStreamReader(is, "UTF-8"));
				String factoryClassName = rd.readLine();
				rd.close();
				if (factoryClassName != null && ! "".equals(factoryClassName)) {
					return newInstance(factoryClassName, classLoader);
				}
			}
		} catch( Exception ex ) {
		}

		return null;
	}

	static Object find(String factoryId, String fallbackClassName) throws OAuthException{
		Object obj = find(factoryId);
		if (obj != null)
			return obj;
		ClassLoader classLoader;
		try {
			classLoader = Thread.currentThread().getContextClassLoader();
		} catch (Exception x) {
			throw new OAuthException(x, 500);
		}
		if (fallbackClassName == null) {
			throw new OAuthException("Provider for " + factoryId + " cannot be found", 500);
		}

		return newInstance(fallbackClassName, classLoader);
	}

	private static Object newInstance(String className, ClassLoader classLoader) throws OAuthException{
		try {
			Class<?> spiClass;
			if (classLoader == null) {
				spiClass = Class.forName(className);
			} else {
				spiClass = classLoader.loadClass(className);
			}
			return spiClass.newInstance();
		} catch (ClassNotFoundException x) {
			throw new OAuthException( "Provider " + className + " not found", x, 500);
		} catch (Exception x) {
			throw new OAuthException("Provider " + className + " could not be instantiated: " + className, x, 500);
		}
	}
}