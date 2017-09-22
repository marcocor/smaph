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

package it.unipi.di.acube.smaph.main.experiments;

import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;

import it.unipi.di.acube.batframework.cache.BenchmarkCache;
import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.datasetPlugins.DatasetBuilder;
import it.unipi.di.acube.batframework.metrics.Metrics;
import it.unipi.di.acube.batframework.metrics.MetricsResultSet;
import it.unipi.di.acube.batframework.metrics.StrongAnnotationMatch;
import it.unipi.di.acube.batframework.metrics.StrongMentionAnnotationMatch;
import it.unipi.di.acube.batframework.metrics.StrongTagMatch;
import it.unipi.di.acube.batframework.metrics.StrongTagMatchNEOnly;
import it.unipi.di.acube.batframework.metrics.WeakAnnotationMatch;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.problems.A2WSystem;
import it.unipi.di.acube.batframework.problems.C2WDataset;
import it.unipi.di.acube.batframework.problems.C2WSystem;
import it.unipi.di.acube.batframework.systemPlugins.CachedWATAnnotator;
import it.unipi.di.acube.batframework.systemPlugins.MockAnnotator;
import it.unipi.di.acube.batframework.systemPlugins.TagmeAnnotator;
import it.unipi.di.acube.batframework.utils.DumpData;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.TestDataset;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.batframework.utils.WikipediaLocalInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphBuilder;
import it.unipi.di.acube.smaph.SmaphBuilder.SmaphVersion;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;
import it.unipi.di.acube.smaph.learn.featurePacks.GreedyFeaturePack;
import it.unipi.di.acube.smaph.learn.models.entityfilters.NoEntityFilter;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.AnnotationRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.LibSvmAnnotationRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.GreedyLinkback;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.BindingGenerator;

public class RunBenchmark {
	private static final Locale LOCALE = Locale.US;

