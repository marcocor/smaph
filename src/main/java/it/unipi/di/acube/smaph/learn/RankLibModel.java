package it.unipi.di.acube.smaph.learn;

import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

import java.util.List;

import ciir.umass.edu.learning.DataPoint;
import ciir.umass.edu.learning.DenseDataPoint;
import ciir.umass.edu.learning.Ranker;
import ciir.umass.edu.learning.RankerFactory;
import ciir.umass.edu.utilities.Sorter;

public class RankLibModel <T>{
	Ranker ranker;

	public RankLibModel(String modelFile) {
		RankerFactory rFact = new RankerFactory();
		ranker = rFact.loadRankerFromFile(modelFile);
	}

	public static String ftrVectToString(double[] ftrVect, int rank, int groupid) {
		StringBuilder sb = new StringBuilder();
		sb.append(rank).append(" qid:").append(groupid).append(" ");
		for (int ftr = 0; ftr < ftrVect.length; ftr++){
			sb.append(ftr + 1);
			sb.append(String.format(":%.9f ", ftrVect[ftr]));
		}
		return sb.toString();
	}

	private static DataPoint featuresToDatapointString(double[] features) {
		return new DenseDataPoint(ftrVectToString(features, 1, 1));
	}

	private double[] getScores(List<FeaturePack<T>> features, FeatureNormalizer fn) {
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
	public int[] getRanking(List<FeaturePack<T>> features, FeatureNormalizer fn) {
		return Sorter.sort(getScores(features, fn), false);
	}

	/**
	 * @param features a list of feature packs.
	 * @param fn the feature normalizer.
	 * @return the index of the best feature pack.
	 */
	public int getHighestRank(List<FeaturePack<T>> features, FeatureNormalizer fn) {
		return getRanking(features, fn)[0];
	}

	public double predictScore(FeaturePack<T> featuresArray, FeatureNormalizer fn) {
		return ranker.eval(featuresToDatapointString(fn.ftrToNormalizedFtrArray(featuresArray)));
	}

}