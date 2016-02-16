package it.unipi.di.acube.smaph;

import it.unipi.di.acube.batframework.systemPlugins.CachedWATAnnotator;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.learn.featurePacks.AdvancedAnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.BindingFeaturePack;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.models.entityfilters.EntityFilter;
import it.unipi.di.acube.smaph.learn.models.entityfilters.LibSvmEntityFilter;
import it.unipi.di.acube.smaph.learn.models.entityfilters.NoEntityFilter;
import it.unipi.di.acube.smaph.learn.models.linkback.annotationRegressor.LibSvmAnnotationRegressor;
import it.unipi.di.acube.smaph.learn.models.linkback.bindingRegressor.RankLibBindingRegressor;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.AdvancedIndividualLinkback;
import it.unipi.di.acube.smaph.linkback.CollectiveLinkBack;
import it.unipi.di.acube.smaph.linkback.DummyLinkBack;
import it.unipi.di.acube.smaph.linkback.LinkBack;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.DefaultBindingGenerator;
import it.unipi.di.acube.smaph.snippetannotationfilters.FrequencyAnnotationFilter;

import java.io.FileNotFoundException;
import java.io.IOException;

public class SmaphAnnotatorBuilder {
	
	private static SmaphAnnotator getDefaultBingAnnotatorParam(WikipediaApiInterface wikiApi, String bingKey,
	        EntityFilter entityFilter, FeatureNormalizer efNorm, LinkBack lb, boolean s2, boolean s3, boolean s6)
	        throws FileNotFoundException, ClassNotFoundException, IOException {

		CachedWATAnnotator watDefault = new CachedWATAnnotator("wikisense.mkapp.it", 80, "base", "COMMONNESS", "mw", "0.2", "0.0");
		return new SmaphAnnotator(entityFilter, efNorm, lb, s2, s3, 10, s6, 25, false, watDefault, new FrequencyAnnotationFilter(
		        0.03), wikiApi, bingKey);
	}

	public static SmaphAnnotator getDefaultBingAnnotatorGatherer(WikipediaApiInterface wikiApi, String bingKey, boolean s2,
	        boolean s3, boolean s6) throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam(wikiApi, bingKey, new NoEntityFilter(), null, new DummyLinkBack(), s2, s3, s6);
	}

	public static SmaphAnnotator getDefaultBingAnnotatorEF(WikipediaApiInterface wikiApi, String bingKey, String EFModelFileBase)
	        throws FileNotFoundException, ClassNotFoundException, IOException {
		return getDefaultBingAnnotatorParam(wikiApi, bingKey, LibSvmEntityFilter.fromFile(EFModelFileBase + ".model"),
		        new ZScoreFeatureNormalizer(EFModelFileBase + ".zscore", new EntityFeaturePack()), new DummyLinkBack(), true,
		        true, true);
	}

	public static SmaphAnnotator getDefaultBingAnnotatorIndividualAdvancedAnnotationRegressor(WikipediaApiInterface wikiApi,
	        String bingKey, String AFFileBase, double anchorMaxED) throws FileNotFoundException, ClassNotFoundException,
	        IOException {
		return getDefaultBingAnnotatorParam(wikiApi, bingKey, new NoEntityFilter(), null, new AdvancedIndividualLinkback(
		        LibSvmAnnotationRegressor.fromFile(AFFileBase + ".model"), new ZScoreFeatureNormalizer(AFFileBase + ".zscore",
		                new AdvancedAnnotationFeaturePack()), wikiApi, anchorMaxED), true, true, true);
	}

	public static SmaphAnnotator getDefaultBingAnnotatorCollectiveLBRanklib(WikipediaApiInterface wikiApi, String bingKey,
	        String collModelBase) throws FileNotFoundException, ClassNotFoundException, IOException {
		CollectiveLinkBack lb = new CollectiveLinkBack(wikiApi, new DefaultBindingGenerator(), new RankLibBindingRegressor(
		        collModelBase + ".model"), new ZScoreFeatureNormalizer(collModelBase + ".zscore", new BindingFeaturePack()));
		return getDefaultBingAnnotatorParam(wikiApi, bingKey, new NoEntityFilter(), null, lb, true, true, true);
	}

}
