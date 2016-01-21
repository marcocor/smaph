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
import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.batframework.utils.FreebaseApi;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.EntityToVect;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.learn.GenerateTrainingAndTest.OptDataset;
import it.unipi.di.acube.smaph.learn.ParameterTester.ParameterTesterAR;
import it.unipi.di.acube.smaph.learn.ParameterTester.ParameterTesterEF;
import it.unipi.di.acube.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;

import java.io.Serializable;
import java.util.*;
import java.util.concurrent.*;

public class TuneModelLibSvm {
	private static final int THREADS_NUM = 4;

	public enum OptimizaionProfiles {
		MAXIMIZE_TN, MAXIMIZE_MICRO_F1, MAXIMIZE_MACRO_F1
	}

	public static double computeExpParameter(double max, double min, double kappa, int iteration, int steps) {
		if (iteration < 0)
			return min;
		double exp = max == min ? 1 : Math.log((max - min) / kappa) / Math.log(steps);

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
		WATRelatednessComputer.setCache("relatedness.cache");
		WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache", "redirect.cache");
		FreebaseApi freebApi = new FreebaseApi(freebKey, freebCache);
		EntityToVect.initialize();

		OptDataset opt = OptDataset.SMAPH_DATASET;
		WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");

		SmaphAnnotator bingAnnotator = GenerateTrainingAndTest
		        .getDefaultBingAnnotatorGatherer(wikiApi, bingKey, true, true, true);

		
		double maxAnchorSegmentED = 0.7; 
		/*Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> modelAndStats = trainIterativeEF(bingAnnotator, opt,
		        wikiToFreebase, freebApi, maxAnchorSegmentED, OptimizaionProfiles.MAXIMIZE_MACRO_F1, -1.0, wikiApi);*/
		Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> modelAndStats = trainIterativeAR(bingAnnotator, opt,
				wikiToFreebase, freebApi, maxAnchorSegmentED, OptimizaionProfiles.MAXIMIZE_MACRO_F1, -1.0, wikiApi);

		for (ModelConfigurationResult res : modelAndStats.first)
			System.out.println(res.getReadable());

		System.gc();

		System.out.println("Best model:" + modelAndStats.second.getReadable());

		System.out.println("Flushing Bing API...");

		BingInterface.flush();
		wikiApi.flush();
		WATAnnotator.flush();
		WATRelatednessComputer.flush();
	}

	private static Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> trainIterativeEF(
			SmaphAnnotator annotator, OptDataset optDs, WikipediaToFreebase wikiToFreebase, FreebaseApi freebApi, double maxAnchorSegmentED,
			OptimizaionProfiles optProfile, double optProfileThreshold,
			WikipediaApiInterface wikiApi) throws Exception {

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
		double bestGamma = 1.0 / new EntityFeaturePack().getFeatureCount();
		double bestC=1;
		double fineCMin = bestC * 0.1;
		double fineCMax = bestC * 10.0;
		double fineGammaMin = bestGamma * 0.1;
		double fineGammaMax = bestGamma * 10.0;

		ExampleGatherer<Tag, HashSet<Tag>> trainGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
		ExampleGatherer<Tag, HashSet<Tag>> develGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();

		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
				annotator, develGatherer,
				develGatherer, null, null, null, null, null, null, wikiApi, wikiToFreebase, freebApi, optDs, maxAnchorSegmentED);

		int[] featuresToInclude = SmaphUtils.getAllFtrVect(new EntityFeaturePack().getFeatureCount());

		// broad tune weights (all ftr)
		{
			new WeightSelector(
					broadwPosMin, broadwPosMax, broadkPos, broadwNegMin,
					broadwNegMax, 1.0, bestGamma, bestC, broadSteps,
					featuresToInclude, trainGatherer, develGatherer,
					globalScoreboard, wikiApi).run();
			System.err.println("Done broad weighting.");

			ModelConfigurationResult bestWeights = ModelConfigurationResult
					.findBest(globalScoreboard, optProfile,
							optProfileThreshold);

			bestwPos = bestWeights.getWPos();
			bestwNeg = bestWeights.getWNeg();
		}
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

