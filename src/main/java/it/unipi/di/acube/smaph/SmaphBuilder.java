package it.unipi.di.acube.smaph;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.apache.commons.lang.NotImplementedException;

import it.unipi.di.acube.batframework.systemPlugins.CachedWATAnnotator;
import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.searchapi.CachedWebsearchApi;
import it.unipi.di.acube.searchapi.WebsearchApi;
import it.unipi.di.acube.searchapi.callers.BingSearchApiCaller;
import it.unipi.di.acube.searchapi.callers.GoogleSearchApiCaller;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;
import it.unipi.di.acube.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.GreedyFeaturePack;
import it.unipi.di.acube.smaph.learn.models.entityfilters.EntityFilter;
import it.unipi.di.acube.smaph.learn.models.entityfilters.LibSvmEntityFilter;
import it.unipi.di.acube.smaph.learn.models.entityfilters.NoEntityFilter;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.AnnotationRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.LibSvmAnnotationRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor.BindingRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor.RankLibBindingRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.NoFeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.AdvancedIndividualLinkback;
import it.unipi.di.acube.smaph.linkback.CollectiveLinkBack;
import it.unipi.di.acube.smaph.linkback.DummyLinkBack;
import it.unipi.di.acube.smaph.linkback.GreedyLinkback;
import it.unipi.di.acube.smaph.linkback.LinkBack;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.BindingGenerator;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.DefaultBindingGenerator;
import it.unipi.di.acube.smaph.snippetannotationfilters.FrequencyAnnotationFilter;

public class SmaphBuilder {

	public static final BindingGenerator DEFAULT_BINDING_GENERATOR = new DefaultBindingGenerator();
	public static final CachedWATAnnotator DEFAULT_CACHED_AUX_ANNOTATOR = new CachedWATAnnotator("wikisense.mkapp.it", 80, "base",
	        "COMMONNESS", "mw", "0.2", "0.0");
	public static final WATAnnotator DEFAULT_AUX_ANNOTATOR = new WATAnnotator("wikisense.mkapp.it", 80, "base", "COMMONNESS",
	        "mw", "0.2", "0.0");
	public static WebsearchApi BING_WEBSEARCH_API = null;
	public static WebsearchApi GOOGLE_WEBSEARCH_API = null;
	private static Map<URL, FeatureNormalizer> urlToNormalizer = new HashMap<>();
	private static Map<URL, EntityFilter> urlToEntityFilter = new HashMap<>();
	private static Map<URL, AnnotationRegressor> urlToAnnotationRegressor = new HashMap<>();
	private static Map<URL, BindingRegressor> urlToBindingRegressor = new HashMap<>();
	public static final int DEFAULT_NORMALSEARCH_RESULTS = 5;
	public static final int DEFAULT_WIKISEARCH_RESULTS = 10;
	public static final int DEFAULT_ANNOTATED_SNIPPETS = 20;
	public static final double DEFAULT_ANNOTATIONFILTER_RATIO = 0.03;
	public static final double DEFAULT_ANCHOR_MENTION_ED = 0.7;
	public static final Websearch DEFAULT_WEBSEARCH = Websearch.GOOGLE_CSE;

	public enum SmaphVersion {
		ENTITY_FILTER("ef"), ANNOTATION_REGRESSOR("ar"), COLLECTIVE("coll"), GREEDY("greedy");

		private String label;

