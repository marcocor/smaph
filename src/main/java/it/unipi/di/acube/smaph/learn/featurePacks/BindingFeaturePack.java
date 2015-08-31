package it.unipi.di.acube.smaph.learn.featurePacks;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.WATRelatednessComputer;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;

public class BindingFeaturePack extends FeaturePack<HashSet<Annotation>> {
	private static final long serialVersionUID = 1L;

	public BindingFeaturePack(
			HashSet<Annotation> binding, String query,
			HashMap<Tag, String[]> entitiesToBolds,HashMap<Tag, String> entityToTitle,
			HashMap<Tag, List<HashMap<String, Double>>> entityToFeatureVectors, HashMap<Annotation,Double> annotationRegressorScores){
		super(getFeatures(binding, query, entitiesToBolds, entityToTitle, entityToFeatureVectors, annotationRegressorScores));
	}

	public BindingFeaturePack() {
		super(null);
	}

	@Override
	public String[] getFeatureNames() {
		return getFeatureNamesStatic();
	}

	public static String[] getFeatureNamesStatic() {
		List<String> ftrNames = new Vector<>();
		ftrNames.add("min_relatedness");
		ftrNames.add("max_relatedness");
		ftrNames.add("avg_relatedness");
		
		ftrNames.add("min_annotation_regressor_score");
		ftrNames.add("max_annotation_regressor_score");
		ftrNames.add("avg_annotation_regressor_score");

		ftrNames.add("min_min_edit_distance_bold");
		ftrNames.add("max_min_edit_distance_bold");
		ftrNames.add("avg_min_edit_distance_bold");

		ftrNames.add("min_min_edit_distance_title");
		ftrNames.add("max_min_edit_distance_title");
		ftrNames.add("avg_min_edit_distance_title");

		ftrNames.add("query_tokens");
		ftrNames.add("annotation_count");
		ftrNames.add("covered_tokens");

		for (String ftrName : EntityFeaturePack.ftrNames)
			if (/*ftrName.startsWith("s1_") || */ftrName.startsWith("s2_")
					|| ftrName.startsWith("s3_")|| ftrName.startsWith("s6_")) {
				ftrNames.add("min_" + ftrName);
				ftrNames.add("max_" + ftrName);
				ftrNames.add("avg_" + ftrName);
			}
		for (String ftrName : EntityFeaturePack.ftrNames)
			if (ftrName.startsWith("is_")) {
				ftrNames.add("count_" + ftrName);
			}

		return ftrNames.toArray(new String[] {});
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
	 * Given a list of feature vectors associated to an entity, return a single
	 * feature vector (in hashmap form). This vector will contain the max,
	 * min, and avg of for [0,1] features and the sum for counting features.
	 * 
	 * @param allFtrVects
	 * @return a single representation
	 */
	private static HashMap<String, Double> collapseEntityFeatures(
			List<HashMap<String, Double>> allFtrVects) {
		// count
		HashMap<String, Integer> ftrCount = new HashMap<>();
		for (HashMap<String, Double> ftrVectToMerge : allFtrVects)
			for (String ftrName : ftrVectToMerge.keySet()) {
				if (!ftrCount.containsKey(ftrName))
					ftrCount.put(ftrName, 0);
				ftrCount.put(ftrName, ftrCount.get(ftrName) + 1);
			}

		//min, max, avg
		HashMap<String, Double> entitySetFeatures = new HashMap<>();
		for (HashMap<String, Double> ftrVectToMerge : allFtrVects) {
			for (String ftrName : ftrVectToMerge.keySet()) {
				if (/*ftrName.startsWith("s1_") || */ftrName.startsWith("s2_")
						|| ftrName.startsWith("s6_")|| ftrName.startsWith("s3_")) {
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
							entitySetFeatures.get("avg_" + ftrName) + ftrValue / ftrCount.get(ftrName));

				} else if (ftrName.startsWith("is_")) {
					String key = "count_" + ftrName;
					if (!entitySetFeatures.containsKey(key))
						entitySetFeatures.put(key, 0.0);
					entitySetFeatures.put(key, entitySetFeatures.get(key) + 1);
				}
			}
		}
		return entitySetFeatures;
	}

