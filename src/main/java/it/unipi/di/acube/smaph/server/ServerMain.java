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
	 * Starts Grizzly HTTP server exposing SMAPH JAX-RS resources.
	 */
	public static void startServer(String serverUri) {
		LOG.info("Initializing SMAPH services.");
		RestService.initialize();

		ResourceConfig rc = new ResourceConfig().packages("it.unipi.di.acube.smaph.server.rest");
		StaticHttpHandler staticHandler = new StaticHttpHandler("src/main/resources/webapp/");

		HttpServer httpServer = GrizzlyHttpServerFactory.createHttpServer(URI.create(serverUri), rc);
		httpServer.getServerConfiguration().addHttpHandler(staticHandler, "/");

		Runtime.getRuntime().addShutdownHook(new Thread(new Runnable() {
			@Override
			public void run() {
				LOG.info("Shutting server down..");
				httpServer.shutdown();
			}
		}, "shutdownHook"));

		try {
			httpServer.start();
			LOG.info("Smaph started with WADL available at " + "{}application.wadl\nPress CTRL^C (SIGINT) to terminate.", serverUri);
			Thread.currentThread().join();
		} catch (Exception e) {
			LOG.error("There was an error while starting Grizzly HTTP server.", e);
		}
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
		startServer(serverUri);
	}
}