		private SmaphVersion(String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	public enum Websearch {
		BING, GOOGLE_CSE
	}

	public static Websearch websearchFromString(String wsStr) {
		switch (wsStr) {
		case "bing":
			return SmaphBuilder.Websearch.BING;
		case "google":
			return SmaphBuilder.Websearch.GOOGLE_CSE;
		}
		return null;
	}

	public static WebsearchApi getWebsearch(Websearch ws, SmaphConfig c)
	        throws FileNotFoundException, ClassNotFoundException, IOException {

		if (c.getDefaultWebsearchCache() == null)
			switch (ws) {
			case GOOGLE_CSE:
				return new WebsearchApi(new GoogleSearchApiCaller(c.getDefaultGoogleCseId(), c.getDefaultGoogleApiKey()));
			case BING:
				return new WebsearchApi(new BingSearchApiCaller(c.getDefaultBingKey()));
			}
		else
			switch (ws) {
			case GOOGLE_CSE:
				if (GOOGLE_WEBSEARCH_API == null)
					GOOGLE_WEBSEARCH_API = CachedWebsearchApi.builder()
					        .api(new GoogleSearchApiCaller(c.getDefaultGoogleCseId(), c.getDefaultGoogleApiKey()))
					        .dbFrom((CachedWebsearchApi) BING_WEBSEARCH_API).path(c.getDefaultWebsearchCache()).create();
				return GOOGLE_WEBSEARCH_API;
			case BING:
				if (BING_WEBSEARCH_API == null)
					BING_WEBSEARCH_API = CachedWebsearchApi.builder().api(new BingSearchApiCaller(c.getDefaultBingKey()))
					        .dbFrom((CachedWebsearchApi) GOOGLE_WEBSEARCH_API).path(c.getDefaultWebsearchCache()).create();
				return BING_WEBSEARCH_API;
			}
		return null;
	}

	public static SmaphAnnotator getDefaultSmaphParamTopk(WikipediaInterface wikiApi, WikipediaToFreebase wikiToFreeb,
	        WATAnnotator auxAnnotator, EntityToAnchors e2a, EntityFilter entityFilter, FeatureNormalizer efNorm, LinkBack lb,
	        boolean s1, int topkS1, boolean s2, int topkS2, boolean s3, int topkS3, Websearch ws, SmaphConfig c)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		return new SmaphAnnotator(s1, topkS1, s2, topkS2, s3, topkS3, DEFAULT_ANCHOR_MENTION_ED, false, lb, entityFilter, efNorm,
		        DEFAULT_BINDING_GENERATOR, auxAnnotator, new FrequencyAnnotationFilter(DEFAULT_ANNOTATIONFILTER_RATIO), wikiApi,
		        wikiToFreeb, getWebsearch(ws, c), e2a);
	}

	private static SmaphAnnotator getDefaultSmaphParam(WikipediaInterface wikiApi, WikipediaToFreebase wikiToFreeb,
	        WATAnnotator auxAnnotator, EntityToAnchors e2a, EntityFilter entityFilter, FeatureNormalizer efNorm, LinkBack lb,
	        boolean s1, boolean s2, boolean s3, Websearch ws, SmaphConfig c)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultSmaphParamTopk(wikiApi, wikiToFreeb, auxAnnotator, e2a, entityFilter, efNorm, lb, s1,
		        DEFAULT_NORMALSEARCH_RESULTS, s2, DEFAULT_WIKISEARCH_RESULTS, s3, DEFAULT_ANNOTATED_SNIPPETS, ws, c);
	}

