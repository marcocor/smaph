package it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor;

import it.unipi.di.acube.smaph.learn.models.RankLibModel;

public class RankLibAnnotationRegressor extends RankLibModel implements AnnotationRegressor{
	public RankLibAnnotationRegressor(String modelFile) {
		super(modelFile);
	}
}