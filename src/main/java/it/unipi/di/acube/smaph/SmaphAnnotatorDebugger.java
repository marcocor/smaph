package it.unipi.di.acube.smaph;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.metrics.MatchRelation;
import it.unipi.di.acube.batframework.metrics.Metrics;
import it.unipi.di.acube.batframework.metrics.StrongAnnotationMatch;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;

import java.io.IOException;
import java.io.PrintStream;
import java.net.URLEncoder;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.collections4.comparators.ReverseComparator;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

public class SmaphAnnotatorDebugger {
	public static final PrintStream out = System.out;
	public HashMap<String, List<Triple<String, Integer, HashSet<String>>>> queryToSourceEntityBolds = new HashMap<>();
	private List<String> processedQueries = new Vector<>();
	private HashMap<String, JSONObject> bingResponsesNS = new HashMap<String, JSONObject>();
	private HashMap<String, JSONObject> bingResponsesWS = new HashMap<String, JSONObject>();
	private HashMap<String, List<Triple<String, Integer, Double>>> boldPositionED = new HashMap<>();
	private HashMap<String, List<String>> boldFilterOutput = new HashMap<>();
	private HashMap<String, List<Pair<String, Integer>>> returnedAnnotations = new HashMap<>();
	private HashMap<String, HashMap<Triple<Integer, EntityFeaturePack, Boolean>, String>> ftrToBoldS1 = new HashMap<>();
	private HashMap<String, List<Triple<Integer, EntityFeaturePack, Boolean>>> entityFeaturesS1 = new HashMap<>();
	private HashMap<String, List<Triple<Integer, EntityFeaturePack, Boolean>>> entityFeaturesS2 = new HashMap<>();
	private HashMap<String, List<Triple<Integer, EntityFeaturePack, Boolean>>> entityFeaturesS3 = new HashMap<>();
	private HashMap<String, List<Triple<Integer, EntityFeaturePack, Boolean>>> entityFeaturesS6 = new HashMap<>();
	private HashMap<String, List<Triple<Integer, String, Integer>>> source2SearchResult = new HashMap<>();
	private HashMap<String, List<Triple<Integer, String, Integer>>> source3SearchResult = new HashMap<>();
	private HashMap<String, HashSet<Integer>> result = new HashMap<>();
	private HashMap<String, List<Pair<String, Vector<Pair<Integer, Integer>>>>> snippetsToBolds = new HashMap<>();
	private HashMap<String, Set<Integer>> candidateEntities = new HashMap<String, Set<Integer>>(); 
	private HashMap<String, HashMap<HashSet<Annotation>, Pair<HashMap<Annotation, HashMap<String, Double>>, HashMap<String, Double>>>> linkBackAnnotationFeaturesAndBindingFeatures = new HashMap<>();
	private HashMap<String, HashMap<HashSet<Annotation>, Double>> linkBackBindingScore = new HashMap<>();

	public void addProcessedQuery(String query) {
		processedQueries.add(query);
	}

	public void addQueryCandidateBolds(String query, String source, int entity,
			HashSet<String> bolds) {
		if (!queryToSourceEntityBolds.containsKey(query))
			queryToSourceEntityBolds.put(query,
					new Vector<Triple<String, Integer, HashSet<String>>>());
		boolean update = false;
		for (Triple<String, Integer, HashSet<String>> sourceEntityBold : queryToSourceEntityBolds
				.get(query))
			if (sourceEntityBold.getLeft().equals(source)
					&& sourceEntityBold.getMiddle().equals(entity)) {
				sourceEntityBold.getRight().addAll(bolds);
				update = true;
				break;
			}
		if (!update)
			queryToSourceEntityBolds.get(query).add(
					new ImmutableTriple<String, Integer, HashSet<String>>(
							source, entity, bolds));
	}

