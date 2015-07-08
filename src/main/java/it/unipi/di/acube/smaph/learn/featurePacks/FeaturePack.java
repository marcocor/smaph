package it.unipi.di.acube.smaph.learn.featurePacks;

import java.io.Serializable;
import java.util.Arrays;
import java.util.HashMap;

public abstract class FeaturePack <T extends Object> implements Serializable {

	private static final long serialVersionUID = 1L;
	private double[] featuresArray;

	public FeaturePack(HashMap<String, Double> features){
		if (features == null)
			return;
		checkFeatures(features);
		featuresArray = new double[this.getFeatureCount()];
		
		for (String ftrName : getFeatureNames())
			featuresArray[ftrNameToArrayPosition(ftrName)] = features
					.containsKey(ftrName) ? features.get(ftrName) : Double.NaN;
	}
	
	public abstract void checkFeatures(HashMap<String, Double> features);
	
	/**
	 * @return the array of feature names, ordered by their ID. In name of the
	 *         feature with ID=i+1 is in position i.
	 */
	public abstract String[] getFeatureNames();

	/**
	 * @param ftrName
	 *            the feature name
	 * @return the feature index (an integer >=1) of a feature, or -1 if such a
	 *         feature does not exist.
	 */
	public int ftrNameToId(String ftrName) {
		int idx = Arrays.asList(getFeatureNames()).indexOf(ftrName);
		if (idx == -1)
			return -1;
		return idx + 1;
	}

	public int ftrNameToArrayPosition(String ftrName) {
		return ftrNameToId(ftrName) -1;
	}

	public double getFeature(String ftrName) {
		return featuresArray[ftrNameToArrayPosition(ftrName)];
	}

	public String ftrIdToName(int featureId) {
		return getFeatureNames()[featureId - 1];
	}
	public int getFeatureCount() {
		return getFeatureNames().length;
	}

	public boolean featureIsSet(String ftrName) {
		return !Double.isNaN(featuresArray[ftrNameToArrayPosition(ftrName)]);
	}
}
