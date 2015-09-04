package it.unipi.di.acube.smaph.models.linkback.bindingRegressor;

import java.util.HashSet;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.smaph.learn.RankLibModel;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

public class RankLibBindingRegressor extends RankLibModel<HashSet<Annotation>> implements
		BindingRegressor {

	public RankLibBindingRegressor(String modelFile) {
		super(modelFile);
	}

	@Override
	public double predictScore(FeaturePack<HashSet<Annotation>> features, FeatureNormalizer fn) {
		return super.predictScore(features, fn);
	}
}
