package it.unipi.di.acube.smaph.servlet;

import java.lang.invoke.MethodHandles;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.aksw.gerbil.transfer.nif.TurtleNIFDocumentCreator;
import org.aksw.gerbil.transfer.nif.TurtleNIFDocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;

public class SmaphContextListener implements ServletContextListener {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	public final static String WIKI_CACHE_WID = "it.unipi.di.acube.smaph.wid-cache";
	public final static String WIKI_CACHE_REDIRECT = "it.unipi.di.acube.smaph.redirect-cache";
	public final static String FREEBASE_DIR = "it.unipi.di.acube.smaph.freebase-dir";

	@Override
	public void contextInitialized(ServletContextEvent e) {
		LOG.info("Creating Smaph context.");
		ServletContext context = e.getServletContext();
		context.setAttribute("nif-parser", new TurtleNIFDocumentParser());
		context.setAttribute("nif-creator", new TurtleNIFDocumentCreator());
		context.setAttribute("wikipedia-api", new WikipediaApiInterface(context.getInitParameter(WIKI_CACHE_WID),
		        context.getInitParameter(WIKI_CACHE_REDIRECT)));
		context.setAttribute("wiki-to-freebase", new WikipediaToFreebase(context.getInitParameter(FREEBASE_DIR)));
	}

	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		LOG.info("Destroying Smaph context.");
	}
}