	private static String widToUrl(int wid, WikipediaApiInterface wikiApi) {
		try {
			return "http://en.wikipedia.org/wiki/"
					+ URLEncoder.encode(wikiApi.getTitlebyId(wid), "utf8")
							.replace("+", "%20");
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public JSONObject getBoldsToQuery(WikipediaApiInterface wikiApi)
			throws JSONException, IOException {
		JSONObject dump = new JSONObject();
		JSONArray mentionEntityDump = new JSONArray();
		dump.put("dump", mentionEntityDump);
		for (String query : queryToSourceEntityBolds.keySet()) {
			JSONObject queryData = new JSONObject();
			mentionEntityDump.put(queryData);
			queryData.put("query", query);
			JSONArray boldsEntity = new JSONArray();
			queryData.put("boldsEntity", boldsEntity);

			for (Triple<String, Integer, HashSet<String>> data : queryToSourceEntityBolds
					.get(query)) {
				JSONObject entityData = new JSONObject();
				boldsEntity.put(entityData);
				entityData.put("source", data.getLeft());
				entityData.put("wid", data.getMiddle());
				entityData.put("title", wikiApi.getTitlebyId(data.getMiddle()));
				JSONArray bolds = new JSONArray();
				for (String bold : data.getRight())
					bolds.put(bold);
				entityData.put("bolds", bolds);
				entityData.put("url", widToUrl(data.getMiddle(), wikiApi));

			}
		}
		return dump;
	}

	public void addBingResponseNormalSearch(String query,
			JSONObject bingResponse) {
		this.bingResponsesNS.put(query, bingResponse);
	}

	public JSONObject getBingResponseNormalSearch(String query) {
		return this.bingResponsesNS.get(query);
	}

	public void addBingResponseWikiSearch(String query, JSONObject bingResponse) {
		this.bingResponsesWS.put(query, bingResponse);
	}

	public JSONObject getBingResponseWikiSearch(String query) {
		return this.bingResponsesWS.get(query);
	}

	public void addBoldPositionEditDistance(String query,
			List<Pair<String, Integer>> bingBoldsAndRanks) {
		if (!this.boldPositionED.containsKey(query))
			this.boldPositionED.put(query,
					new Vector<Triple<String, Integer, Double>>());
		for (Pair<String, Integer> bingBoldsAndRank : bingBoldsAndRanks)
			this.boldPositionED.get(query).add(
					new ImmutableTriple<>(bingBoldsAndRank.first,
							bingBoldsAndRank.second, SmaphUtils.getMinEditDist(
									query, bingBoldsAndRank.first)));
	}

	public JSONArray getBoldPositionEditDistance(String query)
			throws JSONException {
		JSONArray res = new JSONArray();
		for (Triple<String, Integer, Double> triple : this.boldPositionED
				.get(query)) {
			JSONObject tripleJs = new JSONObject();
			res.put(tripleJs);
			tripleJs.put("bold", triple.getLeft());
			tripleJs.put("rank", triple.getMiddle());
			tripleJs.put("editDistance", triple.getRight());
		}
		return res;
	}

	public void addSnippets(String query,
			List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBold) {
		this.snippetsToBolds.put(query, snippetsToBold);
	}

	public JSONArray getSnippets(String query) throws JSONException {
		JSONArray res = new JSONArray();
		List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBolds = this.snippetsToBolds
				.get(query);
		for (Pair<String, Vector<Pair<Integer, Integer>>> snippetsToBold : snippetsToBolds) {
			JSONObject objI = new JSONObject();
			res.put(objI);
			objI.put("snippet", snippetsToBold.first);
			JSONArray positionsI = new JSONArray();
			objI.put("bold_positions", positionsI);
			for (Pair<Integer, Integer> startAndLength : snippetsToBold.second) {
				JSONObject position = new JSONObject();
				positionsI.put(position);
				position.put("start", startAndLength.first);
				position.put("length", startAndLength.second);
			}
		}
		return res;
	}

	public void addBoldFilterOutput(String query, List<String> bolds) {
		this.boldFilterOutput.put(query, bolds);
	}

	public JSONArray getBoldFilterOutput(String query) throws JSONException {
		JSONArray res = new JSONArray();
		for (String bold : this.boldFilterOutput.get(query))
			res.put(bold);
		return res;
	}

	public void addReturnedAnnotation(String query,
			HashMap<String, Tag> boldToAnnotation) {
		if (!this.returnedAnnotations.containsKey(query))
			this.returnedAnnotations.put(query,
					new Vector<Pair<String, Integer>>());
		for (String bold : boldToAnnotation.keySet())
			this.returnedAnnotations.get(query).add(
					new Pair<>(bold, boldToAnnotation.get(bold).getConcept()));
	}

	public JSONArray getReturnedAnnotations(String query,
			WikipediaApiInterface wikiApi) throws JSONException, IOException {
		JSONArray res = new JSONArray();
		for (Pair<String, Integer> p : this.returnedAnnotations.get(query)) {
			JSONObject pairJs = new JSONObject();
			res.put(pairJs);
			pairJs.put("bold", p.first);
			pairJs.put("wid", p.second);
			pairJs.put("title", wikiApi.getTitlebyId(p.second));
			pairJs.put("url", widToUrl(p.second, wikiApi));
		}
		return res;
	}

	public void addEntityFeaturesS1(String query, String bold, int wid,
			EntityFeaturePack features, boolean accepted) {
		ImmutableTriple<Integer, EntityFeaturePack, Boolean> ftrTriple = addEntityFeatures(
				this.entityFeaturesS1, query, wid, features, accepted);

		if (!ftrToBoldS1.containsKey(query))
			ftrToBoldS1
					.put(query,
							new HashMap<Triple<Integer, EntityFeaturePack, Boolean>, String>());
		ftrToBoldS1.get(query).put(ftrTriple, bold);
	}

	public void addEntityFeaturesS2(String query, int wid,
			EntityFeaturePack features, boolean accepted) {
		addEntityFeatures(this.entityFeaturesS2, query, wid, features, accepted);
	}

	public void addEntityFeaturesS3(String query, int wid,
			EntityFeaturePack features, boolean accepted) {
		addEntityFeatures(this.entityFeaturesS3, query, wid, features, accepted);
	}

	public void addEntityFeaturesS6(String query, int wid,
			EntityFeaturePack features, boolean accepted) {
		addEntityFeatures(this.entityFeaturesS6, query, wid, features, accepted);
	}

	private ImmutableTriple<Integer, EntityFeaturePack, Boolean> addEntityFeatures(
			HashMap<String, List<Triple<Integer, EntityFeaturePack, Boolean>>> source,
			String query, int wid, EntityFeaturePack features,
			boolean accepted) {
		if (!source.containsKey(query))
			source.put(
					query,
					new Vector<Triple<Integer, EntityFeaturePack, Boolean>>());
		ImmutableTriple<Integer, EntityFeaturePack, Boolean> ftrTriple = new ImmutableTriple<>(
				wid, features, accepted);
		source.get(query).add(ftrTriple);
		return ftrTriple;
	}

	private void addSourceSearchResult(
			HashMap<String, List<Triple<Integer, String, Integer>>> source,
			String query, HashMap<Integer, Integer> rankToIdNS,
			List<String> urls) {
		if (!source.containsKey(query))
			source.put(query, new Vector<Triple<Integer, String, Integer>>());
		for (int i = 0; i < urls.size(); i++)
			source.get(query).add(
					new ImmutableTriple<>(i, urls.get(i), rankToIdNS
							.containsKey(i) ? rankToIdNS.get(i) : -1));
	}

	public void addSource2SearchResult(String query,
			HashMap<Integer, Integer> rankToIdNS, List<String> urls) {
		addSourceSearchResult(source2SearchResult, query, rankToIdNS, urls);
	}

	public void addSource3SearchResult(String query,
			HashMap<Integer, Integer> rankToIdWS, List<String> urls) {
		addSourceSearchResult(source3SearchResult, query, rankToIdWS, urls);
	}

	public void addResult(String query, int wid) {
		if (!this.result.containsKey(query))
			this.result.put(query, new HashSet<Integer>());
		this.result.get(query).add(wid);

	}

	private JSONArray getEntityFeaturesJson(
			HashMap<String, List<Triple<Integer, EntityFeaturePack, Boolean>>> source,
			String query, WikipediaApiInterface wikiApi) throws JSONException,
			IOException {
		JSONArray res = new JSONArray();
		if (source.containsKey(query))
			for (Triple<Integer, EntityFeaturePack, Boolean> p : source
					.get(query)) {
				JSONObject pairJs = new JSONObject();
				res.put(pairJs);
				String bold = ftrToBoldS1.get(query).get(p);
				if (bold != null)
					pairJs.put("bold", bold);
				pairJs.put("wid", p.getLeft());
				pairJs.put("title", wikiApi.getTitlebyId(p.getLeft()));
				pairJs.put("url", widToUrl(p.getLeft(), wikiApi));
				JSONObject features = new JSONObject();
				pairJs.put("features", features);
				for (String ftrName : p.getMiddle().getFeatureNames())
					features.put(ftrName, p.getMiddle().getFeature(ftrName));
				pairJs.put("accepted", p.getRight());
			}
		return res;
	}
	
	private JSONArray getSourceSearchResultJson(
			HashMap<String, List<Triple<Integer, String, Integer>>> source,
			String query, WikipediaApiInterface wikiApi) throws JSONException,
			IOException {
		JSONArray res = new JSONArray();
		for (Triple<Integer, String, Integer> t : source.get(query)) {
			JSONObject triple = new JSONObject();
			res.put(triple);
			triple.put("rank", t.getLeft());
			triple.put("wid", t.getRight());
			triple.put("title",
					t.getRight() >= 0 ? wikiApi.getTitlebyId(t.getRight())
							: "---not a wikipedia page---");
			triple.put("url", t.getMiddle());
		}
		return res;
	}
	
	private JSONArray getResultsJson(String query, WikipediaApiInterface wikiApi)
			throws JSONException, IOException {
		JSONArray res = new JSONArray();
		if (result.containsKey(query))
			for (Integer wid : result.get(query)) {
				JSONObject triple = new JSONObject();
				res.put(triple);
				triple.put("wid", wid);
				triple.put("title", wikiApi.getTitlebyId(wid));
				triple.put("url", widToUrl(wid, wikiApi));
			}
		return res;
	}

	public JSONObject toJson(WikipediaApiInterface wikiApi)
			throws JSONException, IOException {
		JSONObject dump = new JSONObject();

		for (String query : processedQueries) {
			JSONObject queryData = new JSONObject();
			dump.put(query, queryData);
			JSONObject phase1 = new JSONObject();
			JSONObject phase1S1 = new JSONObject();
			JSONObject phase1S2 = new JSONObject();
			JSONObject phase1S3 = new JSONObject();
			JSONObject phase1S6 = new JSONObject();
			queryData.put("bingResponseNS", getBingResponseNormalSearch(query));
			queryData.put("bingResponseWS", getBingResponseWikiSearch(query));
			queryData.put("phase1", phase1);
			phase1.put("source1", phase1S1);
			phase1.put("source2", phase1S2);
			phase1.put("source3", phase1S3);
			phase1.put("source6", phase1S6);

			/** Populate phase1 - source1 */
			phase1S1.put("bolds", getBoldPositionEditDistance(query));
			phase1S1.put("snippets", getSnippets(query));
			phase1S1.put("filteredBolds", getBoldFilterOutput(query));
			phase1S1.put("annotations", getReturnedAnnotations(query, wikiApi));
			phase1S1.put("entityFeatures",
					getEntityFeaturesJson(this.entityFeaturesS1, query, wikiApi));

			/** Populate phase1 - source2 */
			phase1S2.put("pages",
					getSourceSearchResultJson(source2SearchResult, query, wikiApi));
			phase1S2.put("entityFeatures",
					getEntityFeaturesJson(this.entityFeaturesS2, query, wikiApi));

			/** Populate phase1 - source3 */
			phase1S3.put("pages",
					getSourceSearchResultJson(source3SearchResult, query, wikiApi));
			phase1S3.put("entityFeatures",
					getEntityFeaturesJson(this.entityFeaturesS3, query, wikiApi));

			/** Populate phase1 - source6 */
			phase1S6.put("entityFeatures",
					getEntityFeaturesJson(this.entityFeaturesS6, query, wikiApi));

			/** Populate results */
			queryData.put("results", getResultsJson(query, wikiApi));
		}
		return dump;
	}

	public HashMap<String, Set<Integer>> getCandidateEntities() {
		return new HashMap<String, Set<Integer>>(candidateEntities);
	}

	public void addCandidateEntities(String query, Set<Tag> candidates) {
		Set<Integer> wids = new HashSet<Integer>();
		for (Tag candidateEntity: candidates)
			wids.add(candidateEntity.getConcept());
		candidateEntities.put(query, wids);
	}

	public void addLinkbackBindingScore(
			String query,
			HashSet<Annotation> binding, double predictedScore) {
		if (!linkBackBindingScore.containsKey(query))
			linkBackBindingScore.put(query, new HashMap<HashSet<Annotation>,Double>());
		linkBackBindingScore.get(query).put(binding, predictedScore);
	}
	
	public void addLinkbackBindingFeatures(
				String query, HashSet<Annotation> binding,
				HashMap<Annotation, HashMap<String, Double>> debugAnnotationFeatures,
				HashMap<String, Double> debugBindingFeatures) {
		if (!linkBackAnnotationFeaturesAndBindingFeatures.containsKey(query))
			linkBackAnnotationFeaturesAndBindingFeatures.put(query, new HashMap<HashSet<Annotation>, Pair<HashMap<Annotation, HashMap<String, Double>>, HashMap<String, Double>>>());
		linkBackAnnotationFeaturesAndBindingFeatures.get(query).put(binding, new Pair<HashMap<Annotation,HashMap<String,Double>>, HashMap<String,Double>>(debugAnnotationFeatures, debugBindingFeatures));
	}
	
	public JSONArray getLinkbackBindingFeatures(String query, HashSet<Annotation> goldStandard, WikipediaApiInterface wikiApi) throws JSONException, IOException {
		MatchRelation<Annotation> sam = new StrongAnnotationMatch(wikiApi);
		Metrics<Annotation> m = new Metrics<Annotation>();
		float bestF1 = -1;
		if (goldStandard != null)
			bestF1 = getBestF1(this.linkBackBindingScore.get(query).keySet(), goldStandard, sam);
		
		Vector<Pair<JSONObject, Double>> res = new Vector<>();
		for (HashSet<Annotation> binding : this.linkBackAnnotationFeaturesAndBindingFeatures.get(query).keySet()) {
			Pair<HashMap<Annotation, HashMap<String, Double>>, HashMap<String, Double>> t = this.linkBackAnnotationFeaturesAndBindingFeatures.get(query).get(binding);
			JSONObject tripleJs = new JSONObject();
			JSONArray annotationsJs = new JSONArray();
			tripleJs.put("annotations", annotationsJs);
			for (Annotation a: SmaphUtils.sorted(t.first.keySet())){
				JSONObject annotationJs = new JSONObject();
				annotationsJs.put(annotationJs);
				annotationJs.put("mention", query.substring(a.getPosition(), a.getPosition()+a.getLength()));
				annotationJs.put("wid", a.getConcept());
				annotationJs.put("title", wikiApi.getTitlebyId(a.getConcept()));
				annotationJs.put("url", widToUrl(a.getConcept(), wikiApi));
				JSONObject annotationFeaturesJs = new JSONObject();
				annotationJs.put("annotation_features", annotationFeaturesJs);
				for (String fName: SmaphUtils.sorted(t.first.get(a).keySet()))
					annotationFeaturesJs.put(fName, t.first.get(a).get(fName));
			}
			
			JSONObject bindingFeaturesJs = new JSONObject();
			tripleJs.put("binding_features", bindingFeaturesJs);
			for (String fName: SmaphUtils.sorted(t.second.keySet(), new CompareFeatureName()))
				bindingFeaturesJs.put(fName, t.second.get(fName));

			double score = this.linkBackBindingScore.get(query).get(binding);
			res.add(new Pair<JSONObject, Double>(tripleJs, score));
			tripleJs.put("predicted_score", score);
			
			if (goldStandard != null){
				float f1 = m.getSingleF1(goldStandard, new HashSet<Annotation>(t.first.keySet()), sam);
				float prec = m.getSinglePrecision(goldStandard, new HashSet<Annotation>(t.first.keySet()), sam);
				float rec = m.getSingleRecall(goldStandard, new HashSet<Annotation>(t.first.keySet()), sam);
				tripleJs.put("strong_f1", f1);
				tripleJs.put("strong_prec", prec);
				tripleJs.put("strong_rec", rec);
				tripleJs.put("is_best_solution", bestF1 == f1);
				tripleJs.put("strong_f1_best_score", bestF1);
			}
		}
		
		Collections.sort(res, new ReverseComparator<>(new SmaphUtils.ComparePairsBySecondElement<JSONObject, Double>()));
		
		JSONArray resJs = new JSONArray();
		for (Pair<JSONObject, Double> p : res)
			resJs.put(p.first);

		return resJs;
	}
	
	private float getBestF1(
			Set<HashSet<Annotation>> binding,
			HashSet<Annotation> goldStandard, MatchRelation<Annotation> sam) {
		float best = 0f;
		Metrics<Annotation> m = new Metrics<>();
		for (HashSet<Annotation> t : binding){
			float f1 = m.getSingleF1(goldStandard, t, sam);
			best = Math.max(best, f1);
		}
		return best;
	}

	public static class CompareFeatureName implements Comparator<String> {
		@Override
		public int compare(String fn1, String fn2) {
			int prefix1 = 0, prefix2 = 0;
			switch (fn1.substring(0,4)){
			case "min_": prefix1 = 1; break;
			case "max_": prefix1 = 2; break;
			case "avg_": prefix1 = 3; break;
			}
			switch (fn2.substring(0,4)){
			case "min_": prefix2 = 1; break;
			case "max_": prefix2 = 2; break;
			case "avg_": prefix2 = 3; break;
			}
			
			if (prefix1 == 0 && prefix2 == 0)
				return fn1.compareTo(fn2);
			else if (prefix1 == 0)
				return 1;
			else if (prefix2 == 0)
				return -1;
			else if (!fn1.substring(4).equals(fn2.substring(4))) //different feature names
				return fn1.substring(4).compareTo(fn2.substring(4));
			else //same feature name
				return prefix1 - prefix2;
		}
	}

}
