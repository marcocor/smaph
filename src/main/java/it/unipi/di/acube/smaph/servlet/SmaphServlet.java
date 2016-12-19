package it.unipi.di.acube.smaph.servlet;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import javax.servlet.ServletContext;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.aksw.gerbil.transfer.nif.Document;
import org.aksw.gerbil.transfer.nif.Marking;
import org.aksw.gerbil.transfer.nif.TurtleNIFDocumentCreator;
import org.aksw.gerbil.transfer.nif.TurtleNIFDocumentParser;
import org.aksw.gerbil.transfer.nif.data.ScoredNamedEntity;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.problems.Sa2WSystem;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphBuilder;
import it.unipi.di.acube.smaph.SmaphBuilder.SmaphVersion;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.SmaphDebugger;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.Annotation;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;

/**
 * @author Marco Cornolti
 */

@Path("/")
public class SmaphServlet {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private final static SmaphVersion DEFAULT_SMAPH_VERSION = SmaphBuilder.SmaphVersion.ANNOTATION_REGRESSOR;

	@Context
	ServletContext context;

	/**
	 * Debug interface
	 */
	@GET
	@Path("/debug")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response debugSmaph(@QueryParam("Text") String text, @QueryParam("google-cse-id") String cseId,
	        @QueryParam("google-api-key") String apiKey) {
		WikipediaInterface wikiApi = (WikipediaInterface) context.getAttribute("wikipedia-api");
		SmaphDebugger debugger = new SmaphDebugger();
		SmaphConfig c = getSmaphConfig(cseId, apiKey);
		getAnnotatorByName("default", c).solveSa2W(text, debugger);

		try {
			return Response.ok(debugger.toJson(wikiApi).toString()).build();
		} catch (Exception e) {
			LOG.error("Error debugging query: " + text, e);
			throw new RuntimeException(e);
		}
	}

	@POST
	@Path("/annotate-nif")
	public Response annotateNif(String request, @QueryParam("q") String q,
	        @QueryParam("annotator") @DefaultValue("default") String annotator, @QueryParam("google-cse-id") String cseId,
	        @QueryParam("google-api-key") String apiKey) {
		if (q == null)
			return Response.serverError().entity("Parameter q required.").build();
		if (cseId == null)
			return Response.serverError().entity("Parameter google-cse-id required.").build();
		if (apiKey == null)
			return Response.serverError().entity("Parameter google-api-key required.").build();
		SmaphConfig c = getSmaphConfig(cseId, apiKey);
		return Response.ok(encodeResponseNif(request, getAnnotatorByName(annotator, c))).build();
	}

	@GET
	@Path("/annotate")
	@Produces({ MediaType.APPLICATION_JSON })
	public Response annotateDefault(@QueryParam("q") String q, @QueryParam("annotator") @DefaultValue("default") String annotator,
	        @QueryParam("google-cse-id") String cseId, @QueryParam("google-api-key") String apiKey) {
		if (q == null)
			return Response.serverError().entity("Parameter q required.").build();
		if (cseId == null)
			return Response.serverError().entity("Parameter google-cse-id required.").build();
		if (apiKey == null)
			return Response.serverError().entity("Parameter google-api-key required.").build();
		SmaphConfig c = getSmaphConfig(cseId, apiKey);
		SmaphAnnotator ann = getAnnotatorByName(annotator, c);
		return Response.ok(encodeResponseJson(ann.solveSa2W(q), ann)).build();
	}

	private SmaphConfig getSmaphConfig(String cseId, String apiKey) {
		return new SmaphConfig(null, null, null, apiKey, cseId, null, null, null, null, null);
	}

	private SmaphAnnotator getAnnotatorByName(String annotator, SmaphConfig c) {
		WikipediaInterface wikiApi = (WikipediaInterface) context.getAttribute("wikipedia-api");
		WikipediaToFreebase wikiToFreebase = (WikipediaToFreebase) context.getAttribute("wiki-to-freebase");
		EntityToAnchors e2a = (EntityToAnchors) context.getAttribute("entity-to-anchors");
		try {
			switch (annotator) {
			case "default":
				return SmaphBuilder.getSmaph(DEFAULT_SMAPH_VERSION, wikiApi, wikiToFreebase, SmaphBuilder.DEFAULT_AUX_ANNOTATOR,
				        e2a, c);
			case "ef":
				return SmaphBuilder.getSmaph(SmaphVersion.ENTITY_FILTER, wikiApi, wikiToFreebase,
				        SmaphBuilder.DEFAULT_AUX_ANNOTATOR, e2a, c);
			case "ar":
				return SmaphBuilder.getSmaph(SmaphVersion.ANNOTATION_REGRESSOR, wikiApi, wikiToFreebase,
				        SmaphBuilder.DEFAULT_AUX_ANNOTATOR, e2a, c);
			case "coll":
				return SmaphBuilder.getSmaph(SmaphVersion.COLLECTIVE, wikiApi, wikiToFreebase, SmaphBuilder.DEFAULT_AUX_ANNOTATOR,
				        e2a, c);
			}
		} catch (ClassNotFoundException | IOException e) {
			throw new RuntimeException(e);
		}
		throw new IllegalArgumentException("Unrecognized annotator identifier " + annotator);
	}

