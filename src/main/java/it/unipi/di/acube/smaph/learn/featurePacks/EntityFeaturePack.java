package it.unipi.di.acube.smaph.learn.featurePacks;

import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;
import it.unipi.di.acube.smaph.main.ERDDatasetFilter;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;


public class EntityFeaturePack extends FeaturePack<Tag> {
	private static final long serialVersionUID = 1L;

	public EntityFeaturePack(Tag candidate, String query, QueryInformation qi, WikipediaInterface wikiApi,
	        WikipediaToFreebase freeb) {
		super(getFeatures(candidate, query, qi, wikiApi, freeb));
	}

	public EntityFeaturePack() {
		super(null);
	}


	public static String[] ftrNames = new String[] {
		"found_s1",
		"found_s2",
		"found_s3",
		"a_editDistanceTitle",
		"a_webTotal",
		"a_wikiWebTotal",
		"a_editDistanceNoPar",
		"a_is_named_entity",
		"s1_rank",
		"s1_editDistanceBolds",
		"s1_capitalizedBolds",
		"s1_avgBoldsWords",
		"s2_rank",
		"s2_editDistanceBolds",
		"s2_capitalizedBolds",
		"s2_avgBoldsWords",
		"s3_freq",
		"s3_avgRank",
		"s3_pageRank",
		"s3_min_rho",
		"s3_max_rho",
		"s3_avg_rho",
		"s3_min_lp",
		"s3_max_lp",
		"s3_avg_lp",
		"s3_min_commonness",
		"s3_max_commonness",
		"s3_avg_commonness",
		"s3_min_ambig",
		"s3_max_ambig",
		"s3_avg_ambig",
		"s3_min_min_bold_ed",
		"s3_max_min_bold_ed",
		"s3_avg_min_bold_ed",
		"s3_min_min_mention_ed",
		"s3_max_min_mention_ed",
		"s3_avg_min_mention_ed",
		"s3_min_mention_bold_overlap",
		"s3_max_mention_bold_overlap",
		"s3_avg_mention_bold_overlap",
	};

	@Override
	public String[] getFeatureNames() {
		return getFeatureNamesStatic();
	}

	private static String[] getFeatureNamesStatic() {
		return ftrNames;
	}

