package it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor;

import java.util.HashSet;
import java.util.List;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

public interface BindingRegressor {
	public double[] getScores(List<FeaturePack<HashSet<Annotation>>> features, FeatureNormalizer fn);
}
