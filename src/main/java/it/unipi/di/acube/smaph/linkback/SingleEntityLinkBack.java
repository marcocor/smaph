package it.unipi.di.acube.smaph.linkback;

import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphAnnotatorDebugger;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.models.entityfilters.LibSvmEntityFilter;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

public class SingleEntityLinkBack implements LinkBack {
	private LibSvmEntityFilter ef;
	private WikipediaApiInterface wikiApi;
	private FeatureNormalizer fn;

	public SingleEntityLinkBack(LibSvmEntityFilter ef, FeatureNormalizer fn,
			WikipediaApiInterface wikiApi) {
		this.ef = ef;
		this.wikiApi = wikiApi;
		this.fn = fn;
	}

	@Override
	public HashSet<ScoredAnnotation> linkBack(String query,
			HashSet<Tag> acceptedEntities, QueryInformation qi) {

		List<Pair<Double, Tag>> scoreAndTags = new Vector<>();
		for (Tag t : acceptedEntities) {
			List<EntityFeaturePack> featureVects = EntityFeaturePack.getAllFeaturePacks(t, query, qi, wikiApi);
			double bestScore = Double.NEGATIVE_INFINITY;
			for (EntityFeaturePack features : featureVects)
				bestScore = Math.max(ef.predictScore(features, fn), bestScore);
			scoreAndTags.add(new Pair<Double, Tag>(bestScore, t));
		}

		Collections.sort(scoreAndTags, new ComparePairsByScore());
		Collections.reverse(scoreAndTags);

		List<Pair<Integer, Integer>> segments = SmaphUtils.findSegments(query);
		HashMap<Tag, String[]> entityToBoldsS1 = SmaphUtils.getEntitiesToBolds(
				qi.boldToEntityS1, acceptedEntities);
		HashMap<Tag, String> entityToTitle = SmaphUtils.getEntitiesToTitles(
				acceptedEntities, wikiApi);

		HashSet<ScoredAnnotation> res = new HashSet<>();
		for (Pair<Double, Tag> scoreAndTag : scoreAndTags) {
			Tag tag = scoreAndTag.second;
			double minBoldED = Double.POSITIVE_INFINITY;
			double minTitleED = Double.POSITIVE_INFINITY;
			Pair<Integer, Integer> bestSegmentBold = null;
			Pair<Integer, Integer> bestSegmentTitle = null;
			if (entityToBoldsS1.containsKey(tag)) {
				for (String bold : entityToBoldsS1.get(tag))
					for (Pair<Integer, Integer> segment : segments) {
						String segmentStr = query.substring(segment.first,
								segment.second);
						double minEdI = SmaphUtils.getMinEditDist(segmentStr,
								bold);
						if (minEdI < minBoldED) {
							minBoldED = minEdI;
							bestSegmentBold = segment;
						}
					}
			}
			String title = entityToTitle.get(tag);
			for (Pair<Integer, Integer> segment : segments) {
				String segmentStr = query.substring(segment.first,
						segment.second);
				double minEDTitleI = SmaphUtils.getMinEditDist(
						title, segmentStr);
				if (minEDTitleI < minTitleED) {
					minTitleED = minEDTitleI;
					bestSegmentTitle = segment;
				}
			}

			double score = scoreAndTag.first;
			if (score <-0.2) continue;
			
			Pair<Integer, Integer> bestSegment = null;
			if (minTitleED < 0.1) bestSegment = bestSegmentTitle;
			else if (bestSegmentBold != null && minBoldED <=0.6)
				bestSegment = bestSegmentBold;
			
			   
			 
			else
				continue;

			ScoredAnnotation bestAnn = new ScoredAnnotation(bestSegment.first,
					bestSegment.second - bestSegment.first, tag.getConcept(),
					1f);
			boolean overlap = false;
			for (ScoredAnnotation ann : res)
				if (bestAnn.overlaps(ann)) {
					overlap = true;
					break;
				}
			if (!overlap)
				res.add(bestAnn);

		}

		return res;
	}

	private class ComparePairsByScore implements Comparator<Pair<Double, Tag>> {
		@Override
		public int compare(Pair<Double, Tag> o1, Pair<Double, Tag> o2) {
			double diff = o1.first - o2.first;
			if (diff < 0)
				return -1;
			else if (diff == 0)
				return 0;
			else
				return 1;
		}
	}

	@Override
	public void setDebugger(SmaphAnnotatorDebugger debugger) {
	}

}
