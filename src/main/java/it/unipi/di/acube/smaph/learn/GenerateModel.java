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
import it.unipi.di.acube.BingInterface;
import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.metrics.MetricsResultSet;
import it.unipi.di.acube.batframework.metrics.StrongAnnotationMatch;
import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.batframework.utils.FreebaseApi;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.ProblemReduction;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.EntityToVect;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.learn.GenerateTrainingAndTest.OptDataset;
import it.unipi.di.acube.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.models.entityfilters.EntityFilter;
import it.unipi.di.acube.smaph.learn.models.entityfilters.LibSvmEntityFilter;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.AnnotationRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.LibLinearAnnotatorRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.LibSvmAnnotationRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor.BindingRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor.LibLinearBindingRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor.RankLibBindingRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.NoFeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.IndividualAnnotationLinkBack;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_parameter;
import libsvm.svm_problem;

import org.apache.commons.lang3.tuple.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ciir.umass.edu.eval.Evaluator;

public class GenerateModel {
	private static final String LIBLINEAR_BASE = "libs/liblinear-2.0";
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
		wikiApi = new WikipediaApiInterface("wid.cache", "redirect.cache");
		WATRelatednessComputer.setCache("relatedness.cache");
		freebApi = new FreebaseApi(freebKey, freebCache);
		WATAnnotator.setCache("wikisense.cache");
		wikiToFreebase = new WikipediaToFreebase("mapdb");
		EntityToVect.initialize();

