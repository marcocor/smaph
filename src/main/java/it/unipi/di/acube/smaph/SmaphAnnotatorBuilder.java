package it.unipi.di.acube.smaph;

import java.io.FileNotFoundException;
import java.io.IOException;

import it.unipi.di.acube.batframework.systemPlugins.CachedWATAnnotator;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.searchapi.CachedWebsearchApi;
import it.unipi.di.acube.searchapi.WebsearchApi;
import it.unipi.di.acube.searchapi.callers.BingSearchApiCaller;
import it.unipi.di.acube.searchapi.callers.GoogleSearchApiCaller;
import it.unipi.di.acube.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
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

public class SmaphAnnotatorBuilder {

	public static final BindingGenerator DEFAULT_BINDING_GENERATOR = new DefaultBindingGenerator();
	public static final CachedWATAnnotator DEFAULT_AUX_ANNOTATOR = new CachedWATAnnotator("wikisense.mkapp.it", 80, "base",
	        "COMMONNESS", "mw", "0.2", "0.0");
	public static WebsearchApi BING_WEBSEARCH_API = null;
	public static WebsearchApi GOOGLE_WEBSEARCH_API = null;
	public static final int DEFAULT_WIKISEARCH_PAGES = 10;
	public static final int DEFAULT_ANNOTATED_PAGES = 25;
	public static final int DEFAULT_NORMALSEARCH_PAGES = 25;
	public static final double DEFAULT_ANNOTATIONFILTER_RATIO = 0.03;
	public static final double DEFAULT_ANCHOR_MENTION_ED = 0.7;

	public enum Websearch {
		BING, GOOGLE_CSE
	}

	public static Websearch websearchFromString(String wsStr) {
		switch (wsStr) {
		case "bing":
			return SmaphAnnotatorBuilder.Websearch.BING;
		case "google":
			return SmaphAnnotatorBuilder.Websearch.GOOGLE_CSE;
		}
		return null;
	}

	public static String websearchToString(Websearch ws) {
		switch (ws) {
		case BING:
			return "bing";
		case GOOGLE_CSE:
			return "google";
		}
		return null;
	}

	public static WebsearchApi getWebsearch(Websearch ws) throws FileNotFoundException, ClassNotFoundException, IOException {
		switch (ws) {
		case BING:
			if (GOOGLE_WEBSEARCH_API == null)
				GOOGLE_WEBSEARCH_API = new CachedWebsearchApi(
				        new GoogleSearchApiCaller(SmaphConfig.getDefaultGoogleCseId(), SmaphConfig.getDefaultGoogleApiKey()),
				        SmaphConfig.getDefaultWebsearchCache());
			return GOOGLE_WEBSEARCH_API;
		case GOOGLE_CSE:

			if (BING_WEBSEARCH_API == null)
				BING_WEBSEARCH_API = new CachedWebsearchApi(new BingSearchApiCaller(SmaphConfig.getDefaultBingKey()),
				        SmaphConfig.getDefaultWebsearchCache());
			return BING_WEBSEARCH_API;
		default:
			return null;
		}
	}

	private static SmaphAnnotator getDefaultSmaphParam(WikipediaApiInterface wikiApi, EntityFilter entityFilter,
	        FeatureNormalizer efNorm, LinkBack lb, boolean s2, boolean s3, boolean s6, Websearch ws)
	                throws FileNotFoundException, ClassNotFoundException, IOException {
		return new SmaphAnnotator(entityFilter, efNorm, lb, s2, s3, DEFAULT_WIKISEARCH_PAGES, s6, DEFAULT_ANNOTATED_PAGES,
		        DEFAULT_NORMALSEARCH_PAGES, false, DEFAULT_AUX_ANNOTATOR,
		        new FrequencyAnnotationFilter(DEFAULT_ANNOTATIONFILTER_RATIO), wikiApi, getWebsearch(ws),
		        DEFAULT_ANCHOR_MENTION_ED, DEFAULT_BINDING_GENERATOR);
	}

	public static SmaphAnnotator getSmaphGatherer(WikipediaApiInterface wikiApi, boolean s2, boolean s3, boolean s6, Websearch ws)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultSmaphParam(wikiApi, new NoEntityFilter(), null, new DummyLinkBack(), s2, s3, s6, ws);
	}

	public static SmaphAnnotator getSmaphEF(WikipediaApiInterface wikiApi, String EFModelFileBase, Websearch ws)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultSmaphParam(wikiApi, LibSvmEntityFilter.fromFile(EFModelFileBase + ".model"),
		        new ZScoreFeatureNormalizer(EFModelFileBase + ".zscore", new EntityFeaturePack()), new DummyLinkBack(), true,
		        true, true, ws);
	}

	public static SmaphAnnotator getSmaphIndividualAR(WikipediaApiInterface wikiApi, String AFFileBase, Websearch ws)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultSmaphParam(wikiApi, new NoEntityFilter(), null,
		        new AdvancedIndividualLinkback(LibSvmAnnotationRegressor.fromFile(AFFileBase + ".model"),
		                new ZScoreFeatureNormalizer(AFFileBase + ".zscore", new AdvancedAnnotationFeaturePack()), wikiApi,
		                DEFAULT_ANCHOR_MENTION_ED),
		        true, true, true, ws);
	}

	public static SmaphAnnotator getSmaphCollective(WikipediaApiInterface wikiApi, String collModelBase, Websearch ws)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		CollectiveLinkBack lb = new CollectiveLinkBack(wikiApi, new DefaultBindingGenerator(),
		        new RankLibBindingRegressor(collModelBase + ".model"), new NoFeatureNormalizer());
		return getDefaultSmaphParam(wikiApi, new NoEntityFilter(), null, lb, true, true, true, ws);
	}

}