	private String encodeResponseNif(String request, Sa2WSystem ann) {
		WikipediaInterface wikiApi = (WikipediaInterface) context.getAttribute("wikipedia-api");
		TurtleNIFDocumentCreator creator = (TurtleNIFDocumentCreator) context.getAttribute("nif-creator");
		TurtleNIFDocumentParser parser = (TurtleNIFDocumentParser) context.getAttribute("nif-parser");
		Document doc;
		try {
			doc = parser.getDocumentFromNIFString(request);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		Vector<Marking> markings = new Vector<>();
		for (ScoredAnnotation aBat : ann.solveSa2W(doc.getText()))
			try {
				markings.add(new ScoredNamedEntity(aBat.getPosition(), aBat.getLength(),
				        SmaphUtils.getDBPediaURI(wikiApi.getTitlebyId(aBat.getConcept())), aBat.getScore()));
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		doc.setMarkings(markings);
		return creator.getDocumentAsNIFString(doc);
	}

	private String encodeResponseJson(HashSet<ScoredAnnotation> annotations, SmaphAnnotator annotator) {
		WikipediaInterface wikiApi = (WikipediaInterface) context.getAttribute("wikipedia-api");
		JSONObject res = new JSONObject();

		try {
			res.put("response-code", "OK");
			res.put("annotator", annotator.getName());

			JSONArray annotJson = new JSONArray();
			for (ScoredAnnotation ann : annotations) {
				int wid = ann.getConcept();
				String title = wikiApi.getTitlebyId(ann.getConcept());
				if (wid >= 0 && title != null) {
					JSONObject annJson = new JSONObject();
					annJson.put("begin", ann.getPosition());
					annJson.put("end", ann.getPosition() + ann.getLength());
					annJson.put("wid", wid);
					annJson.put("title", title);
					annJson.put("url", SmaphUtils.getWikipediaURI(title));
					annJson.put("score", ann.getScore());
					annotJson.put(annJson);
				}
			}
			res.put("annotations", annotJson);
		} catch (JSONException | IOException e) {
			throw new RuntimeException(e);
		}

		return res.toString();
	}

	public List<Annotation> annotatePure(String query, String textID, Sa2WSystem annotator) {
		WikipediaInterface wikiApi = (WikipediaInterface) context.getAttribute("wikipedia-api");
		WikipediaToFreebase wikiToFreeb = (WikipediaToFreebase) context.getAttribute("wiki-to-freebase");

		List<Annotation> annotations = new ArrayList<Annotation>();
		HashSet<ScoredAnnotation> res = annotator.solveSa2W(query);
		HashMap<Annotation, String> annToTitle = new HashMap<>();
		for (ScoredAnnotation ann : res) {
			Annotation a = new Annotation();
			a.setQid(textID);
			a.setInterpretationSet(0);
			int wid = ann.getConcept();
			String title = null;
			try {
				title = wikiApi.getTitlebyId(wid);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			String mid = wikiToFreeb.getFreebaseId(title);
			annToTitle.put(a, title);
			if (mid == null)
				continue;
			a.setPrimaryId(mid);
			a.setMentionText(query.substring(ann.getPosition(), ann.getPosition() + ann.getLength()));
			a.setScore(ann.getScore());
			annotations.add(a);
		}
		return annotations;
	}

	private String encodeResponseERD(String query, String textID, SmaphAnnotator annotator) {
		WikipediaInterface wikiApi = (WikipediaInterface) context.getAttribute("wikipedia-api");
		WikipediaToFreebase wikiToFreeb = (WikipediaToFreebase) context.getAttribute("wiki-to-freebase");

		List<Annotation> annotations = new ArrayList<Annotation>();
		HashSet<ScoredAnnotation> res = annotator.solveSa2W(query);
		HashMap<Annotation, String> annToTitle = new HashMap<>();
		for (ScoredAnnotation ann : res) {
			Annotation a = new Annotation();
			a.setQid(textID);
			a.setInterpretationSet(0);
			int wid = ann.getConcept();
			String title = null;
			try {
				title = wikiApi.getTitlebyId(wid);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			String mid = wikiToFreeb.getFreebaseId(title);
			annToTitle.put(a, title);
			if (mid == null)
				continue;
			a.setPrimaryId(mid);
			a.setMentionText(query.substring(ann.getPosition(), ann.getPosition() + ann.getLength()));
			a.setScore(ann.getScore());
			annotations.add(a);
		}

		StringBuilder sb = new StringBuilder();
		for (Annotation a : annotations)
			sb.append(a.toTsv()).append('\n');
		return sb.toString();
	}
}
