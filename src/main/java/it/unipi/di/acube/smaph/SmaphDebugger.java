package it.unipi.di.acube.smaph;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;

import org.apache.commons.collections4.comparators.ReverseComparator;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Mention;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.metrics.MatchRelation;
import it.unipi.di.acube.batframework.metrics.Metrics;
import it.unipi.di.acube.batframework.metrics.StrongAnnotationMatch;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;

public class SmaphDebugger {
	private List<String> processedQueries = new Vector<>();
	private HashMap<String, List<JSONObject>> websearchResponsesNS = new HashMap<String, List<JSONObject>>();
	private HashMap<String, List<JSONObject>> websearchResponsesWS = new HashMap<String, List<JSONObject>>();
	private HashMap<String, List<Triple<String, HashSet<Annotation>, HashSet<Mention>>>> annotatedSnippetsAndBoldsS3 = new HashMap<>();
	private HashMap<String, List<Triple<Integer, HashMap<String, Double>, Boolean>>> entityFeaturesS1 = new HashMap<>();
	private HashMap<String, List<Triple<Integer, HashMap<String, Double>, Boolean>>> entityFeaturesS2 = new HashMap<>();
	private HashMap<String, List<Triple<Integer, HashMap<String, Double>, Boolean>>> entityFeaturesS3 = new HashMap<>();
	private HashMap<String, List<Triple<Integer, String, Integer>>> source1SearchResult = new HashMap<>();
	private HashMap<String, List<Triple<Integer, String, Integer>>> source2SearchResult = new HashMap<>();
	private HashMap<String, Set<ScoredAnnotation>> result = new HashMap<>();
	private HashMap<String, Set<Integer>> candidateEntities = new HashMap<String, Set<Integer>>(); 
	private HashMap<String, HashMap<HashSet<Annotation>, Pair<HashMap<Annotation, HashMap<String, Double>>, HashMap<String, Double>>>> linkBackAnnotationFeaturesAndBindingFeatures = new HashMap<>();
	private HashMap<String, HashMap<HashSet<Annotation>, Double>> linkBackBindingScore = new HashMap<>();

	public void addProcessedQuery(String query) {
		processedQueries.add(query);
	}

