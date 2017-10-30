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

import java.io.File;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.Vector;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.apache.commons.lang3.ArrayUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;
import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.systemPlugins.CachedWAT2Annotator;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.batframework.utils.WikipediaInterfaceWAT;
import it.unipi.di.acube.batframework.utils.WikipediaLocalInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphBuilder;
import it.unipi.di.acube.smaph.SmaphBuilder.SmaphVersion;
import it.unipi.di.acube.smaph.SmaphBuilder.Websearch;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;
import it.unipi.di.acube.smaph.learn.GenerateTrainingAndTest.OptDataset;
import it.unipi.di.acube.smaph.learn.ParameterTester.ParameterTesterAR;
import it.unipi.di.acube.smaph.learn.ParameterTester.ParameterTesterEF;
import it.unipi.di.acube.smaph.learn.ParameterTester.ParameterTesterGreedy;
import it.unipi.di.acube.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.GreedyFeaturePack;
import it.unipi.di.acube.smaph.learn.models.entityfilters.LibSvmEntityFilter;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.LibSvmAnnotationRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.GreedyLinkback;

public class TuneModelLibSvm {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static int THREADS_NUM = Runtime.getRuntime().availableProcessors();
	private static WikipediaInterface wikiApi;
	private static WikipediaToFreebase w2f;
	private static EntityToAnchors e2a;

	public enum OptimizaionProfiles {
		MAXIMIZE_TN, MAXIMIZE_MICRO_F1, MAXIMIZE_MACRO_F1
	}

	private static double computeExpParameter(double max, double min, double kappa, int iteration, int steps) {
		if (iteration < 0)
			return min;
		double exp = max == min ? 1 : Math.log((max - min) / kappa) / Math.log(steps);

		return min + kappa * Math.pow(iteration, exp);
	}

	public static int paramToIter(double weight, double max, double min, double kappa, int steps) {
		if (max == min)
			return 0;
		double exp = Math.log((max - min) / kappa) / Math.log(steps);

		return (int) Math.round(Math.pow((weight - min) / kappa, 1.0 / exp));
	}

	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new GnuParser();
		Options options = new Options();

		options.addOption(OptionBuilder.withLongOpt("opt-annotation-regressor").withDescription("Optimize single-annotation regressor.").create("a"));
		options.addOption(OptionBuilder.withLongOpt("opt-entity-filter").withDescription("Optimize single-entity filter.").create("e"));
		options.addOption(OptionBuilder.withLongOpt("opt-greedy-regressor").withDescription("Optimize greedy regressors.").create("g"));
		options.addOption(OptionBuilder.withLongOpt("ftr-sel-method").hasArg().withArgName("FTR_SEL_METHOD").withDescription("Feature selection method. Can be either `ablation' (default), `oneshot' or `increment'").create("m"));
		options.addOption(OptionBuilder.withLongOpt("restrict-ftr-set").hasArgs().withArgName("RESTRICT_FTR_SET").withDescription("Feature restriction set (initial features for `ablation' and `oneshot' methods, pool of selectable features for `increment'), e.g. `1,3,6,7,8'. Multiple sets can be defined (separated by a space). Defaults to all features.").create("f"));
		options.addOption(OptionBuilder.withLongOpt("initial-ftr-set").hasArgs().withArgName("INIT_FTR_SET").withDescription("Initial feature set. Considered for `increment' method only. Defaults to no features.").create("i"));
		options.addOption(OptionBuilder.withLongOpt("threads").hasArg().withArgName("N_THREADS").withDescription("Number of threads to launch. Default: number of cores.").create("t"));
		options.addOption(OptionBuilder.withLongOpt("websearch-piggyback").isRequired().hasArg().withArgName("WEBSEARCH").withDescription("What web search engine to piggyback on. Can be either `bing' or `google'.").create("w"));
		options.addOption(OptionBuilder.withLongOpt("topk-S1").hasArg().withDescription("Comma-separated limits K for Source 1. Only top-K results are analyzed. Applies to EF tuning only. Defaults to "+SmaphBuilder.DEFAULT_NORMALSEARCH_RESULTS).create());
		options.addOption(OptionBuilder.withLongOpt("topk-S2").hasArg().withDescription("Comma-separated limits K for Source 2. Only top-K results are analyzed. Applies to EF tuning only. Defaults to "+SmaphBuilder.DEFAULT_WIKISEARCH_RESULTS).create());
		options.addOption(OptionBuilder.withLongOpt("topk-S3").hasArg().withDescription("Comma-separated limits K for Source 3. Only top-K results are analyzed. Applies to EF tuning only. Defaults to "+SmaphBuilder.DEFAULT_ANNOTATED_SNIPPETS).create());

		CommandLine line = parser.parse(options, args);

		if (line.hasOption("threads"))
			THREADS_NUM = Integer.parseInt(line.getOptionValue("threads"));

		int[] topKS1 = new int[] { SmaphBuilder.DEFAULT_NORMALSEARCH_RESULTS };
		if (line.hasOption("topk-S1"))
			topKS1 = parseIntList(line.getOptionValue("topk-S1"));

		int[] topKS2 = new int[] { SmaphBuilder.DEFAULT_WIKISEARCH_RESULTS };
		if (line.hasOption("topk-S2"))
			topKS2 = parseIntList(line.getOptionValue("topk-S2"));

