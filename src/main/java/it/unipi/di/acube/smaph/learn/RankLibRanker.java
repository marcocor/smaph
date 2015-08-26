package it.unipi.di.acube.smaph.learn;

import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

import java.util.List;

import ciir.umass.edu.learning.DataPoint;
import ciir.umass.edu.learning.RankList;
import ciir.umass.edu.learning.Ranker;
import ciir.umass.edu.learning.RankerFactory;
import ciir.umass.edu.utilities.Sorter;

public class RankLibRanker{
	Ranker ranker;

	public RankLibRanker(String modelFile) {
		RankerFactory rFact = new RankerFactory();
		ranker = rFact.loadRanker(modelFile);
	}

	private static DataPoint featuresToDatapointString(double[] features) {
		String str = String.format("1 qid:1 ");
		for (int i = 0; i < features.length; i++) {
			str += String.format("%d:%.9f", i + 1, features[i]);
			if (i != features.length - 1)
				str += " ";
		}
		return new DataPoint(str);
	}

	private double[] getScores(List<FeaturePack> features, FeatureNormalizer fn) {
		double[] scores = new double[features.size()];
		for (int j = 0; j < features.size(); j++)
			scores[j] = ranker.eval(featuresToDatapointString(fn.ftrToNormalizedFtrArray(features.get(j))));
		return scores;
	}

	/**
	 * @param features a list of feature packs.
	 * @param fn the feature normalizer.
	 * @return indexes of the feature list, from best to least rank.
	 */
	public int[] getRanking(List<FeaturePack> features, FeatureNormalizer fn) {
		return Sorter.sort(getScores(features, fn), false);
	}

	/**
	 * @param features a list of feature packs.
	 * @param fn the feature normalizer.
	 * @return the index of the best feature pack.
	 */
	public int getHighestRank(List<FeaturePack> features, FeatureNormalizer fn) {
		return getRanking(features, fn)[0];
	}

	public double predictScore(FeaturePack featuresArray, FeatureNormalizer fn) {
		return ranker.eval(featuresToDatapointString(fn.ftrToNormalizedFtrArray(featuresArray)));
	}

}