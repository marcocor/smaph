package it.acubelab.smaph.linkback.bindingRegressor;

import it.acubelab.smaph.learn.LibLinearModel;
import it.acubelab.smaph.learn.featurePacks.BindingFeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;

public class LibLinearBindingRegressor extends LibLinearModel implements BindingRegressor {

	public LibLinearBindingRegressor(String modelFile) {
		super(modelFile);
	}

	@Override
	public double predictScore(BindingFeaturePack features, FeatureNormalizer fn) {
		return super.predictScore(features, fn);
	}

}
