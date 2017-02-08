package it.unipi.di.acube.smaph.learn.models;

import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

import java.io.File;
import java.io.IOException;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;

public abstract class LibLinearModel<T> {
	private Model model;

	public LibLinearModel(String modelFile){
		try {
			model = Model.load(new File(modelFile));
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
	public double predictScore(FeaturePack<T> fp, FeatureNormalizer fn) {
		return Linear.predict(model, featureMapToFeatures(fn.ftrToNormalizedFtrArray(fp)));
	}

	public Feature[] featureMapToFeatures(double[] ftrArray) {
		Feature[] llFtrArray = new Feature[ftrArray.length];
		for (int i = 0; i < llFtrArray.length; i++)
			llFtrArray[i] = new FeatureNode(i + 1, ftrArray[i]);
		return llFtrArray;
	}

	public static Feature[] featureMapToFeatures(double[] ftrArray, int[] ftrList) {
		Feature[] llFtrArray = new Feature[ftrList.length];
		int j = 0;
		for (int i = 0; i < ftrList.length; i++)
			llFtrArray[j++] = new FeatureNode(i + 1, ftrArray[ftrList[i] - 1]);
		return llFtrArray;
	}
}
