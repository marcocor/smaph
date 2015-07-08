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
import it.unipi.di.acube.smaph.SmaphAnnotatorDebugger;
import it.unipi.di.acube.smaph.SmaphUtils;

import java.util.HashSet;
import java.util.List;
import java.util.Vector;

/**
 * A filter that filters out all bolds that have an edit distance higher than the threshold.
 */
public class EditDistanceBoldFilter implements BoldFilter {
	private double threshold;

	public EditDistanceBoldFilter(double threshold) {
		this.threshold = threshold;
	}

	@Override
	public List<String> filterBolds(String query,
			List<Pair<String, Integer>> spotAndRanks, int resultsCount) {
		List<String> res = new Vector<>();
		HashSet<String> seen = new HashSet<>();
		SmaphAnnotatorDebugger.out.println("*** Filtering Bolds ***");
		for (Pair<String, Integer> spotAndRank : spotAndRanks) {
			String bold = spotAndRank.first.toLowerCase();
			if (seen.contains(bold))
				continue;
			seen.add(bold);
			double minDist = SmaphUtils
					.getMinEditDist(query, bold);
			boolean accept = minDist < threshold;
			if (accept)
				res.add(bold);
			SmaphAnnotatorDebugger.out.printf("Min edit distance: %f (%s)%n",
					minDist, accept ? "accept" : "discard");

		}
		return res;
	}

}
