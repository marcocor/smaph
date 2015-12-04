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
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.batframework.utils.FreebaseApi;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.learn.GenerateTrainingAndTest.OptDataset;
import it.unipi.di.acube.smaph.learn.ParameterTester.ParameterTesterEF;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;

public class TuneModelLibSvm {
	private static final int THREADS_NUM = 4;

	public enum OptimizaionProfiles {
		MAXIMIZE_TN, MAXIMIZE_MICRO_F1, MAXIMIZE_MACRO_F1
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

		WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache", "redirect.cache");
		FreebaseApi freebApi = new FreebaseApi(freebKey, freebCache);

		OptDataset opt = OptDataset.SMAPH_DATASET;
		WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase(
				"mapdb");

		SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
				.getDefaultBingAnnotatorGatherer(wikiApi, 
						bingKey, true, true, true);

		ExampleGatherer<Tag, HashSet<Tag>> trainEntityFilterGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
		ExampleGatherer<Tag, HashSet<Tag>> develEntityFilterGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();

		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
				bingAnnotator, trainEntityFilterGatherer,
				develEntityFilterGatherer, null, null, null, null, null, null, wikiApi, wikiToFreebase, freebApi, opt,-1);
		int[] ftrToInclude = SmaphUtils.getAllFtrVect(new EntityFeaturePack().getFeatureCount());


		Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> modelAndStatsEF = trainIterative(
				trainEntityFilterGatherer, develEntityFilterGatherer,
				OptimizaionProfiles.MAXIMIZE_MACRO_F1, -1.0,
				ftrToInclude, wikiApi);
		for (ModelConfigurationResult res : modelAndStatsEF.first)
			System.out.println(res.getReadable());

		System.gc();

		System.out.println("Best EF:" + modelAndStatsEF.second.getReadable());

		System.out.println("Flushing Bing API...");

		BingInterface.flush();
		wikiApi.flush();
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

				ParameterTesterEF pt = new ParameterTesterEF(bestwPos, bestwNeg, featuresToInclude, trainGatherer, develGatherer, bestGamma, bestC, scoreboardFtrSelection, wikiApi);
				new AblationFeatureSelector<Tag, HashSet<Tag>>(pt, optProfile, optProfileThreshold, scoreboardFtrSelection).run();

				/*				new IncrementalFeatureSelector(bestwPos, bestwNeg, bestGamma,
						bestC, trainGatherer, develGatherer, optProfile,
						optProfileThreshold, scoreboardFtrSelection, wikiApi)
						.run();
				 */
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
					c = TuneModelLibSvm.computeExpParameter(cMax, cMin, kappaC, cI,
							steps);
					gamma = TuneModelLibSvm.computeExpParameter(gammaMax, gammaMin,
							kappaGamma, gammaI, steps);
					futures.add(execServ.submit(new ParameterTester.ParameterTesterEF(wPos, wNeg,
							features, trainGatherer,
							testGatherer,
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
			for (int posI = 0; (wPos = TuneModelLibSvm.computeExpParameter(wPosMax, wPosMin,
					kappaPos, posI, steps)) <= wPosMax; posI++)
				for (int negI = 0; (wNeg = TuneModelLibSvm.computeExpParameter(wNegMax, wNegMin,
						kappaNeg, negI, steps)) <= wNegMax; negI++)
					futures.add(execServ.submit(new ParameterTester.ParameterTesterEF(wPos, wNeg, features, trainGatherer,
							testGatherer, gamma, C, scoreboard, wikiApi)));

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

	static class AblationFeatureSelector<E extends Serializable,G extends Serializable> implements Runnable {
		private double optProfileThreshold;
		private OptimizaionProfiles optProfile;
		Vector<ModelConfigurationResult> scoreboard;
		private ParameterTester<E, G> pt;

		public AblationFeatureSelector(ParameterTester<E, G> pt, OptimizaionProfiles optProfile, double optProfileThreshold,
				Vector<ModelConfigurationResult> scoreboard) {
			this.optProfileThreshold = optProfileThreshold;
			this.optProfile = optProfile;
			this.scoreboard = scoreboard;
			this.pt = pt;
		}

		@Override
		public void run() {

			ModelConfigurationResult bestBase;
			try {
				bestBase = pt.call();
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
								.submit(pt.cloneWithFeatures(pickedFtrsIteration));
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

	static class IncrementalFeatureSelector<E extends Serializable, G extends Serializable> implements Runnable {
		private double optProfileThreshold;
		private ExampleGatherer<E, G> testGatherer;
		private OptimizaionProfiles optProfile;
		Vector<ModelConfigurationResult> scoreboard;
		private ParameterTester<E, G> pt;

		public IncrementalFeatureSelector(ParameterTester<E,G> pt, ExampleGatherer<E, G>testGatherer, Vector<ModelConfigurationResult> scoreboard, OptimizaionProfiles optProfile, double optProfileThreshold) {
			this.pt = pt;
			this.testGatherer = testGatherer;
			this.scoreboard = scoreboard;
			this.optProfile = optProfile;
			this.optProfileThreshold = optProfileThreshold;
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
								.submit(pt);
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
