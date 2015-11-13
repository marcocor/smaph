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

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;


public class EntityFeaturePack extends FeaturePack<Tag> {
	private static final long serialVersionUID = 1L;

	public EntityFeaturePack(Tag candidate, String query, QueryInformation qi, WikipediaApiInterface wikiApi) {
		super(getFeatures(candidate, query, qi, wikiApi));
	}

	public EntityFeaturePack() {
		super(null);
	}


	public static String[] ftrNames = new String[] {
		"found_s2",
		"found_s3",
		"found_s6",
		"a_editDistanceTitle",
		"a_webTotal",
		"a_wikiWebTotal",
		"a_editDistanceNoPar",
		"a_is_named_entity",
		"s2_rank",
		"s2_editDistanceBolds",
		"s2_capitalizedBolds",
		"s2_avgBoldsWords",
		"s3_rank",
		"s3_editDistanceBolds",
		"s3_capitalizedBolds",
		"s3_avgBoldsWords",
		"s6_freq",
		"s6_avgRank",
		"s6_pageRank",
		"s6_min_rho",
		"s6_max_rho",
		"s6_avg_rho",
		"s6_min_lp",
		"s6_max_lp",
		"s6_avg_lp",
		"s6_min_commonness",
		"s6_max_commonness",
		"s6_avg_commonness",
		"s6_min_ambig",
		"s6_max_ambig",
		"s6_avg_ambig",
		"s6_min_min_bold_ed",
		"s6_max_min_bold_ed",
		"s6_avg_min_bold_ed",
		"s6_min_min_mention_ed",
		"s6_max_min_mention_ed",
		"s6_avg_min_mention_ed",
		"s6_min_mention_bold_overlap",
		"s6_max_mention_bold_overlap",
		"s6_avg_mention_bold_overlap",
	};

	@Override
	public String[] getFeatureNames() {
		return getFeatureNamesStatic();
	}

	private static String[] getFeatureNamesStatic() {
		return ftrNames;
	}

