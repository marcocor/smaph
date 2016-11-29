package it.unipi.di.acube.smaph;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.nio.file.Paths;

import org.apache.commons.lang.NotImplementedException;

import it.unipi.di.acube.batframework.systemPlugins.CachedWATAnnotator;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.searchapi.CachedWebsearchApi;
import it.unipi.di.acube.searchapi.WebsearchApi;
import it.unipi.di.acube.searchapi.callers.BingSearchApiCaller;
import it.unipi.di.acube.searchapi.callers.GoogleSearchApiCaller;
import it.unipi.di.acube.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.models.entityfilters.EntityFilter;
import it.unipi.di.acube.smaph.learn.models.entityfilters.LibSvmEntityFilter;
import it.unipi.di.acube.smaph.learn.models.entityfilters.NoEntityFilter;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.LibSvmAnnotationRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor.RankLibBindingRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.NoFeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.AdvancedIndividualLinkback;
import it.unipi.di.acube.smaph.linkback.CollectiveLinkBack;
import it.unipi.di.acube.smaph.linkback.DummyLinkBack;
import it.unipi.di.acube.smaph.linkback.LinkBack;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.BindingGenerator;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.DefaultBindingGenerator;
import it.unipi.di.acube.smaph.snippetannotationfilters.FrequencyAnnotationFilter;

public class SmaphBuilder {

	public static final BindingGenerator DEFAULT_BINDING_GENERATOR = new DefaultBindingGenerator();
	public static final CachedWATAnnotator DEFAULT_AUX_ANNOTATOR = new CachedWATAnnotator("wikisense.mkapp.it", 80, "base",
	        "COMMONNESS", "mw", "0.2", "0.0");
	public static WebsearchApi BING_WEBSEARCH_API = null;
	public static WebsearchApi GOOGLE_WEBSEARCH_API = null;
	public static final int DEFAULT_NORMALSEARCH_RESULTS = 5;
	public static final int DEFAULT_WIKISEARCH_RESULTS = 10;
	public static final int DEFAULT_ANNOTATED_SNIPPETS = 15;
	public static final double DEFAULT_ANNOTATIONFILTER_RATIO = 0.03;
	public static final double DEFAULT_ANCHOR_MENTION_ED = 0.7;
	public static final Websearch DEFAULT_WEBSEARCH = Websearch.GOOGLE_CSE;

	public enum SmaphVersion {
		ENTITY_FILTER("ef"), ANNOTATION_REGRESSOR("ar"), COLLECTIVE("coll");

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

	public static WebsearchApi getWebsearch(Websearch ws) throws FileNotFoundException, ClassNotFoundException, IOException {
		switch (ws) {
		case GOOGLE_CSE:
			if (GOOGLE_WEBSEARCH_API == null)
				GOOGLE_WEBSEARCH_API = new CachedWebsearchApi(
				        new GoogleSearchApiCaller(SmaphConfig.getDefaultGoogleCseId(), SmaphConfig.getDefaultGoogleApiKey()),
				        SmaphConfig.getDefaultWebsearchCache());
			return GOOGLE_WEBSEARCH_API;
		case BING:

			if (BING_WEBSEARCH_API == null)
				BING_WEBSEARCH_API = new CachedWebsearchApi(new BingSearchApiCaller(SmaphConfig.getDefaultBingKey()),
				        SmaphConfig.getDefaultWebsearchCache());
			return BING_WEBSEARCH_API;
		default:
			return null;
		}
	}

	private static SmaphAnnotator getDefaultSmaphParamTopk(WikipediaApiInterface wikiApi, EntityFilter entityFilter,
	        FeatureNormalizer efNorm, LinkBack lb, boolean s1, int topkS1, boolean s2, int topkS2, boolean s3, int topkS3,
	        Websearch ws) throws FileNotFoundException, ClassNotFoundException, IOException {
		return new SmaphAnnotator(s1, topkS1, s2, topkS2, s3, topkS3, DEFAULT_ANCHOR_MENTION_ED, false, lb, entityFilter, efNorm,
		        DEFAULT_BINDING_GENERATOR, DEFAULT_AUX_ANNOTATOR, new FrequencyAnnotationFilter(DEFAULT_ANNOTATIONFILTER_RATIO),
		        wikiApi, getWebsearch(ws));
	}

	private static SmaphAnnotator getDefaultSmaphParam(WikipediaApiInterface wikiApi, EntityFilter entityFilter,
	        FeatureNormalizer efNorm, LinkBack lb, boolean s1, boolean s2, boolean s3, Websearch ws)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultSmaphParamTopk(wikiApi, entityFilter, efNorm, lb, s1, DEFAULT_NORMALSEARCH_RESULTS, s2,
		        DEFAULT_WIKISEARCH_RESULTS, s3, DEFAULT_ANNOTATED_SNIPPETS, ws);
	}

