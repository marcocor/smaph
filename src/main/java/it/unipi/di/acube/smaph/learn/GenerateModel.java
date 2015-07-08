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

import it.unipi.di.acube.BingInterface;
import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.metrics.MentionAnnotationMatch;
import it.unipi.di.acube.batframework.metrics.MetricsResultSet;
import it.unipi.di.acube.batframework.metrics.StrongMentionAnnotationMatch;
import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.batframework.utils.FreebaseApi;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.learn.GenerateTrainingAndTest.OptDataset;
import it.unipi.di.acube.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.NoFeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.annotationRegressor.LibLinearRegressor;
import it.unipi.di.acube.smaph.linkback.annotationRegressor.Regressor;
import it.unipi.di.acube.smaph.linkback.bindingRegressor.LibLinearBindingRegressor;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_parameter;
import libsvm.svm_problem;

public class GenerateModel {
	private static final String LIBLINEAR_BASE = "/home/marco/Downloads/liblinear-1.96";
	private static String bingKey, freebKey, freebCache;
	private static WikipediaApiInterface wikiApi;
	private static FreebaseApi freebApi;
	private static WikipediaToFreebase wikiToFreebase;
	private static Logger logger = LoggerFactory.getLogger(GenerateModel.class);
	
	public static void main(String[] args) throws Exception {
		Locale.setDefault(Locale.US);
		SmaphConfig.setConfigFile("smaph-config.xml");
		bingKey = SmaphConfig.getDefaultBingKey();
		freebKey = SmaphConfig.getDefaultFreebaseKey();
		freebCache = SmaphConfig.getDefaultFreebaseCache();
		BingInterface.setCache(SmaphConfig.getDefaultBingCache());
		wikiApi = new WikipediaApiInterface("wid.cache",
				"redirect.cache");
		WATRelatednessComputer.setCache("relatedness.cache");
		freebApi = new FreebaseApi(freebKey, freebCache);
			WATAnnotator.setCache("wikisense.cache");
		wikiToFreebase = new WikipediaToFreebase("mapdb");
		
		//generateEFModel(OptDataset.ERD_CHALLENGE);
		//generateAnnotationModel();
		//generateLBModel();
		//generateStackedModel();
		generateIndividualAdvancedAnnotationModel();
		WATAnnotator.flush();
	}
	
	public static void generateEFModel(OptDataset opt) throws Exception {

		SmaphConfig.setConfigFile("smaph-config.xml");
		String bingKey = SmaphConfig.getDefaultBingKey();
		String freebKey = SmaphConfig.getDefaultFreebaseKey();
		String freebCache = SmaphConfig.getDefaultFreebaseCache();
		BingInterface.setCache(SmaphConfig.getDefaultBingCache());
		WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache",
				"redirect.cache");
		FreebaseApi freebApi = new FreebaseApi(freebKey, freebCache);
		double[][] paramsToTest = null;
		double[][] weightsToTest = null;
		int[][] featuresSetsToTest = null;

	if (opt == OptDataset.ERD_CHALLENGE) {
		paramsToTest = new double[][] { { 0.010, 100 } };
		weightsToTest = new double[][] {
				{ 3.8, 4.5 },
				{ 3.8, 4.9 },
				{ 3.8, 5.2 },
				{ 3.8, 5.6 },
				{ 3.8, 5.9 },
				};
		featuresSetsToTest = new int[][] {
				//{ 1, 2, 3, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 33, 34, 35, 36, 37 },

				{2,3,15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 33, 34, 35, 36, 37,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69}
				};
	} else if (opt == OptDataset.SMAPH_DATASET) {
		paramsToTest = new double[][] {
				//{ 0.050, 1 },
				{0.01449, 1.0}

		};
		weightsToTest = new double[][] {
				{4.74985, 2.0}
				//{2.88435, 1.2},
				/*{2.88435, 1.4},
				{2.88435, 1.6},
				{2.88435, 1.8},
				{2.88435, 2.0},
				{2.88435, 2.2},*/
		};
		featuresSetsToTest = new int[][] {
				{2,15,16,20,21,22,24,33,34,35,36,39,40,44,46,47,48,49,50,51,52,53,54,56,57,58,59,60,61,63,64,65,66}
				//{7,8,9,10,11,12,13,15,17,20,21,23,24,25,33,34,35,37},
				//{ 1, 2, 3, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 33, 34, 35, 36, 37,38,39,40,41 },
				//{ 2, 3, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 33, 34, 35, 36, 37,39,40,41,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66},
				
		};
				
	}
		
