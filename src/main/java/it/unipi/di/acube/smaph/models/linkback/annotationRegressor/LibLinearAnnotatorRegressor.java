package it.unipi.di.acube.smaph.models.linkback.annotationRegressor;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.smaph.learn.LibLinearModel;

public class LibLinearAnnotatorRegressor extends LibLinearModel<Annotation> implements AnnotationRegressor{

	public LibLinearAnnotatorRegressor(String modelFile) {
		super(modelFile);
	}
	
}
