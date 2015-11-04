package it.unipi.di.acube.smaph;

import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class QueryInformation {
	public boolean includeSourceNormalSearch;
	public boolean includeSourceWikiSearch;
	public boolean includeSourceSnippets;
	public HashMap<Tag, String[]> entityToBoldS2S3;
	public HashMap<String, Tag> boldToEntityS1;
	public HashMap<Tag, List<String>> tagToBoldsS6;
	public Double webTotalNS;
	public List<String> allBoldsNS;
	public HashMap<Integer, Integer> idToRankNS;
	public double webTotalWS;
	public List<Pair<String, Integer>> bingBoldsAndRankNS;
	public HashMap<String, Pair<Integer, Integer>> annTitlesToIdAndRankWS;
	public List<Pair<String, Integer>> bingBoldsAndRankWS;
	public Set<Tag> candidatesSA;
	public Set<Tag> candidatesNS;
	public Set<Tag> candidatesWS;
	public int resultsCount;
	public HashMap<Tag, List<String>> tagToMentionsSA;
	public HashMap<Tag, List<String>> tagToBoldsSA;
	public HashMap<Tag, List<Integer>> tagToRanksSA;
	public HashMap<Tag, List<HashMap<String, Double>>> tagToAdditionalInfosSA;
	
	private Set<Tag> allCandidates = null;
	
	public Set<Tag> allCandidates() {
		if (allCandidates == null){
			allCandidates= new HashSet<Tag>();
			if (includeSourceSnippets)
				allCandidates.addAll(candidatesSA);
			if (includeSourceNormalSearch)
				allCandidates.addAll(candidatesNS);
			if (includeSourceWikiSearch)
				allCandidates.addAll(candidatesWS);
		}
		return allCandidates;
	}
}
