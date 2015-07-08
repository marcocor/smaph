package it.unipi.di.acube.smaph.learn.normalizer;

import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;

public abstract class FeatureNormalizer {

	/**
	 * @param fp
	 *            a feature pack
	 * @param ftrName
	 *            a feature name
	 * @return the normalized value for this feature.
	 */
	public abstract double normalizeFeature(FeaturePack<?> fp, String ftrName);

	/**
	 * @param features
	 *            the hashmap of non-normalized features.
	 * @return a feature vector of the given features (normalized), 0.0 in case
	 *         a feature is not provided.
	 */
	public double[] ftrToNormalizedFtrArray(FeaturePack<?> fp) {
		double[] res = new double[fp.getFeatureCount()];
		for (String featureName : fp.getFeatureNames()) {
			int ftrPos = fp.ftrNameToArrayPosition(featureName);
			res[ftrPos] = normalizeFeature(fp, featureName);
		}
		return res;
	}
}
