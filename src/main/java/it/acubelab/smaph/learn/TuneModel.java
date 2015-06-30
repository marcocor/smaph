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
import it.acubelab.batframework.metrics.Metrics;
import it.acubelab.batframework.metrics.MetricsResultSet;
import it.acubelab.batframework.systemPlugins.WATAnnotator;
import it.acubelab.batframework.utils.FreebaseApi;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.BingInterface;
import it.acubelab.smaph.IndexMatch;
import it.acubelab.smaph.SmaphAnnotator;
import it.acubelab.smaph.SmaphConfig;
import it.acubelab.smaph.SmaphUtils;
import it.acubelab.smaph.learn.GenerateTrainingAndTest.OptDataset;
import it.acubelab.smaph.learn.featurePacks.EntityFeaturePack;
import it.acubelab.smaph.learn.featurePacks.FeaturePack;
import it.acubelab.smaph.learn.normalizer.FeatureNormalizer;
import it.acubelab.smaph.learn.normalizer.ScaleFeatureNormalizer;
import it.acubelab.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.Serializable;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Vector;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

public class TuneModel {
	private static final int THREADS_NUM = 4;

	public enum OptimizaionProfiles {
		MAXIMIZE_TN, MAXIMIZE_MICRO_F1, MAXIMIZE_MACRO_F1
	}

	public static svm_parameter getParametersEF(double wPos, double wNeg,
			double gamma, double C) {
		svm_parameter param = new svm_parameter();
		param.svm_type = svm_parameter.C_SVC;
		param.kernel_type = svm_parameter.RBF;
		param.degree = 2;
		param.gamma = gamma;
		param.coef0 = 0;
		param.nu = 0.5;
		param.cache_size = 100;
		param.C = C;
		param.eps = 0.001;
		param.p = 0.1;
		param.shrinking = 1;
		param.probability = 0;
		param.nr_weight = 2;
		param.weight_label = new int[] { 1, -1 };
		param.weight = new double[] { wPos, wNeg };
		return param;
	}
	
	public static svm_parameter getParametersLB(
			double gamma, double c) {
		svm_parameter param = new svm_parameter();
		param.svm_type = svm_parameter.EPSILON_SVR;
		param.kernel_type = svm_parameter.LINEAR;
		param.degree = 2;
		param.gamma = gamma;
		param.coef0 = 0;
		param.nu = 0.5;
		param.cache_size = 100;
		param.C = c;
		param.eps = 0.001;
		param.p = 0.1;
		param.shrinking = 1;
		param.probability = 0;
		param.nr_weight = 2;
		param.weight_label = new int[] { };
		param.weight = new double[] { };
		return param;	}

	public static svm_parameter getParametersEFRegressor(double gamma, double c) {
		
		svm_parameter params = getParametersEF (-1, -1, gamma, c);
		params.svm_type = svm_parameter.EPSILON_SVR;
		return params;
	}

	public static double computeExpParameter(double max, double min,
			double kappa, int iteration, int steps) {
		if (iteration < 0)
			return min;
		double exp = max == min ? 1 : Math.log((max - min) / kappa)
				/ Math.log(steps);

		return min + kappa * Math.pow(iteration, exp);
	}

	public static int paramToIter(double weight, double max, double min,
			double kappa, int steps) {
		if (max == min)
			return 0;
		double exp = Math.log((max - min) / kappa) / Math.log(steps);

		return (int) Math.round(Math
				.pow((weight - min) / kappa, 1.0 / exp));
	}



	public static void main(String[] args) throws Exception {
		Locale.setDefault(Locale.US);
		SmaphConfig.setConfigFile("smaph-config.xml");
		String bingKey = SmaphConfig.getDefaultBingKey();
		String freebKey = SmaphConfig.getDefaultFreebaseKey();
		String freebCache = SmaphConfig.getDefaultFreebaseKey();
		BingInterface.setCache(SmaphConfig.getDefaultBingCache());
		WATAnnotator.setCache("wikisense.cache");

		WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache",
				"redirect.cache");
		FreebaseApi freebApi = new FreebaseApi(freebKey, freebCache);
			