	public static HashMap<String, Double> getFeatures(Tag candidate, String query, QueryInformation qi,
			WikipediaInterface wikiApi, WikipediaToFreebase freeb) {
		int wid = candidate.getConcept();
		boolean candidateIsNE;
		String title;
		try {
			candidateIsNE = ERDDatasetFilter.entityIsNE(wikiApi, freeb, wid);
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
			res.put("found_s1", 0.0);
			/*res.put("s1_rank", qi.resultsCountNS + 1.0);
			res.put("s1_capitalizedBolds", 0.0);
			res.put("s1_editDistanceBolds", 1.0);
			res.put("s1_avgBoldsWords", 0.0);*/

			if (qi.candidatesNS.contains(candidate)) {
				int rank = qi.idToRankNS.get(wid);
				Triple<Double, Double, Double> EDCapitalizedWordcount = getBoldsEDCapitalizedWordcount(query, rank,
						qi.boldsAndRankNS);
				res.put("found_s1", 1.0);
				res.put("s1_rank", (double) rank);
				res.put("s1_capitalizedBolds", EDCapitalizedWordcount.getLeft());
				res.put("s1_editDistanceBolds", EDCapitalizedWordcount.getMiddle());
				res.put("s1_avgBoldsWords", EDCapitalizedWordcount.getLeft());
			}
		}

		// Wikipedia search (S3) features
		if (qi.includeSourceWikiSearch) {
			res.put("found_s2", 0.0);
/*			res.put("s2_rank", qi.resultsCountWS + 1.0);
			res.put("s2_capitalizedBolds", 0.0);
			res.put("s2_editDistanceBolds", 1.0);
			res.put("s2_avgBoldsWords", 0.0);
*/
			if (qi.candidatesWS.contains(candidate)) {
				int rank = qi.idToRankWS.get(wid);
				Triple<Double, Double, Double> EDCapitalizedWordcount = getBoldsEDCapitalizedWordcount(query, rank,
						qi.boldsAndRankWS);
				res.put("found_s2", 1.0);
				res.put("s2_rank", (double) rank);
				res.put("s2_capitalizedBolds", EDCapitalizedWordcount.getLeft());
				res.put("s2_editDistanceBolds", EDCapitalizedWordcount.getMiddle());
				res.put("s2_avgBoldsWords", EDCapitalizedWordcount.getLeft());
			}
		}

		// Snippet annotation (S3) features
		if (qi.includeSourceSnippets){
			res.put("found_s3", 0.0);
/*			res.put("s3_freq", 0.0);
			res.put("s3_avgRank", 1.0);
			res.put("s3_pageRank", 0.0);
			res.put("s3_min_rho", 0.0);
			res.put("s3_max_rho", 0.0);
			res.put("s3_avg_rho", 0.0);
			res.put("s3_min_lp", 0.0);
			res.put("s3_max_lp", 0.0);
			res.put("s3_avg_lp", 0.0);
			res.put("s3_min_commonness", 0.0);
			res.put("s3_max_commonness", 0.0);
			res.put("s3_avg_commonness", 0.0);
			res.put("s3_min_ambig", 0.0);
			res.put("s3_max_ambig", 0.0);
			res.put("s3_avg_ambig", 0.0);
			res.put("s3_min_min_bold_ed", 1.0);
			res.put("s3_max_min_bold_ed", 1.0);
			res.put("s3_avg_min_bold_ed", 1.0);
			res.put("s3_min_min_mention_ed", 1.0);
			res.put("s3_max_min_mention_ed", 1.0);
			res.put("s3_avg_min_mention_ed", 1.0);
			res.put("s3_min_mention_bold_overlap", 1.0);
			res.put("s3_max_mention_bold_overlap", 1.0);
			res.put("s3_avg_mention_bold_overlap", 1.0);
*/
			if (qi.candidatesSA.contains(candidate)) {
				res.put("found_s3", 1.0);
				List<HashMap<String, Double>> additionalInfos = qi.entityToAdditionalInfosSA.get(candidate);
				double pageRank = additionalInfos.get(0).get("pageRank");
				List<String> mentions = qi.entityToMentionsSA.get(candidate);
				List<String> bolds = qi.entityToBoldsSA.get(candidate);
				List<Integer> ranks = qi.entityToRanksSA.get(candidate);

				res.put("s3_freq", SmaphUtils.getFrequency(ranks.size(), qi.resultsCountNS));
				res.put("s3_avgRank", SmaphUtils.computeAvgRank(ranks, qi.resultsCountNS));
				res.put("s3_pageRank", pageRank);

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
				res.put("s3_min_rho", minMaxAvgRho.getLeft());
				res.put("s3_max_rho", minMaxAvgRho.getMiddle());
				res.put("s3_avg_rho", minMaxAvgRho.getRight());
				Triple<Double, Double, Double> minMaxAvgLp = SmaphUtils.getMinMaxAvg(lps);
				res.put("s3_min_lp", minMaxAvgLp.getLeft());
				res.put("s3_max_lp", minMaxAvgLp.getMiddle());
				res.put("s3_avg_lp", minMaxAvgLp.getRight());
				Triple<Double, Double, Double> minMaxAvgComm = SmaphUtils.getMinMaxAvg(commonness);
				res.put("s3_min_commonness", minMaxAvgComm.getLeft());
				res.put("s3_max_commonness", minMaxAvgComm.getMiddle());
				res.put("s3_avg_commonness", minMaxAvgComm.getRight());
				Triple<Double, Double, Double> minMaxAvgAmbig = SmaphUtils.getMinMaxAvg(ambiguities);
				res.put("s3_min_ambig", minMaxAvgAmbig.getLeft());
				res.put("s3_max_ambig", minMaxAvgAmbig.getMiddle());
				res.put("s3_avg_ambig", minMaxAvgAmbig.getRight());
				Triple<Double, Double, Double> minMaxAvgBoldED = SmaphUtils.getMinMaxAvg(boldEDs);
				res.put("s3_min_min_bold_ed", minMaxAvgBoldED.getLeft());
				res.put("s3_max_min_bold_ed", minMaxAvgBoldED.getMiddle());
				res.put("s3_avg_min_bold_ed", minMaxAvgBoldED.getRight());
				Triple<Double, Double, Double> minMaxAvgMentionED = SmaphUtils.getMinMaxAvg(mentionEDs);
				res.put("s3_min_min_mention_ed", minMaxAvgMentionED.getLeft());
				res.put("s3_max_min_mention_ed", minMaxAvgMentionED.getMiddle());
				res.put("s3_avg_min_mention_ed", minMaxAvgMentionED.getRight());
				Triple<Double, Double, Double> minMaxAvgMentionBoldOverlap = SmaphUtils.getMinMaxAvg(mentionBoldOverlap);
				res.put("s3_min_mention_bold_overlap", minMaxAvgMentionBoldOverlap.getLeft());
				res.put("s3_max_mention_bold_overlap", minMaxAvgMentionBoldOverlap.getMiddle());
				res.put("s3_avg_mention_bold_overlap", minMaxAvgMentionBoldOverlap.getRight());

			}
		}
		if (res.isEmpty())
			throw new RuntimeException(wid + " is not a candidate for query " + query);
		return res;
	}

	@Override
	public void checkFeatures(HashMap<String, Double> features) {
		long ftrCount = 0;
		String[] sources = new String[] { "s1", "s2", "s3" };
		for (String source : sources)
			if (features.containsKey("found_" + source)) {
				long count = features.keySet().stream().filter(fn -> fn.startsWith(source + "_")).count();
				if ((source.equals("s1") || source.equals("s2")) && count != 4 && count != 0)
					throw new RuntimeException(String.format("Sources 1 and 2 must have 0 or 4 fetures set. %d found.", count));
				if (source.equals("s3") && count != 24 && count != 0)
					throw new RuntimeException(String.format("Source 3 must have 0 or 24 fetures set. %d found.", count));
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

	private static Triple<Double, Double, Double> getBoldsEDCapitalizedWordcount(String query, int rank, List<Pair<String, Integer>> websearchBolds){
		double minEdDist = 1.0;
		double capitalized = 0;
		double avgNumWords = 0;
		int boldsCount = 0;
		for (Pair<String, Integer> p : websearchBolds)
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
