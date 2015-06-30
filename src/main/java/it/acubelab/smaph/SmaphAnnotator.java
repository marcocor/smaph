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

package it.acubelab.smaph;

import it.unipi.di.acube.BingInterface;
import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.Mention;
import it.acubelab.batframework.data.ScoredAnnotation;
import it.acubelab.batframework.data.ScoredTag;
import it.acubelab.batframework.data.Tag;
import it.acubelab.batframework.metrics.MatchRelation;
import it.acubelab.batframework.metrics.Metrics;
import it.acubelab.batframework.metrics.StrongAnnotationMatch;
import it.acubelab.batframework.metrics.StrongMentionAnnotationMatch;
import it.acubelab.batframework.metrics.StrongTagMatch;
import it.acubelab.batframework.problems.Sa2WSystem;
import it.acubelab.batframework.systemPlugins.WATAnnotator;
import it.acubelab.batframework.utils.AnnotationException;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.batframework.utils.ProblemReduction;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.boldfilters.BoldFilter;
import it.acubelab.smaph.boldfilters.FrequencyBoldFilter;
import it.acubelab.smaph.boldfilters.RankWeightBoldFilter;
import it.acubelab.smaph.entityfilters.EntityFilter;
import it.acubelab.smaph.learn.GenerateModel;
import it.acubelab.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
import it.acubelab.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.acubelab.smaph.learn.featurePacks.BindingFeaturePack;
import it.acubelab.smaph.learn.featurePacks.EntityFeaturePack;
import it.acubelab.smaph.learn.featurePacks.FeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;
import it.acubelab.smaph.linkback.LinkBack;
import it.acubelab.smaph.linkback.SvmAdvancedIndividualSingleLinkback;
import it.acubelab.smaph.linkback.SvmIndividualAnnotationLinkBack;
import it.acubelab.smaph.linkback.annotationRegressor.Regressor;
import it.acubelab.smaph.linkback.bindingGenerator.BindingGenerator;
import it.acubelab.smaph.main.ERDDatasetFilter;
import it.acubelab.smaph.snippetannotationfilters.SnippetAnnotationFilter;
import it.acubelab.smaph.wikiAnchors.EntityToAnchors;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
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
import org.tartarus.snowball.ext.EnglishStemmer;
import org.xml.sax.SAXException;

public class SmaphAnnotator implements Sa2WSystem {
	private static final String WIKI_URL_LEADING = "http://en.wikipedia.org/wiki/";
	public static final String WIKITITLE_ENDPAR_REGEX = "\\s*\\([^\\)]*\\)\\s*$";
	private WikipediaApiInterface wikiApi;
	private static WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");;
	private BingInterface bingInterface = null;
	
