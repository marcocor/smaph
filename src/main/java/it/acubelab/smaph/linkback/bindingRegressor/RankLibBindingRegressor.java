package it.acubelab.smaph.linkback.bindingRegressor;

import it.acubelab.smaph.learn.RankLibRanker;
import it.acubelab.smaph.learn.featurePacks.BindingFeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;

public class RankLibBindingRegressor extends RankLibRanker implements
		BindingRegressor {

	public RankLibBindingRegressor(String modelFile) {
		super(modelFile);
	}

	@Override
	public double predictScore(BindingFeaturePack features, FeatureNormalizer fn) {
		return super.predictScore(features, fn);
	}
}
