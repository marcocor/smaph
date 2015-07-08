package it.unipi.di.acube.smaph.linkback.annotationRegressor;

import it.unipi.di.acube.smaph.learn.RankLibRanker;

public class RankLibAnnotationRegressor extends RankLibRanker implements Regressor{
	public RankLibAnnotationRegressor(String modelFile) {
		super(modelFile);
	}
}