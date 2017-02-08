package it.unipi.di.acube.smaph.learn.featurePacks;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import org.apache.commons.lang3.tuple.Triple;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;

/**
 * This feature pack represents a set of feature of an annotation, based on the
 * number of tokens covered and the edit distance towards bolds and anchors.
 *
 */
public class GreedyFeaturePack extends FeaturePack<Annotation> {
	private static String[] ftrNames = null;
	
	private static final long serialVersionUID = 1L;

	public <A extends Annotation> GreedyFeaturePack(Annotation a, String query, QueryInformation qi, HashSet<A> partialSolution,
	        WikipediaInterface wikiApi, WikipediaToFreebase w2f, EntityToAnchors e2a) {
		super(getFeaturesStatic(a, query, qi, partialSolution, wikiApi, w2f, e2a));
	}
	
	public GreedyFeaturePack() {
		super(null);
	}
	
	public static <A extends Annotation> HashMap<String, Double> getFeaturesStatic(Annotation a, String query,
	        QueryInformation qi, HashSet<A> partialSolution, WikipediaInterface wikiApi, WikipediaToFreebase w2f,
	        EntityToAnchors e2a) {
		HashMap<String, Double> annotationFeatures = AnnotationFeaturePack.getFeaturesStatic(a, query, qi, wikiApi, w2f, e2a);

		int queryTokens = SmaphUtils.tokenize(query).size();

		int coveredTokensBefore = 0;
		for (Annotation aS : partialSolution)
			coveredTokensBefore += SmaphUtils.tokenize(query.substring(aS.getPosition(), aS.getPosition() + aS.getLength()))
			        .size();

		int coveredTokensAnnotation = SmaphUtils.tokenize(query.substring(a.getPosition(), a.getPosition() + a.getLength()))
		        .size();

		// Features: coverage
		annotationFeatures.put("covered_tokens_incr", (double) coveredTokensAnnotation);
		annotationFeatures.put("covered_tokens_incr_ratio", ((double) coveredTokensAnnotation) / queryTokens);
		annotationFeatures.put("covered_tokens_after", (double) coveredTokensAnnotation + coveredTokensBefore);

		Pair<Double, Double> lpSumAndAvg = BindingFeaturePack.getLpSumAvg(query);
		annotationFeatures.put("segments_lp_sum", lpSumAndAvg.first);
		annotationFeatures.put("segments_lp_avg", lpSumAndAvg.second);
		annotationFeatures.put("segments_lp_ratio", lpSumAndAvg.first / (partialSolution.size() + 1));
		
		if (partialSolution.size() >= 1) {
			// Features: Annotation -- partial solution
			Vector<Double> relatednessPairsJaccardBefore = new Vector<>();
			Vector<Double> relatednessPairsMWBefore = new Vector<>();
			for (Annotation a1 : partialSolution)
				for (Annotation a2 : partialSolution)
					if (a1 != a2) {
						relatednessPairsJaccardBefore
						.add(WATRelatednessComputer.getJaccardRelatedness(a1.getConcept(), a2.getConcept()));
						relatednessPairsMWBefore.add(WATRelatednessComputer.getMwRelatedness(a1.getConcept(), a2.getConcept()));
					}

			Triple<Double, Double, Double> minMaxAvgRelJaccardPS = SmaphUtils.getMinMaxAvg(relatednessPairsJaccardBefore);
			double minRelBefore = minMaxAvgRelJaccardPS.getLeft();
			double maxRelBefore = minMaxAvgRelJaccardPS.getMiddle();
			double avgRelBefore = minMaxAvgRelJaccardPS.getRight();
			annotationFeatures.put("max_relatedness_before", maxRelBefore);
			annotationFeatures.put("avg_relatedness_before", avgRelBefore);

			Triple<Double, Double, Double> minMaxAvgRelMWPS = SmaphUtils.getMinMaxAvg(relatednessPairsMWBefore);
			double minRelMwBefore = minMaxAvgRelMWPS.getLeft();
			double maxRelMwBefore = minMaxAvgRelMWPS.getMiddle();
			annotationFeatures.put("max_relatedness_mw_before", maxRelMwBefore);

			// Features: partial solution
			Vector<Double> relatednessPairsJaccardThisA = new Vector<>();
			Vector<Double> relatednessPairsMWThisA = new Vector<>();
			for (Annotation aS : partialSolution) {
				relatednessPairsJaccardThisA.add(WATRelatednessComputer.getJaccardRelatedness(a.getConcept(), aS.getConcept()));
				relatednessPairsMWThisA.add(WATRelatednessComputer.getMwRelatedness(a.getConcept(), aS.getConcept()));
			}

			Triple<Double, Double, Double> minMaxAvgRelJaccard = SmaphUtils.getMinMaxAvg(relatednessPairsJaccardThisA);
			double minRel = minMaxAvgRelJaccard.getLeft();
			double avgRel = minMaxAvgRelJaccard.getRight();

			Triple<Double, Double, Double> minMaxAvgRelMW = SmaphUtils.getMinMaxAvg(relatednessPairsMWThisA);
			double minRelMW = minMaxAvgRelMW.getLeft();
			double maxRelMW = minMaxAvgRelMW.getMiddle();
			annotationFeatures.put("min_relatedness_mw", minRelMW);
			annotationFeatures.put("max_relatedness_mw", maxRelMW);
			
			// Features: difference wrt partial solution
			double newMinRelatedness = Math.min(minRel, minRelBefore);
			double newMinRelatednessMw = Math.min(minRelMW, minRelMwBefore);
			double newMaxRelatednessMw = Math.max(maxRelMW, maxRelMwBefore);
			annotationFeatures.put("min_relatedness_diff", newMinRelatedness - minRelBefore);
			annotationFeatures.put("avg_relatedness_diff", avgRel - avgRelBefore);
			annotationFeatures.put("min_relatedness_mw_diff", newMinRelatednessMw - minRelMwBefore);
			annotationFeatures.put("max_relatedness_mw_diff", newMaxRelatednessMw - maxRelMwBefore);
		} else {
			// In case the partial solution is empty (first step), these features get the same values and will be discarded.
			annotationFeatures.put("max_relatedness_before", 0.0);
			annotationFeatures.put("avg_relatedness_before", 0.0);
			annotationFeatures.put("max_relatedness_mw_before", 0.0);
			annotationFeatures.put("min_relatedness_mw", 0.0);
			annotationFeatures.put("max_relatedness_mw", 0.0);
			annotationFeatures.put("min_relatedness_diff", 0.0);
			annotationFeatures.put("avg_relatedness_diff", 0.0);
			annotationFeatures.put("min_relatedness_mw_diff", 0.0);
			annotationFeatures.put("max_relatedness_mw_diff", 0.0);
		}

		return annotationFeatures;
	}

