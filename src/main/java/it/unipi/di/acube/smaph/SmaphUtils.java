/**
 *  Copyright 2014 Marco Cornolti
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package it.unipi.di.acube.smaph;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.learn.featurePacks.AnnotationFeaturePack;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.models.linkback.annotationRegressor.AnnotationRegressor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Vector;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.tartarus.snowball.ext.EnglishStemmer;

public class SmaphUtils {

	/**
	 * For each word of bold, finds the word in query that has the minimum edit
	 * distance, normalized by the word length. Returns the average of those
	 * distances.
	 * 
	 * @param query
	 *            a query.
	 * @param bold
	 *            a bold.
	 * @return the averaged normalized word-by-word edit distance of bold
	 *         against query.
	 */
	public static double getMinEditDist(String query, String bold) {
		return getMinEditDist(query, bold, null);
	}

	/**
	 * For each word of bold, finds the word in query that has the minimum edit
	 * distance, normalized by the word length. Put that word in minTokens.
	 * Returns the average of those distances.
	 * 
	 * @param query
	 *            a query.
	 * @param bold
	 *            a bold.
	 * @param minTokens
	 *            the tokens of query having minimum edit distance.
	 * @return the averaged normalized word-by-word edit distance of bold
	 *         against query.
	 */
	public static double getMinEditDist(String query, String bold,
			List<String> minTokens) {
		List<String> tokensQ = tokenize(query);
		List<String> tokensB = tokenize(bold);

		if (tokensB.size() == 0 || tokensQ.size() == 0)
			return 1;

		float avgMinDist = 0;
		for (String tokenB : tokensB) {
			float minDist = Float.MAX_VALUE;
			String bestQToken = null;
			for (String tokenQ : tokensQ) {
				float relLev = getNormEditDistance(tokenB, tokenQ);
				if (relLev < minDist) {
					minDist = relLev;
					bestQToken = tokenQ;
				}
			}
			if (minTokens != null)
				minTokens.add(bestQToken);
			avgMinDist += minDist;
		}
		return avgMinDist / tokensB.size();
	}

	/**
	 * @param tokenB
	 *            a word.
	 * @param tokenQ
	 *            another word.
	 * @return the normalized edit distance between tokenB and tokenQ.
	 */
	public static float getNormEditDistance(String tokenB, String tokenQ) {
		if (tokenQ.isEmpty() || tokenB.isEmpty())
			return 1;
		int lev = StringUtils.getLevenshteinDistance(tokenB, tokenQ);
		return (float) lev / (float) Math.max(tokenB.length(), tokenQ.length());
	}

	public static float getNormEditDistanceLC(String tokenB, String tokenQ) {
		tokenB = tokenB.replaceAll("\\W+", " ").toLowerCase();
		tokenQ = tokenQ.replaceAll("\\W+", " ").toLowerCase();
		return getNormEditDistance(tokenB, tokenQ);
	}

	/**
	 * @param title
	 *            the title of a Wikipedia page.
	 * @return true iff the title is that of a regular page.
	 */
	public static boolean acceptWikipediaTitle(String title) {
		// TODO: this can definitely be done in a cleaner way.
		return !(title.startsWith("Talk:") || title.startsWith("Special:")
				|| title.startsWith("Portal:")
				|| title.startsWith("Wikipedia:")
				|| title.startsWith("Template:")
				|| title.startsWith("Wikipedia_talk:")
				|| title.startsWith("File:") || title.startsWith("User:")
				|| title.startsWith("Category:") || title.startsWith("List") || title
					.contains("(disambiguation)"));
	}

	/**
	 * @param ftrCount
	 *            the number of features.
	 * @param excludedFeatures
	 *            the features that have to be excluded from the feature list.
	 * @return a vector containing all feature ids from 1 to ftrCount
	 *         (included), except those in excludedFeatures.
	 */
	public static int[] getAllFtrVect(int ftrCount, int[] excludedFeatures) {
		Arrays.sort(excludedFeatures);
		Vector<Integer> res = new Vector<>();
		for (int i = 1; i < ftrCount + 1; i++)
			if (Arrays.binarySearch(excludedFeatures, i) < 0)
				res.add(i);
		return ArrayUtils.toPrimitive(res.toArray(new Integer[] {}));
	}

	/**
	 * @param ftrCount
	 *            the number of features.
	 * @return a vector containing all feature ids from 1 to ftrCount (included.
	 */
	public static int[] getAllFtrVect(int ftrCount) {
		return getAllFtrVect(ftrCount, new int[0]);
	}

	/**
	 * Turns a list of pairs <b,r>, where b is a bold and r is the position in
	 * which the bold occurred, to the list of bolds and the hashmap between a
	 * position and the list of bolds occurring in that position.
	 * 
	 * @param boldAndRanks
	 *            a list of pairs <b,r>, where b is a bold and r is the position
	 *            in which the bold occurred.
	 * @param positions
	 *            where to store the mapping between a position (rank) and all
	 *            bolds that appear in that position.
	 * @param bolds
	 *            where to store the bolds.
	 */
	public static void mapRankToBoldsLC(
			List<Pair<String, Integer>> boldAndRanks,
			HashMap<Integer, HashSet<String>> positions, HashSet<String> bolds) {

		for (Pair<String, Integer> boldAndRank : boldAndRanks) {
			String spot = boldAndRank.first.toLowerCase();
			int rank = boldAndRank.second;
			if (bolds != null)
				bolds.add(spot);
			if (positions != null) {
				if (!positions.containsKey(rank))
					positions.put(rank, new HashSet<String>());
				positions.get(rank).add(spot);
			}
		}

	}

	/**
	 * Turns a list of pairs <b,r>, where b is a bold and r is the position in
	 * which the bold occurred, to a mapping from a bold to the positions in
	 * which the bolds occurred.
	 * 
	 * @param boldAndRanks
	 *            a list of pairs <b,r>, where b is a bold and r is the position
	 *            in which the bold occurred.
	 * @return a mapping from a bold to the positions in which the bold
	 *         occurred.
	 */
	public static HashMap<String, HashSet<Integer>> findPositionsLC(
			List<Pair<String, Integer>> boldAndRanks) {
		HashMap<String, HashSet<Integer>> positions = new HashMap<>();
		for (Pair<String, Integer> boldAndRank : boldAndRanks) {
			String bold = boldAndRank.first.toLowerCase();
			int rank = boldAndRank.second;
			if (!positions.containsKey(bold))
				positions.put(bold, new HashSet<Integer>());
			positions.get(bold).add(rank);
		}
		return positions;
	}

	/**
	 * Given a string, replaces all words with their stemmed version.
	 * 
	 * @param str
	 *            a string.
	 * @param stemmer
	 *            the stemmer.
	 * @return str with all words stemmed.
	 */
	public static String stemString(String str, EnglishStemmer stemmer) {
		String stemmedString = "";
		String[] words = str.split("\\s+");
		for (int i = 0; i < words.length; i++) {
			String word = words[i];
			stemmer.setCurrent(word);
			stemmer.stem();
			stemmedString += stemmer.getCurrent();
			if (i != words.length)
				stemmedString += " ";
		}
		return stemmedString;
	}

	/**
	 * Compress a string with GZip.
	 * 
	 * @param str
	 *            the string.
	 * @return the compressed string.
	 * @throws IOException
	 *             if something went wrong during compression.
	 */
	public static byte[] compress(String str) throws IOException {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		GZIPOutputStream gzip = new GZIPOutputStream(out);
		gzip.write(str.getBytes());
		gzip.close();
		return out.toByteArray();
	}

	/**
	 * Decompress a GZipped string.
	 * 
	 * @param compressed
	 *            the sequence of bytes
	 * @return the decompressed string.
	 * @throws IOException
	 *             if something went wrong during decompression.
	 */
	public static String decompress(byte[] compressed) throws IOException {
		GZIPInputStream gis = new GZIPInputStream(new ByteArrayInputStream(
				compressed));
		return new String(IOUtils.toByteArray(gis));
	}

	public static List<String> tokenize(String text) {
		int start = -1;
		Vector<String> tokens = new Vector<>();
		for (int i = 0; i < text.length(); i++) {
			if (Character.isWhitespace(text.charAt(i))) {
				if (start != -1) {
					tokens.add(text.substring(start, i));
					start = -1;
				}
			} else if (start == -1)
				start = i;
		}
		if (start != -1)
			tokens.add(text.substring(start));

		return tokens;
	}

	public static List<Pair<Integer, Integer>> findTokensPosition(String text) {
		text = text.replaceAll("\\W", " ").replaceAll("\\s", " ");
		List<Pair<Integer, Integer>> positions = new Vector<>();
		int idx = 0;
		while (idx < text.length()) {
			while (idx < text.length() && text.charAt(idx) == ' ')
				idx++;
			if (idx == text.length())
				break;
			int start = idx;
			while (idx < text.length() && text.charAt(idx) != ' ')
				idx++;
			int end = idx;
			positions.add(new Pair<>(start, end));
		}
		return positions;
	}
	
	public static List<Pair<Integer, Integer>> findSegments(String text) {
		List<Pair<Integer, Integer>> tokens = findTokensPosition(text);
		List<Pair<Integer, Integer>> segments = new Vector<>();
		for (int n= 1; n<=tokens.size(); n++)
			for (int i=0;i<=tokens.size()-n;i++)
				segments.add(new Pair<Integer, Integer>(tokens.get(i).first, tokens.get(i+n-1).second));
		return segments;
	}
		
	private static void addBIOToken(int n, char token, String sequence,
			List<String> sequences, int limit) {
		if (sequences.size() >= limit)
			return;
		sequence += token;
		if (n > 0) {
			addBIOToken(n - 1, 'B', sequence, sequences, limit);
			if (token != 'O')
				addBIOToken(n - 1, 'I', sequence, sequences, limit);
			addBIOToken(n - 1, 'O', sequence, sequences, limit);
		} else
			sequences.add(sequence);
	}

	public static List<String> getBioSequences(int n, int limit) {
		List<String> sequences = new Vector<>();
		addBIOToken(n - 1, 'B', "", sequences, limit);
		addBIOToken(n - 1, 'O', "", sequences, limit);
		return sequences;
	}

	public static List<List<Pair<Integer, Integer>>> getSegmentations(
			String query, int maxBioSequence) {
		List<Pair<Integer, Integer>> qTokens = findTokensPosition(query);
		List<List<Pair<Integer, Integer>>> segmentations = new Vector<>();
		List<String> bioSequences = getBioSequences(qTokens.size(),
				maxBioSequence);
		for (String bioSequence : bioSequences) {
			int start = -1;
			int end = -1;
			List<Pair<Integer, Integer>> segmentation = new Vector<>();
			for (int i = 0; i < qTokens.size(); i++) {
				Pair<Integer, Integer> token = qTokens.get(i);
				if (start >= 0
						&& (bioSequence.charAt(i) == 'B' || bioSequence
								.charAt(i) == 'O')) {
					segmentation.add(new Pair<Integer, Integer>(start, end));
					start = -1;
				}
				if (bioSequence.charAt(i) == 'B'
						|| bioSequence.charAt(i) == 'I') {
					if (start == -1)
						start = token.first;
					end = token.second;
				}
			}
			if (start != -1)
				segmentation.add(new Pair<Integer, Integer>(start, end));
			segmentations.add(segmentation);
		}
		return segmentations;
	}
	
	public static HashMap<Tag, String[]> getEntitiesToBoldsList(
			HashMap<Tag, List<String>> tagToBoldsS6, Set<Tag> entityToKeep) {
		HashMap<Tag, String[]> res = new HashMap<>();
		for (Tag t : tagToBoldsS6.keySet())
			if (entityToKeep == null || entityToKeep.contains(t))
				res.put(t, tagToBoldsS6.get(t).toArray(new String[]{}));
		return res;
	}

	public static HashMap<Tag, String[]> getEntitiesToBolds(
			HashMap<String, Tag> boldToEntity, Set<Tag> entityToKeep) {
		HashMap<Tag, String[]> entityToTexts = new HashMap<>();
		for (String bold : boldToEntity.keySet()) {
			Tag tag = boldToEntity.get(bold);
			if (entityToKeep != null && !entityToKeep.contains(tag))
				continue;
			List<String> boldsForEntity = new Vector<>();
			if (entityToTexts.containsKey(tag))
				boldsForEntity.addAll(Arrays.asList(entityToTexts.get(tag)));
			boldsForEntity.add(bold);
			entityToTexts.put(tag, boldsForEntity.toArray(new String[] {}));

		}
		return entityToTexts;
	}

	public static HashMap<Tag, String> getEntitiesToTitles(
			Set<Tag> acceptedEntities, WikipediaApiInterface wikiApi) {
		HashMap<Tag, String> res = new HashMap<>();
		for (Tag t : acceptedEntities)
			try {
				res.put(t, wikiApi.getTitlebyId(t.getConcept()));
			} catch (IOException e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
		return res;
	}

	public static int getNonAlphanumericCharCount(String str) {
		int count = 0;
		for (char c : str.toCharArray())
			if (!((c >= 'a' &&c <= 'z') || (c >= 'A' &&c <= 'Z') || (c >= '0' &&c <= '9') || c == ' '))
				count ++;
		return count;
	}

	public static class ComparePairsByFirstElement<E extends Serializable, T extends Comparable<T> & Serializable> implements Comparator<Pair<T, E>> {
		@Override
		public int compare(Pair<T, E> o1, Pair<T, E> o2) {
			return o1.first.compareTo(o2.first);
		}
	}

	public static class ComparePairsBySecondElement<E extends Serializable, T extends Comparable<T> & Serializable> implements Comparator<Pair<E, T>> {
		@Override
		public int compare(Pair<E, T> o1, Pair<E, T> o2) {
			return o1.second.compareTo(o2.second);
		}
	}

	public static HashMap<Annotation, Double> predictBestScores(AnnotationRegressor ar, FeatureNormalizer fn,
			List<HashSet<Annotation>> allBindings, String query,
			HashMap<Tag, List<HashMap<String, Double>>> entityToFtrVects,
			HashMap<Tag, String[]> entitiesToBoldsS1,
			HashMap<Tag, String> entitiesToTitles, EnglishStemmer englishStemmer) {
		
		List<Annotation> annotations = new Vector<>();
		for (HashSet<Annotation> binding : allBindings)
			for (Annotation a : binding)
				annotations.add(a);

		HashMap<Annotation, Double> annsToRegressorScore = new HashMap<>();
			for (Annotation ann : annotations){
				double bestScore = Double.NEGATIVE_INFINITY;
				for (HashMap<String, Double> entityFtrs : entityToFtrVects.get(new Tag(ann.getConcept())))
					bestScore = Math.max(bestScore, ar.predictScore(new AnnotationFeaturePack(ann, query, new EnglishStemmer(),
							entityFtrs, entitiesToBoldsS1, entitiesToTitles), fn));
				annsToRegressorScore.put(ann, bestScore);
			}

		return annsToRegressorScore;
	}

	/**
	 * @param tokensA
	 * @param tokensB
	 * @return true if tokensA is a strict sublist of tokensB (i.e. |tokensA| < |tokensB| and there are two indexes i and j s.t. tokensA.equals(tokensB.subList(i, j))).
	 */
	public static boolean isSubToken(List<String> tokensA, List<String> tokensB){
		if (tokensA.size() >= tokensB.size())
			return false;
		for (int i=0 ; i<=tokensB.size() - tokensA.size(); i++)
			if (tokensA.equals(tokensB.subList(i, i+tokensA.size())))
				return true;
		return false;
	}
	
	
	/**
	 * @param bolds
	 *            a list of bolds
	 * @param bold
	 *            a bold
	 * @return the proportion between the number of times bold appears in the
	 *         list and the number of times in which shorter bolds having at
	 *         least one word in common appear in the list.
	 */
	public static double getFragmentation(List<String> bolds, String bold) {
		int boldCount = 0;
		int fragmentsCount = 0;
		List<String> tokensBold = tokenize(stemString(bold, new EnglishStemmer()));

		for (String b : bolds) {
			List<String> tokensB = tokenize(stemString(b, new EnglishStemmer()));
			if (tokensBold.equals(tokensB))
				boldCount++;
			else {
				if (isSubToken(tokensB, tokensBold))
				fragmentsCount ++;
				/*if (tokensB.size() < tokensBold.size()) {
					boolean found = false;
					for (String tokenB : tokensB)
						for (String tokenBold : tokensBold)
							if (tokenB.equals(tokenBold)) {
								found = true;
								break;
							}
					if (found)
						fragmentsCount++;
				}*/
			}
		}
		if (boldCount == 0)
			return 0.0;
		return (double) boldCount / (double) (Math.pow(fragmentsCount, 1.4) + boldCount);
	}
	
	/**
	 * @param bolds
	 *            a list of bolds
	 * @param bold
	 *            a bold
	 * @return the proportion between the number of times bold appears in the
	 *         list and the number of times in which longer bolds containing all
	 *         words of bold appear in the list.
	 */
	public static double getAggregation(List<String> bolds, String bold) {
		int boldCount = 0;
		int fragmentsCount = 0;
		List<String> tokensBold = tokenize(stemString(bold, new EnglishStemmer()));

		for (String b : bolds) {
			List<String> tokensB = tokenize(stemString(b, new EnglishStemmer()));
			if (tokensBold.equals(tokensB))
				boldCount++;
			else {
				if (isSubToken(tokensBold, tokensB))
					fragmentsCount ++;
				/*if (tokensB.size() > tokensBold.size()) {
					boolean cover = true;
					for (String tokenBold : tokensBold)
						if (!tokensB.contains(tokenBold)){
							cover = false;
							break;
						}
					if (cover)
						fragmentsCount++;
				}*/
			}
		}
		if (boldCount == 0)
			return 0.0;
		return (double) boldCount / (double) (Math.pow(fragmentsCount, 1.4) + boldCount);
	}


	public static List<String> boldPairsToListLC(
			List<Pair<String, Integer>> boldAndRanks) {
		List<String> res = new Vector<>();
		for (Pair<String, Integer> boldAndRank : boldAndRanks)
			res.add(boldAndRank.first.toLowerCase());
		return res;
	}
	

	
	public static Triple<Double, Double, Double> getMinMaxAvg(List<Double> values) {
		if (values.isEmpty())
			return new ImmutableTriple<Double, Double, Double>(0.0, 0.0, 0.0);
		
		double minVal = Double.POSITIVE_INFINITY;
		double maxVal = Double.NEGATIVE_INFINITY;
		double avgVal = 0.0;
		for (double v : values) {
			minVal = Math.min(v, minVal);
			maxVal = Math.max(v, maxVal);
			avgVal += v / values.size();
		}

		return new ImmutableTriple<Double, Double, Double>(minVal, maxVal,
				avgVal);
	}

	public static HashSet<ScoredAnnotation> collapseBinding(
			HashSet<ScoredAnnotation> binding) {
		if (binding.size() <= 1)
			return binding;

		HashSet<ScoredAnnotation> res = new HashSet<>();
		Vector<ScoredAnnotation> bindingOrdered = new Vector<>(binding);
		Collections.sort(bindingOrdered);
		ScoredAnnotation firstEqual = bindingOrdered.get(0);
		float score = 0f;
		int equalCount = 0;
		for (int i = 0; i < bindingOrdered.size(); i++) {
			ScoredAnnotation nextAnn = (i == bindingOrdered.size() - 1) ? null
					: bindingOrdered.get(i + 1);
			ScoredAnnotation annI = bindingOrdered.get(i);
			score += annI.getScore();
			equalCount++;
			if (nextAnn == null
					|| nextAnn.getConcept() != firstEqual.getConcept()) {
				res.add(new ScoredAnnotation(firstEqual.getPosition(), annI
						.getPosition()
						+ annI.getLength()
						- firstEqual.getPosition(), firstEqual.getConcept(),
						score / equalCount));
				firstEqual = nextAnn;
				score = 0;
				equalCount = 0;
			}
		}
		return res;
	}
	
	public static <T extends Comparable<? super T>> List<T> sorted(Collection<T> c, Comparator<T> comp) {
		List<T> list = new ArrayList<T>(c);
		Collections.sort(list, comp);
		return list;
	}
	public static <T extends Comparable<? super T>> List<T> sorted(Collection<T> c) {
		return sorted(c, null);
	}

}