	public static SmaphAnnotator getSmaphGatherer(WikipediaInterface wikiApi, WikipediaToFreebase wikiToFreeb,
	        EntityToAnchors e2a, boolean s1, boolean s2, boolean s3, Websearch ws, SmaphConfig c)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultSmaphParam(wikiApi, wikiToFreeb, DEFAULT_CACHED_AUX_ANNOTATOR, e2a, new NoEntityFilter(), null,
		        new DummyLinkBack(), s1, s2, s3, ws, c);
	}

	public static SmaphAnnotator getSmaphGatherer(WikipediaInterface wikiApi, WikipediaToFreebase wikiToFreeb,
	        EntityToAnchors e2a, boolean s1, int topkS1, boolean s2, int topkS2, boolean s3, int topkS3, Websearch ws,
	        SmaphConfig c) throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultSmaphParamTopk(wikiApi, wikiToFreeb, DEFAULT_CACHED_AUX_ANNOTATOR, e2a, new NoEntityFilter(), null,
		        new DummyLinkBack(), s1, topkS1, s2, topkS2, s3, topkS3, ws, c);
	}

	public static SmaphAnnotator getSmaph(SmaphVersion v, WikipediaInterface wikiApi, WikipediaToFreebase wikiToFreeb,
	        WATAnnotator auxAnnotator, EntityToAnchors e2a, boolean includeS2, Websearch ws, SmaphConfig c)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		URL model = getDefaultModel(v, ws, true, includeS2, true, -1);
		URL zscore = getDefaultZscoreNormalizer(v, ws, true, includeS2, true, -1);

		SmaphAnnotator a = null;
		switch (v) {
		case ANNOTATION_REGRESSOR: {
			AnnotationRegressor ar = getCachedAnnotationRegressor(model);
			FeatureNormalizer fn = getCachedFeatureNormalizer(zscore, new GreedyFeaturePack());
			a = getDefaultSmaphParam(wikiApi, wikiToFreeb, auxAnnotator, e2a, new NoEntityFilter(), null,
			        new AdvancedIndividualLinkback(ar, fn, wikiApi, wikiToFreeb, e2a, DEFAULT_ANCHOR_MENTION_ED), true, includeS2,
			        true, ws, c);
		}
			break;
		case ENTITY_FILTER: {
			EntityFilter ef = getCachedSvmEntityFilter(model);
			FeatureNormalizer norm = getCachedFeatureNormalizer(zscore, new EntityFeaturePack());
			a = getDefaultSmaphParam(wikiApi, wikiToFreeb, auxAnnotator, e2a, ef, norm, new DummyLinkBack(), true, includeS2,
			        true, ws, c);
		}
			break;
		case COLLECTIVE: {
			BindingRegressor bindingRegressor = getCachedBindingRegressor(model);
			CollectiveLinkBack lb = new CollectiveLinkBack(wikiApi, wikiToFreeb, e2a, new DefaultBindingGenerator(),
			        bindingRegressor, new NoFeatureNormalizer());
			a = getDefaultSmaphParam(wikiApi, wikiToFreeb, auxAnnotator, e2a, new NoEntityFilter(), null, lb, true, includeS2,
			        true, ws, c);
		}
			break;
		case GREEDY: {
			List<AnnotationRegressor> regressors = new Vector<>();
			List<FeatureNormalizer> fns = new Vector<>();
			int nGreedySteps = 0;
			while (getDefaultModel(v, ws, true, includeS2, true, nGreedySteps) != null)
				nGreedySteps++;
			if (nGreedySteps == 0)
				throw new IllegalArgumentException("Could not find models.");

			for (int i = 0; i < nGreedySteps; i++) {
				URL modelI = getDefaultModel(v, ws, true, includeS2, true, i);
				URL zscoreI = getDefaultZscoreNormalizer(v, ws, true, includeS2, true, i);
				AnnotationRegressor arI = getCachedAnnotationRegressor(modelI);
				FeatureNormalizer fnI = getCachedFeatureNormalizer(zscoreI, new GreedyFeaturePack());
				regressors.add(arI);
				fns.add(fnI);
			}
			GreedyLinkback lbGreedy = new GreedyLinkback(regressors, fns, wikiApi, wikiToFreeb, e2a, DEFAULT_ANCHOR_MENTION_ED);
			a = getDefaultSmaphParam(wikiApi, wikiToFreeb, auxAnnotator, e2a, new NoEntityFilter(), null, lbGreedy, true,
			        includeS2, true, ws, c);
		}
			break;

		default:
			throw new NotImplementedException();
		}
		a.appendName(String.format(" - %s, %s%s", v, ws, includeS2 ? "" : ", excl. S2"));

		return a;
	}

	private static AnnotationRegressor getCachedAnnotationRegressor(URL model) {
		if (!urlToAnnotationRegressor.containsKey(model))
			urlToAnnotationRegressor.put(model, LibSvmAnnotationRegressor.fromUrl(model));
		return urlToAnnotationRegressor.get(model);
	}

	private static EntityFilter getCachedSvmEntityFilter(URL model) throws IOException {
		if (!urlToEntityFilter.containsKey(model))
			urlToEntityFilter.put(model, LibSvmEntityFilter.fromUrl(model));
		return urlToEntityFilter.get(model);
	}

	private static <T> FeatureNormalizer getCachedFeatureNormalizer(URL zscore, FeaturePack<T> fp) {
		if (!urlToNormalizer.containsKey(zscore))
			urlToNormalizer.put(zscore, ZScoreFeatureNormalizer.fromUrl(zscore, fp));
		return urlToNormalizer.get(zscore);
	}

	private static BindingRegressor getCachedBindingRegressor(URL model) throws IOException {
		if (!urlToBindingRegressor.containsKey(model))
			urlToBindingRegressor.put(model, RankLibBindingRegressor.fromUrl(model));
		return urlToBindingRegressor.get(model);
	}

	public static SmaphAnnotator getSmaph(SmaphVersion v, WikipediaInterface wikiApi, WikipediaToFreebase wikiToFreeb,
	        WATAnnotator auxAnnotator, EntityToAnchors e2a, SmaphConfig c)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		return getSmaph(v, wikiApi, wikiToFreeb, auxAnnotator, e2a, false, DEFAULT_WEBSEARCH, c);
	}

	public static String getDefaultLabel(SmaphVersion v, Websearch ws, boolean s1, boolean s2, boolean s3) {
		return getSourceLabel(v, ws, s1 ? DEFAULT_NORMALSEARCH_RESULTS : 0, s2 ? DEFAULT_WIKISEARCH_RESULTS : 0,
		        s3 ? DEFAULT_ANNOTATED_SNIPPETS : 0);
	}

	private static URL getBestModelFileBase(String label, String extension, int greedyStep) {
		String stepStr = greedyStep < 0 ? "" : String.format("_%d", greedyStep);
		return SmaphBuilder.class.getClassLoader().getResource(String.format("models/best_%s%s.%s", label, stepStr, extension));
	}

	private static URL getDefaultModel(SmaphVersion v, Websearch ws, boolean s1, boolean s2, boolean s3, int greedyStep) {
		return getBestModelFileBase(getDefaultLabel(v, ws, s1, s2, s3), "model", greedyStep);
	}

	public static URL getDefaultZscoreNormalizer(SmaphVersion v, Websearch ws, boolean s1, boolean s2, boolean s3, int greedyStep) {
		return getBestModelFileBase(getDefaultLabel(v, ws, s1, s2, s3), "zscore", greedyStep);
	}

	public static URL getModel(SmaphVersion v, Websearch ws, int topKS1, int topKS2, int topKS3, int greedyStep) {
		return getBestModelFileBase(getSourceLabel(v, ws, topKS1, topKS2, topKS3), "model", greedyStep);
	}

	public static URL getZscoreNormalizer(SmaphVersion v, Websearch ws, int topKS1, int topKS2, int topKS3, int greedyStep) {
		return getBestModelFileBase(getSourceLabel(v, ws, topKS1, topKS2, topKS3), "zscore", greedyStep);
	}

	public static String getSourceLabel(SmaphVersion v, Websearch ws, int topKS1, int topKS2, int topKS3) {
		return String.format("%s_%s-S1=%d_S2=%d_S3=%d", v, ws, topKS1, topKS2, topKS3);
	}

	public static File getModelFile(SmaphVersion v, Websearch ws, int topKS1, int topKS2, int topKS3, int step) {
		String label = getSourceLabel(v, ws, topKS1, topKS2, topKS3);
		String stepStr = step < 0 ? "" : String.format("_%d", step);
		return Paths.get("src", "main", "resources", "models", String.format("best_%s%s.model", label, stepStr)).toFile();
	}

	public static File getModelFile(SmaphVersion v, Websearch ws, int topKS1, int topKS2, int topKS3) {
		return getModelFile(v, ws, topKS1, topKS2, topKS3, -1);
	}

	public static File getZscoreNormalizerFile(SmaphVersion v, Websearch ws, int topKS1, int topKS2, int topKS3, int step) {
		String label = getSourceLabel(v, ws, topKS1, topKS2, topKS3);
		String stepStr = step == -1 ? "" : String.format("_%d", step);
		return Paths.get("src", "main", "resources", "models", String.format("best_%s%s.zscore", label, stepStr)).toFile();
	}
	
	public static File getZscoreNormalizerFile(SmaphVersion v, Websearch ws, int topKS1, int topKS2, int topKS3) {
		return getZscoreNormalizerFile(v, ws, topKS1, topKS2, topKS3, -1);
	}
}
