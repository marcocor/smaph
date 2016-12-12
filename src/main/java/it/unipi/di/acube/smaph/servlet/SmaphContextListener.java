package it.unipi.di.acube.smaph.servlet;

import java.lang.invoke.MethodHandles;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.aksw.gerbil.transfer.nif.TurtleNIFDocumentCreator;
import org.aksw.gerbil.transfer.nif.TurtleNIFDocumentParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unipi.di.acube.batframework.utils.WikipediaLocalInterface;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;

public class SmaphContextListener implements ServletContextListener {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	public final static String WIKI_PAGES_DB = "it.unipi.di.acube.smaph.wiki-pages-db";
	public final static String FREEBASE_DIR = "it.unipi.di.acube.smaph.wiki-to-freebase-db";
	public final static String  ENTITY_TO_ANCHORS_DB = "it.unipi.di.acube.smaph.entity-to-anchors-db";
	@Override
	public void contextInitialized(ServletContextEvent e) {
		LOG.info("Creating Smaph context.");
		ServletContext context = e.getServletContext();
		context.setAttribute("nif-parser", new TurtleNIFDocumentParser());
		context.setAttribute("nif-creator", new TurtleNIFDocumentCreator());
		context.setAttribute("wikipedia-api", WikipediaLocalInterface.open(context.getInitParameter(WIKI_PAGES_DB)));
		context.setAttribute("wiki-to-freebase", WikipediaToFreebase.open(context.getInitParameter(FREEBASE_DIR)));
		context.setAttribute("entity-to-anchors", EntityToAnchors.fromDB(context.getInitParameter(ENTITY_TO_ANCHORS_DB)));
	}

	@Override
	public void contextDestroyed(ServletContextEvent arg0) {
		LOG.info("Destroying Smaph context.");
	}
}
