package it.unipi.di.acube.smaph.linkback;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphDebugger;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;
import it.unipi.di.acube.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.AnnotationRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class IndividualAnnotationLinkBack implements LinkBack {
	private AnnotationRegressor ar;
	private FeatureNormalizer annFn;
	private WikipediaApiInterface wikiApi;
	private double threshold;
	private WikipediaToFreebase w2f;

	public IndividualAnnotationLinkBack(AnnotationRegressor ar,
			FeatureNormalizer annFn, WikipediaApiInterface wikiApi,
			double threshold, WikipediaToFreebase w2f) {
		this.ar = ar;
		this.annFn = annFn;
		this.wikiApi = wikiApi;
		this.threshold = threshold;
		this.w2f = w2f;
	}

	private static List<Annotation> getAnnotations(String query,
			Set<Tag> acceptedEntities, QueryInformation qi){
		List<Pair<Integer, Integer>> segments = SmaphUtils.findSegments(query);
		List<Annotation> annotations = new Vector<>();
		for (Tag t : acceptedEntities)
			for (Pair<Integer, Integer> segment : segments) 
				annotations.add(new Annotation(segment.first, segment.second-segment.first, t.getConcept()));
				
		return annotations;
	}
	
	@Override
	public HashSet<ScoredAnnotation> linkBack(String query,
			HashSet<Tag> acceptedEntities, QueryInformation qi) {

		List<Pair<Annotation, Double>> scoreAndAnnotations = new Vector<>();
		for (Annotation a : getAnnotations(query, acceptedEntities, qi)) {
			double score = ar.predictScore(
					new AnnotationFeaturePack(a, query, qi, wikiApi, w2f), annFn);
			scoreAndAnnotations.add(new Pair<Annotation, Double>(a, score));
		}

		return getResult(scoreAndAnnotations, threshold);
	}
	
	public static HashSet<ScoredAnnotation> getResult(List<Pair<Annotation, Double>> annotationsAndScore, double threshold){
		Collections.sort(annotationsAndScore, new SmaphUtils.ComparePairsBySecondElement<Annotation, Double>());
		Collections.reverse(annotationsAndScore);

		HashSet<ScoredAnnotation> res = new HashSet<>();
		
		for (Pair<Annotation, Double> pair : annotationsAndScore){
			Annotation annI = pair.first;
			double score = pair.second;
			
			if (score < threshold) break;
			
			boolean overlap = false;
			for (ScoredAnnotation ann : res)
				if (annI.overlaps(ann)) {
					overlap = true;
					break;
				}
			if (!overlap){
				res.add(new ScoredAnnotation(annI.getPosition(), annI.getLength(), annI.getConcept(), (float)score));
			}
		}

		//TODO: shall we collapse bindings?
		//return SmaphUtils.collapseBinding(res);
		return res;
	}

	@Override
	public void setDebugger(SmaphDebugger debugger) {
	}

}
