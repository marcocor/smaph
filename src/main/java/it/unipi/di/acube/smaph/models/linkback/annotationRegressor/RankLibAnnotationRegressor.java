package it.unipi.di.acube.smaph.models.linkback.annotationRegressor;

import it.unipi.di.acube.smaph.learn.RankLibModel;

public class RankLibAnnotationRegressor extends RankLibModel implements AnnotationRegressor{
	public RankLibAnnotationRegressor(String modelFile) {
		super(modelFile);
	}
}