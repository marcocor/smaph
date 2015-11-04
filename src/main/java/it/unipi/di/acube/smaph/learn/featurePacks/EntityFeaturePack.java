package it.unipi.di.acube.smaph.learn.featurePacks;

import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.main.ERDDatasetFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang3.tuple.Triple;


public class EntityFeaturePack extends FeaturePack<Tag> {
	private static final long serialVersionUID = 1L;
	
	public static List<EntityFeaturePack> getAllFeaturePacks(Tag candidate, String query, QueryInformation qi, WikipediaApiInterface wikiApi){
		List<EntityFeaturePack> res = new Vector<>();
		for (HashMap<String, Double> ftrs : getFeatures(candidate, query, qi, wikiApi))
			res.add(new EntityFeaturePack(ftrs));
		return res;
	}

	/*public EntityFeaturePack(Tag candidate, String query, QueryInformation qi, WikipediaApiInterface wikiApi) {
		super(getFeatures(candidate, query, qi, wikiApi));
	}*/

	//TODO: delete!
	public EntityFeaturePack(HashMap<String, Double> ftrs) {
	   super(ftrs);
    }

	public EntityFeaturePack() {
		super(null);
	}


	public static String[] ftrNames = new String[] {
		"is_s1", // 1
		"is_s2",
		"is_s3",
		"is_s4",
		"is_s5",
		"s1_freq",
		"s1_rhoScore", //
		"s1_localCoherence", //
		"s1_lp",
		"s1_editDistance", // 10
		"s1_commonness", //
		"s1_avgRank",
		"s1_ambiguity",
		"s1_pageRank", //
		"s2_editDistanceTitle",
		"s2_rank",
		"s2_wikiWebTotal",
		"s2_webTotal",
		"s3_rank",
		"s3_wikiWebTotal", // 20
		"s3_editDistanceTitle",
		"s3_editDistanceNoPar",
		"s3_editDistanceBolds",
		"s3_capitalizedBolds",
		"s3_avgBoldsWords",
		"s5_rank",
		"s5_wikiWebTotal",
		"s5_editDistanceTitle",
		"s5_editDistanceNoPar",
		"s5_editDistanceBolds", // 30
		"s5_capitalizedBolds",
		"s5_avgBoldsWords",
		"s3_webTotal",
		"s2_editDistanceNoPar",
		"s2_editDistanceBolds",
		"s2_capitalizedBolds",
		"s2_avgBoldsWords",
		"s1_is_named_entity",
		"s2_is_named_entity",
		"s3_is_named_entity", //40
		"s6_is_named_entity",
		"s1_fragmentation",
		"s1_aggregation",
		"is_s6",
		"s6_webTotal",
		"s6_freq",
		"s6_avgRank",
		"s6_pageRank",
		"s6_min_rho",
		"s6_max_rho",       //50
		"s6_avg_rho",
		"s6_min_lp",
		"s6_max_lp",
		"s6_avg_lp",
		"s6_min_commonness",
		"s6_max_commonness",
		"s6_avg_commonness",
		"s6_min_ambig",
		"s6_max_ambig",
		"s6_avg_ambig",		//60
		"s6_min_min_bold_ed",
		"s6_max_min_bold_ed",
		"s6_avg_min_bold_ed",
		"s6_min_min_mention_ed",
		"s6_max_min_mention_ed",
		"s6_avg_min_mention_ed",
		"s6_min_mention_bold_overlap",
		"s6_max_mention_bold_overlap",
		"s6_avg_mention_bold_overlap", //69
	};

	@Override
	public String[] getFeatureNames() {
		return getFeatureNamesStatic();
	}

	private static String[] getFeatureNamesStatic() {
		return ftrNames;
	}

