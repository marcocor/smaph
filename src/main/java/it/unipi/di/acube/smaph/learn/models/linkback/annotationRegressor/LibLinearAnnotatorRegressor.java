package it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.smaph.learn.models.LibLinearModel;

public class LibLinearAnnotatorRegressor extends LibLinearModel<Annotation> implements AnnotationRegressor {
	double threshold;

	public LibLinearAnnotatorRegressor(String modelFile, double threshold) {
		super(modelFile);
		this.threshold = threshold;
	}

	@Override
	public double threshold() {
		return threshold;
	}
}
