package it.unipi.di.acube.smaph.main.utils;

import it.unipi.di.acube.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.GreedyFeaturePack;

public class FeatureNames {

	public static void main(String[] args) {
		
		String[] entityFeatures = EntityFeaturePack.ftrNames;
		String[] annotationFeatures = AnnotationFeaturePack.getFeatureNamesStatic();
		String[] bindingFeatures = BindingFeaturePack.getFeatureNamesStatic();
		String[] greedyFeatures = GreedyFeaturePack.getFeatureNamesStatic();
		System.out.println("Entity features:");
		printFeatureNames(entityFeatures);
		System.out.println("Annotation features:");
		printFeatureNames(annotationFeatures);
		System.out.println("Binding features:");
		printFeatureNames(bindingFeatures);
		System.out.println("Greedy features:");
		printFeatureNames(greedyFeatures);
	}
	
	public static void printFeatureNames(String[] features){
		for (int i=0; i<features.length; i++)
			System.out.printf("%d %s%n", i + 1, features[i]);
	}

}
