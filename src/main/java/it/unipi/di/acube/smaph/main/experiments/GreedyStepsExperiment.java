package it.unipi.di.acube.smaph.main.experiments;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;

import it.unipi.di.acube.batframework.cache.BenchmarkCache;
import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.datasetPlugins.DatasetBuilder;
import it.unipi.di.acube.batframework.metrics.Metrics;
import it.unipi.di.acube.batframework.metrics.MetricsResultSet;
import it.unipi.di.acube.batframework.metrics.StrongAnnotationMatch;
import it.unipi.di.acube.batframework.metrics.StrongTagMatch;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.problems.A2WSystem;
import it.unipi.di.acube.batframework.systemPlugins.CachedWATAnnotator;
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

public class GreedyStepsExperiment {
	private static final Locale LOCALE = Locale.US;

	public static void main(String[] args) throws Exception {
		Options options = new Options().addOption("w", "websearch-piggyback", true,
		        "What web search engine to piggyback on. Can be either `bing' or `google'.");
		CommandLine line = new GnuParser().parse(options, args);

		java.security.Security.setProperty("networkaddress.cache.ttl", "0");
		Locale.setDefault(LOCALE);

		SmaphConfig c = SmaphConfig.fromConfigFile("smaph-config.xml");
		WikipediaInterface wikiApi = WikipediaLocalInterface.open(c.getDefaultWikipagesStorage());
		WikipediaToFreebase w2f = WikipediaToFreebase.open(c.getDefaultWikipediaToFreebaseStorage());
		EntityToAnchors e2a = EntityToAnchors.fromDB(c.getDefaultEntityToAnchorsStorage());
		WATRelatednessComputer.setCache("relatedness.cache");
		A2WDataset ds = DatasetBuilder.getGerdaqTest(wikiApi);
		CachedWATAnnotator.setCache("wikisense.cache");

		System.out.println("Printing basic information about dataset " + ds.getName());
		TestDataset.dumpInfo(ds, wikiApi);

		SmaphBuilder.Websearch ws = SmaphBuilder.websearchFromString(line.getOptionValue("websearch-piggyback"));

		HashMap<A2WSystem, MetricsResultSet> C2WRes = new HashMap<>();
		HashMap<A2WSystem, MetricsResultSet> samRes = new HashMap<>();

		int[] stepCount;

		int annCount = 0;
		int steps = 0;
		while (true) {
			SmaphAnnotator ann = SmaphBuilder.getSmaph(SmaphVersion.GREEDY, wikiApi, w2f,
			        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, true, ws, c, steps).appendName("-Steps=" + steps);

			List<HashSet<Tag>> resTag = BenchmarkCache.doC2WTags(ann, ds);
			List<HashSet<Annotation>> resAnn = BenchmarkCache.doA2WAnnotations(ann, ds);
			C2WRes.put(ann, new Metrics<Tag>().getResult(resTag, ds.getC2WGoldStandardList(), new StrongTagMatch(wikiApi)));
			samRes.put(ann,
			        new Metrics<Annotation>().getResult(resAnn, ds.getA2WGoldStandardList(), new StrongAnnotationMatch(wikiApi)));
			BenchmarkCache.flush();

			int thisAnnCount = resAnn.stream().mapToInt(res -> res.size()).sum();

			if (annCount == thisAnnCount && steps != 0) {
				stepCount = new int[steps];
				for (HashSet<Annotation> res : resAnn)
					stepCount[res.size()]++;
				break;
			} else {
				annCount = thisAnnCount;
				steps++;
			}
		}

		for (A2WSystem ann : C2WRes.keySet())
			printMetricsResultSet("C2W", C2WRes.get(ann), ann.getName());
		for (A2WSystem ann : samRes.keySet())
			printMetricsResultSet("SAM", samRes.get(ann), ann.getName());

		for (int i = 0; i < steps; i++) {
			System.out.printf("%d queries received a negative judgment at step %d%n", stepCount[i], i);
		}

		wikiApi.flush();
		CachedWATAnnotator.flush();
		WATRelatednessComputer.flush();
		BenchmarkCache.flush();
	}

	public static void printMetricsResultSet(String exp, MetricsResultSet rs, String annName) {
		System.out.format(LOCALE, "%s\t mac-P/R/F1: %.3f\t%.3f\t%.3f TP/FP/FN: %d\t%d\t%d mic-P/R/F1: %.3f\t%.3f\t%.3f\t%s%n",
		        exp, rs.getMacroPrecision(), rs.getMacroRecall(), rs.getMacroF1(), rs.getGlobalTp(), rs.getGlobalFp(),
		        rs.getGlobalFn(), rs.getMicroPrecision(), rs.getMicroRecall(), rs.getMicroF1(), annName);
	}
}
