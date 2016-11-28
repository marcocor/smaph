package it.unipi.di.acube.smaph;

import java.io.FileNotFoundException;
import java.io.IOException;

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
		                new ZScoreFeatureNormalizer(AFFileBase + ".zscore", new AnnotationFeaturePack()), wikiApi,
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
