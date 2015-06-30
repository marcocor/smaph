package it.acubelab.smaph.main;

import java.io.IOException;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import it.unipi.di.acube.BingInterface;
import it.acubelab.batframework.cache.BenchmarkCache;
import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.datasetPlugins.SMAPHDataset;
import it.acubelab.batframework.systemPlugins.*;
import it.acubelab.batframework.utils.*;
import it.acubelab.smaph.*;
import it.acubelab.smaph.boldfilters.*;
import it.acubelab.smaph.entityfilters.*;
import it.acubelab.smaph.learn.featurePacks.FeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;
import it.acubelab.smaph.learn.normalizer.NoFeatureNormalizer;
import it.acubelab.smaph.learn.normalizer.ScaleFeatureNormalizer;
import it.acubelab.smaph.linkback.SvmCollectiveLinkBack;
import it.acubelab.smaph.linkback.bindingGenerator.DefaultBindingGenerator;

public class DebugMain {

	public static void main(String[] args) throws Exception {
		WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache",
				"redirect.cache");
		
		SMAPHDataset ds = new SMAPHDataset("datasets/smaph/smaph_test.xml",
				wikiApi);

		SmaphConfig.setConfigFile("smaph-config.xml");
		String bingKey = SmaphConfig.getDefaultBingKey();
		String bingCache = SmaphConfig.getDefaultBingCache();
		String LBmodelBase = "/tmp/train.dat.model";
		String LBrange = "models/model_1-92_LB_0.060_0.01086957_5.00000000_ANW.range";
		WATAnnotator auxAnnotatorService = new WATAnnotator(
				"wikisense.mkapp.it", 80, "base", "COMMONNESS", "jaccard", "0.6",
				"0.0", false, false, false);
		WATAnnotator.setCache("wikisense.cache");
		
		
		SvmCollectiveLinkBack lb = new SvmCollectiveLinkBack(wikiApi, new DefaultBindingGenerator(), null, null, new NoFeatureNormalizer(), null);////////////////////////////////////////////////////////////
		BingInterface.setCache(bingCache);
		SmaphAnnotator ann3 = new SmaphAnnotator(auxAnnotatorService,
				new FrequencyBoldFilter(0.06f), new NoEntityFilter(),
				null, lb, true, true, true, 10, false,
				-1, false, -1, false, 0, false, null, null, wikiApi, bingKey);
		ann3.appendName("-svmLb");
		
		
		BenchmarkCache.doA2WAnnotations(ann3, ds);
		
		Vector<Double> predScores = lb.getPredictionScores();
		for (double range = -10.0; range <=10.0; range+=0.1){
			int count=0;
			for (Double s : predScores){
				if (range<=s && s < range+0.1)
					count++;
			}
			System.out.printf("Score [%.2f:%.2f]: %d (%.1f%%)%n",range, range +0.1, count, ((double)count*100)/predScores.size());
		}
		
		
/*		int id = 228;
		String query = ds.getTextInstanceList().get(id);

		List<Pair<double[], Double>> lbVectorsToF1 = new Vector<>();
		HashMap<HashSet<Annotation>, Pair<double[], Double>> bindingsToFtrAndF1Debug = new HashMap<>();
		ann3.generateExamples(query, ds.getC2WGoldStandardList().get(id), ds.getA2WGoldStandardList().get(id), null, null, lbVectorsToF1 , false, lb, null, bindingsToFtrAndF1Debug );
		
		dumpBindingsToFtrAndF1(query,ds.getA2WGoldStandardList().get(id), bindingsToFtrAndF1Debug, wikiApi);
		
		HashSet<Annotation> res = ann3.solveA2W(query);*/
	}

	private static void dumpBindingsToFtrAndF1(String query,HashSet<Annotation> gold,
			HashMap<HashSet<Annotation>, Pair<double[], Double>> bindingsToFtrAndF1Debug, WikipediaApiInterface wikiApi) throws IOException {
		System.out.printf("%s%n",representBinding(gold, query, wikiApi));
		for (HashSet<Annotation> binding : bindingsToFtrAndF1Debug.keySet()){
			double f1 = bindingsToFtrAndF1Debug.get(binding).second;
			String bindingStr = representBinding(binding, query, wikiApi);
			System.out.printf("%s -> F1=%.3f%n",bindingStr, f1);
		}
	}
	
	private static String representBinding(HashSet<Annotation> binding, String query, WikipediaApiInterface wikiApi) throws IOException{
		List<Annotation> orderedBindings = new Vector<>(binding);
			Collections.sort(orderedBindings);
			int i=0;
			int lastIdx = 0;
			String repr = "";
			while(i<orderedBindings.size()){
				Annotation a = orderedBindings.get(i);
				repr+=query.substring(lastIdx, a.getPosition());
				repr += String.format("[%s](%s)",query.substring(a.getPosition(), a.getPosition()+a.getLength()), wikiApi.getTitlebyId(a.getConcept()));
				lastIdx = a.getPosition()+a.getLength();
				i++;
			}
			repr+=query.substring(lastIdx);
			return repr;
	}

}
