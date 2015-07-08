package it.unipi.di.acube.smaph.learn.featurePacks;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.wikiAnchors.EntityToAnchors;

import java.util.*;

import org.tartarus.snowball.ext.EnglishStemmer;

/**
 * This feature pack represents a set of feature of an annotation, based on the
 * number of tokens covered and the edit distance towards bolds and anchors.
 *
 */
public class AdvancedAnnotationFeaturePack extends FeaturePack<Annotation> {
	private static String[] ftrNames = null;
	
	private static final long serialVersionUID = 1L;

	public AdvancedAnnotationFeaturePack(Annotation a, String query, List<Pair<String, Integer>> anchorAndOccurrencies,/*, EnglishStemmer stemmer,
			HashMap<Tag, String[]> entityToBolds,
			HashMap<Tag, String[]> entityToAnchors,
			String entityTitle*/
			HashMap<String, Double> entityFeatures
			) {
		super(getFeaturesStatic(a, query, anchorAndOccurrencies, entityFeatures));
	}
	
	public AdvancedAnnotationFeaturePack() {
		super(null);
	}

	private static HashMap<String, Double> getFeaturesStatic(Annotation a, String query, List<Pair<String, Integer>> anchorAndOccurrencies, HashMap<String, Double> entityFeatures) {
		String mention = query.substring(a.getPosition(), a.getPosition() + a.getLength());
		
		HashMap<String, Double> features = new HashMap<String, Double>(entityFeatures);
		features.put("edit_distance_anchor_segment_sqrt", edAnchorsWeightSqrt(mention, anchorAndOccurrencies));
		features.put("min_edit_distance_anchor_segment_sqrt", minEdAnchorsWeightSqrt(mention, anchorAndOccurrencies));
		features.put("min_edit_distance_anchor_segment_vv_sqrt", minEdAnchorsWeightSqrtVV(mention, anchorAndOccurrencies));
		features.put("edit_distance_anchor_segment_sqrt_comm", edAnchorsWeightSqrtComm(mention, anchorAndOccurrencies, a.getConcept()));
		features.put("min_edit_distance_anchor_segment_sqrt_comm", minEdAnchorsWeightSqrtComm(mention, anchorAndOccurrencies, a.getConcept()));
		features.put("min_edit_distance_anchor_segment_vv_sqrt_comm", minEdAnchorsWeightSqrtVVComm(mention, anchorAndOccurrencies, a.getConcept()));
		//features.put("edit_distance_anchor_segment_sqrt_swapped", edAnchorsWeightSqrtSwapped(mention, anchorAndOccurrencies));
		features.put("random", Math.random());
		
		return features;
	}

	private static double edAnchorsWeightSqrt(String segmentStr, List<Pair<String, Integer>> anchorAndOccurrencies) {
		double num = 0;
		double denom = 0;
		for (Pair<String, Integer> p: anchorAndOccurrencies){
			num += Math.sqrt(p.second)*SmaphUtils.getNormEditDistance(segmentStr.toLowerCase(), p.first);
			denom += Math.sqrt(p.second);
		}
		return num/denom;
	}
	private static double minEdAnchorsWeightSqrt(String segmentStr, List<Pair<String, Integer>> anchorAndOccurrencies) {
		double num = 0;
		double denom = 0;
		for (Pair<String, Integer> p: anchorAndOccurrencies){
			num += Math.sqrt(p.second)*SmaphUtils.getMinEditDist(segmentStr.toLowerCase(), p.first);
			denom += Math.sqrt(p.second);
		}
		return num/denom;
	}
	
	private static double minEdAnchorsWeightSqrtVV(String segmentStr, List<Pair<String, Integer>> anchorAndOccurrencies) {
		double num = 0;
		double denom = 0;
		for (Pair<String, Integer> p: anchorAndOccurrencies){
			num += Math.sqrt(p.second)*(SmaphUtils.getMinEditDist(p.first, segmentStr.toLowerCase() + SmaphUtils.getMinEditDist(segmentStr.toLowerCase(), p.first)));
			denom += Math.sqrt(p.second);
		}
		return num/denom;
	}

	private static double edAnchorsWeightSqrtComm(String segmentStr, List<Pair<String, Integer>> anchorAndOccurrencies, int entity) {
		double num = 0;
		double denom = 0;
		for (Pair<String, Integer> p: anchorAndOccurrencies){
			num += Math.sqrt(EntityToAnchors.e2a().getCommonness(p.first, entity))*Math.sqrt(p.second)*SmaphUtils.getNormEditDistance(segmentStr.toLowerCase(), p.first);
			denom += Math.sqrt(EntityToAnchors.e2a().getCommonness(p.first, entity))*Math.sqrt(p.second);
		}
		return num/denom;
	}
	private static double minEdAnchorsWeightSqrtComm(String segmentStr, List<Pair<String, Integer>> anchorAndOccurrencies, int entity) {
		double num = 0;
		double denom = 0;
		for (Pair<String, Integer> p: anchorAndOccurrencies){
			num += Math.sqrt(EntityToAnchors.e2a().getCommonness(p.first, entity))*Math.sqrt(p.second)*SmaphUtils.getMinEditDist(segmentStr.toLowerCase(), p.first);
			denom += Math.sqrt(EntityToAnchors.e2a().getCommonness(p.first, entity))*Math.sqrt(p.second);
		}
		return num/denom;
	}
	
	private static double minEdAnchorsWeightSqrtVVComm(String segmentStr, List<Pair<String, Integer>> anchorAndOccurrencies, int entity) {
		double num = 0;
		double denom = 0;
		for (Pair<String, Integer> p: anchorAndOccurrencies){
			num += Math.sqrt(EntityToAnchors.e2a().getCommonness(p.first, entity))*Math.sqrt(p.second)*(SmaphUtils.getMinEditDist(p.first, segmentStr.toLowerCase() + SmaphUtils.getMinEditDist(segmentStr.toLowerCase(), p.first)));
			denom += Math.sqrt(EntityToAnchors.e2a().getCommonness(p.first, entity))*Math.sqrt(p.second);
		}
		return num/denom;
	}

	@Override
	public String[] getFeatureNames() {
		return getFeatureNamesStatic();
	}

	private static String[] getFeatureNamesStatic() {
		if (ftrNames == null) {
			Vector<String> v = new Vector<String>();
			v.add("random");
			v.add("edit_distance_anchor_segment_sqrt");
			v.add("min_edit_distance_anchor_segment_sqrt");
			v.add("min_edit_distance_anchor_segment_vv_sqrt");
			v.add("edit_distance_anchor_segment_sqrt_comm");
			v.add("min_edit_distance_anchor_segment_sqrt_comm");
			v.add("min_edit_distance_anchor_segment_vv_sqrt_comm");
			v.addAll(Arrays.asList(EntityFeaturePack.ftrNames));

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