		int[] topKS3 = new int[] { SmaphBuilder.DEFAULT_ANNOTATED_SNIPPETS };
		if (line.hasOption("topk-S3"))
			topKS3 = parseIntList(line.getOptionValue("topk-S3"));

		SmaphBuilder.Websearch ws = SmaphBuilder.websearchFromString(line.getOptionValue("websearch-piggyback"));

		Locale.setDefault(Locale.US);
		SmaphConfig c = SmaphConfig.fromConfigFile("smaph-config.xml");
		SmaphBuilder.initialize(c.getWatGcubeToken());
		CachedWAT2Annotator.setCache("wat2.cache");
		WATRelatednessComputer.setGcubeToken(c.getWatGcubeToken());
		WATRelatednessComputer.setCache("relatedness_wat2.cache");
		wikiApi = new WikipediaInterfaceWAT.WikipediaInterfaceWATBuilder().gcubeToken(c.getWatGcubeToken()).cache().build();
		w2f = WikipediaToFreebase.open(c.getDefaultWikipediaToFreebaseStorage());
		e2a = EntityToAnchors.fromDB(c.getDefaultEntityToAnchorsStorage());

		OptDataset opt = OptDataset.SMAPH_DATASET;

		String ftrSelMethod = line.getOptionValue("ftr-sel-method", "ablation-rank");
		if (!ftrSelMethod.equals("ablation") && !ftrSelMethod.equals("ablation-rank") && !ftrSelMethod.equals("increment") && !ftrSelMethod.equals("oneshot"))
			throw new IllegalArgumentException("ftr-sel-method must be either `ablation', `ablation-rank', `oneshot' or `increment'.");

		int[][] ftrRestriction = null;
		if (line.hasOption("restrict-ftr-set")) {
			String[] initFtrs = line.getOptionValues("restrict-ftr-set");
			ftrRestriction = new int[initFtrs.length][];
			for (int i = 0; i < initFtrs.length; i++)
				ftrRestriction[i] = SmaphUtils.strToFeatureVector(initFtrs[i]);
		}

		int[] initialFtrSet = line.hasOption("initial-ftr-set")
		        ? SmaphUtils.strToFeatureVector(line.getOptionValue("initial-ftr-set")) : null;

		Map<String, QueryInformation> qiCache = new HashMap<>();
		List<String> readableBestModels = new Vector<>();
		if (line.hasOption("opt-entity-filter")) {
			for (int topKS1i : topKS1)
				for (int topKS2i : topKS2)
					for (int topKS3i : topKS3) {
						String label = SmaphBuilder.getSourceLabel(SmaphVersion.ENTITY_FILTER, ws, topKS1i, topKS2i, topKS3i);
						File modelFile = SmaphBuilder.getModelFile(SmaphVersion.ENTITY_FILTER, ws, topKS1i, topKS2i, topKS3i);
						File normFile = SmaphBuilder.getZscoreNormalizerFile(SmaphVersion.ENTITY_FILTER, ws, topKS1i, topKS2i, topKS3i);
						SmaphAnnotator smaphGatherer = SmaphBuilder
						        .getSmaphGatherer(wikiApi, w2f, e2a, true, topKS1i, true, topKS2i, true, topKS3i, ws, c)
						        .appendName("-" + label);
						Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> modelAndStats = trainIterativeEF(
						        smaphGatherer, opt, OptimizaionProfiles.MAXIMIZE_MACRO_F1, -1.0, ftrSelMethod, ftrRestriction,
						        initialFtrSet, modelFile, normFile, qiCache);
						System.gc();
						for (ModelConfigurationResult res : modelAndStats.first)
							LOG.info(res.getReadable());
						readableBestModels.add(smaphGatherer.getName() + modelAndStats.second.getReadable());
					}
		}

		if (line.hasOption("opt-annotation-regressor")) {
			for (int topKS1i : topKS1)
				for (int topKS2i : topKS2)
					for (int topKS3i : topKS3) {
						String label = SmaphBuilder.getSourceLabel(SmaphVersion.ANNOTATION_REGRESSOR, ws, topKS1i, topKS2i,
						        topKS3i);
						File modelFile = SmaphBuilder.getModelFile(SmaphVersion.ANNOTATION_REGRESSOR, ws, topKS1i, topKS2i,
						        topKS3i);
						File normFile = SmaphBuilder.getZscoreNormalizerFile(SmaphVersion.ANNOTATION_REGRESSOR, ws, topKS1i,
						        topKS2i, topKS3i);
						SmaphAnnotator smaphGatherer = SmaphBuilder
						        .getSmaphGatherer(wikiApi, w2f, e2a, true, topKS1i, true, topKS2i, true, topKS3i, ws, c)
						        .appendName("-" + label);
						Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> modelAndStats = trainIterativeAR(
						        smaphGatherer, opt, OptimizaionProfiles.MAXIMIZE_MACRO_F1, -1.0, ftrSelMethod,
						        ftrRestriction, initialFtrSet, modelFile, normFile, qiCache);
						System.gc();
						for (ModelConfigurationResult res : modelAndStats.first)
							LOG.info(res.getReadable());
						readableBestModels.add(smaphGatherer.getName() + modelAndStats.second.getReadable());
					}
		}
		
