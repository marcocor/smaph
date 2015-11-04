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

import it.unipi.di.acube.BingInterface;
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
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.models.entityfilters.EntityFilter;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.AnnotationRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.CollectiveLinkBack;
import it.unipi.di.acube.smaph.linkback.LinkBack;
import it.unipi.di.acube.smaph.linkback.AdvancedIndividualLinkback;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.BindingGenerator;
import it.unipi.di.acube.smaph.main.ERDDatasetFilter;
import it.unipi.di.acube.smaph.snippetannotationfilters.SnippetAnnotationFilter;
import it.unipi.di.acube.smaph.wikiAnchors.EntityToAnchors;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPathExpressionException;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

public class SmaphAnnotator implements Sa2WSystem {
	private static final String WIKI_URL_LEADING = "http://en.wikipedia.org/wiki/";
	private WikipediaApiInterface wikiApi;
	private BingInterface bingInterface = null;
	private WATAnnotator snippetAnnotator;
	private EntityFilter entityFilter;
	private FeatureNormalizer entityFilterNormalizer;
	private LinkBack linkBack;
	private boolean includeSourceNormalSearch;
	private boolean includeSourceSnippets;
	private int topKAnnotateSnippet;
	private boolean includeSourceWikiSearch;
	private int topKWikiSearch = 0;
	private SmaphAnnotatorDebugger debugger;
	private boolean predictNEonly;
	private String appendName = "";
	private SnippetAnnotationFilter snippetAnnotationFilter;
	private boolean tagTitles;

	private static Logger logger = LoggerFactory.getLogger(SmaphAnnotator.class);

	/**
	 * Constructs a SMAPH annotator.
	 * 
	 * @param auxDisambiguator
	 *            the disambiguator used in Source 1.
	 * @param boldFilter
	 *            the filter of the bolds used in Source 1.
	 * @param entityFilter
	 *            the entity filter used in the second stage.
	 * @param entityFilterNormalizer
	 *            the entity filter feature normalizer.
	 * @param includeSourceAnnotator
	 *            true iff Source 1 has to be enabled.
	 * @param includeSourceNormalSearch
	 *            true iff Source 2 has to be enabled.
	 * @param includeSourceWikiSearch
	 *            true iff Source 3 has to be enabled.
	 * @param wikiSearchPages
	 *            Source 3 results limit.
	 * @param includeRelatedSearch
	 *            true iff Source 4 has to be enabled.
	 * @param topKRelatedSearch
	 *            Source 4 results limit.
	 * @param wikiApi
	 *            an API to Wikipedia.
	 * @param bingKey
	 *            the key to the Bing API.
	 */
	public SmaphAnnotator(EntityFilter entityFilter, FeatureNormalizer entityFilterNormalizer,
			LinkBack linkBack,
			boolean includeSourceNormalSearch,
			boolean includeSourceWikiSearch, int wikiSearchPages,
			boolean includeSourceSnippets, int topKAnnotateSnippet, 
			boolean tagTitles, WATAnnotator snippetAnnotator,
			SnippetAnnotationFilter snippetAnnotationFilter,
			WikipediaApiInterface wikiApi, String bingKey) {
		this.entityFilter = entityFilter;
		this.entityFilterNormalizer = entityFilterNormalizer;
		this.linkBack = linkBack;
		this.wikiApi = wikiApi;
		this.includeSourceNormalSearch = includeSourceNormalSearch;
		this.includeSourceWikiSearch = includeSourceWikiSearch;
		this.topKWikiSearch = wikiSearchPages;
		this.bingInterface =  new BingInterface(bingKey);
		this.includeSourceSnippets = includeSourceSnippets;
		this.snippetAnnotationFilter = snippetAnnotationFilter;
		this.topKAnnotateSnippet = topKAnnotateSnippet;
		this.tagTitles = tagTitles;
		this.snippetAnnotator = snippetAnnotator;
	}

