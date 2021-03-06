package it.unipi.di.acube.smaph.learn;

import java.io.IOException;
import java.io.Serializable;
import java.lang.invoke.MethodHandles;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.metrics.MetricsResultSet;
import it.unipi.di.acube.batframework.metrics.StrongAnnotationMatch;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.smaph.learn.SolutionComputer.AnnotationSetSolutionComputer;
import it.unipi.di.acube.smaph.learn.TuneModelLibSvm.OptimizaionProfiles;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.models.entityfilters.EntityFilter;
import it.unipi.di.acube.smaph.learn.models.entityfilters.LibSvmEntityFilter;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.AnnotationRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.LibSvmAnnotationRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor.BindingRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_parameter;
import libsvm.svm_problem;

public abstract class ParameterTester<E extends Serializable, G extends Serializable> implements Callable<ModelConfigurationResult> {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	public abstract ParameterTester<E, G> cloneWithFeatures(int[] ftrs);
	public abstract ParameterTester<E, G> cloneWithWeights(double wPos, double wNeg);
	public abstract ParameterTester<E, G> cloneWithGammaC(double gamma, double C);
	public abstract ExampleGatherer<E, G> getTrainGatherer();
	public abstract int[] getFeatures();

	private static svm_parameter getParametersClassifier(double wPos, double wNeg, double gamma, double C) {
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

	private static svm_parameter getParametersRegressor(double gamma, double C) {
		svm_parameter param = new svm_parameter();
		param.svm_type = svm_parameter.EPSILON_SVR;
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
		param.weight = new double[] { -1, -1 };
		return param;
	}

	private static svm_model trainModel(svm_parameter param, 
			svm_problem trainProblem) {
		String error_msg = svm.svm_check_parameter(trainProblem, param);

		if (error_msg != null) {
			LOG.error(error_msg);
			System.exit(1);
		}

		return svm.svm_train(trainProblem, param);
	}

	public static MetricsResultSet testEntityFilter(EntityFilter model, ExampleGatherer<Tag, HashSet<Tag>> testGatherer, int[] features, FeatureNormalizer scaleFn, WikipediaInterface wikiApi){
		List<Pair<Vector<Pair<FeaturePack<Tag>, Tag>>, HashSet<Tag>>> ftrsAndDatasAndGolds = testGatherer.getDataAndFeaturePacksAndGoldOnePerInstance();

		List<HashSet<Tag>> golds = new Vector<>();
		List<List<Pair<Tag, Double>>> candidateAndPreds = new Vector<>();
		for (Pair<Vector<Pair<FeaturePack<Tag>, Tag>>, HashSet<Tag>> ftrsAndDatasAndGold : ftrsAndDatasAndGolds) {
			golds.add(ftrsAndDatasAndGold.second);
			List<Pair<Tag, Double>> candidateAndPred = new Vector<>();
			candidateAndPreds.add(candidateAndPred);
			for (Pair<FeaturePack<Tag>, Tag> p : ftrsAndDatasAndGold.first)
				candidateAndPred.add(new Pair<Tag, Double>(p.second, model.filterEntity(p.first, scaleFn)? 1.0 : 0.0));
		}

		try {
			return new SolutionComputer.TagSetSolutionComputer(wikiApi, candidateAndPreds, golds).getResults();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static MetricsResultSet testAnnotationRegressorModel(AnnotationRegressor model, ExampleGatherer<Annotation, HashSet<Annotation>> testGatherer, FeatureNormalizer scaleFn, WikipediaInterface wikiApi) throws IOException{
		List<HashSet<Annotation>> golds = new Vector<>();
		List<List<Pair<Annotation, Double>>> candidateAndPreds = new Vector<>();
		for (Pair<Vector<Pair<FeaturePack<Annotation>, Annotation>>, HashSet<Annotation>> ftrsAndDatasAndGold : testGatherer.getDataAndFeaturePacksAndGoldOnePerInstance()) {
			golds.add(ftrsAndDatasAndGold.second);
			List<Pair<Annotation, Double>> candidateAndPred = new Vector<>();
			candidateAndPreds.add(candidateAndPred);
			for (Pair<FeaturePack<Annotation>, Annotation> p : ftrsAndDatasAndGold.first){
				double predictedScore = model.predictScore(p.first, scaleFn);
				candidateAndPred.add(new Pair<Annotation, Double>(p.second, predictedScore));
			}
		}

		try {
			return new SolutionComputer.AnnotationSetSolutionComputer(wikiApi, model.threshold(), candidateAndPreds, golds).getResults();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static MetricsResultSet testBindingRegressorModel(BindingRegressor model, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> testGatherer, FeatureNormalizer scaleFn, SolutionComputer<HashSet<Annotation>, HashSet<Annotation>> sc, WikipediaInterface wikiApi) throws IOException{
		List<HashSet<Annotation>> golds = new Vector<>();
		List<List<Pair<HashSet<Annotation>, Double>>> candidateAndPreds = new Vector<>();
		for (Pair<Vector<Pair<FeaturePack<HashSet<Annotation>>, HashSet<Annotation>>>, HashSet<Annotation>> ftrsAndDatasAndGold : testGatherer.getDataAndFeaturePacksAndGoldOnePerInstance()) {
			Vector<Pair<FeaturePack<HashSet<Annotation>>, HashSet<Annotation>>> ftrsAndBindings = ftrsAndDatasAndGold.first;
			HashSet<Annotation> gold = ftrsAndDatasAndGold.second; 

			golds.add(gold);

			List<FeaturePack<HashSet<Annotation>>> packs = new Vector<>();
			for (Pair<FeaturePack<HashSet<Annotation>>, HashSet<Annotation>> ftrsAndBinding: ftrsAndBindings)
				packs.add(ftrsAndBinding.first);

			double[] scores = model.getScores(packs, scaleFn);
			if (scores.length != ftrsAndDatasAndGold.first.size())
				throw new RuntimeException("Invalid scores retrieved.");

			List<Pair<HashSet<Annotation>, Double>> candidateAndPred = new Vector<>();
			candidateAndPreds.add(candidateAndPred);
			for (int i = 0; i < scores.length; i++)
				candidateAndPred.add(new Pair<HashSet<Annotation>, Double>(ftrsAndBindings.get(i).second, scores[i]));
		}

		try {
			return new SolutionComputer.BindingSolutionComputer(wikiApi, candidateAndPreds, golds).getResults();
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static class ParameterTesterAR extends ParameterTester<Annotation, HashSet<Annotation>>{
		private ExampleGatherer<Annotation, HashSet<Annotation>> trainGatherer;
		private ExampleGatherer<Annotation, HashSet<Annotation>> testGatherer;
		private int[] features;
		private WikipediaInterface wikiApi;
		private double gamma, C;
		private double optProfileThr;
		private OptimizaionProfiles optProfile;
		private int thrSteps;

		public ParameterTesterAR(int[] features,
				ExampleGatherer<Annotation, HashSet<Annotation>> trainGatherer,
				ExampleGatherer<Annotation, HashSet<Annotation>> testGatherer,
				double gamma, double C, OptimizaionProfiles optProfile, double optProfileThr,
				int thrSteps,
				WikipediaInterface wikiApi) {
			this.features = features;
			this.trainGatherer = trainGatherer;
			this.testGatherer = testGatherer;
			this.gamma = gamma;
			this.C = C;
			this.wikiApi = wikiApi;
			this.optProfileThr = optProfileThr;
			this.optProfile = optProfile;
			this.thrSteps =thrSteps;
		}

		public static LibSvmAnnotationRegressor getRegressor(ExampleGatherer<Annotation, HashSet<Annotation>> trainGatherer, ZScoreFeatureNormalizer scaleFn, int[] features, double gamma, double C, double threshold){
			svm_problem trainProblem = trainGatherer.generateLibSvmProblem(features, scaleFn);
			svm_parameter param = getParametersRegressor(gamma, C);
			svm_model model = trainModel(param, trainProblem);

			return new LibSvmAnnotationRegressor(model, threshold);
		}

		@Override
		public ModelConfigurationResult call() throws Exception {
			ZScoreFeatureNormalizer scaleFn = ZScoreFeatureNormalizer.fromGatherer(trainGatherer);
			LibSvmAnnotationRegressor ar = getRegressor(trainGatherer, scaleFn, features, gamma, C, -1);

			List<HashSet<Annotation>> golds = new Vector<>();
			List<List<Pair<Annotation, Double>>> candidateAndPreds = new Vector<>();
			List<Double> positiveScores = new Vector<>();
			for (Pair<Vector<Pair<FeaturePack<Annotation>, Annotation>>, HashSet<Annotation>> ftrsAndDatasAndGold : testGatherer.getDataAndFeaturePacksAndGoldOnePerInstance()) {
				golds.add(ftrsAndDatasAndGold.second);
				List<Pair<Annotation, Double>> candidateAndPred = new Vector<>();
				candidateAndPreds.add(candidateAndPred);
				for (Pair<FeaturePack<Annotation>, Annotation> p : ftrsAndDatasAndGold.first){
					double predictedScore = ar.predictScore(p.first, scaleFn);
					candidateAndPred.add(new Pair<Annotation, Double>(p.second, predictedScore));
					StrongAnnotationMatch am = new StrongAnnotationMatch(wikiApi);
					if (AnnotationSetSolutionComputer.candidateScoreStatic(p.second, ftrsAndDatasAndGold.second, am) == 1.0)
						positiveScores.add(predictedScore);
				}
			}

			Collections.sort(positiveScores);
			double thrMin = positiveScores.get((int) (positiveScores.size() * 0.05));
			double thrMax = positiveScores.get((int) (positiveScores.size() * 0.95));

			List<ModelConfigurationResult> thrConfigurations = new Vector<>();
			scanThresholdRange(thrSteps, thrMin, thrMax, thrConfigurations, candidateAndPreds, golds);

			double bestThr = ModelConfigurationResult.findBest(thrConfigurations, optProfile, optProfileThr).getThreshold();
			double stepSize = (thrMax - thrMin) / thrSteps;
			thrMin = bestThr - 2.0 * stepSize;
			thrMax = bestThr + 2.0 * stepSize;

			List<ModelConfigurationResult> thrConfigurations2 = new Vector<>();
			scanThresholdRange(thrSteps, thrMin, thrMax, thrConfigurations2, candidateAndPreds, golds);
			ModelConfigurationResult oneBestMcr = ModelConfigurationResult.findBest(thrConfigurations2, optProfile,
			        optProfileThr);

			// Find middle of thresholds that achieve best score.
			int first = 0;
			while (thrConfigurations2.get(first).worseThan(oneBestMcr, optProfile, optProfileThr))
				first++;
			int last = first + 1;
			while (last < thrConfigurations2.size()
			        && !thrConfigurations2.get(last).worseThan(oneBestMcr, optProfile, optProfileThr))
				last++;

			ModelConfigurationResult bestMcr = thrConfigurations2.get((last + first) / 2);

			LOG.info("Best threshold: {}.", bestMcr.getReadable());

			return bestMcr;
		}

		private void scanThresholdRange(int thrSteps, double thrMin, double thrMax, List<ModelConfigurationResult> configurations,
				List<List<Pair<Annotation, Double>>> candidateAndPreds, List<HashSet<Annotation>> golds) throws IOException {
			for (int i = 0; i < thrSteps; i++) {
				double thr = thrMin + i * (thrMax - thrMin) / thrSteps;
				MetricsResultSet metrics = new SolutionComputer.AnnotationSetSolutionComputer(wikiApi, thr, candidateAndPreds, golds).getResults();

				ModelConfigurationResult mcr = new ModelConfigurationResult(features, -1, -1, gamma, C, thr, metrics,
						testGatherer.getExamplesCount(), -1);
				configurations.add(mcr);
			}

		}

		@Override
		public ParameterTester<Annotation, HashSet<Annotation>> cloneWithFeatures(int[] ftrs) {
			return new ParameterTesterAR(ftrs, trainGatherer, testGatherer, gamma, C, optProfile, optProfileThr, thrSteps, wikiApi);
		}

		@Override
		public ParameterTester<Annotation, HashSet<Annotation>> cloneWithWeights(double wPos, double wNeg) {
			return null;
		}

		@Override
		public ParameterTester<Annotation, HashSet<Annotation>> cloneWithGammaC(double gamma, double C) {
			return new ParameterTesterAR(features, trainGatherer, testGatherer, gamma, C, optProfile, optProfileThr, thrSteps, wikiApi);
		}

		@Override
		public ExampleGatherer<Annotation, HashSet<Annotation>> getTrainGatherer() {
			return trainGatherer;
		}
		
		@Override
		public int[] getFeatures() {
			return features;
		}
	}

	public static class ParameterTesterGreedy extends ParameterTester<Annotation, HashSet<Annotation>>{
		private ExampleGatherer<Annotation, HashSet<Annotation>> trainGatherer;
		private ExampleGatherer<Annotation, HashSet<Annotation>> testGatherer;
		private int[] features;
		private WikipediaInterface wikiApi;
		private double gamma, C;
		private double optProfileThr;
		private OptimizaionProfiles optProfile;
		private int thrSteps;
		private int greedyStep;
		private List<HashSet<Annotation>> partialSolutionsTest;

		public ParameterTesterGreedy(
				List<HashSet<Annotation>> partialSolutionsTest,
				int[] features,
				ExampleGatherer<Annotation, HashSet<Annotation>> trainGatherer,
				ExampleGatherer<Annotation, HashSet<Annotation>> testGatherer,
				double gamma, double C, OptimizaionProfiles optProfile, double optProfileThr,
				int thrSteps,
				WikipediaInterface wikiApi, int greedyStep) {
			this.features = features;
			this.trainGatherer = trainGatherer;
			this.testGatherer = testGatherer;
			this.gamma = gamma;
			this.C = C;
			this.wikiApi = wikiApi;
			this.optProfileThr = optProfileThr;
			this.optProfile = optProfile;
			this.thrSteps =thrSteps;
			this.partialSolutionsTest = partialSolutionsTest;
			this.greedyStep = greedyStep;
		}

		public static LibSvmAnnotationRegressor getRegressor(ExampleGatherer<Annotation, HashSet<Annotation>> trainGatherer, ZScoreFeatureNormalizer scaleFn, int[] features, double gamma, double C, double threshold){
			svm_problem trainProblem = trainGatherer.generateLibSvmProblem(features, scaleFn);
			svm_parameter param = getParametersRegressor(gamma, C);
			svm_model model = trainModel(param, trainProblem);
			
			if (model.SV.length == 0)
				return null;

			return new LibSvmAnnotationRegressor(model, threshold);
		}

		@Override
		public ModelConfigurationResult call() throws Exception {
			ZScoreFeatureNormalizer scaleFn = ZScoreFeatureNormalizer.fromGatherer(trainGatherer, false);
			LibSvmAnnotationRegressor ar = getRegressor(trainGatherer, scaleFn, features, gamma, C, -1.0);
			if (ar == null)
				return null;

			List<HashSet<Annotation>> golds = new Vector<>();
			List<List<Pair<Annotation, Double>>> candidateAndPreds = new Vector<>();
			List<Double> positiveScores = new Vector<>();
			List<Pair<Vector<Pair<FeaturePack<Annotation>, Annotation>>, HashSet<Annotation>>> dataAndFtrPackAndGolds = testGatherer.getDataAndFeaturePacksAndGoldOnePerInstance();
			for (int i=0; i<dataAndFtrPackAndGolds.size(); i++) {
				Vector<Pair<FeaturePack<Annotation>, Annotation>> dataAndFtrPack = dataAndFtrPackAndGolds.get(i).first;
				HashSet<Annotation> gold = dataAndFtrPackAndGolds.get(i).second;
				golds.add(gold);
				List<Pair<Annotation, Double>> candidateAndPred = new Vector<>();
				candidateAndPreds.add(candidateAndPred);
				for (Pair<FeaturePack<Annotation>, Annotation> p : dataAndFtrPack){
					double predictedScore = ar.predictScore(p.first, scaleFn);
					candidateAndPred.add(new Pair<Annotation, Double>(p.second, predictedScore));
					StrongAnnotationMatch am = new StrongAnnotationMatch(wikiApi);
					if (SolutionComputer.GreedySolutionComputer.candidateScoreStatic(partialSolutionsTest.get(i), p.second, gold, am) > 0.0)
						positiveScores.add(predictedScore);
				}
			}
			
			Collections.sort(positiveScores);
			double thrMin = positiveScores.get(0);
			double thrMax = positiveScores.get(positiveScores.size() - 1);

			List<ModelConfigurationResult> thrConfigurations = new Vector<>();
			scanThresholdRange(thrSteps, thrMin, thrMax, thrConfigurations, candidateAndPreds, golds, partialSolutionsTest, greedyStep);

			double bestThr = ModelConfigurationResult.findBest(thrConfigurations, optProfile, optProfileThr).getThreshold();
			double stepSize = (thrMax - thrMin) / thrSteps;
			thrMin = bestThr - 2.0 * stepSize;
			thrMax = bestThr + 2.0 * stepSize;

			List<ModelConfigurationResult> thrConfigurations2 = new Vector<>();
			scanThresholdRange(thrSteps, thrMin, thrMax, thrConfigurations2, candidateAndPreds, golds, partialSolutionsTest, greedyStep);

			ModelConfigurationResult oneBestMcr = ModelConfigurationResult.findBest(thrConfigurations2, optProfile, optProfileThr);
			
			//Find middle of thresholds that achieve best score.
			int first = 0;
			while (thrConfigurations2.get(first).worseThan(oneBestMcr, optProfile, optProfileThr))
				first++;
			int last = first+1;
			while (last < thrConfigurations2.size() && !thrConfigurations2.get(last).worseThan(oneBestMcr, optProfile, optProfileThr))
				last++;
			
			ModelConfigurationResult bestMcr = thrConfigurations2.get((last + first) / 2);
			
			LOG.info("Best threshold: {}.", bestMcr.getReadable());

			return bestMcr;
		}

		private void scanThresholdRange(int thrSteps, double thrMin, double thrMax, List<ModelConfigurationResult> configurations,
				List<List<Pair<Annotation, Double>>> candidateAndPreds, List<HashSet<Annotation>> golds, List<HashSet<Annotation>> partialSolutions, int greedyStep) throws IOException {
			for (int i = 0; i <= thrSteps; i++) {
				double thr = thrMin + i * (thrMax - thrMin) / thrSteps;
				MetricsResultSet metrics = new SolutionComputer.GreedySolutionComputer(wikiApi, thr, partialSolutions, candidateAndPreds, golds).getResults();

				ModelConfigurationResult mcr = new ModelConfigurationResult(features, -1, -1, gamma, C, thr, metrics,
						testGatherer.getExamplesCount(), greedyStep);
				configurations.add(mcr);
			}

		}

		@Override
		public ParameterTester<Annotation, HashSet<Annotation>> cloneWithFeatures(int[] ftrs) {
			return new ParameterTesterGreedy(partialSolutionsTest, ftrs, trainGatherer, testGatherer, gamma, C, optProfile, optProfileThr, thrSteps, wikiApi, greedyStep);
		}

		@Override
		public ParameterTester<Annotation, HashSet<Annotation>> cloneWithWeights(double wPos, double wNeg) {
			return null;
		}

		@Override
		public ParameterTester<Annotation, HashSet<Annotation>> cloneWithGammaC(double gamma, double C) {
			return new ParameterTesterGreedy(partialSolutionsTest, features, trainGatherer, testGatherer, gamma, C, optProfile, optProfileThr, thrSteps, wikiApi, greedyStep);
		}

		@Override
		public ExampleGatherer<Annotation, HashSet<Annotation>> getTrainGatherer() {
			return trainGatherer;
		}
		
		@Override
		public int[] getFeatures() {
			return features;
		}
	}

	public static class ParameterTesterEF extends ParameterTester<Tag, HashSet<Tag>> {
		private double wPos, wNeg, gamma, C;
		private ExampleGatherer<Tag, HashSet<Tag>> trainGatherer;
		private ExampleGatherer<Tag, HashSet<Tag>> testGatherer;
		private int[] features;
		private WikipediaInterface wikiApi;

		public ParameterTesterEF(double wPos, double wNeg, int[] features,
				ExampleGatherer<Tag, HashSet<Tag>> trainEQFGatherer,
				ExampleGatherer<Tag, HashSet<Tag>> testEQFGatherer,
				double gamma, double C,
				WikipediaInterface wikiApi) {
			this.wPos = wPos;
			this.wNeg = wNeg;
			this.features = features;
			this.trainGatherer = trainEQFGatherer;
			this.testGatherer = testEQFGatherer;
			this.gamma = gamma;
			this.C = C;
			this.wikiApi = wikiApi;
		}

		public static LibSvmEntityFilter getFilter(ExampleGatherer<Tag, HashSet<Tag>> trainGatherer,
		        ZScoreFeatureNormalizer scaleFn, int[] features, double wPos, double wNeg, double gamma, double C) {
			svm_problem trainProblem = trainGatherer.generateLibSvmProblem(features, scaleFn);
			svm_parameter param = getParametersClassifier(wPos, wNeg, gamma, C);
			svm_model model = trainModel(param, trainProblem);

			return LibSvmEntityFilter.fromModel(model);
		}

		@Override
		public ModelConfigurationResult call() throws Exception {

			ZScoreFeatureNormalizer scaleFn = ZScoreFeatureNormalizer.fromGatherer(trainGatherer);
			EntityFilter ef = getFilter(trainGatherer, scaleFn, features, wPos, wNeg, gamma, C);

			MetricsResultSet metrics = testEntityFilter(ef, testGatherer, this.features, scaleFn, wikiApi);

			ModelConfigurationResult mcr = new ModelConfigurationResult(
					features, wPos, wNeg, gamma, C, -1, metrics,
					testGatherer.getExamplesCount(), -1);

			LOG.info("Found: {}.", mcr.getReadable());

			return mcr;
		}

		@Override
		public ParameterTester<Tag, HashSet<Tag>> cloneWithFeatures(int[] ftrs) {
			return new ParameterTesterEF(wPos, wNeg, ftrs, trainGatherer, testGatherer, gamma, C, wikiApi);
		}

		@Override
		public ParameterTester<Tag, HashSet<Tag>> cloneWithWeights(double wPos, double wNeg) {
			return new ParameterTesterEF(wPos, wNeg, features, trainGatherer, testGatherer, gamma, C, wikiApi);
		}

		@Override
		public ParameterTester<Tag, HashSet<Tag>> cloneWithGammaC(double gamma, double C) {
			return new ParameterTesterEF(wPos, wNeg, features, trainGatherer, testGatherer, gamma, C, wikiApi);
		}

		@Override
		public ExampleGatherer<Tag, HashSet<Tag>> getTrainGatherer() {
			return trainGatherer;
		}
		
		@Override
		public int[] getFeatures() {
			return features;
		}
	}
}