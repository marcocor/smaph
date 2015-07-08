package it.unipi.di.acube.smaph.linkback.annotationRegressor;

import it.unipi.di.acube.smaph.learn.LibLinearModel;

public class LibLinearRegressor extends LibLinearModel implements Regressor{

	public LibLinearRegressor(String modelFile) {
		super(modelFile);
	}
	
}
