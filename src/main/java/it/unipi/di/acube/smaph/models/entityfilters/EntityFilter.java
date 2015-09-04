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

package it.unipi.di.acube.smaph.models.entityfilters;

import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

/**
 * An interface to an Entity filter.
 */
public interface EntityFilter {
	/**
	 * @param fp
	 *            not-yet-normalized features of the entity.
	 * @return true iff the entity should be kept.
	 */
	public boolean filterEntity(FeaturePack<Tag> fp, FeatureNormalizer fn);
}
