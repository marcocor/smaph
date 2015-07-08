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

package it.unipi.di.acube.smaph.main;

import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.batframework.utils.ProblemReduction;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.boldfilters.EditDistanceBoldFilter;
import it.unipi.di.acube.smaph.boldfilters.FrequencyBoldFilter;
import it.unipi.di.acube.smaph.entityfilters.LibSvmEntityFilter;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.ScaleFeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.DummyLinkBack;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;

public class MainTest {

	public static void main(String[] args) throws FileNotFoundException,
			ClassNotFoundException, IOException {

		SmaphConfig.setConfigFile("smaph-config.xml");
		String bingKey = SmaphConfig.getDefaultBingKey();
		
		WATAnnotator wikiSense = new WATAnnotator("wikisense.mkapp.it", 80,
				"base", "", "mw", "0.8", "0", false, false, false);

		WikipediaApiInterface wikiApi = new WikipediaApiInterface(
				"/tmp/wid.cache", "/tmp/redirect.cache");

		// BingAnnotator bing = new BingAnnotator(wikiSense, new
		// RankWeightSpotFilter(0.9f));
		String modelBase = "models/model_1-3,6-7,9-25,33-37_EF_3.80000_5.20000_0.060_0.01000000_100.00000000_ANW-erd";
		SmaphAnnotator bing = new SmaphAnnotator(wikiSense,
				new FrequencyBoldFilter(0.06f), new LibSvmEntityFilter(
						modelBase+".model"), new ZScoreFeatureNormalizer(modelBase+".zscore", new EntityFeaturePack(null)), new DummyLinkBack(), true, true, true, 10,
				false, 0, true, 10, false, 0, false, null, null, wikiApi, bingKey);
		String q1 = "errors in mathematics calculas";
		String q2 = "ammstrong moon landing";
		String q3 = "bill gates";
		String q4 = "berkeley square mini series";
		HashSet<ScoredAnnotation> sa2wSolution = bing.solveSa2W(q4);
		HashSet<Tag> tags = ProblemReduction.A2WToC2W(ProblemReduction
				.Sa2WToA2W(sa2wSolution, -1));
		System.out.println("total tags:" + tags.size());
		for (Tag t : tags)
			System.out.println(wikiApi.getTitlebyId(t.getConcept()));

	}

}
