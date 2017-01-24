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

package it.unipi.di.acube.smaph.learn;

import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.logging.ProgressLogger;
import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.datasetPlugins.DatasetBuilder;
import it.unipi.di.acube.batframework.datasetPlugins.YahooWebscopeL24Dataset;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.main.ERDDatasetFilter;

public class GenerateTrainingAndTest {
	private static Logger logger = LoggerFactory.getLogger(GenerateTrainingAndTest.class);
	private static final ClassLoader classLoader = GenerateTrainingAndTest.class.getClassLoader();

	public enum OptDataset {ERD_CHALLENGE, SMAPH_DATASET}

	public static void gatherExamples(SmaphAnnotator smaph, A2WDataset ds,
	        ExampleGatherer<Tag, HashSet<Tag>> entityFilterGatherer,
	        ExampleGatherer<Annotation, HashSet<Annotation>> annotationRegressorGatherer,
	        ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> collectiveGatherer,
	        HashSet<Annotation> greedyPartialSolution,
	        ExampleGatherer<Annotation, HashSet<Annotation>> greedyGatherer,
	        boolean keepNEOnly)
	        throws Exception {
		ProgressLogger plog = new ProgressLogger(logger, "document");
		plog.start("Collecting examples.");
		for (int i = 0; i < ds.getSize(); i++) {
			String query = ds.getTextInstanceList().get(i);
			HashSet<Tag> goldStandard = ds.getC2WGoldStandardList().get(i);
			HashSet<Annotation> goldStandardAnn = ds.getA2WGoldStandardList().get(i);
			List<Pair<FeaturePack<Tag>, Boolean>> efVectorsToPresence = null;
			List<Tag> efCandidates = null;
			List<Pair<FeaturePack<Annotation>, Boolean>> arVectorsToPresence = null;
			List<Annotation> arCandidates = null;
			List<Pair<FeaturePack<HashSet<Annotation>>, Double>> collVectorsToF1 = null;
			List<HashSet<Annotation>> collCandidates = null;
			List<Pair<FeaturePack<Annotation>, Double>> greedyVectorsToF1Incr = null;
			List<Annotation> greedyCandidates = null;

			if (entityFilterGatherer != null){
				efVectorsToPresence = new Vector<>();
				efCandidates = new Vector<>();
			}
			if (annotationRegressorGatherer != null) {
				arVectorsToPresence = new Vector<>();
				arCandidates = new Vector<>();
			}
			if (collectiveGatherer != null){
				collVectorsToF1 = new Vector<>();
				collCandidates = new Vector<>();
			}
			if (greedyGatherer != null){
				greedyVectorsToF1Incr = new Vector<>();
				greedyCandidates = new Vector<>();
			}

			smaph.generateExamples(query, goldStandard, goldStandardAnn, efVectorsToPresence, efCandidates, arVectorsToPresence,
			        arCandidates, collVectorsToF1, collCandidates, greedyPartialSolution, greedyVectorsToF1Incr, greedyCandidates, keepNEOnly, null);

			if (entityFilterGatherer != null)
				entityFilterGatherer.addExample(goldBoolToDouble(efVectorsToPresence), efCandidates, goldStandard);
			if (collectiveGatherer != null)
				collectiveGatherer.addExample(collVectorsToF1, collCandidates, goldStandardAnn);
			if (annotationRegressorGatherer != null)
				annotationRegressorGatherer.addExample(goldBoolToDouble(arVectorsToPresence), arCandidates, goldStandardAnn);
			if (greedyGatherer != null)
				greedyGatherer.addExample(greedyVectorsToF1Incr, greedyCandidates, goldStandardAnn);

			plog.lightUpdate();
		}
		plog.done();
	}

	private static <E extends Object> List<Pair<FeaturePack<E>, Double>> goldBoolToDouble(
			List<Pair<FeaturePack<E>, Boolean>> eFVectorsToPresence) {
		List<Pair<FeaturePack<E>, Double>> res = new Vector<>();
		for (Pair<FeaturePack<E>, Boolean> p : eFVectorsToPresence)
			res.add(new Pair<FeaturePack<E>, Double>(p.first, p.second ? 1.0
					: -1.0));
		return res;
	}