		Vector<ModelConfigurationResult> bestEFModels = new Vector<>();
		OptDataset opt = OptDataset.SMAPH_DATASET;
		for (double boldFilterThr = 0.06; boldFilterThr <= 0.06; boldFilterThr += 0.1) {
			WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase(
					"mapdb");

			SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
					.getDefaultBingAnnotatorGatherer(wikiApi, 
							boldFilterThr, bingKey);

			ExampleGatherer<Tag, HashSet<Tag>> trainEntityFilterGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
			ExampleGatherer<Tag, HashSet<Tag>> develEntityFilterGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
			
			GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
					bingAnnotator, trainEntityFilterGatherer,
					develEntityFilterGatherer, null, null, null, null, null,null, null,
					null, null, null, null, null, wikiApi, wikiToFreebase, freebApi, opt,-1);
			int[] ftrToInclude = { 2, 3, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 33, 34, 35, 36, 37,39,40,41,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66};
			//int[] ftrToInclude = { 2, 3, 15};
					
		
			Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> modelAndStatsEF = trainIterative(
					trainEntityFilterGatherer, develEntityFilterGatherer,
					OptimizaionProfiles.MAXIMIZE_MACRO_F1, -1.0,
					ftrToInclude, wikiApi);
			for (ModelConfigurationResult res : modelAndStatsEF.first)
				System.out.println(res.getReadable());

