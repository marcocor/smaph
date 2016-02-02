package it.unipi.di.acube.smaph.learn.featurePacks;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.EntityToVect;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.wikiAnchors.EntityToAnchors;

import java.io.IOException;
import java.net.URLEncoder;
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
		Tag entity = new Tag(a.getConcept());
		String mention = query.substring(a.getPosition(), a.getPosition() + a.getLength());
		List<Pair<String, Integer>> anchorAndOccurrencies = EntityToAnchors.e2a().getAnchors(a.getConcept());
		HashMap<String, Double> entityFeatures = EntityFeaturePack.getFeatures(entity, query, qi, wikiApi);
		List<String> bolds = null;
		if (qi.entityToBoldsSA.containsKey(entity))
			bolds = qi.entityToBoldsSA.get(entity);

		String title, urlEncodedTitle;
		try {
			title = wikiApi.getTitlebyId(a.getConcept());
			urlEncodedTitle = URLEncoder.encode(title.replace(" ", "_"), "utf-8");
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		List<String> queryKeywords = SmaphUtils.tokenize(query.replaceAll("[^a-zA-Z0-9]", " ").toLowerCase());
		List<String> mentionKeywords = SmaphUtils.tokenize(mention.replaceAll("[^a-zA-Z0-9]", " ").toLowerCase());
		
		HashMap<String, Double> features = new HashMap<String, Double>(entityFeatures);
		features.put("edit_distance_anchor_segment_sqrt", edAnchorsWeightSqrt(mention, anchorAndOccurrencies));
		features.put("min_edit_distance_anchor_segment_sqrt", minEdAnchorsWeightSqrt(mention, anchorAndOccurrencies));
		features.put("min_edit_distance_anchor_segment_vv_sqrt", minEdAnchorsWeightSqrtVV(mention, anchorAndOccurrencies));
		features.put("edit_distance_anchor_segment_sqrt_comm", edAnchorsWeightSqrtComm(mention, anchorAndOccurrencies, a.getConcept()));
		features.put("min_edit_distance_anchor_segment_sqrt_comm", minEdAnchorsWeightSqrtComm(mention, anchorAndOccurrencies, a.getConcept()));
		features.put("min_edit_distance_anchor_segment_vv_sqrt_comm", minEdAnchorsWeightSqrtVVComm(mention, anchorAndOccurrencies, a.getConcept()));
		features.put("min_edit_distance_anchor_segment_sqrt_geometric_0.20", minEdAnchorsWeightSqrtGeom(mention, anchorAndOccurrencies, 0.20));
		features.put("min_edit_distance_anchor_segment_sqrt_geometric_0.10", minEdAnchorsWeightSqrtGeom(mention, anchorAndOccurrencies, 0.10));
		features.put("min_edit_distance_anchor_segment_sqrt_geometric_0.05", minEdAnchorsWeightSqrtGeom(mention, anchorAndOccurrencies, 0.05));
		features.put("min_edit_distance_anchor_segment_sqrt_geometric_0.02", minEdAnchorsWeightSqrtGeom(mention, anchorAndOccurrencies, 0.02));
		features.put("min_edit_distance_title", minEdTitle(mention, title));
		features.put("edit_distance_title", (double) SmaphUtils.getNormEditDistanceLC(title, mention));
		if (bolds != null)
			features.put("min_edit_distance_bolds", minEdBold(mention, bolds));
		features.put("commonness", EntityToAnchors.e2a().getCommonness(mention, a.getConcept()));
		features.put("link_prob", WATRelatednessComputer.getLp(mention));
		
		putConditional(features, "entity2vec_lr_mention_entity", EntityToVect.getLrScore(urlEncodedTitle, mentionKeywords));
		putConditional(features, "entity2vec_centroid_mention_entity", EntityToVect.getCentroidScore(urlEncodedTitle, mentionKeywords));
		putConditional(features, "entity2vec_lr_query_entity", EntityToVect.getLrScore(urlEncodedTitle, queryKeywords));
		putConditional(features, "entity2vec_centroid_query_entity", EntityToVect.getCentroidScore(urlEncodedTitle, queryKeywords));
		return features;
	}
	
	public static void putConditional(HashMap<String, Double> features, String featureName, Float value){
		if (value != null)
			features.put(featureName, value.doubleValue());
	}

	public static double minEdTitle(String mention, String title) {
		return Math.min(SmaphUtils.getMinEditDist(title/*.toLowerCase()*/, mention), 1.0);
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
		return num / denom;
	}

	private static double minEdAnchorsWeightSqrtGeom(String segmentStr, List<Pair<String, Integer>> anchorAndOccurrencies, double smooth) {
		double values[] = new double[anchorAndOccurrencies.size()];
		double weights[] = new double[anchorAndOccurrencies.size()];

		for (int i = 0; i < anchorAndOccurrencies.size(); i++) {
			Pair<String, Integer> p = anchorAndOccurrencies.get(i);
			values[i] = smooth + SmaphUtils.getMinEditDist(segmentStr.toLowerCase(), p.first);
			weights[i] = Math.sqrt(p.second);
		}
		return SmaphUtils.weightedGeometricAverage(values, weights);
	}

	@Override
	public String[] getFeatureNames() {
		return getFeatureNamesStatic();
	}

	public static String[] getFeatureNamesStatic() {
		if (ftrNames == null) {
			Vector<String> v = new Vector<String>();
			v.addAll(Arrays.asList(EntityFeaturePack.ftrNames));
			v.add("edit_distance_anchor_segment_sqrt"); //41
			v.add("min_edit_distance_anchor_segment_sqrt");
			v.add("min_edit_distance_anchor_segment_vv_sqrt");
			v.add("edit_distance_anchor_segment_sqrt_comm");
			v.add("min_edit_distance_anchor_segment_sqrt_comm");
			v.add("min_edit_distance_anchor_segment_vv_sqrt_comm");
			v.add("min_edit_distance_anchor_segment_sqrt_geometric_0.20");
			v.add("min_edit_distance_anchor_segment_sqrt_geometric_0.10");
			v.add("min_edit_distance_anchor_segment_sqrt_geometric_0.05");
			v.add("min_edit_distance_anchor_segment_sqrt_geometric_0.02"); //50
			v.add("min_edit_distance_title");
			v.add("min_edit_distance_bolds");
			v.add("commonness");
			v.add("link_prob");
			v.add("edit_distance_title");
			v.add("entity2vec_lr_mention_entity");
			v.add("entity2vec_centroid_mention_entity");
			v.add("entity2vec_lr_query_entity");
			v.add("entity2vec_centroid_query_entity");			
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
