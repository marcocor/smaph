package it.unipi.di.acube.smaph.snippetannotationfilters;

import it.unipi.di.acube.batframework.data.Tag;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

public interface SnippetAnnotationFilter {

	public HashSet<Tag> filterAnnotations(HashMap<Tag, List<Integer>> tagToRanks, double resCount);
}
