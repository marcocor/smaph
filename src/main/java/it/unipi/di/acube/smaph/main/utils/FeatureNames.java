package it.unipi.di.acube.smaph.main.utils;

import it.unipi.di.acube.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;

public class FeatureNames {

	public static void main(String[] args) {
		
		String[] entityFeatures = EntityFeaturePack.ftrNames;
		String[] annotationFeatures = AdvancedAnnotationFeaturePack.getFeatureNamesStatic();
		String[] bindingFeatures = BindingFeaturePack.getFeatureNamesStatic();
		System.out.println("Entity features:");
		printFeatureNames(entityFeatures);
		System.out.println("Annotation features:");
		printFeatureNames(annotationFeatures);
		System.out.println("Binding features:");
		printFeatureNames(bindingFeatures);
	}
	
	public static void printFeatureNames(String[] features){
		for (int i=0; i<features.length; i++)
			System.out.printf("%d %s%n", i + 1, features[i]);
	}

}
