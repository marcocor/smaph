package it.unipi.di.acube.smaph.learn.featurePacks;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;

import java.io.IOException;
import java.util.*;

/**
 * This feature pack represents a set of feature of an annotation, based on the
 * number of tokens covered and the edit distance towards bolds and anchors.
 *
 */
public class AnnotationFeaturePack extends FeaturePack<Annotation> {
	private static String[] ftrNames = null;
	
	private static final long serialVersionUID = 1L;

	public AnnotationFeaturePack(Annotation a, String query, QueryInformation qi, WikipediaInterface wikiApi,
	        WikipediaToFreebase w2f, EntityToAnchors e2a) {
		super(getFeaturesStatic(a, query, qi, wikiApi, w2f, e2a));
	}
	
	public AnnotationFeaturePack() {
		super(null);
	}

	public static List<Pair<String, Integer>> getFakeAnchors(String title) {
		List<Pair<String, Integer>> l = new Vector<>();
		l.add(new Pair<>(title.toLowerCase(), 10));
		return l;
	}

	public static HashMap<String, Double> getFeaturesStatic(Annotation a, String query, QueryInformation qi,
	        WikipediaInterface wikiApi, WikipediaToFreebase w2f, EntityToAnchors e2a) {
		Tag entity = new Tag(a.getConcept());
		String mention = query.substring(a.getPosition(), a.getPosition() + a.getLength());
		HashMap<String, Double> entityFeatures = EntityFeaturePack.getFeatures(entity, query, qi, wikiApi, w2f);
		List<String> bolds = null;
		if (qi.entityToBoldsSA.containsKey(entity))
			bolds = qi.entityToBoldsSA.get(entity);

		String title;
		try {
			title = wikiApi.getTitlebyId(a.getConcept());
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		
		//List<Pair<String, Integer>> anchorAndOccurrencies = e2a.containsId(a.getConcept()) ? e2a.getAnchors(a.getConcept())
		  //      : getFakeAnchors(title.toLowerCase());

		List<Pair<String, Integer>> anchorAndOccurrencies = null;
		if (e2a.containsId(a.getConcept()))
			anchorAndOccurrencies = e2a.getAnchors(a.getConcept());
		else if (title != null)
			anchorAndOccurrencies = AnnotationFeaturePack.getFakeAnchors(title);

		HashMap<String, Double> features = new HashMap<String, Double>(entityFeatures);
		features.put("edit_distance_anchor_segment_sqrt", edAnchorsWeightSqrt(mention, anchorAndOccurrencies));
		features.put("edit_distance_anchor_segment_sqrt_comm", edAnchorsWeightSqrtComm(mention, anchorAndOccurrencies, a.getConcept(), e2a));
		features.put("min_edit_distance_anchor_segment_sqrt_geometric_0.02", minEdAnchorsWeightSqrtGeom(mention, anchorAndOccurrencies, 0.02));
		features.put("min_edit_distance_title", minEdTitle(mention, title));
		features.put("edit_distance_title", (double) SmaphUtils.getNormEditDistanceLC(title, mention));
		if (bolds != null)
			features.put("min_edit_distance_bolds", minEdBold(mention, bolds));
		features.put("commonness", e2a.containsId(a.getConcept())? e2a.getCommonness(mention, a.getConcept()) : 1.0);
		features.put("link_prob", WATRelatednessComputer.getLp(mention));
		
		features.put("edit_distance_anchor_segment_sqrt_geometric_0.05", edAnchorsWeightSqrtGeom(mention, anchorAndOccurrencies, 0.05));
		double expandibilityRight = expandibility(query, a.getPosition(), a.getPosition() + a.getLength(), anchorAndOccurrencies,
		        true);
		double expandibilityLeft = expandibility(query, a.getPosition(), a.getPosition() + a.getLength(), anchorAndOccurrencies,
		        false);
		features.put("expandibility_sum", expandibilityLeft + expandibilityRight);
		return features;
	}

	public static String expandedMention(String query, int beginning, int end, boolean rightOrLeft) {
		query = query.replaceAll("[^a-zA-Z0-9]", " ").toLowerCase();
		int i = rightOrLeft ? end : beginning - 1;
		int spotted = 0;
		while (i >= 0 && i < query.length()) {
			if (query.charAt(i) != ' ')
				spotted++;
			if (spotted >= 3) {
				if ((i == 0 || i == query.length() - 1) && query.charAt(i) != ' ')
					return rightOrLeft ? query.substring(beginning, i + 1) : query.substring(i, end);
				if (query.charAt(i) == ' ')
					return rightOrLeft ? query.substring(beginning, i) : query.substring(i + 1, end);
			}
			i += rightOrLeft ? +1 : -1;
		}
		return null;
	}

	public static double expandibility(String query, int beginning, int end, List<Pair<String, Integer>> anchorAndOccurrencies,
	        boolean rightOrLeft) {
		String expanded = expandedMention(query, beginning, end, rightOrLeft);
		if (expanded == null)
			return -1.0;
		String mention = query.substring(beginning, end);

		double edExpandedAnchor = 1.0;
		double edMentionAnchor = 1.0;
		for (Pair<String, Integer> p : anchorAndOccurrencies) {
			edExpandedAnchor = Math.min(edExpandedAnchor, SmaphUtils.getNormEditDistanceLC(expanded, p.first));
			edMentionAnchor = Math.min(edMentionAnchor,
			        SmaphUtils.getNormEditDistanceLC(mention, p.first));
		}
		return edMentionAnchor - edExpandedAnchor;
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

	private static double edAnchorsWeightSqrtComm(String segmentStr, List<Pair<String, Integer>> anchorAndOccurrencies, int entity, EntityToAnchors e2a) {
		double num = 0;
		double denom = 0;
		for (Pair<String, Integer> p: anchorAndOccurrencies){
			num += Math.sqrt((e2a.containsId(entity)? e2a.getCommonness(p.first, entity, p.second) : 1.0))*SmaphUtils.getNormEditDistance(segmentStr.toLowerCase(), p.first);
			denom += Math.sqrt(e2a.containsId(entity)? e2a.getCommonness(p.first, entity, p.second) : 1.0);
		}
		return num/denom;
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
	
	private static double edAnchorsWeightSqrtGeom(String segmentStr, List<Pair<String, Integer>> anchorAndOccurrencies, double smooth) {
		double values[] = new double[anchorAndOccurrencies.size()];
		double weights[] = new double[anchorAndOccurrencies.size()];

		for (int i = 0; i < anchorAndOccurrencies.size(); i++) {
			Pair<String, Integer> p = anchorAndOccurrencies.get(i);
			values[i] = smooth + SmaphUtils.getNormEditDistanceLC(segmentStr.toLowerCase(), p.first);
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
			v.add("edit_distance_anchor_segment_sqrt");
			v.add("edit_distance_anchor_segment_sqrt_comm");
			v.add("min_edit_distance_anchor_segment_sqrt_geometric_0.02");
			v.add("min_edit_distance_title");
			v.add("min_edit_distance_bolds");
			v.add("commonness");
			v.add("link_prob");
			v.add("edit_distance_title");
			v.add("edit_distance_anchor_segment_sqrt_geometric_0.05");
			v.add("expandibility_sum");
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
