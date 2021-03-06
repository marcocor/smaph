package it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor;

import java.util.HashSet;
import java.util.List;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.models.LibLinearModel;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

public class LibLinearBindingRegressor extends LibLinearModel<HashSet<Annotation>> implements BindingRegressor {

	public LibLinearBindingRegressor(String modelFile) {
		super(modelFile);
	}

	@Override
	public double[] getScores(List<FeaturePack<HashSet<Annotation>>> features,
			FeatureNormalizer fn) {
		double[] scores = new double[features.size()];
		for (int i = 0; i < features.size(); i++)
			scores[i] =  super.predictScore(features.get(i), fn);
		return scores;
	}
}
