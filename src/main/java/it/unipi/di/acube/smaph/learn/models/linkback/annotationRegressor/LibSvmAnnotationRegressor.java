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

import java.io.IOException;

import libsvm.svm_model;

/**
 * An SVM-based annotation regressor.
 */
public class LibSvmAnnotationRegressor extends LibSvmModel<Annotation> implements AnnotationRegressor {
	
	public LibSvmAnnotationRegressor(String modelFile) throws IOException {
		super(modelFile);
	}

	public LibSvmAnnotationRegressor(svm_model model) {
		super(model);
	}
}
