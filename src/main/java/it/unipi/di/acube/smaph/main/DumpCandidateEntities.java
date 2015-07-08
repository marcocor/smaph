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

import it.unipi.di.acube.batframework.cache.BenchmarkCache;
import it.unipi.di.acube.batframework.cache.BenchmarkResults;
import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.datasetPlugins.SMAPHDataset;
import it.unipi.di.acube.batframework.metrics.*;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.problems.A2WSystem;
import it.unipi.di.acube.batframework.problems.C2WDataset;
import it.unipi.di.acube.batframework.problems.C2WSystem;
import it.unipi.di.acube.batframework.problems.Sa2WSystem;
import it.unipi.di.acube.batframework.systemPlugins.AIDADefaultAnnotator;
import it.unipi.di.acube.batframework.systemPlugins.ERDSystem;
import it.unipi.di.acube.batframework.systemPlugins.MockAnnotator;
import it.unipi.di.acube.batframework.systemPlugins.TagmeAnnotator;
import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.batframework.utils.*;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphAnnotatorDebugger;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.boldfilters.EditDistanceBoldFilter;
import it.unipi.di.acube.smaph.boldfilters.FrequencyBoldFilter;
import it.unipi.di.acube.smaph.entityfilters.LibSvmEntityFilter;
import it.unipi.di.acube.smaph.entityfilters.NoEntityFilter;
import it.unipi.di.acube.smaph.learn.GenerateTrainingAndTest;
import it.unipi.di.acube.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.NoFeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.ScaleFeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.BaselineLinkBack;
import it.unipi.di.acube.smaph.linkback.DummyLinkBack;
import it.unipi.di.acube.smaph.linkback.SvmCollectiveLinkBack;
import it.unipi.di.acube.smaph.linkback.SvmIndividualAnnotationLinkBack;
import it.unipi.di.acube.smaph.linkback.annotationRegressor.LibLinearRegressor;
import it.unipi.di.acube.smaph.linkback.annotationRegressor.RankLibAnnotationRegressor;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.BindingGenerator;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.DefaultBindingGenerator;
import it.unipi.di.acube.smaph.linkback.bindingRegressor.LibLinearBindingRegressor;
import it.unipi.di.acube.smaph.linkback.bindingRegressor.RankLibBindingRegressor;
import it.unipi.di.acube.smaph.snippetannotationfilters.FrequencyAnnotationFilter;
import it.unipi.di.acube.smaph.snippetannotationfilters.SnippetAnnotationFilter;
import it.unipi.di.acube.BingInterface;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.BufferedWriter;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.UnsupportedEncodingException;
import java.nio.channels.GatheringByteChannel;
import java.util.*;

import org.apache.commons.lang3.ArrayUtils;
import org.codehaus.jackson.annotate.JsonAnyGetter;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONWriter;

import de.bwaldvogel.liblinear.Train;

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
		/*A2WDataset ds = new ERDDatasetFilter (new SMAPHDataset("datasets/smaph/smaph_test.xml",
				wikiApi), wikiApi, wikiToFreebase);*/
		A2WDataset ds = new SMAPHDataset("datasets/smaph/smaph_trainingA.xml",
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
