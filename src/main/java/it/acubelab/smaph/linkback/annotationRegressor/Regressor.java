package it.acubelab.smaph.linkback.annotationRegressor;

import it.acubelab.smaph.learn.featurePacks.FeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;

public interface Regressor {
	public double predictScore(FeaturePack<?> fp, FeatureNormalizer fn);	
}
