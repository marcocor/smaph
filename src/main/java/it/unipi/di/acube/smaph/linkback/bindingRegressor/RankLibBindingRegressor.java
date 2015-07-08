package it.unipi.di.acube.smaph.linkback.bindingRegressor;

import it.unipi.di.acube.smaph.learn.RankLibRanker;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

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
