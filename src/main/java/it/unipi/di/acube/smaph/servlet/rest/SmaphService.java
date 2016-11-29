package it.unipi.di.acube.smaph.server.rest;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import javax.ws.rs.Consumes;
import javax.ws.rs.DefaultValue;
import javax.ws.rs.FormParam;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

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

import it.cnr.isti.hpc.erd.Annotation;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.problems.Sa2WSystem;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphBuilder;
import it.unipi.di.acube.smaph.SmaphDebugger;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.SmaphBuilder.SmaphVersion;

/**
 * @author Marco Cornolti
 */

@Path("/")
public class RestService {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static WikipediaApiInterface wikiApi;
	private static SmaphAnnotator entityFilterAnn, annotationRegressorAnn, collectiveAnn, entityFilterAnnNoS2,
	        annotationRegressorAnnNoS2, collectiveAnnNoS2, defaultAnn, defaultAnnNoS2;
	private static TurtleNIFDocumentParser parser;
	private static TurtleNIFDocumentCreator creator;
	private static WikipediaToFreebase wikiToFreeb;
	private static SmaphBuilder.Websearch ws = SmaphBuilder.Websearch.GOOGLE_CSE;

	public static void initialize() {
		parser = new TurtleNIFDocumentParser();
		creator = new TurtleNIFDocumentCreator();
		wikiApi = new WikipediaApiInterface("wid.cache", "redirect.cache");
		SmaphConfig.setConfigFile("smaph-config.xml");

		try {
			entityFilterAnn = SmaphBuilder.getSmaph(SmaphVersion.ENTITY_FILTER, wikiApi, true, ws);
			annotationRegressorAnn = SmaphBuilder.getSmaph(SmaphVersion.ANNOTATION_REGRESSOR, wikiApi, true, ws);
			collectiveAnn = SmaphBuilder.getSmaph(SmaphVersion.ENTITY_FILTER, wikiApi, true, ws);
			entityFilterAnnNoS2 = SmaphBuilder.getSmaph(SmaphVersion.COLLECTIVE, wikiApi, false, ws);
			annotationRegressorAnnNoS2 = SmaphBuilder.getSmaph(SmaphVersion.ANNOTATION_REGRESSOR, wikiApi, false, ws);
			collectiveAnnNoS2 = SmaphBuilder.getSmaph(SmaphVersion.COLLECTIVE, wikiApi, false, ws);
		} catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		wikiToFreeb = new WikipediaToFreebase("mapdb");

		defaultAnn = annotationRegressorAnn;
		defaultAnnNoS2 = annotationRegressorAnnNoS2;
	}

	/**
	 * ERD Challenge interface
	 */
	@POST
	@Path("/erd")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces({ MediaType.TEXT_PLAIN })
	public String annotatePost(@FormParam("runID") String runId, @FormParam("TextID") String textId,
	        @FormParam("Text") String query) {
		SmaphAnnotator ann = null;
		if (runId.equals("entity-filter"))
			ann = entityFilterAnn;
		else if (runId.equals("annotation-regressor"))
			ann = annotationRegressorAnn;
		else if (runId.equals("collective"))
			ann = collectiveAnn;
		return encodeResponseERD(query, textId, ann);
	}

	/**
	 * Debug interface
	 */
	@GET
	@Path("/debug")
	@Produces({ MediaType.APPLICATION_JSON })
	public String debugSmaph(@QueryParam("Text") String text) {
		SmaphDebugger debugger = new SmaphDebugger();
		defaultAnn.solveSa2W(text, debugger);

		try {
			return debugger.toJson(wikiApi).toString();
		} catch (Exception e) {
			LOG.error("Error debugging query: " + text, e);
			throw new RuntimeException(e);
		}
	}

	@POST
	@Path("/annotate-nif")
	public String annotateNif(String request, @QueryParam("q") String q, @QueryParam("annotator") @DefaultValue("default") String annotator,
	        @QueryParam("excludeS2") String excludeS2) {
		return encodeResponseNif(request, getAnnotatorByName(annotator, excludeS2 != null));
	}

	@GET
	@Path("/annotate")
	@Produces({ MediaType.APPLICATION_JSON })
	public String annotateDefault(@QueryParam("q") String q, @QueryParam("annotator") @DefaultValue("default") String annotator,
	        @QueryParam("excludeS2") String excludeS2) {
		return encodeResponseJson(getAnnotatorByName(annotator, excludeS2 != null).solveSa2W(q));
	}

	private Sa2WSystem getAnnotatorByName(String annotator, boolean excludeS2) {
		switch (annotator) {
		case "default":
			return excludeS2 ? defaultAnnNoS2 : defaultAnn;
		case "ef":
			return excludeS2 ? entityFilterAnnNoS2 : entityFilterAnn;
		case "ar":
			return excludeS2 ? annotationRegressorAnnNoS2 : annotationRegressorAnn;
		case "coll":
			return excludeS2 ? collectiveAnnNoS2 : collectiveAnn;
		}
		throw new IllegalArgumentException("Unrecognized annotator identifier " + annotator);
	}

	private static String encodeResponseNif(String request, Sa2WSystem ann) {
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

	private String encodeResponseJson(HashSet<ScoredAnnotation> annotations) {
		JSONObject res = new JSONObject();

		try {
			res.put("response-code", "OK");

			JSONArray annotJson = new JSONArray();
			for (ScoredAnnotation ann : annotations) {
				int wid = ann.getConcept();
				String title = wikiApi.getTitlebyId(ann.getConcept());
				if (wid >= 0 && title != null) {
					JSONObject annJson = new JSONObject();
					annJson.put("wid", wid);
					annJson.put("title", title);
					annJson.put("url", SmaphUtils.getWikipediaURI(title));
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
