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

package it.acubelab.smaph.boldfilters;

import it.acubelab.batframework.utils.Pair;

import java.util.*;

/**
 * A filter that does nothing (accept all bolds).
 */
public class NoBoldFilter implements BoldFilter {

	@Override
	public List<String> filterBolds(String query,
			List<Pair<String, Integer>> spotAndRanks, int resultsCount) {
		HashSet<String> filteredSpots = new HashSet<>();
		for (Pair<String, Integer> spotAndRank : spotAndRanks)
			filteredSpots.add(spotAndRank.first);
		return new ArrayList<String>(filteredSpots);
	}

}
