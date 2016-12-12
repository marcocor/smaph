package it.unipi.di.acube.smaph.linkback;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphDebugger;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;
import it.unipi.di.acube.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.AnnotationRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class AdvancedIndividualLinkback implements LinkBack {
	private AnnotationRegressor ar;
	private FeatureNormalizer annFn;
	private WikipediaInterface wikiApi;
	private double edthreshold;
	private WikipediaToFreebase w2f;
	private EntityToAnchors e2a;

	public AdvancedIndividualLinkback(AnnotationRegressor ar, FeatureNormalizer annFn, WikipediaInterface wikiApi,
	        WikipediaToFreebase w2f, EntityToAnchors e2a, double edthreshold) throws FileNotFoundException, IOException {
		this.ar = ar;
		this.annFn = annFn;
		this.wikiApi = wikiApi;
		this.w2f = w2f;
		this.edthreshold = edthreshold;
		this.e2a = e2a;
	}

	public static List<Annotation> getAnnotations(String query, Set<Tag> acceptedEntities, double anchorMaxED, EntityToAnchors e2a) {
		List<Pair<Integer, Integer>> segments = SmaphUtils.findSegments(query);
		List<Annotation> annotations = new Vector<>();
		for (Tag t : acceptedEntities) {
			if (!e2a.containsId(t.getConcept()))
				continue;
			List<Pair<String, Integer>> entityAnchors = e2a.getAnchors(t.getConcept());
			for (Pair<Integer, Integer> segment : segments) {
				String segmentStr = query.substring(segment.first, segment.second);
				if (entityAnchors.stream().anyMatch(anchor -> SmaphUtils.getNormEditDistance(anchor.first, segmentStr) < anchorMaxED))
					annotations.add(new Annotation(segment.first, segment.second - segment.first, t.getConcept()));
			}
		}
		return annotations;
	}

	@Override
	public HashSet<ScoredAnnotation> linkBack(String query, HashSet<Tag> acceptedEntities, QueryInformation qi) {

		List<Pair<Annotation, Double>> scoreAndAnnotations = new Vector<>();
		for (Annotation a : getAnnotations(query, acceptedEntities, edthreshold, e2a)) {
			double score = ar.predictScore(new AnnotationFeaturePack(a, query, qi, wikiApi, w2f, e2a),
					annFn);
			scoreAndAnnotations.add(new Pair<Annotation, Double>(a, score));
		}

		return IndividualAnnotationLinkBack.getResult(scoreAndAnnotations, ar.threshold());
	}

	@Override
	public void setDebugger(SmaphDebugger debugger) {
	}

}