	public static void gatherExamplesTrainingAndDevel(
			SmaphAnnotator smaph,
			ExampleGatherer<Tag, HashSet<Tag>> trainEntityFilterGatherer,
			ExampleGatherer<Tag, HashSet<Tag>> develEntityFilterGatherer,
			ExampleGatherer<Annotation, HashSet<Annotation>> trainAnnotationRegressorGatherer,
			ExampleGatherer<Annotation, HashSet<Annotation>> develAnnotationRegressorGatherer,
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> trainCollectiveGatherer,
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> develCollectiveGatherer,
			HashSet<Annotation> greedyPartialSolution,
			ExampleGatherer<Annotation, HashSet<Annotation>> trainGreedyGatherer,
			ExampleGatherer<Annotation, HashSet<Annotation>> develGreedyGatherer,
			List<String> trainInstances, List<String> develInstances,
			WikipediaInterface wikiApi, WikipediaToFreebase w2f,
			OptDataset opt) throws Exception {
		if (trainEntityFilterGatherer != null || trainAnnotationRegressorGatherer != null || trainCollectiveGatherer != null || trainGreedyGatherer != null) {

			if (opt == OptDataset.ERD_CHALLENGE) {

				boolean keepNEOnly = true;
				A2WDataset smaphTrainA = new ERDDatasetFilter(DatasetBuilder.getGerdaqTrainA(wikiApi), wikiApi, w2f);
				gatherExamples(smaph, smaphTrainA,
						trainEntityFilterGatherer, trainAnnotationRegressorGatherer,
						trainCollectiveGatherer, greedyPartialSolution, trainGreedyGatherer, keepNEOnly);

				A2WDataset smaphTrainB = new ERDDatasetFilter(DatasetBuilder.getGerdaqTrainB(wikiApi), wikiApi, w2f);
				gatherExamples(smaph, smaphTrainB,
						trainEntityFilterGatherer, trainAnnotationRegressorGatherer,
						trainCollectiveGatherer, greedyPartialSolution, trainGreedyGatherer, keepNEOnly);

				A2WDataset smaphTest = new ERDDatasetFilter(DatasetBuilder.getGerdaqTest(wikiApi), wikiApi, w2f);
				gatherExamples(smaph, smaphTest,
						trainEntityFilterGatherer, trainAnnotationRegressorGatherer,
						trainCollectiveGatherer, greedyPartialSolution, trainGreedyGatherer, keepNEOnly);

				A2WDataset smaphDevel = new ERDDatasetFilter(DatasetBuilder.getGerdaqDevel(wikiApi), wikiApi, w2f);
				gatherExamples(smaph, smaphDevel,
						trainEntityFilterGatherer, trainAnnotationRegressorGatherer,
						trainCollectiveGatherer, greedyPartialSolution, trainGreedyGatherer, keepNEOnly);

				A2WDataset yahoo = new ERDDatasetFilter(
						new YahooWebscopeL24Dataset(
								classLoader.getResource("datasets/yahoo_webscope_L24/ydata-search-query-log-to-entities-v1_0.xml").getFile()),
								wikiApi, w2f);
				gatherExamples(smaph, yahoo, trainEntityFilterGatherer, trainAnnotationRegressorGatherer,
						trainCollectiveGatherer, greedyPartialSolution, trainGreedyGatherer, keepNEOnly);

				if (trainInstances != null){
					trainInstances.addAll(smaphTrainA.getTextInstanceList());
					trainInstances.addAll(smaphTrainB.getTextInstanceList());
					trainInstances.addAll(smaphTest.getTextInstanceList());
					trainInstances.addAll(smaphDevel.getTextInstanceList());
					trainInstances.addAll(yahoo.getTextInstanceList());
				}

			} else if (opt == OptDataset.SMAPH_DATASET) {
				boolean keepNEOnly = false;
				A2WDataset smaphTrainA = DatasetBuilder.getGerdaqTrainA(wikiApi);
				gatherExamples(smaph, smaphTrainA,
						trainEntityFilterGatherer, trainAnnotationRegressorGatherer,
						trainCollectiveGatherer, greedyPartialSolution, trainGreedyGatherer, keepNEOnly);

				A2WDataset smaphTrainB = DatasetBuilder.getGerdaqTrainB(wikiApi);
				gatherExamples(smaph, smaphTrainB,
						trainEntityFilterGatherer, trainAnnotationRegressorGatherer,
						trainCollectiveGatherer, greedyPartialSolution, trainGreedyGatherer, keepNEOnly);

				if (trainInstances != null){
					trainInstances.addAll(smaphTrainA.getTextInstanceList());
					trainInstances.addAll(smaphTrainB.getTextInstanceList());
				}
			}
		}
		if (develEntityFilterGatherer != null || develAnnotationRegressorGatherer != null || develCollectiveGatherer != null || develGreedyGatherer != null) {
			if (opt == OptDataset.ERD_CHALLENGE) {
				boolean keepNEOnly = true;
				A2WDataset smaphDevel = new ERDDatasetFilter(DatasetBuilder.getGerdaqDevel(wikiApi), wikiApi, w2f);
				gatherExamples(smaph, smaphDevel,
						develEntityFilterGatherer,
						develAnnotationRegressorGatherer,
						develCollectiveGatherer,
						greedyPartialSolution,
						develGreedyGatherer, 
						keepNEOnly);
				if (develInstances != null){
					develInstances.addAll(smaphDevel.getTextInstanceList());
				}
			}
			else if (opt == OptDataset.SMAPH_DATASET){
				boolean keepNEOnly = false;
				A2WDataset smaphDevel = DatasetBuilder.getGerdaqDevel(wikiApi);
				gatherExamples(smaph, smaphDevel, develEntityFilterGatherer, develAnnotationRegressorGatherer,
				        develCollectiveGatherer, greedyPartialSolution, develGreedyGatherer, keepNEOnly);
				if (develInstances != null){
					develInstances.addAll(smaphDevel.getTextInstanceList());
				}
			}
		}
	}
}
