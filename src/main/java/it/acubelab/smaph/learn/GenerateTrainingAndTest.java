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

package it.acubelab.smaph.learn;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.Tag;
import it.acubelab.batframework.datasetPlugins.SMAPHDataset;
import it.acubelab.batframework.datasetPlugins.YahooWebscopeL24Dataset;
import it.acubelab.batframework.problems.A2WDataset;
import it.acubelab.batframework.systemPlugins.WATAnnotator;
import it.acubelab.batframework.utils.FreebaseApi;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.BingInterface;
import it.acubelab.smaph.SmaphAnnotator;
import it.acubelab.smaph.boldfilters.FrequencyBoldFilter;
import it.acubelab.smaph.entityfilters.EntityFilter;
import it.acubelab.smaph.entityfilters.LibSvmEntityFilter;
import it.acubelab.smaph.entityfilters.NoEntityFilter;
import it.acubelab.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
import it.acubelab.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.acubelab.smaph.learn.featurePacks.BindingFeaturePack;
import it.acubelab.smaph.learn.featurePacks.EntityFeaturePack;
import it.acubelab.smaph.learn.featurePacks.FeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;
import it.acubelab.smaph.learn.normalizer.NoFeatureNormalizer;
import it.acubelab.smaph.learn.normalizer.ScaleFeatureNormalizer;
import it.acubelab.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.acubelab.smaph.linkback.DummyLinkBack;
import it.acubelab.smaph.linkback.LinkBack;
import it.acubelab.smaph.linkback.SvmAdvancedIndividualSingleLinkback;
import it.acubelab.smaph.linkback.SvmCollectiveLinkBack;
import it.acubelab.smaph.linkback.SvmIndividualAnnotationLinkBack;
import it.acubelab.smaph.linkback.SvmSingleEntityLinkBack;
import it.acubelab.smaph.linkback.annotationRegressor.Regressor;
import it.acubelab.smaph.linkback.annotationRegressor.LibLinearRegressor;
import it.acubelab.smaph.linkback.bindingGenerator.DefaultBindingGenerator;
import it.acubelab.smaph.linkback.bindingRegressor.LibLinearBindingRegressor;
import it.acubelab.smaph.main.ERDDatasetFilter;
import it.acubelab.smaph.snippetannotationfilters.FrequencyAnnotationFilter;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

public class GenerateTrainingAndTest {
	
