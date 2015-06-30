package it.acubelab.smaph.linkback.bindingRegressor;

import it.acubelab.smaph.learn.LibSvmModel;
import it.acubelab.smaph.learn.featurePacks.BindingFeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;

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
