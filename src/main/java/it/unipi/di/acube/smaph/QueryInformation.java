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
	public HashMap<Tag, String[]> entityToBoldNS;
	public Double webTotalNS;
	public List<String> allBoldsNS;
	public HashMap<Integer, Integer> idToRankNS;
	public List<Pair<String, Integer>> bingBoldsAndRankNS;
	public int resultsCountNS;
	public double webTotalWS;
	public double resultsCountWS;
	public HashMap<String, Pair<Integer, Integer>> annTitlesToIdAndRankWS;
	public List<Pair<String, Integer>> bingBoldsAndRankWS;
	public HashMap<Integer, Integer> idToRankWS;
	public HashMap<Tag, List<String>> entityToBoldsSA;
	public HashMap<Tag, List<String>> entityToMentionsSA;
	public HashMap<Tag, List<Integer>> entityToRanksSA;
	public HashMap<Tag, List<HashMap<String, Double>>> entityToAdditionalInfosSA;
	public Set<Tag> candidatesSA;
	public Set<Tag> candidatesNS;
	public Set<Tag> candidatesWS;
	
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
