package it.unipi.di.acube.smaph.linkback.bindingGenerator;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.AnnotationRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.apache.commons.collections4.comparators.ReverseComparator;

public class AnnotationRegressorBindingGenerator implements BindingGenerator {
	private AnnotationRegressor ar;
	private double threshold;
	private FeatureNormalizer fn;
	private static final int MAX_ANNOTATIONS = 30;

	public AnnotationRegressorBindingGenerator(AnnotationRegressor ar, FeatureNormalizer fn, double threshold){
		this.ar = ar;
		this.fn = fn;
		this.threshold = threshold;
	}

	@Override
	public List<HashSet<Annotation>> getBindings(String query, QueryInformation qi, Set<Tag> acceptedEntities,
	        WikipediaApiInterface wikiApi) {
		
		//Generate all annotations
		Set<Pair<Annotation, Double>> validAnnsAndScore = new HashSet<>();
		List<Pair<Integer, Integer>> segments = SmaphUtils.findSegments(query);
		for (Pair<Integer, Integer> s: segments)
			for (Tag e: acceptedEntities){
				Annotation a = new Annotation(s.first, s.second-s.first, e.getConcept());
				double score = ar.predictScore(new AdvancedAnnotationFeaturePack(a, query, qi, wikiApi), fn);
				if (score > threshold){
					validAnnsAndScore.add(new Pair<Annotation, Double>(a,score));
				}
			}
		List<Pair<Annotation, Double>> sortedAnns = SmaphUtils.sorted(validAnnsAndScore, new ReverseComparator<Pair<Annotation, Double>>(new SmaphUtils.ComparePairsBySecondElement<Annotation, Double>()));
		sortedAnns = sortedAnns.subList(0, Math.min(MAX_ANNOTATIONS, sortedAnns.size()));
		
		Set<Annotation> validAnns = new HashSet<>();
		for (Pair<Annotation, Double> p:sortedAnns)
			validAnns.add(p.first);
		
		List<HashSet<Annotation>> bindingAnns = getBindingsAnn(validAnns);
		return bindingAnns;
	}

	public static List<HashSet<Annotation>> getBindingsAnn(Set<Annotation> validAnns) {
		List<Annotation> sortedAnns = SmaphUtils.sorted(validAnns);
	    
		List<HashSet<Annotation>> res = new Vector<>();
		getBindingsAnnRec(new Vector<Annotation>(), sortedAnns, 0, res );
		
		return res;
    }

	private static void getBindingsAnnRec(Vector<Annotation> sofar, List<Annotation> sortedAnns, int i, List<HashSet<Annotation>> res) {
	    res.add(new HashSet<Annotation>(sofar));
	    for (int h=i; h<sortedAnns.size(); h++)
	    	if (sofar.isEmpty() || !sofar.get(sofar.size()-1).overlaps(sortedAnns.get(h))){
	    		Vector<Annotation> sofarRec = new Vector<>(sofar);
	    		sofarRec.add(sortedAnns.get(h));
	    		getBindingsAnnRec(sofarRec, sortedAnns, h+1, res);
	    	}
    }
}