	private WATAnnotator boldDisambiguator;
	private WATAnnotator snippetAnnotator;
	private BoldFilter boldFilter;
	private EntityFilter entityFilter;
	private FeatureNormalizer entityFilterNormalizer;
	private LinkBack linkBack;
	private boolean includeSourceNormalSearch;
	private boolean includeSourceAnnotator;
	private boolean includeSourceSnippets;
	private int topKAnnotateSnippet;
	private boolean includeSourceWikiSearch;
	private int topKWikiSearch = 0;
	private boolean includeSourceRelatedSearch;
	private int topKRelatedSearch;
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
	public SmaphAnnotator(WATAnnotator auxDisambiguator, BoldFilter boldFilter,
			EntityFilter entityFilter,FeatureNormalizer entityFilterNormalizer, LinkBack linkBack,
			boolean includeSourceAnnotator, boolean includeSourceNormalSearch,
			boolean includeSourceWikiSearch, int wikiSearchPages,
			boolean includeSourceAnnotatorTopK, int topKAnnotatorCandidates,
			boolean includeRelatedSearch, int topKRelatedSearch,
			boolean includeSourceSnippets, int topKAnnotateSnippet, boolean tagTitles, WATAnnotator snippetAnnotator,
			SnippetAnnotationFilter snippetAnnotationFilter,
			WikipediaApiInterface wikiApi, String bingKey) {
		this.boldDisambiguator = auxDisambiguator;
		this.boldFilter = boldFilter;
		this.entityFilter = entityFilter;
		this.entityFilterNormalizer = entityFilterNormalizer;
		this.linkBack = linkBack;
		this.wikiApi = wikiApi;
		this.includeSourceAnnotator = includeSourceAnnotator;
		this.includeSourceNormalSearch = includeSourceNormalSearch;
		this.includeSourceWikiSearch = includeSourceWikiSearch;
		this.topKWikiSearch = wikiSearchPages;
		this.includeSourceRelatedSearch = includeRelatedSearch;
		this.topKRelatedSearch = topKRelatedSearch;
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

	/**
	 * Call the disambiguator and disambiguate the bolds.
	 * 
	 * @param text
	 *            concatenated bolds.
	 * @param mentions
	 *            mentions (one per bold).
	 * @return a triple that has: additional info returned by the annotator for
	 *         the query as left element; the mapping from bold to annotation as
	 *         middle element; additional candidates info as right element.
	 * @throws IOException
	 * @throws XPathExpressionException
	 * @throws ParserConfigurationException
	 * @throws SAXException
	 */
	private Pair<HashMap<Mention, HashMap<String, Double>>, HashMap<String, Tag>> disambiguateBolds(
			String text, HashSet<Mention> mentions) throws IOException,
			XPathExpressionException, ParserConfigurationException,
			SAXException {
		HashSet<Annotation> anns;
		anns = boldDisambiguator.solveD2W(text, mentions);
		if (anns == null)
			return new Pair<HashMap<Mention, HashMap<String, Double>>, HashMap<String, Tag>>(
					new HashMap<Mention, HashMap<String, Double>>(),
					new HashMap<String, Tag>());

		List<Integer> widsToPrefetch = new Vector<>();
		for (Annotation ann : anns)
			widsToPrefetch.add(ann.getConcept());
		wikiApi.prefetchWids(widsToPrefetch);

		HashMap<Mention, List<HashMap<String, Double>>> additionalCandidatesInfo = boldDisambiguator
				.getLastQueryAdditionalCandidatesInfo();
		for (Mention mention : additionalCandidatesInfo.keySet())
			additionalCandidatesInfo.put(mention,
					additionalCandidatesInfo.get(mention));

		HashMap<String, Tag> boldToEntity = new HashMap<>();
		for (Annotation ann : anns)
			boldToEntity.put(
					text.substring(ann.getPosition(),
							ann.getPosition() + ann.getLength()),
					new Tag(ann.getConcept()));
		return new Pair<HashMap<Mention, HashMap<String, Double>>, HashMap<String, Tag>>(
				boldDisambiguator.getLastQueryAdditionalInfo(), boldToEntity);
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

			for (Tag candidate : qi.entityToFtrVects.keySet()){
				if (predictNEonly
						&& !ERDDatasetFilter.EntityIsNE(wikiApi,
								wikiToFreebase, candidate.getConcept()))
					continue;
				for (HashMap<String, Double> features : qi.entityToFtrVects
						.get(candidate)) {
					boolean accept = entityFilter.filterEntity(
							new EntityFeaturePack(features),
							entityFilterNormalizer);
					if (accept)
						acceptedEntities.add(candidate);
					if (debugger != null) {
						if (accept)
							debugger.addResult(query, candidate.getConcept());

					}
				}
			}
			/** Link entities back to query mentions */
			annotations = linkBack.linkBack(query, acceptedEntities, qi);

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		SmaphAnnotatorDebugger.out.printf("*** END :%s ***%n", query);

		return annotations;

	}

	/**
	 * @param relatedSearchRes
	 *            the related search suggested in the first query to Bing.
	 * @param query
	 *            the input query.
	 * @return the best related search for Source 4.
	 */
	private static String getRelatedSearch(List<String> relatedSearchRes,
			String query) {
		if (relatedSearchRes.isEmpty())
			return null;
		List<String> qTokens = SmaphUtils.tokenize(query);
		List<String> rsTokens = SmaphUtils.tokenize(relatedSearchRes.get(0));

		String newSearch = "";
		int insertedTokens = 0;
		for (String rsToken : rsTokens)
			for (String qToken : qTokens)
				if (SmaphUtils.getNormEditDistance(qToken, rsToken) < 0.5) {
					newSearch += rsToken + " ";
					insertedTokens++;
					break;
				}
		if (insertedTokens == 0)
			return null;
		if (newSearch.isEmpty())
			return null;
		if (newSearch.charAt(newSearch.length() - 1) == ' ')
			newSearch = newSearch.substring(0, newSearch.length() - 1);
		return newSearch;
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
					title = title.replaceAll(WIKITITLE_ENDPAR_REGEX, "");

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
	 * Concatenates the bolds passed by argument.
	 * 
	 * @param bolds
	 * @return the list of concatenated bolds and the set of their mentions.
	 */
	private static Pair<String, HashSet<Mention>> concatenateBolds(
			List<String> bolds) {
		HashSet<Mention> mentions = new HashSet<Mention>();
		String concat = "";
		for (String spot : bolds) {
			int mentionStart = concat.length();
			int mentionEnd = mentionStart + spot.length() - 1;
			mentions.add(new Mention(mentionStart, mentionEnd - mentionStart
					+ 1));
			concat += spot + " ";
		}
		return new Pair<String, HashSet<Mention>>(concat, mentions);
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
			SmaphAnnotatorDebugger.out.printf("Rank:%d Bold:%s%n", rankI, spot);
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

	/**
	 * Generates the Entity Selection features for an entity drawn from Source 1
	 * (Annotator)
	 * 
	 * @param query
	 *            the query that has been issued to Bing.
	 * @param resultsCount
	 *            the number of results contained in the Bing response.
	 * @param bold
	 *            the bold that was tagged to this entity.
	 * @param bingBolds
	 *            the list of bolds spotted by Bing plus their position.
	 * @param additionalInfo
	 *            additional info returned by the annotator.
	 * @param wid 
	 * @param allBoldsNS list of bolds seen in search results.
	 * @return a mapping between feature name and its value.
	 */
	private HashMap<String, Double> generateEntityFeaturesAnnotator(
			String query, int resultsCount, String bold,
			List<Pair<String, Integer>> bingBolds,
			HashMap<String, Double> additionalInfo, int wid, List<String> allBoldsNS) {
		HashMap<String, Double> result = new HashMap<>();

		try {
			result.put("s1_is_named_entity", ERDDatasetFilter.EntityIsNE(wikiApi,
									wikiToFreebase, wid)? 1.0:0.0);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		result.put("is_s1", 1.0);
		result.put("is_s2", 0.0);
		result.put("is_s3", 0.0);
		result.put("is_s4", 0.0);
		result.put("is_s5", 0.0);
		result.put("is_s6", 0.0);
		result.put("s1_freq",
				FrequencyBoldFilter.getFrequency(bingBolds, bold, resultsCount));
		result.put("s1_avgRank",
				RankWeightBoldFilter.getAvgRank(bingBolds, bold, resultsCount));

		result.put("s1_editDistance", SmaphUtils.getMinEditDist(query, bold));
		
		result.put("s1_fragmentation", SmaphUtils.getFragmentation(allBoldsNS, bold));
		result.put("s1_aggregation", SmaphUtils.getAggregation(allBoldsNS, bold));

		// Add additional info like rho, commonness, etc.
		for (String key : additionalInfo.keySet())
			result.put("s1_" + key, additionalInfo.get(key));

		return result;

	}

	/**
	 * Generates the Entity Selection features for an entity drawn from Source 2
	 * (Normal Search)
	 * 
	 * @param query
	 *            the query that has been issued to Bing.
	 * @param wid
	 *            the Wikipedia page ID of the entity.
	 * @param rank
	 *            the position in which the entity appeared in the Bing results.
	 * @param wikiWebTotal
	 *            total web results found by Bing for the Wikisearch.
	 * @param webTotal
	 *            total web results found by Bing for the normal search.
	 * @param bingBoldsWS
	 *            the list of bolds spotted by Bing for the Wikisearch plus
	 *            their position.
	 * @param source
	 *            Source id (3 for WikiSearch)
	 * @return a mapping between feature name and its value.
	 */
	private HashMap<String, Double> generateEntityFeaturesSearch(
			String query, int wid, int rank, double webTotal,
			double wikiWebTotal, List<Pair<String, Integer>> bingBoldsWS,
			int source) {
		String sourceName = "s" + source;
		HashMap<String, Double> result = new HashMap<>();
		try {
			result.put(sourceName + "_is_named_entity", ERDDatasetFilter.EntityIsNE(wikiApi,
									wikiToFreebase, wid)? 1.0:0.0);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		result.put("is_s1", 0.0);
		result.put("is_s2", 0.0);
		result.put("is_s3", 0.0);
		result.put("is_s4", 0.0);
		result.put("is_s5", 0.0);
		result.put("is_s6", 0.0);
		result.put("is_" + sourceName, 1.0);
		result.put(sourceName + "_rank", (double) rank);
		result.put(sourceName + "_webTotal", (double) webTotal);
		result.put(sourceName + "_wikiWebTotal", (double) wikiWebTotal);
		String title;
		try {
			title = wikiApi.getTitlebyId(wid);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		result.put(sourceName + "_editDistanceTitle",
				SmaphUtils.getMinEditDist(query, title));
		result.put(
				sourceName + "_editDistanceNoPar",
				SmaphUtils.getMinEditDist(query,
						title.replaceAll(WIKITITLE_ENDPAR_REGEX, "")));

		double minEdDist = 1.0;
		double capitalized = 0;
		double avgNumWords = 0;
		int boldsCount = 0;
		for (Pair<String, Integer> p : bingBoldsWS)
			if (p.second == rank) {
				boldsCount++;
				minEdDist = Math.min(minEdDist,
						SmaphUtils.getMinEditDist(query, p.first));
				if (Character.isUpperCase(p.first.charAt(0)))
					capitalized++;
				avgNumWords += p.first.split("\\W+").length;
			}
		if (boldsCount != 0)
			avgNumWords /= boldsCount;
		result.put(sourceName + "_editDistanceBolds", minEdDist);
		result.put(sourceName + "_capitalizedBolds", capitalized);
		result.put(sourceName + "_avgBoldsWords", avgNumWords);

		return result;
	}

	private HashMap<String, Double> generateEntityFeaturesSnippet(String query,
			double webTotal, int resultsCount, List<String> mentions, List<String> bolds,
			List<Integer> ranks, List<HashMap<String, Double>> additionalInfos,
			int wid) {
		HashMap<String, Double> result = new HashMap<>();
		result.put("is_s1", 0.0);
		result.put("is_s2", 0.0);
		result.put("is_s3", 0.0);
		result.put("is_s4", 0.0);
		result.put("is_s5", 0.0);
		result.put("is_s6", 1.0);
		result.put("s6_webTotal", (double) webTotal);
		result.put("s6_freq",
				FrequencyBoldFilter.getFrequency(ranks.size(), resultsCount));
		result.put("s6_avgRank",
				RankWeightBoldFilter.computeAvgRank(ranks, resultsCount));

		result.put("s6_pageRank", additionalInfos.get(0).get("pageRank"));

		try {
			result.put("s6_is_named_entity", ERDDatasetFilter.EntityIsNE(wikiApi,
					wikiToFreebase, wid) ? 1.0 : 0.0);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		List<Double> rhoes = new Vector<>();
		List<Double> lps = new Vector<>();
		List<Double> commonness = new Vector<>();
		List<Double> ambiguities = new Vector<>();
		for (HashMap<String, Double> addInfo : additionalInfos){
			rhoes.add(addInfo.get("rho"));
			lps.add(addInfo.get("lp"));
			commonness.add(addInfo.get("commonness"));
			ambiguities.add(addInfo.get("ambiguity"));
		}
		List<Double> boldEDs = new Vector<>();
		for (String bold : bolds)
			boldEDs.add(SmaphUtils.getMinEditDist(query, bold));
		List<Double> mentionEDs = new Vector<>();
		for (String mention : mentions)
			mentionEDs.add(SmaphUtils.getMinEditDist(query, mention));
		List<Double> mentionBoldOverlap = new Vector<>();
		for (int i = 0; i < mentions.size(); i++) {
			List<String> boldTokens = SmaphUtils.tokenize(bolds.get(i));
			List<String> mentionTokens = SmaphUtils.tokenize(mentions.get(i));
			int overlap = 0;
			for (String boldToken : boldTokens)
				if (mentionTokens.contains(boldToken))
					overlap++;
			mentionBoldOverlap.add(((double) overlap)
					/ ((double) boldTokens.size() + mentionTokens.size()));
		}		
			
		Triple<Double, Double, Double> minMaxAvgRho = SmaphUtils.getMinMaxAvg(rhoes);
		result.put("s6_min_rho", minMaxAvgRho.getLeft());
		result.put("s6_max_rho", minMaxAvgRho.getMiddle());
		result.put("s6_avg_rho", minMaxAvgRho.getRight());
		Triple<Double, Double, Double> minMaxAvgLp = SmaphUtils.getMinMaxAvg(lps);
		result.put("s6_min_lp", minMaxAvgLp.getLeft());
		result.put("s6_max_lp", minMaxAvgLp.getMiddle());
		result.put("s6_avg_lp", minMaxAvgLp.getRight());
		Triple<Double, Double, Double> minMaxAvgComm = SmaphUtils.getMinMaxAvg(commonness);
		result.put("s6_min_commonness", minMaxAvgComm.getLeft());
		result.put("s6_max_commonness", minMaxAvgComm.getMiddle());
		result.put("s6_avg_commonness", minMaxAvgComm.getRight());
		Triple<Double, Double, Double> minMaxAvgAmbig = SmaphUtils.getMinMaxAvg(ambiguities);
		result.put("s6_min_ambig", minMaxAvgAmbig.getLeft());
		result.put("s6_max_ambig", minMaxAvgAmbig.getMiddle());
		result.put("s6_avg_ambig", minMaxAvgAmbig.getRight());
		Triple<Double, Double, Double> minMaxAvgBoldED = SmaphUtils.getMinMaxAvg(boldEDs);
		result.put("s6_min_min_bold_ed", minMaxAvgBoldED.getLeft());
		result.put("s6_max_min_bold_ed", minMaxAvgBoldED.getMiddle());
		result.put("s6_avg_min_bold_ed", minMaxAvgBoldED.getRight());
		Triple<Double, Double, Double> minMaxAvgMentionED = SmaphUtils.getMinMaxAvg(mentionEDs);
		result.put("s6_min_min_mention_ed", minMaxAvgMentionED.getLeft());
		result.put("s6_max_min_mention_ed", minMaxAvgMentionED.getMiddle());
		result.put("s6_avg_min_mention_ed", minMaxAvgMentionED.getRight());
		Triple<Double, Double, Double> minMaxAvgMentionBoldOverlap = SmaphUtils.getMinMaxAvg(mentionBoldOverlap);
		result.put("s6_min_mention_bold_overlap", minMaxAvgMentionBoldOverlap.getLeft());
		result.put("s6_max_mention_bold_overlap", minMaxAvgMentionBoldOverlap.getMiddle());
		result.put("s6_avg_mention_bold_overlap", minMaxAvgMentionBoldOverlap.getRight());
		
		
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
		List<String> filteredBolds = null;
		HashMap<Integer, Integer> rankToIdNS = null;
		HashMap<Integer, HashSet<String>> rankToBoldsNS = null;
		List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBolds = null;
		HashMap<Tag, String[]> entityToBoldsS2S3 = new HashMap<>();
		List<String> allBoldsNS = null;
		if (includeSourceAnnotator || includeSourceWikiSearch
				|| includeSourceRelatedSearch || includeSourceNormalSearch
				|| includeSourceSnippets) {
			bingBoldsAndRankNS = new Vector<>();
			urls = new Vector<>();
			relatedSearchRes = new Vector<>();
			snippetsToBolds = new Vector<>();
			resCountAndWebTotalNS = takeBingData(query, bingBoldsAndRankNS,
					urls, relatedSearchRes, snippetsToBolds, Integer.MAX_VALUE,
					false);
			resultsCount = resCountAndWebTotalNS.getLeft();
			webTotalNS = resCountAndWebTotalNS.getMiddle();
			filteredBolds = boldFilter.filterBolds(query, bingBoldsAndRankNS,
					resultsCount);
			rankToIdNS = urlsToRankID(urls);
			rankToBoldsNS = new HashMap<>();
			allBoldsNS = SmaphUtils.boldPairsToListLC(bingBoldsAndRankNS);
			SmaphUtils
					.mapRankToBoldsLC(bingBoldsAndRankNS, rankToBoldsNS, null);
			for (int rank : rankToIdNS.keySet()) {
				String[] bolds = rankToBoldsNS.containsKey(rank) ? rankToBoldsNS
						.get(rank).toArray(new String[] {}) : new String[] {};
				entityToBoldsS2S3.put(new Tag(rankToIdNS.get(rank)), bolds);
			}

			if (debugger != null) {
				debugger.addBoldPositionEditDistance(query, bingBoldsAndRankNS);
				debugger.addSnippets(query, snippetsToBolds);
				debugger.addBoldFilterOutput(query, filteredBolds);
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
		double webTotalWS = Double.NaN;
		if (includeSourceWikiSearch | includeSourceNormalSearch) {
			resCountAndWebTotalWS = takeBingData(query, bingBoldsAndRankWS,
					wikiSearchUrls, null, null, topKWikiSearch, true);
			webTotalWS = resCountAndWebTotalWS.getMiddle();
			HashMap<Integer, Integer> rankToIdWikiSearch = urlsToRankID(wikiSearchUrls);
			rankToBoldsWS = new HashMap<>();
			SmaphUtils
					.mapRankToBoldsLC(bingBoldsAndRankWS, rankToBoldsWS, null);
			if (debugger != null) {
				debugger.addSource3SearchResult(query, rankToIdWikiSearch,
						wikiSearchUrls);
				debugger.addBingResponseWikiSearch(query,
						resCountAndWebTotalWS.getRight());

			}
			annTitlesToIdAndRankWS = adjustTitles(rankToIdWikiSearch);
		}

		/** Do the RelatedSearch on bing */
		String relatedSearch = null;
		List<String> relatedSearchUrls = null;
		List<Pair<String, Integer>> bingBoldsAndRankRS = null;
		HashMap<Integer, Integer> rankToIdRelatedSearch = null;
		HashMap<String, Pair<Integer, Integer>> annTitlesToIdAndRankRS = null;
		double webTotalRelatedSearch = Double.NaN;
		HashMap<Integer, HashSet<String>> rankToBoldsRS = null;
		if (includeSourceRelatedSearch) {
			relatedSearch = getRelatedSearch(relatedSearchRes, query);
			relatedSearchUrls = new Vector<>();
			bingBoldsAndRankRS = new Vector<>();
			Triple<Integer, Double, JSONObject> resCountAndWebTotalRS = takeBingData(
					query, bingBoldsAndRankRS, relatedSearchUrls, null, null,
					topKRelatedSearch, false);
			webTotalRelatedSearch = resCountAndWebTotalRS.getMiddle();
			rankToIdRelatedSearch = urlsToRankID(relatedSearchUrls);
			annTitlesToIdAndRankRS = adjustTitles(rankToIdRelatedSearch);
			rankToBoldsRS = new HashMap<>();
			SmaphUtils
					.mapRankToBoldsLC(bingBoldsAndRankRS, rankToBoldsRS, null);

		}

		/** Annotate bolds on the annotator */
		HashMap<String, Tag> boldToEntity = null;
		HashMap<Mention, HashMap<String, Double>> additionalInfo = null;
		HashMap<String, Mention> boldToMention = null;
		if (includeSourceAnnotator) {
			Pair<String, HashSet<Mention>> annInput = concatenateBolds(filteredBolds);
			boldToMention = getBoldToMention(annInput.first, annInput.second);
			Pair<HashMap<Mention, HashMap<String, Double>>, HashMap<String, Tag>> infoAndAnnotations = disambiguateBolds(
					annInput.first, annInput.second);
			boldToEntity = infoAndAnnotations.second;
			additionalInfo = infoAndAnnotations.first;

			if (debugger != null)
				debugger.addReturnedAnnotation(query, boldToEntity);
		}
		
		/** Annotate snippets */
		HashMap<Tag,List<Integer>> tagToRanks = null;
		HashMap<Tag,List<String>> tagToMentions = null;
		HashMap<Tag,List<String>> tagToBolds = null;
		HashMap<Tag,List<HashMap<String,Double>>> tagToAdditionalInfos = null;
		HashSet<Tag> filteredAnnotations = null;
		if (includeSourceSnippets){
			List<List<Pair<ScoredAnnotation, HashMap<String, Double>>>> snippetAnnotations = new Vector<>();
			tagToBolds = new HashMap<>();
			annotateSnippets(snippetsToBolds, snippetAnnotations, tagToBolds);
			tagToRanks = getSnippetAnnotationRanks(snippetAnnotations);
			tagToMentions = getSnippetMentions(snippetAnnotations, snippetsToBolds);
			tagToAdditionalInfos = getSnippetAdditionalInfo(snippetAnnotations);
			filteredAnnotations = snippetAnnotationFilter.filterAnnotations(tagToRanks, resultsCount);
		}

		HashMap<Tag, List<HashMap<String, Double>>> entityToFtrVects = new HashMap<>();
		// Filter and add annotations found by the disambiguator
		if (includeSourceAnnotator) {
			for (String bold : filteredBolds) {
				if (boldToEntity.containsKey(bold)) {
					Tag ann = boldToEntity.get(bold);
					HashMap<String, Double> ESFeatures = generateEntityFeaturesAnnotator(
							query, resultsCount, bold, bingBoldsAndRankNS,
							additionalInfo.get(boldToMention.get(bold)), ann.getConcept(), allBoldsNS);
					Tag tag = new Tag(ann.getConcept());
					if (!entityToFtrVects.containsKey(tag))
						entityToFtrVects.put(tag,
								new Vector<HashMap<String, Double>>());
					entityToFtrVects.get(tag).add(ESFeatures);
				}
			}
		}

		// Filter and add entities found in the normal search
		if (includeSourceNormalSearch) {
			for (int rank : rankToIdNS.keySet()) {
				int wid = rankToIdNS.get(rank);
				HashMap<String, Double> ESFeatures = generateEntityFeaturesSearch(
						query, wid, rank, webTotalNS, webTotalWS,
						bingBoldsAndRankNS, 2);
				Tag tag = new Tag(wid);
				if (!entityToFtrVects.containsKey(tag))
					entityToFtrVects.put(tag,
							new Vector<HashMap<String, Double>>());
				entityToFtrVects.get(tag).add(ESFeatures);

			}
		}

		// Filter and add entities found in the WikipediaSearch
		if (includeSourceWikiSearch) {
			for (String annotatedTitleWS : annTitlesToIdAndRankWS.keySet()) {
				int wid = annTitlesToIdAndRankWS.get(annotatedTitleWS).first;
				int rank = annTitlesToIdAndRankWS.get(annotatedTitleWS).second;
				HashMap<String, Double> ESFeatures = generateEntityFeaturesSearch(
						query, wid, rank, webTotalNS, webTotalWS,
						bingBoldsAndRankWS, 3);

				Tag tag = new Tag(wid);
				if (!entityToFtrVects.containsKey(tag))
					entityToFtrVects.put(tag,
							new Vector<HashMap<String, Double>>());
				entityToFtrVects.get(tag).add(ESFeatures);

			}
		}

		// Filter and add entities found in the RelatedSearch
		if (includeSourceRelatedSearch) {
			for (String annotatedTitleRS : annTitlesToIdAndRankRS.keySet()) {
				int wid = annTitlesToIdAndRankRS.get(annotatedTitleRS).first;
				int rank = annTitlesToIdAndRankRS.get(annotatedTitleRS).second;
				HashMap<String, Double> ESFeatures = generateEntityFeaturesSearch(
						relatedSearch, wid, rank, webTotalNS,
						webTotalRelatedSearch, bingBoldsAndRankRS, 5);

				Tag tag = new Tag(wid);
				if (!entityToFtrVects.containsKey(tag))
					entityToFtrVects.put(tag,
							new Vector<HashMap<String, Double>>());
				entityToFtrVects.get(tag).add(ESFeatures);

			}
		}
		
		//Generate features for entities found by the Snippet annotation
		if (includeSourceSnippets) {
			for (Tag entity : filteredAnnotations) {
				HashMap<String, Double> ESFeatures = generateEntityFeaturesSnippet(
						query, webTotalNS, resultsCount,
						tagToMentions.get(entity), tagToBolds.get(entity), tagToRanks.get(entity),
						tagToAdditionalInfos.get(entity), entity.getConcept());
				if (!entityToFtrVects.containsKey(entity))
					entityToFtrVects.put(entity,
							new Vector<HashMap<String, Double>>());
				entityToFtrVects.get(entity).add(ESFeatures);
			}
		}

		if (debugger != null)
			debugger.addCandidateEntities(query, entityToFtrVects.keySet());

		QueryInformation qi = new QueryInformation();
		qi.entityToFtrVects = entityToFtrVects;
		qi.boldToEntityS1 = boldToEntity;
		qi.tagToBoldsS6 = tagToBolds;
		qi.entityToBoldS2S3 = entityToBoldsS2S3;
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

	private HashMap<String, Mention> getBoldToMention(String text,
			HashSet<Mention> mentions) {
		HashMap<String,Mention> res = new HashMap<>();
		for (Mention m : mentions)
			res.put(text.substring(m.getPosition(),  m.getPosition()+m.getLength()), m);
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
			Regressor arLevel1,
			FeatureNormalizer arNormLevel1,
			WikipediaToFreebase wikiToFreeb, double anchorMaxED)
			throws Exception {

		//TODO: qi should contain all info to generate entity feature vectors, rather than the vectors theirselves.
		QueryInformation qi = getQueryInformation(query);

		// Generate examples for entityFilter
		if (EFVectorsToPresence != null)
			for (Tag tag : qi.entityToFtrVects.keySet()) {
				if (keepNEOnly
						&& !ERDDatasetFilter.EntityIsNE(wikiApi, wikiToFreeb,
								tag.getConcept()))
					continue;
				for (HashMap<String, Double> ftrs : qi.entityToFtrVects
						.get(tag)) {

					EFVectorsToPresence.add(new Pair<FeaturePack<Tag>, Boolean>(new EntityFeaturePack(ftrs), goldStandard.contains(tag)));
					if (EFCandidates != null)
						EFCandidates.add(tag);
					System.out.printf("%d in query [%s] is a %s example.%n",
							tag.getConcept(), query,
							goldStandard.contains(tag) ? "positive"
									: "negative");
				}
			}

		// Generate examples for linkBack
		if (lbVectorsToF1 != null) {
			List<Triple<HashSet<Annotation>, BindingFeaturePack, Double>> bindingsToFtrAndF1 = getLBBindingToFtrsAndF1(
					query, qi, bg, arLevel1, arNormLevel1, goldStandardAnn, new StrongAnnotationMatch(
							wikiApi));
			for (Triple<HashSet<Annotation>, BindingFeaturePack, Double> bindingAndFtrsAndF1 : bindingsToFtrAndF1) {
				BindingFeaturePack features = bindingAndFtrsAndF1.getMiddle();
				double f1 = bindingAndFtrsAndF1.getRight();
				HashSet<Annotation> binding = bindingAndFtrsAndF1.getLeft();
				lbVectorsToF1.add(new Pair<FeaturePack<HashSet<Annotation>>, Double>(features, f1));
				
				if (BRCandidates != null)
					BRCandidates.add(binding);
				
			}
		}

		// Generate examples for annotation filter
		if (annVectorsToPresence != null) {
			List<Triple<Annotation, AnnotationFeaturePack, Boolean>> annotationsAndFtrAndPresences = getARToFtrsAndPresence(
					query, qi, goldStandardAnn, new StrongAnnotationMatch(wikiApi));
			for (Triple<Annotation, AnnotationFeaturePack, Boolean> annotationsAndFtrAndPresence : annotationsAndFtrAndPresences) {
				AnnotationFeaturePack features = annotationsAndFtrAndPresence.getMiddle();
				Annotation ann = annotationsAndFtrAndPresence.getLeft();
				annVectorsToPresence.add(new Pair<FeaturePack<Annotation>, Boolean>(features, annotationsAndFtrAndPresence
						.getRight()));
				if (ARCandidates != null)
					ARCandidates.add(ann);
				System.out.printf("[%s]->%d in query [%s] is a %s example.%n",
						query.substring(ann.getPosition(), ann.getPosition() + ann.getLength()), ann.getConcept(), query,
						annotationsAndFtrAndPresence.getRight() ? "positive" : "negative");

			}
		}
		// Generate examples for advanced annotation filter
		if (advancedAnnVectorsToPresence != null) {
			//TODO: WATCH OUT! you should use StrongAnnotationMatch here!!!
			List<Triple<Annotation, AdvancedAnnotationFeaturePack, Boolean>> annotationsAndFtrAndPresences = getAdvancedARToFtrsAndPresence(
					query, qi, goldStandardAnn, new StrongMentionAnnotationMatch(), anchorMaxED);
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
		for (Annotation a : SvmAdvancedIndividualSingleLinkback.getAnnotations(query,
				qi.entityToFtrVects.keySet(), qi, anchorMaxED)) {
			boolean inGold = false;
			for (Annotation goldAnn : goldStandardAnn)
				if (annotationMatch.match(goldAnn, a)) {
					inGold = true;
					break;
				}
			for (HashMap<String, Double> entityFeatures : qi.entityToFtrVects.get(new Tag(a.getConcept()))) {
				if (EntityToAnchors.e2a().containsId(a.getConcept())) {
					AdvancedAnnotationFeaturePack features = new AdvancedAnnotationFeaturePack(a, query, EntityToAnchors.e2a()
							.getAnchors(a.getConcept()), entityFeatures);

					annAndFtrsAndPresence.add(new ImmutableTriple<Annotation, AdvancedAnnotationFeaturePack, Boolean>(a,
							features, inGold));
				} else
					logger.debug("No anchors found for id=" + a.getConcept());
			}
		}

		return annAndFtrsAndPresence;
	}

	private List<Triple<Annotation, AnnotationFeaturePack, Boolean>> getARToFtrsAndPresence(
			String query, QueryInformation qi,
			HashSet<Annotation> goldStandardAnn,
			MatchRelation<Annotation> annotationMatch) {
		HashMap<Tag, String> entityToTitle = SmaphUtils.getEntitiesToTitles(
				qi.entityToFtrVects.keySet(), wikiApi);

		HashMap<Tag, String[]> entityToBolds;
				
		if (qi.boldToEntityS1 != null)
			entityToBolds = SmaphUtils.getEntitiesToBolds(qi.boldToEntityS1,
					qi.entityToFtrVects.keySet());
		else
			entityToBolds = SmaphUtils.getEntitiesToBoldsList(qi.tagToBoldsS6,
					qi.entityToFtrVects.keySet());

		EnglishStemmer stemmer = new EnglishStemmer();

		List<Triple<Annotation, AnnotationFeaturePack, Boolean>> annAndFtrsAndPresence = new Vector<>();
		for (Annotation a : SvmIndividualAnnotationLinkBack.getAnnotations(query,
				qi.entityToFtrVects.keySet(), qi)) {
			boolean inGold = false;
			for (Annotation goldAnn : goldStandardAnn)
				if (annotationMatch.match(goldAnn, a)) {
					inGold = true;
					break;
				}
			for (HashMap<String,Double> entityFeatures : qi.entityToFtrVects
 					.get(new Tag(a.getConcept()))) {
				AnnotationFeaturePack features = new AnnotationFeaturePack(a, query, stemmer, entityFeatures,
								entityToBolds, entityToTitle);

				annAndFtrsAndPresence
						.add(new ImmutableTriple<Annotation, AnnotationFeaturePack, Boolean>(
								a, features, inGold));
			}
		}

		return annAndFtrsAndPresence;
	}

	private List<Triple<HashSet<Annotation>, BindingFeaturePack, Double>> getLBBindingToFtrsAndF1(
			String query, QueryInformation qi, BindingGenerator bg, Regressor ar, FeatureNormalizer annFn,
			HashSet<Annotation> goldStandardAnn, MatchRelation<Annotation> match) {
		HashMap<Tag, String> entityToTitles = SmaphUtils.getEntitiesToTitles(qi.entityToFtrVects.keySet(), wikiApi);
		
		HashMap<Tag, String[]> entityToBolds;
		if (qi.boldToEntityS1 != null)
			entityToBolds = SmaphUtils.getEntitiesToBolds(qi.boldToEntityS1,
					qi.entityToFtrVects.keySet());
		else
			entityToBolds = SmaphUtils.getEntitiesToBoldsList(qi.tagToBoldsS6,
					qi.entityToFtrVects.keySet());
		
		List<HashSet<Annotation>> allBindings = bg.getBindings(query, qi,
				qi.entityToFtrVects.keySet(), wikiApi);
		
		// Precompute annotation regressor scores
		HashMap<Annotation, Double> regressorScores = null;
		if (ar != null){
			regressorScores = SmaphUtils.predictBestScores(ar, annFn, allBindings, query,
					qi.entityToFtrVects, entityToBolds, entityToTitles,
					new EnglishStemmer());
		}

		List<Triple<HashSet<Annotation>, BindingFeaturePack, Double>> res = new Vector<>();
		for (HashSet<Annotation> binding : allBindings) {
			Metrics<Annotation> m = new Metrics<>();
			int tp = m.getSingleTp(goldStandardAnn, binding, match).size();
			int fp = m.getSingleFp(goldStandardAnn, binding, match).size();
			int fn = m.getSingleFn(goldStandardAnn, binding, match).size();
			float f1 = Metrics.F1(Metrics.recall(tp, fp, fn),
					Metrics.precision(tp, fp));

			BindingFeaturePack bindingFeatures = new BindingFeaturePack(binding,
					query, entityToBolds, entityToTitles,
					qi.entityToFtrVects, regressorScores);
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
			Set<Tag> retrievedEntities = qi.entityToFtrVects.keySet();
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
				query, qi, bg, null, null, goldStandardAnn, new StrongAnnotationMatch(
						wikiApi));
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
				query, qi, bg, null, null, goldStandardAnn,
				new StrongMentionAnnotationMatch());
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