	private static HashMap<String, Double> getFeatures(
			HashSet<Annotation> binding,
			String query,
			HashMap<Tag, String[]> entitiesToBolds,
			HashMap<Tag, String> entityToTitle,
			HashMap<Tag, List<HashMap<String, Double>>> entityToFeatureVectors,
			HashMap<Annotation, Double> annotationRegressorScores) {
		HashSet<Tag> selectedEntities = new HashSet<>();
		for (Annotation ann : binding)
			selectedEntities.add(new Tag(ann.getConcept()));
		
		List<HashMap<String, Double>> allEntitiesFeatures = new Vector<>();
		for (Tag t : selectedEntities)
			allEntitiesFeatures.addAll(entityToFeatureVectors.get(t));
		HashMap<String, Double> bindingFeatures = collapseEntityFeatures(
				allEntitiesFeatures);

		Vector<Double> minEdsBold = new Vector<>();
		Vector<Double> minEdsTitle= new Vector<>();
		for (Annotation a : binding) {
			Tag t = new Tag(a.getConcept());
			double minEDBold = 1.0;
			if (entitiesToBolds.containsKey(t))
				for (String bold : entitiesToBolds.get(t)){
					minEDBold = Math.min(SmaphUtils.getMinEditDist(
							query.substring(a.getPosition(), a.getPosition()
									+ a.getLength()), bold), minEDBold);
					minEDBold = Math.min(SmaphUtils.getMinEditDist(bold,
							query.substring(a.getPosition(), a.getPosition()
									+ a.getLength())), minEDBold);
				}
			minEdsBold.add(minEDBold);
			
			double minEDTitle = 1.0;
			String title = entityToTitle.get(t);
			minEDTitle = Math.min(SmaphUtils.getMinEditDist(title,
							query.substring(a.getPosition(), a.getPosition()
									+ a.getLength())), minEDTitle);
				
			minEdsTitle.add(minEDTitle);
		}
		
		
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

		Triple<Double, Double, Double> minMaxAvgEDBold = SmaphUtils.getMinMaxAvg(minEdsBold);
		bindingFeatures.put("min_min_edit_distance_bold", minMaxAvgEDBold.getLeft());
		bindingFeatures.put("max_min_edit_distance_bold", minMaxAvgEDBold.getMiddle());
		bindingFeatures.put("avg_min_edit_distance_bold", minMaxAvgEDBold.getRight());
		
		Triple<Double, Double, Double> minMaxAvgEDTitle = SmaphUtils.getMinMaxAvg(minEdsTitle);
		bindingFeatures.put("min_min_edit_distance_title", minMaxAvgEDTitle.getLeft());
		bindingFeatures.put("max_min_edit_distance_title", minMaxAvgEDTitle.getMiddle());
		bindingFeatures.put("avg_min_edit_distance_title", minMaxAvgEDTitle.getRight());

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
		
		/* Add features derived from the Annotation Regressor */
		if (annotationRegressorScores != null /*&& binding.size() >= 2*/) {
			List<Double> scores = new Vector<>();
			for (Annotation ann : binding)
				scores.add(annotationRegressorScores.get(ann));

			Triple<Double, Double, Double> minMaxAvgAcores = SmaphUtils.getMinMaxAvg(scores);
			bindingFeatures.put("min_annotation_regressor_score",
					minMaxAvgAcores.getLeft());
			bindingFeatures.put("max_annotation_regressor_score",
					minMaxAvgAcores.getMiddle());
			bindingFeatures.put("avg_annotation_regressor_score",
					minMaxAvgAcores.getRight());
		}
		
		/* Add relatedness among entities (only if there are more than two entities)*/
		Vector<Double> relatednessPairs = new Vector<>();
		for (Annotation a1 : binding)
			for (Annotation a2 : binding)
				if (a1.getConcept() != a2.getConcept())
					relatednessPairs.add(WATRelatednessComputer.getRelatedness(a1.getConcept(), a2.getConcept()));
		if (relatednessPairs.size() >= 1) {
			Triple<Double, Double, Double> minMaxAvgRel = SmaphUtils
					.getMinMaxAvg(relatednessPairs);

			bindingFeatures.put("min_relatedness", minMaxAvgRel.getLeft());
			bindingFeatures.put("max_relatedness", minMaxAvgRel.getMiddle());
			bindingFeatures.put("avg_relatedness", minMaxAvgRel.getRight());
		}
		
		return bindingFeatures;
	}
	
	
}
