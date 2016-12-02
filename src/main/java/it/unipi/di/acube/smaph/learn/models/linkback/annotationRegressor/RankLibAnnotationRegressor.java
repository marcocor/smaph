package it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor;

import java.io.IOException;
import java.net.URL;

import it.unipi.di.acube.smaph.learn.models.RankLibModel;

public class RankLibAnnotationRegressor extends RankLibModel implements AnnotationRegressor {
	private double threshold;

	public RankLibAnnotationRegressor(URL modelUrl, double threshold) throws IOException {
		super(modelUrl);
		this.threshold = threshold;
	}

	@Override
	public double threshold() {
		return threshold;
	}
}