	@Override
	public String[] getFeatureNames() {
		return getFeatureNamesStatic();
	}

	public static String[] getFeatureNamesStatic() {
		if (ftrNames == null) {
			Vector<String> v = new Vector<String>();
			v.addAll(Arrays.asList(AnnotationFeaturePack.getFeatureNamesStatic()));
			v.add("covered_tokens_incr");
			v.add("covered_tokens_incr_ratio");
			v.add("covered_tokens_after");
			v.add("segments_lp_sum");
			v.add("segments_lp_avg");
			v.add("segments_lp_ratio");
			v.add("max_relatedness_before");
			v.add("avg_relatedness_before");
			v.add("max_relatedness_mw_before");
			v.add("min_relatedness_mw");
			v.add("max_relatedness_mw");
			v.add("min_relatedness_diff");
			v.add("avg_relatedness_diff");
			v.add("min_relatedness_mw_diff");
			v.add("max_relatedness_mw_diff");
			ftrNames = v.toArray(new String[] {});
		}
		return ftrNames;
	}

	@Override
	public void checkFeatures(HashMap<String, Double> features) {
		String[] ftrNames = getFeatureNames();
		for (String ftrName : features.keySet())
			if (!Arrays.asList(ftrNames).contains(ftrName))
				throw new RuntimeException("Feature " + ftrName
						+ " does not exist!");
	}
}
