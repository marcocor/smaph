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
import it.unipi.di.acube.batframework.systemPlugins.CachedWATAnnotator;
import it.unipi.di.acube.batframework.utils.FreebaseApi;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphAnnotatorBuilder;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.learn.GenerateTrainingAndTest.OptDataset;
import it.unipi.di.acube.smaph.learn.ParameterTester.ParameterTesterAR;
import it.unipi.di.acube.smaph.learn.ParameterTester.ParameterTesterEF;
import it.unipi.di.acube.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.models.entityfilters.LibSvmEntityFilter;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.LibSvmAnnotationRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;

import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.*;
import java.util.concurrent.*;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TuneModelLibSvm {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static int THREADS_NUM = Runtime.getRuntime().availableProcessors();

	public enum OptimizaionProfiles {
		MAXIMIZE_TN, MAXIMIZE_MICRO_F1, MAXIMIZE_MACRO_F1
	}

	private static double computeExpParameter(double max, double min, double kappa, int iteration, int steps) {
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
		CommandLineParser parser = new GnuParser();
		Options options = new Options();

		options.addOption(OptionBuilder.withLongOpt("opt-annotation-regressor").withDescription("Optimize single-annotation regressor.").create("a"));
		options.addOption(OptionBuilder.withLongOpt("opt-entity-filter").withDescription("Optimize single-entity filter.").create("e"));
		options.addOption(OptionBuilder.withLongOpt("ftr-sel-method").hasArg().withArgName("FTR_SEL_METHOD").withDescription("Feature selection method. Can be either `ablation' (default), `oneshot' or `increment'").create("m"));
		options.addOption(OptionBuilder.withLongOpt("restrict-ftr-set").hasArgs().withArgName("RESTRICT_FTR_SET").withDescription("Feature restriction set (initial features for `ablation' and `oneshot' methods, pool of selectable features for `increment'), e.g. `1,3,6,7,8'. Multiple sets can be defined (separated by a space). Defaults to all features.").create("f"));
		options.addOption(OptionBuilder.withLongOpt("initial-ftr-set").hasArgs().withArgName("INIT_FTR_SET").withDescription("Initial feature set. Considered for `increment' method only. Defaults to no features.").create("i"));
		options.addOption(OptionBuilder.withLongOpt("threads").hasArg().withArgName("N_THREADS").withDescription("Number of threads to launch. Default: number of cores.").create("t"));

		CommandLine line = parser.parse(options, args);

		if (line.hasOption("threads"))
			THREADS_NUM = Integer.parseInt(line.getOptionValue("threads"));

		Locale.setDefault(Locale.US);
		SmaphConfig.setConfigFile("smaph-config.xml");
		String bingKey = SmaphConfig.getDefaultBingKey();
		String freebKey = SmaphConfig.getDefaultFreebaseKey();
		String freebCache = SmaphConfig.getDefaultFreebaseKey();
		BingInterface.setCache(SmaphConfig.getDefaultBingCache());
		CachedWATAnnotator.setCache("wikisense.cache");
		WATRelatednessComputer.setCache("relatedness.cache");
		WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache", "redirect.cache");
		FreebaseApi freebApi = new FreebaseApi(freebKey, freebCache);

		OptDataset opt = OptDataset.SMAPH_DATASET;
		WikipediaToFreebase wikiToFreebase = new WikipediaToFreebase("mapdb");

		SmaphAnnotator bingAnnotator = SmaphAnnotatorBuilder
				.getDefaultBingAnnotatorGatherer(wikiApi, bingKey, true, true, true);

		double maxAnchorSegmentED = 0.7;

		String ftrSelMethod = line.getOptionValue("ftr-sel-method", "ablation");
		if (!ftrSelMethod.equals("ablation") && !ftrSelMethod.equals("increment") && !ftrSelMethod.equals("oneshot"))
			throw new IllegalArgumentException("ftr-sel-method must be either `ablation', `oneshot' or `increment'.");

		int[][] ftrRestriction = null;
		if (line.hasOption("restrict-ftr-set")){
			String[] initFtrs = line.getOptionValues("restrict-ftr-set");
			ftrRestriction = new int[initFtrs.length][];
			for (int i = 0; i < initFtrs.length; i++)
				ftrRestriction[i] = SmaphUtils.strToFeatureVector(initFtrs[i]);
		}

		int[] initialFtrSet = line.hasOption("initial-ftr-set") ? SmaphUtils.strToFeatureVector(line.getOptionValue("initial-ftr-set")) : null;

		if (line.hasOption("opt-entity-filter")) {
			Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> modelAndStats = trainIterativeEF(bingAnnotator,
					opt, wikiToFreebase, freebApi, maxAnchorSegmentED, OptimizaionProfiles.MAXIMIZE_MACRO_F1, -1.0,
					ftrSelMethod, ftrRestriction, initialFtrSet, wikiApi);
			System.gc();
			for (ModelConfigurationResult res : modelAndStats.first)
				LOG.info(res.getReadable());
			LOG.info("Entity Filter - Overall best model:" + modelAndStats.second.getReadable());
		}

		if (line.hasOption("opt-annotation-regressor")) {
			Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> modelAndStats = trainIterativeAR(bingAnnotator, opt,
					wikiToFreebase, freebApi, maxAnchorSegmentED, OptimizaionProfiles.MAXIMIZE_MACRO_F1, -1.0, ftrSelMethod,
					ftrRestriction, initialFtrSet, wikiApi);
			System.gc();
			for (ModelConfigurationResult res : modelAndStats.first)
				LOG.info(res.getReadable());
			LOG.info("Annotation Regressor - Overall best model:" + modelAndStats.second.getReadable());
		}

		LOG.info("Flushing everything...");
		BingInterface.flush();
		wikiApi.flush();
		CachedWATAnnotator.flush();
		WATRelatednessComputer.flush();
	}

	private static Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> trainIterativeEF(
			SmaphAnnotator annotator, OptDataset optDs, WikipediaToFreebase wikiToFreebase, FreebaseApi freebApi, double maxAnchorSegmentED,
			OptimizaionProfiles optProfile, double optProfileThreshold, String ftrSelMethod, int[][] restrictFeatures, int[] initialFeatures,
			WikipediaApiInterface wikiApi) throws Exception {

		Vector<ModelConfigurationResult> globalScoreboard = new Vector<>();
		int broadStepsWeight = 10;
		int fineStepsWeight = 10;
		int fineStepsCGamma = 10;
		int maxIterations = 10;

		ExampleGatherer<Tag, HashSet<Tag>> trainGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
		ExampleGatherer<Tag, HashSet<Tag>> develGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();

		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(annotator, trainGatherer, develGatherer, null, null, null, null,
				null, null, wikiApi, wikiToFreebase, freebApi, optDs, maxAnchorSegmentED);

		int[] allFtrs = SmaphUtils.getAllFtrVect(new EntityFeaturePack().getFeatureCount());

		if (restrictFeatures == null)
			restrictFeatures = new int[][] { allFtrs };

		for (int[] restrictFeaturesI : restrictFeatures) {
			Vector<ModelConfigurationResult> restrictedFeaturesScoreboard = new Vector<>();

			int[] bestFeatures = ftrSelMethod.equals("increment") ? initialFeatures : restrictFeaturesI;

			double bestGamma = 1.0 / bestFeatures.length;
			double bestC = 1.0;
			double bestwPos;
			double bestwNeg;
			
			double broadwPosMin = 0.5;
			double broadwPosMax = 5.0;
			double broadwNegMin = 1.0;
			double broadwNegMax = 1.0;
			double broadkPos = 0.2;

			// broad tune weights (all ftr)
			{
				int[] broadWeightFtrs = bestFeatures == null ? allFtrs : bestFeatures;

				new WeightSelector(broadwPosMin, broadwPosMax, broadkPos, broadwNegMin, broadwNegMax, 1.0, bestGamma, bestC,
						broadStepsWeight, broadWeightFtrs, trainGatherer, develGatherer, restrictedFeaturesScoreboard, wikiApi).run();

				ModelConfigurationResult bestWeights = ModelConfigurationResult
						.findBest(restrictedFeaturesScoreboard, optProfile,
								optProfileThreshold);

				bestwPos = bestWeights.getWPos();
				bestwNeg = bestWeights.getWNeg();
				LOG.info("Done broad weighting. Best weight +/-: {}/{}", bestwPos, bestwNeg);
			}

			ModelConfigurationResult bestResult = ModelConfigurationResult
					.findBest(restrictedFeaturesScoreboard, optProfile, optProfileThreshold);

			for (int iteration = 0; iteration < maxIterations; iteration++) {
				// Fine-tune weights
				{
					double finewPosMin = bestwPos * 0.7;
					double finewPosMax = bestwPos * 1.5;
					double finewNegMin = bestwNeg * 0.7;
					double finewNegMax = bestwNeg * 1.5;

					Vector<ModelConfigurationResult> scoreboardWeightsTuning = new Vector<>();
					new WeightSelector(finewPosMin, finewPosMax, -1,
							finewNegMin, finewNegMax, -1, bestGamma, bestC, fineStepsWeight,
							bestFeatures, trainGatherer,
							develGatherer, scoreboardWeightsTuning, wikiApi).run();

					ModelConfigurationResult bestWeightsRes = ModelConfigurationResult
							.findBest(scoreboardWeightsTuning, optProfile,
									optProfileThreshold);

					bestwPos = bestWeightsRes.getWPos();
					bestwNeg = bestWeightsRes.getWNeg();

					restrictedFeaturesScoreboard.addAll(scoreboardWeightsTuning);
					LOG.info("Done fine weights tuning (iteration {}). Best weigth +/-: {}/{}", iteration, bestwPos, bestwNeg);
				}

				// Fine-tune C and Gamma
				{
					double fineCMin = bestC * 0.7;
					double fineCMax = bestC * 1.8;
					double fineGammaMin = bestGamma * 0.7;
					double fineGammaMax = bestGamma * 1.8;

					Vector<ModelConfigurationResult> scoreboardGammaCTuning = new Vector<>();
					ParameterTesterEF pt = new ParameterTesterEF(bestwPos, bestwNeg, bestFeatures, trainGatherer, develGatherer, bestGamma, bestC, wikiApi);
					new GammaCSelector<Tag, HashSet<Tag>>(fineCMin, fineCMax, bestC, (fineCMax-fineCMin)/fineStepsCGamma/10.0,
							fineGammaMin, fineGammaMax, bestGamma, (fineGammaMax-fineGammaMin)/fineStepsCGamma/10.0, fineStepsCGamma, pt, 
							scoreboardGammaCTuning, wikiApi).run();

					ModelConfigurationResult bestCGammaResult = ModelConfigurationResult.findBest(scoreboardGammaCTuning, optProfile,
							optProfileThreshold);

					bestC = bestCGammaResult.getC();
					bestGamma = bestCGammaResult.getGamma();

					restrictedFeaturesScoreboard.addAll(scoreboardGammaCTuning);
					LOG.info("Done gamma-C tuning (iteration {}). Best gamma/C: {}/{}", iteration, bestGamma, bestC);
				}

				// Do feature selection
				if (!ftrSelMethod.equals("oneshot")) {
					Vector<ModelConfigurationResult> scoreboardFtrSelection = new Vector<>();

					ParameterTesterEF pt = new ParameterTesterEF(bestwPos, bestwNeg, bestFeatures, trainGatherer, develGatherer, bestGamma, bestC, wikiApi);
					if (ftrSelMethod.equals("ablation"))
						new AblationFeatureSelector<Tag, HashSet<Tag>>(pt, optProfile, optProfileThreshold, scoreboardFtrSelection).run();

					else if (ftrSelMethod.equals("increment"))
						new IncrementalFeatureSelector<Tag, HashSet<Tag>>(pt, bestFeatures, restrictFeaturesI, optProfile, optProfileThreshold, scoreboardFtrSelection).run();

					bestFeatures = ModelConfigurationResult
							.findBest(scoreboardFtrSelection, optProfile,
									optProfileThreshold).getFeatures();
					restrictedFeaturesScoreboard.addAll(scoreboardFtrSelection);
					LOG.info("Done feature selection (iteration {}).", iteration);
				}

				ModelConfigurationResult newBest = ModelConfigurationResult
						.findBest(restrictedFeaturesScoreboard, optProfile, optProfileThreshold);
				if (newBest.equalResult(bestResult, optProfile, optProfileThreshold)) {
					LOG.info("Not improving, stopping on iteration {}.",
							iteration);
					break;
				}
				bestResult = newBest;
			}
			LOG.info("Best result for restricted features iteration: {}", bestResult.getReadable());
			globalScoreboard.addAll(restrictedFeaturesScoreboard);
		}

		ModelConfigurationResult globalBest = ModelConfigurationResult
		        .findBest(globalScoreboard, optProfile, optProfileThreshold);
		
		ZScoreFeatureNormalizer scaleFn = new ZScoreFeatureNormalizer(trainGatherer);
		LibSvmEntityFilter bestEf = ParameterTesterEF.getFilter(trainGatherer, scaleFn, globalBest.getFeatures(), globalBest.getWPos(), globalBest.getWNeg(), globalBest.getGamma(), globalBest.getC());
		scaleFn.dump("models/best_ef.zscore");
		bestEf.toFile("models/best_ef.model");
		
		return new Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult>(globalScoreboard, globalBest);
	}

	private static Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> trainIterativeAR(
			SmaphAnnotator annotator, OptDataset optDs, WikipediaToFreebase wikiToFreebase, FreebaseApi freebApi, double maxAnchorSegmentED,
			OptimizaionProfiles optProfile, double optProfileThreshold, String ftrSelMethod, int[][] restrictFeatures, int[] initFeatures,
			WikipediaApiInterface wikiApi) throws Exception {
		Vector<ModelConfigurationResult> globalScoreboard = new Vector<>();
		int stepsCGamma = 6;
		int fineStepsCGamma = 6;
		int maxIterations = 10;
		int fineTuneIterations = 3;
		int thrSteps = 30;

		ExampleGatherer<Annotation, HashSet<Annotation>> trainGatherer = new ExampleGatherer<>();
		ExampleGatherer<Annotation, HashSet<Annotation>> develGatherer = new ExampleGatherer<>();

		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(
				annotator, null, null, null, null, trainGatherer,
				develGatherer, null, null, wikiApi, wikiToFreebase, freebApi, optDs, maxAnchorSegmentED);

		int[] allFtrs = SmaphUtils.getAllFtrVect(new AdvancedAnnotationFeaturePack().getFeatureCount());

		if (restrictFeatures == null)
			restrictFeatures = new int[][] { allFtrs };

		for (int[] restrictedFeaturesI: restrictFeatures){
			Vector<ModelConfigurationResult> restrictedFeaturesScoreboard = new Vector<>();
			ModelConfigurationResult bestResult = null;

			int[] bestFeatures = ftrSelMethod.equals("increment") ? initFeatures : restrictedFeaturesI;

			double bestC = 1;
			double bestGamma = 1.0 / (bestFeatures == null ? allFtrs : bestFeatures).length;
			for (int iteration = 0; iteration < maxIterations; iteration++) {
				int[] gammaCFeatures = bestFeatures == null ? allFtrs : bestFeatures;

				// Broad-tune C and Gamma
				ModelConfigurationResult bestCGammaResult = null;
				{
					double cMin = bestC * 0.7;
					double cMax = bestC * 1.8;
					double gammaMin = bestGamma * 0.7;
					double gammaMax = bestGamma * 1.8;

					Vector<ModelConfigurationResult> scoreboardGammaCTuning = new Vector<>();
					ParameterTesterAR pt = new ParameterTesterAR(gammaCFeatures, trainGatherer, develGatherer, bestGamma, bestC, optProfile, optProfileThreshold, thrSteps, wikiApi);

					new GammaCSelector<Annotation, HashSet<Annotation>>(cMin, cMax, bestC, (cMax-cMin)/stepsCGamma/10.0, gammaMin,
							gammaMax, bestGamma, (gammaMax-gammaMin)/stepsCGamma/10.0, stepsCGamma, pt, scoreboardGammaCTuning, wikiApi).run();
					bestCGammaResult = ModelConfigurationResult.findBest(scoreboardGammaCTuning, optProfile,
							optProfileThreshold);

					bestC = bestCGammaResult.getC();
					bestGamma = bestCGammaResult.getGamma();
					if (!ftrSelMethod.equals("increment"))
						restrictedFeaturesScoreboard.addAll(scoreboardGammaCTuning);
					LOG.error("Done broad gamma-C tuning (iteration {}) best gamma/C: {}/{}", iteration, bestGamma, bestC);
				}

				// Fine-tune C and Gamma
				Vector<ModelConfigurationResult> scoreboardGammaCTuning = new Vector<>();
				for (int i = 0; i < fineTuneIterations; i++) {
					double fineCMin = bestC * 0.85;
					double fineGammaMin = bestGamma * 0.85;
					double fineCMax = bestC * 1.20;
					double fineGammaMax = bestGamma * 1.20;
					ParameterTesterAR pt = new ParameterTesterAR(gammaCFeatures, trainGatherer, develGatherer, bestGamma, bestC, optProfile, optProfileThreshold, thrSteps, wikiApi);

					new GammaCSelector<Annotation, HashSet<Annotation>>(fineCMin, fineCMax, bestC, (fineCMax-fineCMin)/fineStepsCGamma/5.0, fineGammaMin,
							fineGammaMax, bestGamma, (fineGammaMax-fineGammaMin)/fineStepsCGamma/5.0, fineStepsCGamma, pt, scoreboardGammaCTuning, wikiApi).run();
					ModelConfigurationResult newBestCGammaResult = ModelConfigurationResult.findBest(scoreboardGammaCTuning, optProfile,
							optProfileThreshold);

					if (bestCGammaResult.worseThan(newBestCGammaResult, optProfile, optProfileThreshold)) {
						bestCGammaResult = newBestCGammaResult;
						bestC = bestCGammaResult.getC();
						bestGamma = bestCGammaResult.getGamma();

						LOG.info(
								"Done fine gamma-C tuning (outer iteration {}, inner iteration {}) best gamma/C: {}/{}",
								iteration, i, bestGamma, bestC);
					} else {
						LOG.info(
								"No advances in inner gamma-C iteration (outer iteration {}, inner iteration {}) best gamma/C: {}/{}",
								iteration, i, bestGamma, bestC);
						break;
					}
				}
				if (!ftrSelMethod.equals("increment"))
					restrictedFeaturesScoreboard.addAll(scoreboardGammaCTuning);	

				// Do feature selection
				if (!ftrSelMethod.equals("oneshot")) {
					Vector<ModelConfigurationResult> scoreboardFtrSelection = new Vector<>();

					ParameterTesterAR pt = new ParameterTesterAR(bestFeatures, trainGatherer, develGatherer, bestGamma, bestC, optProfile, optProfileThreshold, thrSteps, wikiApi);

					if (ftrSelMethod.equals("ablation"))
						new AblationFeatureSelector<Annotation, HashSet<Annotation>>(pt, optProfile, optProfileThreshold,
								scoreboardFtrSelection).run();
					else if (ftrSelMethod.equals("increment"))
						new IncrementalFeatureSelector<Annotation, HashSet<Annotation>>(pt, bestFeatures, restrictedFeaturesI,
								optProfile, optProfileThreshold, scoreboardFtrSelection).run();

					bestFeatures = ModelConfigurationResult.findBest(scoreboardFtrSelection, optProfile, optProfileThreshold)
							.getFeatures();

					restrictedFeaturesScoreboard.addAll(scoreboardFtrSelection);
					LOG.info("Done feature selection (iteration {}).", iteration);
				}

				//Fine-tune threshold
				{
					int finalThrSteps = 100;
					restrictedFeaturesScoreboard.add(new ParameterTesterAR(bestFeatures, trainGatherer, develGatherer, bestGamma, bestC, optProfile, optProfileThreshold, finalThrSteps, wikiApi).call());
					LOG.info("Done fine threshold selection (iteration {}).", iteration);
				}

				ModelConfigurationResult newBest = ModelConfigurationResult
						.findBest(restrictedFeaturesScoreboard, optProfile, optProfileThreshold);
				if (bestResult != null
						&& newBest.equalResult(bestResult, optProfile,
								optProfileThreshold)) {
					LOG.info("Not improving, stopping on iteration {}/", iteration);
					break;
				}
				bestResult = newBest;
			}
			globalScoreboard.addAll(restrictedFeaturesScoreboard);
			LOG.info("Best result for restricted features iteration: {}", bestResult.getReadable());
		}
		
		ModelConfigurationResult globalBest = ModelConfigurationResult.findBest(
						globalScoreboard, optProfile, optProfileThreshold);
		
		ZScoreFeatureNormalizer scaleFn = new ZScoreFeatureNormalizer(trainGatherer);
		LibSvmAnnotationRegressor bestAR = ParameterTesterAR.getRegressor(trainGatherer, scaleFn, globalBest.getFeatures(), globalBest.getGamma(), globalBest.getC(), globalBest.getThreshold());
		scaleFn.dump("models/best_ar.zscore");
		bestAR.toFile("models/best_ar.model");
		
		return new Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult>(
				globalScoreboard, globalBest);
	}

	private static class GammaCSelector <E extends Serializable, G extends Serializable> implements Runnable {
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

			/*for (int cI = 0; cI<steps; cI++)
				for (int gammaI = 0; gammaI<steps ; gammaI++){
					double c = TuneModelLibSvm.computeExpParameter(cMax, cMin, kappaC, cI, steps);
					double gamma = TuneModelLibSvm.computeExpParameter(gammaMax, gammaMin, kappaGamma, gammaI, steps);
					futures.add(execServ.submit(pt.cloneWithGammaC(gamma, c)));
				}*/
			for (int cI = 0; cI < steps; cI++) {
				double c = TuneModelLibSvm.computeExpParameter(cMax, cMin, kappaC, cI, steps);
				futures.add(execServ.submit(pt.cloneWithGammaC(origGamma, c)));
			}
			for (int gammaI = 0; gammaI < steps; gammaI++) {
				double gamma = TuneModelLibSvm.computeExpParameter(gammaMax, gammaMin, kappaGamma, gammaI, steps);
				futures.add(execServ.submit(pt.cloneWithGammaC(gamma, origC)));
			}
			futures.add(execServ.submit(pt.cloneWithGammaC(origGamma, origC)));
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

	private static class WeightSelector implements Runnable {
		private double wPosMin, wPosMax, wNegMin, wNegMax, gamma, C;
		private ExampleGatherer<Tag, HashSet<Tag>> trainGatherer;
		private ExampleGatherer<Tag, HashSet<Tag>> testGatherer;
		private double kappaPos, kappaNeg;
		private int[] features;
		private Vector<ModelConfigurationResult> scoreboard;
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

	private static class AblationFeatureSelector<E extends Serializable,G extends Serializable> implements Runnable {
		private double optProfileThreshold;
		private OptimizaionProfiles optProfile;
		private Vector<ModelConfigurationResult> scoreboard;
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
					int[] pickedFtrsIteration = SmaphUtils.removeFtrVect(bestBase.getFeatures(), testFtrId);
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

	private static class IncrementalFeatureSelector<E extends Serializable, G extends Serializable> implements Runnable {
		private double optProfileThreshold;
		private OptimizaionProfiles optProfile;
		private Vector<ModelConfigurationResult> scoreboard;
		private ParameterTester<E, G> pt;
		private int[] selectedFeatures, restrictFeatures;

		public IncrementalFeatureSelector(ParameterTester<E, G> pt, int[] selectedFeatures, int[] restrictFeatures, OptimizaionProfiles optProfile, double optProfileThreshold,
				Vector<ModelConfigurationResult> scoreboard) {
			this.pt = pt;
			this.scoreboard = scoreboard;
			this.optProfile = optProfile;
			this.optProfileThreshold = optProfileThreshold;
			this.restrictFeatures = restrictFeatures;
			this.selectedFeatures = selectedFeatures;
		}

		@Override
		public void run() {
			Set<Integer> alreadyInFtrs = selectedFeatures != null ? new HashSet<Integer>(Arrays.asList(ArrayUtils
					.toObject(selectedFeatures))) : new HashSet<>();

			HashSet<Integer> ftrToTry = new HashSet<>();
			for (Integer f : ArrayUtils.toObject(restrictFeatures))
				if (!alreadyInFtrs.contains(f))
					ftrToTry.add(f);

			ModelConfigurationResult bestIter = null;
			int[] bestFeatures = selectedFeatures;
			while (!ftrToTry.isEmpty()) {
				ExecutorService execServ = Executors.newFixedThreadPool(THREADS_NUM);
				List<Future<ModelConfigurationResult>> futures = new Vector<>();
				HashMap<Future<ModelConfigurationResult>, Integer> futureToFtrId = new HashMap<>();

				for (int testFtrId : SmaphUtils.sorted(ftrToTry)) {
					int[] pickedFtrsIteration = SmaphUtils
							.addFtrVect(bestFeatures, testFtrId);

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

				int bestFtrId = -1;
				for (Future<ModelConfigurationResult> future : futures)
					try {
						ModelConfigurationResult res = future.get();
						scoreboard.add(res);
						if (bestIter == null
								|| bestIter.worseThan(res, optProfile,
										optProfileThreshold)) {
							bestFtrId = futureToFtrId.get(future);
							bestIter = res;
							bestFeatures = res.getFeatures();
						}
					} catch (InterruptedException | ExecutionException | Error e) {
						throw new RuntimeException(e);
					}
				execServ.shutdown();

				if (bestFtrId == -1) {
					break;
				} else {
					ftrToTry.remove(Integer.valueOf(bestFtrId));
				}
			}
		}
	}
}
