package it.unipi.di.acube.smaph.main.experiments;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;

import org.apache.commons.math3.stat.descriptive.DescriptiveStatistics;

import it.unipi.di.acube.batframework.cache.BenchmarkCache;
import it.unipi.di.acube.batframework.datasetPlugins.DatasetBuilder;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.systemPlugins.CachedWATAnnotator;
import it.unipi.di.acube.batframework.utils.TestDataset;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.batframework.utils.WikipediaLocalInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphBuilder;
import it.unipi.di.acube.smaph.SmaphBuilder.SmaphVersion;
import it.unipi.di.acube.smaph.SmaphBuilder.Websearch;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;

public class RuntimeExperiment {
	private static final Locale LOCALE = Locale.US;

	public static void main(String[] args) throws Exception {
		java.security.Security.setProperty("networkaddress.cache.ttl", "0");
		Locale.setDefault(LOCALE);

		SmaphConfig c = SmaphConfig.fromConfigFile("smaph-config.xml");
		WikipediaInterface wikiApi = WikipediaLocalInterface.open(c.getDefaultWikipagesStorage());
		WikipediaToFreebase w2f = WikipediaToFreebase.open(c.getDefaultWikipediaToFreebaseStorage());
		EntityToAnchors e2a = EntityToAnchors.fromDB(c.getDefaultEntityToAnchorsStorage());
		WATRelatednessComputer.setCache("relatedness.cache");
		A2WDataset ds = DatasetBuilder.getGerdaqDevel(wikiApi);
		CachedWATAnnotator.setCache("wikisense.cache");

		System.out.println("Printing basic information about dataset " + ds.getName());
		TestDataset.dumpInfo(ds, wikiApi);

		HashMap<String, DescriptiveStatistics> annToStats = new HashMap<>();

		for (SmaphBuilder.Websearch ws : new SmaphBuilder.Websearch[] { Websearch.BING, Websearch.GOOGLE_CSE }) {
			for (SmaphVersion version : new SmaphVersion[] { SmaphVersion.ENTITY_FILTER, SmaphVersion.ANNOTATION_REGRESSOR,
			        SmaphVersion.COLLECTIVE, SmaphVersion.GREEDY }) {
				SmaphAnnotator smaph = SmaphBuilder.getSmaph(version, wikiApi, w2f, SmaphBuilder.DEFAULT_CACHED_AUX_ANNOTATOR,
				        e2a, true, ws, c, -1);
				BenchmarkCache.doC2WTags(smaph, ds);
				List<Long> times = BenchmarkCache.getC2WTimingsForDataset(smaph.getName(), ds.getName());

				DescriptiveStatistics s = new DescriptiveStatistics();
				for (Long t : times)
					s.addValue(t);
				annToStats.put(smaph.getName(), s);
			}
		}
		for (String name : annToStats.keySet()) {
			DescriptiveStatistics s = annToStats.get(name);
			System.out.format(LOCALE, "Annotator: %s Mean: %f StDev: %f Min: %f Max: %f%n", name, s.getMean(),
			        s.getStandardDeviation(), s.getMin(), s.getMax());
		}

		wikiApi.flush();
		CachedWATAnnotator.flush();
		WATRelatednessComputer.flush();
		BenchmarkCache.flush();
	}
}
