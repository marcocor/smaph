package it.unipi.di.acube.smaph.main.experiments;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import it.unipi.di.acube.batframework.cache.BenchmarkCache;
import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.datasetPlugins.DatasetBuilder;
import it.unipi.di.acube.batframework.metrics.MatchRelation;
import it.unipi.di.acube.batframework.metrics.Metrics;
import it.unipi.di.acube.batframework.metrics.MetricsResultSet;
import it.unipi.di.acube.batframework.metrics.StrongAnnotationMatch;
import it.unipi.di.acube.batframework.metrics.StrongTagMatch;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.problems.A2WSystem;
import it.unipi.di.acube.batframework.systemPlugins.CachedWAT2Annotator;
import it.unipi.di.acube.batframework.utils.TestDataset;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.batframework.utils.WikipediaInterfaceWAT;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphBuilder;
import it.unipi.di.acube.smaph.SmaphBuilder.SmaphVersion;
import it.unipi.di.acube.smaph.SmaphBuilder.Websearch;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;

public class GreedyStepsExperiment {
	private static final Locale LOCALE = Locale.US;

	public static void main(String[] args) throws Exception {
		java.security.Security.setProperty("networkaddress.cache.ttl", "0");
		Locale.setDefault(LOCALE);

		Locale.setDefault(Locale.US);
		SmaphConfig c = SmaphConfig.fromConfigFile("smaph-config.xml");
		SmaphBuilder.initialize(c.getWatGcubeToken());
		CachedWAT2Annotator.setCache("wat2.cache");
		WATRelatednessComputer.setGcubeToken(c.getWatGcubeToken());
		WATRelatednessComputer.setCache("relatedness_wat2.cache");
		WikipediaInterface wikiApi = new WikipediaInterfaceWAT.WikipediaInterfaceWATBuilder().gcubeToken(c.getWatGcubeToken()).cache().build();
		WikipediaToFreebase w2f = WikipediaToFreebase.open(c.getDefaultWikipediaToFreebaseStorage());
		EntityToAnchors e2a = EntityToAnchors.fromDB(c.getDefaultEntityToAnchorsStorage());

		A2WDataset ds = DatasetBuilder.getGerdaqTest(wikiApi);

		System.out.println("Printing basic information about dataset " + ds.getName());
		TestDataset.dumpInfo(ds, wikiApi);

		Metrics<Annotation> mA = new Metrics<>();
		Metrics<Tag> mT = new Metrics<>();
		MatchRelation<Annotation> sam = new StrongAnnotationMatch(wikiApi);
		MatchRelation<Tag> stm = new StrongTagMatch(wikiApi);

		HashMap<A2WSystem, List<HashSet<Tag>>> C2WRes = new HashMap<>();
		HashMap<A2WSystem, List<HashSet<Annotation>>> A2WRes = new HashMap<>();
		int stepsGoogle = SmaphBuilder.getGreedyRegressors(Websearch.GOOGLE_CSE, true, true, true).getFirst().size();
		int stepsBing = SmaphBuilder.getGreedyRegressors(Websearch.BING, true, true, true).getFirst().size();
		SmaphAnnotator[] googleAnnotators = new SmaphAnnotator[stepsGoogle + 1];
		SmaphAnnotator[] bingAnnotators = new SmaphAnnotator[stepsBing + 1];
		int[] stepCountGoogle = new int[stepsGoogle + 1];
		int[] stepCountBing = new int[stepsBing + 1];

		for (Websearch ws : new Websearch[] { Websearch.GOOGLE_CSE, Websearch.BING }) {
			SmaphAnnotator[] annotatorsStep = (ws == Websearch.GOOGLE_CSE) ? googleAnnotators : bingAnnotators;

			for (int steps = 0; steps < annotatorsStep.length; steps++) {
				SmaphAnnotator ann = SmaphBuilder.getSmaph(SmaphVersion.GREEDY, wikiApi, w2f,
				        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, true, ws, c, steps)
				        .appendName("-" + ws + "-Steps=" + steps);
				annotatorsStep[steps] = ann;

				List<HashSet<Tag>> resTag = BenchmarkCache.doC2WTags(ann, ds);
				List<HashSet<Annotation>> resAnn = BenchmarkCache.doA2WAnnotations(ann, ds);
				C2WRes.put(ann, resTag);
				A2WRes.put(ann, resAnn);
				BenchmarkCache.flush();

				if (steps == annotatorsStep.length - 1) {
					int[] stepCount = (ws == Websearch.GOOGLE_CSE) ? stepCountGoogle : stepCountBing;
					for (HashSet<Annotation> res : resAnn)
						stepCount[res.size()]++;
				}
			}

		}

		for (Websearch ws : new Websearch[] { Websearch.GOOGLE_CSE, Websearch.BING }) {
			SmaphAnnotator[] annotatorsStep = (ws == Websearch.GOOGLE_CSE) ? googleAnnotators : bingAnnotators;
			for (A2WSystem ann : annotatorsStep) {
				MetricsResultSet res = mT.getResult(C2WRes.get(ann), ds.getC2WGoldStandardList(), stm);
				printMetricsResultSet("C2W", res, ann.getName());
			}
			for (A2WSystem ann : annotatorsStep) {
				MetricsResultSet res = mA.getResult(A2WRes.get(ann), ds.getA2WGoldStandardList(), sam);
				printMetricsResultSet("SAM", res, ann.getName());
			}
			int[] stepCount = (ws == Websearch.GOOGLE_CSE) ? stepCountGoogle : stepCountBing;
			for (int i = 0; i < stepCount.length; i++) {
				System.out.printf("%s: %d queries received a negative judgment at step %d%n", ws, stepCount[i], i);
			}
		}

		for (int step = 0; step < Math.max(googleAnnotators.length, bingAnnotators.length); step++) {
			List<HashSet<Annotation>> resStepBing = A2WRes.get(bingAnnotators[Math.min(step, bingAnnotators.length - 1)]);
			List<HashSet<Annotation>> resStepGoogle = A2WRes.get(googleAnnotators[Math.min(step, googleAnnotators.length - 1)]);
			System.out.printf("Macro-sim at step %d: %.3f%n", step, mA.macroSimilarity(resStepBing, resStepGoogle, sam));
		}
		for (int step = 0; step < Math.max(googleAnnotators.length, bingAnnotators.length); step++) {
			List<HashSet<Annotation>> resStepBing = A2WRes.get(bingAnnotators[Math.min(step, bingAnnotators.length - 1)]);
			List<HashSet<Annotation>> resStepGoogle = A2WRes.get(googleAnnotators[Math.min(step, googleAnnotators.length - 1)]);
			System.out.printf("TP Macro-sim at step %d: %.3f%n", step,
			        mA.macroSimilarity(
			        		mA.getTp(ds.getA2WGoldStandardList(), resStepBing, sam),
			                mA.getTp(ds.getA2WGoldStandardList(), resStepGoogle, sam),
			                sam));
		}

		wikiApi.flush();
		CachedWAT2Annotator.flush();
		WATRelatednessComputer.flush();
		BenchmarkCache.flush();
	}

	public static void printMetricsResultSet(String exp, MetricsResultSet rs, String annName) {
		System.out.format(LOCALE, "%s\t mac-P/R/F1: %.3f\t%.3f\t%.3f TP/FP/FN: %d\t%d\t%d mic-P/R/F1: %.3f\t%.3f\t%.3f\t%s%n",
		        exp, rs.getMacroPrecision(), rs.getMacroRecall(), rs.getMacroF1(), rs.getGlobalTp(), rs.getGlobalFp(),
		        rs.getGlobalFn(), rs.getMicroPrecision(), rs.getMicroRecall(), rs.getMicroF1(), annName);
	}
}
