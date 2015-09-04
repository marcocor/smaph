package it.unipi.di.acube.smaph.models.linkback.bindingRegressor;

import java.util.HashSet;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.smaph.learn.LibLinearModel;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

public class LibLinearBindingRegressor extends LibLinearModel<HashSet<Annotation>> implements BindingRegressor {

	public LibLinearBindingRegressor(String modelFile) {
		super(modelFile);
	}

	@Override
	public double predictScore(FeaturePack<HashSet<Annotation>> features,
			FeatureNormalizer fn) {
		return super.predictScore(features, fn);
	}

}