	String filePrefix = "_xNWS"+(opt == OptDataset.ERD_CHALLENGE?"-erd":"-smaph");
	WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");
	List<ModelConfigurationResult> mcrs = new Vector<>();
	for (double boldFilterThr = 0.06; boldFilterThr <= 0.06; boldFilterThr += 0.02) {
		SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
				.getDefaultBingAnnotatorGatherer(wikiApi,
						boldFilterThr, bingKey);

		ExampleGatherer<Tag, HashSet<Tag>> trainEntityFilterGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
		ExampleGatherer<Tag, HashSet<Tag>> develEntityFilterGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
				bingAnnotator, trainEntityFilterGatherer,
				develEntityFilterGatherer,null, null, null, null, null, null, null, null, null, null, null, null, wikiApi,
				wikiToFreebase, freebApi, opt, -1);
		//ScaleFeatureNormalizer fNorm = new ScaleFeatureNormalizer(trainEntityFilterGatherer);
		//trainEntityFilterGatherer.dumpExamplesLibSvm("train_ef_scaled.dat", fNorm);
		
		ZScoreFeatureNormalizer fNormEF = new ZScoreFeatureNormalizer(trainEntityFilterGatherer);
		
		trainEntityFilterGatherer.dumpExamplesLibSvm("train_ef_zscore.dat", fNormEF);
		trainEntityFilterGatherer.dumpExamplesLibSvm("train_ef.dat", new NoFeatureNormalizer());
		
