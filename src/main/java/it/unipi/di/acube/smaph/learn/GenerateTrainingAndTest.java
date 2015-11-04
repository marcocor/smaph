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

import it.unimi.dsi.logging.ProgressLogger;
import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.datasetPlugins.GERDAQDataset;
import it.unipi.di.acube.batframework.datasetPlugins.YahooWebscopeL24Dataset;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.batframework.utils.FreebaseApi;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.models.entityfilters.EntityFilter;
import it.unipi.di.acube.smaph.learn.models.entityfilters.LibSvmEntityFilter;
import it.unipi.di.acube.smaph.learn.models.entityfilters.NoEntityFilter;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.AnnotationRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.LibLinearAnnotatorRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor.LibLinearBindingRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor.RankLibBindingRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.DummyLinkBack;
import it.unipi.di.acube.smaph.linkback.LinkBack;
import it.unipi.di.acube.smaph.linkback.AdvancedIndividualLinkback;
import it.unipi.di.acube.smaph.linkback.CollectiveLinkBack;
import it.unipi.di.acube.smaph.linkback.IndividualAnnotationLinkBack;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.DefaultBindingGenerator;
import it.unipi.di.acube.smaph.main.ERDDatasetFilter;
import it.unipi.di.acube.smaph.snippetannotationfilters.FrequencyAnnotationFilter;
import it.unipi.di.acube.BingInterface;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenerateTrainingAndTest {
	private static Logger logger = LoggerFactory.getLogger(GenerateTrainingAndTest.class);

	public enum OptDataset {ERD_CHALLENGE, SMAPH_DATASET, SMAPH_DATASET_NE}
	public static void gatherExamples(SmaphAnnotator bingAnnotator,
			A2WDataset ds, ExampleGatherer<Tag, HashSet<Tag>> entityFilterGatherer, ExampleGatherer<Annotation, HashSet<Annotation>> annotationLevel1Gatherer, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> linkBackLevel2Gatherer, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> linkBackCollectiveGatherer, ExampleGatherer<Annotation, HashSet<Annotation>> individualAnnotationGatherer, ExampleGatherer<Annotation, HashSet<Annotation>> advancedIndividualAnnotationGatherer,
			WikipediaToFreebase wikiToFreeb, AnnotationRegressor ar, FeatureNormalizer annFn, boolean keepNEOnly, double anchorMaxED) throws Exception {
		gatherExamples(bingAnnotator, ds, entityFilterGatherer,annotationLevel1Gatherer,
				linkBackLevel2Gatherer, linkBackCollectiveGatherer, individualAnnotationGatherer, advancedIndividualAnnotationGatherer, wikiToFreeb, ar,annFn, keepNEOnly, -1, anchorMaxED);
	}
	public static void gatherExamples(SmaphAnnotator bingAnnotator,
			A2WDataset ds, ExampleGatherer<Tag, HashSet<Tag>> entityFilterGatherer,ExampleGatherer<Annotation, HashSet<Annotation>> annotationLevel1Gatherer, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> linkBackLevel2Gatherer, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> linkBackCollectiveGatherer,ExampleGatherer<Annotation, HashSet<Annotation>> annotationRegressorGatherer,ExampleGatherer<Annotation, HashSet<Annotation>> advancedAnnotationRegressorGatherer,
			WikipediaToFreebase wikiToFreeb, AnnotationRegressor arLevel1, FeatureNormalizer arNormLevel1, boolean keepNEOnly, int limit, double anchorMaxED) throws Exception {
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
			List<Pair<FeaturePack<Annotation>, Boolean>> annVectorsToPresence = null;
			List<Pair<FeaturePack<Annotation>, Boolean>> advAnnVectorsToPresence = null;

			if (entityFilterGatherer != null){
				EFVectorsToPresence = new Vector<>();
				EFCandidates = new Vector<>();
			}
			if (linkBackCollectiveGatherer != null || linkBackLevel2Gatherer != null){
				LbVectorsToF1 = new Vector<>();
				BRCandidates = new Vector<>();
			}
			if (annotationRegressorGatherer != null || annotationLevel1Gatherer != null) {
				annVectorsToPresence = new Vector<>();
				ARCandidates = new Vector<>();
			}
			if (advancedAnnotationRegressorGatherer != null) {
				ARCandidates = new Vector<>();
				advAnnVectorsToPresence = new Vector<>();
			}

			bingAnnotator.generateExamples(query, goldStandard, goldStandardAnn, EFVectorsToPresence, EFCandidates,
					LbVectorsToF1, BRCandidates, annVectorsToPresence, advAnnVectorsToPresence, ARCandidates, keepNEOnly,
					new DefaultBindingGenerator(), arLevel1, arNormLevel1, wikiToFreeb, anchorMaxED);

			if (entityFilterGatherer != null)
				entityFilterGatherer.addExample(goldBoolToDouble(EFVectorsToPresence), EFCandidates, goldStandard);
			if (annotationLevel1Gatherer != null)
				annotationLevel1Gatherer.addExample(goldBoolToDouble(annVectorsToPresence), ARCandidates, goldStandardAnn);
			if (linkBackLevel2Gatherer!= null)
				linkBackLevel2Gatherer.addExample(LbVectorsToF1, BRCandidates, goldStandardAnn);
			if (annotationRegressorGatherer!= null)
				annotationRegressorGatherer.addExample(goldBoolToDouble(annVectorsToPresence), ARCandidates, goldStandardAnn);
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
			ExampleGatherer<Annotation, HashSet<Annotation>> trainLevel1AnnotationGatherer,
			ExampleGatherer<Annotation, HashSet<Annotation>> develLevel1AnnotationGatherer,
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> trainLinkBackLevel2Gatherer,
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> develLinkBackLevel2Gatherer,
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> trainLinkBackCollectiveGatherer,
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> develLinkBackCollectiveGatherer,
			ExampleGatherer<Annotation, HashSet<Annotation>> trainIndividualAnnotationGatherer,
			ExampleGatherer<Annotation, HashSet<Annotation>> develIndividualAnnotationGatherer,
			ExampleGatherer<Annotation, HashSet<Annotation>> trainIndividualAdvancedAnnotationGatherer,
			ExampleGatherer<Annotation, HashSet<Annotation>> develIndividualAdvancedAnnotationGatherer,
			AnnotationRegressor arLevel1, FeatureNormalizer arNormLevel1,
			WikipediaApiInterface wikiApi, WikipediaToFreebase wikiToFreebase,
			FreebaseApi freebApi, OptDataset opt, double anchorMaxED) throws Exception {
		if (trainEntityFilterGatherer != null || trainLinkBackCollectiveGatherer != null || trainLinkBackLevel2Gatherer != null || trainIndividualAnnotationGatherer != null || trainIndividualAdvancedAnnotationGatherer != null) {

			if (opt == OptDataset.ERD_CHALLENGE) {

				boolean keepNEOnly = true;
				A2WDataset smaphTrainA = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_trainingA.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphTrainA,
						trainEntityFilterGatherer, trainLevel1AnnotationGatherer, null, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

				A2WDataset smaphTrainB = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_trainingB.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphTrainB,
						trainEntityFilterGatherer, null, trainLinkBackLevel2Gatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

				A2WDataset smaphTest = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_test.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphTest,
						trainEntityFilterGatherer, null, trainLinkBackLevel2Gatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

				A2WDataset smaphDevel = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_devel.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphDevel,
						trainEntityFilterGatherer,null, trainLinkBackLevel2Gatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

				A2WDataset yahoo = new ERDDatasetFilter(
						new YahooWebscopeL24Dataset(
								"datasets/yahoo_webscope_L24/ydata-search-query-log-to-entities-v1_0.xml"),
								wikiApi);
				gatherExamples(bingAnnotator, yahoo, trainEntityFilterGatherer, null, trainLinkBackCollectiveGatherer,
						trainLinkBackLevel2Gatherer, trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

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
			} else if (opt == OptDataset.SMAPH_DATASET) {
				boolean keepNEOnly = false;
				A2WDataset smaphTrainA = new GERDAQDataset(
						"datasets/gerdaq/gerdaq_trainingA.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphTrainA,
						trainEntityFilterGatherer, trainLevel1AnnotationGatherer, null, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

				A2WDataset smaphTrainB = new GERDAQDataset(
						"datasets/gerdaq/gerdaq_trainingB.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphTrainB,
						trainEntityFilterGatherer, null, trainLinkBackLevel2Gatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

				/*A2WDataset smaphDevel = new SMAPHDataset(
						"datasets/gerdaq/gerdaq_devel.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphDevel,
						trainEntityFilterGatherer, null, trainLinkBackGatherer,
						trainAnnotationGatherer,  wikiToFreebase, ar, annFn, keepNEOnly);*/


				/*				A2WDataset yahoo = new YahooWebscopeL24Dataset(
						"datasets/yahoo_webscope_L24/ydata-search-query-log-to-entities-v1_0.xml");
				gatherExamples(bingAnnotator, yahoo, trainEntityFilterGatherer,
						trainLinkBackGatherer, wikiToFreebase);*/

				/*A2WDataset smaphSingle = new SMAPHDataset(
						"datasets/smaph/single_test.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphSingle,
						trainEntityFilterGatherer, trainLevel1AnnotationGatherer, null, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);*/
			} else if (opt == OptDataset.SMAPH_DATASET_NE) {
				boolean keepNEOnly = true;
				A2WDataset smaphTrainA = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_trainingA.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphTrainA,
						trainEntityFilterGatherer, trainLevel1AnnotationGatherer, null, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

				A2WDataset smaphTrainB = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_trainingB.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphTrainB,
						trainEntityFilterGatherer, null, trainLinkBackLevel2Gatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);
			}
		}
		if (develEntityFilterGatherer != null || develLinkBackLevel2Gatherer != null || develLinkBackCollectiveGatherer != null|| develIndividualAnnotationGatherer != null || develIndividualAdvancedAnnotationGatherer != null) {
			if (opt == OptDataset.ERD_CHALLENGE) {
				boolean keepNEOnly = true;
				A2WDataset smaphDevel = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_devel.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphDevel,
						develEntityFilterGatherer,
						develLevel1AnnotationGatherer, develLinkBackLevel2Gatherer, develLinkBackCollectiveGatherer,
						develIndividualAnnotationGatherer,develIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1,
						keepNEOnly, anchorMaxED);

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
						develEntityFilterGatherer, develLevel1AnnotationGatherer, develLinkBackLevel2Gatherer, develLinkBackCollectiveGatherer,
						develIndividualAnnotationGatherer,develIndividualAdvancedAnnotationGatherer,  wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);
				/*A2WDataset smaphSingle = new SMAPHDataset(
						"datasets/smaph/single_test.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphSingle,
						develEntityFilterGatherer, develLevel1AnnotationGatherer, develLinkBackLevel2Gatherer, develLinkBackCollectiveGatherer,
						develIndividualAnnotationGatherer,develIndividualAdvancedAnnotationGatherer,  wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);*/
			}
			else if (opt == OptDataset.SMAPH_DATASET_NE){
				boolean keepNEOnly = true;
				A2WDataset smaphDevel = new ERDDatasetFilter(new GERDAQDataset(
						"datasets/gerdaq/gerdaq_devel.xml", wikiApi), wikiApi);
				gatherExamples(bingAnnotator, smaphDevel,
						develEntityFilterGatherer, develLevel1AnnotationGatherer, develLinkBackLevel2Gatherer, develLinkBackCollectiveGatherer,
						develIndividualAnnotationGatherer,develIndividualAdvancedAnnotationGatherer,  wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);
			}

		}

		BingInterface.flush();
		wikiApi.flush();

	}
	private static SmaphAnnotator getDefaultBingAnnotatorParam(
			WikipediaApiInterface wikiApi,
			String bingKey, EntityFilter entityFilter,FeatureNormalizer efNorm, LinkBack lb,
			boolean s2, boolean s3, boolean s6) throws FileNotFoundException,
			ClassNotFoundException, IOException {

		WATAnnotator watDefault = new WATAnnotator(
				"wikisense.mkapp.it", 80, "base", "COMMONNESS", "mw", "0.2",
				"0.0", false, false, false);
		return new SmaphAnnotator(entityFilter
				, efNorm, lb, s2, s3,
				10, s6, 25, false, watDefault, new FrequencyAnnotationFilter(0.03), wikiApi, bingKey);

	}
	public static SmaphAnnotator getDefaultBingAnnotatorGatherer(
			WikipediaApiInterface wikiApi, 
			String bingKey, boolean s2, boolean s3, boolean s6) throws FileNotFoundException,
			ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam( wikiApi, 
				bingKey, new NoEntityFilter(), null, new DummyLinkBack(), s2, s3, s6);
	}
	public static SmaphAnnotator getDefaultBingAnnotatorEF(
			WikipediaApiInterface wikiApi, 
			String bingKey, String EFModelFileBase) throws FileNotFoundException,
			ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam( wikiApi, 
				bingKey, new LibSvmEntityFilter(EFModelFileBase+".model"), new ZScoreFeatureNormalizer(EFModelFileBase+".zscore", new EntityFeaturePack()), new DummyLinkBack(), true, true, true);
	}
	public static SmaphAnnotator getDefaultBingAnnotatorIndividualLBLiblinear(
			WikipediaApiInterface wikiApi, String bingKey,
			String AFModelFileBase, String AFScaleFile, double annotationFilterThreshold) throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam( wikiApi, 
				bingKey, new NoEntityFilter(), null, new IndividualAnnotationLinkBack(new LibLinearAnnotatorRegressor(AFModelFileBase), new ZScoreFeatureNormalizer(AFScaleFile, new AdvancedAnnotationFeaturePack()), wikiApi, annotationFilterThreshold), true, true, true);
	}
	public static SmaphAnnotator getDefaultBingAnnotatorCollectiveLBLiblinear(
			WikipediaApiInterface wikiApi, String bingKey,
			String lBmodel, String lBrange) throws FileNotFoundException, ClassNotFoundException, IOException {
		CollectiveLinkBack lb = new CollectiveLinkBack(wikiApi, new DefaultBindingGenerator(), new LibLinearBindingRegressor(lBmodel), new ZScoreFeatureNormalizer(lBrange, new BindingFeaturePack()));
		return getDefaultBingAnnotatorParam( wikiApi, 
				bingKey, new NoEntityFilter(), null, lb, true, true, true);
	}
	public static SmaphAnnotator getDefaultBingAnnotatorCollectiveLBRanklibS6(
			WikipediaApiInterface wikiApi, String bingKey,
			String lBmodel, String lbRange) throws FileNotFoundException, ClassNotFoundException, IOException {
		CollectiveLinkBack lb = new CollectiveLinkBack(wikiApi, new DefaultBindingGenerator(), new RankLibBindingRegressor(lBmodel), new ZScoreFeatureNormalizer(lbRange, new BindingFeaturePack()));
		return getDefaultBingAnnotatorParam(
				wikiApi, 
				bingKey, new NoEntityFilter(), null, lb, false, false, true);
	}
	public static SmaphAnnotator getDefaultBingAnnotatorCollectiveLBRanklibAllSources(
			WikipediaApiInterface wikiApi, String bingKey,
			String lBmodel, String lbRange) throws FileNotFoundException, ClassNotFoundException, IOException {
		CollectiveLinkBack lb = new CollectiveLinkBack(wikiApi, new DefaultBindingGenerator(), new RankLibBindingRegressor(lBmodel), new ZScoreFeatureNormalizer(lbRange, new BindingFeaturePack()));
		return getDefaultBingAnnotatorParam(
				wikiApi, 
				bingKey, new NoEntityFilter(), null, lb, true, true, true);
	}
	public static SmaphAnnotator getDefaultBingAnnotatorIndividualAdvancedAnnotationRegressor(
			WikipediaApiInterface wikiApi, String bingKey,
			String AAFModelFileBase, String AAFScaleFile, double annotationFilterThreshold, double anchorMaxED) throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam( wikiApi, 
				bingKey, new NoEntityFilter(), null, new AdvancedIndividualLinkback(new LibLinearAnnotatorRegressor(AAFModelFileBase), new ZScoreFeatureNormalizer(AAFScaleFile, new AdvancedAnnotationFeaturePack()), wikiApi, annotationFilterThreshold, anchorMaxED), false, false, true);
	}

}
