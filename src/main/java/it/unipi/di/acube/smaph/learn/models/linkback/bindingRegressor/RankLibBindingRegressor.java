package it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor;

import java.io.IOException;
import java.net.URL;
import java.util.HashSet;
import java.util.List;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.models.RankLibModel;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

public class RankLibBindingRegressor extends RankLibModel<HashSet<Annotation>> implements BindingRegressor {

	private RankLibBindingRegressor(URL modelUrl) throws IOException {
		super(modelUrl);
	}

	public static RankLibBindingRegressor fromUrl(URL modelUrl) throws IOException {
		return new RankLibBindingRegressor(modelUrl);
	}

	@Override
	public double[] getScores(List<FeaturePack<HashSet<Annotation>>> features, FeatureNormalizer fn) {
		double[] scores = new double[features.size()];
		for (int i = 0; i < features.size(); i++) {
			scores[i] = super.predictScore(features.get(i), fn);
		}
		return scores;
	}
}
