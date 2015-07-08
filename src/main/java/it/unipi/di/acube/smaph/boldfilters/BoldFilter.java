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

package it.unipi.di.acube.smaph.boldfilters;

import it.unipi.di.acube.batframework.utils.Pair;

import java.util.List;

/**
 * An interface to a bold filter.
 */
public interface BoldFilter {
	/**
	 * @param query the query.
	 * @param spotAndRank a list of pairs &lt;b,r&gt;, meaning bold b appeared in result ranked r.
	 * @param resultsCount the number of results returned by the search engine.
	 * @return the list of bolds that should be kept.
	 * 	 */
	public List<String> filterBolds(String query, List<Pair<String, Integer>> spotAndRank, int resultsCount);
}
