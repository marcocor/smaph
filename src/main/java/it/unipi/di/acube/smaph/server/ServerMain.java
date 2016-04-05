package it.unipi.di.acube.smaph.server;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unipi.di.acube.smaph.server.rest.RestService;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;

public class ServerMain {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * Starts Grizzly HTTP server exposing JAX-RS resources defined in this
	 * application.
	 * 
	 * @return Grizzly HTTP server.
	 */
	public static HttpServer startServer(String serverUri) {
		final ResourceConfig rc = new ResourceConfig().packages("it.unipi.di.acube.smaph.server.rest");
		StaticHttpHandler staticHandler = new StaticHttpHandler("src/main/resources/webapp/");

		HttpServer httpServer = GrizzlyHttpServerFactory.createHttpServer(URI.create(serverUri), rc);
		httpServer.getServerConfiguration().addHttpHandler(staticHandler, "/");
		return httpServer;
	}

	/**
	 * Main method.
	 * 
	 * @param args
	 * @throws IOException
	 * @throws ParseException 
	 */
	public static void main(String[] args) throws IOException, ParseException {
		CommandLineParser parser = new GnuParser();
		Options options = new Options();
		options.addOption(OptionBuilder.withLongOpt("host").hasArg().withArgName("HOSTNAME").withDescription("Server hostname").create("h"));
		options.addOption(OptionBuilder.withLongOpt("port").hasArg().withArgName("PORT").withDescription("TCP port to listen.").create("p"));
		CommandLine line = parser.parse(options, args);

		String serverUri = String.format("http://%s:%d/rest", line.getOptionValue("host", "localhost"), Integer.parseInt(line.getOptionValue("port", "8080")));
		LOG.info("Initializing server.");
		RestService.initialize();
		HttpServer server = startServer(serverUri);
		LOG.info("Smaph started with WADL available at " + "{}application.wadl\nPress Enter to terminate.", serverUri);
		System.in.read();
		server.shutdown();
	}
}
