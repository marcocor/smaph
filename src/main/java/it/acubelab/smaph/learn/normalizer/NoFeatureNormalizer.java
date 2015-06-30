package it.acubelab.smaph.learn.normalizer;

import it.acubelab.smaph.learn.featurePacks.FeaturePack;

public class NoFeatureNormalizer extends FeatureNormalizer {

	@Override
	public double normalizeFeature(FeaturePack fp, String ftrName) {
		if (!fp.featureIsSet(ftrName))
			return 0.0;
		return fp.getFeature(ftrName);
	}

}
