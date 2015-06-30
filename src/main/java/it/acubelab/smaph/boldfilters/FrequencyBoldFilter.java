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
import it.acubelab.smaph.SmaphAnnotatorDebugger;
import it.acubelab.smaph.SmaphUtils;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

/**
 * A filter that filters out all bolds that have a frequency lower than the threshold.
 */
public class FrequencyBoldFilter implements BoldFilter {
	private float minSpotFreq;

	public FrequencyBoldFilter(float minSpotFreq) {
		this.minSpotFreq = minSpotFreq;
	}

	@Override
	public List<String> filterBolds(String query,
			List<Pair<String, Integer>> boldAndRanks, int resultsCount) {
		HashMap<String, HashSet<Integer>> positions = SmaphUtils.findPositionsLC(boldAndRanks);

		List<String> spots = new Vector<>();
		for (String spot : positions.keySet())
			if (getFrequency(positions.get(spot).size(), resultsCount) >= this.minSpotFreq) {
				spots.add(spot);
				SmaphAnnotatorDebugger.out.printf("%s -> %d%n", spot, positions.get(spot)
						.size());
			}
		return spots;
	}

	public static double getFrequency(int occurrences, int resultsCount) {
		return (float) occurrences / (float) resultsCount;
	}

	public static double getFrequency(List<Pair<String, Integer>> boldAndRanks,
			String bold, int resultsCount) {
		HashMap<String, HashSet<Integer>> positions = SmaphUtils.findPositionsLC(boldAndRanks);
		return getFrequency(positions.get(bold.toLowerCase()).size(), resultsCount);
	}
}
