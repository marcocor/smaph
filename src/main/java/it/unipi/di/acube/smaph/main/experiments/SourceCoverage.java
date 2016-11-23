package it.unipi.di.acube.smaph.main.experiments;

import java.io.FileNotFoundException;
import java.io.IOException;
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
import it.unipi.di.acube.batframework.systemPlugins.CachedWATAnnotator;
import it.unipi.di.acube.batframework.utils.TestDataset;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphBuilder;
import it.unipi.di.acube.smaph.SmaphBuilder.Websearch;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.learn.models.entityfilters.NoEntityFilter;
import it.unipi.di.acube.smaph.linkback.DummyLinkBack;
import it.unipi.di.acube.smaph.snippetannotationfilters.FrequencyAnnotationFilter;

public class SourceCoverage {
	private static final Locale LOCALE = Locale.US;

	public static void main(String[] args) throws Exception {
		Options options = new Options().addOption("w", "websearch-piggyback", true,
		        "What web search engine to piggyback on. Can be either `bing' or `google'.");
		CommandLine line = new GnuParser().parse(options, args);

		java.security.Security.setProperty("networkaddress.cache.ttl", "0");
		Locale.setDefault(LOCALE);
		WikipediaApiInterface wikiApi = WikipediaApiInterface.api();
		WATRelatednessComputer.setCache("relatedness.cache");
		A2WDataset ds = DatasetBuilder.getGerdaqDevel();
		CachedWATAnnotator.setCache("wikisense.cache");

		System.out.println("Printing basic information about dataset " + ds.getName());
		TestDataset.dumpInfo(ds, wikiApi);

		SmaphConfig.setConfigFile("smaph-config.xml");
		SmaphBuilder.Websearch ws = SmaphBuilder.websearchFromString(line.getOptionValue("websearch-piggyback"));
		String wsStr = ws.toString();
		List<C2WSystem> annotators = new Vector<C2WSystem>();
		annotators.add(SmaphBuilder.getSmaphGatherer(wikiApi, true, false, false, ws).appendName("-" + wsStr + "-S2-def"));
		annotators.add(SmaphBuilder.getSmaphGatherer(wikiApi, false, true, false, ws).appendName("-" + wsStr + "-S3-def"));
		annotators.add(SmaphBuilder.getSmaphGatherer(wikiApi, false, false, true, ws).appendName("-" + wsStr + "-S6-def"));
		annotators.add(SmaphBuilder.getSmaphGatherer(wikiApi, true, true, true, ws).appendName("-" + wsStr + "-S236-def"));

		for (int topKS2 : new int[] { 0, 1, 2, 3, 4, 5, 10, 20, 30 })
			annotators.add(getAnnotator(true, topKS2, false, 0, false, 0, ws).appendName("-" + wsStr + "-S2-top" + topKS2));
		for (int topKS3 : new int[] { 0, 1, 2, 3, 4, 6, 8, 10 })
			annotators.add(getAnnotator(false, 0, true, topKS3, false, 0, ws).appendName("-" + wsStr + "-S3-top" + topKS3));
		for (int topKS6 : new int[] { 0, 1, 2, 3, 4, 5, 10, 20, 30 })
			annotators.add(getAnnotator(false, 0, false, 0, true, topKS6, ws).appendName("-" + wsStr + "-S6-top" + topKS6));

		HashMap<C2WSystem, MetricsResultSet> C2WRes = new HashMap<>();
		HashMap<C2WSystem, MetricsResultSet> C2WResNEOnly = new HashMap<>();

		for (C2WSystem ann : annotators) {
			List<HashSet<Tag>> resTag = BenchmarkCache.doC2WTags(ann, ds);
			Metrics<Tag> metrics = new Metrics<Tag>();
			C2WRes.put(ann, metrics.getResult(resTag, ds.getC2WGoldStandardList(), new StrongTagMatch(wikiApi)));
			C2WResNEOnly.put(ann, metrics.getResult(resTag, ds.getC2WGoldStandardList(), new StrongTagMatchNEOnly(wikiApi)));
			BenchmarkCache.flush();
		}

		for (C2WSystem ann : annotators)
			printMetricsResultSet("C2W", C2WRes.get(ann), ann.getName());
		for (C2WSystem ann : annotators)
			printMetricsResultSet("C2W-NE", C2WResNEOnly.get(ann), ann.getName());

		wikiApi.flush();
		CachedWATAnnotator.flush();
		WATRelatednessComputer.flush();
		BenchmarkCache.flush();
	}

	private static void printMetricsResultSet(String exp, MetricsResultSet rs, String annName) {
		System.out.format(LOCALE, "%s\t mac-P/R: %.3f\t%.3f mic-P/R: %.3f\t%.3f TP/FP/FN: %d\t%d\t%d\t%s%n", exp,
		        rs.getMacroPrecision(), rs.getMacroRecall(), rs.getMicroPrecision(), rs.getMicroRecall(), rs.getGlobalTp(),
		        rs.getGlobalFp(), rs.getGlobalFn(), annName);
	}

	private static SmaphAnnotator getAnnotator(boolean s2, int topKS2, boolean s3, int topKS3, boolean s6, int topKS6,
	        Websearch ws) throws FileNotFoundException, ClassNotFoundException, IOException {
		return new SmaphAnnotator(new NoEntityFilter(), null, new DummyLinkBack(), s2, s3, topKS3, s6, topKS6, topKS2, false,
		        SmaphBuilder.DEFAULT_AUX_ANNOTATOR, new FrequencyAnnotationFilter(SmaphBuilder.DEFAULT_ANNOTATIONFILTER_RATIO),
		        WikipediaApiInterface.api(), SmaphBuilder.getWebsearch(ws), SmaphBuilder.DEFAULT_ANCHOR_MENTION_ED, null);
	}

}