	public static HashMap<String, Double> getFeatures(Tag candidate, String query, QueryInformation qi,
			WikipediaApiInterface wikiApi) {
		int wid = candidate.getConcept();
		boolean candidateIsNE;
		String title;
		try {
			candidateIsNE = ERDDatasetFilter.EntityIsNE(wikiApi, wid);
			title = wikiApi.getTitlebyId(wid);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}

		HashMap<String, Double> res = new HashMap<>();

		// Source-independent features
		res.put("a_editDistanceTitle", SmaphUtils.getMinEditDist(query, title));
		res.put("a_webTotal", qi.webTotalNS);
		res.put("a_wikiWebTotal", qi.webTotalWS);
		res.put("a_editDistanceNoPar", SmaphUtils.getMinEditDist(query, SmaphUtils.removeTrailingParenthetical(title)));
		res.put("a_is_named_entity", candidateIsNE ? 1.0 : 0.0);

		// Normal search (S2) features
		if (qi.includeSourceNormalSearch) {
			res.put("found_s2", 0.0);
			/*res.put("s2_rank", qi.resultsCountNS + 1.0);
			res.put("s2_capitalizedBolds", 0.0);
			res.put("s2_editDistanceBolds", 1.0);
			res.put("s2_avgBoldsWords", 0.0);*/

			if (qi.candidatesNS.contains(candidate)) {
				int rank = qi.idToRankNS.get(wid);
				Triple<Double, Double, Double> EDCapitalizedWordcount = getBoldsEDCapitalizedWordcount(query, rank,
						qi.bingBoldsAndRankNS);
				res.put("found_s2", 1.0);
				res.put("s2_rank", (double) rank);
				res.put("s2_capitalizedBolds", EDCapitalizedWordcount.getLeft());
				res.put("s2_editDistanceBolds", EDCapitalizedWordcount.getMiddle());
				res.put("s2_avgBoldsWords", EDCapitalizedWordcount.getLeft());
			}
		}

		// Wikipedia search (S3) features
		if (qi.includeSourceWikiSearch) {
			res.put("found_s3", 0.0);
/*			res.put("s3_rank", qi.resultsCountWS + 1.0);
			res.put("s3_capitalizedBolds", 0.0);
			res.put("s3_editDistanceBolds", 1.0);
			res.put("s3_avgBoldsWords", 0.0);
*/
			if (qi.candidatesWS.contains(candidate)) {
				int rank = qi.idToRankWS.get(wid);
				Triple<Double, Double, Double> EDCapitalizedWordcount = getBoldsEDCapitalizedWordcount(query, rank,
						qi.bingBoldsAndRankWS);
				res.put("found_s3", 1.0);
				res.put("s3_rank", (double) rank);
				res.put("s3_capitalizedBolds", EDCapitalizedWordcount.getLeft());
				res.put("s3_editDistanceBolds", EDCapitalizedWordcount.getMiddle());
				res.put("s3_avgBoldsWords", EDCapitalizedWordcount.getLeft());
			}
		}

		// Snippet annotation (S6) features
		if (qi.includeSourceSnippets){
			res.put("found_s6", 0.0);
/*			res.put("s6_freq", 0.0);
			res.put("s6_avgRank", 1.0);
			res.put("s6_pageRank", 0.0);
			res.put("s6_min_rho", 0.0);
			res.put("s6_max_rho", 0.0);
			res.put("s6_avg_rho", 0.0);
			res.put("s6_min_lp", 0.0);
			res.put("s6_max_lp", 0.0);
			res.put("s6_avg_lp", 0.0);
			res.put("s6_min_commonness", 0.0);
			res.put("s6_max_commonness", 0.0);
			res.put("s6_avg_commonness", 0.0);
			res.put("s6_min_ambig", 0.0);
			res.put("s6_max_ambig", 0.0);
			res.put("s6_avg_ambig", 0.0);
			res.put("s6_min_min_bold_ed", 1.0);
			res.put("s6_max_min_bold_ed", 1.0);
			res.put("s6_avg_min_bold_ed", 1.0);
			res.put("s6_min_min_mention_ed", 1.0);
			res.put("s6_max_min_mention_ed", 1.0);
			res.put("s6_avg_min_mention_ed", 1.0);
			res.put("s6_min_mention_bold_overlap", 1.0);
			res.put("s6_max_mention_bold_overlap", 1.0);
			res.put("s6_avg_mention_bold_overlap", 1.0);
*/
			if (qi.candidatesSA.contains(candidate)) {
				res.put("found_s6", 1.0);
				List<HashMap<String, Double>> additionalInfos = qi.entityToAdditionalInfosSA.get(candidate);
				double pageRank = additionalInfos.get(0).get("pageRank");
				List<String> mentions = qi.entityToMentionsSA.get(candidate);
				List<String> bolds = qi.entityToBoldsSA.get(candidate);
				List<Integer> ranks = qi.entityToRanksSA.get(candidate);

				res.put("s6_freq", SmaphUtils.getFrequency(ranks.size(), qi.resultsCountNS));
				res.put("s6_avgRank", SmaphUtils.computeAvgRank(ranks, qi.resultsCountNS));
				res.put("s6_pageRank", pageRank);

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
				res.put("s6_min_rho", minMaxAvgRho.getLeft());
				res.put("s6_max_rho", minMaxAvgRho.getMiddle());
				res.put("s6_avg_rho", minMaxAvgRho.getRight());
				Triple<Double, Double, Double> minMaxAvgLp = SmaphUtils.getMinMaxAvg(lps);
				res.put("s6_min_lp", minMaxAvgLp.getLeft());
				res.put("s6_max_lp", minMaxAvgLp.getMiddle());
				res.put("s6_avg_lp", minMaxAvgLp.getRight());
				Triple<Double, Double, Double> minMaxAvgComm = SmaphUtils.getMinMaxAvg(commonness);
				res.put("s6_min_commonness", minMaxAvgComm.getLeft());
				res.put("s6_max_commonness", minMaxAvgComm.getMiddle());
				res.put("s6_avg_commonness", minMaxAvgComm.getRight());
				Triple<Double, Double, Double> minMaxAvgAmbig = SmaphUtils.getMinMaxAvg(ambiguities);
				res.put("s6_min_ambig", minMaxAvgAmbig.getLeft());
				res.put("s6_max_ambig", minMaxAvgAmbig.getMiddle());
				res.put("s6_avg_ambig", minMaxAvgAmbig.getRight());
				Triple<Double, Double, Double> minMaxAvgBoldED = SmaphUtils.getMinMaxAvg(boldEDs);
				res.put("s6_min_min_bold_ed", minMaxAvgBoldED.getLeft());
				res.put("s6_max_min_bold_ed", minMaxAvgBoldED.getMiddle());
				res.put("s6_avg_min_bold_ed", minMaxAvgBoldED.getRight());
				Triple<Double, Double, Double> minMaxAvgMentionED = SmaphUtils.getMinMaxAvg(mentionEDs);
				res.put("s6_min_min_mention_ed", minMaxAvgMentionED.getLeft());
				res.put("s6_max_min_mention_ed", minMaxAvgMentionED.getMiddle());
				res.put("s6_avg_min_mention_ed", minMaxAvgMentionED.getRight());
				Triple<Double, Double, Double> minMaxAvgMentionBoldOverlap = SmaphUtils.getMinMaxAvg(mentionBoldOverlap);
				res.put("s6_min_mention_bold_overlap", minMaxAvgMentionBoldOverlap.getLeft());
				res.put("s6_max_mention_bold_overlap", minMaxAvgMentionBoldOverlap.getMiddle());
				res.put("s6_avg_mention_bold_overlap", minMaxAvgMentionBoldOverlap.getRight());

			}
		}
		if (res.isEmpty())
			throw new RuntimeException(wid + " is not a candidate for query " + query);
		return res;
	}

	@Override
	public void checkFeatures(HashMap<String, Double> features) {
		long ftrCount = 0;
		String[] sources = new String[] { "s2", "s3", "s6" };
		for (String source : sources)
			if (features.containsKey("found_" + source)) {
				long count = features.keySet().stream().filter(fn -> fn.startsWith(source + "_")).count();
				if ((source.equals("s2") || source.equals("s3")) && count != 4 && count != 0)
					throw new RuntimeException(String.format("Sources 2 and 3 must have 0 or 4 fetures set. %d found.", count));
				if (source.equals("s6") && count != 24 && count != 0)
					throw new RuntimeException(String.format("Source 6 must have 0 or 24 fetures set. %d found.", count));
				ftrCount += count;
				ftrCount ++;
			}

		long indipCount = features.keySet().stream().filter(fn -> fn.startsWith("a_")).count();
		if (indipCount != 5)
			throw new RuntimeException(String.format("Source-independent features must be 5. %d found.", indipCount));
		ftrCount += indipCount;

		if (ftrCount != features.size())
			throw new RuntimeException("Unrecognized additional features.");
	}

	private static Triple<Double, Double, Double> getBoldsEDCapitalizedWordcount(String query, int rank, List<Pair<String, Integer>> bingBolds){
		double minEdDist = 1.0;
		double capitalized = 0;
		double avgNumWords = 0;
		int boldsCount = 0;
		for (Pair<String, Integer> p : bingBolds)
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
		return new ImmutableTriple<>(minEdDist, capitalized, avgNumWords);
	}
}
