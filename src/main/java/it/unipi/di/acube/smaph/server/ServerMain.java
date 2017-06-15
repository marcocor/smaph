package it.unipi.di.acube.smaph.server;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.ws.rs.ProcessingException;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;
import org.glassfish.grizzly.http.server.HttpServer;
import org.glassfish.grizzly.http.server.StaticHttpHandler;
import org.glassfish.grizzly.servlet.ServletRegistration;
import org.glassfish.grizzly.servlet.WebappContext;
import org.glassfish.jersey.grizzly2.httpserver.GrizzlyHttpServerFactory;
import org.glassfish.jersey.server.ResourceConfig;
import org.glassfish.jersey.servlet.ServletContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unipi.di.acube.smaph.servlet.SmaphContextListener;

public class ServerMain {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	/**
	 * Starts Grizzly HTTP server exposing SMAPH JAX-RS resources.
	 * 
	 * @throws URISyntaxException
	 * @throws ProcessingException
	 */
	public static void startServer(String serverUri, Path storageBase) throws ProcessingException, URISyntaxException {
		LOG.info("Initializing SMAPH services.");
		LOG.info("Storage path: {}", storageBase.toAbsolutePath());

		HttpServer httpServer = GrizzlyHttpServerFactory.createHttpServer(new URI(serverUri));
		httpServer.getServerConfiguration().addHttpHandler(new StaticHttpHandler("src/main/webapp/"), "/*");

		WebappContext context = new WebappContext("smaph");
		ResourceConfig rc = new ResourceConfig().packages("it.unipi.di.acube.smaph.servlet");
		ServletRegistration registration = context.addServlet("ServletContainer", new ServletContainer(rc));
		registration.addMapping("/smaph/*");
		context.addListener(SmaphContextListener.class);
		context.setInitParameter(SmaphContextListener.WIKI_PAGES_DB, storageBase.resolve("mapdb/wikipedia_pages.db").toString());
		context.setInitParameter(SmaphContextListener.FREEBASE_DIR, storageBase.resolve("mapdb/freebase.db").toString());
		context.setInitParameter(SmaphContextListener.ENTITY_TO_ANCHORS_DB, storageBase.resolve("mapdb/e2a.db").toString());
		context.deploy(httpServer);
		try {
			httpServer.start();
			LOG.info("Smaph started with WADL available at " + "{}application.wadl\nPress CTRL^C (SIGINT) to terminate.",
			        serverUri);
			Thread.currentThread().join();
			LOG.info("Shutting server down..");
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
	public static void main(String[] args) throws Exception {
		java.util.logging.Logger l = java.util.logging.Logger.getLogger("org.glassfish.grizzly.http.server.HttpHandler");
		l.setLevel(java.util.logging.Level.FINE);
		l.setUseParentHandlers(false);
		java.util.logging.ConsoleHandler ch = new java.util.logging.ConsoleHandler();
		ch.setLevel(java.util.logging.Level.ALL);
		l.addHandler(ch);

		CommandLineParser parser = new GnuParser();
		Options options = new Options();
		options.addOption("h", "host", true, "Server hostname.");
		options.addOption("p", "port", true, "TCP port to listen.");
		options.addOption("s", "storage_path", true, "Storage path.");
		CommandLine line = parser.parse(options, args);

		String serverUri = String.format("http://%s:%d/smaph", line.getOptionValue("host", "localhost"),
		        Integer.parseInt(line.getOptionValue("port", "8080")));
		Path storage = Paths.get(line.getOptionValue("storage_path"));
		startServer(serverUri, storage);
	}
}
