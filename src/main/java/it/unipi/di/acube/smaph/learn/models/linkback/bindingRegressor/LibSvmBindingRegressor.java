package it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.models.LibSvmModel;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

import java.io.IOException;
import java.util.HashSet;

public class LibSvmBindingRegressor extends LibSvmModel<HashSet<Annotation>> implements BindingRegressor{
	public LibSvmBindingRegressor(String modelFileBase) throws IOException {
		super(modelFileBase + ".model");
	}

	@Override
	public double predictScore(FeaturePack<HashSet<Annotation>> features, FeatureNormalizer fn) {
		return super.predictScore(features, fn);
	}
}
