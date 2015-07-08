package it.unipi.di.acube.smaph.linkback.bindingRegressor;

import it.unipi.di.acube.smaph.learn.LibSvmModel;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

import java.io.IOException;

public class LibSvmBindingRegressor extends LibSvmModel implements BindingRegressor{
	public LibSvmBindingRegressor(String modelFileBase) throws IOException {
		super(modelFileBase + ".model");
	}

	@Override
	public double predictScore(BindingFeaturePack features, FeatureNormalizer fn) {
		return super.predictScore(features, fn);
	}
}
