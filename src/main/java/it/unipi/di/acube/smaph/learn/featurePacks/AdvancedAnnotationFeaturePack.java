package it.unipi.di.acube.smaph.learn.featurePacks;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.wikiAnchors.EntityToAnchors;

import java.io.IOException;
import java.util.*;

/**
 * This feature pack represents a set of feature of an annotation, based on the
 * number of tokens covered and the edit distance towards bolds and anchors.
 *
 */
public class AdvancedAnnotationFeaturePack extends FeaturePack<Annotation> {
	private static String[] ftrNames = null;
	
	private static final long serialVersionUID = 1L;

	public AdvancedAnnotationFeaturePack(Annotation a, String query,
			QueryInformation qi, WikipediaApiInterface wikiApi) {
		super(getFeaturesStatic(a, query, qi, wikiApi));
	}
	
	public AdvancedAnnotationFeaturePack() {
		super(null);
	}

	public static HashMap<String, Double> getFeaturesStatic(Annotation a, String query, QueryInformation qi, WikipediaApiInterface wikiApi) {
		String mention = query.substring(a.getPosition(), a.getPosition() + a.getLength());
		List<Pair<String, Integer>> anchorAndOccurrencies = EntityToAnchors.e2a().getAnchors(a.getConcept());
		if (qi.entityToFtrVects.get(new Tag(a.getConcept())).size() != 1)
			throw new RuntimeException("Multiple feature vectors for same entity not supported.");
		HashMap<String, Double> entityFeatures = qi.entityToFtrVects.get(new Tag(a.getConcept())).get(0);
		List<String> bolds = qi.tagToBoldsS6.get(new Tag(a.getConcept()));

		String title;
		try {
			title = wikiApi.getTitlebyId(a.getConcept());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		HashMap<String, Double> features = new HashMap<String, Double>(entityFeatures);
		features.put("edit_distance_anchor_segment_sqrt", edAnchorsWeightSqrt(mention, anchorAndOccurrencies));
		features.put("min_edit_distance_anchor_segment_sqrt", minEdAnchorsWeightSqrt(mention, anchorAndOccurrencies));
		features.put("min_edit_distance_anchor_segment_vv_sqrt", minEdAnchorsWeightSqrtVV(mention, anchorAndOccurrencies));
		features.put("edit_distance_anchor_segment_sqrt_comm", edAnchorsWeightSqrtComm(mention, anchorAndOccurrencies, a.getConcept()));
		features.put("min_edit_distance_anchor_segment_sqrt_comm", minEdAnchorsWeightSqrtComm(mention, anchorAndOccurrencies, a.getConcept()));
		features.put("min_edit_distance_anchor_segment_vv_sqrt_comm", minEdAnchorsWeightSqrtVVComm(mention, anchorAndOccurrencies, a.getConcept()));
		//features.put("edit_distance_anchor_segment_sqrt_swapped", edAnchorsWeightSqrtSwapped(mention, anchorAndOccurrencies));
		features.put("min_edit_distance_title", minEdTitle(mention, title));
		features.put("min_edit_distance_bolds", minEdBold(mention, bolds));
		return features;
	}

	public static double minEdTitle(String mention, String title) {
		double minEDTitle = 1.0;
		return Math.min(SmaphUtils.getMinEditDist(title, mention), minEDTitle);
	}

	public static double minEdBold(String mention, List<String> bolds) {
		double minEDBold = 1.0;
		for (String bold : bolds) {
			minEDBold = Math.min(SmaphUtils.getMinEditDist(mention, bold),
					minEDBold);
			minEDBold = Math.min(SmaphUtils.getMinEditDist(bold, mention),
					minEDBold);
		}
		return minEDBold;
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
			num += Math.sqrt(EntityToAnchors.e2a().getCommonness(p.first, entity, p.second))*SmaphUtils.getNormEditDistance(segmentStr.toLowerCase(), p.first);
			denom += Math.sqrt(EntityToAnchors.e2a().getCommonness(p.first, entity, p.second));
		}
		return num/denom;
	}
	private static double minEdAnchorsWeightSqrtComm(String segmentStr, List<Pair<String, Integer>> anchorAndOccurrencies, int entity) {
		double num = 0;
		double denom = 0;
		for (Pair<String, Integer> p: anchorAndOccurrencies){
			num += Math.sqrt(EntityToAnchors.e2a().getCommonness(p.first, entity, p.second))*SmaphUtils.getMinEditDist(segmentStr.toLowerCase(), p.first);
			denom += Math.sqrt(EntityToAnchors.e2a().getCommonness(p.first, entity, p.second));
		}
		return num/denom;
	}
	
	private static double minEdAnchorsWeightSqrtVVComm(String segmentStr, List<Pair<String, Integer>> anchorAndOccurrencies, int entity) {
		double num = 0;
		double denom = 0;
		for (Pair<String, Integer> p: anchorAndOccurrencies){
			num += Math.sqrt(EntityToAnchors.e2a().getCommonness(p.first, entity, p.second))*(SmaphUtils.getMinEditDist(p.first, segmentStr.toLowerCase() + SmaphUtils.getMinEditDist(segmentStr.toLowerCase(), p.first)));
			denom += Math.sqrt(EntityToAnchors.e2a().getCommonness(p.first, entity, p.second));
		}
		return num/denom;
	}

	@Override
	public String[] getFeatureNames() {
		return getFeatureNamesStatic();
	}

	public static String[] getFeatureNamesStatic() {
		if (ftrNames == null) {
			Vector<String> v = new Vector<String>();
			v.addAll(Arrays.asList(EntityFeaturePack.ftrNames));
			v.add("edit_distance_anchor_segment_sqrt");
			v.add("min_edit_distance_anchor_segment_sqrt");
			v.add("min_edit_distance_anchor_segment_vv_sqrt");
			v.add("edit_distance_anchor_segment_sqrt_comm");
			v.add("min_edit_distance_anchor_segment_sqrt_comm");
			v.add("min_edit_distance_anchor_segment_vv_sqrt_comm");
			v.add("min_edit_distance_title");
			v.add("min_edit_distance_bolds");

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
