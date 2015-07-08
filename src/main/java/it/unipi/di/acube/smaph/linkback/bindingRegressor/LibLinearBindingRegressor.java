package it.unipi.di.acube.smaph.linkback.bindingRegressor;

import it.unipi.di.acube.smaph.learn.LibLinearModel;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

public class LibLinearBindingRegressor extends LibLinearModel implements BindingRegressor {

	public LibLinearBindingRegressor(String modelFile) {
		super(modelFile);
	}

	@Override
	public double predictScore(BindingFeaturePack features, FeatureNormalizer fn) {
		return super.predictScore(features, fn);
	}

}
