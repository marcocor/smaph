package it.acubelab.smaph.learn.featurePacks;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.Tag;
import it.acubelab.smaph.SmaphUtils;

import java.util.*;

import org.tartarus.snowball.ext.EnglishStemmer;

/**
 * This feature pack represents a set of basic feature of an annotation, based
 * on the number of segments covered and the edit distance towards the bolds.
 *
 */
public class AnnotationFeaturePack extends FeaturePack<Annotation> {

	private static final long serialVersionUID = 1L;

	public AnnotationFeaturePack(Annotation a, String query, EnglishStemmer stemmer,
			HashMap<String, Double> entityFeatures,
			HashMap<Tag, String[]> entityToBolds,
			HashMap<Tag, String> entityToTitle) {
		super(getFeaturesStatic(a, query, stemmer, entityFeatures, entityToBolds, entityToTitle));
	}
	
	public AnnotationFeaturePack() {
		super(null);
	}

	private static HashMap<String, Double> getFeaturesStatic(Annotation a, String query, EnglishStemmer stemmer,
			HashMap<String, Double> entityFeatures,
			HashMap<Tag, String[]> entityToBolds,
			HashMap<Tag, String> entityToTitle) {
		String segmentStr = query.substring(a.getPosition(),
				a.getPosition()+a.getLength());

		Tag t = new Tag(a.getConcept());
		HashMap<String, Double> features = new HashMap<String, Double>(entityFeatures);
		features.put("min_edit_distance_bold_segment", minEDBold(entityToBolds.get(t), segmentStr, null));
		features.put("min_edit_distance_title_segment", SmaphUtils.getMinEditDist(entityToTitle.get(t), segmentStr));
		features.put("min_edit_distance_bold_segment_stemmed", minEDBold(entityToBolds.get(t), segmentStr, stemmer));
		features.put("min_edit_distance_title_segment_stemmed", SmaphUtils.getMinEditDist(SmaphUtils.stemString(entityToTitle.get(t), stemmer), SmaphUtils.stemString(segmentStr, stemmer)));
		features.put("segment_tokens", (double)SmaphUtils.tokenize(segmentStr).size());
		features.put("segment_nonalphanumeric_chars", (double)SmaphUtils.getNonAlphanumericCharCount(segmentStr));
		return features;
	}

	private static Double minEDBold(String[] bolds, String segmentStr,
			EnglishStemmer stemmer) {
		if (stemmer != null)
			segmentStr = SmaphUtils.stemString(segmentStr, stemmer);
		if (bolds == null)
			return 1.0;
		double minBoldED = Double.POSITIVE_INFINITY;
		for (String bold : bolds) {
			if (stemmer!= null)
				bold = SmaphUtils.stemString(bold, stemmer);
			double minEdI = Math.min(SmaphUtils.getMinEditDist(segmentStr, bold), SmaphUtils.getMinEditDist(bold, segmentStr));
			if (minEdI < minBoldED) {
				minBoldED = minEdI;
			}
		}
		return minBoldED;
	}

	@Override
	public String[] getFeatureNames() {
		return getFeatureNamesStatic();
	}

	private static String[] getFeatureNamesStatic() {
			List<String> ftrNames = new Vector<>();
			ftrNames.addAll(Arrays.asList(EntityFeaturePack.ftrNames));
			ftrNames.add("min_edit_distance_bold_segment");
			ftrNames.add("min_edit_distance_title_segment");
			ftrNames.add("min_edit_distance_bold_segment_stemmed");
			ftrNames.add("min_edit_distance_title_segment_stemmed");
			ftrNames.add("segment_tokens");
			ftrNames.add("segment_nonalphanumeric_chars");
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
}
