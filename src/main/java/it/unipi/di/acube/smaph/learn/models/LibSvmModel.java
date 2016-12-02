/**
 *  Copyright 2014 Marco Cornolti
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package it.unipi.di.acube.smaph.learn.models;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URL;

import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;

/**
 * A wrapper for a LibSvm model.
 * 
 * @author Marco Cornolti
 *
 */
public abstract class LibSvmModel<T> implements Serializable {
	private static final long serialVersionUID = 1L;
	private svm_model model;
	private URL modelURL;

	public static svm_node[] featuresArrayToNode(double[] ftrArray, int[] pickedFtrsI) {
		svm_node[] ftrVect = new svm_node[pickedFtrsI.length];
		for (int i = 0; i < pickedFtrsI.length; i++) {
			int ftrId = pickedFtrsI[i];
			ftrVect[i] = new svm_node();
			ftrVect[i].index = ftrId;
			ftrVect[i].value = ftrArray[ftrId - 1];
		}
		return ftrVect;
	}

	public LibSvmModel(URL modelFile) throws IOException {
		setModel(modelFile);
	}

	public LibSvmModel(svm_model model) {
		this.model = model;
	}

	public boolean predict(FeaturePack<T> fp, FeatureNormalizer fn) {
		return predictScore(fp, fn) > 0.0;
	}

	private int[] getUsedFtr() {
		svm_node[] firstSv = model.SV[0];
		int[] res = new int[firstSv.length];
		for (int i = 0; i < res.length; i++)
			res[i] = firstSv[i].index;
		return res;
	}

	public double predictScore(FeaturePack<T> fp, FeatureNormalizer fn) {
		svm_node[] ftrVect = featuresArrayToNode(fn.ftrToNormalizedFtrArray(fp), getUsedFtr());
		return svm.svm_predict(model, ftrVect);
	}

	public String getModel() {
		return modelURL.toString();
	}

	public void setModel(URL modelFile) {
		this.modelURL = modelFile;
		try {
			this.model = svm.svm_load_model(new BufferedReader(new InputStreamReader(modelURL.openStream(), "ascii")));
			if (this.model == null)
				throw new RuntimeException("Could not load model file.");
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
