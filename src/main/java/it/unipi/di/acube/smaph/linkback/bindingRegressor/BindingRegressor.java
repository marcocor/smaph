package it.unipi.di.acube.smaph.linkback.bindingRegressor;

import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

public interface BindingRegressor {
	public double predictScore(BindingFeaturePack features, FeatureNormalizer fn);
}
