package it.acubelab.smaph.linkback.bindingRegressor;

import it.acubelab.smaph.learn.featurePacks.BindingFeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;

public interface BindingRegressor {
	public double predictScore(BindingFeaturePack features, FeatureNormalizer fn);
}
