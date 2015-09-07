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

import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

import java.io.IOException;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;

/**
 * A wrapper for a LibSvm model.
 * 
 * @author Marco Cornolti
 *
 */
public abstract class LibSvmModel <T> {
	private svm_model model;
	String modelFile;
	
	public static svm_node[] featuresArrayToNode(double[] ftrArray, int[] pickedFtrsI) {
		svm_node[] ftrVect = new svm_node[pickedFtrsI.length];
		for (int i=0; i<pickedFtrsI.length; i++) {
			int ftrId = pickedFtrsI[i];
			ftrVect[i] = new svm_node();
			ftrVect[i].index = ftrId;
			ftrVect[i].value = ftrArray[ftrId-1];
		}
		return ftrVect;
	}

	public LibSvmModel(String modelFile) throws IOException {
		setModel(modelFile);
	}

	public LibSvmModel(svm_model model) {
		this.model = model;
	}

	public boolean predict(FeaturePack<T> fp, FeatureNormalizer fn) {
		return predictScore(fp, fn) > 0.0;
	}

	private int[] getUsedFtr(){
		svm_node[] firstSv = model.SV[0];
		int[] res = new int[firstSv.length];
		for (int i=0 ; i<res.length; i++)
			res[i] = firstSv[i].index;
		return res;
	}
	
	public double predictScore(FeaturePack<T> fp, FeatureNormalizer fn) {
		svm_node[] ftrVect = featuresArrayToNode(fn
				.ftrToNormalizedFtrArray(fp), getUsedFtr());
		return svm.svm_predict(model, ftrVect);
	}

	public String getModel() {
		return modelFile;
	}

	public void setModel(String modelFile) {
		this.modelFile = modelFile;
		try {
			this.model = svm.svm_load_model(modelFile);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}
}
