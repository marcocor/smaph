package it.unipi.di.acube.smaph.server;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unipi.di.acube.smaph.server.rest.RestService;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;

public class ServerMain {
	public static final String BASE_URI = "http://localhost:8080/smaph/";
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * Starts Grizzly HTTP server exposing JAX-RS resources defined in this
	 * application.
	 * 
	 * @return Grizzly HTTP server.
	 */
	public static HttpServer startServer() {
		final ResourceConfig rc = new ResourceConfig().packages("it.unipi.di.acube.smaph.server.rest");

		return GrizzlyHttpServerFactory.createHttpServer(URI.create(BASE_URI), rc);
	}

	/**
	 * Main method.
	 * 
	 * @param args
	 * @throws IOException
	 */
	public static void main(String[] args) throws IOException {
		LOG.info("Initializing server.");
		RestService.initialize();
		HttpServer server = startServer();
		LOG.info("Smaph started with WADL available at " + "{}application.wadl\nPress Enter to terminate.", BASE_URI);
		System.in.read();
		server.shutdown();
	}
}
