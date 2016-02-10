package it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor;

import it.unipi.di.acube.smaph.learn.models.RankLibModel;

public class RankLibAnnotationRegressor extends RankLibModel implements AnnotationRegressor{
	private double threshold;
	
	public RankLibAnnotationRegressor(String modelFile, double threshold) {
		super(modelFile);
		this.threshold = threshold;
	}

	@Override
	public double threshold() {
		return threshold;
	}
}