	public static void main(String[] args) throws Exception {
		Options options = new Options().addOption("g", "tagme-gcube-token", true, "Tagme Gcube Authentication Token.");
		CommandLine line = new GnuParser().parse(options, args);

		java.security.Security.setProperty("networkaddress.cache.ttl", "0");
		Locale.setDefault(LOCALE);
		SmaphConfig c = SmaphConfig.fromConfigFile("smaph-config.xml");
		WikipediaInterface wikiApi = WikipediaLocalInterface.open(c.getDefaultWikipagesStorage());
		EntityToAnchors e2a = EntityToAnchors.fromDB(c.getDefaultEntityToAnchorsStorage());
		WikipediaToFreebase w2f = WikipediaToFreebase.open(c.getDefaultWikipediaToFreebaseStorage());

		WATRelatednessComputer.setCache("relatedness.cache");
		A2WDataset ds = DatasetBuilder.getGerdaqTest(wikiApi);

		System.out.println("Printing basic information about dataset " + ds.getName());
		TestDataset.dumpInfo(ds, wikiApi);

		CachedWATAnnotator wat = new CachedWATAnnotator("wikisense.mkapp.it", 80, "base", "COMMONNESS", "mw", "0.2", "0.0");

		CachedWATAnnotator.setCache("wikisense.cache");
		TagmeAnnotator tagme = null;
		if (line.hasOption("tagme-gcube-token"))
			tagme = new TagmeAnnotator("https://tagme.d4science.org/tagme/tag", line.getOptionValue("tagme-gcube-token"), 0.8f, 0.005f, 0.02f);

		SmaphAnnotator ann1 = SmaphBuilder.getSmaph(SmaphVersion.ENTITY_FILTER, wikiApi, w2f,
		        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, true, SmaphBuilder.Websearch.BING, c, -1);
		SmaphAnnotator ann2 = SmaphBuilder.getSmaph(SmaphVersion.ENTITY_FILTER, wikiApi, w2f,
		        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, false, SmaphBuilder.Websearch.BING, c, -1);
		SmaphAnnotator ann3 = SmaphBuilder.getSmaph(SmaphVersion.ENTITY_FILTER, wikiApi, w2f,
		        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, true, SmaphBuilder.Websearch.GOOGLE_CSE, c, -1);
		SmaphAnnotator ann4 = SmaphBuilder.getSmaph(SmaphVersion.ENTITY_FILTER, wikiApi, w2f,
		        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, false, SmaphBuilder.Websearch.GOOGLE_CSE, c, -1);

		SmaphAnnotator ann5 = SmaphBuilder.getSmaph(SmaphVersion.ANNOTATION_REGRESSOR, wikiApi, w2f,
		        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, true, SmaphBuilder.Websearch.BING, c, -1);
		SmaphAnnotator ann6 = SmaphBuilder.getSmaph(SmaphVersion.ANNOTATION_REGRESSOR, wikiApi, w2f,
		        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, false, SmaphBuilder.Websearch.BING, c, -1);
		SmaphAnnotator ann7 = SmaphBuilder.getSmaph(SmaphVersion.ANNOTATION_REGRESSOR, wikiApi, w2f,
		        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, true, SmaphBuilder.Websearch.GOOGLE_CSE, c, -1);
		SmaphAnnotator ann8 = SmaphBuilder.getSmaph(SmaphVersion.ANNOTATION_REGRESSOR, wikiApi, w2f,
		        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, false, SmaphBuilder.Websearch.GOOGLE_CSE, c, -1);

		SmaphAnnotator ann9 = SmaphBuilder.getSmaph(SmaphVersion.COLLECTIVE, wikiApi, w2f,
		        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, true, SmaphBuilder.Websearch.BING, c, -1);
		SmaphAnnotator ann10 = SmaphBuilder.getSmaph(SmaphVersion.COLLECTIVE, wikiApi, w2f,
		        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, false, SmaphBuilder.Websearch.BING, c, -1);
		SmaphAnnotator ann11 = SmaphBuilder.getSmaph(SmaphVersion.COLLECTIVE, wikiApi, w2f,
		        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, true, SmaphBuilder.Websearch.GOOGLE_CSE, c, -1);
		SmaphAnnotator ann12 = SmaphBuilder.getSmaph(SmaphVersion.COLLECTIVE, wikiApi, w2f,
		        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, false, SmaphBuilder.Websearch.GOOGLE_CSE, c, -1);

		SmaphAnnotator ann13 = SmaphBuilder.getSmaph(SmaphVersion.GREEDY, wikiApi, w2f, SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR,
		        e2a, true, SmaphBuilder.Websearch.BING, c, -1);
		SmaphAnnotator ann14 = SmaphBuilder.getSmaph(SmaphVersion.GREEDY, wikiApi, w2f, SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR,
		        e2a, false, SmaphBuilder.Websearch.BING, c, -1);
		SmaphAnnotator ann15 = SmaphBuilder.getSmaph(SmaphVersion.GREEDY, wikiApi, w2f, SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR,
		        e2a, true, SmaphBuilder.Websearch.GOOGLE_CSE, c, -1);
		SmaphAnnotator ann16 = SmaphBuilder.getSmaph(SmaphVersion.GREEDY, wikiApi, w2f, SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR,
		        e2a, false, SmaphBuilder.Websearch.GOOGLE_CSE, c, -1);

		List<Pair<Integer, Double>> maxAndAvgGeneratedBindingsList = new Vector<>();

		MockAnnotator upperBound1 = getUpperBound1Annotator(ds, ann1);

		Pair<Integer, Double> maxAndAvgGeneratedBindings = new Pair<Integer, Double>(-1, -1.0);
		maxAndAvgGeneratedBindingsList.add(maxAndAvgGeneratedBindings);

		HashMap<A2WSystem, MetricsResultSet> samRes = new HashMap<>();
		HashMap<A2WSystem, MetricsResultSet> wamRes = new HashMap<>();
		HashMap<C2WSystem, MetricsResultSet> C2WRes = new HashMap<>();
		HashMap<C2WSystem, MetricsResultSet> C2WResNEONly = new HashMap<>();
		HashMap<C2WSystem, MetricsResultSet> mentionRes = new HashMap<>();

		List<C2WSystem> annotatorsC2W = new Vector<>();
		if (tagme != null)
			annotatorsC2W.add(tagme);
		annotatorsC2W.add(wat);
		annotatorsC2W.add(ann1);
		annotatorsC2W.add(ann2);
		annotatorsC2W.add(ann3);
		annotatorsC2W.add(ann4);
		annotatorsC2W.add(ann5);
		annotatorsC2W.add(ann6);
		annotatorsC2W.add(ann7);
		annotatorsC2W.add(ann8);
		annotatorsC2W.add(ann9);
		annotatorsC2W.add(ann10);
		annotatorsC2W.add(ann11);
		annotatorsC2W.add(ann12);
		annotatorsC2W.add(ann13);
		annotatorsC2W.add(ann14);
		annotatorsC2W.add(ann15);
		annotatorsC2W.add(ann16);

		List<A2WSystem> annotatorsA2W = new Vector<>();
		if (tagme != null)
			annotatorsA2W.add(tagme);
		annotatorsA2W.add(wat);
		annotatorsA2W.add(ann5);
		annotatorsA2W.add(ann6);
		annotatorsA2W.add(ann7);
		annotatorsA2W.add(ann8);
		annotatorsA2W.add(ann9);
		annotatorsA2W.add(ann10);
		annotatorsA2W.add(ann11);
		annotatorsA2W.add(ann12);
		annotatorsA2W.add(ann13);
		annotatorsA2W.add(ann14);
		annotatorsA2W.add(ann15);
		annotatorsA2W.add(ann16);

		annotatorsC2W.add(upperBound1);
		annotatorsA2W.add(upperBound1);

		for (C2WSystem ann : annotatorsC2W) {

			List<HashSet<Tag>> resTag = BenchmarkCache.doC2WTags(ann, ds);

			Metrics<Tag> metrics = new Metrics<Tag>();
			C2WRes.put(ann, metrics.getResult(resTag, ds.getC2WGoldStandardList(), new StrongTagMatch(wikiApi)));
			C2WResNEONly.put(ann, metrics.getResult(resTag, ds.getC2WGoldStandardList(), new StrongTagMatchNEOnly(wikiApi, w2f)));
			BenchmarkCache.flush();

		}
		for (A2WSystem ann : annotatorsA2W) {

			List<HashSet<Annotation>> resAnn = BenchmarkCache.doA2WAnnotations(ann, ds);
			Metrics<Annotation> metrics = new Metrics<>();
			samRes.put(ann, metrics.getResult(resAnn, ds.getA2WGoldStandardList(), new StrongAnnotationMatch(wikiApi)));
			wamRes.put(ann, metrics.getResult(resAnn, ds.getA2WGoldStandardList(), new WeakAnnotationMatch(wikiApi)));
			mentionRes.put(ann, metrics.getResult(resAnn, ds.getA2WGoldStandardList(), new StrongMentionAnnotationMatch()));
			BenchmarkCache.flush();

		}
		for (A2WSystem ann : annotatorsA2W) {
			MetricsResultSet rs = samRes.get(ann);
			printMetricsResultSet("A2W-SAM", rs, ann.getName());
		}
		for (A2WSystem ann : annotatorsA2W) {
			MetricsResultSet rs = wamRes.get(ann);
			printMetricsResultSet("A2W-WAM", rs, ann.getName());
		}
		for (A2WSystem ann : annotatorsA2W) {
			MetricsResultSet rs = mentionRes.get(ann);
			printMetricsResultSet("Mention", rs, ann.getName());
		}
		for (C2WSystem ann : annotatorsC2W) {
			MetricsResultSet rs = C2WRes.get(ann);
			printMetricsResultSet("C2W", rs, ann.getName());
		}
		for (C2WSystem ann : annotatorsC2W) {
			MetricsResultSet rs = C2WResNEONly.get(ann);
			printMetricsResultSet("C2W-NE", rs, ann.getName());
		}

		for (Pair<Integer, Double> maxAndAvgGeneratedBindingsI : maxAndAvgGeneratedBindingsList)
			System.out.format(LOCALE, "LB avg/max examples: %.3f\t%d%n", maxAndAvgGeneratedBindingsI.second,
			        maxAndAvgGeneratedBindingsI.first);
		wikiApi.flush();
		CachedWATAnnotator.flush();
		WATRelatednessComputer.flush();
		BenchmarkCache.flush();
	}

