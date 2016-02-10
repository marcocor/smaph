package it.unipi.di.acube.smaph.server;

import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;

import java.io.IOException;
import java.net.URI;

public class ServerMain {
	public static final String BASE_URI = "http://localhost:8080/smaph/";

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
		HttpServer server = startServer();
		System.out.println(String.format("Smaph started with WADL available at " + "%sapplication.wadl%nPress Enter to terminate.", BASE_URI));
		System.in.read();
		server.shutdown();
	}
}
