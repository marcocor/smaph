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

package it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.smaph.learn.models.LibSvmModel;

import java.io.*;
import java.net.URL;

import libsvm.svm_model;

/**
 * An SVM-based annotation regressor.
 */
public class LibSvmAnnotationRegressor extends LibSvmModel<Annotation> implements AnnotationRegressor, Serializable {
    private static final long serialVersionUID = 1L;
	private double threshold;
	
	public static LibSvmAnnotationRegressor fromUrl(URL uri) {
		LibSvmAnnotationRegressor obj;
		try {
			ObjectInputStream in = new ObjectInputStream(uri.openStream());
			obj = (LibSvmAnnotationRegressor) in.readObject();
			in.close();
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
		return obj;
	}
	
	public void toFile(File file){
        try {
			ObjectOutputStream out = new ObjectOutputStream(new FileOutputStream(file));
			out.writeObject(this);
			out.close();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	public LibSvmAnnotationRegressor(svm_model model, double threshold) {
		super(model);
		this.threshold = threshold;
	}

	@Override
	public double threshold() {
		return threshold;
	}
}