	private static String widToUrl(int wid, WikipediaInterface wikiApi) {
		try {
			return "http://en.wikipedia.org/wiki/"
					+ URLEncoder.encode(wikiApi.getTitlebyId(wid), "utf8")
							.replace("+", "%20");
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	public void addWebsearchResponseNormalSearch(String query,
			List<JSONObject> websearchResponse) {
		this.websearchResponsesNS.put(query, websearchResponse);
	}

	private JSONArray getWebsearchResponseNormalSearch(String query) {
		JSONArray responses = new JSONArray();
		for (JSONObject r : this.websearchResponsesNS.get(query))
			responses.put(r);
		return responses;
	}

	public void addWebsearchResponseWikiSearch(String query, List<JSONObject> websearchResponse) {
		this.websearchResponsesWS.put(query, websearchResponse);
	}

	private JSONArray getWebsearchResponseWikiSearch(String query) {
		JSONArray responses = new JSONArray();
		for (JSONObject r : this.websearchResponsesWS.get(query))
			responses.put(r);
		return responses;
	}

	public void addAnnotatedSnippetS3(String query, String snippet, HashSet<Annotation> annotations, HashSet<Mention> bolds) {
		if (!this.annotatedSnippetsAndBoldsS3.containsKey(query))
			this.annotatedSnippetsAndBoldsS3.put(query, new Vector<>());
		this.annotatedSnippetsAndBoldsS3.get(query).add(new ImmutableTriple<>(snippet, annotations, bolds));
	}

	private JSONObject getTextPartJson(String text, int begin, int end, Collection<Mention> bolds) throws JSONException{
		JSONObject textJs = new JSONObject();
		textJs.put("text", text.substring(begin, end));
		JSONArray boldsJs = new JSONArray();
		textJs.put("bolds", boldsJs);
		for (Mention bold : bolds.stream().filter(b -> new Mention(begin, end-begin).overlaps(b)).collect(Collectors.toList())){
			boldsJs.put(new JSONObject()
					.put("begin",
							Math.max(bold.getPosition() - begin, 0))
					.put("end",
							Math.min(bold.getPosition() + bold.getLength() - begin, end-begin)));
		};
		return textJs;
	}

	private JSONArray getAnnotatedSnippetS3(String query, WikipediaInterface wikiApi) throws JSONException, IOException {
		JSONArray res = new JSONArray();
		if (this.annotatedSnippetsAndBoldsS3.containsKey(query))
			for (Triple<String, HashSet<Annotation>, HashSet<Mention>> p : this.annotatedSnippetsAndBoldsS3.get(query)) {
				JSONObject pairJs = new JSONObject();
				res.put(pairJs);
				pairJs.put("snippet", p.getLeft());
				JSONArray annotationsJs = new JSONArray();
				pairJs.put("parts", annotationsJs);
				int lastIdx = 0;
				for (Annotation a : SmaphUtils.sorted(p.getMiddle())) {
					annotationsJs.put(getTextPartJson(p.getLeft(), lastIdx, a.getPosition(), p.getRight()));

					JSONObject annotationJs = getTextPartJson(p.getLeft(), a.getPosition(), a.getPosition() + a.getLength(),
							p.getRight());
					annotationsJs.put(annotationJs);
					annotationJs.put("title", wikiApi.getTitlebyId(a.getConcept()));
					annotationJs.put("wid", a.getConcept());
					annotationJs.put("url", widToUrl(a.getConcept(), wikiApi));
					lastIdx = a.getPosition() + a.getLength();
				}
				annotationsJs.put(getTextPartJson(p.getLeft(), lastIdx, p.getLeft().length(), p.getRight()));
			}
		return res;
	}

	public void addEntityFeaturesS1(String query, int wid,
			HashMap<String, Double> features, boolean accepted) {
		addEntityFeatures(this.entityFeaturesS1, query, wid, features, accepted);
	}

	public void addEntityFeaturesS2(String query, int wid,
			HashMap<String, Double> features, boolean accepted) {
		addEntityFeatures(this.entityFeaturesS2, query, wid, features, accepted);
	}

	public void addEntityFeaturesS3(String query, int wid,
			HashMap<String, Double> features, boolean accepted) {
		addEntityFeatures(this.entityFeaturesS3, query, wid, features, accepted);
	}

	private ImmutableTriple<Integer, HashMap<String, Double>, Boolean> addEntityFeatures(
			HashMap<String, List<Triple<Integer, HashMap<String, Double>, Boolean>>> source,
			String query, int wid, HashMap<String, Double> features,
			boolean accepted) {
		if (!source.containsKey(query))
			source.put(
					query,
					new Vector<Triple<Integer, HashMap<String, Double>, Boolean>>());
		ImmutableTriple<Integer, HashMap<String, Double>, Boolean> ftrTriple = new ImmutableTriple<>(
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

	public void addSource1SearchResult(String query,
			HashMap<Integer, Integer> rankToIdNS, List<String> urls) {
		addSourceSearchResult(source1SearchResult, query, rankToIdNS, urls);
	}

	public void addSource2SearchResult(String query,
			HashMap<Integer, Integer> rankToIdWS, List<String> urls) {
		addSourceSearchResult(source2SearchResult, query, rankToIdWS, urls);
	}

	public void addResult(String query, HashSet<ScoredAnnotation> annotations) {
		this.result.put(query, annotations);

	}

	private JSONArray getEntityFeaturesJson(
			HashMap<String, List<Triple<Integer, HashMap<String, Double>, Boolean>>> source,
			String query, WikipediaInterface wikiApi) throws JSONException,
			IOException {
		JSONArray res = new JSONArray();
		if (source.containsKey(query))
			for (Triple<Integer, HashMap<String, Double>, Boolean> p : source
					.get(query)) {
				JSONObject pairJs = new JSONObject();
				res.put(pairJs);
				pairJs.put("wid", p.getLeft());
				pairJs.put("title", wikiApi.getTitlebyId(p.getLeft()));
				pairJs.put("url", widToUrl(p.getLeft(), wikiApi));
				JSONObject features = new JSONObject();
				pairJs.put("features", features);
				for (String ftrName : SmaphUtils.sorted(p.getMiddle().keySet()))
					features.put(ftrName, p.getMiddle().get(ftrName));
				pairJs.put("accepted", p.getRight());
			}
		return res;
	}

	private JSONArray getSourceSearchResultJson(
			HashMap<String, List<Triple<Integer, String, Integer>>> source,
			String query, WikipediaInterface wikiApi) throws JSONException, IOException {
		JSONArray res = new JSONArray();
		if (source.containsKey(query))
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

	private JSONArray getResultsJson(String query, WikipediaInterface wikiApi)
			throws JSONException, IOException {
		JSONArray res = new JSONArray();
		if (result.containsKey(query))
			for (ScoredAnnotation a: result.get(query)) {
				JSONObject triple = new JSONObject();
				res.put(triple);
				triple.put("begin", a.getPosition());
				triple.put("end", a.getPosition() + a.getLength());
				triple.put("score", a.getScore());
				triple.put("wid", a.getConcept());
				triple.put("title", wikiApi.getTitlebyId(a.getConcept()));
				triple.put("url", widToUrl(a.getConcept(), wikiApi));
			}
		return res;
	}

	public JSONObject toJson(WikipediaInterface wikiApi)
			throws JSONException, IOException {
		JSONObject dump = new JSONObject();

		for (String query : processedQueries) {
			JSONObject queryData = new JSONObject();
			dump.put(query, queryData);
			JSONObject phase1 = new JSONObject();
			JSONObject phase1S1 = new JSONObject();
			JSONObject phase1S2 = new JSONObject();
			JSONObject phase1S3 = new JSONObject();
			queryData.put("websearchResponseNS", getWebsearchResponseNormalSearch(query));
			queryData.put("websearchResponseWS", getWebsearchResponseWikiSearch(query));
			queryData.put("phase1", phase1);
			phase1.put("source1", phase1S1);
			phase1.put("source2", phase1S2);
			phase1.put("source3", phase1S3);

			/** Populate phase1 - source1 */
			phase1S1.put("pages",
					getSourceSearchResultJson(source1SearchResult, query, wikiApi));
			phase1S1.put("entityFeatures",
					getEntityFeaturesJson(this.entityFeaturesS1, query, wikiApi));

			/** Populate phase1 - source2 */
			phase1S2.put("pages",
					getSourceSearchResultJson(source2SearchResult, query, wikiApi));
			phase1S2.put("entityFeatures",
					getEntityFeaturesJson(this.entityFeaturesS2, query, wikiApi));

			/** Populate phase1 - source3 */
			phase1S3.put("annotatedSnippets",
					getAnnotatedSnippetS3(query, wikiApi));
			phase1S3.put("entityFeatures",
					getEntityFeaturesJson(this.entityFeaturesS3, query, wikiApi));

			/** Populate results */
			queryData.put("results", getResultsJson(query, wikiApi));
		}
		return dump;
	}

	public HashMap<String, Set<Integer>> getCandidateEntities() {
		return new HashMap<String, Set<Integer>>(candidateEntities);
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
	
	public JSONArray getLinkbackBindingFeatures(String query, HashSet<Annotation> goldStandard, WikipediaInterface wikiApi) throws JSONException, IOException {
		MatchRelation<Annotation> sam = new StrongAnnotationMatch(wikiApi);
		Metrics<Annotation> m = new Metrics<Annotation>();
		float bestF1 = -1;
		if (goldStandard != null)
			bestF1 = getBestF1(this.linkBackBindingScore.get(query).keySet(), goldStandard, sam);

		Vector<Pair<JSONObject, Double>> res = new Vector<>();
		if (this.linkBackAnnotationFeaturesAndBindingFeatures.containsKey(query))
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