	public static void printMetricsResultSet(String exp, MetricsResultSet rs, String annName) {
		System.out.format(LOCALE, "%s\t mac-P/R/F1: %.3f\t%.3f\t%.3f TP/FP/FN: %d\t%d\t%d mic-P/R/F1: %.3f\t%.3f\t%.3f\t%s%n",
		        exp, rs.getMacroPrecision(), rs.getMacroRecall(), rs.getMacroF1(), rs.getGlobalTp(), rs.getGlobalFp(),
		        rs.getGlobalFn(), rs.getMicroPrecision(), rs.getMicroRecall(), rs.getMicroF1(), annName);
		System.out.format(LOCALE, "%s\t BS-std-mac-P/R/F1: %.3f\t%.3f\t%.3f BS-std-mic-P/R/F1: %.3f\t%.3f\t%.3f\t%s%n", exp,
		        rs.getMacroPrecisionStdBootstrap(), rs.getMacroRecallStdBootstrap(), rs.getMacroF1StdBootstrap(),
		        rs.getMicroPrecisionStdBootstrap(), rs.getMicroRecallStdBootstrap(), rs.getMicroF1StdBootstrap(), annName);
	}

	private static void printDifferentRecall(C2WDataset ds, C2WSystem ann1, C2WSystem ann2, WikipediaInterface wikiApi)
	        throws Exception {
		Metrics<Tag> metrics = new Metrics<>();
		StrongTagMatch m = new StrongTagMatch(wikiApi);
		List<HashSet<Tag>> resTag1 = BenchmarkCache.doC2WTags(ann1, ds);
		MetricsResultSet m1 = metrics.getResult(resTag1, ds.getC2WGoldStandardList(), m);

		List<HashSet<Tag>> resTag2 = BenchmarkCache.doC2WTags(ann2, ds);
		MetricsResultSet m2 = metrics.getResult(resTag2, ds.getC2WGoldStandardList(), m);

		for (int i = 0; i < ds.getSize(); i++) {
			if (m1.getRecalls(i) != m2.getRecalls(i)) {
				String text = ds.getTextInstanceList().get(i);
				HashSet<Tag> gold = ds.getC2WGoldStandardList().get(i);
				HashSet<Tag> tag1 = resTag1.get(i);
				HashSet<Tag> tag2 = resTag2.get(i);
				System.out.printf("%d %s P/R/F1=%.3f/%.3f/%.3f [%s]%n", i, ann1.getName(), m1.getPrecisions(i), m1.getRecalls(i),
				        m1.getF1s(i), text);
				System.out.printf("%d %s P/R/F1=%.3f/%.3f/%.3f [%s]%n", i, ann2.getName(), m2.getPrecisions(i), m2.getRecalls(i),
				        m2.getF1s(i), text);
				System.out.printf("%d %s output:%n", i, ann1.getName());
				DumpData.dumpCompareMatch(text, gold, tag1, m, wikiApi);
				System.out.printf("%d %s output:%n", i, ann2.getName());
				DumpData.dumpCompareMatch(text, gold, tag2, m, wikiApi);
				System.out.println();
			}
		}
	}