		generateEFModel();
		//generateCollectiveModel();
		//generateStackedModel();
		generateIndividualAdvancedAnnotationModel();
		WATAnnotator.flush();
		WATRelatednessComputer.flush();
	}

	public static void generateEFModel() throws Exception {
		OptDataset opt = OptDataset.SMAPH_DATASET;
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
					{2,3,15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 33, 34, 35, 36, 37,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69}
			};
		} else if (opt == OptDataset.SMAPH_DATASET) {
			paramsToTest = new double[][] {
					/*{0.16567, 0.1},
					{0.02500, 1.0},*/
					//{0.04210, 1.04000}
					{0.04210, 1.00000}
			};
			weightsToTest = new double[][] {
					/*{2.88435, 0.8},
					{2.88435, 0.9},
					{2.88435, 1.0},
					{2.88435, 1.2},
					{2.88435, 1.4},
					{3.85075, 1.3},
					{3.85075, 1.4},
					{3.85075, 1.5},*/
					//{4.23583, 1.54000},
					{3.86853, 1.42175}
			};
			featuresSetsToTest = new int[][] {
					//SmaphUtils.getAllFtrVect(EntityFeaturePack.ftrNames.length),
					//{1,2,3,6,7,8,10,11,12,13,15,16,18,20,21,22,23,24,25,27,28,29,30,31,34,35,36,37,38,39},
					{1,2,3,4,6,7,8,11,12,14,16,18,20,23,24,27,29,30,31,32,35,36,38,39,40}
			};

		}

		String filePrefix = "_xNWS"+(opt == OptDataset.ERD_CHALLENGE?"-erd":"-smaph");
		List<ModelConfigurationResult> mcrs = new Vector<>();
		SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
				.getDefaultBingAnnotatorGatherer(wikiApi,
						bingKey, true, true, true);

		ExampleGatherer<Tag, HashSet<Tag>> trainEntityFilterGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
		ExampleGatherer<Tag, HashSet<Tag>> develEntityFilterGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
				bingAnnotator, trainEntityFilterGatherer,
				develEntityFilterGatherer, null, null, null, null, null, null, wikiApi,
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
							gamma, C) + filePrefix;

					svm_problem trainProblem = trainEntityFilterGatherer.generateLibSvmProblem(ftrToTestArray, fNormEF);
					svm_parameter param = ParameterTester.getParametersEF(wPos,
							wNeg, gamma, C);						
					System.out.println("Training binary classifier...");
					svm_model model = ParameterTester.trainModel(param,
							trainProblem);
					svm.svm_save_model(fileBase + ".model", model);
					fNormEF.dump(fileBase + ".zscore");
					EntityFilter ef = new LibSvmEntityFilter(model);
					MetricsResultSet metrics = ParameterTester.testEntityFilter(ef, develEntityFilterGatherer, ftrToTestArray, fNormEF, new SolutionComputer.TagSetSolutionComputer(wikiApi));

					int tp = metrics.getGlobalTp();
					int fp = metrics.getGlobalFp();
					int fn = metrics.getGlobalFn();
					float microF1 = metrics.getMicroF1();
					float macroF1 = metrics.getMacroF1();
					float macroRec = metrics.getMacroRecall();
					float macroPrec = metrics.getMacroPrecision();
					int totVects = develEntityFilterGatherer.getExamplesCount();
					mcrs.add(new ModelConfigurationResult(ftrToTestArray, wPos,
							wNeg, gamma, C, -1, tp, fp, fn, totVects - tp
							- fp - fn, microF1, macroF1, macroRec,
							macroPrec));

					System.err.printf("Trained %d/%d models.%n", ++count,
							weightsToTest.length
							* featuresSetsToTest.length
							* paramsToTest.length);
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

	public static void generateIndividualAdvancedAnnotationModel() throws Exception {
		int[][] featuresSetsToTest = new int[][] { 
				//SmaphUtils.getAllFtrVect(new AdvancedAnnotationFeaturePack().getFeatureCount()),
				//new int[]{2,3,4,5,6,7,8,10,12,13,16,17,18,20,21,23,24,25,26,27,28,29,30,31,32,34,36,37,38,39,41,44,45,46,47,48,50,51,52,54,55},
				//new int[] {1,2,3,4,6,7,8,9,10,12,14,18,21,22,24,26,27,28,29,30,32,33,35,36,38,40,41,42,43,44,45,46,51,52,53,54,55,58,59},
				new int[] {1,2,3,4,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31,32,35,36,37,38,39,40,41,42,43,44,46,47,48,49,50,51,52,54,55,56,57,58,59,60,61,62,63,64,65,66},
		};
		OptDataset opt = OptDataset.SMAPH_DATASET;
		double anchorMaxED = 0.7;
		WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");
		List<ModelConfigurationResult> mcrs = new Vector<>();
		SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
				.getDefaultBingAnnotatorGatherer(wikiApi,
						bingKey, true, true, true);

		ExampleGatherer<Annotation, HashSet<Annotation>> trainAdvancedAnnotationGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
		ExampleGatherer<Annotation, HashSet<Annotation>> develAdvancedAnnotationGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
		List<String> develInstances = new Vector<>();
		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
				bingAnnotator, null, null, null,
				null, trainAdvancedAnnotationGatherer, develAdvancedAnnotationGatherer, null, develInstances, wikiApi, wikiToFreebase, freebApi, opt, anchorMaxED);
		trainAdvancedAnnotationGatherer.dumpExamplesLibSvm("train_adv_ann_noscaled.dat", new NoFeatureNormalizer());

		System.out.println("Building normalizer...");
		ZScoreFeatureNormalizer fNorm = new ZScoreFeatureNormalizer(trainAdvancedAnnotationGatherer);

		//List<Triple<AnnotationRegressor, Double, int[]>> annAndFeatures = getLiblinearAnnotationRegressor(trainAdvancedAnnotationGatherer, develAdvancedAnnotationGatherer, featuresSetsToTest, fNorm, anchorMaxED, mcrs);
		List<Triple<AnnotationRegressor, Double, int[]>> annAndFeatures = getLibsvmAnnotationRegressor(trainAdvancedAnnotationGatherer, develAdvancedAnnotationGatherer, featuresSetsToTest, fNorm, anchorMaxED, mcrs);

		AnnotationRegressor bestAnnReg = null;
		double bestMacroF1 = Double.NEGATIVE_INFINITY;
		double bestThr = -1;
		for (Triple<AnnotationRegressor, Double, int[]> t : annAndFeatures){
			for (double thr = -0.95590; thr < -0.7; thr += 0.05) {
				System.out.println("Testing threshold "+thr);

				MetricsResultSet metrics = ParameterTester
						.testAnnotationRegressorModel(
								t.getLeft(),
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
				mcrs.add(new ModelConfigurationResult(t.getRight(), -1, -1,
						-1, t.getMiddle(), thr, tp, fp, fn, totVects - tp - fp - fn,
						microF1, macroF1, macroRec, macroPrec));
				if (macroF1 > bestMacroF1){
					bestMacroF1 = macroF1;
					bestAnnReg = t.getLeft();
					bestThr = thr;
				}
			}
		}

		//dumpPredictions(bestAnnReg,fNorm,develAdvancedAnnotationGatherer, bestThr, develInstances);

		for (ModelConfigurationResult mcr : mcrs)
			System.out.printf("%.5f%%\t%.5f%%\t%.5f%%%n",
					mcr.getMacroPrecision() * 100, mcr.getMacroRecall() * 100,
					mcr.getMacroF1() * 100);
		for (ModelConfigurationResult mcr : mcrs)
			System.out.println(mcr.getReadable());

	}

	private static List<Triple<AnnotationRegressor, Double, int[]>> getLibsvmAnnotationRegressor(ExampleGatherer<Annotation, HashSet<Annotation>> trainAdvancedAnnotationGatherer, ExampleGatherer<Annotation, HashSet<Annotation>> develAdvancedAnnotationGatherer, int[][] featuresSetsToTest, ZScoreFeatureNormalizer fNorm, double anchorMaxED, List<ModelConfigurationResult> mcrs) throws Exception{
		List<Triple<AnnotationRegressor, Double, int[]>> res = new Vector<>();
		for (int[] ftrs : featuresSetsToTest) {
			String trainFileLibLinear = "train_adv_ann_scaled.dat";
			System.out.println("Dumping Annotation problems...");
			trainAdvancedAnnotationGatherer.dumpExamplesLibSvm(trainFileLibLinear, fNorm, ftrs);
			//develAdvancedAnnotationGatherer.dumpExamplesLibSvm("devel_adv_ann_scaled.dat", fNorm, ftrs);
			double[][] paramsToTest = new double[][] {
					//{0.02500, 1.0},
					//{0.01644, 0.90420},
					//{0.02280, 1.17972},
					//{0.02420, 1.13552}
					{0.01515, 1.07181},
			};
			for (double[] paramsToTestArray : paramsToTest) {
				double gamma = paramsToTestArray[0];
				double C = paramsToTestArray[1];
				String fileBase = getModelFileNameBaseAF(
						ftrs, C,
						gamma, anchorMaxED) + ".libsvm";

				svm_problem trainProblem = trainAdvancedAnnotationGatherer.generateLibSvmProblem(ftrs, fNorm);
				svm_parameter param = ParameterTester.getParametersEFRegressor(gamma, C);						
				System.out.println("Training binary classifier...");
				svm_model model = ParameterTester.trainModel(param, trainProblem);
				svm.svm_save_model(fileBase + ".model", model);
				fNorm.dump(fileBase + ".zscore");
				AnnotationRegressor annReg = new LibSvmAnnotationRegressor(model);

				res.add(new ImmutableTriple<AnnotationRegressor, Double, int[]>(annReg, C, ftrs));
			}
		}
		return res;
	}

	private static void dumpPredictions(AnnotationRegressor annReg, FeatureNormalizer fNorm, ExampleGatherer<Annotation, HashSet<Annotation>> annotationGatherer, double threshold, List<String> develInstances) throws IOException{
		String dumpPredictionFile = "dump_predictions.dat";
		PrintWriter writer = new PrintWriter(dumpPredictionFile, "UTF-8");
		int i=0;
		for (Pair<Vector<Pair<FeaturePack<Annotation>, Annotation>>, HashSet<Annotation>> ftrsAndDatasAndGold : annotationGatherer
				.getDataAndFeaturePacksAndGoldOnePerInstance()) {
			String query = develInstances.get(i);
			System.out.println(query);
			List<Pair<Annotation, Double>> candidateAndPred = new Vector<>();

			for (Pair<FeaturePack<Annotation>, Annotation> p : ftrsAndDatasAndGold.first) {
				double predictedScore = annReg.predictScore(p.first, fNorm);
				candidateAndPred.add(new Pair<Annotation, Double>(p.second, predictedScore));
			}
			HashSet<Annotation> solution = ProblemReduction.Sa2WToA2W(IndividualAnnotationLinkBack.getResult(candidateAndPred, threshold));
			for (Pair<Annotation, Double> p: candidateAndPred){
				double score = SolutionComputer.AnnotationSetSolutionComputer.candidateScoreStatic(
						p.first, ftrsAndDatasAndGold.second, new StrongAnnotationMatch(wikiApi));
				writer.printf("%.6f\t%.6f\n", score, p.second);

				Annotation a = p.first;

				String comment = null;
				if (score == 1){
					if (p.second < threshold)
						comment = "FN (threshold)";
					else if (solution.contains(a))
						comment = "TP";
					else
						comment = "FN (collision)";
				} else {
					if (p.second < threshold)
						comment = "TN (threshold)";
					else if (solution.contains(a))
						comment = "FP";
					else
						comment = "TN (collision)";
				}

				System.out.printf("%s pred_score:%.3f %s -> %d (%s)%n", comment, p.second, query.substring(a.getPosition(), a.getPosition() + a.getLength()), a.getConcept(), wikiApi.getTitlebyId(a.getConcept()));
			}
			i++;
		}
		writer.close();
	}

	private static List<Triple<AnnotationRegressor, Double, int[]>> getLiblinearAnnotationRegressor(ExampleGatherer<Annotation, HashSet<Annotation>> trainAdvancedAnnotationGatherer, ExampleGatherer<Annotation, HashSet<Annotation>> develAdvancedAnnotationGatherer, int[][] featuresSetsToTest, ZScoreFeatureNormalizer fNorm, double anchorMaxED, List<ModelConfigurationResult> mcrs) throws Exception{

		List<Triple<AnnotationRegressor, Double, int[]>> res = new Vector<>();
		for (int[] ftrs : featuresSetsToTest) {
			String trainFileLibLinear = "train_adv_ann_scaled.dat";
			System.out.println("Dumping Annotation problems...");
			trainAdvancedAnnotationGatherer.dumpExamplesLibSvm(trainFileLibLinear, fNorm, ftrs);
			develAdvancedAnnotationGatherer.dumpExamplesLibSvm("devel_adv_ann_scaled.dat", fNorm, ftrs);

			for (int modelType : new int[] { 13 }) {
				for (double c = 1.0; c <= 1.0; c += 0.5) {
					String ARModel = getModelFileNameBaseAF(ftrs, c, anchorMaxED);
					String modelFile = ARModel + "." + modelType
							+ ".regressor.model";
					fNorm.dump(ARModel + ".regressor.zscore");

					String cmd = String.format(
							"%s/train -s %d -c %.8f %s %s", LIBLINEAR_BASE,
							modelType, c, trainFileLibLinear, modelFile);
					System.out
					.println("Training libLinear model... " + cmd);
					Runtime.getRuntime().exec(cmd).waitFor();
					System.out.println("Model trained.");

					LibLinearAnnotatorRegressor annReg = new LibLinearAnnotatorRegressor(
							modelFile);

					res.add(new ImmutableTriple<AnnotationRegressor, Double, int[]>(annReg, c, ftrs));
				}
			}
		}
		return res;
	}

	public static void generateCollectiveModel() throws Exception {

		int[][] featuresSetsToTest = new int[][] {
				SmaphUtils.getAllFtrVect(new BindingFeaturePack().getFeatureCount()),
		};

		/*		OptDataset opt = OptDataset.ERD_CHALLENGE;
		boolean useS2 = true, useS3 = true, useS6 = true;*/

		OptDataset opt = OptDataset.SMAPH_DATASET;
		boolean useS2 = true, useS3 = true, useS6 = true;

		/*OptDataset opt = OptDataset.SMAPH_DATASET_NE;
		boolean useS2 = true, useS3 = true, useS6 = true;*/

		String prefix = "";
		if (opt == OptDataset.ERD_CHALLENGE) prefix = "ERD-";
		else if (opt == OptDataset.SMAPH_DATASET) prefix = "SMAPH-";
		else if (opt == OptDataset.SMAPH_DATASET_NE) prefix = "SMAPHNE-";
		else throw new RuntimeException("OptDataset not recognized.");
		prefix += (useS2 ? "S2" : "") + (useS3 ? "S3" : "") + (useS6 ? "S6" : "");
		prefix += ".before.with.merged-nodefault-newanchors";

		List<ModelConfigurationResult> mcrs = new Vector<>();
		SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
				.getDefaultBingAnnotatorGatherer(wikiApi, 
						bingKey, useS2, useS3, useS6);
		WATAnnotator.setCache("wikisense.cache");

		ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> trainLinkBackGatherer = new ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>>();
		ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> develLinkBackGatherer = new ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>>();
		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
				bingAnnotator, null, null, trainLinkBackGatherer,
				develLinkBackGatherer, null, null, null, null, wikiApi, wikiToFreebase, freebApi, opt, -1);
		WATRelatednessComputer.flush();

		//List<Triple<BindingRegressor, FeatureNormalizer, int[]>> regressors = getLibLinearBindingRegressors(featuresSetsToTest, trainLinkBackGatherer, develLinkBackGatherer, mcrs);
		List<Triple<BindingRegressor, FeatureNormalizer, int[]>> regressors = getRanklibBindingRegressors(featuresSetsToTest, trainLinkBackGatherer, develLinkBackGatherer, mcrs, prefix);

		for (Triple<BindingRegressor, FeatureNormalizer, int[]> t : regressors){
			MetricsResultSet metrics = ParameterTester
					.testBindingRegressorModel(
							t.getLeft(),
							develLinkBackGatherer,
							t.getMiddle(),
							new SolutionComputer.BindingSolutionComputer(
									wikiApi));

			int tp = metrics.getGlobalTp();
			int fp = metrics.getGlobalFp();
			int fn = metrics.getGlobalFn();
			float microF1 = metrics.getMicroF1();
			float macroF1 = metrics.getMacroF1();
			float macroRec = metrics.getMacroRecall();
			float macroPrec = metrics.getMacroPrecision();
			int totVects = develLinkBackGatherer.getExamplesCount();
			ModelConfigurationResult mcr = new ModelConfigurationResult(t.getRight(), -1, -1, -1, -1, -1,
					tp, fp, fn, totVects - tp - fp - fn, microF1,
					macroF1, macroRec, macroPrec);
			System.out.printf("%.5f%%\t%.5f%%\t%.5f%%%n",
					mcr.getMacroPrecision() * 100, mcr.getMacroRecall() * 100,
					mcr.getMacroF1() * 100);
			mcrs.add(mcr);
		}
		for (ModelConfigurationResult mcr : mcrs)
			System.out.println(mcr.getReadable());
	}

	private static List<Triple<BindingRegressor, FeatureNormalizer, int[]>> getLibLinearBindingRegressors(
			int[][] featuresSetsToTest,
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> trainLinkBackGatherer,
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> develLinkBackGatherer,
			List<ModelConfigurationResult> mcrs)
					throws IOException, InterruptedException {
		List<Triple<BindingRegressor, FeatureNormalizer, int[]>> res = new Vector<>();
		for (int[] ftrs : featuresSetsToTest) {
			System.out.println("Building normalizer...");
			ZScoreFeatureNormalizer brNorm = new ZScoreFeatureNormalizer(
					trainLinkBackGatherer);
			brNorm.dump("models/binding.zscore");
			/*
			 * System.out.println("Dumping LB development problems...");
			 * develLinkBackGatherer.dumpExamplesLibSvm("devel.dat", fn);
			 */

			String trainFileLibLinear = "train_binding_full.dat";
			System.out.println("Dumping binding training problems...");
			trainLinkBackGatherer
			.dumpExamplesLibSvm(trainFileLibLinear, brNorm);

			for (int modelType : new int[] { 12 }) {
				for (double c = 0.4; c <= 0.4; c += 0.1) {
					String BRModel = getModelFileNameBaseLB(ftrs, c) + ".full";
					String modelFile = BRModel + "." + modelType
							+ ".regressor.model";
					brNorm.dump(BRModel + ".regressor.zscore");

					String cmd = String.format("%s/train -s %d -c %.8f %s %s",
							LIBLINEAR_BASE, modelType, c, trainFileLibLinear,
							modelFile);
					System.out.println("Training libLinear model (full)... "
							+ cmd);
					Runtime.getRuntime().exec(cmd).waitFor();
					System.out.println("Model trained (full).");

					LibLinearBindingRegressor br = new LibLinearBindingRegressor(
							modelFile);
					res.add(new ImmutableTriple<BindingRegressor, FeatureNormalizer, int[]>(br, brNorm, ftrs));
				}
			}
		}
		return res;
	}

	private static List<Triple<BindingRegressor, FeatureNormalizer, int[]>> getRanklibBindingRegressors(
			int[][] featuresSetsToTest,
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> trainLinkBackGatherer,
			ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> develLinkBackGatherer,
			List<ModelConfigurationResult> mcrs, String prefix)
					throws IOException, InterruptedException {
		List<Triple<BindingRegressor, FeatureNormalizer, int[]>> res = new Vector<>();

		String trainFile = "train_binding_ranking_"+prefix+".dat";
		String develFile = "devel_binding_ranking_"+prefix+".dat";
		String normFile = "models/train_binding_ranking_"+prefix+".zscore";
		//double defaultValueNorm = 0.0;

		System.out.println("Building normalizer...");
		ZScoreFeatureNormalizer brNorm = new ZScoreFeatureNormalizer(trainLinkBackGatherer/*, defaultValueNorm*/);
		brNorm.dump(normFile);
		System.out.println("Dumping binding training problems for ranking...");
		trainLinkBackGatherer.dumpExamplesRankLib(trainFile, brNorm);
		System.out.println("Dumping binding development problems for ranking...");
		develLinkBackGatherer.dumpExamplesRankLib(develFile, brNorm);

		for (int[] ftrs : featuresSetsToTest) {
			String ftrListFile = generateFeatureListFile(ftrs);
			for (int modelType : new int[] { 6 }) {
				for (int ncdgTop : new int[] {14,15,19,21,23,24}){
					String optMetric = "NDCG@" + ncdgTop;
					String rankModelBase = getModelFileNameBaseRL(ftrs) + ".full";
					String modelFile = rankModelBase + "." + modelType+ "." + optMetric + "." + prefix + ".model";
					String cliOpts = String.format("-feature %s -ranker %d -metric2t %s -train %s -validate %s -save %s",
							ftrListFile, modelType, optMetric, trainFile, develFile, modelFile);
					System.out.println("Training rankLib model (binding)... " + cliOpts);
					Evaluator.main(cliOpts.split("\\s+"));
					System.out.println("Model trained (binding).");

					BindingRegressor br = new RankLibBindingRegressor(modelFile);
					FeatureNormalizer brNormLoaded = new ZScoreFeatureNormalizer(normFile, new BindingFeaturePack()/*, defaultValueNorm*/);
					res.add(new ImmutableTriple<BindingRegressor, FeatureNormalizer, int[]>(br, brNormLoaded, ftrs));
				}
			}
		}
		return res;

	}

	public static String getModelFileNameBaseRL(int[] ftrs) {
		return String.format("models/model_%s_RL",
				getFtrListRepresentation(ftrs));
	}

	public static String getModelFileNameBaseLB(int[] ftrs, double C) {
		return String.format("models/model_%s_LB_%.3f_%.8f",
				getFtrListRepresentation(ftrs), C);
	}

	public static String getModelFileNameBaseEF(int[] ftrs, double wPos,
			double wNeg, double gamma, double C) {
		return String.format("models/model_%s_EF_%.5f_%.5f_%.8f_%.8f",
				getFtrListRepresentation(ftrs), wPos, wNeg, gamma, C);
	}

	private static String getModelFileNameBaseAF(int[] ftrs,
			double c, double anchorMaxED) {
		return String.format("models/model_%s_AF_%.8f_%.3f",
				getFtrListRepresentation(ftrs), c, anchorMaxED);
	}
	private static String getModelFileNameBaseAF(int[] ftrs,
			double c, double gamma, double anchorMaxED) {
		return String.format("models/model_%s_AF_%.8f_%.3f_%.8f",
				getFtrListRepresentation(ftrs), c, anchorMaxED, gamma);
	}

	private static String generateFeatureListFile(int[] ftrs) throws IOException {
		String filename = "/tmp/feature_list_"+getFtrListRepresentation(ftrs);
		FileWriter fw = new FileWriter(filename);
		for (int f : ftrs)
			fw.write(String.format("%d%n", f));
		fw.close();
		return filename;
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