	public enum OptDataset {ERD_CHALLENGE, SMAPH_DATASET}
	public static void gatherExamples(SmaphAnnotator bingAnnotator,
			A2WDataset ds, ExampleGatherer<Tag, HashSet<Tag>> entityFilterGatherer, ExampleGatherer<Annotation, HashSet<Annotation>> annotationLevel1Gatherer, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> linkBackLevel2Gatherer, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> linkBackCollectiveGatherer, ExampleGatherer<Annotation, HashSet<Annotation>> individualAnnotationGatherer, ExampleGatherer<Annotation, HashSet<Annotation>> advancedIndividualAnnotationGatherer,
			WikipediaToFreebase wikiToFreeb, Regressor ar, FeatureNormalizer annFn, boolean keepNEOnly, double anchorMaxED) throws Exception {
		gatherExamples(bingAnnotator, ds, entityFilterGatherer,annotationLevel1Gatherer,
				linkBackLevel2Gatherer, linkBackCollectiveGatherer, individualAnnotationGatherer, advancedIndividualAnnotationGatherer, wikiToFreeb, ar,annFn, keepNEOnly, -1, anchorMaxED);
	}
	public static void gatherExamples(SmaphAnnotator bingAnnotator,
			A2WDataset ds, ExampleGatherer<Tag, HashSet<Tag>> entityFilterGatherer,ExampleGatherer<Annotation, HashSet<Annotation>> annotationLevel1Gatherer, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> linkBackLevel2Gatherer, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> linkBackCollectiveGatherer,ExampleGatherer<Annotation, HashSet<Annotation>> annotationRegressorGatherer,ExampleGatherer<Annotation, HashSet<Annotation>> advancedAnnotationRegressorGatherer,
			WikipediaToFreebase wikiToFreeb, Regressor arLevel1, FeatureNormalizer arNormLevel1, boolean keepNEOnly, int limit, double anchorMaxED) throws Exception {
			limit = limit ==-1? ds.getSize() : Math.min(limit, ds.getSize());
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
			
		}
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
			Regressor arLevel1, FeatureNormalizer arNormLevel1,
			WikipediaApiInterface wikiApi, WikipediaToFreebase wikiToFreebase,
			FreebaseApi freebApi, OptDataset opt, double anchorMaxED) throws Exception {
		if (trainEntityFilterGatherer != null || trainLinkBackCollectiveGatherer != null || trainLinkBackLevel2Gatherer != null || trainIndividualAnnotationGatherer != null || trainIndividualAdvancedAnnotationGatherer != null) {

			if (opt == OptDataset.ERD_CHALLENGE) {

				boolean keepNEOnly = true;
				A2WDataset smaphTrainA = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_trainingA.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphTrainA,
						trainEntityFilterGatherer, trainLevel1AnnotationGatherer, null, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

				A2WDataset smaphTrainB = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_trainingB.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphTrainB,
						trainEntityFilterGatherer, null, trainLinkBackLevel2Gatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

				A2WDataset smaphTest = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_test.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphTest,
						trainEntityFilterGatherer, null, trainLinkBackLevel2Gatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

				A2WDataset smaphDevel = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_devel.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphDevel,
						trainEntityFilterGatherer,null, trainLinkBackLevel2Gatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

				A2WDataset yahoo = new ERDDatasetFilter(
						new YahooWebscopeL24Dataset(
								"datasets/yahoo_webscope_L24/ydata-search-query-log-to-entities-v1_0.xml"),
						wikiApi, wikiToFreebase);
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
				A2WDataset smaphTrainA = new SMAPHDataset(
						"datasets/smaph/smaph_trainingA.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphTrainA,
						trainEntityFilterGatherer, trainLevel1AnnotationGatherer, null, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

				A2WDataset smaphTrainB = new SMAPHDataset(
						"datasets/smaph/smaph_trainingB.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphTrainB,
						trainEntityFilterGatherer, null, trainLinkBackLevel2Gatherer, trainLinkBackCollectiveGatherer,
						trainIndividualAnnotationGatherer,trainIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);

				/*A2WDataset smaphDevel = new SMAPHDataset(
						"datasets/smaph/smaph_devel.xml", wikiApi);
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
						trainEntityFilterGatherer, trainLinkBackGatherer,
						wikiToFreebase);*/
			}
		}
		if (develEntityFilterGatherer != null || develLinkBackLevel2Gatherer != null || develLinkBackCollectiveGatherer != null|| develIndividualAnnotationGatherer != null || develIndividualAdvancedAnnotationGatherer != null) {
			if (opt == OptDataset.ERD_CHALLENGE) {
				boolean keepNEOnly = true;
				A2WDataset smaphDevel = new ERDDatasetFilter(new SMAPHDataset(
						"datasets/smaph/smaph_devel.xml", wikiApi), wikiApi,
						wikiToFreebase);
				gatherExamples(bingAnnotator, smaphDevel,
						develEntityFilterGatherer,
						develLevel1AnnotationGatherer, develLinkBackLevel2Gatherer, develLinkBackCollectiveGatherer,
						develIndividualAnnotationGatherer,develIndividualAdvancedAnnotationGatherer, wikiToFreebase, arLevel1, arNormLevel1,
						keepNEOnly, anchorMaxED);

			}
			else if (opt == OptDataset.SMAPH_DATASET){
				boolean keepNEOnly = false;
				/*A2WDataset smaphTest = new SMAPHDataset(
						"datasets/smaph/smaph_test.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphTest,
						develEntityFilterGatherer, develEntityRegressorGatherer, develLinkBackGatherer,develAnnotationGatherer,
						wikiToFreebase, ar, annFn, keepNEOnly);*/
				
				A2WDataset smaphDevel = new SMAPHDataset(
						"datasets/smaph/smaph_devel.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphDevel,
						develEntityFilterGatherer, develLevel1AnnotationGatherer, develLinkBackLevel2Gatherer, develLinkBackCollectiveGatherer,
						develIndividualAnnotationGatherer,develIndividualAdvancedAnnotationGatherer,  wikiToFreebase, arLevel1, arNormLevel1, keepNEOnly, anchorMaxED);
/*				A2WDataset smaphSingle = new SMAPHDataset(
						"datasets/smaph/single_test.xml", wikiApi);
				gatherExamples(bingAnnotator, smaphSingle,
						develEntityFilterGatherer, develEntityRegressorGatherer,develLinkBackGatherer,develAnnotationGatherer,
						wikiToFreebase);*/
			}
			
		}

		BingInterface.flush();
		wikiApi.flush();

	}
	private static SmaphAnnotator getDefaultBingAnnotatorParam(
			WikipediaApiInterface wikiApi,
			double editDistanceSpotFilterThreshold, 
			String bingKey, EntityFilter entityFilter,FeatureNormalizer efNorm, LinkBack lb) throws FileNotFoundException,
			ClassNotFoundException, IOException {
				WATAnnotator wikiSense = new WATAnnotator("wikisense.mkapp.it", 80,
				"base", "COMMONNESS", "jaccard", "0.6", "0.0"/* minlp */, false,
				false, false);

				WATAnnotator watDefault = new WATAnnotator(
						"wikisense.mkapp.it", 80, "base", "COMMONNESS", "mw", "0.2",
						"0.0", false, false, false);
		return new SmaphAnnotator(wikiSense,
				new FrequencyBoldFilter((float)editDistanceSpotFilterThreshold),entityFilter
				, efNorm, lb, false, true, true,
				10, false, 0, false, 0, true, 25, false, watDefault, new FrequencyAnnotationFilter(0.03), wikiApi, bingKey);

	}
	public static SmaphAnnotator getDefaultBingAnnotatorGatherer(
			WikipediaApiInterface wikiApi, 
			double editDistanceSpotFilterThreshold, 
			String bingKey) throws FileNotFoundException,
			ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam( wikiApi, 
			 editDistanceSpotFilterThreshold,  
			 bingKey, new NoEntityFilter(), null, new DummyLinkBack());
	}
	public static SmaphAnnotator getDefaultBingAnnotatorEF(
			WikipediaApiInterface wikiApi, 
			double editDistanceSpotFilterThreshold, 
			String bingKey, String EFModelFileBase) throws FileNotFoundException,
			ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam( wikiApi, 
			 editDistanceSpotFilterThreshold, 
			 bingKey, new LibSvmEntityFilter(EFModelFileBase+".model"), new ZScoreFeatureNormalizer(EFModelFileBase+".zscore", new EntityFeaturePack()), new DummyLinkBack());
	}
	public static SmaphAnnotator getDefaultBingAnnotatorLBStacked(
			WikipediaApiInterface wikiApi, 
			double editDistanceSpotFilterThreshold, 
			String bingKey, String LBLevel2model, String LBLevel2Range, String ARLevel1Model, String AFLevel1Range) throws FileNotFoundException,
			ClassNotFoundException, IOException {
		SvmCollectiveLinkBack lb = new SvmCollectiveLinkBack(wikiApi, new DefaultBindingGenerator(), new LibLinearRegressor(ARLevel1Model), new LibLinearBindingRegressor(LBLevel2model), new ZScoreFeatureNormalizer(AFLevel1Range, new AnnotationFeaturePack()), new ZScoreFeatureNormalizer(LBLevel2Range, new BindingFeaturePack()));
		return getDefaultBingAnnotatorParam( wikiApi, 
			 editDistanceSpotFilterThreshold, 
			 bingKey, new NoEntityFilter(), null, lb);
	}
	public static SmaphAnnotator getDefaultBingAnnotatorEFRegressor(
			WikipediaApiInterface wikiApi, double editDistanceSpotFilterThreshold, String bingKey,
			String EFModelFileBase) throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam( wikiApi, 
				 editDistanceSpotFilterThreshold, 
				 bingKey, new NoEntityFilter(), null, new SvmSingleEntityLinkBack(new LibSvmEntityFilter(EFModelFileBase), new ScaleFeatureNormalizer(EFModelFileBase+".range", new EntityFeaturePack()), wikiApi));
	}
	public static SmaphAnnotator getDefaultBingAnnotatorAFRegressor(
			WikipediaApiInterface wikiApi, double editDistanceSpotFilterThreshold, String bingKey,
			String AFModelFileBase, String AFScaleFile, double annotationFilterThreshold) throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam( wikiApi, 
				 editDistanceSpotFilterThreshold, 
				 bingKey, new NoEntityFilter(), null, new SvmIndividualAnnotationLinkBack(new LibLinearRegressor(AFModelFileBase), new ZScoreFeatureNormalizer(AFScaleFile, new AnnotationFeaturePack()), wikiApi, annotationFilterThreshold));
	}
	public static SmaphAnnotator getDefaultBingAnnotatorLB(
			WikipediaApiInterface wikiApi, double editDistanceSpotFilterThreshold, String bingKey,
			String lBmodel, String lBrange) throws FileNotFoundException, ClassNotFoundException, IOException {
		SvmCollectiveLinkBack lb = new SvmCollectiveLinkBack(wikiApi, new DefaultBindingGenerator(), null, new LibLinearBindingRegressor(lBmodel), null, new ZScoreFeatureNormalizer(lBrange, new BindingFeaturePack()));
		return getDefaultBingAnnotatorParam( wikiApi, 
				 editDistanceSpotFilterThreshold, 
				 bingKey, new NoEntityFilter(), null, lb);
	}
	public static SmaphAnnotator getDefaultBingAnnotatorIndividualAdvancedAnnotationRegressor(
			WikipediaApiInterface wikiApi, double editDistanceSpotFilterThreshold, String bingKey,
			String AAFModelFileBase, String AAFScaleFile, double annotationFilterThreshold, double anchorMaxED) throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam( wikiApi, 
				 editDistanceSpotFilterThreshold, 
				 bingKey, new NoEntityFilter(), null, new SvmAdvancedIndividualSingleLinkback(new LibLinearRegressor(AAFModelFileBase), new ZScoreFeatureNormalizer(AAFScaleFile, new AdvancedAnnotationFeaturePack()), wikiApi, annotationFilterThreshold, anchorMaxED));
	}
	
}
