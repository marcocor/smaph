package it.unipi.di.acube.smaph.linkback;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.annotationRegressor.Regressor;
import it.unipi.di.acube.smaph.wikiAnchors.EntityToAnchors;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class SvmAdvancedIndividualSingleLinkback implements LinkBack {
	private Regressor ar;
	private FeatureNormalizer annFn;
	private WikipediaApiInterface wikiApi;
	private double threshold;
	private double edthreshold;

	public SvmAdvancedIndividualSingleLinkback(Regressor ar, FeatureNormalizer annFn, WikipediaApiInterface wikiApi,
			double threshold, double edthreshold) throws FileNotFoundException, IOException {
		this.ar = ar;
		this.annFn = annFn;
		this.wikiApi = wikiApi;
		this.threshold = threshold;
		this.edthreshold = edthreshold;
	}

	public static List<Annotation> getAnnotations(String query, Set<Tag> acceptedEntities, QueryInformation qi, double anchorMaxED) {
		List<Pair<Integer, Integer>> segments = SmaphUtils.findSegments(query);
		List<Annotation> annotations = new Vector<>();
		for (Tag t : acceptedEntities) {
			if (!EntityToAnchors.e2a().containsId(t.getConcept()))
				continue;
			List<Pair<String, Integer>> entityAnchors = EntityToAnchors.e2a().getAnchors(t.getConcept());
			for (Pair<Integer, Integer> segment : segments) {
				String segmentStr = query.substring(segment.first, segment.second);
				for (Pair<String, Integer> anchor : entityAnchors) {
					if (SmaphUtils.getNormEditDistance(anchor.first, segmentStr) < anchorMaxED)
						annotations.add(new Annotation(segment.first, segment.second - segment.first, t.getConcept()));
				}
			}
		}
		return annotations;
	}

	@Override
	public HashSet<ScoredAnnotation> linkBack(String query, HashSet<Tag> acceptedEntities, QueryInformation qi) {

		HashMap<Tag, String> entityToTitle = SmaphUtils.getEntitiesToTitles(acceptedEntities, wikiApi);

		List<Pair<Annotation, Double>> scoreAndAnnotations = new Vector<>();
		for (Annotation a : getAnnotations(query, acceptedEntities, qi, edthreshold)) {
			for (HashMap<String, Double> entityFeatures : qi.entityToFtrVects.get(new Tag(a.getConcept()))) {
				double score = ar.predictScore(new AdvancedAnnotationFeaturePack(a, query, EntityToAnchors.e2a().getAnchors(a.getConcept()), entityFeatures),
					annFn);
			scoreAndAnnotations.add(new Pair<Annotation, Double>(a, score));
			}
		}

		return SvmIndividualAnnotationLinkBack.getResult(scoreAndAnnotations, threshold);
	}

}