	public static List<HashMap<String, Double>> getFeatures(Tag candidate, String query, QueryInformation qi, WikipediaApiInterface wikiApi){
		List<HashMap<String, Double>> res = new Vector<>();
		int wid = candidate.getConcept();
		// Filter and add entities found in the normal search
		if (qi.includeSourceNormalSearch && qi.candidatesNS.contains(candidate)){
			int rank = qi.idToRankNS.get(wid);
			HashMap<String, Double> ESFeatures = generateEntityFeaturesSearch(
					query, wid, rank,  qi.webTotalNS,  qi.webTotalWS,
					qi.bingBoldsAndRankNS, 2, wikiApi);
			res.add(ESFeatures); //TODO: merge with others instead of returning
		}

		// Filter and add entities found in the WikipediaSearch
		if (qi.includeSourceWikiSearch && qi.candidatesWS.contains(candidate)) {
			for (String annotatedTitleWS : qi.annTitlesToIdAndRankWS.keySet()) {
				int widI = qi.annTitlesToIdAndRankWS.get(annotatedTitleWS).first;//TODO: this can definitely be optimized.
				if (widI == wid){
					int rank = qi.annTitlesToIdAndRankWS.get(annotatedTitleWS).second;
					HashMap<String, Double> ESFeatures = generateEntityFeaturesSearch(
							query, wid, rank, qi.webTotalNS, qi.webTotalWS,
							qi.bingBoldsAndRankWS, 3, wikiApi);
					res.add(ESFeatures);
				}
			}
		}

		//Generate features for entities found by the Snippet annotation
		if (qi.includeSourceSnippets && qi.candidatesSA.contains(candidate)) {
			HashMap<String, Double> ESFeatures = generateEntityFeaturesSnippet(
					query, qi.webTotalNS, qi.resultsCount,
					qi.tagToMentionsSA.get(candidate), qi.tagToBoldsSA.get(candidate), qi.tagToRanksSA.get(candidate),
					qi.tagToAdditionalInfosSA.get(candidate), candidate.getConcept(), wikiApi);
			res.add(ESFeatures);

		}
		if (res.isEmpty())
			throw new RuntimeException(wid + " is not a candidate for query " + query);
		return res;
	}

	@Override
	public void checkFeatures(HashMap<String, Double> features) {
		int sourceCount = 0;
		for (String ftrName : new String[] { "is_s1", "is_s2", "is_s3",
				"is_s4", "is_s5", "is_s6" }) {
			if (!features.containsKey(ftrName))
				throw new RuntimeException(
						"All entity sources must be set (one source to 1.0, all others to 0.0)");
			sourceCount += features.get(ftrName);
		}

		if (sourceCount != 1.0)
			throw new RuntimeException(
					"All sources must be set to 0.0, except from one source that must be set to 1.0");

		boolean found = false;
		for (String sourcePrefix : new String[] { "s1_", "s2_", "s3_", "s5_", "s6_" }) {
			int sourceFtrCount = 0;

			for (String ftrName : features.keySet())
				if (ftrName.startsWith(sourcePrefix))
					sourceFtrCount++;
			int baseFeatures = 6;
			if (sourcePrefix.equals("s1_"))
				found = sourceFtrCount == 12
				&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s2_"))
				found = sourceFtrCount == 9
				&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s3_"))
				found = sourceFtrCount == 9
				&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s4_"))
				found = sourceFtrCount == 0
				&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s5_"))
				found = sourceFtrCount == 8
				&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s6_"))
				found = sourceFtrCount == 26
				&& features.size() == sourceFtrCount + baseFeatures;

			if (found)
				return;
		}
		throw new RuntimeException("Incorrect number of features.");
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
	private static HashMap<String, Double> generateEntityFeaturesSearch(
			String query, int wid, int rank, double webTotal,
			double wikiWebTotal, List<Pair<String, Integer>> bingBoldsWS,
			int source, WikipediaApiInterface wikiApi) {
		String sourceName = "s" + source;
		HashMap<String, Double> result = new HashMap<>();
		try {
			result.put(sourceName + "_is_named_entity", ERDDatasetFilter.EntityIsNE(wikiApi, wid)? 1.0:0.0);
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
				SmaphUtils.getMinEditDist(query, SmaphUtils.removeTrailingParenthetical(title)));

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

	private static HashMap<String, Double> generateEntityFeaturesSnippet(String query,
			double webTotal, int resultsCount, List<String> mentions, List<String> bolds,
			List<Integer> ranks, List<HashMap<String, Double>> additionalInfos,
			int wid, WikipediaApiInterface wikiApi) {
		HashMap<String, Double> result = new HashMap<>();
		result.put("is_s1", 0.0);
		result.put("is_s2", 0.0);
		result.put("is_s3", 0.0);
		result.put("is_s4", 0.0);
		result.put("is_s5", 0.0);
		result.put("is_s6", 1.0);
		result.put("s6_webTotal", (double) webTotal);
		result.put("s6_freq",
				SmaphUtils.getFrequency(ranks.size(), resultsCount));
		result.put("s6_avgRank",
				SmaphUtils.computeAvgRank(ranks, resultsCount));

		result.put("s6_pageRank", additionalInfos.get(0).get("pageRank"));

		try {
			result.put("s6_is_named_entity", ERDDatasetFilter.EntityIsNE(wikiApi, wid) ? 1.0 : 0.0);
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

}