	public void setPredictNEOnly(boolean predictNEonly) {
		this.predictNEonly = predictNEonly;
	}

	/**
	 * Set an optional debugger to gather data about the process of a query.
	 * 
	 * @param debugger
	 *            the debugger.
	 */
	public void setDebugger(SmaphAnnotatorDebugger debugger) {
		this.debugger = debugger;
		this.linkBack.setDebugger(debugger);
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
		return 0;
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
	public HashSet<ScoredAnnotation> solveSa2W(String query)
			throws AnnotationException {
		if (debugger != null)
			debugger.addProcessedQuery(query);

		HashSet<ScoredAnnotation> annotations = new HashSet<>();
		try {
			HashSet<Tag> acceptedEntities = new HashSet<>();

			QueryInformation qi = getQueryInformation(query);

			for (Tag candidate : qi.allCandidates()){
				if (predictNEonly
						&& !ERDDatasetFilter.EntityIsNE(wikiApi, candidate.getConcept()))
					continue;
				boolean accept = false;
				for (EntityFeaturePack fp : EntityFeaturePack.getAllFeaturePacks(candidate, query, qi, wikiApi))
					accept |= entityFilter.filterEntity(fp, entityFilterNormalizer);
				if (accept){
					acceptedEntities.add(candidate);
					if (debugger != null)
						debugger.addResult(query, candidate.getConcept());

				}
			}
			/** Link entities back to query mentions */
			annotations = linkBack.linkBack(query, acceptedEntities, qi);

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		SmaphAnnotatorDebugger.out.printf("*** FINISHED PROCESSING QUERY [%s] ***%n", query);

		return annotations;

	}

	/**
	 * Adjust the title of retrieved Wikipedia pages, e.g. removing final
	 * parenthetical.
	 * 
	 * @param rankToIdWS
	 *            a mapping from a rank (position in the search engine result)
	 *            to the Wikipedia ID of the page in that rank.
	 * @return a mapping from adjusted titles to a pair <wid, rank>
	 */
	private HashMap<String, Pair<Integer, Integer>> adjustTitles(
			HashMap<Integer, Integer> rankToIdWS) {
		HashMap<String, Pair<Integer, Integer>> res = new HashMap<>();
		for (int rank : rankToIdWS.keySet()) {
			int wid = rankToIdWS.get(rank);
			try {
				String title = wikiApi.getTitlebyId(wid);
				if (title != null) {
					SmaphUtils.removeTrailingParenthetical(title);
					res.put(title, new Pair<Integer, Integer>(wid, rank));
				}
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException();
			}
		}
		return res;
	}

	/**
	 * Turns a Wikipedia URL to the title of the Wikipedia page.
	 * 
	 * @param encodedWikiUrl
	 * @return a Wikipedia title, or null if the url is not a Wikipedia page.
	 */
	private static String decodeWikiUrl(String encodedWikiUrl) {
		if (!encodedWikiUrl.matches("^" + WIKI_URL_LEADING + ".*")) {
			return null;
		}
		try {
			String title = URLDecoder.decode(
					encodedWikiUrl.substring(WIKI_URL_LEADING.length()),
					"utf-8");
			if (!SmaphUtils.acceptWikipediaTitle(title))
				return null;
			return title;

		} catch (IllegalArgumentException | UnsupportedEncodingException e) {
			return null;

		}

	}

	/**
	 * Issue a query to Bing and extract the result.
	 * 
	 * @param query
	 *            the query to be issued to Bing
	 * @param boldsAndRanks
	 *            storage for the bolds (a pair &lt;bold, rank&gt; means bold
	 *            appeared in the snippets of the result in position rank)
	 * @param urls
	 *            storage for the urls found by Bing.
	 * @param relatedSearch
	 *            storage for the "related search" suggestions.
	 * @param snippetsToBolds
	 *            storage for the list of pairs &lt;snippets, the bolds found in
	 *            that snippet&gt;
	 * @return a triple &lt;results, webTotal, bingReply&gt; where results is
	 *         the number of results returned by Bing, webTotal is the number of
	 *         pages found by Bing, and bingReply is the raw Bing reply.
	 * @param topk
	 *            limit to top-k results.
	 * @param wikisearch
	 *            whether to append the word "wikipedia" to the query or not.
	 * @throws Exception
	 *             if something went wrong while querying Bing.
	 */

	public Triple<Integer, Double, JSONObject> takeBingData(String query,
			List<Pair<String, Integer>> boldsAndRanks, List<String> urls,
			List<String> relatedSearch,
			List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBolds,
			int topk, boolean wikisearch) throws Exception {
		if (!boldsAndRanks.isEmpty())
			throw new RuntimeException("boldsAndRanks must be empty");
		if (!urls.isEmpty())
			throw new RuntimeException("urls must be empty");
		if (wikisearch)
			query += " wikipedia";
		JSONObject bingReply = bingInterface.queryBing(query);
		JSONObject data = (JSONObject) bingReply.get("d");
		JSONObject results = (JSONObject) ((JSONArray) data.get("results"))
				.get(0);
		JSONArray webResults = (JSONArray) results.get("Web");
		double webTotal = new Double((String) results.get("WebTotal"));

		getBoldsAndUrls(webResults, topk, boldsAndRanks, urls, snippetsToBolds, tagTitles);

		if (relatedSearch != null) {
			JSONArray relatedSearchResults = (JSONArray) results
					.get("RelatedSearch");
			for (int i = 0; i < relatedSearchResults.length(); i++) {
				JSONObject resI = (JSONObject) relatedSearchResults.get(i);
				String rsI = (String) resI.get("Title");
				relatedSearch.add(rsI);
			}
		}

		return new ImmutableTriple<Integer, Double, JSONObject>(
				webResults.length(), webTotal, bingReply);
	}
	/**
	 * From the bing results extract the bolds and the urls.
	 * 
	 * @param webResults
	 *            the web results returned by Bing.
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
	 *             if the json returned by Bing could not be read.
	 */
	public static void getBoldsAndUrls(JSONArray webResults, double topk,
			List<Pair<String, Integer>> boldsAndRanks, List<String> urls,
			List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBolds, boolean tagTitles)
					throws JSONException {
		for (int i = 0; i < Math.min(webResults.length(), topk); i++) {
			JSONObject resI = (JSONObject) webResults.get(i);
			String titleI = (String) resI.get("Title");
			String descI = (String) resI.get("Description");
			String url = (String) resI.get("Url");
			urls.add(url);
			//getBolds(titleI, i, snippetsToBolds, boldsAndRanks);
			getBolds((tagTitles ? titleI + " " : "") + descI, i, snippetsToBolds, boldsAndRanks);
		}
	}

	public static void getBolds(String field,int rankI, List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBolds, List<Pair<String, Integer>> boldsAndRanks){
		String snippet = "";
		byte[] startByte = new byte[] { (byte) 0xee, (byte) 0x80,
				(byte) 0x80 };
		byte[] stopByte = new byte[] { (byte) 0xee, (byte) 0x80,
				(byte) 0x81 };
		String start = new String(startByte);
		String stop = new String(stopByte);
		field = field.replaceAll(stop + "." + start, " ");
		int startIdx = field.indexOf(start);
		int stopIdx = field.indexOf(stop, startIdx);
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
			startIdx = field.indexOf(start, startIdx + 1);
			stopIdx = field.indexOf(stop, startIdx + 1);
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
	 * @return a mapping from position to Wikipedia page IDs.
	 */
	private HashMap<Integer, Integer> urlsToRankID(List<String> urls) {
		HashMap<Integer, Integer> result = new HashMap<>();
		HashMap<Integer, String> rankToTitle = new HashMap<>();
		for (int i = 0; i < urls.size(); i++) {
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
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
			if (wid > 0) {
				result.put(rank, wid);
				SmaphAnnotatorDebugger.out.printf(
						"Found Wikipedia url:%s rank:%d id:%d%n",
						urls.get(rank), rank, wid);
			} else
				SmaphAnnotatorDebugger.out.printf(
						"Discarding Wikipedia url:%s rank:%d id:%d%n",
						urls.get(rank), rank, wid);
		}
		return result;
	}


	public QueryInformation getQueryInformation(String query) throws Exception {

		/** Search the query on bing */
		List<Pair<String, Integer>> bingBoldsAndRankNS = null;
		List<String> urls = null;
		List<String> relatedSearchRes = null;
		Triple<Integer, Double, JSONObject> resCountAndWebTotalNS = null;
		int resultsCount = -1;
		double webTotalNS = Double.NaN;
		HashMap<Integer, Integer> rankToIdNS = null;
		HashMap<Integer, HashSet<String>> rankToBoldsNS = null;
		List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBolds = null;
		HashMap<Tag, String[]> entityToBoldsS2S3 = new HashMap<>();
		List<String> allBoldsNS = null;
		Set<Tag> candidatesNS = null;
		if (includeSourceWikiSearch || includeSourceNormalSearch || includeSourceSnippets) {
			bingBoldsAndRankNS = new Vector<>();
			urls = new Vector<>();
			relatedSearchRes = new Vector<>();
			snippetsToBolds = new Vector<>();
			candidatesNS = new HashSet<>();
			resCountAndWebTotalNS = takeBingData(query, bingBoldsAndRankNS,
					urls, relatedSearchRes, snippetsToBolds, Integer.MAX_VALUE,
					false);
			resultsCount = resCountAndWebTotalNS.getLeft();
			webTotalNS = resCountAndWebTotalNS.getMiddle();
			rankToIdNS = urlsToRankID(urls);
			rankToBoldsNS = new HashMap<>();
			allBoldsNS = SmaphUtils.boldPairsToListLC(bingBoldsAndRankNS);
			SmaphUtils.mapRankToBoldsLC(bingBoldsAndRankNS, rankToBoldsNS, null);
			for (int rank : rankToIdNS.keySet()) {
				String[] bolds = rankToBoldsNS.containsKey(rank) ? rankToBoldsNS
						.get(rank).toArray(new String[] {}) : new String[] {};
				Tag candidate = new Tag(rankToIdNS.get(rank));
				entityToBoldsS2S3.put(candidate, bolds);
				candidatesNS.add(candidate);		
			}

			if (debugger != null) {
				debugger.addBoldPositionEditDistance(query, bingBoldsAndRankNS);
				debugger.addSnippets(query, snippetsToBolds);
				debugger.addSource2SearchResult(query, rankToIdNS, urls);
				debugger.addBingResponseNormalSearch(query,
						resCountAndWebTotalNS.getRight());
			}
		}

		/** Do the WikipediaSearch on bing. */
		List<String> wikiSearchUrls = new Vector<>();
		List<Pair<String, Integer>> bingBoldsAndRankWS = new Vector<>();
		HashMap<String, Pair<Integer, Integer>> annTitlesToIdAndRankWS = null;
		Triple<Integer, Double, JSONObject> resCountAndWebTotalWS = null;
		HashMap<Integer, HashSet<String>> rankToBoldsWS = null;
		Set<Tag> candidatesWS = null;
		double webTotalWS = Double.NaN;
		if (includeSourceWikiSearch | includeSourceNormalSearch) {
			resCountAndWebTotalWS = takeBingData(query, bingBoldsAndRankWS,
					wikiSearchUrls, null, null, topKWikiSearch, true);
			webTotalWS = resCountAndWebTotalWS.getMiddle();
			HashMap<Integer, Integer> rankToIdWikiSearch = urlsToRankID(wikiSearchUrls);
			candidatesWS = new HashSet<>();
			for (int wid : rankToIdWikiSearch.values())
				candidatesWS.add(new Tag(wid));
			rankToBoldsWS = new HashMap<>();
			SmaphUtils.mapRankToBoldsLC(bingBoldsAndRankWS, rankToBoldsWS, null);
			if (debugger != null) {
				debugger.addSource3SearchResult(query, rankToIdWikiSearch,
						wikiSearchUrls);
				debugger.addBingResponseWikiSearch(query,
						resCountAndWebTotalWS.getRight());

			}
			annTitlesToIdAndRankWS = adjustTitles(rankToIdWikiSearch);
		}

		/** Annotate snippets */
		HashMap<Tag,List<Integer>> tagToRanks = null;
		HashMap<Tag,List<String>> tagToMentions = null;
		HashMap<Tag,List<String>> tagToBolds = null;
		HashMap<Tag,List<HashMap<String,Double>>> tagToAdditionalInfos = null;
		HashSet<Tag> filteredAnnotationsSA = null;
		if (includeSourceSnippets){
			List<List<Pair<ScoredAnnotation, HashMap<String, Double>>>> snippetAnnotations = new Vector<>();
			tagToBolds = new HashMap<>();
			annotateSnippets(snippetsToBolds, snippetAnnotations, tagToBolds);
			tagToRanks = getSnippetAnnotationRanks(snippetAnnotations);
			tagToMentions = getSnippetMentions(snippetAnnotations, snippetsToBolds);
			tagToAdditionalInfos = getSnippetAdditionalInfo(snippetAnnotations);
			filteredAnnotationsSA = snippetAnnotationFilter.filterAnnotations(tagToRanks, resultsCount);
		}


		QueryInformation qi = new QueryInformation();
		qi.includeSourceNormalSearch = includeSourceNormalSearch;
		qi.includeSourceWikiSearch = includeSourceWikiSearch;
		qi.includeSourceSnippets = includeSourceSnippets;
		qi.idToRankNS = SmaphUtils.inverseMap(rankToIdNS);
		qi.tagToBoldsS6 = tagToBolds;
		qi.entityToBoldS2S3 = entityToBoldsS2S3;
		qi.webTotalNS = webTotalNS;
		qi.allBoldsNS = allBoldsNS;
		qi.bingBoldsAndRankNS = bingBoldsAndRankNS;
		qi.annTitlesToIdAndRankWS = annTitlesToIdAndRankWS;
		qi.webTotalWS = webTotalWS;
		qi.bingBoldsAndRankWS = bingBoldsAndRankWS;
		qi.resultsCount = resultsCount;
		qi.tagToMentionsSA = tagToMentions;
		qi.tagToBoldsSA = tagToBolds;
		qi.tagToRanksSA = tagToRanks;
		qi.tagToAdditionalInfosSA = tagToAdditionalInfos;
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
			HashMap<Tag, List<String>> tagToBolds) throws IOException {

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

			for (ScoredAnnotation a : resolvedAnns)
				for (Mention m : boldMentions)
					//if (a.getPosition() <= m.getPosition() && m.getPosition()+m.getLength() <= a.getPosition() + a.getLength()) {
					if (a.overlaps(m)) {
						resI.add(new Pair<>(a, addInfo.get(new Mention(a.getPosition(), a.getLength()))));
						Tag t = new Tag(a.getConcept());
						if (!tagToBolds.containsKey(t))
							tagToBolds.put(t, new Vector<String>());
						String bold = snippet.substring(m.getPosition(), m.getPosition()+m.getLength());
						tagToBolds.get(t).add(bold);
						break;
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
	 * @param EFCandidates 
	 * @param BRCandidates 
	 * @param ARCandidates 
	 * @param posEFVectors
	 *            where to store the positive-example (true positives) feature
	 *            vectors.
	 * @param negEFVectors
	 *            where to store the negative-example (false positives) feature
	 *            vectors.
	 * @param keepNEOnly
	 *            whether to limit the output to named entities, as defined by
	 *            ERDDatasetFilter.EntityIsNE.
	 * @param wikiToFreeb
	 *            a wikipedia to freebase-id mapping.
	 * @throws Exception
	 *             if something went wrong while annotating the query.
	 */
	public void generateExamples(
			String query,
			HashSet<Tag> goldStandard,
			HashSet<Annotation> goldStandardAnn,
			List<Pair<FeaturePack<Tag>, Boolean>> EFVectorsToPresence,
			List<Tag> EFCandidates,
			List<Pair<FeaturePack<HashSet<Annotation>>, Double>> lbVectorsToF1,
			List<HashSet<Annotation>> BRCandidates,
			List<Pair<FeaturePack<Annotation>, Boolean>> annVectorsToPresence,
			List<Pair<FeaturePack<Annotation>, Boolean>> advancedAnnVectorsToPresence,
			List<Annotation> ARCandidates, boolean keepNEOnly,
			BindingGenerator bg,
			AnnotationRegressor arLevel1,
			FeatureNormalizer arNormLevel1,
			WikipediaToFreebase wikiToFreeb, double anchorMaxED)
					throws Exception {

		QueryInformation qi = getQueryInformation(query);

		// Generate examples for entityFilter
		if (EFVectorsToPresence != null)
			for (Tag tag : qi.allCandidates()) {
				if (keepNEOnly
						&& !ERDDatasetFilter.EntityIsNE(wikiApi, tag.getConcept()))
					continue;
				for (FeaturePack<Tag> features : EntityFeaturePack.getAllFeaturePacks(tag, query, qi, wikiApi)){
					EFVectorsToPresence.add(new Pair<FeaturePack<Tag>, Boolean>(features, goldStandard.contains(tag)));
					if (EFCandidates != null)
						EFCandidates.add(tag);
				}
				System.out.printf("%d in query [%s] is a %s example.%n",
						tag.getConcept(), query,
						goldStandard.contains(tag) ? "positive"
								: "negative");
			}

		// Generate examples for linkBack
		if (lbVectorsToF1 != null) {
			Set<Tag> acceptedEntities = null;
			if (keepNEOnly) {
				acceptedEntities = new HashSet<Tag>();
				for (Tag entity : qi.allCandidates())
					if (ERDDatasetFilter.EntityIsNE(wikiApi, entity.getConcept()))
						acceptedEntities.add(entity);

			} else {
				acceptedEntities = qi.allCandidates();
			}
			List<Triple<HashSet<Annotation>, BindingFeaturePack, Double>> bindingsToFtrAndF1 = getLBBindingToFtrsAndF1(
					query, qi, bg, goldStandardAnn, new StrongAnnotationMatch(
							wikiApi), acceptedEntities);
			for (Triple<HashSet<Annotation>, BindingFeaturePack, Double> bindingAndFtrsAndF1 : bindingsToFtrAndF1) {
				BindingFeaturePack features = bindingAndFtrsAndF1.getMiddle();
				double f1 = bindingAndFtrsAndF1.getRight();
				HashSet<Annotation> binding = bindingAndFtrsAndF1.getLeft();
				lbVectorsToF1.add(new Pair<FeaturePack<HashSet<Annotation>>, Double>(features, f1));

				if (BRCandidates != null)
					BRCandidates.add(binding);

			}
		}

		// Generate examples for advanced annotation filter
		if (advancedAnnVectorsToPresence != null) {

			List<Triple<Annotation, AdvancedAnnotationFeaturePack, Boolean>> annotationsAndFtrAndPresences = getAdvancedARToFtrsAndPresence(
					query, qi, goldStandardAnn, new StrongAnnotationMatch(wikiApi), anchorMaxED);
			for (Triple<Annotation, AdvancedAnnotationFeaturePack, Boolean> annotationsAndFtrAndPresence : annotationsAndFtrAndPresences) {
				AdvancedAnnotationFeaturePack features = annotationsAndFtrAndPresence.getMiddle();
				Annotation ann = annotationsAndFtrAndPresence.getLeft();
				advancedAnnVectorsToPresence.add(new Pair<FeaturePack<Annotation>, Boolean>(features, annotationsAndFtrAndPresence
						.getRight()));
				if (ARCandidates != null)
					ARCandidates.add(ann);
				System.out.printf("[%s]->%d in query [%s] is a %s example.%n",
						query.substring(ann.getPosition(), ann.getPosition() + ann.getLength()), ann.getConcept(), query,
						annotationsAndFtrAndPresence.getRight() ? "positive" : "negative");

			}
		}
	}

	private List<Triple<Annotation, AdvancedAnnotationFeaturePack, Boolean>> getAdvancedARToFtrsAndPresence(
			String query, QueryInformation qi,
			HashSet<Annotation> goldStandardAnn,
			MatchRelation<Annotation> annotationMatch, double anchorMaxED) {

		List<Triple<Annotation, AdvancedAnnotationFeaturePack, Boolean>> annAndFtrsAndPresence = new Vector<>();
		for (Annotation a : AdvancedIndividualLinkback.getAnnotations(query,
				qi.allCandidates(), qi, anchorMaxED)) {
			boolean inGold = false;
			for (Annotation goldAnn : goldStandardAnn)
				if (annotationMatch.match(goldAnn, a)) {
					inGold = true;
					break;
				}
			if (EntityToAnchors.e2a().containsId(a.getConcept())) {
				AdvancedAnnotationFeaturePack features = new AdvancedAnnotationFeaturePack(a, query, qi, wikiApi);
				annAndFtrsAndPresence.add(new ImmutableTriple<Annotation, AdvancedAnnotationFeaturePack, Boolean>(a, features, inGold));
			} else
				logger.debug("No anchors found for id=" + a.getConcept());
		}

		return annAndFtrsAndPresence;
	}

	private List<Triple<HashSet<Annotation>, BindingFeaturePack, Double>> getLBBindingToFtrsAndF1(
			String query, QueryInformation qi, BindingGenerator bg,
			HashSet<Annotation> goldStandardAnn, MatchRelation<Annotation> match, Set<Tag> acceptedEntities) {

		Collection<Pair<HashSet<Annotation>, BindingFeaturePack>> bindingAndFeaturePacks = CollectiveLinkBack.getBindingFeaturePacks(query, acceptedEntities, qi, bg, wikiApi, debugger);

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
			HashSet<Annotation> goldStandardAnn) throws Exception {
		QueryInformation qi = getQueryInformation(query);
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
			HashSet<Annotation> goldStandardAnn, BindingGenerator bg)
					throws Exception {
		QueryInformation qi = getQueryInformation(query);
		List<Triple<HashSet<Annotation>, BindingFeaturePack, Double>> bindingToFtrsAndF1 = getLBBindingToFtrsAndF1(
				query, qi, bg, goldStandardAnn, new StrongAnnotationMatch(
						wikiApi), qi.allCandidates());
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

	public HashSet<ScoredAnnotation> getUpperBoundMentions(String query,
			HashSet<Annotation> goldStandardAnn, BindingGenerator bg)
					throws Exception {
		QueryInformation qi = getQueryInformation(query);
		List<Triple<HashSet<Annotation>, BindingFeaturePack, Double>> bindingToFtrsAndF1 = getLBBindingToFtrsAndF1(
				query, qi, bg, goldStandardAnn,
				new StrongMentionAnnotationMatch(), qi.allCandidates());
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

	public void appendName(String appendName) {
		this.appendName = appendName;
	}

}