		if (line.hasOption("opt-greedy-regressor")) {
			for (int topKS1i : topKS1)
				for (int topKS2i : topKS2)
					for (int topKS3i : topKS3) {
						String label = SmaphBuilder.getSourceLabel(SmaphVersion.GREEDY, ws, topKS1i, topKS2i, topKS3i);
						SmaphAnnotator smaphGatherer = SmaphBuilder
						        .getSmaphGatherer(wikiApi, w2f, e2a, true, topKS1i, true, topKS2i, true, topKS3i, ws, c)
						        .appendName("-" + label);
						Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> modelAndStats = trainIterativeGreedy(
						        smaphGatherer, opt, OptimizaionProfiles.MAXIMIZE_MACRO_F1, -1.0, ftrSelMethod,
						        ftrRestriction, initialFtrSet, ws, topKS1i, topKS2i, topKS3i, qiCache);
						System.gc();
						for (ModelConfigurationResult res : modelAndStats.first)
							LOG.info(res.getReadable());
						readableBestModels.add(smaphGatherer.getName() + modelAndStats.second.getReadable());
					}
		}
		for (String readable : readableBestModels)
			LOG.info("Overall best model: {}", readable);

		LOG.info("Flushing everything...");
		wikiApi.flush();
		CachedWAT2Annotator.flush();
		WATRelatednessComputer.flush();
	}

	private static int[] parseIntList(String intList) {
		return Arrays.stream(intList.split("\\D")).mapToInt(n -> Integer.parseInt(n)).toArray();
	}

	private static Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> trainIterativeEF(SmaphAnnotator annotator,
	        OptDataset optDs, OptimizaionProfiles optProfile, double optProfileThreshold, String ftrSelMethod,
	        int[][] restrictFeatures, int[] initialFeatures, File modelFile, File normFile, Map<String, QueryInformation> qiCache)
	                throws Exception {

		Vector<ModelConfigurationResult> globalScoreboard = new Vector<>();
		int broadStepsWeight = 10;
		int fineStepsWeight = 10;
		int fineStepsCGamma = 10;
		int maxIterations = 10;

		ExampleGatherer<Tag, HashSet<Tag>> trainGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
		ExampleGatherer<Tag, HashSet<Tag>> develGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();

		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(annotator, trainGatherer, develGatherer, null, null, null, null,
				null, null, -1, null, null, null, null, wikiApi, w2f, optDs, qiCache);

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
				        broadStepsWeight, broadWeightFtrs, trainGatherer, develGatherer, restrictedFeaturesScoreboard)
				                .run();

				ModelConfigurationResult bestWeights = ModelConfigurationResult.findBest(restrictedFeaturesScoreboard, optProfile,
				        optProfileThreshold);

				bestwPos = bestWeights.getWPos();
				bestwNeg = bestWeights.getWNeg();
				LOG.info("Done broad weighting. Best weight +/-: {}/{}", bestwPos, bestwNeg);
			}

			ModelConfigurationResult bestResult = ModelConfigurationResult.findBest(restrictedFeaturesScoreboard, optProfile,
			        optProfileThreshold);

			for (int iteration = 0; iteration < maxIterations; iteration++) {
				// Fine-tune weights
				{
					double finewPosMin = bestwPos * 0.7;
					double finewPosMax = bestwPos * 1.5;
					double finewNegMin = bestwNeg * 0.7;
					double finewNegMax = bestwNeg * 1.5;

					Vector<ModelConfigurationResult> scoreboardWeightsTuning = new Vector<>();
					new WeightSelector(finewPosMin, finewPosMax, -1, finewNegMin, finewNegMax, -1, bestGamma, bestC,
					        fineStepsWeight, bestFeatures, trainGatherer, develGatherer, scoreboardWeightsTuning).run();

					ModelConfigurationResult bestWeightsRes = ModelConfigurationResult.findBest(scoreboardWeightsTuning,
					        optProfile, optProfileThreshold);

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
					ParameterTesterEF pt = new ParameterTesterEF(bestwPos, bestwNeg, bestFeatures, trainGatherer, develGatherer,
					        bestGamma, bestC, wikiApi);
					new GammaCSelector<Tag, HashSet<Tag>>(fineCMin, fineCMax, bestC,
					        (fineCMax - fineCMin) / fineStepsCGamma / 10.0, fineGammaMin, fineGammaMax, bestGamma,
					        (fineGammaMax - fineGammaMin) / fineStepsCGamma / 10.0, fineStepsCGamma, pt, scoreboardGammaCTuning)
					                .run();

					ModelConfigurationResult bestCGammaResult = ModelConfigurationResult.findBest(scoreboardGammaCTuning,
					        optProfile, optProfileThreshold);

					bestC = bestCGammaResult.getC();
					bestGamma = bestCGammaResult.getGamma();

					restrictedFeaturesScoreboard.addAll(scoreboardGammaCTuning);
					LOG.info("Done gamma-C tuning (iteration {}). Best gamma/C: {}/{}", iteration, bestGamma, bestC);
				}

				// Do feature selection
				if (!ftrSelMethod.equals("oneshot")) {
					Vector<ModelConfigurationResult> scoreboardFtrSelection = new Vector<>();

					ParameterTesterEF pt = new ParameterTesterEF(bestwPos, bestwNeg, bestFeatures, trainGatherer, develGatherer,
					        bestGamma, bestC, wikiApi);
					if (ftrSelMethod.equals("ablation"))
						new AblationFeatureSelector<Tag, HashSet<Tag>>(pt, optProfile, optProfileThreshold,
						        scoreboardFtrSelection).run();
					else if (ftrSelMethod.equals("ablation-rank"))
						new AblationRankFeatureSelector<Tag, HashSet<Tag>>(pt, scoreboardFtrSelection).run();
					else if (ftrSelMethod.equals("increment"))
						new IncrementalFeatureSelector<Tag, HashSet<Tag>>(pt, bestFeatures, restrictFeaturesI, optProfile,
						        optProfileThreshold, scoreboardFtrSelection).run();

					bestFeatures = ModelConfigurationResult.findBest(scoreboardFtrSelection, optProfile, optProfileThreshold)
					        .getFeatures();
					restrictedFeaturesScoreboard.addAll(scoreboardFtrSelection);
					LOG.info("Done feature selection (iteration {}).", iteration);
				}

				ModelConfigurationResult newBest = ModelConfigurationResult.findBest(restrictedFeaturesScoreboard, optProfile,
				        optProfileThreshold);
				if (newBest.equalResult(bestResult, optProfile, optProfileThreshold)) {
					LOG.info("Not improving, stopping on iteration {}.", iteration);
					break;
				}
				bestResult = newBest;
			}
			LOG.info("Best result for restricted features iteration: {}", bestResult.getReadable());
			globalScoreboard.addAll(restrictedFeaturesScoreboard);
		}

		ModelConfigurationResult globalBest = ModelConfigurationResult.findBest(globalScoreboard, optProfile,
		        optProfileThreshold);

		ZScoreFeatureNormalizer scaleFn = ZScoreFeatureNormalizer.fromGatherer(trainGatherer);
		LibSvmEntityFilter bestEf = ParameterTesterEF.getFilter(trainGatherer, scaleFn, globalBest.getFeatures(),
		        globalBest.getWPos(), globalBest.getWNeg(), globalBest.getGamma(), globalBest.getC());
		scaleFn.dump(normFile);
		bestEf.toFile(modelFile);

		return new Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult>(globalScoreboard, globalBest);
	}

	private static Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> trainIterativeAR(SmaphAnnotator annotator,
	        OptDataset optDs, OptimizaionProfiles optProfile, double optProfileThreshold, String ftrSelMethod,
	        int[][] restrictFeatures, int[] initFeatures, File modelFile, File normFile, Map<String, QueryInformation> qiCache)
	        throws Exception {
		Vector<ModelConfigurationResult> globalScoreboard = new Vector<>();
		int stepsCGamma = 6;
		int fineStepsCGamma = 6;
		int maxIterations = 10;
		int fineTuneIterations = 3;
		int thrSteps = 30;

		ExampleGatherer<Annotation, HashSet<Annotation>> trainGatherer = new ExampleGatherer<>();
		ExampleGatherer<Annotation, HashSet<Annotation>> develGatherer = new ExampleGatherer<>();

		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(annotator, null, null, trainGatherer, develGatherer, null, null,
		        null, null, -1, null, null, null, null, wikiApi, w2f, optDs, qiCache);

		int[] allFtrs = SmaphUtils.getAllFtrVect(new AnnotationFeaturePack().getFeatureCount());

		if (restrictFeatures == null)
			restrictFeatures = new int[][] { allFtrs };

		for (int[] restrictedFeaturesI : restrictFeatures) {
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
					ParameterTesterAR pt = new ParameterTesterAR(gammaCFeatures, trainGatherer, develGatherer, bestGamma, bestC,
					        optProfile, optProfileThreshold, thrSteps, wikiApi);

					new GammaCSelector<Annotation, HashSet<Annotation>>(cMin, cMax, bestC, (cMax - cMin) / stepsCGamma / 10.0,
					        gammaMin, gammaMax, bestGamma, (gammaMax - gammaMin) / stepsCGamma / 10.0, stepsCGamma, pt,
					        scoreboardGammaCTuning).run();
					bestCGammaResult = ModelConfigurationResult.findBest(scoreboardGammaCTuning, optProfile, optProfileThreshold);

					bestC = bestCGammaResult.getC();
					bestGamma = bestCGammaResult.getGamma();
					if (!ftrSelMethod.equals("increment"))
						restrictedFeaturesScoreboard.addAll(scoreboardGammaCTuning);
					LOG.info("Done broad gamma-C tuning (iteration {}) best gamma/C: {}/{}", iteration, bestGamma, bestC);
				}

				// Fine-tune C and Gamma
				Vector<ModelConfigurationResult> scoreboardGammaCTuning = new Vector<>();
				for (int i = 0; i < fineTuneIterations; i++) {
					double fineCMin = bestC * 0.85;
					double fineGammaMin = bestGamma * 0.85;
					double fineCMax = bestC * 1.20;
					double fineGammaMax = bestGamma * 1.20;
					ParameterTesterAR pt = new ParameterTesterAR(gammaCFeatures, trainGatherer, develGatherer, bestGamma, bestC,
					        optProfile, optProfileThreshold, thrSteps, wikiApi);

					new GammaCSelector<Annotation, HashSet<Annotation>>(fineCMin, fineCMax, bestC,
					        (fineCMax - fineCMin) / fineStepsCGamma / 5.0, fineGammaMin, fineGammaMax, bestGamma,
					        (fineGammaMax - fineGammaMin) / fineStepsCGamma / 5.0, fineStepsCGamma, pt, scoreboardGammaCTuning).run();
					ModelConfigurationResult newBestCGammaResult = ModelConfigurationResult.findBest(scoreboardGammaCTuning,
					        optProfile, optProfileThreshold);

					if (bestCGammaResult.worseThan(newBestCGammaResult, optProfile, optProfileThreshold)) {
						bestCGammaResult = newBestCGammaResult;
						bestC = bestCGammaResult.getC();
						bestGamma = bestCGammaResult.getGamma();

						LOG.info("Done fine gamma-C tuning (outer iteration {}, inner iteration {}) best gamma/C: {}/{}",
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

					ParameterTesterAR pt = new ParameterTesterAR(bestFeatures, trainGatherer, develGatherer, bestGamma, bestC,
					        optProfile, optProfileThreshold, thrSteps, wikiApi);

					if (ftrSelMethod.equals("ablation"))
						new AblationFeatureSelector<Annotation, HashSet<Annotation>>(pt, optProfile, optProfileThreshold,
						        scoreboardFtrSelection).run();
					else if (ftrSelMethod.equals("ablation-rank"))
						new AblationRankFeatureSelector<Annotation, HashSet<Annotation>>(pt, scoreboardFtrSelection).run();
					else if (ftrSelMethod.equals("increment"))
						new IncrementalFeatureSelector<Annotation, HashSet<Annotation>>(pt, bestFeatures, restrictedFeaturesI,
						        optProfile, optProfileThreshold, scoreboardFtrSelection).run();

					bestFeatures = ModelConfigurationResult.findBest(scoreboardFtrSelection, optProfile, optProfileThreshold)
					        .getFeatures();

					restrictedFeaturesScoreboard.addAll(scoreboardFtrSelection);
					LOG.info("Done feature selection (iteration {}).", iteration);
				}

				// Fine-tune threshold
				{
					int finalThrSteps = 100;
					restrictedFeaturesScoreboard.add(new ParameterTesterAR(bestFeatures, trainGatherer, develGatherer, bestGamma,
					        bestC, optProfile, optProfileThreshold, finalThrSteps, wikiApi).call());
					LOG.info("Done fine threshold selection (iteration {}).", iteration);
				}

				ModelConfigurationResult newBest = ModelConfigurationResult.findBest(restrictedFeaturesScoreboard, optProfile,
				        optProfileThreshold);
				if (bestResult != null && newBest.equalResult(bestResult, optProfile, optProfileThreshold)) {
					LOG.info("Not improving, stopping on iteration {}/", iteration);
					break;
				}
				bestResult = newBest;
			}
			globalScoreboard.addAll(restrictedFeaturesScoreboard);
			LOG.info("Best result for restricted features iteration: {}", bestResult.getReadable());
		}

		ModelConfigurationResult globalBest = ModelConfigurationResult.findBest(globalScoreboard, optProfile,
		        optProfileThreshold);

		ZScoreFeatureNormalizer scaleFn = ZScoreFeatureNormalizer.fromGatherer(trainGatherer);
		LibSvmAnnotationRegressor bestAR = ParameterTesterAR.getRegressor(trainGatherer, scaleFn, globalBest.getFeatures(),
		        globalBest.getGamma(), globalBest.getC(), globalBest.getThreshold());
		scaleFn.dump(normFile);
		bestAR.toFile(modelFile);

		return new Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult>(globalScoreboard, globalBest);
	}

	private static Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult> trainIterativeGreedy(SmaphAnnotator annotator,
	        OptDataset optDs, OptimizaionProfiles optProfile, double optProfileThreshold, String ftrSelMethod,
	        int[][] restrictFeatures, int[] initFeatures, Websearch ws, int topKS1, int topKS2, int topKS3, Map<String, QueryInformation> qiCache) throws Exception {
		
		{
			int step = 0;
			while (SmaphBuilder.getModelFile(SmaphVersion.GREEDY, ws, topKS1, topKS2, topKS3, step).exists()) {
				File model = SmaphBuilder.getModelFile(SmaphVersion.GREEDY, ws, topKS1, topKS2, topKS3, step);
				File fn = SmaphBuilder.getZscoreNormalizerFile(SmaphVersion.GREEDY, ws, topKS1, topKS2, topKS3, step);
				LOG.info("Deleting file {}", model.getAbsolutePath());
				model.delete();
				LOG.info("Deleting file {}", fn.getAbsolutePath());
				fn.delete();
				step++;
			}
		}
		
		int step = 0;
		List<HashSet<Annotation>> partialSolutionsTrain = new Vector<>();
		List<HashSet<Annotation>> partialSolutionsDevel = new Vector<>();
		Vector<ModelConfigurationResult> allModels = new Vector<>();
		ModelConfigurationResult previousBest = null;
		do {
			Pair<Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult>, Pair<ZScoreFeatureNormalizer, LibSvmAnnotationRegressor>> p = trainIterativeGreedyStep(annotator, partialSolutionsTrain, partialSolutionsDevel, step, optDs, optProfile, optProfileThreshold, ftrSelMethod, restrictFeatures, initFeatures, previousBest, qiCache);
			if (p == null) {
				LOG.info("Greedy step {}. No improvement is possible, stopping.", step);
				break;
			}
			ModelConfigurationResult bestConfigurationStep = p.first.second;
			Vector<ModelConfigurationResult> allModelsStep = p.first.first;
			ZScoreFeatureNormalizer fnStep = p.second.first;
			LibSvmAnnotationRegressor bestModelStep = p.second.second;
			
			allModels.addAll(allModelsStep);
			
			if (previousBest == null || previousBest.worseThan(bestConfigurationStep, optProfile, optProfileThreshold)) {
				LOG.info("Greedy step {}. Found a better model: {}", step, bestConfigurationStep.getReadable());
				File modelFile = SmaphBuilder.getModelFile(SmaphVersion.GREEDY, ws, topKS1, topKS2, topKS3, step);
				File normFile = SmaphBuilder.getZscoreNormalizerFile(SmaphVersion.GREEDY, ws, topKS1, topKS2, topKS3, step);
				fnStep.dump(normFile);
				bestModelStep.toFile(modelFile);
				previousBest = bestConfigurationStep;
			} else {
				LOG.info("Greedy step {} did not improve previous step. Stopping.");
				break;
			}
			step++;
		} while (true);
		
		return new Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult>(allModels,
				ModelConfigurationResult.findBest(allModels, optProfile, optProfileThreshold));
	}

	private static boolean improvementPossibleGreedy(ExampleGatherer<Annotation, HashSet<Annotation>> gatherer) {
		return gatherer.getHighestTarget() > 0;
	}

	private static Pair<Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult>, Pair<ZScoreFeatureNormalizer, LibSvmAnnotationRegressor>> trainIterativeGreedyStep(SmaphAnnotator annotator,
			List<HashSet<Annotation>> partialSolutionsTrain, List<HashSet<Annotation>> partialSolutionsDevel, int step,
	        OptDataset optDs, OptimizaionProfiles optProfile, double optProfileThreshold, String ftrSelMethod,
	        int[][] restrictFeatures, int[] initFeatures, ModelConfigurationResult previousBest, Map<String, QueryInformation> qiCache)
	        throws Exception {
		Vector<ModelConfigurationResult> globalScoreboard = new Vector<>();
		int maxIterations = 10;
		int thrSteps = 30;

		ExampleGatherer<Annotation, HashSet<Annotation>> trainGatherer = new ExampleGatherer<>();
		ExampleGatherer<Annotation, HashSet<Annotation>> develGatherer = new ExampleGatherer<>();

		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(annotator, null, null, null, null, null, null,
				partialSolutionsTrain, partialSolutionsDevel, step, trainGatherer, develGatherer, null, null, wikiApi, w2f, optDs, qiCache);

		if (!improvementPossibleGreedy(trainGatherer) || !improvementPossibleGreedy(develGatherer))
			return null;
		
		int[] allFtrs = SmaphUtils.getAllFtrVect(new GreedyFeaturePack().getFeatureCount());

		if (restrictFeatures == null)
			restrictFeatures = new int[][] { allFtrs };

		for (int[] restrictedFeaturesI : restrictFeatures) {
			Vector<ModelConfigurationResult> restrictedFeaturesScoreboard = new Vector<>();
			ModelConfigurationResult bestResult = null;

			int[] bestFeatures = ftrSelMethod.equals("increment") ? initFeatures : restrictedFeaturesI;

			double bestC = 1;
			double bestGamma = 1.0 / (bestFeatures == null ? allFtrs : bestFeatures).length;
			for (int iteration = 0; iteration < maxIterations; iteration++) {
				
				// Do feature selection
				if (!ftrSelMethod.equals("oneshot")) {
					Vector<ModelConfigurationResult> scoreboardFtrSelection = new Vector<>();

					ParameterTesterGreedy pt = new ParameterTesterGreedy(partialSolutionsDevel, bestFeatures, trainGatherer,
					        develGatherer, bestGamma, bestC, optProfile, optProfileThreshold, thrSteps, wikiApi, step);

					if (ftrSelMethod.equals("ablation"))
						new AblationFeatureSelector<Annotation, HashSet<Annotation>>(pt, optProfile, optProfileThreshold,
						        scoreboardFtrSelection).run();
					else if (ftrSelMethod.equals("ablation-rank"))
						new AblationRankFeatureSelector<Annotation, HashSet<Annotation>>(pt, scoreboardFtrSelection).run();
					else if (ftrSelMethod.equals("increment"))
						new IncrementalFeatureSelector<Annotation, HashSet<Annotation>>(pt, bestFeatures, restrictedFeaturesI,
						        optProfile, optProfileThreshold, scoreboardFtrSelection).run();

					ModelConfigurationResult newBest = ModelConfigurationResult.findBest(scoreboardFtrSelection, optProfile, optProfileThreshold);
					if (newBest != null)
						bestFeatures = newBest.getFeatures();

					restrictedFeaturesScoreboard.addAll(scoreboardFtrSelection);
					LOG.info("Greedy step {}. Done feature selection (iteration {}).", step, iteration);
				}
				
				// Fine-tune threshold
				{
					int finalThrSteps = 100;
					restrictedFeaturesScoreboard.add(new ParameterTesterGreedy(partialSolutionsDevel, bestFeatures, trainGatherer,
					        develGatherer, bestGamma, bestC, optProfile, optProfileThreshold, finalThrSteps, wikiApi, step).call());
					LOG.info("Greedy step {}. Done fine threshold selection (iteration {}).", step, iteration);
				}

				ModelConfigurationResult newBest = ModelConfigurationResult.findBest(restrictedFeaturesScoreboard, optProfile,
				        optProfileThreshold);
				if (bestResult != null && newBest.equalResult(bestResult, optProfile, optProfileThreshold)) {
					LOG.info("Greedy step {}. Not improving, stopping on iteration {}.", step, iteration);
					break;
				}
				bestResult = newBest;
			}
			globalScoreboard.addAll(restrictedFeaturesScoreboard);
			LOG.info("Greedy step {}. Best result for restricted features iteration: {}", step, bestResult.getReadable());
		}

		ModelConfigurationResult globalBest = ModelConfigurationResult.findBest(globalScoreboard, optProfile,
		        optProfileThreshold);

		ZScoreFeatureNormalizer scaleFn = ZScoreFeatureNormalizer.fromGatherer(trainGatherer, false);
		LibSvmAnnotationRegressor bestGreedy = ParameterTesterGreedy.getRegressor(trainGatherer, scaleFn, globalBest.getFeatures(),
		        globalBest.getGamma(), globalBest.getC(), globalBest.getThreshold());

		// Update partial solutions
		List<Vector<Pair<FeaturePack<Annotation>, Annotation>>> ftrPacksAndAnnotationsTrain = trainGatherer.getDataAndFeaturePacksOnePerInstance();
		List<Vector<Pair<FeaturePack<Annotation>, Annotation>>> ftrPacksAndAnnotationsDevel = develGatherer.getDataAndFeaturePacksOnePerInstance();
		
		if (ftrPacksAndAnnotationsTrain.size() != partialSolutionsTrain.size() || ftrPacksAndAnnotationsDevel.size() != partialSolutionsDevel.size())
			throw new IllegalStateException();

		for (int i=0; i<ftrPacksAndAnnotationsTrain.size(); i++){
			ScoredAnnotation a = GreedyLinkback.getStepAnnotation(ftrPacksAndAnnotationsTrain.get(i), bestGreedy, scaleFn);
			if (a != null)
				partialSolutionsTrain.get(i).add(a);
		}

		for (int i=0; i<ftrPacksAndAnnotationsDevel.size(); i++){
			ScoredAnnotation a = GreedyLinkback.getStepAnnotation(ftrPacksAndAnnotationsDevel.get(i), bestGreedy, scaleFn);
			if (a != null)
				partialSolutionsDevel.get(i).add(a);
		}

		return new Pair<Pair<Vector<ModelConfigurationResult>, ModelConfigurationResult>, Pair<ZScoreFeatureNormalizer, LibSvmAnnotationRegressor>>(new Pair<>(globalScoreboard, globalBest), new Pair<>(scaleFn, bestGreedy));
	}
	
	private static class GammaCSelector<E extends Serializable, G extends Serializable> implements Runnable {
		private double gammaMin, gammaMax, cMin, cMax;
		private double kappaC, kappaGamma;
		private double origC, origGamma;
		private int steps;
		private ParameterTester<E, G> pt;
		private Vector<ModelConfigurationResult> scoreBoard;

		public GammaCSelector(double cMin, double cMax, double origC, double kappaC, double gammaMin, double gammaMax,
		        double origGamma, double kappaGamma, int steps, ParameterTester<E, G> pt,
		        Vector<ModelConfigurationResult> scoreboardGammaCTuning) {
			if (kappaC == -1)
				kappaC = (cMax - cMin) / steps;
			if (kappaGamma == -1)
				kappaGamma = (gammaMax - gammaMin) / steps;

			if (!(kappaC > 0 && (cMax - cMin == 0 || kappaC <= cMax - cMin)))
				throw new IllegalArgumentException(String.format("k must be between 0.0 and %f. Got %f", cMax - cMin, kappaC));
			if (!(kappaGamma > 0 && (gammaMax - gammaMin == 0 || kappaGamma <= gammaMax - gammaMin)))
				throw new IllegalArgumentException(
				        String.format("k must be between 0.0 and %f. Got %f", gammaMax - gammaMin, kappaGamma));
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
			ExecutorService execServ = Executors.newFixedThreadPool(THREADS_NUM);
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

		public WeightSelector(double wPosMin, double wPosMax, double kappaPos, double wNegMin, double wNegMax, double kappaNeg,
		        double gamma, double C, int steps, int[] features, ExampleGatherer<Tag, HashSet<Tag>> trainEQFGatherer,
		        ExampleGatherer<Tag, HashSet<Tag>> testEQFGatherer, Vector<ModelConfigurationResult> scoreboard) {
			if (kappaNeg == -1)
				kappaNeg = (wNegMax - wNegMin) / steps;
			if (kappaPos == -1)
				kappaPos = (wPosMax - wPosMin) / steps;

			if (!(kappaPos > 0 && (wPosMax - wPosMin == 0 || kappaPos <= wPosMax - wPosMin)))
				throw new IllegalArgumentException(
				        String.format("k must be between 0.0 and %f. Got %f", wPosMax - wPosMin, kappaPos));
			if (!(kappaNeg > 0 && (wNegMax - wNegMin == 0 || kappaNeg <= wNegMax - wNegMin)))
				throw new IllegalArgumentException(
				        String.format("k must be between 0.0 and %f. Got %f", wNegMax - wNegMin, kappaNeg));
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
		}

		@Override
		public void run() {
			ExecutorService execServ = Executors.newFixedThreadPool(THREADS_NUM);
			List<Future<ModelConfigurationResult>> futures = new Vector<>();

			double wPos, wNeg;
			for (int posI = 0; (wPos = TuneModelLibSvm.computeExpParameter(wPosMax, wPosMin, kappaPos, posI,
			        steps)) <= wPosMax; posI++)
				for (int negI = 0; (wNeg = TuneModelLibSvm.computeExpParameter(wNegMax, wNegMin, kappaNeg, negI,
				        steps)) <= wNegMax; negI++)
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

	private static class AblationFeatureSelector<E extends Serializable, G extends Serializable> implements Runnable {
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

				for (int testFtrId : bestBase.getFeatures()) {
					int[] pickedFtrsIteration = SmaphUtils.removeFtrVect(bestBase.getFeatures(), testFtrId);
					try {
						Future<ModelConfigurationResult> future = execServ.submit(pt.cloneWithFeatures(pickedFtrsIteration));
						futures.add(future);

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
						if (bestIter == null || bestIter.worseThan(res, optProfile, optProfileThreshold))
							bestIter = res;
					} catch (InterruptedException | ExecutionException | Error e) {
						throw new RuntimeException(e);
					}
				execServ.shutdown();

				if (bestIter.worseThan(bestBase, optProfile, optProfileThreshold))
					break;
				else
					bestBase = bestIter;
			}
		}
	}

	private static class AblationRankFeatureSelector<E extends Serializable, G extends Serializable> implements Runnable {
		private Vector<ModelConfigurationResult> scoreboard;
		private ParameterTester<E, G> pt;

		public AblationRankFeatureSelector(ParameterTester<E, G> pt, Vector<ModelConfigurationResult> scoreboard) {
			this.scoreboard = scoreboard;
			this.pt = pt;
		}

		@Override
		public void run() {
			ExecutorService execServ = Executors.newFixedThreadPool(THREADS_NUM);
			List<Future<ModelConfigurationResult>> futures = new Vector<>();

			int[] ftrImportance = getFeatureImportance(pt.getTrainGatherer(), pt.getFeatures());
			
			while (ftrImportance.length > 0) {
				try {
					Future<ModelConfigurationResult> future = execServ.submit(pt.cloneWithFeatures(ftrImportance));
					futures.add(future);
				} catch (Exception | Error e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
				ftrImportance = ArrayUtils.remove(ftrImportance, 0);
			}

			for (Future<ModelConfigurationResult> future : futures)
				try {
					ModelConfigurationResult res = future.get();
					scoreboard.add(res);
				} catch (InterruptedException | ExecutionException | Error e) {
					throw new RuntimeException(e);
				}
			execServ.shutdown();
		}
		


		/**
		 * @param gatherer 
		 * @param features 
		 * @return an array of feature IDs (>=1), ordered by feature importance, without zero-importance features.
		 */
		private static <T extends Serializable, G extends Serializable> int[] getFeatureImportance(ExampleGatherer<T, G> gatherer,
		        int[] features) {
			ZScoreFeatureNormalizer scaleFn = ZScoreFeatureNormalizer.fromGatherer(gatherer);
			Parameter param = new Parameter(SolverType.L2R_L2LOSS_SVR, 0.01, 0.001);
			Problem problem = gatherer.generateLibLinearProblem(features, scaleFn);
			Model m = Linear.train(problem, param);
			double[] weights = m.getFeatureWeights();

			int[] ftrImportance = Arrays.stream(features).boxed().sorted(new Comparator<Integer>() {
				@Override
				public int compare(Integer fId0, Integer fId1) {
					return Double.compare(Math.abs(weights[ArrayUtils.indexOf(features, fId0)]), Math.abs(ArrayUtils.indexOf(features, fId1)));
				}
			}).filter(fId -> weights[ArrayUtils.indexOf(features, fId)] != 0.0).mapToInt(fId -> fId.intValue()).toArray();

			return ftrImportance;
		}
	}

	private static class IncrementalFeatureSelector<E extends Serializable, G extends Serializable> implements Runnable {
		private double optProfileThreshold;
		private OptimizaionProfiles optProfile;
		private Vector<ModelConfigurationResult> scoreboard;
		private ParameterTester<E, G> pt;
		private int[] selectedFeatures, restrictFeatures;

		public IncrementalFeatureSelector(ParameterTester<E, G> pt, int[] selectedFeatures, int[] restrictFeatures,
		        OptimizaionProfiles optProfile, double optProfileThreshold, Vector<ModelConfigurationResult> scoreboard) {
			this.pt = pt;
			this.scoreboard = scoreboard;
			this.optProfile = optProfile;
			this.optProfileThreshold = optProfileThreshold;
			this.restrictFeatures = restrictFeatures;
			this.selectedFeatures = selectedFeatures;
		}

		@Override
		public void run() {
			Set<Integer> alreadyInFtrs = selectedFeatures != null
			        ? new HashSet<Integer>(Arrays.asList(ArrayUtils.toObject(selectedFeatures))) : new HashSet<>();

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
					int[] pickedFtrsIteration = SmaphUtils.addFtrVect(bestFeatures, testFtrId);

					try {
						Future<ModelConfigurationResult> future = execServ.submit(pt.cloneWithFeatures(pickedFtrsIteration));
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
						if (bestIter == null || bestIter.worseThan(res, optProfile, optProfileThreshold)) {
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
