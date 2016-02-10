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

import it.cnr.isti.hpc.erd.WikipediaToFreebase;
import it.unimi.dsi.logging.ProgressLogger;
import it.unipi.di.acube.BingInterface;
import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.datasetPlugins.GERDAQDataset;
import it.unipi.di.acube.batframework.datasetPlugins.YahooWebscopeL24Dataset;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.utils.FreebaseApi;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.DefaultBindingGenerator;
import it.unipi.di.acube.smaph.main.ERDDatasetFilter;

import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenerateTrainingAndTest {
	private static Logger logger = LoggerFactory.getLogger(GenerateTrainingAndTest.class);

	public enum OptDataset {ERD_CHALLENGE, SMAPH_DATASET, SMAPH_DATASET_NE}
	public static void gatherExamples(SmaphAnnotator bingAnnotator,
			A2WDataset ds, ExampleGatherer<Tag, HashSet<Tag>> entityFilterGatherer, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> linkBackCollectiveGatherer, ExampleGatherer<Annotation, HashSet<Annotation>> advancedIndividualAnnotationGatherer,
			WikipediaToFreebase wikiToFreeb, boolean keepNEOnly, double anchorMaxED) throws Exception {
		gatherExamples(bingAnnotator, ds, entityFilterGatherer,
				linkBackCollectiveGatherer, advancedIndividualAnnotationGatherer, keepNEOnly, -1, anchorMaxED);
	}
	public static void gatherExamples(SmaphAnnotator bingAnnotator,
			A2WDataset ds, ExampleGatherer<Tag, HashSet<Tag>> entityFilterGatherer, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> linkBackCollectiveGatherer, ExampleGatherer<Annotation, HashSet<Annotation>> advancedAnnotationRegressorGatherer,
			boolean keepNEOnly, int limit, double anchorMaxED) throws Exception {
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

			bingAnnotator.generateExamples(query, goldStandard, goldStandardAnn, EFVectorsToPresence, EFCandidates,
					LbVectorsToF1, BRCandidates, advAnnVectorsToPresence, ARCandidates, keepNEOnly,
					new DefaultBindingGenerator(), anchorMaxED);

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
			res.add(new Pair<FeaturePack<E>, Double>(p.first, p.second? 1.0
					: -1.0));
		return res;
	}

	public static void gatherExamplesTrainingAndDevel(
			SmaphAnnotator bingAnnotator,
			ExampleGatherer<Tag, HashSet<Tag>> trainEntityFilterGatherer,
			ExampleGatherer<Tag, HashSet<Tag>> develEntityFilterGatherer,
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> trainLinkBackCollectiveGatherer,
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> develLinkBackCollectiveGatherer,
			ExampleGatherer<Annotation, HashSet<Annotation>> trainIndividualAdvancedAnnotationGatherer,
			ExampleGatherer<Annotation, HashSet<Annotation>> develIndividualAdvancedAnnotationGatherer,
			List<String> trainInstances, List<String> develInstances,
			WikipediaApiInterface wikiApi, WikipediaToFreebase wikiToFreebase,
			FreebaseApi freebApi, OptDataset opt, double anchorMaxED) throws Exception {
		if (trainEntityFilterGatherer != null || trainLinkBackCollectiveGatherer != null || trainIndividualAdvancedAnnotationGatherer != null) {

			if (opt == OptDataset.ERD_CHALLENGE) {

				boolean keepNEOnly = true;
				A2WDataset smaphTrainA = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_trainingA.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphTrainA,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, keepNEOnly, anchorMaxED);

				A2WDataset smaphTrainB = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_trainingB.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphTrainB,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, keepNEOnly, anchorMaxED);

				A2WDataset smaphTest = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_test.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphTest,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, keepNEOnly, anchorMaxED);

				A2WDataset smaphDevel = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_devel.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphDevel,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, keepNEOnly, anchorMaxED);

				A2WDataset yahoo = new ERDDatasetFilter(
						new YahooWebscopeL24Dataset(
								"datasets/yahoo_webscope_L24/ydata-search-query-log-to-entities-v1_0.xml"),
								wikiApi);
				gatherExamples(bingAnnotator, yahoo, trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, keepNEOnly, anchorMaxED);

				/*A2WDataset single = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/single_test.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, single,
						trainEntityFilterGatherer, trainLinkBackGatherer,
						trainAnnotationGatherer, wikiToFreebase, ar, annFn);*/

				/*
				 * A2WDataset erd = new ERDDatasetFilter(new ERD2014Dataset(
				 * "datasets/erd2014/Trec_beta.query.txt",
				 * "datasets/erd2014/Trec_beta.annotation.txt", freebApi,
				 * wikiApi), wikiApi, wikiToFreebase);
				 * gatherExamples(bingAnnotator, erd, trainEntityFilterGatherer,
				 * trainLinkBackGatherer, wikiToFreebase);
				 */
				if (trainInstances != null){
					trainInstances.addAll(smaphTrainA.getTextInstanceList());
					trainInstances.addAll(smaphTrainB.getTextInstanceList());
					trainInstances.addAll(smaphTest.getTextInstanceList());
					trainInstances.addAll(smaphDevel.getTextInstanceList());
					trainInstances.addAll(yahoo.getTextInstanceList());
				}

			} else if (opt == OptDataset.SMAPH_DATASET) {
				boolean keepNEOnly = false;
				A2WDataset smaphTrainA = new GERDAQDataset(
						"datasets/gerdaq/gerdaq_trainingA.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphTrainA,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, keepNEOnly, anchorMaxED);

				A2WDataset smaphTrainB = new GERDAQDataset(
						"datasets/gerdaq/gerdaq_trainingB.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphTrainB,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, keepNEOnly, anchorMaxED);

				/*A2WDataset smaphDevel = new GERDAQDataset(
						"datasets/gerdaq/gerdaq_devel.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphDevel,
						trainEntityFilterGatherer, null, trainLinkBackGatherer,
						trainAnnotationGatherer,  wikiToFreebase, ar, annFn, keepNEOnly);*/

				/*				A2WDataset yahoo = new YahooWebscopeL24Dataset(
						"datasets/yahoo_webscope_L24/ydata-search-query-log-to-entities-v1_0.xml");
				gatherExamples(bingAnnotator, yahoo, trainEntityFilterGatherer,
						trainLinkBackGatherer, wikiToFreebase);*/

				/*A2WDataset smaphSingle = new GERDAQDataset(
						"datasets/gerdaq/single_test.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphSingle,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, keepNEOnly, anchorMaxED);*/
				if (trainInstances != null){
					trainInstances.addAll(smaphTrainA.getTextInstanceList());
					trainInstances.addAll(smaphTrainB.getTextInstanceList());
				}
			} else if (opt == OptDataset.SMAPH_DATASET_NE) {
				boolean keepNEOnly = true;
				A2WDataset smaphTrainA = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_trainingA.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphTrainA,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, keepNEOnly, anchorMaxED);

				A2WDataset smaphTrainB = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_trainingB.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphTrainB,
						trainEntityFilterGatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, keepNEOnly, anchorMaxED);
				if (trainInstances != null){
					trainInstances.addAll(smaphTrainA.getTextInstanceList());
					trainInstances.addAll(smaphTrainB.getTextInstanceList());
				}
			}
		}
		if (develEntityFilterGatherer != null || develLinkBackCollectiveGatherer != null|| develIndividualAdvancedAnnotationGatherer != null) {
			if (opt == OptDataset.ERD_CHALLENGE) {
				boolean keepNEOnly = true;
				A2WDataset smaphDevel = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_devel.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphDevel,
						develEntityFilterGatherer,
						develLinkBackCollectiveGatherer,
						develIndividualAdvancedAnnotationGatherer, wikiToFreebase,
						keepNEOnly, anchorMaxED);
				if (develInstances != null){
					develInstances.addAll(smaphDevel.getTextInstanceList());
				}
			}
			else if (opt == OptDataset.SMAPH_DATASET){
				boolean keepNEOnly = false;
				/*A2WDataset smaphTest = new SMAPHDataset(
						"datasets/gerdaq/gerdaq_test.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphTest,
						develEntityFilterGatherer, develEntityRegressorGatherer, develLinkBackGatherer,develAnnotationGatherer,
						wikiToFreebase, ar, annFn, keepNEOnly);*/

				A2WDataset smaphDevel = new GERDAQDataset(
						"datasets/gerdaq/gerdaq_devel.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphDevel,
						develEntityFilterGatherer, develLinkBackCollectiveGatherer,
						develIndividualAdvancedAnnotationGatherer,  wikiToFreebase, keepNEOnly, anchorMaxED);
				/*A2WDataset smaphSingle = new GERDAQDataset(
						"datasets/gerdaq/single_test.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphSingle,
						develEntityFilterGatherer, develLinkBackCollectiveGatherer,
						develIndividualAdvancedAnnotationGatherer, wikiToFreebase, keepNEOnly, anchorMaxED);*/
				if (develInstances != null){
					develInstances.addAll(smaphDevel.getTextInstanceList());
				}
			}
			else if (opt == OptDataset.SMAPH_DATASET_NE){
				boolean keepNEOnly = true;
				A2WDataset smaphDevel = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_devel.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphDevel,
						develEntityFilterGatherer, develLinkBackCollectiveGatherer,
						develIndividualAdvancedAnnotationGatherer,  wikiToFreebase, keepNEOnly, anchorMaxED);
				if (develInstances != null){
					develInstances.addAll(smaphDevel.getTextInstanceList());
				}
			}

		}

		BingInterface.flush();
		wikiApi.flush();

	}
}
