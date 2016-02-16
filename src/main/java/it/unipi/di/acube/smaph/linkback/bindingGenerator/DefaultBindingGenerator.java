package it.unipi.di.acube.smaph.linkback.bindingGenerator;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphUtils;

import java.lang.invoke.MethodHandles;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DefaultBindingGenerator implements BindingGenerator {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static final int MAX_SEGMENTATIONS = 1000;
	private static final int MAX_SEGMENTATIONS_AFTER_FILTER = 150;//150;
	private static final int MAX_BINDINGS_PER_SEGMENTATION = 50;//50;

	public List<HashSet<Annotation>> getAllBindings(String query,
			HashMap<Tag, String[]> entityToBolds, HashMap<Tag, String> entitiesToTitles, Set<Tag> acceptedEntities) {
		HashSet<HashSet<Annotation>> insertedAnnotationSets = new HashSet<>();
		List<HashSet<Annotation>> annotationSets = new Vector<>();
		List<List<Pair<Integer, Integer>>> segmentations = pruneSegmentations(query, entityToBolds, entitiesToTitles);
		
		for (List<Pair<Integer, Integer>> segmentation : segmentations) {
			HashSet<Tag> assignedTags = new HashSet<>();
			List<List<Tag>> candidatesForSegmentation = new Vector<>();
			for (Pair<Integer, Integer> segment : segmentation) {
				List<Tag> candidatesForSegment = new Vector<>();
				candidatesForSegmentation.add(candidatesForSegment);
				// This segment may not be linked to any entity.
				candidatesForSegment.add(new Tag(-1));
				for (Tag tag : entityToBolds.keySet())
					if (acceptedEntities.contains(tag)) {
						double minEditDistance = 1.0;
						for (String tagText : entityToBolds.get(tag))
							minEditDistance = Math.min(
							        SmaphUtils.getNormEditDistanceLC(query.substring(segment.first, segment.second), tagText),
							        minEditDistance);
						if (minEditDistance <= 0.4) {
							candidatesForSegment.add(tag);
							assignedTags.add(tag);
						}
					}
				/*for (Tag tag : entityToBoldS2S3.keySet()) {
					if (candidatesForSegment.contains(tag))
						continue;
					String[] tagTexts = entityToBoldS2S3.get(tag);
					double minEditDistance = 1.0;
					for (String tagText : tagTexts)
						minEditDistance = Math.min(
								SmaphUtils.getNormEditDistanceLC(query
										.substring(segment.first,
												segment.second), tagText),
								minEditDistance);
					if (minEditDistance <= -1) {
						candidatesForSegment.add(tag);
						assignedTags.add(tag);
					}
				}*/
				for (Tag tag : entitiesToTitles.keySet())
					if (acceptedEntities.contains(tag)) {
						if (candidatesForSegment.contains(tag))
							continue;
						if (SmaphUtils.getMinEditDist(entitiesToTitles.get(tag), query.substring(segment.first, segment.second)) <= 0.7) {
							candidatesForSegment.add(tag);
							assignedTags.add(tag);
						}
					}
			}
			
			//add orphan tags to closest segment TODO: don't like at all!!!!
			/*for (Tag tag : entitiesToTitles.keySet())
				if (acceptedEntities.contains(tag)) {
					if (assignedTags.contains(tag))
						continue;

					double minEditDistance = Double.POSITIVE_INFINITY;
					List<Tag> candidatesForBestSegment = null;

					for (int i = 0; i < segmentation.size(); i++) {
						Pair<Integer, Integer> segment = segmentation.get(i);
						String segmentStr = query.substring(segment.first, segment.second);
						double editDistance = SmaphUtils.getMinEditDist(entitiesToTitles.get(tag), segmentStr);
						if (editDistance < minEditDistance) {
							minEditDistance = editDistance;
							candidatesForBestSegment = candidatesForSegmentation.get(i);
						}
					}
					if (minEditDistance < 1.0 && candidatesForBestSegment != null) {
						candidatesForBestSegment.add(tag);
						assignedTags.add(tag);
					}
				}*/
			
			//add tags that were not candidates to any segment to all segments
			for (Tag tag : entitiesToTitles.keySet())
				if (acceptedEntities.contains(tag)) {
					if (assignedTags.contains(tag))
						continue;
					for (List<Tag> candidatesForSegment : candidatesForSegmentation)
						candidatesForSegment.add(tag);
				}

			List<List<Tag>> bindingsForSegmentation = SmaphUtils.getBindings(candidatesForSegmentation, MAX_BINDINGS_PER_SEGMENTATION);

			for (List<Tag> tags : bindingsForSegmentation) {
				HashSet<Annotation> annotations = new HashSet<>();
				for (int i = 0; i < tags.size(); i++)
					if (tags.get(i).getConcept() != -1)
						annotations.add(new Annotation(
								segmentation.get(i).first,
								segmentation.get(i).second
										- segmentation.get(i).first, tags
										.get(i).getConcept()));
				if (!insertedAnnotationSets.contains(annotations)) {
					annotationSets.add(annotations);
					insertedAnnotationSets.add(annotations);
				}
			}
		}
		
		if (!insertedAnnotationSets.contains(new HashSet<Annotation>())) {
			annotationSets.add(new HashSet<Annotation>());
			insertedAnnotationSets.add(new HashSet<Annotation>());
		}
		
		LOG.info("Segmentations: {} Bindings: {}",
				segmentations.size(), annotationSets.size());
		return annotationSets;
	}
	

	private List<List<Pair<Integer, Integer>>> pruneSegmentations(String query,
			HashMap<Tag, String[]> entityToBoldsS1, HashMap<Tag, String> entitiesToTitles){
		//Do an initial pruning of segmentations and entities: if the bold (or title) of an entity found with source1 matches a substring in the query, fix it 
				List<Pair<Integer, Integer>> segments = SmaphUtils.findSegments(query);
				
				HashMap<Pair<Integer, Integer>, List<Tag>> segmentFixedCandidates = new HashMap<>();
				HashSet<Tag> fixedCandidates =  new HashSet<>();
				// link all entities to segments with edit-distance <0.1
				for (Pair<Integer, Integer> segment : segments) {
/*					for (Tag tag : entityToBoldsS1.keySet()) {
						double minEditDistance = 1.0;
						for (String tagText : entityToBoldsS1.get(tag))
							minEditDistance = Math.min(SmaphUtils
									.getNormEditDistanceLC(query.substring(
											segment.first, segment.second), tagText),
									minEditDistance);
						if (minEditDistance <= 0.1) {
							if (!segmentFixedCandidates.containsKey(segment))
								segmentFixedCandidates.put(segment, new Vector<Tag>());
							segmentFixedCandidates.get(segment).add(tag);
							fixedCandidates.add(tag);
						}
					}
*/					for (Tag tag : entitiesToTitles.keySet()) {
						if (segmentFixedCandidates.containsKey(segment)
								&& segmentFixedCandidates.get(segment).contains(tag))
							continue;
						if (SmaphUtils.getMinEditDist(entitiesToTitles.get(tag),
								query.substring(segment.first, segment.second)) <= 0.1) {
							if (!segmentFixedCandidates.containsKey(segment))
								segmentFixedCandidates.put(segment, new Vector<Tag>());
							segmentFixedCandidates.get(segment).add(tag);
							fixedCandidates.add(tag);
						}
					}
				}
				
					
				
				List<List<Pair<Integer, Integer>>> allSegmentations = SmaphUtils
						.getSegmentations(query, MAX_SEGMENTATIONS);
				
				//If a fixed segment is included into another and they point to the same set of entities, delete segmentations that include the inner one (this is safe).
				HashSet<Pair<Integer, Integer>> fixedSegmentsToRemove = new HashSet<>();
				for (Pair<Integer, Integer> segment : segmentFixedCandidates.keySet()) {
					boolean included = false;
					for (Pair<Integer, Integer> segment2 : segmentFixedCandidates
							.keySet())
						if (!segment.equals(segment2)
								&& segment2.first <= segment.first
								&& segment.second <= segment2.second
								&& new HashSet<Tag>(segmentFixedCandidates.get(segment)).equals(
										new HashSet<Tag>(segmentFixedCandidates.get(segment2)))) {
							included = true;
							break;
						}
					if (included) {
						fixedSegmentsToRemove.add(segment);
						List<Integer> segmentationsToRemove = new Vector<>();
						for (int i = 0; i < allSegmentations.size(); i++) {
							List<Pair<Integer, Integer>> segmentation = allSegmentations
									.get(i);
							if (segmentation.contains(segment))
								segmentationsToRemove.add(i);
						}
						for (int i = segmentationsToRemove.size() - 1; i >= 0; i--)
							allSegmentations.remove(segmentationsToRemove.get(i)
									.intValue());
					}
				}
				for (Pair<Integer, Integer> segment : fixedSegmentsToRemove)
					segmentFixedCandidates.remove(segment);
				
				// Throw away bindings that conflict with fixed ones.
				/*for (Pair<Integer, Integer> fixedSegment : segmentFixedCandidates.keySet()){
					//If this segment overlaps with another fixed one, do not perform any deletion.
					boolean foundOverlappingSegment = false;
					for (Pair<Integer, Integer> fixedSegment2 : segmentFixedCandidates.keySet())
						if (!fixedSegment2.equals(fixedSegment) && segmentsOverlap(fixedSegment, fixedSegment2)){
							foundOverlappingSegment = true;
							break;
						}
					LOG.info(query.substring(fixedSegment.first, fixedSegment.second)+" "+(foundOverlappingSegment?"overlap":"no-overlap"));
					if (foundOverlappingSegment)
						continue;
					//There are no overlapping fixed segments, delete all segmentations that contain segments conflicting with segment.
					List<Integer> segmentationsToRemove = new Vector<>();
					for (int i=0; i<allSegmentations.size(); i++){
						List<Pair<Integer, Integer>> segmentation= allSegmentations.get(i);
						for (Pair<Integer, Integer> segment : segmentation)
							if (!fixedSegment.equals(segment) && segmentsOverlap(fixedSegment, segment)){
								segmentationsToRemove.add(i);
								break;
							}
					}
					for (int i = segmentationsToRemove.size()-1; i>=0; i--)
						allSegmentations.remove(segmentationsToRemove.get(i).intValue());
				}
				
				LOG.info(allSegmentations.size());
				*/
				
				if (allSegmentations.size() > MAX_SEGMENTATIONS_AFTER_FILTER)
					allSegmentations.subList(MAX_SEGMENTATIONS_AFTER_FILTER, allSegmentations.size()).clear();
				
				return allSegmentations;
	}
	
	@Override
	public List<HashSet<Annotation>> getBindings(String query,
			QueryInformation qi, Set<Tag> acceptedEntities,
			WikipediaApiInterface wikiApi) {
		HashMap<Tag, String[]> entityToBolds;
		entityToBolds = SmaphUtils.getEntitiesToBoldsList(qi.entityToBoldsSA,
				qi.allCandidates());
		HashMap<Tag, String> entitiesToTitles = SmaphUtils.getEntitiesToTitles(acceptedEntities, wikiApi);
		return getAllBindings(query, entityToBolds, entitiesToTitles, acceptedEntities);
		
	}
}
