package it.unipi.di.acube.smaph.main;

import it.unipi.di.acube.BingInterface;
import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.datasetPlugins.SMAPHDataset;
import it.unipi.di.acube.batframework.metrics.MatchRelation;
import it.unipi.di.acube.batframework.metrics.Metrics;
import it.unipi.di.acube.batframework.metrics.StrongAnnotationMatch;
import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.batframework.utils.DumpData;
import it.unipi.di.acube.batframework.utils.DumpResults;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphAnnotatorDebugger;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.learn.GenerateTrainingAndTest;

import java.util.HashSet;

import org.codehaus.jettison.json.JSONArray;

public class DebugMain {

	public static void main(String[] args) throws Exception {
		WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache",
				"redirect.cache");
		
		SMAPHDataset ds = new SMAPHDataset("datasets/smaph/smaph_devel.xml",
				wikiApi);

		SmaphConfig.setConfigFile("smaph-config.xml");
		String bingKey = SmaphConfig.getDefaultBingKey();
		String bingCache = SmaphConfig.getDefaultBingCache();
		BingInterface.setCache(bingCache);
		WATAnnotator.setCache("wikisense.cache");
		WATRelatednessComputer.setCache("relatedness.cache");
		
		String rankLibBindingModel = "models/model_1-225_RL_0.060.full.6.NDCG@10.model";
		String rankLibBindingNorm = "models/train_binding_ranking.zscore";

		SmaphAnnotator ann = GenerateTrainingAndTest.getDefaultBingAnnotatorCollectiveLBRanklibAllSources(wikiApi, 0.06, bingKey, rankLibBindingModel, rankLibBindingNorm);
		ann.appendName("-lb-collective-ranking");
		SmaphAnnotatorDebugger debugger = new SmaphAnnotatorDebugger();
		ann.setDebugger(debugger);
		
		//BenchmarkCache.doA2WAnnotations(ann, ds);
		Metrics<Annotation> m = new Metrics<>();
		MatchRelation<Annotation> sam = new StrongAnnotationMatch(wikiApi);
		
		int correctEmpty = 0, wrongEmpty = 0;
		
		for (int id = 0; id < ds.getSize(); id++) {
			String query = ds.getTextInstanceList().get(id);
			HashSet<Annotation> gold = ds.getA2WGoldStandardList().get(id);
			
			System.out.printf("*** STARTING ANNOTATION OF QUERY [%s] (ID %d) ***%n", query, id);
			HashSet<Annotation> res = ann.solveA2W(query);

			if (gold.isEmpty())
				if (res.isEmpty())
					correctEmpty++;
				else
					wrongEmpty++;
			
			System.out.printf("*** SOLUTION FOUND FOR QUERY [%s] (ID %d) ***%n", query, id);
			DumpData.dumpCompareMatch(query, gold, res, sam, wikiApi);
			double prec = m.getSinglePrecision(gold, res, sam);
			double rec = m.getSingleRecall(gold, res, sam);
			double f1 = m.getSingleF1(gold, res, sam);
			System.out.printf("%n*** Strong annotation metrics: P/R/F1: %.3f/%.3f/%.3f***%n%n", prec, rec, f1);
			
			JSONArray bindingDebug = debugger.getLinkbackBindingFeatures(query, gold, wikiApi);
			System.out.printf("*** DEBUG INFORMATION FOR QUERY [%s] (ID %d) ***%n", query, id);
			System.out.println(bindingDebug.toString(3));
			System.out.printf("*** END OF QUERY [%s] (ID %d) ***%n", query, id);
		}
		System.out.printf("Queries with empty gold and empty result: %d%n", correctEmpty);
		System.out.printf("Queries with empty gold and non-empty result: %d%n", wrongEmpty);
		wikiApi.flush();
		WATRelatednessComputer.flush();
	}
}