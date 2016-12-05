package it.unipi.di.acube.smaph.linkback;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphDebugger;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor.BindingRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.BindingGenerator;

import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CollectiveLinkBack implements LinkBack {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private BindingRegressor bindingRegressorModel;
	private WikipediaApiInterface wikiApi;
	private BindingGenerator bg;
	private FeatureNormalizer brFn;
	private SmaphDebugger debugger;
	private WikipediaToFreebase w2f;

	public CollectiveLinkBack(WikipediaApiInterface wikiApi, WikipediaToFreebase w2f, BindingGenerator bg, BindingRegressor lbReg,
	        FeatureNormalizer brFn) throws IOException {
		this.bindingRegressorModel = lbReg;
		this.wikiApi = wikiApi;
		this.bg = bg;
		this.brFn = brFn;
		this.w2f = w2f;
	}
	
	public static List<Pair<HashSet<Annotation>, BindingFeaturePack>> getBindingFeaturePacks(String query,
	        Set<Tag> acceptedEntities, QueryInformation qi, BindingGenerator bg, WikipediaApiInterface wikiApi,
	        WikipediaToFreebase w2f, SmaphDebugger debugger) {
		List<Pair<HashSet<Annotation>,BindingFeaturePack>> featurePacks = new Vector<>();
		//TODO: don't like.
		acceptedEntities = acceptedEntities.stream().filter(e -> EntityToAnchors.e2a().containsId(e.getConcept())).collect(Collectors.toCollection(HashSet::new));
		
		// Generate all possible bindings
		LOG.info("Generating bindings.");
		List<HashSet<Annotation>> bindings = bg.getBindings(query, qi,
				acceptedEntities, wikiApi);
		LOG.info("Generated {} bindings.", bindings.size());

		LOG.info("Generating Binding Features.");
		for (HashSet<Annotation> binding : bindings) {
			//Discard bindings that have entities w/o anchors
			/*boolean bad = false;
			for (Annotation a : binding)
				if (!EntityToAnchors.e2a().containsId(a.getConcept())){
					bad = true;
					throw new RuntimeException();
				}
			if (bad) continue;*/

			HashMap<Annotation, HashMap<String, Double>> debugAnnotationFeatures = new HashMap<>();
			HashMap<String, Double> debugBindingFeatures = new HashMap<>();
			BindingFeaturePack features = new BindingFeaturePack(binding, query,
					qi, wikiApi, w2f, debugAnnotationFeatures, debugBindingFeatures);
			featurePacks.add(new Pair<HashSet<Annotation>, BindingFeaturePack>(binding, features));
			if (debugger != null)
				debugger.addLinkbackBindingFeatures(query, binding, debugAnnotationFeatures, debugBindingFeatures);

		}
		LOG.info("Generated Binding Features.");

		return featurePacks;
	}

	@Override
	public HashSet<ScoredAnnotation> linkBack(String query,
			HashSet<Tag> acceptedEntities, QueryInformation qi) {

		// Predict a score and pick the best-performing
		HashSet<Annotation> bestBinding = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		
		List<Pair<HashSet<Annotation>, BindingFeaturePack>> bindingFeaturePacks = getBindingFeaturePacks(query, acceptedEntities, qi, bg, wikiApi, w2f, debugger);
		List<FeaturePack<HashSet<Annotation>>> packs = new Vector<>();
		for (Pair<HashSet<Annotation>, BindingFeaturePack> bindingAndFeatures: bindingFeaturePacks)
			packs.add(bindingAndFeatures.second);
		
		double[] scores = bindingRegressorModel.getScores(packs, brFn);
		
		for (int i=0; i<scores.length; i++){
			if (scores[i] > bestScore) {
				bestBinding = bindingFeaturePacks.get(i).first;
				bestScore = scores[i];
			}
			if (debugger != null)
				debugger.addLinkbackBindingScore(query, bindingFeaturePacks.get(i).first, scores[i]);
		}

		HashSet<ScoredAnnotation> scoredBestBinding = new HashSet<>();
		for (Annotation ann : bestBinding) {
			scoredBestBinding.add(new ScoredAnnotation(ann.getPosition(), ann
					.getLength(), ann.getConcept(), 1.0f));
		}
		return SmaphUtils.collapseBinding(scoredBestBinding);
	}




/*
	@Override
	public HashSet<ScoredAnnotation> linkBack(String query,
			HashSet<Tag> acceptedEntities, QueryInformation qi) {
		// Generate all possible bindings
		List<HashSet<Annotation>> bindings = bg.getBindings(query, qi,
				acceptedEntities, wikiApi);

		// Predict a score and pick the best-performing
		HashSet<Annotation> bestBinding = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		for (HashSet<Annotation> binding : bindings) {
			
			//Discard bindings that have entities w/o anchors
			boolean bad = false;
			for (Annotation a : binding)
				if (!EntityToAnchors.e2a().containsId(a.getConcept()))
					bad = true;
			if (bad) continue;
			
			HashMap<Annotation, HashMap<String, Double>> debugAnnotationFeatures = new HashMap<>();
			HashMap<String, Double> debugBindingFeatures = new HashMap<>();
			BindingFeaturePack features = new BindingFeaturePack(binding, query,
					qi, acceptedEntities, wikiApi, debugAnnotationFeatures, debugBindingFeatures);
			
			double predictedScore = bindingRegressorModel
					.predictScore(features,brFn);
			
			if (predictedScore > bestScore) {
				bestBinding = binding;
				bestScore = predictedScore;
			}

			if (debugger != null){
				debugger.addLinkbackBindingFeatures(query, binding, debugAnnotationFeatures, debugBindingFeatures);
				debugger.addLinkbackBindingScore(query, binding, predictedScore);
			}
		}

		HashSet<ScoredAnnotation> scoredBestBinding = new HashSet<>();
		for (Annotation ann : bestBinding) {
			scoredBestBinding.add(new ScoredAnnotation(ann.getPosition(), ann
					.getLength(), ann.getConcept(), 1.0f));
		}
		return SmaphUtils.collapseBinding(scoredBestBinding);
	}
*/
	@Override
	public void setDebugger(SmaphDebugger debugger) {
		this.debugger = debugger;
	}
}