				ParameterTesterEF pt = new ParameterTesterEF(bestwPos, bestwNeg, featuresToInclude, trainGatherer, develGatherer, bestGamma, bestC, wikiApi);
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
				new WeightSelector(finewPosMin, finewPosMax, -1,
						finewNegMin, finewNegMax, -1, bestGamma, bestC, fineSteps,
						bestFeatures, trainGatherer,
						develGatherer, scoreboardWeightsTuning, wikiApi).run();
				
				ModelConfigurationResult bestWeightsRes = ModelConfigurationResult
						.findBest(scoreboardWeightsTuning, optProfile,
								optProfileThreshold);

				bestwPos = bestWeightsRes.getWPos();
				bestwNeg = bestWeightsRes.getWNeg();
				
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
				ParameterTesterEF pt = new ParameterTesterEF(bestwPos, bestwNeg, featuresToInclude, trainGatherer, develGatherer, bestGamma, bestC, wikiApi);
				new GammaCSelector<Tag, HashSet<Tag>>(fineCMin, fineCMax, bestC, (fineCMax-fineCMin)/fineStepsCGamma/10.0,
						fineGammaMin, fineGammaMax, bestGamma, (fineGammaMax-fineGammaMin)/fineStepsCGamma/10.0, fineStepsCGamma, pt, 
						scoreboardGammaCTuning, wikiApi).run();

				ModelConfigurationResult bestCGammaResult = ModelConfigurationResult.findBest(scoreboardGammaCTuning, optProfile,
				        optProfileThreshold);

				bestC = bestCGammaResult.getC();
				bestGamma = bestCGammaResult.getGamma();

				fineCMin = bestC * 0.5;
				fineCMax = bestC * 2.0;
				fineGammaMin = bestGamma * 0.5;
				fineGammaMax = bestGamma * 2.0;

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

	private static Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> trainIterativeAR(
			SmaphAnnotator annotator, OptDataset optDs, WikipediaToFreebase wikiToFreebase, FreebaseApi freebApi, double maxAnchorSegmentED,
			OptimizaionProfiles optProfile, double optProfileThreshold,
			WikipediaApiInterface wikiApi) throws Exception {
		Vector<ModelConfigurationResult> globalScoreboard = new Vector<>();
		int stepsCGamma = 6;
		int iterations = 5;
		double bestGamma = 1.0 / new AdvancedAnnotationFeaturePack().getFeatureCount();
		double bestC = 1;
		double cMin = bestC * 0.7;
		double cMax = bestC * 1.8;
		double gammaMin = bestGamma * 0.7;
		double gammaMax = bestGamma * 1.8;
		int thrSteps = 30;

		ExampleGatherer<Annotation, HashSet<Annotation>> trainGatherer = new ExampleGatherer<>();
		ExampleGatherer<Annotation, HashSet<Annotation>> develGatherer = new ExampleGatherer<>();

		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
				annotator, null, null, null, null, trainGatherer,
				develGatherer, null, null, wikiApi, wikiToFreebase, freebApi, optDs, maxAnchorSegmentED);

		int[] featuresToInclude = SmaphUtils.getAllFtrVect(new AdvancedAnnotationFeaturePack().getFeatureCount());

		ModelConfigurationResult bestResult = null;

