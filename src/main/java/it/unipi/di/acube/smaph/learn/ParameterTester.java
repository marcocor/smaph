package it.unipi.di.acube.smaph.learn;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.metrics.MetricsResultSet;
import it.unipi.di.acube.batframework.metrics.StrongAnnotationMatch;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
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

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.Callable;

import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_parameter;
import libsvm.svm_problem;

public abstract class ParameterTester<E, G> implements Callable<ModelConfigurationResult> {
	public abstract ParameterTester<E, G> cloneWithFeatures(int[] ftrs);
	public abstract ParameterTester<E, G> cloneWithWeights(double wPos, double wNeg);
	public abstract ParameterTester<E, G> cloneWithGammaC(double gamma, double C);

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

	public static svm_parameter getParametersLB(double gamma, double c) {
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
	public static svm_model trainModel(svm_parameter param, 
			svm_problem trainProblem) {
		String error_msg = svm.svm_check_parameter(trainProblem, param);

		if (error_msg != null) {
			System.err.print("ERROR: " + error_msg + "\n");
			System.exit(1);
		}

		svm_model m = svm.svm_train(trainProblem, param);
		/*try {
	        svm.svm_save_model(String.format("/tmp/model.%f.%f", param.gamma, param.C), m);
        } catch (IOException e) {
	        // TODO Auto-generated catch block
	        e.printStackTrace();
        }*/
		return m;
	}

	public static MetricsResultSet testEntityFilter(EntityFilter model, ExampleGatherer<Tag, HashSet<Tag>> testGatherer, int[] features, FeatureNormalizer scaleFn, SolutionComputer<Tag, HashSet<Tag>> sc){
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
			return sc.getResults(candidateAndPreds, golds);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static MetricsResultSet testAnnotationRegressorModel(AnnotationRegressor model, ExampleGatherer<Annotation, HashSet<Annotation>> testGatherer, FeatureNormalizer scaleFn, SolutionComputer<Annotation, HashSet<Annotation>> sc) throws IOException{
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
			return sc.getResults(candidateAndPreds, golds);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static MetricsResultSet testBindingRegressorModel(BindingRegressor model, ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> testGatherer, FeatureNormalizer scaleFn, SolutionComputer<HashSet<Annotation>, HashSet<Annotation>> sc) throws IOException{
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
			return sc.getResults(candidateAndPreds, golds);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public static class ParameterTesterAR extends ParameterTester<Annotation, HashSet<Annotation>>{
		private ExampleGatherer<Annotation, HashSet<Annotation>> trainGatherer;
		private ExampleGatherer<Annotation, HashSet<Annotation>> testGatherer;
		private int[] features;
		private WikipediaApiInterface wikiApi;
		private double gamma, C;
		private double optProfileThr;
		private OptimizaionProfiles optProfile;
		private int thrSteps;

		public ParameterTesterAR(int[] features,
				ExampleGatherer<Annotation, HashSet<Annotation>> trainGatherer,
				ExampleGatherer<Annotation, HashSet<Annotation>> testGatherer,
				double gamma, double C, OptimizaionProfiles optProfile, double optProfileThr,
				int thrSteps,
				WikipediaApiInterface wikiApi) {
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

		@Override
		public ModelConfigurationResult call() throws Exception {
			ZScoreFeatureNormalizer scaleFn = new ZScoreFeatureNormalizer(trainGatherer);
			svm_problem trainProblem = trainGatherer.generateLibSvmProblem(this.features, scaleFn);

			svm_parameter param = getParametersEFRegressor(gamma, C);

			svm_model model = trainModel(param, trainProblem);
			LibSvmAnnotationRegressor ar = new LibSvmAnnotationRegressor(model);


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

			List<ModelConfigurationResult> configurations = new Vector<>();
			scanThresholdRange(thrSteps, thrMin, thrMax, configurations, candidateAndPreds, golds);
			
			double bestThr = ModelConfigurationResult.findBest(configurations, optProfile, optProfileThr).getThreshold();
			double stepSize = (thrMax - thrMin) / thrSteps;
			thrMin = bestThr - 2.0 * stepSize;
			thrMax = bestThr + 2.0 * stepSize;
			
			scanThresholdRange(thrSteps, thrMin, thrMax, configurations, candidateAndPreds, golds);

			ModelConfigurationResult bestMcr = ModelConfigurationResult.findBest(configurations, optProfile, optProfileThr);

			System.err.printf("Best threshold: %s.%n", bestMcr.getReadable());

			return bestMcr;
		}
		
		public void scanThresholdRange(int thrSteps, double thrMin, double thrMax, List<ModelConfigurationResult> configurations,
		        List<List<Pair<Annotation, Double>>> candidateAndPreds, List<HashSet<Annotation>> golds) throws IOException {
			for (int i = 0; i < thrSteps; i++) {
				double thr = thrMin + i * (thrMax - thrMin) / thrSteps;
				MetricsResultSet metrics = new SolutionComputer.AnnotationSetSolutionComputer(wikiApi, thr).getResults(candidateAndPreds, golds);

				ModelConfigurationResult mcr = new ModelConfigurationResult(features, -1, -1, gamma, C, thr, metrics,
				        testGatherer.getExamplesCount());
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
	}

	public static class ParameterTesterEF extends ParameterTester<Tag, HashSet<Tag>> {
		private double wPos, wNeg, gamma, C;
		private ExampleGatherer<Tag, HashSet<Tag>> trainGatherer;
		private ExampleGatherer<Tag, HashSet<Tag>> testGatherer;
		private int[] features;
		private WikipediaApiInterface wikiApi;

		public ParameterTesterEF(double wPos, double wNeg, int[] features,
				ExampleGatherer<Tag, HashSet<Tag>> trainEQFGatherer,
				ExampleGatherer<Tag, HashSet<Tag>> testEQFGatherer,
				double gamma, double C,
				WikipediaApiInterface wikiApi) {
			this.wPos = wPos;
			this.wNeg = wNeg;
			this.features = features;
			this.trainGatherer = trainEQFGatherer;
			this.testGatherer = testEQFGatherer;
			this.gamma = gamma;
			this.C = C;
			this.wikiApi = wikiApi;
		}

		@Override
		public ModelConfigurationResult call() throws Exception {

			ZScoreFeatureNormalizer scaleFn = new ZScoreFeatureNormalizer(trainGatherer);
			svm_problem trainProblem = trainGatherer.generateLibSvmProblem(this.features, scaleFn);

			svm_parameter param = getParametersEF(wPos, wNeg, gamma, C);

			svm_model model = trainModel(param, trainProblem);
			EntityFilter ef = new LibSvmEntityFilter(model);

			MetricsResultSet metrics = testEntityFilter(ef, testGatherer, this.features, scaleFn, new SolutionComputer.TagSetSolutionComputer(wikiApi));

			ModelConfigurationResult mcr = new ModelConfigurationResult(
					features, wPos, wNeg, gamma, C, -1, metrics,
					testGatherer.getExamplesCount());

			System.err.printf("Found: %s.%n", mcr.getReadable());

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
	}

}