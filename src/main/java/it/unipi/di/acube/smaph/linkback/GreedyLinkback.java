package it.unipi.di.acube.smaph.linkback;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphDebugger;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.GreedyFeaturePack;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.AnnotationRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

public class GreedyLinkback implements LinkBack {
	private List<AnnotationRegressor> ar;
	private List<FeatureNormalizer> annFn;
	private WikipediaInterface wikiApi;
	private double edthreshold;
	private WikipediaToFreebase w2f;
	private EntityToAnchors e2a;
	
	public GreedyLinkback(List<AnnotationRegressor> annRegs, List<FeatureNormalizer> fns, WikipediaInterface wikiApi,
	        WikipediaToFreebase w2f, EntityToAnchors e2a, double edthreshold) throws FileNotFoundException, IOException {
		if (annRegs.size() != fns.size())
			throw new IllegalArgumentException();
		this.ar = annRegs;
		this.annFn = fns;
		this.wikiApi = wikiApi;
		this.w2f = w2f;
		this.edthreshold = edthreshold;
		this.e2a = e2a;
	}

	public static List<Annotation> getAnnotations(String query, Set<Tag> acceptedEntities, double anchorMaxED, EntityToAnchors e2a, WikipediaInterface wikiApi) {
		return AdvancedIndividualLinkback.getAnnotations(query, acceptedEntities, anchorMaxED, e2a, wikiApi);
	}

	@Override
	public HashSet<ScoredAnnotation> linkBack(String query, HashSet<Tag> acceptedEntities, QueryInformation qi) {
		List<Annotation> annotations = getAnnotations(query, acceptedEntities, edthreshold, e2a, wikiApi);
		
		HashSet<ScoredAnnotation> solution = new HashSet<>();
		for (int i = 0; i < ar.size(); i++) {
			List<Pair<GreedyFeaturePack, Annotation>> ftrsAndAnnotations = annotations.stream().map(
			        a -> new Pair<GreedyFeaturePack, Annotation>(
			        		new GreedyFeaturePack(a, query, qi, solution, wikiApi, w2f, e2a),
			                a))
			        .collect(Collectors.toList());
			ScoredAnnotation annToAdd = getStepAnnotation(ftrsAndAnnotations, ar.get(i), annFn.get(i));
			if (annToAdd == null)
				break;
			solution.add(annToAdd);
			annotations = annotations.stream().filter(a -> solution.stream().allMatch(as -> !as.overlaps(a)))
			        .collect(Collectors.toList());
			if (annotations.isEmpty())
				break;
		}
		return solution;
	}

	public static <A extends Annotation, F extends FeaturePack<A>> ScoredAnnotation getStepAnnotation(
	        List<Pair<F, Annotation>> ftrsAndAnnotations, AnnotationRegressor annotationRegressor,
	        FeatureNormalizer featureNormalizer) {
		double highestScore = Double.NEGATIVE_INFINITY;
		Annotation bestAnn = null;
		for (Pair<F, Annotation> ftrsAndAnnotation : ftrsAndAnnotations) {
			double score = annotationRegressor.predictScore(ftrsAndAnnotation.first, featureNormalizer);
			if (score > annotationRegressor.threshold() && score > highestScore) {
				bestAnn = ftrsAndAnnotation.second;
				highestScore = score;
			}
		}
		if (bestAnn == null)
			return null;

		return new ScoredAnnotation(bestAnn.getPosition(), bestAnn.getLength(), bestAnn.getConcept(), (float) highestScore);
	}

	@Override
	public void setDebugger(SmaphDebugger debugger) {
	}

}
