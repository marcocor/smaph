/**
 *  Copyright 2014 Marco Cornolti
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package it.unipi.di.acube.smaph;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URLDecoder;
import java.util.Calendar;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Mention;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.ScoredTag;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.metrics.MatchRelation;
import it.unipi.di.acube.batframework.metrics.Metrics;
import it.unipi.di.acube.batframework.metrics.StrongAnnotationMatch;
import it.unipi.di.acube.batframework.metrics.StrongMentionAnnotationMatch;
import it.unipi.di.acube.batframework.metrics.StrongTagMatch;
import it.unipi.di.acube.batframework.problems.Sa2WSystem;
import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.batframework.utils.AnnotationException;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.ProblemReduction;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.searchapi.WebsearchApi;
import it.unipi.di.acube.searchapi.model.WebsearchResponse;
import it.unipi.di.acube.searchapi.model.WebsearchResponseEntry;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;
import it.unipi.di.acube.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.GreedyFeaturePack;
import it.unipi.di.acube.smaph.learn.models.entityfilters.EntityFilter;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.AdvancedIndividualLinkback;
import it.unipi.di.acube.smaph.linkback.CollectiveLinkBack;
import it.unipi.di.acube.smaph.linkback.LinkBack;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.BindingGenerator;
import it.unipi.di.acube.smaph.main.ERDDatasetFilter;
import it.unipi.di.acube.smaph.snippetannotationfilters.SnippetAnnotationFilter;

public class SmaphAnnotator implements Sa2WSystem {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static final Pattern WIKI_URL_PATTERN = Pattern.compile("https?://en.wikipedia.org/wiki/(.+)");
	private WikipediaInterface wikiApi;
	private WebsearchApi websearchApi = null;
	private WATAnnotator snippetAnnotator;
	private EntityFilter entityFilter;
	private FeatureNormalizer entityFilterNormalizer;
	private LinkBack linkBack;
	private boolean includeSourceWikiResults;
	private boolean includeSourceSnippets;
	private int topKAnnotateSnippet;
	private int topKWikiResults;
	private boolean includeSourceWikiSearchResults;
	private int topKWikiSearch;
	private boolean predictNEonly;
	private String appendName = "";
	private SnippetAnnotationFilter snippetAnnotationFilter;
	private double anchorMaxED;
	private BindingGenerator bg;
	private WikipediaToFreebase wikiToFreeb;
	private EntityToAnchors e2a;
	private long lastAnnotationTime;

	/**
	 * Constructs a SMAPH annotator.
	 * @param includeSourceWikiResults
	 *            true iff Source 1 has to be enabled.
	 * @param includeSourceWikiSearchResults
	 *            true iff Source 2 has to be enabled.
	 * @param topKwikiSearch
	 *            Source 3 results limit.
	 * @param entityFilter
	 *            the entity filter used in the second stage.
	 * @param entityFilterNormalizer
	 *            the entity filter feature normalizer.
	 * @param wikiApi
	 *            an API to Wikipedia.
	 * @param wikiToFreeb 
	 * @param searchApi
	 *            the key to the search engine API.
	 * @param auxDisambiguator
	 *            the disambiguator used in Source 1.
	 * @param boldFilter
	 *            the filter of the bolds used in Source 1.
	 * @param includeSourceAnnotator
	 *            true iff Source 3 has to be enabled.
	 * @param topKRelatedSearch
	 *            Source 4 results limit.
	 */
	public SmaphAnnotator(boolean includeSourceWikiResults, int topKWikiResults, boolean includeSourceWikiSearchResults,
	        int topKwikiSearch, boolean includeSourceSnippets, int topKSnippets, double anchorMaxED, boolean tagTitles,
	        LinkBack linkBack, EntityFilter entityFilter, FeatureNormalizer entityFilterNormalizer, BindingGenerator bg,
	        WATAnnotator snippetAnnotator, SnippetAnnotationFilter snippetAnnotationFilter, WikipediaInterface wikiApi,
	        WikipediaToFreebase wikiToFreeb, WebsearchApi searchApi, EntityToAnchors e2a) {
		this.entityFilter = entityFilter;
		this.entityFilterNormalizer = entityFilterNormalizer;
		this.linkBack = linkBack;
		this.wikiApi = wikiApi;
		this.includeSourceWikiResults = includeSourceWikiResults;
		this.includeSourceWikiSearchResults = includeSourceWikiSearchResults;
		this.topKWikiSearch = topKwikiSearch;
		this.websearchApi =  searchApi;
		this.includeSourceSnippets = includeSourceSnippets;
		this.snippetAnnotationFilter = snippetAnnotationFilter;
		this.topKAnnotateSnippet = topKSnippets;
		this.topKWikiResults = topKWikiResults;
		this.snippetAnnotator = snippetAnnotator;
		this.anchorMaxED = anchorMaxED;
		this.bg = bg;
		this.wikiToFreeb = wikiToFreeb;
		this.e2a = e2a;
	}

	public void setPredictNEOnly(boolean predictNEonly) {
		this.predictNEonly = predictNEonly;
	}

	@Override
	public HashSet<Annotation> solveA2W(String text) throws AnnotationException {
		return ProblemReduction.Sa2WToA2W(solveSa2W(text));
	}

	@Override
	public HashSet<Tag> solveC2W(String text) throws AnnotationException {
		return ProblemReduction.A2WToC2W(ProblemReduction
				.Sa2WToA2W(solveSa2W(text)));
	}

	@Override
	public String getName() {
		return "Smaph annotator" + appendName;
	}

	@Override
	public long getLastAnnotationTime() {
		return lastAnnotationTime;
	}

	@Override
	public HashSet<Annotation> solveD2W(String text, HashSet<Mention> mentions)
			throws AnnotationException {
		return null;
	}

	@Override
	public HashSet<ScoredTag> solveSc2W(String text) throws AnnotationException {
		return null;
	}

	@Override
	public HashSet<ScoredAnnotation> solveSa2W(String query) throws AnnotationException {
		return solveSa2W(query, null);
	}

	public HashSet<ScoredAnnotation> solveSa2W(String query, SmaphDebugger debugger) throws AnnotationException {
		HashSet<ScoredAnnotation> annotations = new HashSet<>();
		try {
			lastAnnotationTime = Calendar.getInstance().getTimeInMillis();
			HashSet<Tag> acceptedEntities = new HashSet<>();

			QueryInformation qi = getQueryInformation(query, debugger);

			if (debugger != null){
				debugger.addProcessedQuery(query);
				debugger.addQueryInformation(query, qi);
			}

			for (Tag candidate : qi.allCandidates()){
				if (predictNEonly
						&& !ERDDatasetFilter.entityIsNE(wikiApi, wikiToFreeb, candidate.getConcept()))
					continue;
				EntityFeaturePack fp = new EntityFeaturePack(candidate, query, qi, wikiApi, wikiToFreeb);
				boolean accept = entityFilter.filterEntity(fp, entityFilterNormalizer);
				if (accept){
					acceptedEntities.add(candidate);
				}
			}
			/** Link entities back to query mentions */
			annotations = linkBack.linkBack(query, acceptedEntities, qi);
			
			lastAnnotationTime = Calendar.getInstance().getTimeInMillis() - lastAnnotationTime;

			if (debugger != null){
				debugger.addResult(query, annotations);
				Set<Tag> resultsTag = annotations.stream().map(a -> new Tag(a.getConcept())).collect(Collectors.toSet());

				for (Tag candidate : qi.candidatesNS)
					debugger.addEntityFeaturesS1(query, candidate.getConcept(), EntityFeaturePack.getFeatures(candidate, query, qi, wikiApi, wikiToFreeb), resultsTag.contains(candidate));
				
				for (Tag candidate : qi.candidatesWS)
					debugger.addEntityFeaturesS2(query, candidate.getConcept(), EntityFeaturePack.getFeatures(candidate, query, qi, wikiApi, wikiToFreeb), resultsTag.contains(candidate));
				
				for (Tag candidate : qi.candidatesSA)
					debugger.addEntityFeaturesS3(query, candidate.getConcept(), EntityFeaturePack.getFeatures(candidate, query, qi, wikiApi, wikiToFreeb), resultsTag.contains(candidate));
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		LOG.info("*** FINISHED PROCESSING QUERY [{}] ***", query);

		return annotations;

	}

	/**
	 * Turns a Wikipedia URL to the title of the Wikipedia page.
	 * 
	 * @param encodedWikiUrl
	 * @return a Wikipedia title, or null if the url is not a Wikipedia page.
	 */
	private static String decodeWikiUrl(String encodedWikiUrl) {
		Matcher matcher = WIKI_URL_PATTERN.matcher(encodedWikiUrl);
		if (!matcher.matches())
			return null;
		try {
			String title = URLDecoder.decode(matcher.group(1), "utf-8");
			if (!SmaphUtils.acceptWikipediaTitle(title))
				return null;
			return WikipediaInterface.normalize(title);

		} catch (IllegalArgumentException | UnsupportedEncodingException e) {
			return null;

		}

	}

	/**
	 * Issue a query to the search engine and extract the result.
	 * 
	 * @param query
	 *            the query to be issued to the search engine
	 * @param boldsAndRanks
	 *            storage for the bolds (a pair &lt;bold, rank&gt; means bold
	 *            appeared in the snippets of the result in position rank)
	 * @param urls
	 *            storage for the urls found by the search engine.
	 * @param relatedSearch
	 *            storage for the "related search" suggestions.
	 * @param snippetsToBolds
	 *            storage for the list of pairs &lt;snippets, the bolds found in
	 *            that snippet&gt;
	 * @return a triple &lt;results, webTotal, websearchReply&gt; where results is
	 *         the number of results returned by the search engine, webTotal is the number of
	 *         pages found by the search engine, and websearchReply is the raw search engine reply.
	 * @param topk
	 *            limit to top-k results.
	 * @param wikisearch
	 *            whether to append the word "wikipedia" to the query or not.
	 * @throws Exception
	 *             if something went wrong while querying the search engine.
	 */

	private Triple<Integer, Double, List<JSONObject>> takeWebsearchData(String query,
			List<Pair<String, Integer>> boldsAndRanks, List<String> urls,
			List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBolds,
			int topk, boolean wikisearch) throws Exception {
		if (!boldsAndRanks.isEmpty())
			throw new RuntimeException("boldsAndRanks must be empty");
		if (!urls.isEmpty())
			throw new RuntimeException("urls must be empty");
		if (wikisearch)
			query += " wikipedia";
		WebsearchResponse websearchReply = websearchApi.query(query, topk);
		double webTotal = websearchReply.getTotalResults();

		getBoldsAndUrls(websearchReply, topk, boldsAndRanks, urls, snippetsToBolds);

		return new ImmutableTriple<Integer, Double, List<JSONObject>>(
				websearchReply.getWebEntries().size(), webTotal, websearchReply.getJsonResponses());
	}
	
	/**
	 * From the websearch results extract the bolds and the urls.
	 * 
	 * @param websearchResult
	 *            the web results returned by the search engine.
	 * @param topk
	 *            limit the extraction to the first topk results.
	 * @param boldsAndRanks
	 *            storage for the bolds and their rank.
	 * @param urls
	 *            storage for the result URLs.
	 * @param tagTitles
	 *            whether to concatenate the title to the description before tagging it.
	 * @param snippetsToBolds
	 *            storage for the list of pairs &lt;snippets, the bolds found in
	 *            that snippet&gt;
	 * @throws JSONException
	 *             if the json returned by the search engine could not be read.
	 */
	private static void getBoldsAndUrls(WebsearchResponse websearchResult, int topk,
			List<Pair<String, Integer>> boldsAndRanks, List<String> urls,
			List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBolds)
					throws JSONException {
		List<WebsearchResponseEntry> webEntries = websearchResult.getWebEntries();
		for (int i = 0; i < Math.min(webEntries.size(), topk); i++) {
			WebsearchResponseEntry entry = webEntries.get(i);
			String descI = entry.getSnippet();
			String url = entry.getDisplayUrl();
			urls.add(url);
			getBolds(descI, i, snippetsToBolds, boldsAndRanks);
		}
	}

	private static void getBolds(String field,int rankI, List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBolds, List<Pair<String, Integer>> boldsAndRanks){
		String snippet = "";
		field = field.replaceAll(WebsearchApi.SNIPPET_BOLD_END_STR + "." + WebsearchApi.SNIPPET_BOLD_START_STR, " ");
		int startIdx = field.indexOf(WebsearchApi.SNIPPET_BOLD_START_STR);
		int stopIdx = field.indexOf(WebsearchApi.SNIPPET_BOLD_END_STR, startIdx);
		int lastStop = -1;
		Vector<Pair<Integer, Integer>> boldPosInSnippet = new Vector<>();
		while (startIdx != -1 && stopIdx != -1) {
			String spot = field.subSequence(startIdx + 1, stopIdx)
					.toString();
			boldsAndRanks.add(new Pair<String, Integer>(spot, rankI));
			snippet += field.substring(lastStop + 1, startIdx);
			boldPosInSnippet.add(new Pair<Integer, Integer>(snippet
					.length(), spot.length()));
			snippet += spot;
			lastStop = stopIdx;
			startIdx = field.indexOf(WebsearchApi.SNIPPET_BOLD_START_STR, startIdx + 1);
			stopIdx = field.indexOf(WebsearchApi.SNIPPET_BOLD_END_STR, startIdx + 1);
		}
		snippet += field.substring(lastStop + 1);
		if (snippetsToBolds != null)
			snippetsToBolds
			.add(new Pair<String, Vector<Pair<Integer, Integer>>>(
					snippet, boldPosInSnippet));
	}

	/**
	 * Given a list of urls, creates a mapping from the url position to the
	 * Wikipedia page ID of that URL. If an url is not a Wikipedia url, no
	 * mapping is added.
	 * 
	 * @param urls
	 *            a list of urls.
	 * @param limit 
	 * @return a mapping from position to Wikipedia page IDs.
	 */
	private HashMap<Integer, Integer> urlsToRankID(List<String> urls, int limit) {
		HashMap<Integer, Integer> result = new HashMap<>();
		HashMap<Integer, String> rankToTitle = new HashMap<>();
		for (int i = 0; i < urls.size() && i < limit; i++) {
			String title = decodeWikiUrl(urls.get(i));
			if (title != null)
				rankToTitle.put(i, title);
		}

		try {
			wikiApi.prefetchTitles(new Vector<String>(rankToTitle.values()));
		} catch (XPathExpressionException | IOException
				| ParserConfigurationException | SAXException e) {
			throw new RuntimeException(e);
		}
		for (int rank : rankToTitle.keySet()) {
			int wid;
			try {
				wid = wikiApi.getIdByTitle(rankToTitle.get(rank));
				if (wid > 0)
					wid = wikiApi.dereference(wid);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			if (wid > 0 && !result.containsValue(wid)) {
				result.put(rank, wid);
				LOG.debug(
						"Found Wikipedia url:{} rank:{} id:{}",
						urls.get(rank), rank, wid);
			} else
				LOG.debug(
						"Discarding Wikipedia url:{} rank:{} id:{} (double entity or invalid title)",
						urls.get(rank), rank, wid);
		}
		return result;
	}


	private QueryInformation getQueryInformation(String query, SmaphDebugger debugger) throws Exception {

		/** Search the query on the search engine */
		List<Pair<String, Integer>> boldsAndRankNS = null;
		List<String> urlsNS = null;
		Triple<Integer, Double, List<JSONObject>> resCountAndWebTotalNS = null;
		int resultsCountNS = -1;
		double webTotalNS = Double.NaN;
		HashMap<Integer, Integer> rankToIdNS = null;
		HashMap<Integer, HashSet<String>> rankToBoldsNS = null;
		List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBoldsNS = null;
		HashMap<Tag, String[]> entityToBoldsNS = new HashMap<>();
		List<String> allBoldsNS = null;
		Set<Tag> candidatesNS = null;
		HashMap<Integer, Integer> idToRankNS = null;
		if (includeSourceWikiSearchResults || includeSourceWikiResults || includeSourceSnippets) {
			boldsAndRankNS = new Vector<>();
			urlsNS = new Vector<>();
			snippetsToBoldsNS = new Vector<>();
			candidatesNS = new HashSet<>();
			resCountAndWebTotalNS = takeWebsearchData(query, boldsAndRankNS,
					urlsNS, snippetsToBoldsNS, Math.max(topKAnnotateSnippet, topKWikiResults),
					false);
			resultsCountNS = resCountAndWebTotalNS.getLeft();
			webTotalNS = resCountAndWebTotalNS.getMiddle();
			rankToIdNS = urlsToRankID(urlsNS, topKWikiResults);
			idToRankNS  = SmaphUtils.inverseMap(rankToIdNS);
			rankToBoldsNS = new HashMap<>();
			allBoldsNS = SmaphUtils.boldPairsToListLC(boldsAndRankNS);
			SmaphUtils.mapRankToBoldsLC(boldsAndRankNS, rankToBoldsNS, null);
			for (int rank : rankToIdNS.keySet()) {
				String[] bolds = rankToBoldsNS.containsKey(rank) ? rankToBoldsNS
						.get(rank).toArray(new String[] {}) : new String[] {};
				Tag candidate = new Tag(rankToIdNS.get(rank));
				entityToBoldsNS.put(candidate, bolds);
				candidatesNS.add(candidate);		
			}

			if (debugger != null) {
				debugger.addSource1SearchResult(query, rankToIdNS, urlsNS);
				debugger.addWebsearchResponseNormalSearch(query,
						resCountAndWebTotalNS.getRight());
			}
		}

		/** Do the WikipediaSearch on the search engine. */
		List<String> wikiSearchUrls = new Vector<>();
		List<Pair<String, Integer>> boldsAndRankWS = new Vector<>();
		Triple<Integer, Double, List<JSONObject>> resCountAndWebTotalWS = null;
		HashMap<Integer, HashSet<String>> rankToBoldsWS = null;
		Set<Tag> candidatesWS = null;
		double webTotalWS = Double.NaN;
		HashMap<Integer, Integer> rankToIdWS = null;
		HashMap<Integer, Integer> idToRankWS = null;
		if (includeSourceWikiSearchResults | includeSourceWikiResults) {
			resCountAndWebTotalWS = takeWebsearchData(query, boldsAndRankWS,
					wikiSearchUrls, null, topKWikiSearch, true);
			webTotalWS = resCountAndWebTotalWS.getMiddle();
			rankToIdWS = urlsToRankID(wikiSearchUrls, topKWikiSearch);
			idToRankWS = SmaphUtils.inverseMap(rankToIdWS);
			candidatesWS = new HashSet<>();
			for (int wid : rankToIdWS.values())
				candidatesWS.add(new Tag(wid));
			rankToBoldsWS = new HashMap<>();
			SmaphUtils.mapRankToBoldsLC(boldsAndRankWS, rankToBoldsWS, null);
			if (debugger != null) {
				debugger.addSource2SearchResult(query, rankToIdWS,
						wikiSearchUrls);
				debugger.addWebsearchResponseWikiSearch(query,
						resCountAndWebTotalWS.getRight());

			}
		}

		/** Annotate snippets */
		HashMap<Tag,List<Integer>> entityToRanksSA = null;
		HashMap<Tag,List<String>> entityToMentionsSA = null;
		HashMap<Tag,List<String>> entityToBoldsSA = null;
		HashMap<Tag,List<HashMap<String,Double>>> entiyToAdditionalInfosSA = null;
		HashSet<Tag> filteredAnnotationsSA = null;
		if (includeSourceSnippets){
			List<List<Pair<ScoredAnnotation, HashMap<String, Double>>>> snippetAnnotations = new Vector<>();
			entityToBoldsSA = new HashMap<>();
			annotateSnippets(snippetsToBoldsNS, snippetAnnotations, entityToBoldsSA, debugger, query);
			entityToRanksSA = getSnippetAnnotationRanks(snippetAnnotations);
			entityToMentionsSA = getSnippetMentions(snippetAnnotations, snippetsToBoldsNS);
			entiyToAdditionalInfosSA = getSnippetAdditionalInfo(snippetAnnotations);
			filteredAnnotationsSA = snippetAnnotationFilter.filterAnnotations(entityToRanksSA, resultsCountNS);
		}

		QueryInformation qi = new QueryInformation();
		qi.includeSourceNormalSearch = includeSourceWikiResults;
		qi.includeSourceWikiSearch = includeSourceWikiSearchResults;
		qi.includeSourceSnippets = includeSourceSnippets;

		qi.idToRankNS = idToRankNS;
		qi.webTotalNS = webTotalNS;
		qi.allBoldsNS = allBoldsNS;
		qi.boldsAndRankNS = boldsAndRankNS;
		qi.resultsCountNS = resultsCountNS;

		qi.idToRankWS = idToRankWS;
		qi.webTotalWS = webTotalWS;
		qi.boldsAndRankWS = boldsAndRankWS;

		qi.entityToBoldsSA = entityToBoldsSA;
		qi.entityToMentionsSA = entityToMentionsSA;
		qi.entityToRanksSA = entityToRanksSA;
		qi.entityToAdditionalInfosSA = entiyToAdditionalInfosSA;

		qi.candidatesSA = filteredAnnotationsSA;
		qi.candidatesNS = candidatesNS;
		qi.candidatesWS = candidatesWS;

		return qi;
	}

	private HashMap<Tag, List<HashMap<String, Double>>> getSnippetAdditionalInfo(
			List<List<Pair<ScoredAnnotation, HashMap<String, Double>>>> snippetsAnnotations) {
		HashMap<Tag, List<HashMap<String, Double>>> res = new HashMap<>();
		for (List<Pair<ScoredAnnotation, HashMap<String, Double>>> snippetAnnotation : snippetsAnnotations)
			for (Pair<ScoredAnnotation, HashMap<String, Double>> p : snippetAnnotation) {
				ScoredAnnotation ann = p.first;
				HashMap<String, Double> additionalInfo = p.second;
				Tag t = new Tag(ann.getConcept());
				if (!res.containsKey(t))
					res.put(t, new Vector<HashMap<String, Double>>());
				res.get(t).add(additionalInfo);
			}
		return res;
	}

	private HashMap<Tag, List<String>> getSnippetMentions(
			List<List<Pair<ScoredAnnotation, HashMap<String, Double>>>> snippetAnnotations,
			List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBolds) {
		HashMap<Tag, List<String>> res = new HashMap<>();
		for (int rank=0; rank<snippetAnnotations.size(); rank ++){
			for (Pair<ScoredAnnotation, HashMap<String, Double>> annAndInfo : snippetAnnotations.get(rank)){
				ScoredAnnotation ann = annAndInfo.first;
				String mentionStr =  snippetsToBolds.get(rank).first.substring(ann.getPosition(), ann.getPosition()+ann.getLength());
				Tag t = new Tag(ann.getConcept());
				if (!res.containsKey(t))
					res.put(t, new Vector<String>());
				res.get(t).add(mentionStr);
			}
		}
		return res;
	}

	private HashMap<Tag, List<Integer>> getSnippetAnnotationRanks(
			List<List<Pair<ScoredAnnotation, HashMap<String, Double>>>> snippetAnnotations) {
		HashMap<Tag, List<Integer>> res = new HashMap<>();
		for (int rank=0; rank<snippetAnnotations.size(); rank ++){
			HashSet<Tag> alreadyInserted = new HashSet<>();
			for (Pair<ScoredAnnotation, HashMap<String, Double>> annAndInfo : snippetAnnotations.get(rank)){
				ScoredAnnotation ann = annAndInfo.first;
				Tag t = new Tag(ann.getConcept());
				if (alreadyInserted.contains(t))
					continue;
				alreadyInserted.add(t);
				if (!res.containsKey(t))
					res.put(t, new Vector<Integer>());
				res.get(t).add(rank);
			}
		}
		return res;
	}

	private void annotateSnippets(
			List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBolds,
			List<List<Pair<ScoredAnnotation, HashMap<String, Double>>>> snippetAnnotations,
			HashMap<Tag, List<String>> tagToBolds, SmaphDebugger debugger, String query) throws IOException {

		for (Pair<String, Vector<Pair<Integer, Integer>>> snippetAndBolds : snippetsToBolds.subList(0, Math.min(snippetsToBolds.size(),topKAnnotateSnippet))) {
			List<Pair<ScoredAnnotation, HashMap<String, Double>>> resI = new Vector<>();
			snippetAnnotations.add(resI);
			String snippet = snippetAndBolds.first;
			Vector<Pair<Integer, Integer>> bolds = snippetAndBolds.second;
			HashSet<Mention> boldMentions = new HashSet<>();
			for (Pair<Integer, Integer> bold : bolds)
				boldMentions.add(new Mention(bold.first, bold.second));

			HashSet<ScoredAnnotation> annotations = snippetAnnotator
					.solveSa2W(snippet);
			HashMap<Mention, HashMap<String, Double>> addInfo = snippetAnnotator
					.getLastQueryAdditionalInfo();

			// Prefetch wids (this is needed to resolve redirects).
			List<Integer> widsToPrefetch = new Vector<Integer>();
			for (ScoredAnnotation a : annotations)
				widsToPrefetch.add(a.getConcept());
			try {
				wikiApi.prefetchWids(widsToPrefetch);
			} catch (Exception e) {
				throw new RuntimeException(e);
			}

			//De-reference annotations
			HashSet<ScoredAnnotation> resolvedAnns = new HashSet<ScoredAnnotation>();
			for (ScoredAnnotation a : annotations) {
				int wid = wikiApi.dereference(a.getConcept());
				if (wid > 0)
					resolvedAnns.add(new ScoredAnnotation(a.getPosition(), a
							.getLength(), wid, a.getScore()));
			}

			HashSet<Annotation> filtered = new HashSet<>();
			
			for (ScoredAnnotation a : resolvedAnns)
				for (Mention m : boldMentions)
					if (a.overlaps(m)) {
						filtered.add(a);
						resI.add(new Pair<>(a, addInfo.get(new Mention(a.getPosition(), a.getLength()))));
						Tag t = new Tag(a.getConcept());
						if (!tagToBolds.containsKey(t))
							tagToBolds.put(t, new Vector<String>());
						String bold = snippet.substring(m.getPosition(), m.getPosition()+m.getLength());
						tagToBolds.get(t).add(bold);
						break;
					}
			if (debugger != null){
				debugger.addAnnotatedSnippetS3(query, snippet, filtered, boldMentions);
			}
		}
		return;
	}

	/**
	 * Given a query and its gold standard, generate
	 * 
	 * @param query
	 *            a query.
	 * @param goldStandard
	 *            the entities associated to the query.
	 * @param efCandidates 
	 * @param arCandidates 
	 * @param collCandidates 
	 * @param greedyStep 
	 * @param greedyCandidates 
	 * @param greedyVectorsToF1Incr 
	 * @param keepNEOnly
	 *            whether to limit the output to named entities, as defined by
	 *            ERDDatasetFilter.EntityIsNE.
	 * @param posEFVectors
	 *            where to store the positive-example (true positives) feature
	 *            vectors.
	 * @param negEFVectors
	 *            where to store the negative-example (false positives) feature
	 *            vectors.
	 * @param wikiToFreeb
	 *            a wikipedia to freebase-id mapping.
	 * @throws Exception
	 *             if something went wrong while annotating the query.
	 */
	public void generateExamples(
			String query,
			HashSet<Tag> goldStandard,
			HashSet<Annotation> goldStandardAnn,
			List<Pair<FeaturePack<Tag>, Boolean>> efVectorsToPresence,
			List<Tag> efCandidates,
			List<Pair<FeaturePack<Annotation>, Boolean>> arVectorsToPresence,
			List<Annotation> arCandidates,
			List<Pair<FeaturePack<HashSet<Annotation>>, Double>> collVectorsToF1,
			List<HashSet<Annotation>> collCandidates,
			HashSet<Annotation> greedyPartialSolution,
			int greedyStep, List<Pair<FeaturePack<Annotation>, Double>> greedyVectorsToF1Incr,
			List<Annotation> greedyCandidates,
			boolean keepNEOnly,
	        SmaphDebugger debugger) throws Exception {

		QueryInformation qi = getQueryInformation(query, debugger);

		// Generate examples for entityFilter
		if (efVectorsToPresence != null)
			for (Tag tag : qi.allCandidates()) {
				if (keepNEOnly
						&& !ERDDatasetFilter.entityIsNE(wikiApi, wikiToFreeb, tag.getConcept()))
					continue;
				FeaturePack<Tag> features  = new EntityFeaturePack(tag, query, qi, wikiApi, wikiToFreeb);
				efVectorsToPresence.add(new Pair<FeaturePack<Tag>, Boolean>(features, goldStandard.contains(tag)));
				if (efCandidates != null)
					efCandidates.add(tag);

				LOG.debug("{} in query [{}] is a {} example.",
						tag.getConcept(), query,
						goldStandard.contains(tag) ? "positive" : "negative");
			}

		// Generate examples for annotation regressor
		if (arVectorsToPresence != null) {
			List<Triple<Annotation, AnnotationFeaturePack, Boolean>> annotationsAndFtrAndPresences = getAdvancedARToFtrsAndPresence(
					query, qi, goldStandardAnn, new StrongAnnotationMatch(wikiApi));
			for (Triple<Annotation, AnnotationFeaturePack, Boolean> annotationsAndFtrAndPresence : annotationsAndFtrAndPresences) {
				AnnotationFeaturePack features = annotationsAndFtrAndPresence.getMiddle();
				Annotation ann = annotationsAndFtrAndPresence.getLeft();
				arVectorsToPresence.add(new Pair<FeaturePack<Annotation>, Boolean>(features, annotationsAndFtrAndPresence
						.getRight()));
				if (arCandidates != null)
					arCandidates.add(ann);
				LOG.info("[{}]->{} in query [{}] is a {} example.",
						query.substring(ann.getPosition(), ann.getPosition() + ann.getLength()), ann.getConcept(), query,
						annotationsAndFtrAndPresence.getRight() ? "positive" : "negative");

			}
		}

		// Generate examples for collective annotation
		if (collVectorsToF1 != null) {
			Set<Tag> acceptedEntities = null;
			if (keepNEOnly) {
				acceptedEntities = new HashSet<Tag>();
				for (Tag entity : qi.allCandidates())
					if (ERDDatasetFilter.entityIsNE(wikiApi, wikiToFreeb, entity.getConcept()))
						acceptedEntities.add(entity);

			} else {
				acceptedEntities = qi.allCandidates();
			}
			List<Triple<HashSet<Annotation>, BindingFeaturePack, Double>> bindingsToFtrAndF1 = getLBBindingToFtrsAndF1(
					query, qi, goldStandardAnn, new StrongAnnotationMatch(
							wikiApi), acceptedEntities, debugger);
			for (Triple<HashSet<Annotation>, BindingFeaturePack, Double> bindingAndFtrsAndF1 : bindingsToFtrAndF1) {
				BindingFeaturePack features = bindingAndFtrsAndF1.getMiddle();
				double f1 = bindingAndFtrsAndF1.getRight();
				HashSet<Annotation> binding = bindingAndFtrsAndF1.getLeft();
				collVectorsToF1.add(new Pair<FeaturePack<HashSet<Annotation>>, Double>(features, f1));

				if (collCandidates != null)
					collCandidates.add(binding);
			}
		}

		// Generate examples for greedy regressor
		if (greedyVectorsToF1Incr != null && greedyPartialSolution.size() == greedyStep) {
			List<Triple<Annotation, GreedyFeaturePack, Double>> annotationsAndFtrAndIncrements = getGreedyAnnotationToFtrsAndIncrement(
					query, qi, goldStandardAnn, greedyPartialSolution, new StrongAnnotationMatch(wikiApi));
			for (Triple<Annotation, GreedyFeaturePack, Double> annotationsAndFtrAndIncrement : annotationsAndFtrAndIncrements) {
				GreedyFeaturePack features = annotationsAndFtrAndIncrement.getMiddle();
				Annotation ann = annotationsAndFtrAndIncrement.getLeft();
				greedyVectorsToF1Incr.add(new Pair<FeaturePack<Annotation>, Double>(features, annotationsAndFtrAndIncrement
						.getRight()));
				if (greedyCandidates != null)
					greedyCandidates.add(ann);
				String psStr = greedyPartialSolution
				        .stream().map(a -> String.format("%s -> %d",
				                query.substring(a.getPosition(), a.getPosition() + a.getLength()), a.getConcept()))
				        .collect(Collectors.joining(", "));
				LOG.info("[{}]->{} in query [{}] with partial solution [{}] changes F1 by {}.",
						query.substring(ann.getPosition(), ann.getPosition() + ann.getLength()), ann.getConcept(), query,
						psStr, annotationsAndFtrAndIncrement.getRight());
			}
		}
	}

	private List<Triple<Annotation, GreedyFeaturePack, Double>> getGreedyAnnotationToFtrsAndIncrement(String query,
	        QueryInformation qi, HashSet<Annotation> goldStandardAnn, HashSet<Annotation> greedyPartialSolution,
	        StrongAnnotationMatch annotationMatch) {
		List<Annotation> candidates = AdvancedIndividualLinkback.getAnnotations(query, qi.allCandidates(), anchorMaxED, e2a)
		        .stream().filter(a -> !greedyPartialSolution.stream().anyMatch(aPS -> aPS.overlaps(a)))
		        .collect(Collectors.toList());

		double f1Before = new Metrics<Annotation>().getSingleF1(goldStandardAnn, greedyPartialSolution, annotationMatch);
		
		List<Triple<Annotation, GreedyFeaturePack, Double>> annAndFtrsAndIncrement = new Vector<>();
		for (Annotation a : candidates) {
			if (e2a.containsId(a.getConcept())) {
				HashSet<Annotation> stepCandidateSolution = new HashSet<Annotation>(greedyPartialSolution);
				stepCandidateSolution.add(a);
				double f1After= new Metrics<Annotation>().getSingleF1(goldStandardAnn, stepCandidateSolution, annotationMatch);
				
				GreedyFeaturePack features = new GreedyFeaturePack(a, query, qi, greedyPartialSolution, wikiApi, wikiToFreeb, e2a);
				annAndFtrsAndIncrement.add(new ImmutableTriple<Annotation, GreedyFeaturePack, Double>(a, features, f1After - f1Before));
			} else
				LOG.warn("No anchors found for id={}", a.getConcept());
		}

		return annAndFtrsAndIncrement;
	}

	private List<Triple<Annotation, AnnotationFeaturePack, Boolean>> getAdvancedARToFtrsAndPresence(
			String query, QueryInformation qi,
			HashSet<Annotation> goldStandardAnn,
			MatchRelation<Annotation> annotationMatch) {

		List<Triple<Annotation, AnnotationFeaturePack, Boolean>> annAndFtrsAndPresence = new Vector<>();
		for (Annotation a : AdvancedIndividualLinkback.getAnnotations(query,
				qi.allCandidates(), anchorMaxED, e2a)) {
			boolean inGold = false;
			for (Annotation goldAnn : goldStandardAnn)
				if (annotationMatch.match(goldAnn, a)) {
					inGold = true;
					break;
				}
			if (e2a.containsId(a.getConcept())) {
				AnnotationFeaturePack features = new AnnotationFeaturePack(a, query, qi, wikiApi, wikiToFreeb, e2a);
				annAndFtrsAndPresence.add(new ImmutableTriple<Annotation, AnnotationFeaturePack, Boolean>(a, features, inGold));
			} else
				LOG.warn("No anchors found for id={}", a.getConcept());
		}

		return annAndFtrsAndPresence;
	}

	private List<Triple<HashSet<Annotation>, BindingFeaturePack, Double>> getLBBindingToFtrsAndF1(String query,
	        QueryInformation qi, HashSet<Annotation> goldStandardAnn, MatchRelation<Annotation> match,
	        Set<Tag> acceptedEntities, SmaphDebugger debugger) {

		Collection<Pair<HashSet<Annotation>, BindingFeaturePack>> bindingAndFeaturePacks = CollectiveLinkBack
		        .getBindingFeaturePacks(query, acceptedEntities, qi, bg, wikiApi, wikiToFreeb, e2a, debugger);

		List<Triple<HashSet<Annotation>, BindingFeaturePack, Double>> res = new Vector<>();
		for (Pair<HashSet<Annotation>, BindingFeaturePack> bindingAndFeaturePack : bindingAndFeaturePacks) {
			HashSet<Annotation> binding = bindingAndFeaturePack.first;
			BindingFeaturePack bindingFeatures = bindingAndFeaturePack.second;

			Metrics<Annotation> m = new Metrics<>();
			float f1 = m.getSingleF1(goldStandardAnn, binding, match);

			res.add(new ImmutableTriple<HashSet<Annotation>, BindingFeaturePack, Double>(
					binding, bindingFeatures, (double) f1));
		}
		return res;
	}

	public HashSet<ScoredAnnotation> getLBUpperBound1(String query,
			HashSet<Annotation> goldStandardAnn, SmaphDebugger debugger) throws Exception {
		QueryInformation qi = getQueryInformation(query, debugger);
		HashSet<ScoredAnnotation> idealAnnotations = new HashSet<>();
		StrongTagMatch stm = new StrongTagMatch(wikiApi);
		for (Annotation ann : goldStandardAnn) {
			Set<Tag> retrievedEntities = qi.allCandidates();
			for (Tag entity : retrievedEntities) {
				if (stm.match(new Tag(ann.getConcept()), entity)) {
					idealAnnotations.add(new ScoredAnnotation(ann.getPosition(), ann.getLength(), ann.getConcept(), 1f));
					break;
				}
			}
		}
		return idealAnnotations;
	}

	public Pair<HashSet<ScoredAnnotation>, Integer> getLBUpperBound2(String query,
			HashSet<Annotation> goldStandardAnn, BindingGenerator bg, SmaphDebugger debugger)
					throws Exception {
		QueryInformation qi = getQueryInformation(query, debugger);
		List<Triple<HashSet<Annotation>, BindingFeaturePack, Double>> bindingToFtrsAndF1 = getLBBindingToFtrsAndF1(
				query, qi, goldStandardAnn, new StrongAnnotationMatch(
						wikiApi), qi.allCandidates(), debugger);
		HashSet<Annotation> bestBinding = null;
		double bestF1 = Double.NEGATIVE_INFINITY;
		for (Triple<HashSet<Annotation>, BindingFeaturePack, Double> bindingAndFtrsAndF1 : bindingToFtrsAndF1) {
			double f1 = bindingAndFtrsAndF1.getRight();
			if (f1 > bestF1) {
				bestBinding = bindingAndFtrsAndF1.getLeft();
				bestF1 = f1;
			}
		}

		HashSet<ScoredAnnotation> bestBindingScored = new HashSet<>();
		for (Annotation a: bestBinding)
			bestBindingScored.add(new ScoredAnnotation(a.getPosition(), a.getLength(), a.getConcept(), 1.0f));
		return new Pair<HashSet<ScoredAnnotation>, Integer>(bestBindingScored,
				bindingToFtrsAndF1.size());
	}

	public Pair<HashSet<ScoredAnnotation>, Integer> getLBUpperBound3(String query, HashSet<Annotation> goldStandardAnn,
	        double maxAnchorEd, SmaphDebugger debugger) throws Exception {
		QueryInformation qi = getQueryInformation(query, debugger);
		List<Annotation> candidateAnnotations = AdvancedIndividualLinkback.getAnnotations(query, qi.allCandidates(),
		        maxAnchorEd, e2a);
		StrongAnnotationMatch sam = new StrongAnnotationMatch(wikiApi);

		HashSet<ScoredAnnotation> bestBindingScored = new HashSet<>();
		for (Annotation a : candidateAnnotations) {
			if (goldStandardAnn.stream().anyMatch(p -> sam.match(a, p)))
				bestBindingScored.add(new ScoredAnnotation(a.getPosition(), a.getLength(), a.getConcept(), 1.0f));
		}
		return new Pair<HashSet<ScoredAnnotation>, Integer>(bestBindingScored, candidateAnnotations.size());
	}

	public HashSet<ScoredAnnotation> getLBUpperBound4(String query, HashSet<Annotation> gold, SmaphDebugger debugger) throws Exception {
		List<Pair<Integer, Integer>> segments = SmaphUtils.findSegments(query);
		Set<Tag> entities = getQueryInformation(query, debugger).allCandidates();
		HashSet<ScoredAnnotation> solution = new HashSet<>();
		StrongAnnotationMatch sam = new StrongAnnotationMatch(wikiApi);

		for (Pair<Integer, Integer> segment : segments)
			for (Tag entity : entities) {
				Annotation a = new Annotation(segment.first, segment.second - segment.first, entity.getConcept());
				if (gold.stream().anyMatch(ga -> sam.match(a, ga)))
					solution.add(new ScoredAnnotation(segment.first, segment.second - segment.first, entity.getConcept(), 1.0f));
			}
		return solution;
	}

	public HashSet<ScoredAnnotation> getUpperBoundMentions(String query,
			HashSet<Annotation> goldStandardAnn, BindingGenerator bg, SmaphDebugger debugger)
					throws Exception {
		QueryInformation qi = getQueryInformation(query, debugger);
		List<Triple<HashSet<Annotation>, BindingFeaturePack, Double>> bindingToFtrsAndF1 = getLBBindingToFtrsAndF1(
				query, qi, goldStandardAnn,
				new StrongMentionAnnotationMatch(), qi.allCandidates(), debugger);
		HashSet<Annotation> bestBinding = null;
		double bestF1 = Double.NEGATIVE_INFINITY;
		for (Triple<HashSet<Annotation>, BindingFeaturePack, Double> bindingAndFtrsAndF1 : bindingToFtrsAndF1) {
			double f1 = bindingAndFtrsAndF1.getRight();
			if (f1 > bestF1) {
				bestBinding = bindingAndFtrsAndF1.getLeft();
				bestF1 = f1;
			}
		}

		if (bestBinding == null) return null;

		HashSet<ScoredAnnotation> bestBindingScored= new HashSet<>();
		for (Annotation a : bestBinding)
			bestBindingScored.add(new ScoredAnnotation(a.getPosition(), a.getLength(), a.getConcept(), 1.0f));
		return bestBindingScored;
	}

	public SmaphAnnotator appendName(String appendName) {
		this.appendName = appendName;
		return this;
	}

}