		for (int iteration = 0; iteration < iterations; iteration++) {
			// Broad-tune C and Gamma
			{
				Vector<ModelConfigurationResult> scoreboardGammaCTuning = new Vector<>();
				ParameterTesterAR pt = new ParameterTesterAR(featuresToInclude, trainGatherer, develGatherer, bestGamma, bestC, optProfile, optProfileThreshold, thrSteps, wikiApi);

				new GammaCSelector<Annotation, HashSet<Annotation>>(cMin, cMax, bestC, (cMax-cMin)/stepsCGamma/10.0, gammaMin,
						gammaMax, bestGamma, (gammaMax-gammaMin)/stepsCGamma/10.0, stepsCGamma, pt, scoreboardGammaCTuning, wikiApi).run();
				ModelConfigurationResult bestCGammaResult = ModelConfigurationResult.findBest(scoreboardGammaCTuning, optProfile,
						optProfileThreshold);

				bestC = bestCGammaResult.getC();
				bestGamma = bestCGammaResult.getGamma();

				globalScoreboard.addAll(scoreboardGammaCTuning);
				System.err.printf("Done gamma-C tuning (iteration %d).%n", iteration);
			}

			// Fine-tune C and Gamma
			{
				double fineCMin = bestC * 0.85;
				double fineGammaMin = bestGamma * 0.85;
				double fineCMax = bestC * 1.20;
				double fineGammaMax = bestGamma * 1.20;
				int fineStepsCGamma = 10;
				Vector<ModelConfigurationResult> scoreboardGammaCTuning = new Vector<>();
				ParameterTesterAR pt = new ParameterTesterAR(featuresToInclude, trainGatherer, develGatherer, bestGamma, bestC, optProfile, optProfileThreshold, thrSteps, wikiApi);

				new GammaCSelector<Annotation, HashSet<Annotation>>(fineCMin, fineCMax, bestC, (fineCMax-fineCMin)/fineStepsCGamma/5.0, fineGammaMin,
						fineGammaMax, bestGamma, (fineGammaMax-fineGammaMin)/fineStepsCGamma/5.0, fineStepsCGamma, pt, scoreboardGammaCTuning, wikiApi).run();
				ModelConfigurationResult bestCGammaResult = ModelConfigurationResult.findBest(scoreboardGammaCTuning, optProfile,
						optProfileThreshold);

				bestC = bestCGammaResult.getC();
				bestGamma = bestCGammaResult.getGamma();

				cMin = bestC * 0.7;
				cMax = bestC * 1.5;
				gammaMin = bestGamma * 0.7;
				gammaMax = bestGamma * 1.5;

				globalScoreboard.addAll(scoreboardGammaCTuning);
				System.err.printf("Done fine gamma-C tuning (iteration %d).%n", iteration);
			}

			// Do feature selection
			{
				Vector<ModelConfigurationResult> scoreboardFtrSelection = new Vector<>();

				ParameterTesterAR pt = new ParameterTesterAR(featuresToInclude, trainGatherer, develGatherer, bestGamma, bestC, optProfile, optProfileThreshold, thrSteps, wikiApi);
				new AblationFeatureSelector<Annotation, HashSet<Annotation>>(pt, optProfile, optProfileThreshold, scoreboardFtrSelection).run();

				ModelConfigurationResult bestFtrResult = ModelConfigurationResult.findBest(scoreboardFtrSelection, optProfile,
				        optProfileThreshold);
				featuresToInclude = bestFtrResult.getFeatures();
				
				globalScoreboard.addAll(scoreboardFtrSelection);
				System.err.printf("Done feature selection (iteration %d).%n", iteration);
			}

			//Fine-tune threshold
			{
				int finalThrSteps = 100;
				globalScoreboard.add(new ParameterTesterAR(featuresToInclude, trainGatherer, develGatherer, bestGamma, bestC, optProfile, optProfileThreshold, finalThrSteps, wikiApi).call());
				System.err.printf("Done fine threshold selection (iteration %d).%n", iteration);
			}

			ModelConfigurationResult newBest = ModelConfigurationResult
					.findBest(globalScoreboard, optProfile, optProfileThreshold);
			if (bestResult != null
					&& newBest.equalResult(bestResult, optProfile,
							optProfileThreshold)) {
				System.err.printf("Not improving, stopping on iteration %d.%n", iteration);
				break;
			}
			bestResult = newBest;
		}
		return new Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult>(
				globalScoreboard, ModelConfigurationResult.findBest(
						globalScoreboard, optProfile, optProfileThreshold));
	}

	static class GammaCSelector <E extends Serializable, G extends Serializable> implements Runnable {
		private double gammaMin, gammaMax, cMin, cMax;
		private double kappaC, kappaGamma;
		private double origC, origGamma;
		private int steps;
		private ParameterTester<E, G> pt;
		private Vector<ModelConfigurationResult> scoreBoard;

		public GammaCSelector(double cMin, double cMax, double origC, double kappaC, double gammaMin, double gammaMax, double origGamma, double kappaGamma, int steps, ParameterTester<E, G> pt, Vector<ModelConfigurationResult> scoreboardGammaCTuning, WikipediaApiInterface wikiApi) {
			if (kappaC == -1)
				kappaC = (cMax - cMin) / steps;
			if (kappaGamma == -1)
				kappaGamma = (gammaMax - gammaMin) / steps;

			if (!(kappaC > 0 && (cMax - cMin == 0 || kappaC <= cMax - cMin)))
				throw new IllegalArgumentException(String.format(
						"k must be between 0.0 and %f. Got %f", cMax
						- cMin, kappaC));
			if (!(kappaGamma > 0 && (gammaMax - gammaMin == 0 || kappaGamma <= gammaMax - gammaMin)))
				throw new IllegalArgumentException(String.format(
						"k must be between 0.0 and %f. Got %f", gammaMax
						- gammaMin, kappaGamma));
			this.gammaMin = gammaMin;
			this.gammaMax = gammaMax;
			this.cMin = cMin;
			this.cMax = cMax;
			this.kappaGamma = kappaGamma;
			this.kappaC = kappaC;
			this.pt = pt;
			this.steps = steps;
			this.scoreBoard = scoreboardGammaCTuning;
			this.origC = origC;
			this.origGamma = origGamma;
		}
		@Override
		public void run() {
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
					futures.add(execServ.submit(
							pt.cloneWithGammaC(gamma,c)));
				}
			if (origGamma != -1)
				for (int cI = 0; cI<steps; cI++){
					c = TuneModelLibSvm.computeExpParameter(cMax, cMin, kappaC, cI,
							steps);
					futures.add(execServ.submit(
							pt.cloneWithGammaC(origGamma,c)));
				}
			if (origC != -1)
				for (int gammaI = 0; gammaI<steps ; gammaI++){
					gamma = TuneModelLibSvm.computeExpParameter(gammaMax, gammaMin,
							kappaGamma, gammaI, steps);
					futures.add(execServ.submit(
							pt.cloneWithGammaC(gamma,origC)));
				}
			if (origGamma != -1 && origC != -1)
				futures.add(execServ.submit(
						pt.cloneWithGammaC(origGamma,origC)));
			for (Future<ModelConfigurationResult> future : futures)
				try {
					ModelConfigurationResult res = future.get();
					scoreBoard.add(res);
				} catch (InterruptedException | ExecutionException | Error e) {
					throw new RuntimeException(e);
				}
			execServ.shutdown();
		}
	}

	static class WeightSelector implements Runnable {
		private double wPosMin, wPosMax, wNegMin, wNegMax, gamma, C;
		private ExampleGatherer<Tag, HashSet<Tag>> trainGatherer;
		private ExampleGatherer<Tag, HashSet<Tag>> testGatherer;
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
			this.scoreboard = scoreboard;
			this.C = C;
			this.gamma = gamma;
			this.steps = steps;
			this.wikiApi = wikiApi;
		}

		@Override
		public void run() {
			ExecutorService execServ = Executors
					.newFixedThreadPool(THREADS_NUM);
			List<Future<ModelConfigurationResult>> futures = new Vector<>();

			double wPos, wNeg;
			for (int posI = 0; (wPos = TuneModelLibSvm.computeExpParameter(wPosMax, wPosMin,
					kappaPos, posI, steps)) <= wPosMax; posI++)
				for (int negI = 0; (wNeg = TuneModelLibSvm.computeExpParameter(wNegMax, wNegMin,
						kappaNeg, negI, steps)) <= wNegMax; negI++)
					futures.add(execServ.submit(new ParameterTester.ParameterTesterEF(wPos, wNeg, features, trainGatherer,
							testGatherer, gamma, C, wikiApi)));

			for (Future<ModelConfigurationResult> future : futures)
				try {
					ModelConfigurationResult res = future.get();
					scoreboard.addElement(res);
				} catch (InterruptedException | ExecutionException | Error e) {
					throw new RuntimeException(e);
				}
			execServ.shutdown();
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
				ExecutorService execServ = Executors.newFixedThreadPool(THREADS_NUM);
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
						scoreboard.add(res);
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
