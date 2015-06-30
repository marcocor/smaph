package it.acubelab.smaph.learn;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.Tag;
import it.acubelab.batframework.metrics.MatchRelation;
import it.acubelab.batframework.metrics.MentionAnnotationMatch;
import it.acubelab.batframework.metrics.Metrics;
import it.acubelab.batframework.metrics.MetricsResultSet;
import it.acubelab.batframework.metrics.StrongMentionAnnotationMatch;
import it.acubelab.batframework.metrics.StrongTagMatch;
import it.acubelab.batframework.metrics.WeakAnnotationMatch;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.batframework.utils.ProblemReduction;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.linkback.SvmIndividualAnnotationLinkBack;

import java.io.IOException;
import java.io.Serializable;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

/**
 * This object computes, given a list of FeatureVectors and associated data, and a gold standard, the associated score.
 * @author Marco Cornolti
 * 
 *
 * @param <T> the type of object on which predictions are made
 * @param <G> the type of the gold standard
 */
public abstract class SolutionComputer <T extends Serializable, G extends Object> {
	public abstract MetricsResultSet getResults(List<List<Pair<T, Double>>> candidateAndPred, List<G> gold) throws IOException;
	public abstract double candidateScore(T candidate, G gold) throws IOException;

	public static class TagSetSolutionComputer extends SolutionComputer<Tag, HashSet<Tag>>{
		private WikipediaApiInterface wikiApi;
		private StrongTagMatch stm = new StrongTagMatch(wikiApi);
			
		public  TagSetSolutionComputer(WikipediaApiInterface wikiApi){
			this.wikiApi = wikiApi;
		}
		
		@Override
		public MetricsResultSet getResults(
				List<List<Pair<Tag, Double>>> candidateAndPreds,
				List<HashSet<Tag>> gold) throws IOException {
			List<HashSet<Tag>> solutionList = new Vector<>();
			for (List<Pair<Tag, Double>> candidateAndPred : candidateAndPreds) {
				HashSet<Tag> computedSolution = new HashSet<>();
				solutionList.add(computedSolution);
				for (Pair<Tag, Double> p : candidateAndPred)
					if (p.second > 0)
						computedSolution.add(p.first);
			}
			
			if (gold.size() !=  solutionList.size())
				throw new RuntimeException();
			Metrics<Tag> m = new Metrics<>();
			return m.getResult(solutionList, gold, stm);
		}

		@Override
		public double candidateScore(Tag candidate, HashSet<Tag> gold) {
			for (Tag t : gold)
				if (stm.match(candidate, t))
					return 1;
			return 0;
		}

	}
	
	public static class AnnotationSetSolutionComputer extends SolutionComputer<Annotation, HashSet<Annotation>>{
		private WikipediaApiInterface wikiApi;
		private double threshold;
		private MatchRelation<Annotation> am = new StrongMentionAnnotationMatch();
			
		public  AnnotationSetSolutionComputer(WikipediaApiInterface wikiApi, double threshold){
			this.wikiApi = wikiApi;
			this.threshold = threshold;
		}
		
		@Override
		public MetricsResultSet getResults(
				List<List<Pair<Annotation, Double>>> candidateAndPreds,
				List<HashSet<Annotation>> gold) throws IOException {
			
			List<HashSet<Annotation>> solutionList = new Vector<>();
			for (List<Pair<Annotation, Double>> candidateAndPred : candidateAndPreds) {
				HashSet<Annotation> computedSolution = ProblemReduction
						.Sa2WToA2W(SvmIndividualAnnotationLinkBack.getResult(
								candidateAndPred, threshold));
			solutionList.add(computedSolution);
			}

            if (gold.size() != solutionList.size())
                throw new RuntimeException();
			Metrics<Annotation> m = new Metrics<>();
			return m.getResult(solutionList, gold, am);
		}

		public static double candidateScoreStatic(Annotation candidate, HashSet<Annotation> gold, MatchRelation<Annotation> am) {
			for (Annotation t : gold)
				if (am.match(candidate, t))
					return 1;
			return 0;
		}

		@Override
		public double candidateScore(Annotation candidate, HashSet<Annotation> gold) {
			return candidateScoreStatic(candidate, gold, am);
		}

	}

	public static class BindingSolutionComputer extends SolutionComputer<HashSet<Annotation>, HashSet<Annotation>> {
		private WikipediaApiInterface wikiApi;
		private MatchRelation<Annotation> wam = new WeakAnnotationMatch(wikiApi);

		public  BindingSolutionComputer(WikipediaApiInterface wikiApi){
			this.wikiApi = wikiApi;
		}
		
		@Override
		public MetricsResultSet getResults(
				List<List<Pair<HashSet<Annotation>, Double>>> candidateAndPreds,
				List<HashSet<Annotation>> gold) throws IOException {
			
			List<HashSet<Annotation>> solutionList = new Vector<>();
			for (List<Pair<HashSet<Annotation>, Double>> candidateAndPred : candidateAndPreds) {
				double bestScore = Double.NEGATIVE_INFINITY;
				HashSet<Annotation> computedSolution = new HashSet<>();
				for (Pair<HashSet<Annotation>, Double>bindingAndScore : candidateAndPred){
					if (bindingAndScore.second > bestScore){
						computedSolution = bindingAndScore.first;
						bestScore = bindingAndScore.second;
					}
				}
				solutionList.add(computedSolution);
			}
			
			if (gold.size() !=  solutionList.size())
				throw new RuntimeException();
			Metrics<Annotation> m = new Metrics<>();
			return m.getResult(solutionList, gold, wam);
		}

		@Override
		public double candidateScore(HashSet<Annotation> candidate, HashSet<Annotation> gold) throws IOException {
			List<HashSet<Annotation>> lC = new Vector<HashSet<Annotation>>();
			lC.add(candidate);
			List<HashSet<Annotation>> lG = new Vector<HashSet<Annotation>>();
			lG.add(gold);

			return new Metrics<Annotation>().getResult(lC, lG, wam).getF1s(0);
		}

	}
}
