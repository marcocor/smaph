package it.unipi.di.acube.smaph.main.experiments;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;

import it.unipi.di.acube.batframework.cache.BenchmarkCache;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.datasetPlugins.DatasetBuilder;
import it.unipi.di.acube.batframework.metrics.Metrics;
import it.unipi.di.acube.batframework.metrics.MetricsResultSet;
import it.unipi.di.acube.batframework.metrics.StrongTagMatch;
import it.unipi.di.acube.batframework.metrics.StrongTagMatchNEOnly;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.problems.C2WSystem;
import it.unipi.di.acube.batframework.systemPlugins.CachedWAT2Annotator;
import it.unipi.di.acube.batframework.utils.TestDataset;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.batframework.utils.WikipediaInterfaceWAT;
import it.unipi.di.acube.smaph.SmaphBuilder;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;

public class SourceCoverage {
	private static final Locale LOCALE = Locale.US;

	public static void main(String[] args) throws Exception {
		Options options = new Options().addOption("w", "websearch-piggyback", true,
		        "What web search engine to piggyback on. Can be either `bing' or `google'.");
		CommandLine line = new GnuParser().parse(options, args);

		java.security.Security.setProperty("networkaddress.cache.ttl", "0");
		Locale.setDefault(LOCALE);

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

		SmaphBuilder.Websearch ws = SmaphBuilder.websearchFromString(line.getOptionValue("websearch-piggyback"));
		String wsStr = ws.toString();
		List<C2WSystem> annotators = new Vector<C2WSystem>();
		annotators.add(SmaphBuilder.getSmaphGatherer(wikiApi, w2f, e2a, true, false, false, ws, c).appendName("-" + wsStr + "-S1-def"));
		annotators.add(SmaphBuilder.getSmaphGatherer(wikiApi, w2f, e2a, false, true, false, ws, c).appendName("-" + wsStr + "-S2-def"));
		annotators.add(SmaphBuilder.getSmaphGatherer(wikiApi, w2f, e2a, false, false, true, ws, c).appendName("-" + wsStr + "-S3-def"));
		annotators.add(SmaphBuilder.getSmaphGatherer(wikiApi, w2f, e2a, true, true, true, ws, c).appendName("-" + wsStr + "-S123-def"));
		annotators.add(SmaphBuilder.getSmaphGatherer(wikiApi, w2f, e2a, true, false, true, ws, c).appendName("-" + wsStr + "-S13-def"));
		annotators.add(SmaphBuilder.getSmaphGatherer(wikiApi, w2f, e2a, false, true, true, ws, c).appendName("-" + wsStr + "-S23-def"));

		for (int topKS1 : new int[] { 0, 1, 2, 3, 4, 5, 10, 20 })
			annotators.add(SmaphBuilder.getSmaphGatherer(wikiApi, w2f, e2a, true, topKS1, false, 0, false, 0, ws, c)
			        .appendName("-" + wsStr + "-S1-top" + topKS1));
		for (int topKS2 : new int[] { 0, 1, 2, 3, 4, 6, 8, 10 })
			annotators.add(SmaphBuilder.getSmaphGatherer(wikiApi, w2f, e2a, false, 0, true, topKS2, false, 0, ws, c)
			        .appendName("-" + wsStr + "-S2-top" + topKS2));
		for (int topKS3 : new int[] { 0, 1, 2, 3, 4, 5, 10, 20 })
			annotators.add(SmaphBuilder.getSmaphGatherer(wikiApi, w2f, e2a, false, 0, false, 0, true, topKS3, ws, c)
			        .appendName("-" + wsStr + "-S3-top" + topKS3));

		HashMap<C2WSystem, MetricsResultSet> C2WRes = new HashMap<>();
		HashMap<C2WSystem, MetricsResultSet> C2WResNEOnly = new HashMap<>();

		for (C2WSystem ann : annotators) {
			List<HashSet<Tag>> resTag = BenchmarkCache.doC2WTags(ann, ds);
			Metrics<Tag> metrics = new Metrics<Tag>();
			C2WRes.put(ann, metrics.getResult(resTag, ds.getC2WGoldStandardList(), new StrongTagMatch(wikiApi)));
			C2WResNEOnly.put(ann, metrics.getResult(resTag, ds.getC2WGoldStandardList(), new StrongTagMatchNEOnly(wikiApi, w2f)));
			BenchmarkCache.flush();
		}

		for (C2WSystem ann : annotators)
			printMetricsResultSet("C2W", C2WRes.get(ann), ann.getName());
		for (C2WSystem ann : annotators)
			printMetricsResultSet("C2W-NE", C2WResNEOnly.get(ann), ann.getName());

		wikiApi.flush();
		CachedWAT2Annotator.flush();
		WATRelatednessComputer.flush();
		BenchmarkCache.flush();
	}

	private static void printMetricsResultSet(String exp, MetricsResultSet rs, String annName) {
		System.out.format(LOCALE, "%s\t mac-P/R: %.3f\t%.3f mic-P/R: %.3f\t%.3f TP/FP/FN: %d\t%d\t%d\t%s%n", exp,
		        rs.getMacroPrecision(), rs.getMacroRecall(), rs.getMicroPrecision(), rs.getMicroRecall(), rs.getGlobalTp(),
		        rs.getGlobalFp(), rs.getGlobalFn(), annName);
	}
}