	public static SmaphAnnotator getGreedy(int s1, int s2, int s3, SmaphBuilder.Websearch ws, WikipediaInterface wikiApi,
	        WikipediaToFreebase wikiToFreeb, EntityToAnchors e2a, SmaphConfig c) throws Exception {
		List<AnnotationRegressor> regressors = new Vector<>();
		List<FeatureNormalizer> fns = new Vector<>();
		int nGreedySteps = 0;
		while (SmaphBuilder.getModel(SmaphVersion.GREEDY, ws, s1, s2, s3, nGreedySteps) != null)
			nGreedySteps++;
		for (int i = 0; i < nGreedySteps; i++) {
			URL modelI = SmaphBuilder.getModel(SmaphVersion.GREEDY, ws, s1, s2, s3, i);
			URL zscoreI = SmaphBuilder.getZscoreNormalizer(SmaphVersion.GREEDY, ws, s1, s2, s3, i);
			AnnotationRegressor arI = LibSvmAnnotationRegressor.fromUrl(modelI);
			FeatureNormalizer fnI = ZScoreFeatureNormalizer.fromUrl(zscoreI, new GreedyFeaturePack());
			regressors.add(arI);
			fns.add(fnI);
		}
		GreedyLinkback lbGreedy = new GreedyLinkback(regressors, fns, wikiApi, wikiToFreeb, e2a,
		        SmaphBuilder.DEFAULT_ANCHOR_MENTION_ED);
		SmaphAnnotator a = SmaphBuilder.getDefaultSmaphParamTopk(wikiApi, wikiToFreeb, SmaphBuilder.DEFAULT_AUX_ANNOTATOR, e2a,
		        new NoEntityFilter(), null, lbGreedy, true, s1, true, s2, true, s3, ws, c);

		a.appendName(String.format(" - greedy, %s S1=%d S2=%d S3=%d", ws, s1, s2, s3));
		return a;
	}

