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

import it.unipi.di.acube.BingInterface;
import it.unipi.di.acube.batframework.cache.BenchmarkCache;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.datasetPlugins.GERDAQDataset;
import it.unipi.di.acube.batframework.metrics.Metrics;
import it.unipi.di.acube.batframework.metrics.MetricsResultSet;
import it.unipi.di.acube.batframework.metrics.StrongTagMatch;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.batframework.utils.FreebaseApi;
import it.unipi.di.acube.batframework.utils.TestDataset;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphAnnotatorDebugger;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.boldfilters.FrequencyBoldFilter;
import it.unipi.di.acube.smaph.learn.models.entityfilters.NoEntityFilter;
import it.unipi.di.acube.smaph.linkback.DummyLinkBack;
import it.unipi.di.acube.smaph.snippetannotationfilters.FrequencyAnnotationFilter;

import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONWriter;

public class DumpCandidateEntities {
	private static final Locale LOCALE = Locale.US;
	
	public static void main(String[] args) throws Exception {
		java.security.Security.setProperty("networkaddress.cache.ttl" , "0");
		Locale.setDefault(LOCALE);
		WikipediaApiInterface wikiApi = new WikipediaApiInterface("/tmp/wid.cache",
				"/tmp/redirect.cache");
		FreebaseApi freebApi = new FreebaseApi(
				"AIzaSyCRE0wurabdyQU2AJn1EO1KHzugGSeLLp4", "freeb.cache");
		//WATRelatednessComputer.setCache("relatedness.cache");
		//WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");
		//BenchmarkCache.useCache("results.cache");
		/*A2WDataset ds = new ERDDatasetFilter (new SMAPHDataset("datasets/gerdaq/gerdaq_test.xml",
				wikiApi), wikiApi, wikiToFreebase);*/
		A2WDataset ds = new GERDAQDataset("datasets/gerdaq/gerdaq_trainingA.xml",
				wikiApi);

		System.out.println("Printing basic information about dataset "
				+ ds.getName());
		TestDataset.dumpInfo(ds, wikiApi);

		SmaphConfig.setConfigFile("smaph-config.xml");
		String bingKey = SmaphConfig.getDefaultBingKey();
		
		WATAnnotator.setCache("wikisense.cache");
		WATAnnotator wat = new WATAnnotator("wikisense.mkapp.it", 80,
				"base", "COMMONNESS", "jaccard", "0.6", "0.0"/* minlp */, false,
				false, false);

		WATAnnotator watSnippets= new WATAnnotator(
				"wikisense.mkapp.it", 80, "base", "COMMONNESS", "mw", "0.2",
				"0.0", false, false, false);

		
		SmaphAnnotatorDebugger debugger = new SmaphAnnotatorDebugger();
		SmaphAnnotator ann = new SmaphAnnotator(wat, new FrequencyBoldFilter(
				0.06f), new NoEntityFilter(), null, new DummyLinkBack(), false,
				true, true, 10, false, -1, false, -1, true, 25, false,
				watSnippets, new FrequencyAnnotationFilter(0.03), wikiApi,
				bingKey);
			
		
		ann.setDebugger(debugger);
		BingInterface.setCache("bing.cache.compressed");
		
		List<HashSet<Tag>> resTag = BenchmarkCache.doC2WTags(ann, ds);

		Metrics<Tag> metrics = new Metrics<Tag>();
		MetricsResultSet rs = metrics.getResult(resTag,
				ds.getC2WGoldStandardList(), new StrongTagMatch(wikiApi));
		System.out
				.format(LOCALE,
						"mac-P/R/F1: %.3f\t%.3f\t%.3f TP/FP/FN: %d\t%d\t%d mic-P/R/F1: %.3f\t%.3f\t%.3f%n",
						rs.getMacroPrecision(), rs.getMacroRecall(),
						rs.getMacroF1(), rs.getGlobalTp(), rs.getGlobalFp(),
						rs.getGlobalFn(), rs.getMicroPrecision(),
						rs.getMicroRecall(), rs.getMicroF1());

		
		HashMap<String, Set<Integer>> candidateEntities = debugger.getCandidateEntities();
		dumpCandidateEntities("candidate_entities.json", candidateEntities);
		
		freebApi.flush();
		wikiApi.flush();
		BingInterface.flush();
		WATAnnotator.flush();
		WATRelatednessComputer.flush();
		BenchmarkCache.flush();
	}

	public static void dumpCandidateEntities(String filename, HashMap<String, Set<Integer>> candidateEntities) throws IOException, JSONException{
		FileWriter fw = new FileWriter(filename);
		JSONWriter wr = new JSONWriter(fw);
		wr.object();
		for (String query : candidateEntities.keySet()){
			wr.key(query).array();
			for (int entity : candidateEntities.get(query))
				wr.value(entity);
			wr.endArray();
		}
		wr.endObject();
		fw.close();
	}
}
