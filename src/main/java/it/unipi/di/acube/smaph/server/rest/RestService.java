package it.unipi.di.acube.smaph.server.rest;

import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.problems.Sa2WSystem;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphAnnotatorBuilder;
import it.unipi.di.acube.smaph.SmaphAnnotatorDebugger;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.BingInterface;
import it.cnr.isti.hpc.erd.Annotation;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.aksw.gerbil.transfer.nif.Document;
import org.aksw.gerbil.transfer.nif.Marking;
import org.aksw.gerbil.transfer.nif.TurtleNIFDocumentCreator;
import org.aksw.gerbil.transfer.nif.TurtleNIFDocumentParser;
import org.aksw.gerbil.transfer.nif.data.ScoredNamedEntity;
import org.codehaus.jettison.json.*;

/**
 * @author Marco Cornolti
 */

@Path("")
public class RestService {
	private static WikipediaApiInterface wikiApi;
	private static SmaphAnnotator entityFilterAnn, annotationRegressorAnn, collectiveAnn, defaultAnn;
	private static TurtleNIFDocumentParser parser = new TurtleNIFDocumentParser();
	private static TurtleNIFDocumentCreator creator = new TurtleNIFDocumentCreator();
	private static WikipediaToFreebase wikiToFreeb;

	{
		wikiApi = new WikipediaApiInterface("wid.cache", "redirect.cache");
		SmaphConfig.setConfigFile("smaph-config.xml");
		String bingKey = SmaphConfig.getDefaultBingKey();
		String bingCache = SmaphConfig.getDefaultBingCache();
		try {
			if (bingCache != null)
				BingInterface.setCache(bingCache);
			entityFilterAnn = SmaphAnnotatorBuilder.getDefaultBingAnnotatorEF(wikiApi, bingKey, "models/best_ef");
			annotationRegressorAnn = SmaphAnnotatorBuilder.getDefaultBingAnnotatorIndividualAdvancedAnnotationRegressor(wikiApi, bingKey, "models/best_ar", 0.7);
			collectiveAnn = SmaphAnnotatorBuilder.getDefaultBingAnnotatorCollectiveLBRanklib(wikiApi, bingKey, "models/best_coll");
		} catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		wikiToFreeb = new WikipediaToFreebase("mapdb");

		defaultAnn = annotationRegressorAnn;
	}

	/**
	 * ERD Challenge interface
	 */
	@POST
	@Path("/erd")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces({ MediaType.TEXT_PLAIN })
	public String annotatePost(@FormParam("runID") String runId,
			@FormParam("TextID") String textId,
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
		SmaphAnnotatorDebugger debugger = new SmaphAnnotatorDebugger();
		defaultAnn.setDebugger(debugger);
		defaultAnn.solveSa2W(text);

		try {
			return debugger.toJson(wikiApi).toString();
		} catch (JSONException | IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@POST
	@Path("/default-nif")
	public String nifDefault(String request) {
		return encodeResponseNif(request, defaultAnn);
	}

	@POST
	@Path("/annotation-regressor-nif")
	public String nifAnnotationRegressor(String request) {
		return encodeResponseNif(request, annotationRegressorAnn);
	}

	@POST
	@Path("/entity-filter-nif")
	public String nifEntityFilter(String request) {
		return encodeResponseNif(request, entityFilterAnn);
	}

	@POST
	@Path("/collective-nif")
	public String nifCollective(String request) {
		return encodeResponseNif(request, collectiveAnn);
	}

	@GET
	@Path("/default-json")
	@Produces({ MediaType.APPLICATION_JSON })
	public String annotateDefault(@QueryParam("Text") String text) {
		return encodeResponseJson(defaultAnn.solveSa2W(text));
	}

	@GET
	@Path("/annotation-regressor-json")
	@Produces({ MediaType.APPLICATION_JSON })
	public String annotateAr(@QueryParam("Text") String text) {
		return encodeResponseJson(annotationRegressorAnn.solveSa2W(text));
	}

	@GET
	@Path("/entity-filter-json")
	@Produces({ MediaType.APPLICATION_JSON })
	public String annotateEf(@QueryParam("Text") String text) {
		return encodeResponseJson(entityFilterAnn.solveSa2W(text));
	}

	@GET
	@Path("/collective-json")
	@Produces({ MediaType.APPLICATION_JSON })
	public String annotateColl(@QueryParam("Text") String text) {
		return encodeResponseJson(collectiveAnn.solveSa2W(text));
	}

	private static String encodeResponseNif(String request, SmaphAnnotator ann) {
		Document doc;
		try {
			doc = parser.getDocumentFromNIFString(request);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}

		Vector<Marking> markings = new Vector<>();
		for (ScoredAnnotation aBat : ann.solveSa2W(doc.getText()))
			try {
				markings.add(new ScoredNamedEntity(aBat.getPosition(), aBat.getLength(), wikiApi.getTitlebyId(aBat.getConcept()), aBat.getScore()));
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
					annJson.put(
							"url",
							"http://en.wikipedia.org/wiki/"
									+ URLEncoder.encode(title, "utf8").replace(
											"+", "%20"));
					annotJson.put(annJson);
				}
			}
			res.put("annotations", annotJson);
		} catch (JSONException | IOException e) {
			throw new RuntimeException(e);
		}

		return res.toString();
	}

	public List<Annotation> annotatePure(String query, String textID,
			Sa2WSystem annotator) {
		List<Annotation> annotations = new ArrayList<Annotation>();
		HashSet<ScoredAnnotation> res = annotator
				.solveSa2W(query);
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
			a.setMentionText(query.substring(ann.getPosition(),
					ann.getPosition() + ann.getLength()));
			a.setScore(ann.getScore());
			annotations.add(a);
		}
		return annotations;
	}

	private String encodeResponseERD(String query, String textID, SmaphAnnotator annotator) {
		List<Annotation> annotations = new ArrayList<Annotation>();
		HashSet<ScoredAnnotation> res = annotator
				.solveSa2W(query);
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
			a.setMentionText(query.substring(ann.getPosition(),
					ann.getPosition() + ann.getLength()));
			a.setScore(ann.getScore());
			annotations.add(a);
		}

		StringBuilder sb = new StringBuilder();
		for (Annotation a : annotations)
			sb.append(a.toTsv()).append('\n');
		return sb.toString();
	}
}
