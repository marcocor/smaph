package it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

public interface AnnotationRegressor {
	public <A extends Annotation> double predictScore(FeaturePack<A> fp, FeatureNormalizer fn);	
	public double threshold();	
}
