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
	public static void gatherExamples(SmaphAnnotator smaph,
			A2WDataset ds, ExampleGatherer<Tag, HashSet<Tag>> entityFilterGatherer, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> linkBackCollectiveGatherer, ExampleGatherer<Annotation, HashSet<Annotation>> advancedIndividualAnnotationGatherer,
			boolean keepNEOnly) throws Exception {
		gatherExamples(smaph, ds, entityFilterGatherer,
				linkBackCollectiveGatherer, advancedIndividualAnnotationGatherer, keepNEOnly, -1);
	}
	public static void gatherExamples(SmaphAnnotator smaph,
			A2WDataset ds, ExampleGatherer<Tag, HashSet<Tag>> entityFilterGatherer, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> linkBackCollectiveGatherer, ExampleGatherer<Annotation, HashSet<Annotation>> advancedAnnotationRegressorGatherer,
			boolean keepNEOnly, int limit) throws Exception {
		limit = limit ==-1? ds.getSize() : Math.min(limit, ds.getSize());
		ProgressLogger plog = new ProgressLogger(logger, "document");
		plog.start("Collecting examples.");
		for (int i = 0; i < limit; i++) {
			String query = ds.getTextInstanceList().get(i);
			HashSet<Tag> goldStandard = ds.getC2WGoldStandardList().get(i);
			HashSet<Annotation> goldStandardAnn = ds.getA2WGoldStandardList().get(i);
			List<Pair<FeaturePack<Tag>, Boolean>> EFVectorsToPresence = null;
			List<Tag> EFCandidates = null;
			List<Annotation> ARCandidates = null;
			List<HashSet<Annotation>> BRCandidates = null;
			List<Pair<FeaturePack<HashSet<Annotation>>, Double>> LbVectorsToF1 = null;
			List<Pair<FeaturePack<Annotation>, Boolean>> advAnnVectorsToPresence = null;

			if (entityFilterGatherer != null){
				EFVectorsToPresence = new Vector<>();
				EFCandidates = new Vector<>();
			}
			if (linkBackCollectiveGatherer != null){
				LbVectorsToF1 = new Vector<>();
				BRCandidates = new Vector<>();
			}
			if (advancedAnnotationRegressorGatherer != null) {
				ARCandidates = new Vector<>();
				advAnnVectorsToPresence = new Vector<>();
			}

			smaph.generateExamples(query, goldStandard, goldStandardAnn, EFVectorsToPresence, EFCandidates, LbVectorsToF1,
			        BRCandidates, advAnnVectorsToPresence, ARCandidates, keepNEOnly, null);

			if (entityFilterGatherer != null)
				entityFilterGatherer.addExample(goldBoolToDouble(EFVectorsToPresence), EFCandidates, goldStandard);
			if (linkBackCollectiveGatherer != null)
				linkBackCollectiveGatherer.addExample(LbVectorsToF1, BRCandidates, goldStandardAnn);
			if (advancedAnnotationRegressorGatherer != null)
				advancedAnnotationRegressorGatherer.addExample(goldBoolToDouble(advAnnVectorsToPresence), ARCandidates, goldStandardAnn);
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
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> trainLinkBackCollectiveGatherer,
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> develLinkBackCollectiveGatherer,
			ExampleGatherer<Annotation, HashSet<Annotation>> trainIndividualAdvancedAnnotationGatherer,
			ExampleGatherer<Annotation, HashSet<Annotation>> develIndividualAdvancedAnnotationGatherer,
			List<String> trainInstances, List<String> develInstances,
			WikipediaInterface wikiApi, WikipediaToFreebase w2f,
			OptDataset opt) throws Exception {
		if (trainEntityFilterGatherer != null || trainLinkBackCollectiveGatherer != null || trainIndividualAdvancedAnnotationGatherer != null) {

			if (opt == OptDataset.ERD_CHALLENGE) {

				boolean keepNEOnly = true;
				A2WDataset smaphTrainA = new ERDDatasetFilter(DatasetBuilder.getGerdaqTrainA(wikiApi), wikiApi, w2f);
				gatherExamples(smaph, smaphTrainA,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, keepNEOnly);

				A2WDataset smaphTrainB = new ERDDatasetFilter(DatasetBuilder.getGerdaqTrainB(wikiApi), wikiApi, w2f);
				gatherExamples(smaph, smaphTrainB,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, keepNEOnly);

				A2WDataset smaphTest = new ERDDatasetFilter(DatasetBuilder.getGerdaqTest(wikiApi), wikiApi, w2f);
				gatherExamples(smaph, smaphTest,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, keepNEOnly);

				A2WDataset smaphDevel = new ERDDatasetFilter(DatasetBuilder.getGerdaqDevel(wikiApi), wikiApi, w2f);
				gatherExamples(smaph, smaphDevel,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, keepNEOnly);

				A2WDataset yahoo = new ERDDatasetFilter(
						new YahooWebscopeL24Dataset(
								classLoader.getResource("datasets/yahoo_webscope_L24/ydata-search-query-log-to-entities-v1_0.xml").getFile()),
								wikiApi, w2f);
				gatherExamples(smaph, yahoo, trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, keepNEOnly);

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
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, keepNEOnly);

				A2WDataset smaphTrainB = DatasetBuilder.getGerdaqTrainB(wikiApi);
				gatherExamples(smaph, smaphTrainB,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, keepNEOnly);

				if (trainInstances != null){
					trainInstances.addAll(smaphTrainA.getTextInstanceList());
					trainInstances.addAll(smaphTrainB.getTextInstanceList());
				}
			}
		}
		if (develEntityFilterGatherer != null || develLinkBackCollectiveGatherer != null|| develIndividualAdvancedAnnotationGatherer != null) {
			if (opt == OptDataset.ERD_CHALLENGE) {
				boolean keepNEOnly = true;
				A2WDataset smaphDevel = new ERDDatasetFilter(DatasetBuilder.getGerdaqDevel(wikiApi), wikiApi, w2f);
				gatherExamples(smaph, smaphDevel,
						develEntityFilterGatherer,
						develLinkBackCollectiveGatherer,
						develIndividualAdvancedAnnotationGatherer,
						keepNEOnly);
				if (develInstances != null){
					develInstances.addAll(smaphDevel.getTextInstanceList());
				}
			}
			else if (opt == OptDataset.SMAPH_DATASET){
				boolean keepNEOnly = false;
				A2WDataset smaphDevel = DatasetBuilder.getGerdaqDevel(wikiApi);
				gatherExamples(smaph, smaphDevel,
						develEntityFilterGatherer, develLinkBackCollectiveGatherer,
						develIndividualAdvancedAnnotationGatherer,  keepNEOnly);
				if (develInstances != null){
					develInstances.addAll(smaphDevel.getTextInstanceList());
				}
			}
		}
	}
}
