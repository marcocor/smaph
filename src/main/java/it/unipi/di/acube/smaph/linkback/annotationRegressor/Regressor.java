package it.unipi.di.acube.smaph.linkback.annotationRegressor;

import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

public interface Regressor {
	public double predictScore(FeaturePack<?> fp, FeatureNormalizer fn);	
}
