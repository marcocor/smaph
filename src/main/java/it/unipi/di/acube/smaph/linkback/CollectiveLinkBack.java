package it.unipi.di.acube.smaph.linkback;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphAnnotatorDebugger;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor.BindingRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.BindingGenerator;
import it.unipi.di.acube.smaph.wikiAnchors.EntityToAnchors;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

public class CollectiveLinkBack implements LinkBack {
	private BindingRegressor bindingRegressorModel;
	private WikipediaApiInterface wikiApi;
	private BindingGenerator bg;
	private FeatureNormalizer brFn;
	private SmaphAnnotatorDebugger debugger;
	
	public CollectiveLinkBack(WikipediaApiInterface wikiApi,
			BindingGenerator bg, BindingRegressor lbReg, FeatureNormalizer brFn) throws IOException {
		this.bindingRegressorModel = lbReg;
		this.wikiApi = wikiApi;
		this.bg = bg;
		this.brFn = brFn;
	}
	
	public static List<Pair<HashSet<Annotation>,BindingFeaturePack>> getBindingFeaturePacks(String query,
			Set<Tag> acceptedEntities, QueryInformation qi, BindingGenerator bg, WikipediaApiInterface wikiApi, SmaphAnnotatorDebugger debugger){
		List<Pair<HashSet<Annotation>,BindingFeaturePack>> featurePacks = new Vector<>();
		// Generate all possible bindings
		List<HashSet<Annotation>> bindings = bg.getBindings(query, qi,
				acceptedEntities, wikiApi);

		for (HashSet<Annotation> binding : bindings) {
			//Discard bindings that have entities w/o anchors TODO: don't like.
			boolean bad = false;
			for (Annotation a : binding)
				if (!EntityToAnchors.e2a().containsId(a.getConcept()))
					bad = true;
			if (bad) continue;

			HashMap<Annotation, HashMap<String, Double>> debugAnnotationFeatures = new HashMap<>();
			HashMap<String, Double> debugBindingFeatures = new HashMap<>();
			BindingFeaturePack features = new BindingFeaturePack(binding, query,
					qi, wikiApi, debugAnnotationFeatures, debugBindingFeatures);
			featurePacks.add(new Pair<HashSet<Annotation>, BindingFeaturePack>(binding, features));
			if (debugger != null)
				debugger.addLinkbackBindingFeatures(query, binding, debugAnnotationFeatures, debugBindingFeatures);

		}
		return featurePacks;
	}

	@Override
	public HashSet<ScoredAnnotation> linkBack(String query,
			HashSet<Tag> acceptedEntities, QueryInformation qi) {

		// Predict a score and pick the best-performing
		HashSet<Annotation> bestBinding = null;
		double bestScore = Double.NEGATIVE_INFINITY;
		
		List<Pair<HashSet<Annotation>, BindingFeaturePack>> bindingFeaturePacks = getBindingFeaturePacks(query, acceptedEntities, qi, bg, wikiApi, debugger);
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
	public void setDebugger(SmaphAnnotatorDebugger debugger) {
		this.debugger = debugger;
	}
}
