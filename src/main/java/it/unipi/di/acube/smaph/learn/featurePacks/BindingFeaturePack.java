package it.unipi.di.acube.smaph.learn.featurePacks;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.WATRelatednessComputer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.lang3.tuple.Triple;

public class BindingFeaturePack extends FeaturePack<HashSet<Annotation>> {
	private static final long serialVersionUID = 1L;
	private static String[] ftrNames = null;
	
	public BindingFeaturePack(
			HashSet<Annotation> binding, String query,
			QueryInformation qi, Set<Tag> acceptedEntities, WikipediaApiInterface wikiApi, HashMap<Annotation, HashMap<String, Double>> debugAnnotationFeatures, HashMap<String, Double> debugBindingFeatures){
		super(getFeatures(binding, query, qi, acceptedEntities, wikiApi, debugAnnotationFeatures, debugBindingFeatures));
	}

	public BindingFeaturePack() {
		super(null);
	}

	@Override
	public String[] getFeatureNames() {
		return getFeatureNamesStatic();
	}

	public static String[] getFeatureNamesStatic() {
		if (ftrNames == null) {
			List<String> ftrNamesVect = new Vector<>();
			ftrNamesVect.add("min_relatedness");
			ftrNamesVect.add("max_relatedness");
			ftrNamesVect.add("avg_relatedness");

			ftrNamesVect.add("query_tokens");
			ftrNamesVect.add("annotation_count");
			ftrNamesVect.add("covered_tokens");

			for (String ftrName : AdvancedAnnotationFeaturePack
					.getFeatureNamesStatic())
				if (ftrName.startsWith("is_")) {
					ftrNamesVect.add("count_" + ftrName);
				} else {
					ftrNamesVect.add("min_" + ftrName);
					ftrNamesVect.add("max_" + ftrName);
					ftrNamesVect.add("avg_" + ftrName);
				}

			ftrNamesVect.add("min_relatedness_mw");
			ftrNamesVect.add("max_relatedness_mw");
			ftrNamesVect.add("avg_relatedness_mw");
			ftrNamesVect.add("segments_lp_sum");
			ftrNamesVect.add("segments_lp_avg");

			ftrNames = ftrNamesVect.toArray(new String[] {});
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

	

	/**
	 * Given a list of feature vectors, return a single
	 * feature vector (in hashmap form). This vector will contain the max,
	 * min, and avg of for [0,1] features and the sum for counting features.
	 * 
	 * @param allFtrVects
	 * @return a single representation
	 */
	private static HashMap<String, Double> collapseFeatures(
			List<HashMap<String, Double>> allFtrVects) {
		// count feature presence
		HashMap<String, Integer> ftrCount = new HashMap<>();
		for (HashMap<String, Double> ftrVectToMerge : allFtrVects)
			for (String ftrName : ftrVectToMerge.keySet()) {
				if (!ftrCount.containsKey(ftrName))
					ftrCount.put(ftrName, 0);
				ftrCount.put(ftrName, ftrCount.get(ftrName) + 1);
			}

		// compute min, max, avg, count
		HashMap<String, Double> entitySetFeatures = new HashMap<>();
		for (HashMap<String, Double> ftrVectToMerge : allFtrVects) {
			for (String ftrName : ftrVectToMerge.keySet()) {
				if (ftrName.startsWith("is_")) {
					String key = "count_" + ftrName;
					if (!entitySetFeatures.containsKey(key))
						entitySetFeatures.put(key, 0.0);
					entitySetFeatures.put(key, entitySetFeatures.get(key) + ftrVectToMerge.get(ftrName));
				} else {
					if (!entitySetFeatures.containsKey("min_" + ftrName))
						entitySetFeatures.put("min_" + ftrName,
								Double.POSITIVE_INFINITY);
					if (!entitySetFeatures.containsKey("max_" + ftrName))
						entitySetFeatures.put("max_" + ftrName,
								Double.NEGATIVE_INFINITY);
					if (!entitySetFeatures.containsKey("avg_" + ftrName))
						entitySetFeatures.put("avg_" + ftrName, 0.0);

					double ftrValue = ftrVectToMerge.get(ftrName);
					entitySetFeatures.put("min_" + ftrName, Math.min(
							entitySetFeatures.get("min_" + ftrName), ftrValue));
					entitySetFeatures.put("max_" + ftrName, Math.max(
							entitySetFeatures.get("max_" + ftrName), ftrValue));
					entitySetFeatures.put("avg_" + ftrName,
							entitySetFeatures.get("avg_" + ftrName) + ftrValue
									/ ftrCount.get(ftrName));
				}
			}
		}
		return entitySetFeatures;
	}

	private static HashMap<String, Double> getFeatures(
			HashSet<Annotation> binding,
			String query,
			QueryInformation qi, Set<Tag> acceptedEntities, WikipediaApiInterface wikiApi, HashMap<Annotation, HashMap<String, Double>> debugAnnotationFeatures, HashMap<String, Double> debugBindingFeatures) {
		
		List<HashMap<String, Double>> allAnnotationsFeatures = new Vector<>();
		
		for (Annotation ann : binding) {
			HashMap<String, Double> annFeatures = AdvancedAnnotationFeaturePack
					.getFeaturesStatic(ann, query, qi, wikiApi);
			allAnnotationsFeatures.add(annFeatures);
			if (debugAnnotationFeatures != null)
				debugAnnotationFeatures.put(ann, annFeatures);
		}

/*		HashSet<Tag> selectedEntities = new HashSet<>();
		for (Annotation ann : binding)
			selectedEntities.add(new Tag(ann.getConcept()));
		
		List<HashMap<String, Double>> allEntitiesFeatures = new Vector<>();
		for (Tag t : selectedEntities)
			allEntitiesFeatures.addAll(qi.entityToFtrVects.get(t));
		
*/
		HashMap<String, Double> bindingFeatures = collapseFeatures(
				allAnnotationsFeatures);

		/*Vector<Double> mutualInfos = new Vector<>();
		for (Annotation a : binding) {
			Tag t = new Tag(a.getConcept());
			double minED = 1.0;
			if (entitiesToBolds.containsKey(t))
				for (String bold : entitiesToBolds.get(t))
					minED = Math.min(SmaphUtils.getMinEditDist(
							query.substring(a.getPosition(), a.getPosition()
									+ a.getLength()), bold), minED);
			mutualInfos.add(mutualInfo);
		}
		*/

		/*Triple<Double, Double, Double> minMaxAvgMI = getMinMaxAvg(mutualInfos);
		features.put("min_mutual_info", minMaxAvgMI.getLeft());
		features.put("avg_mutual_info", minMaxAvgMI.getRight());
		features.put("max_mutual_info", minMaxAvgMI.getMiddle());*/

		bindingFeatures.put("query_tokens", (double) SmaphUtils.tokenize(query).size());
		bindingFeatures.put("annotation_count", (double) binding.size());

		int coveredTokens = 0;
		for (Annotation a: binding)
			coveredTokens += SmaphUtils.tokenize(query.substring(a.getPosition(), a.getPosition()+a.getLength())).size();
		bindingFeatures.put("covered_tokens", (double)coveredTokens/(double) SmaphUtils.tokenize(query).size());
		
		/* Add relatedness among entities (only if there are more than two entities)*/
		Vector<Double> relatednessPairsJaccard = new Vector<>();
		Vector<Double> relatednessPairsMW = new Vector<>();
		for (Annotation a1 : binding)
			for (Annotation a2 : binding)
				if (a1.getConcept() != a2.getConcept()){
					relatednessPairsJaccard.add(WATRelatednessComputer.getJaccardRelatedness(a1.getConcept(), a2.getConcept()));
					relatednessPairsMW.add(WATRelatednessComputer.getMwRelatedness(a1.getConcept(), a2.getConcept()));
				}
		if (relatednessPairsJaccard.size() >= 1){
			Triple<Double, Double, Double> minMaxAvgRelJaccard = SmaphUtils.getMinMaxAvg(relatednessPairsJaccard);
			bindingFeatures.put("min_relatedness", minMaxAvgRelJaccard.getLeft());
			bindingFeatures.put("max_relatedness", minMaxAvgRelJaccard.getMiddle());
			bindingFeatures.put("avg_relatedness", minMaxAvgRelJaccard.getRight());

			Triple<Double, Double, Double> minMaxAvgRelMW = SmaphUtils.getMinMaxAvg(relatednessPairsMW);
			bindingFeatures.put("min_relatedness_mw", minMaxAvgRelMW.getLeft());
			bindingFeatures.put("max_relatedness_mw", minMaxAvgRelMW.getMiddle());
			bindingFeatures.put("avg_relatedness_mw", minMaxAvgRelMW.getRight());
		}
		
		Pair<Double, Double> lpSumAndAvg = getLpSumAvg(query);
		bindingFeatures.put("segments_lp_sum", lpSumAndAvg.first);
		bindingFeatures.put("segments_lp_avg", lpSumAndAvg.second);
		
		
		if (debugBindingFeatures != null)
			debugBindingFeatures.putAll(bindingFeatures);
		
		return bindingFeatures;
	}

	private static Pair<Double, Double> getLpSumAvg(String query) {
		List<String> segments = SmaphUtils.findSegmentsStrings(query);
		double sum = 0;
		int count = 0;
		for (String s : segments) {
			sum += WATRelatednessComputer.getLp(s);
			count++;
		}
		return new Pair<Double, Double>(sum, sum / count);
	}
}