	/**
	 * Annotator that keeps all annotations whose entities are included in the candidate set. (i.e. if we had magic mention
	 * spotter and mention-entity linking)
	 */
	private static MockAnnotator getUpperBound1Annotator(A2WDataset ds, SmaphAnnotator ann) throws Exception {
		HashMap<String, HashSet<ScoredAnnotation>> answers = new HashMap<>();
		for (int i = 0; i < ds.getSize(); i++)
			answers.put(ds.getTextInstanceList().get(i),
			        ann.getLBUpperBound1(ds.getTextInstanceList().get(i), ds.getA2WGoldStandardList().get(i), null));
		return new MockAnnotator(answers, "upper bound 1");
	}

	/**
	 * Annotator that always picks the best binding among those generated by bg. (i.e. if we had magic binding selection)
	 */
	private static MockAnnotator getUpperBound2Annotator(A2WDataset ds, SmaphAnnotator ann, BindingGenerator bg,
	        Pair<Integer, Double> maxAndAvgGeneratedBindings) throws Exception {
		int bindingsCount = 0;
		int maxBindings = Integer.MIN_VALUE;
		HashMap<String, HashSet<ScoredAnnotation>> answers = new HashMap<>();
		for (int i = 0; i < ds.getSize(); i++) {
			Pair<HashSet<ScoredAnnotation>, Integer> bestBindingAndCount = ann.getLBUpperBound2(ds.getTextInstanceList().get(i),
			        ds.getA2WGoldStandardList().get(i), bg, null);
			answers.put(ds.getTextInstanceList().get(i), bestBindingAndCount.first);
			bindingsCount += bestBindingAndCount.second;
			maxBindings = Math.max(maxBindings, bestBindingAndCount.second);
		}
		maxAndAvgGeneratedBindings.first = maxBindings;
		maxAndAvgGeneratedBindings.second = ((double) bindingsCount) / ds.getSize();
		return new MockAnnotator(answers, "upper bound 2");
	}

	/**
	 * Annotator that always picks the best annotations among those generated (thus, employing the segment generator) that have a
	 * segment-anchor minED > maxAnchorEd. (i.e. if we had magic annotation selection)
	 */
	private static MockAnnotator getUpperBound3Annotator(A2WDataset ds, SmaphAnnotator ann, double maxAnchorEd,
	        Pair<Integer, Double> maxAndAvgGeneratedBindings) throws Exception {
		int bindingsCount = 0;
		int maxBindings = Integer.MIN_VALUE;
		HashMap<String, HashSet<ScoredAnnotation>> answers = new HashMap<>();
		for (int i = 0; i < ds.getSize(); i++) {

			Pair<HashSet<ScoredAnnotation>, Integer> bestBindingAndCount = ann.getLBUpperBound3(ds.getTextInstanceList().get(i),
			        ds.getA2WGoldStandardList().get(i), maxAnchorEd, null);
			answers.put(ds.getTextInstanceList().get(i), bestBindingAndCount.first);
			bindingsCount += bestBindingAndCount.second;
			maxBindings = Math.max(maxBindings, bestBindingAndCount.second);
		}
		maxAndAvgGeneratedBindings.first = maxBindings;
		maxAndAvgGeneratedBindings.second = ((double) bindingsCount) / ds.getSize();
		return new MockAnnotator(answers, String.format("upper bound 3 thr%.3f", maxAnchorEd));
	}

	/**
	 * Annotator that keeps all annotations whose entities are included in the candidate set and mentions included in candidate
	 * segments. (i.e. if we had magic segment-entity linking)
	 */
	private static MockAnnotator getUpperBound4Annotator(A2WDataset ds, SmaphAnnotator ann) throws Exception {
		HashMap<String, HashSet<ScoredAnnotation>> answers = new HashMap<>();
		for (int i = 0; i < ds.getSize(); i++)
			answers.put(ds.getTextInstanceList().get(i),
			        ann.getLBUpperBound4(ds.getTextInstanceList().get(i), ds.getA2WGoldStandardList().get(i), null));
		return new MockAnnotator(answers, "upper bound 4");
	}

	private static MockAnnotator getUpperBoundMention(A2WDataset ds, SmaphAnnotator ann, BindingGenerator bg) throws Exception {
		HashMap<String, HashSet<ScoredAnnotation>> answers = new HashMap<>();
		for (int i = 0; i < ds.getSize(); i++) {
			HashSet<ScoredAnnotation> bestBinding = ann.getUpperBoundMentions(ds.getTextInstanceList().get(i),
			        ds.getA2WGoldStandardList().get(i), bg, null);
			answers.put(ds.getTextInstanceList().get(i), bestBinding);
		}
		return new MockAnnotator(answers, "upper bound mentions");

	}

}
