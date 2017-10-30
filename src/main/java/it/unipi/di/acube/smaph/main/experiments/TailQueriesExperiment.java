package it.unipi.di.acube.smaph.main.experiments;

import java.util.HashSet;
import java.util.Locale;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.Options;

import it.unipi.di.acube.batframework.cache.BenchmarkCache;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.datasetPlugins.DatasetBuilder;
import it.unipi.di.acube.batframework.metrics.Metrics;
import it.unipi.di.acube.batframework.metrics.StrongTagMatch;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.systemPlugins.CachedWAT2Annotator;
import it.unipi.di.acube.batframework.utils.ProblemReduction;
import it.unipi.di.acube.batframework.utils.TestDataset;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.batframework.utils.WikipediaInterfaceWAT;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphBuilder;
import it.unipi.di.acube.smaph.SmaphBuilder.SmaphVersion;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.SmaphDebugger;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;

public class TailQueriesExperiment {
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
		SmaphAnnotator smaph = SmaphBuilder.getSmaph(SmaphVersion.GREEDY, wikiApi, w2f,
		        SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR, e2a, true, ws, c, -1);

		Metrics<Tag> metrics = new Metrics<Tag>();
		StrongTagMatch sam = new StrongTagMatch(wikiApi);
		SmaphDebugger debugger = new SmaphDebugger();

		System.out.format(LOCALE, "query\twebResults\tF1\tprecision\trecall\tTP\tFP\tFN%n");
		for (int i = 0; i < ds.getSize(); i++) {
			String query = ds.getTextInstanceList().get(i);
			HashSet<Tag> result = ProblemReduction.A2WToC2W(ProblemReduction.Sa2WToA2W(smaph.solveSa2W(query, debugger)));
			HashSet<Tag> expected = ds.getC2WGoldStandardList().get(i);

			Double webResults = debugger.getQueryInformation(query).webTotalNS;

			float f1 = metrics.getSingleF1(expected, result, sam);
			int tp = metrics.getSingleTp(expected, result, sam).size();
			int fp = metrics.getSingleFp(expected, result, sam).size();
			int fn = metrics.getSingleFn(expected, result, sam).size();
			float prec = metrics.getSinglePrecision(expected, result, sam);
			float rec = metrics.getSingleRecall(expected, result, sam);
			System.out.format(LOCALE, "%s\t%.0f\t%f\t%f\t%f\t%d\t%d\t%d%n", query, webResults, f1, prec, rec, tp, fp, fn);
		}

		wikiApi.flush();
		CachedWAT2Annotator.flush();
		WATRelatednessComputer.flush();
		BenchmarkCache.flush();
	}
}
