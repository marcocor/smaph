package it.unipi.di.acube.smaph.linkback;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphAnnotatorDebugger;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.models.linkback.annotationRegressor.AnnotationRegressor;
import it.unipi.di.acube.smaph.wikiAnchors.EntityToAnchors;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class AdvancedIndividualLinkback implements LinkBack {
	private AnnotationRegressor ar;
	private FeatureNormalizer annFn;
	private WikipediaApiInterface wikiApi;
	private double threshold;
	private double edthreshold;

	public AdvancedIndividualLinkback(AnnotationRegressor ar, FeatureNormalizer annFn, WikipediaApiInterface wikiApi,
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

		List<Pair<Annotation, Double>> scoreAndAnnotations = new Vector<>();
		for (Annotation a : getAnnotations(query, acceptedEntities, qi, edthreshold)) {
			double score = ar.predictScore(new AdvancedAnnotationFeaturePack(a, query, qi, wikiApi),
					annFn);
			scoreAndAnnotations.add(new Pair<Annotation, Double>(a, score));
		}

		return IndividualAnnotationLinkBack.getResult(scoreAndAnnotations, threshold);
	}

	@Override
	public void setDebugger(SmaphAnnotatorDebugger debugger) {
	}

}