		int count = 0;
		for (int[] ftrToTestArray : featuresSetsToTest) {
			for (double[] paramsToTestArray : paramsToTest) {
				double gamma = paramsToTestArray[0];
				double C = paramsToTestArray[1];
				for (double[] weightsPosNeg : weightsToTest) {
					double wPos = weightsPosNeg[0], wNeg = weightsPosNeg[1];
					String fileBase = getModelFileNameBaseEF(
							ftrToTestArray, wPos, wNeg,
							boldFilterThr, gamma, C) + filePrefix;

					svm_problem trainProblem = trainEntityFilterGatherer.generateLibSvmProblem(ftrToTestArray, fNormEF);
					svm_parameter param = TuneModel.getParametersEF(wPos,
							wNeg, gamma, C);						
					System.out.println("Training binary classifier...");
					svm_model model = TuneModel.trainModel(param,
							trainProblem);
					svm.svm_save_model(fileBase + ".model", model);
					fNormEF.dump(fileBase + ".zscore", new EntityFeaturePack());
					
					MetricsResultSet metrics = TuneModel.ParameterTester.testLibSvmModel(model, develEntityFilterGatherer, ftrToTestArray, fNormEF, new SolutionComputer.TagSetSolutionComputer(wikiApi));

					int tp = metrics.getGlobalTp();
					int fp = metrics.getGlobalFp();
					int fn = metrics.getGlobalFn();
					float microF1 = metrics.getMicroF1();
					float macroF1 = metrics.getMacroF1();
					float macroRec = metrics.getMacroRecall();
					float macroPrec = metrics.getMacroPrecision();
					int totVects = develEntityFilterGatherer.getExamplesCount();
					mcrs.add(new ModelConfigurationResult(ftrToTestArray, wPos,
							wNeg, gamma, C, tp, fp, fn, totVects - tp
									- fp - fn, microF1, macroF1, macroRec,
							macroPrec));

					System.err.printf("Trained %d/%d models.%n", ++count,
							weightsToTest.length
									* featuresSetsToTest.length
									* paramsToTest.length);
				}
			}
		}
	}
	for (ModelConfigurationResult mcr : mcrs)
		System.out.printf("P/R/F1 %.5f%%\t%.5f%%\t%.5f%% TP/FP/FN: %d/%d/%d%n",
				mcr.getMacroPrecision() * 100, mcr.getMacroRecall() * 100,
				mcr.getMacroF1() * 100, mcr.getTP(), mcr.getFP(), mcr.getFN());
	for (double[] weightPosNeg : weightsToTest)
		System.out.printf("%.5f\t%.5f%n", weightPosNeg[0], weightPosNeg[1]);
	for (ModelConfigurationResult mcr : mcrs)
		System.out.println(mcr.getReadable());
	for (double[] paramGammaC : paramsToTest)
		System.out.printf("%.5f\t%.5f%n", paramGammaC[0], paramGammaC[1]);

}

	public static void generateAnnotationModel() throws Exception {
		int[][] featuresSetsToTest = new int[][] { SmaphUtils
				.getAllFtrVect(new AnnotationFeaturePack().getFeatureCount()) };
		OptDataset opt = OptDataset.SMAPH_DATASET;

		WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");
		List<ModelConfigurationResult> mcrs = new Vector<>();
		for (double boldFilterThr = 0.06; boldFilterThr <= 0.06; boldFilterThr += 0.02) {
			SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
					.getDefaultBingAnnotatorGatherer(wikiApi, boldFilterThr,
							bingKey);

			ExampleGatherer<Annotation, HashSet<Annotation>> trainAnnotationGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
			ExampleGatherer<Annotation, HashSet<Annotation>> develAnnotationGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
			ExampleGatherer<Annotation, HashSet<Annotation>> trainLevel1AnnotationGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
			ExampleGatherer<Annotation, HashSet<Annotation>> develLevel1AnnotationGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
			GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
					bingAnnotator, null, null, trainLevel1AnnotationGatherer,
					develLevel1AnnotationGatherer, null, null, null, null,
					trainAnnotationGatherer, develAnnotationGatherer, null, null, null,
					null, wikiApi, wikiToFreebase, freebApi, opt, -1);

			for (int[] ftrs : featuresSetsToTest) {
				String trainFileLibLinear = "train_ann_scaled.dat";
				ZScoreFeatureNormalizer fNorm = new ZScoreFeatureNormalizer(
						trainAnnotationGatherer);
				System.out.println("Dumping Annotation training problems...");
				trainAnnotationGatherer.dumpExamplesLibSvm(trainFileLibLinear,
						fNorm);

					/*System.out.println("Dumping Annotation training problems for ranking...");
					trainAnnotationGatherer.dumpExamplesRankLib(trainFileRankLib, fNorm);
					System.out.println("Dumping Annotation devel problems...");
					develAnnotationGatherer.dumpExamplesLibSvm("devel_ann_scaled.dat", fNorm);
					System.out.println("Dumping Annotation devel problems for ranking...");
					develAnnotationGatherer.dumpExamplesRankLib("devel_ann_scaled_ranking.dat", fNorm);*/
				/*for (int modelType : new int[] { 12 }) {
					for (double c = 4.2; c <= 4.8; c += 0.1) {
						String ARModel = getModelFileNameBaseAF(ftrs,
								boldFilterThr, c);
						String modelFile = ARModel + "." + modelType
								+ ".regressor.model";
						fNorm.dump(ARModel + ".regressor.zscore",
								new AnnotationFeaturePack());

						String cmd = String.format(
								"%s/train -s %d -c %.8f %s %s", LIBLINEAR_BASE,
								modelType, c, trainFileLibLinear, modelFile);
						System.out
								.println("Training libLinear model... " + cmd);
						Runtime.getRuntime().exec(cmd).waitFor();
						System.out.println("Model trained.");

						LibLinearAnnotationRegressor annReg = new LibLinearAnnotationRegressor(
								modelFile);

						for (double thr = 0.1; thr < 0.35; thr += 0.02) {
							MetricsResultSet metrics = TuneModel.ParameterTester
									.testLibLinearModel(
											annReg,
											develAnnotationGatherer,
											fNorm,
											new SolutionComputer.AnnotationSetSolutionComputer(
													wikiApi, thr));

							int tp = metrics.getGlobalTp();
							int fp = metrics.getGlobalFp();
							int fn = metrics.getGlobalFn();
							float microF1 = metrics.getMicroF1();
							float macroF1 = metrics.getMacroF1();
							float macroRec = metrics.getMacroRecall();
							float macroPrec = metrics.getMacroPrecision();
							int totVects = develAnnotationGatherer
									.getExamplesCount();
							mcrs.add(new ModelConfigurationResult(ftrs, -1, -1,
									-1, c, tp, fp, fn, totVects - tp - fp - fn,
									microF1, macroF1, macroRec, macroPrec));
						}
					}
				}*/

				String trainFileLevel1LibLinear = "train_ann_level1_scaled.dat";
				ZScoreFeatureNormalizer fNormLevel1 = new ZScoreFeatureNormalizer(
						trainLevel1AnnotationGatherer);
				System.out
						.println("Dumping Level1 Annotation training problems...");
				trainLevel1AnnotationGatherer.dumpExamplesLibSvm(
						trainFileLevel1LibLinear, fNormLevel1);

				for (int modelType : new int[] { 12 }) {
					for (double c = 3.2; c <= 3.2; c += 0.2) {
						String ARModel = getModelFileNameBaseAF(ftrs,
								boldFilterThr, c);
						String ARModelLevel1 = ARModel + ".level1";
						String modelFileLevel1 = ARModelLevel1 + "."
								+ modelType + ".regressor.model";
						fNormLevel1.dump(ARModelLevel1 + ".regressor.zscore",
								new AnnotationFeaturePack());

						String cmdLevel1 = String.format(
								"%s/train -s %d -c %.8f %s %s", LIBLINEAR_BASE,
								modelType, c, trainFileLevel1LibLinear,
								modelFileLevel1);
						System.out
								.println("Training libLinear model (level1)... "
										+ cmdLevel1);
						Runtime.getRuntime().exec(cmdLevel1).waitFor();
						System.out.println("Model trained (level1).");

						LibLinearRegressor annReg = new LibLinearRegressor(
								modelFileLevel1);

						for (double thr = 0.24; thr < 0.24; thr += 0.02) {
							MetricsResultSet metrics = TuneModel.ParameterTester
									.testLibLinearModel(
											annReg,
											develLevel1AnnotationGatherer,
											fNormLevel1,
											new SolutionComputer.AnnotationSetSolutionComputer(
													wikiApi, thr));

							int tp = metrics.getGlobalTp();
							int fp = metrics.getGlobalFp();
							int fn = metrics.getGlobalFn();
							float microF1 = metrics.getMicroF1();
							float macroF1 = metrics.getMacroF1();
							float macroRec = metrics.getMacroRecall();
							float macroPrec = metrics.getMacroPrecision();
							int totVects = develAnnotationGatherer
									.getExamplesCount();
							mcrs.add(new ModelConfigurationResult(ftrs, -1, -1,
									-1, c, tp, fp, fn, totVects - tp - fp - fn,
									microF1, macroF1, macroRec, macroPrec));
						}
					}
				}
			}
		}
		for (ModelConfigurationResult mcr : mcrs)
			System.out.printf("%.5f%%\t%.5f%%\t%.5f%%%n",
					mcr.getMacroPrecision() * 100, mcr.getMacroRecall() * 100,
					mcr.getMacroF1() * 100);
		for (ModelConfigurationResult mcr : mcrs)
			System.out.println(mcr.getReadable());

	}
	
	public static void generateIndividualAdvancedAnnotationModel() throws Exception {
		int[][] featuresSetsToTest = new int[][] { {2},{3},{4},{5},{6},{7},SmaphUtils
				.getAllFtrVect(new AdvancedAnnotationFeaturePack().getFeatureCount())};
		OptDataset opt = OptDataset.SMAPH_DATASET;
		double anchorMaxED = 0.5;
		WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");
		List<ModelConfigurationResult> mcrs = new Vector<>();
		for (double boldFilterThr = 0.06; boldFilterThr <= 0.06; boldFilterThr += 0.02) {
			SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
					.getDefaultBingAnnotatorGatherer(wikiApi, boldFilterThr,
							bingKey);

			ExampleGatherer<Annotation, HashSet<Annotation>> trainAdvancedAnnotationGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
			ExampleGatherer<Annotation, HashSet<Annotation>> develAdvancedAnnotationGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
			GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
					bingAnnotator, null, null, null,
					null, null, null, null, null, null, null, 
					trainAdvancedAnnotationGatherer, develAdvancedAnnotationGatherer,  null,
					null, wikiApi, wikiToFreebase, freebApi, opt, anchorMaxED);
			trainAdvancedAnnotationGatherer.dumpExamplesLibSvm("train_adv_ann_noscaled.dat", new NoFeatureNormalizer());
		
			System.out.println("Building normalizer...");
			ZScoreFeatureNormalizer fNorm = new ZScoreFeatureNormalizer(trainAdvancedAnnotationGatherer);

			for (int[] ftrs : featuresSetsToTest) {
				String trainFileLibLinear = "train_adv_ann_scaled.dat";
                System.out.println("Dumping Annotation problems...");
				trainAdvancedAnnotationGatherer.dumpExamplesLibSvm(trainFileLibLinear, fNorm, ftrs);
				//develAdvancedAnnotationGatherer.dumpExamplesLibSvm("devel_adv_ann_scaled.dat", fNorm, ftrs);

				for (int modelType : new int[] { 13 }) {
					for (double c = 1.0; c <= 1.0; c += 0.5) {
						String ARModel = getModelFileNameBaseAF(ftrs,
								boldFilterThr, c);
						String modelFile = ARModel + "." + modelType
								+ ".regressor.model";
						fNorm.dump(ARModel + ".regressor.zscore",
								new AdvancedAnnotationFeaturePack());

						String cmd = String.format(
								"%s/train -s %d -c %.8f %s %s", LIBLINEAR_BASE,
								modelType, c, trainFileLibLinear, modelFile);
						System.out
								.println("Training libLinear model... " + cmd);
						Runtime.getRuntime().exec(cmd).waitFor();
						System.out.println("Model trained.");

						LibLinearRegressor annReg = new LibLinearRegressor(
								modelFile);
						
						/*String dumpPredictionFile = String.format("dump_predictions.%d.%.3f.dat", modelType, c);
						if (dumpPredictionFile != null) {
							List<Triple<FeaturePack<Annotation>, Double, Double>> featuresAndExpectedAndPred = new Vector<>();
							List<List<Pair<Annotation, Double>>> candidateAndPreds = new Vector<>();
							for (Pair<Vector<Pair<FeaturePack<Annotation>, Annotation>>, HashSet<Annotation>> ftrsAndDatasAndGold : develAdvancedAnnotationGatherer
							        .getDataAndFeaturePacksAndGoldOnePerInstance()) {
								List<Pair<Annotation, Double>> candidateAndPred = new Vector<>();
								candidateAndPreds.add(candidateAndPred);
								for (Pair<FeaturePack<Annotation>, Annotation> p : ftrsAndDatasAndGold.first) {
									double predictedScore = annReg.predictScore(p.first, fNorm);
									featuresAndExpectedAndPred.add(new ImmutableTriple<FeaturePack<Annotation>, Double, Double>(
									        p.first, SolutionComputer.AnnotationSetSolutionComputer.candidateScoreStatic(
									                p.second, ftrsAndDatasAndGold.second, new StrongMentionAnnotationMatch()),
									        predictedScore));
								}
							}

							PrintWriter writer = new PrintWriter(dumpPredictionFile, "UTF-8");
							for (Triple<FeaturePack<Annotation>, Double, Double> t : featuresAndExpectedAndPred)
								writer.printf("%.6f\t%.6f\n", t.getMiddle(), t.getRight());
							writer.close();
						}*/
				            
				            
						for (double thr = -0.6; thr <= 0.4; thr += 0.1) {
						    System.out.println("Testing threshold "+thr);

							MetricsResultSet metrics = TuneModel.ParameterTester
									.testLibLinearModel(
											annReg,
											develAdvancedAnnotationGatherer,
											fNorm,
											new SolutionComputer.AnnotationSetSolutionComputer(
													wikiApi, thr));

							int tp = metrics.getGlobalTp();
							int fp = metrics.getGlobalFp();
							int fn = metrics.getGlobalFn();
							float microF1 = metrics.getMicroF1();
							float macroF1 = metrics.getMacroF1();
							float macroRec = metrics.getMacroRecall();
							float macroPrec = metrics.getMacroPrecision();
							int totVects = develAdvancedAnnotationGatherer
									.getExamplesCount();
							mcrs.add(new ModelConfigurationResult(ftrs, -1, -1,
									-1, c, tp, fp, fn, totVects - tp - fp - fn,
									microF1, macroF1, macroRec, macroPrec));
						}
					}
				}

			}
		}
		for (ModelConfigurationResult mcr : mcrs)
			System.out.printf("%.5f%%\t%.5f%%\t%.5f%%%n",
					mcr.getMacroPrecision() * 100, mcr.getMacroRecall() * 100,
					mcr.getMacroF1() * 100);
		for (ModelConfigurationResult mcr : mcrs)
			System.out.println(mcr.getReadable());

	}

	public static void generateLBModel() throws Exception {

		int[][] featuresSetsToTest = new int[][] { SmaphUtils
				.getAllFtrVect(new BindingFeaturePack().getFeatureCount()) };
		
		OptDataset opt = OptDataset.SMAPH_DATASET;
		List<ModelConfigurationResult> mcrs = new Vector<>();
		for (double boldFilterThr = 0.06; boldFilterThr <= 0.06; boldFilterThr += 0.02) {
			SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
					.getDefaultBingAnnotatorGatherer(wikiApi, 
							boldFilterThr, bingKey);
			WATAnnotator.setCache("wikisense.cache");

			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> trainLinkBackGatherer = new ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>>();
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> develLinkBackGatherer = new ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>>();
			GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
					bingAnnotator, null, null, null, null, null, null, trainLinkBackGatherer,
					develLinkBackGatherer, null, null, null, null,null, null, wikiApi, wikiToFreebase, freebApi, opt, -1);
			WATRelatednessComputer.flush();
			
			for (int[] ftrs : featuresSetsToTest) {
				String trainFileLibLinear = "train_binding_full.dat";
				//FeatureNormalizer brNormLevel2 = new NoFeatureNormalizer();
				ZScoreFeatureNormalizer brNorm = new ZScoreFeatureNormalizer(trainLinkBackGatherer);
				System.out.println("Dumping binding training problems...");
				trainLinkBackGatherer.dumpExamplesLibSvm(trainFileLibLinear, brNorm);
				/*System.out.println("Dumping binding training problems for ranking...");
				trainLinkBackGatherer.dumpExamplesRankLib("train_binding_ranking.dat", fn);*/
				/*System.out.println("Dumping LB development problems...");
				develLinkBackGatherer.dumpExamplesLibSvm("devel.dat", fn);*/

				for (int modelType : new int[] { 12 }) {
					for (double c = 0.4; c <= 0.4; c += 0.1) {
						String BRModel = getModelFileNameBaseLB(ftrs,
								boldFilterThr, c) + ".full";
						String modelFile = BRModel + "."
								+ modelType + ".regressor.model";
						brNorm.dump(BRModel + ".regressor.zscore",
								new AnnotationFeaturePack());

						String cmd = String.format(
								"%s/train -s %d -c %.8f %s %s", LIBLINEAR_BASE,
								modelType, c, trainFileLibLinear,
								modelFile);
						System.out
								.println("Training libLinear model (full)... "
										+ cmd);
						Runtime.getRuntime().exec(cmd).waitFor();
						System.out.println("Model trained (full).");

						LibLinearBindingRegressor br = new LibLinearBindingRegressor(
								modelFile);

							MetricsResultSet metrics = TuneModel.ParameterTester
									.testLibLinearModel(
											br,
											develLinkBackGatherer,
											brNorm,
											new SolutionComputer.BindingSolutionComputer(wikiApi));

							int tp = metrics.getGlobalTp();
							int fp = metrics.getGlobalFp();
							int fn = metrics.getGlobalFn();
							float microF1 = metrics.getMicroF1();
							float macroF1 = metrics.getMacroF1();
							float macroRec = metrics.getMacroRecall();
							float macroPrec = metrics.getMacroPrecision();
							int totVects = develLinkBackGatherer
									.getExamplesCount();
							mcrs.add(new ModelConfigurationResult(ftrs, -1, -1,
									-1, c, tp, fp, fn, totVects - tp - fp - fn,
									microF1, macroF1, macroRec, macroPrec));
					}
				}
			}
		}
		for (ModelConfigurationResult mcr : mcrs)
			System.out.printf("%.5f%%\t%.5f%%\t%.5f%%%n",
					mcr.getMacroPrecision() * 100, mcr.getMacroRecall() * 100,
					mcr.getMacroF1() * 100);
		for (ModelConfigurationResult mcr : mcrs)
			System.out.println(mcr.getReadable());
	}
	
		public static void generateStackedModel() throws Exception {
			String ARLevel1Model = "models/model_1-75_AF_0.060_3.20000000.level1.12.regressor.model";
			String ARLevel1Range = "models/model_1-75_AF_0.060_3.20000000.level1.regressor.zscore";

			Regressor arLevel1 = new LibLinearRegressor(ARLevel1Model);
			ZScoreFeatureNormalizer arNormLevel1 = new ZScoreFeatureNormalizer(ARLevel1Range, new AnnotationFeaturePack());
			
			int[][] featuresSetsToTest = new int[][] { SmaphUtils
					.getAllFtrVect(new BindingFeaturePack().getFeatureCount()) };
			OptDataset opt = OptDataset.SMAPH_DATASET;
			
			WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");
			List<ModelConfigurationResult> mcrs = new Vector<>();
			for (double boldFilterThr = 0.06; boldFilterThr <= 0.06; boldFilterThr += 0.02) {
				SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
						.getDefaultBingAnnotatorGatherer(wikiApi, 
								boldFilterThr, bingKey);

				ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> trainLinkBackGatherer = new ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>>();
				ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> develLinkBackGatherer = new ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>>();
				GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
						bingAnnotator, null, null, null, null, trainLinkBackGatherer,
						develLinkBackGatherer, null, null, null, null,null, null, arLevel1, arNormLevel1, wikiApi, wikiToFreebase, freebApi, opt, -1);
				WATRelatednessComputer.flush();
				
				for (int[] ftrs : featuresSetsToTest) {
					String trainFileLibLinear = "train_binding_level2.dat";
					//FeatureNormalizer brNormLevel2 = new NoFeatureNormalizer();
					ZScoreFeatureNormalizer brNormLevel2 = new ZScoreFeatureNormalizer(trainLinkBackGatherer);
					System.out.println("Dumping binding training problems...");
					trainLinkBackGatherer.dumpExamplesLibSvm(trainFileLibLinear, brNormLevel2);
					/*System.out.println("Dumping binding training problems for ranking...");
					trainLinkBackGatherer.dumpExamplesRankLib("train_binding_ranking.dat", fn);*/
					/*System.out.println("Dumping LB development problems...");
					develLinkBackGatherer.dumpExamplesLibSvm("devel.dat", fn);*/

					for (int modelType : new int[] { 12 }) {
						for (double c = 0.10; c <= 0.20; c += 0.02) {
							String BRModel = getModelFileNameBaseLB(ftrs,
									boldFilterThr, c);
							String BRModelLevel2 = BRModel + ".level2";
							String modelFileLevel2 = BRModelLevel2 + "."
									+ modelType + ".regressor.model";
							brNormLevel2.dump(BRModelLevel2 + ".regressor.zscore",
									new AnnotationFeaturePack());

							String cmdLevel2 = String.format(
									"%s/train -s %d -c %.8f %s %s", LIBLINEAR_BASE,
									modelType, c, trainFileLibLinear,
									modelFileLevel2);
							System.out
									.println("Training libLinear model (level2)... "
											+ cmdLevel2);
							Runtime.getRuntime().exec(cmdLevel2).waitFor();
							System.out.println("Model trained (level2).");

							LibLinearBindingRegressor brLevel2 = new LibLinearBindingRegressor(
									modelFileLevel2);

								MetricsResultSet metrics = TuneModel.ParameterTester
										.testLibLinearModel(
												brLevel2,
												develLinkBackGatherer,
												brNormLevel2,
												new SolutionComputer.BindingSolutionComputer(wikiApi));

								int tp = metrics.getGlobalTp();
								int fp = metrics.getGlobalFp();
								int fn = metrics.getGlobalFn();
								float microF1 = metrics.getMicroF1();
								float macroF1 = metrics.getMacroF1();
								float macroRec = metrics.getMacroRecall();
								float macroPrec = metrics.getMacroPrecision();
								int totVects = develLinkBackGatherer
										.getExamplesCount();
								mcrs.add(new ModelConfigurationResult(ftrs, -1, -1,
										-1, c, tp, fp, fn, totVects - tp - fp - fn,
										microF1, macroF1, macroRec, macroPrec));
						}
					}
				}
			}
			for (ModelConfigurationResult mcr : mcrs)
				System.out.printf("%.5f%%\t%.5f%%\t%.5f%%%n",
						mcr.getMacroPrecision() * 100, mcr.getMacroRecall() * 100,
						mcr.getMacroF1() * 100);
			for (ModelConfigurationResult mcr : mcrs)
				System.out.println(mcr.getReadable());
		}
		
		

	public static String getModelFileNameBaseLB(int[] ftrs,
			double editDistance, double C) {
		return String.format("models/model_%s_LB_%.3f_%.8f",
				getFtrListRepresentation(ftrs), editDistance, C);
	}

	public static String getModelFileNameBaseEF(int[] ftrs, double wPos,
			double wNeg, double editDistance, double gamma, double C) {
		return String.format("models/model_%s_EF_%.5f_%.5f_%.3f_%.8f_%.8f",
				getFtrListRepresentation(ftrs), wPos, wNeg, editDistance, gamma, C);
	}
	
	private static String getModelFileNameBaseAF(int[] ftrs,
			double boldFilterThr, double c) {
		return String.format("models/model_%s_AF_%.3f_%.8f",
				getFtrListRepresentation(ftrs), boldFilterThr, c);
	}

	private static String getFtrListRepresentation(int[] ftrs) {
		Arrays.sort(ftrs);
		String ftrList = "";
		int i = 0;
		int lastInserted = -1;
		int lastBlockSize = 1;
		while (i < ftrs.length) {
			int current = ftrs[i];
			if (i == 0) // first feature
				ftrList += current;
			else if (current == lastInserted + 1) { // continuation of a block
				if (i == ftrs.length - 1)// last element, close block
					ftrList += "-" + current;
				lastBlockSize++;
			} else {// start of a new block
				if (lastBlockSize > 1) {
					ftrList += "-" + lastInserted;
				}
				ftrList += "," + current;
				lastBlockSize = 1;
			}
			lastInserted = current;
			i++;
		}
		return ftrList;
	}

}