	public static SmaphAnnotator getSmaphGatherer(WikipediaApiInterface wikiApi, boolean s1, boolean s2, boolean s3, Websearch ws)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultSmaphParam(wikiApi, new NoEntityFilter(), null, new DummyLinkBack(), s1, s2, s3, ws);
	}

	public static SmaphAnnotator getSmaphGatherer(WikipediaApiInterface wikiApi, boolean s1, int topkS1, boolean s2, int topkS2,
	        boolean s3, int topkS3, Websearch ws) throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultSmaphParamTopk(wikiApi, new NoEntityFilter(), null, new DummyLinkBack(), s1, topkS1, s2, topkS2, s3,
		        topkS3, ws);
	}

	public static SmaphAnnotator getSmaph(SmaphVersion v, WikipediaApiInterface wikiApi, boolean includeS2, Websearch ws)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		String model = getDefaultModel(v, ws, true, includeS2, true);
		String zscore = getDefaultZscoreNormalizer(v, ws, true, includeS2, true);

		SmaphAnnotator a = null;
		switch (v) {
		case ANNOTATION_REGRESSOR:
			a = getDefaultSmaphParam(wikiApi, new NoEntityFilter(), null,
			        new AdvancedIndividualLinkback(LibSvmAnnotationRegressor.fromFile(model),
			                new ZScoreFeatureNormalizer(zscore, new AnnotationFeaturePack()), wikiApi, DEFAULT_ANCHOR_MENTION_ED),
			        true, includeS2, true, ws);
			break;
		case ENTITY_FILTER:
			a = getDefaultSmaphParam(wikiApi, LibSvmEntityFilter.fromFile(model),
			        new ZScoreFeatureNormalizer(zscore, new EntityFeaturePack()), new DummyLinkBack(), true, includeS2, true, ws);
			break;
		case COLLECTIVE:
			CollectiveLinkBack lb = new CollectiveLinkBack(wikiApi, new DefaultBindingGenerator(),
			        new RankLibBindingRegressor(model), new NoFeatureNormalizer());
			a = getDefaultSmaphParam(wikiApi, new NoEntityFilter(), null, lb, true, includeS2, true, ws);
			break;
		default:
			throw new NotImplementedException();
		}
		a.appendName(String.format(" - %s, %s%s", v, ws, includeS2 ? "" : ", excl. S2"));

		return a;
	}

	public static SmaphAnnotator getSmaph(SmaphVersion v, WikipediaApiInterface wikiApi)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		return getSmaph(v, wikiApi, false, DEFAULT_WEBSEARCH);
	}

	public static String getDefaultLabel(SmaphVersion v, Websearch ws, boolean s1, boolean s2, boolean s3) {
		return getSourceLabel(v, ws, s1 ? DEFAULT_NORMALSEARCH_RESULTS : 0, s2 ? DEFAULT_WIKISEARCH_RESULTS : 0,
		        s3 ? DEFAULT_ANNOTATED_SNIPPETS : 0);
	}

	public static String getBestModelFileBase(String label, String extension) {
		String filename = String.format("best_%s.%s", label, extension);
		return Paths.get("src", "main", "resources", "models", filename).toFile().getAbsolutePath();
	}

	public static String getDefaultModel(SmaphVersion v, Websearch ws, boolean s1, boolean s2, boolean s3) {
		return getBestModelFileBase(getDefaultLabel(v, ws, s1, s2, s3), "model");
	}

	public static String getDefaultZscoreNormalizer(SmaphVersion v, Websearch ws, boolean s1, boolean s2, boolean s3) {
		return getBestModelFileBase(getDefaultLabel(v, ws, s1, s2, s3), "zscore");
	}

	public static String getModel(SmaphVersion v, Websearch ws, int topKS1, int topKS2, int topKS3) {
		return getBestModelFileBase(getSourceLabel(v, ws, topKS1, topKS2, topKS3), "model");
	}

	public static String getZscoreNormalizer(SmaphVersion v, Websearch ws, int topKS1, int topKS2, int topKS3) {
		return getBestModelFileBase(getSourceLabel(v, ws, topKS1, topKS2, topKS3), "zscore");
	}

	public static String getSourceLabel(SmaphVersion v, Websearch ws, int topKS1, int topKS2, int topKS3) {
		return String.format("%s_%s-S1=%d_S2=%d_S3=%d", v, ws, topKS1, topKS2, topKS3);
	}

}
