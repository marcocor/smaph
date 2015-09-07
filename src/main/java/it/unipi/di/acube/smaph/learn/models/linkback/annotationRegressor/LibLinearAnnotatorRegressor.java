package it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.smaph.learn.models.LibLinearModel;

public class LibLinearAnnotatorRegressor extends LibLinearModel<Annotation> implements AnnotationRegressor{

	public LibLinearAnnotatorRegressor(String modelFile) {
		super(modelFile);
	}
	
}
