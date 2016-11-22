package it.unipi.di.acube.smaph;

import java.io.FileNotFoundException;
import java.io.IOException;

import it.unipi.di.acube.batframework.systemPlugins.CachedWATAnnotator;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.searchapi.CachedWebsearchApi;
import it.unipi.di.acube.searchapi.WebsearchApi;
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
	
	public static final double DEFAULT_ANCHOR_MENTION_ED = 0.7;
	public static final BindingGenerator DEFAULT_BINDING_GENERATOR = new DefaultBindingGenerator();
	public static final CachedWATAnnotator DEFAULT_AUX_ANNOTATOR= new CachedWATAnnotator("wikisense.mkapp.it", 80, "base", "COMMONNESS", "mw", "0.2", "0.0");
	public static WebsearchApi DEFAULT_SEARCH_API = null;
	public static final int DEFAULT_WIKISEARCH_PAGES = 10;
	public static final int DEFAULT_ANNOTATED_PAGES = 25;
	public static final int DEFAULT_NORMALSEARCH_PAGES = 25;
	public static final double DEFAULT_ANNOTATIONFILTER_RATIO = 0.03;

	private static SmaphAnnotator getDefaultBingAnnotatorParam(WikipediaApiInterface wikiApi, EntityFilter entityFilter,
	        FeatureNormalizer efNorm, LinkBack lb, boolean s2, boolean s3, boolean s6)
	                throws FileNotFoundException, ClassNotFoundException, IOException {
		if (DEFAULT_SEARCH_API == null)
		//		DEFAULT_SEARCH_API = new CachedWebsearchApi(new BingSearchApiCaller(SmaphConfig.getDefaultBingKey()), SmaphConfig.getDefaultWebsearchCache());
			DEFAULT_SEARCH_API = new CachedWebsearchApi(new GoogleSearchApiCaller(SmaphConfig.getDefaultGoogleCseId(), SmaphConfig.getDefaultGoogleApiKey()), SmaphConfig.getDefaultWebsearchCache());

		return new SmaphAnnotator(entityFilter, efNorm, lb, s2, s3, DEFAULT_WIKISEARCH_PAGES, s6, DEFAULT_ANNOTATED_PAGES,
		        DEFAULT_NORMALSEARCH_PAGES, false, DEFAULT_AUX_ANNOTATOR,
		        new FrequencyAnnotationFilter(DEFAULT_ANNOTATIONFILTER_RATIO), wikiApi, DEFAULT_SEARCH_API,
		        DEFAULT_ANCHOR_MENTION_ED, DEFAULT_BINDING_GENERATOR);
	}

	public static SmaphAnnotator getDefaultBingAnnotatorGatherer(WikipediaApiInterface wikiApi, boolean s2,
	        boolean s3, boolean s6) throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam(wikiApi, new NoEntityFilter(), null, new DummyLinkBack(), s2, s3, s6);
	}

	public static SmaphAnnotator getDefaultBingAnnotatorEF(WikipediaApiInterface wikiApi, String EFModelFileBase)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam(wikiApi, LibSvmEntityFilter.fromFile(EFModelFileBase + ".model"),
		        new ZScoreFeatureNormalizer(EFModelFileBase + ".zscore", new EntityFeaturePack()), new DummyLinkBack(), true,
		        true, true);
	}

	public static SmaphAnnotator getDefaultBingAnnotatorIndividualAdvancedAnnotationRegressor(WikipediaApiInterface wikiApi,
	        String AFFileBase) throws FileNotFoundException, ClassNotFoundException,
	        IOException {
		return getDefaultBingAnnotatorParam(wikiApi, new NoEntityFilter(), null, new AdvancedIndividualLinkback(
		        LibSvmAnnotationRegressor.fromFile(AFFileBase + ".model"), new ZScoreFeatureNormalizer(AFFileBase + ".zscore",
		                new AdvancedAnnotationFeaturePack()), wikiApi, DEFAULT_ANCHOR_MENTION_ED), true, true, true);
	}

	public static SmaphAnnotator getDefaultBingAnnotatorCollectiveLBRanklib(WikipediaApiInterface wikiApi,
	        String collModelBase) throws FileNotFoundException, ClassNotFoundException, IOException {
		CollectiveLinkBack lb = new CollectiveLinkBack(wikiApi, new DefaultBindingGenerator(), new RankLibBindingRegressor(
		        collModelBase + ".model"), new NoFeatureNormalizer());
		return getDefaultBingAnnotatorParam(wikiApi, new NoEntityFilter(), null, lb, true, true, true);
	}

}
