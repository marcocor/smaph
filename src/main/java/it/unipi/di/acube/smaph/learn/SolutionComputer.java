package it.unipi.di.acube.smaph.learn;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.metrics.MatchRelation;
import it.unipi.di.acube.batframework.metrics.Metrics;
import it.unipi.di.acube.batframework.metrics.MetricsResultSet;
import it.unipi.di.acube.batframework.metrics.StrongAnnotationMatch;
import it.unipi.di.acube.batframework.metrics.StrongTagMatch;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.ProblemReduction;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.linkback.IndividualAnnotationLinkBack;

import java.io.IOException;
import java.io.Serializable;
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
	public abstract MetricsResultSet getResults() throws IOException;

	public static class TagSetSolutionComputer extends SolutionComputer<Tag, HashSet<Tag>>{
		private StrongTagMatch stm;
		private List<HashSet<Tag>> gold;
		private List<List<Pair<Tag, Double>>> candidateAndPreds;

		public TagSetSolutionComputer(WikipediaInterface wikiApi, List<List<Pair<Tag, Double>>> candidateAndPreds,
		        List<HashSet<Tag>> gold) {
			this.stm = new StrongTagMatch(wikiApi);
			this.gold = gold;
			this.candidateAndPreds = candidateAndPreds;
		}

		@Override
		public MetricsResultSet getResults() throws IOException {
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

	}
	
	public static class AnnotationSetSolutionComputer extends SolutionComputer<Annotation, HashSet<Annotation>>{
		private double threshold;
		private MatchRelation<Annotation> am;
		private List<List<Pair<Annotation, Double>>> candidateAndPreds;
		private List<HashSet<Annotation>> gold;
			
		public  AnnotationSetSolutionComputer(WikipediaInterface wikiApi, double threshold,
				List<List<Pair<Annotation, Double>>> candidateAndPreds,
		        List<HashSet<Annotation>> gold) {
			this.threshold = threshold;
			this.am = new StrongAnnotationMatch(wikiApi);
			this.candidateAndPreds = candidateAndPreds;
			this.gold = gold;
		}
		
		@Override
		public MetricsResultSet getResults() throws IOException {
			
			List<HashSet<Annotation>> solutionList = new Vector<>();
			for (List<Pair<Annotation, Double>> candidateAndPred : candidateAndPreds) {
				HashSet<Annotation> computedSolution = ProblemReduction.Sa2WToA2W(IndividualAnnotationLinkBack.getResult(candidateAndPred, threshold));
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
	}

	public static class GreedySolutionComputer extends SolutionComputer<Annotation, HashSet<Annotation>>{
		private double threshold;
		private MatchRelation<Annotation> am;
		private List<HashSet<Annotation>> greedyPartialSolutions;
		private List<HashSet<Annotation>> gold;
		private List<List<Pair<Annotation, Double>>> candidateAndPreds;
			
		public  GreedySolutionComputer(WikipediaInterface wikiApi, double threshold, List<HashSet<Annotation>> greedyPartialSolutions,
				List<List<Pair<Annotation, Double>>> candidateAndPreds,
				List<HashSet<Annotation>> gold){
			this.threshold = threshold;
			this.am = new StrongAnnotationMatch(wikiApi);
			this.greedyPartialSolutions = greedyPartialSolutions;
			this.candidateAndPreds = candidateAndPreds;
			this.gold = gold;
		}
		
		@Override
		public MetricsResultSet getResults() throws IOException {
			
			List<HashSet<Annotation>> solutionList = new Vector<>();
			for (int i=0; i<gold.size(); i++) {
				List<Pair<Annotation, Double>> candidateAndPred = candidateAndPreds.get(i);
				HashSet<Annotation> computedSolution = new HashSet<>(greedyPartialSolutions.get(i));
				if (!candidateAndPred.isEmpty()) {
					Pair<Annotation, Double> highestPred = candidateAndPred.stream()
					        .max(new SmaphUtils.ComparePairsBySecondElement<Annotation, Double>()).get();
					if (highestPred.second >= threshold)
						computedSolution.add(highestPred.first);
				}
				solutionList.add(computedSolution);
			}

            if (gold.size() != solutionList.size())
                throw new RuntimeException();
			Metrics<Annotation> m = new Metrics<>();
			return m.getResult(solutionList, gold, am);
		}

		public static double candidateScoreStatic(HashSet<Annotation> partialSolution, Annotation candidate,
		        HashSet<Annotation> gold, MatchRelation<Annotation> am) {
			HashSet<Annotation> newSolution = new HashSet<>(partialSolution);
			newSolution.add(candidate);
			Metrics<Annotation> m = new Metrics<>();
			return m.getSingleF1(gold, newSolution, am) - m.getSingleF1(gold, partialSolution, am);
		}
	}

	public static class BindingSolutionComputer extends SolutionComputer<HashSet<Annotation>, HashSet<Annotation>> {
		private MatchRelation<Annotation> sam;
		private List<List<Pair<HashSet<Annotation>, Double>>> candidateAndPreds;
		private List<HashSet<Annotation>> gold;

		public  BindingSolutionComputer(WikipediaInterface wikiApi,
				List<List<Pair<HashSet<Annotation>, Double>>> candidateAndPreds,
				List<HashSet<Annotation>> gold){
			this.sam = new StrongAnnotationMatch(wikiApi);
			this.gold = gold;
			this.candidateAndPreds = candidateAndPreds;
		}
		
		@Override
		public MetricsResultSet getResults() throws IOException {
			
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
			return m.getResult(solutionList, gold, sam);
		}
	}
}