			bestEFModels.add(modelAndStatsEF.second);
			System.gc();
		}

		for (ModelConfigurationResult modelAndStatsEF : bestEFModels)
			System.out.println("Best EF:" + modelAndStatsEF.getReadable());

		System.out.println("Flushing Bing API...");

		BingInterface.flush();
		wikiApi.flush();
	}

	public static svm_model trainModel(svm_parameter param, 
			svm_problem trainProblem) {
		String error_msg = svm.svm_check_parameter(trainProblem, param);

		if (error_msg != null) {
			System.err.print("ERROR: " + error_msg + "\n");
			System.exit(1);
		}

		return svm.svm_train(trainProblem, param);
	}

	private static Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> trainIterative(
			ExampleGatherer<Tag, HashSet<Tag>> trainGatherer,
			ExampleGatherer<Tag, HashSet<Tag>> develGatherer,
			OptimizaionProfiles optProfile, double optProfileThreshold, int[] featuresToInclude,
			 WikipediaApiInterface wikiApi) {
		Vector<ModelConfigurationResult> globalScoreboard = new Vector<>();
		double bestwPos;
		double bestwNeg;
		double broadwPosMin = 0.1;
		double broadwPosMax = 50.0;
		double broadwNegMin = 1.0;
		double broadwNegMax = 1.0;
		double broadkPos = 0.2;
		int broadSteps = 10;
		int fineSteps = 5;
		int fineStepsCGamma = 6;
		int iterations = 3;
		double bestGamma=1.0/trainGatherer.getFtrCount();
		double bestC=1;
		double fineCMin = bestC * 0.1;
		double fineCMax = bestC * 10.0;
		double fineGammaMin = bestGamma * 0.1;
		double fineGammaMax = bestGamma * 10.0;

		// broad tune weights (all ftr)
		try {
			Pair<Double, Double> bestBroadWeights = new WeightSelector(
					broadwPosMin, broadwPosMax, broadkPos, broadwNegMin,
					broadwNegMax, 1.0, bestGamma, bestC, broadSteps,
					featuresToInclude, trainGatherer, develGatherer,
					optProfile, globalScoreboard, wikiApi).call();
			bestwPos = bestBroadWeights.first;
			bestwNeg = bestBroadWeights.second;
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException();
		}
		System.err.println("Done broad weighting.");

		int bestIterPos = paramToIter(bestwPos, broadwPosMax,
				broadwPosMin, broadkPos, broadSteps);
		double finewPosMin = computeExpParameter(broadwPosMax,
				broadwPosMin, broadkPos, bestIterPos - 1, broadSteps);
		double finewPosMax = computeExpParameter(broadwPosMax,
				broadwPosMin, broadkPos, bestIterPos + 1, broadSteps);
		double finewNegMin = 0.5;
		double finewNegMax = 2.0;

		ModelConfigurationResult bestResult = ModelConfigurationResult
				.findBest(globalScoreboard, optProfile, optProfileThreshold);

		for (int iteration = 0; iteration < iterations; iteration++) {
			// Do feature selection
			ModelConfigurationResult bestFtr;
			{
				Vector<ModelConfigurationResult> scoreboardFtrSelection = new Vector<>();
				
				  new AblationFeatureSelector(bestwPos, bestwNeg, bestGamma, bestC,
				  trainGatherer, develGatherer, featuresToInclude,
				  optProfile, optProfileThreshold, scoreboardFtrSelection, wikiApi)
				  .run();
				 
				/*new IncrementalFeatureSelector(bestwPos, bestwNeg, gamma, C,
						boldFilterThreshold, trainGatherer, develGatherer,featuresToInclude,
						optProfile, optProfileThreshold, scoreboardFtrSelection)
						.run();*/
				bestFtr = ModelConfigurationResult
						.findBest(scoreboardFtrSelection, optProfile,
								optProfileThreshold);
				globalScoreboard.addAll(scoreboardFtrSelection);
				System.err.printf("Done feature selection (iteration %d).%n",
						iteration);
			}
			int[] bestFeatures = bestFtr.getFeatures();

			// Fine-tune weights
			{
				Vector<ModelConfigurationResult> scoreboardWeightsTuning = new Vector<>();
				Pair<Double, Double> weights;
				try {
					weights = new WeightSelector(finewPosMin, finewPosMax, -1,
							finewNegMin, finewNegMax, -1, bestGamma, bestC, fineSteps,
							bestFeatures, trainGatherer,
							develGatherer, optProfile, scoreboardWeightsTuning, wikiApi)
							.call();
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException();
				}
				bestwPos = weights.first;
				bestwNeg = weights.second;
				finewPosMin = bestwPos * 0.5;
				finewPosMax = bestwPos * 2.0;
				finewNegMin = bestwNeg * 0.5;
				finewNegMax = bestwNeg * 2.0;

				globalScoreboard.addAll(scoreboardWeightsTuning);
				System.err.printf("Done weights tuning (iteration %d).%n",
						iteration);
			}
			// Fine-tune C and Gamma
			{
				Vector<ModelConfigurationResult> scoreboardGammaCTuning = new Vector<>();
				Pair<Double, Double> cGamma;
				try {
					cGamma = new GammaCSelector(fineCMin, fineCMax, 0.1,
							fineGammaMin, fineGammaMax, 0.1, bestwPos, bestwNeg, fineStepsCGamma,
							bestFeatures, trainGatherer,
							develGatherer, optProfile, scoreboardGammaCTuning,
							wikiApi).call();
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException();
				}
				bestC = cGamma.first;
				bestGamma = cGamma.second;
				fineCMin = bestC * 0.1;
				fineCMax = bestC * 10.0;
				fineGammaMin = bestGamma * 0.1;
				fineGammaMax = bestGamma * 10.0;

				globalScoreboard.addAll(scoreboardGammaCTuning);
				System.err.printf("Done weights tuning (iteration %d).%n",
						iteration);
			}
			
			ModelConfigurationResult newBest = ModelConfigurationResult
					.findBest(globalScoreboard, optProfile, optProfileThreshold);
			if (bestResult != null
					&& newBest.equalResult(bestResult, optProfile,
							optProfileThreshold)) {
				System.err.printf("Not improving, stopping on iteration %d.%n",
						iteration);
				break;
			}
			bestResult = newBest;
		}

		return new Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult>(
				globalScoreboard, ModelConfigurationResult.findBest(
						globalScoreboard, optProfile, optProfileThreshold));

	}

	public static class ParameterTester implements
			Callable<ModelConfigurationResult> {
		private double wPos, wNeg, gamma, C;
		private ExampleGatherer<Tag, HashSet<Tag>> trainGatherer;
		private ExampleGatherer<Tag, HashSet<Tag>> testGatherer;
		private int[] features;
		Vector<ModelConfigurationResult> scoreboard;
		private WikipediaApiInterface wikiApi;

		public ParameterTester(double wPos, double wNeg, int[] features,
				ExampleGatherer<Tag, HashSet<Tag>> trainEQFGatherer,
				ExampleGatherer<Tag, HashSet<Tag>> testEQFGatherer,
				OptimizaionProfiles optProfile, double optProfileThreshold,
				double gamma, double C,
				Vector<ModelConfigurationResult> scoreboard, WikipediaApiInterface wikiApi) {
			this.wPos = wPos;
			this.wNeg = wNeg;
			this.features = features;
			this.trainGatherer = trainEQFGatherer;
			this.testGatherer = testEQFGatherer;
			this.scoreboard = scoreboard;
			this.gamma = gamma;
			this.C = C;
			this.wikiApi = wikiApi;
		}
		
		public static <T extends Serializable, G extends Serializable> MetricsResultSet testLibSvmModel(svm_model model, ExampleGatherer<T, G> testGatherer, int[] features, FeatureNormalizer scaleFn, SolutionComputer<T, G> sc){
			
			List<Pair<Vector<Pair<svm_node[], T>>, G>> ftrsAndDatasAndGolds = testGatherer.getDataAndNodesAndGoldOnePerInstance(features, scaleFn);

			List<G> golds = new Vector<>();
			List<List<Pair<T, Double>>> candidateAndPreds = new Vector<>();
			for (Pair<Vector<Pair<svm_node[], T>>, G> ftrsAndDatasAndGold : ftrsAndDatasAndGolds) {
				golds.add(ftrsAndDatasAndGold.second);
				List<Pair<T, Double>> candidateAndPred = new Vector<>();
				candidateAndPreds.add(candidateAndPred);
				for (Pair<svm_node[], T> p : ftrsAndDatasAndGold.first)
					candidateAndPred.add(new Pair<T, Double>(p.second, svm.svm_predict(model, p.first)));
			}

			try {
				return sc.getResults(candidateAndPreds, golds);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		
		public static <T extends Serializable, G extends Serializable> MetricsResultSet testLibLinearModel(LibLinearModel model, ExampleGatherer<T, G> testGatherer, FeatureNormalizer scaleFn, SolutionComputer<T, G> sc) throws IOException{
			List<G> golds = new Vector<>();
			List<List<Pair<T, Double>>> candidateAndPreds = new Vector<>();
			for (Pair<Vector<Pair<FeaturePack<T>, T>>, G> ftrsAndDatasAndGold : testGatherer.getDataAndFeaturePacksAndGoldOnePerInstance()) {
				golds.add(ftrsAndDatasAndGold.second);
				List<Pair<T, Double>> candidateAndPred = new Vector<>();
				candidateAndPreds.add(candidateAndPred);
				for (Pair<FeaturePack<T>, T> p : ftrsAndDatasAndGold.first){
					double predictedScore = model.predictScore(p.first, scaleFn);
					candidateAndPred.add(new Pair<T, Double>(p.second, predictedScore));
				}
			}
			
			try {
				return sc.getResults(candidateAndPreds, golds);
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}

		

		@Override
		public ModelConfigurationResult call() throws Exception {

			ZScoreFeatureNormalizer scaleFn = new ZScoreFeatureNormalizer(trainGatherer);
			svm_problem trainProblem = trainGatherer.generateLibSvmProblem(this.features, scaleFn);

			svm_parameter param = getParametersEF(wPos, wNeg, gamma, C);

			svm_model model = trainModel(param,
					trainProblem);

			MetricsResultSet metrics = testLibSvmModel(model, testGatherer, this.features, scaleFn, new SolutionComputer.TagSetSolutionComputer(wikiApi));
			
			int tp = metrics.getGlobalTp();
			int fp = metrics.getGlobalFp();
			int fn = metrics.getGlobalFn();
			float microF1 = metrics.getMicroF1();
			float macroF1 = metrics.getMacroF1();
			float macroRec = metrics.getMacroRecall();
			float macroPrec = metrics.getMacroPrecision();

			ModelConfigurationResult mcr = new ModelConfigurationResult(
					features, wPos, wNeg, gamma, C, tp, fp, fn,
					testGatherer.getExamplesCount() - tp - fp - fn, microF1,
					macroF1, macroRec, macroPrec);

			synchronized (scoreboard) {
				scoreboard.add(mcr);
			}
			return mcr;

		}

	}
	static class GammaCSelector implements Callable<Pair<Double, Double>> {
		private double gammaMin, gammaMax, cMin, cMax, wPos, wNeg;
		private double optProfileThreshold;
		private ExampleGatherer<Tag, HashSet<Tag>> trainGatherer;
		private ExampleGatherer<Tag, HashSet<Tag>> testGatherer;
		private OptimizaionProfiles optProfile;
		private double kappaC, kappaGamma;
		private int[] features;
		Vector<ModelConfigurationResult> scoreboard;
		private int steps;
		private WikipediaApiInterface wikiApi;

		public GammaCSelector(double cMin, double cMax, double kappaC, double gammaMin, double gammaMax, double kappaGamma, double wPos, double wNeg, int steps, int[] features, ExampleGatherer<Tag, HashSet<Tag>> trainGatherer, ExampleGatherer<Tag, HashSet<Tag>> testGatherer, OptimizaionProfiles optProfile, Vector<ModelConfigurationResult> scoreboard, WikipediaApiInterface wikiApi) {
			if (kappaC == -1)
				kappaC = (cMax - cMin) / steps;
			if (kappaGamma == -1)
				kappaGamma = (gammaMax - gammaMin) / steps;

			if (!(kappaC > 0 && (cMax - cMin == 0 || kappaC <= cMax
					- cMin)))
				throw new IllegalArgumentException(String.format(
						"k must be between 0.0 and %f. Got %f", cMax
								- cMin, kappaC));
			if (!(kappaGamma > 0 && (gammaMax - gammaMin == 0 || kappaGamma <= gammaMax
					- gammaMin)))
				throw new IllegalArgumentException(String.format(
						"k must be between 0.0 and %f. Got %f", gammaMax
								- gammaMin, kappaGamma));
			this.gammaMin = gammaMin;
			this.gammaMax = gammaMax;
			this.cMin = cMin;
			this.cMax = cMax;
			this.kappaGamma = kappaGamma;
			this.kappaC = kappaC;
			this.features = features;
			this.trainGatherer = trainGatherer;
			this.testGatherer = testGatherer;
			this.optProfile = optProfile;
			this.scoreboard = scoreboard;
			this.wPos = wPos;
			this.wNeg = wNeg;
			this.steps = steps;
			this.wikiApi = wikiApi;
		}
		@Override
		public Pair<Double, Double> call() throws Exception {
			ExecutorService execServ = Executors
					.newFixedThreadPool(THREADS_NUM);
			List<Future<ModelConfigurationResult>> futures = new Vector<>();

			double c, gamma;
			for (int cI = 0; cI<steps; cI++)
				for (int gammaI = 0; gammaI<steps ; gammaI++){
					c = TuneModel.computeExpParameter(cMax, cMin, kappaC, cI,
							steps);
					gamma = TuneModel.computeExpParameter(gammaMax, gammaMin,
							kappaGamma, gammaI, steps);
					futures.add(execServ.submit(new ParameterTester(wPos, wNeg,
							features, trainGatherer,
							testGatherer, optProfile, optProfileThreshold,
							gamma, c, scoreboard, wikiApi)));
				}

			ModelConfigurationResult best = null;
			for (Future<ModelConfigurationResult> future : futures)
				try {
					ModelConfigurationResult res = future.get();
					if (best == null
							|| best.worseThan(res, optProfile,
									optProfileThreshold))
						best = res;
				} catch (InterruptedException | ExecutionException | Error e) {
					throw new RuntimeException(e);
				}
			execServ.shutdown();

			return new Pair<Double, Double>(best.getWPos(), best.getWNeg());
		}

	}
		
	static class WeightSelector implements Callable<Pair<Double, Double>> {
		private double wPosMin, wPosMax, wNegMin, wNegMax, gamma, C;
		private double optProfileThreshold;
		private ExampleGatherer<Tag, HashSet<Tag>> trainGatherer;
		private ExampleGatherer<Tag, HashSet<Tag>> testGatherer;
		private OptimizaionProfiles optProfile;
		private double kappaPos, kappaNeg;
		private int[] features;
		Vector<ModelConfigurationResult> scoreboard;
		private int steps;
		private WikipediaApiInterface wikiApi;

		public WeightSelector(double wPosMin, double wPosMax, double kappaPos,
				double wNegMin, double wNegMax, double kappaNeg, double gamma,
				double C, int steps,
				int[] features,
				ExampleGatherer<Tag, HashSet<Tag>> trainEQFGatherer,
				ExampleGatherer<Tag, HashSet<Tag>> testEQFGatherer,
				OptimizaionProfiles optProfile,
				Vector<ModelConfigurationResult> scoreboard, WikipediaApiInterface wikiApi) {
			if (kappaNeg == -1)
				kappaNeg = (wNegMax - wNegMin) / steps;
			if (kappaPos == -1)
				kappaPos = (wPosMax - wPosMin) / steps;

			if (!(kappaPos > 0 && (wPosMax - wPosMin == 0 || kappaPos <= wPosMax
					- wPosMin)))
				throw new IllegalArgumentException(String.format(
						"k must be between 0.0 and %f. Got %f", wPosMax
								- wPosMin, kappaPos));
			if (!(kappaNeg > 0 && (wNegMax - wNegMin == 0 || kappaNeg <= wNegMax
					- wNegMin)))
				throw new IllegalArgumentException(String.format(
						"k must be between 0.0 and %f. Got %f", wNegMax
								- wNegMin, kappaNeg));
			this.wNegMin = wNegMin;
			this.wNegMax = wNegMax;
			this.wPosMin = wPosMin;
			this.wPosMax = wPosMax;
			this.kappaNeg = kappaNeg;
			this.kappaPos = kappaPos;
			this.features = features;
			this.trainGatherer = trainEQFGatherer;
			this.testGatherer = testEQFGatherer;
			this.optProfile = optProfile;
			this.scoreboard = scoreboard;
			this.C = C;
			this.gamma = gamma;
			this.steps = steps;
			this.wikiApi = wikiApi;
		}

		@Override
		public Pair<Double, Double> call() throws Exception {
			ExecutorService execServ = Executors
					.newFixedThreadPool(THREADS_NUM);
			List<Future<ModelConfigurationResult>> futures = new Vector<>();

			double wPos, wNeg;
			for (int posI = 0; (wPos = TuneModel.computeExpParameter(wPosMax, wPosMin,
					kappaPos, posI, steps)) <= wPosMax; posI++)
				for (int negI = 0; (wNeg = TuneModel.computeExpParameter(wNegMax, wNegMin,
						kappaNeg, negI, steps)) <= wNegMax; negI++)
					futures.add(execServ.submit(new ParameterTester(wPos, wNeg,
							features, trainGatherer,
							testGatherer, optProfile, optProfileThreshold,
							gamma, C, scoreboard, wikiApi)));

			ModelConfigurationResult best = null;
			for (Future<ModelConfigurationResult> future : futures)
				try {
					ModelConfigurationResult res = future.get();
					if (best == null
							|| best.worseThan(res, optProfile,
									optProfileThreshold))
						best = res;
				} catch (InterruptedException | ExecutionException | Error e) {
					throw new RuntimeException(e);
				}
			execServ.shutdown();

			return new Pair<Double, Double>(best.getWPos(), best.getWNeg());
		}

	}

	static class AblationFeatureSelector implements Runnable {
		private double wPos, wNeg, gamma, C;
		private double optProfileThreshold;
		private ExampleGatherer<Tag, HashSet<Tag>> trainGatherer;
		private ExampleGatherer<Tag, HashSet<Tag>> testGatherer;
		private OptimizaionProfiles optProfile;
		Vector<ModelConfigurationResult> scoreboard;
		private WikipediaApiInterface wikiApi;
		private int[] featuresToInclude;

		public AblationFeatureSelector(double wPos, double wNeg, double gamma,
				double C,
				ExampleGatherer<Tag, HashSet<Tag>> trainGatherer,
				ExampleGatherer<Tag, HashSet<Tag>> testGatherer,
				int[] featuresToInclude, OptimizaionProfiles optProfile, double optProfileThreshold,
				Vector<ModelConfigurationResult> scoreboard, WikipediaApiInterface wikiApi) {
			this.wNeg = wNeg;
			this.wPos = wPos;
			this.optProfileThreshold = optProfileThreshold;
			this.trainGatherer = trainGatherer;
			this.testGatherer = testGatherer;
			this.optProfile = optProfile;
			this.scoreboard = scoreboard;
			this.gamma = gamma;
			this.C = C;
			this.wikiApi = wikiApi;
			this.featuresToInclude = featuresToInclude;
		}

		@Override
		public void run() {

			ModelConfigurationResult bestBase;
			try {
				bestBase = new ParameterTester(wPos, wNeg, featuresToInclude,
						trainGatherer, testGatherer, optProfile,
						optProfileThreshold, gamma, C, scoreboard, wikiApi).call();
			} catch (Exception e1) {
				e1.printStackTrace();
				throw new RuntimeException(e1);
			}

			while (bestBase.getFeatures().length > 1) {
				ExecutorService execServ = Executors
						.newFixedThreadPool(THREADS_NUM);
				List<Future<ModelConfigurationResult>> futures = new Vector<>();
				HashMap<Future<ModelConfigurationResult>, Integer> futureToFtrId = new HashMap<>();

				for (int testFtrId : bestBase.getFeatures()) {
					int[] pickedFtrsIteration = new int[bestBase.getFeatures().length -1];
					int j=0;
					for (int i=0; i<bestBase.getFeatures().length; i++)
						if (bestBase.getFeatures()[i] == testFtrId)
							continue;
						else
							pickedFtrsIteration[j++] = bestBase.getFeatures()[i];
							
					try {
						Future<ModelConfigurationResult> future = execServ
								.submit(new ParameterTester(wPos, wNeg,
										pickedFtrsIteration, trainGatherer,
										testGatherer, optProfile,
										optProfileThreshold, gamma, C,
										scoreboard, wikiApi));
						futures.add(future);
						futureToFtrId.put(future, testFtrId);

					} catch (Exception | Error e) {
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				}

				ModelConfigurationResult bestIter = null;
				for (Future<ModelConfigurationResult> future : futures)
					try {
						ModelConfigurationResult res = future.get();
						if (bestIter == null
								|| bestIter.worseThan(res, optProfile,
										optProfileThreshold))
							bestIter = res;
					} catch (InterruptedException | ExecutionException | Error e) {
						throw new RuntimeException(e);
					}
				execServ.shutdown();

				if (bestIter.worseThan(bestBase, optProfile,
						optProfileThreshold))
					break;
				else
					bestBase = bestIter;
			}
		}
	}

	static class IncrementalFeatureSelector implements Runnable {
		private double wPos, wNeg, gamma, C;
		private double optProfileThreshold;
		private ExampleGatherer<Tag, HashSet<Tag>> trainGatherer;
		private ExampleGatherer<Tag, HashSet<Tag>> testGatherer;
		private OptimizaionProfiles optProfile;
		private double editDistanceThreshold;
		Vector<ModelConfigurationResult> scoreboard;
		private WikipediaApiInterface wikiApi;

		public IncrementalFeatureSelector(double wPos, double wNeg,
				double gamma, double C, double editDistanceThreshold,
				ExampleGatherer<Tag, HashSet<Tag>> trainGatherer,
				ExampleGatherer<Tag, HashSet<Tag>> testGatherer,
				OptimizaionProfiles optProfile, double optProfileThreshold,
				Vector<ModelConfigurationResult> scoreboard, WikipediaApiInterface wikiApi) {
			this.wNeg = wNeg;
			this.wPos = wPos;
			this.optProfileThreshold = optProfileThreshold;
			this.trainGatherer = trainGatherer;
			this.testGatherer = testGatherer;
			this.optProfile = optProfile;
			this.editDistanceThreshold = editDistanceThreshold;
			this.scoreboard = scoreboard;
			this.gamma = gamma;
			this.C = C;
			this.wikiApi = wikiApi;
		}

		@Override
		public void run() {

			HashSet<Integer> ftrToTry = new HashSet<>();
			for (int f : SmaphUtils.getAllFtrVect(testGatherer.getFtrCount()))
				ftrToTry.add(f);

			ModelConfigurationResult bestBase = null;
			while (!ftrToTry.isEmpty()) {
				ModelConfigurationResult bestIter = bestBase;
				ExecutorService execServ = Executors
						.newFixedThreadPool(THREADS_NUM);
				List<Future<ModelConfigurationResult>> futures = new Vector<>();
				HashMap<Future<ModelConfigurationResult>, Integer> futureToFtrId = new HashMap<>();

				for (int testFtrId : ftrToTry) {
					int[] pickedFtrsIteration;
					if (bestBase == null)
						pickedFtrsIteration = new int[] { testFtrId };
					else {
						pickedFtrsIteration = new int[bestBase.getFeatures().length + 1];
						for (int i = 0; i < bestBase.getFeatures().length; i++)
							pickedFtrsIteration[i] = bestBase.getFeatures()[i];
						pickedFtrsIteration[pickedFtrsIteration.length - 1] = testFtrId;
						Arrays.sort(pickedFtrsIteration);

					}

					try {
						Future<ModelConfigurationResult> future = execServ
								.submit(new ParameterTester(wPos, wNeg,
										pickedFtrsIteration, trainGatherer,
										testGatherer, optProfile,
										optProfileThreshold, gamma, C,
										scoreboard, wikiApi));
						futures.add(future);
						futureToFtrId.put(future, testFtrId);

					} catch (Exception | Error e) {
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				}

				int bestFtrId = -1;
				for (Future<ModelConfigurationResult> future : futures)
					try {
						ModelConfigurationResult res = future.get();
						if (bestIter == null
								|| bestIter.worseThan(res, optProfile,
										optProfileThreshold)) {
							bestFtrId = futureToFtrId.get(future);
							bestIter = res;
						}
					} catch (InterruptedException | ExecutionException | Error e) {
						throw new RuntimeException(e);
					}
				execServ.shutdown();

				if (bestFtrId == -1) {
					break;
				} else {
					bestBase = bestIter;
					ftrToTry.remove(Integer.valueOf(bestFtrId));
				}

			}
		}
	}

}
