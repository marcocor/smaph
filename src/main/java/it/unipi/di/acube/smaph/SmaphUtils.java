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

import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.Vector;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

import org.aksw.gerbil.io.nif.NIFWriter;
import org.aksw.gerbil.io.nif.impl.TurtleNIFWriter;
import org.aksw.gerbil.transfer.nif.data.DocumentImpl;
import org.aksw.gerbil.transfer.nif.data.NamedEntity;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang3.tuple.ImmutableTriple;
import org.apache.commons.lang3.tuple.Triple;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.tartarus.snowball.ext.EnglishStemmer;

public class SmaphUtils {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	public static final String BASE_DBPEDIA_URI = "http://dbpedia.org/resource/";
	public static final String BASE_WIKIPEDIA_URI = "http://en.wikipedia.org/wiki/";
	public static final String WIKITITLE_ENDPAR_REGEX = "\\s*\\([^\\)]*\\)\\s*$";

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

	public static double weightedGeometricAverage(double[] vals, double[] weights) {
		if (vals.length != weights.length)
			throw new IllegalArgumentException();

		double num = 0;
		double denum = 0;

		for (int i = 0; i < vals.length; i++) {
			num += Math.log(vals[i]) * weights[i];
			denum += weights[i];
		}

		return Math.exp(num / denum);
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
	 * @param base
	 * @param ftrId
	 * @return a new feature vector composed by base with the addition of ftrId.
	 */
	public static int[] addFtrVect(int[] base, int ftrId) {
		if (base == null)
			return new int[] { ftrId };
		else {
			if (ArrayUtils.contains(base, ftrId))
				throw new IllegalArgumentException("Trying to add a feature to a vector that already contains it.");
			int[] newVect = new int[base.length + 1];
			System.arraycopy(base, 0, newVect, 0, base.length);
			newVect[newVect.length - 1] = ftrId;
			Arrays.sort(newVect);
			return newVect;
		}
	}

	/**
	 * @param features
	 * @param ftrToRemove
	 * @return a new feature vector composed by base without ftrId.
	 */
	public static int[] removeFtrVect(int[] features, int ftrToRemove) {
		int[] newFtrVect = new int[features.length -1];
		int j=0;
		for (int i=0; i<features.length; i++)
			if (features[i] == ftrToRemove)
				continue;
			else
				newFtrVect[j++] = features[i];
		if (j != newFtrVect.length)
			throw new IllegalArgumentException("Feature "+ ftrToRemove+" is not present and cannot be removed.");
		Arrays.sort(newFtrVect);
		return newFtrVect;
	}

	/**
	 * @param ftrVectorStr a list of comma-separated integers (e.g. 1,5,6,7)
	 * @return a feature vector with features provided as input.
	 */
	public static int[] strToFeatureVector(String ftrVectorStr) {
		if (ftrVectorStr.isEmpty())
			return new int[] {};
		String[] tokens = ftrVectorStr.split(",");
		int[] ftrVect = new int[tokens.length];
		for (int j = 0; j < tokens.length; j++)
			ftrVect[j] = Integer.parseInt(tokens[j]);
		Arrays.sort(ftrVect);
		return ftrVect;
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
	private static String stemString(String str, EnglishStemmer stemmer) {
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
		gzip.write(str.getBytes("utf-8"));
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
		return new String(IOUtils.toByteArray(gis), "utf-8");
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

	public static List<String> findSegmentsStrings(String text) {
		Vector<String> words = new Vector<String>();
		for (Pair<Integer, Integer> startEnd : findTokensPosition(text))
			words.add(text.substring(startEnd.first, startEnd.second));
		Vector<String> segments = new Vector<String>();
		for (int start = 0; start < words.size(); start++) {
			for (int end = 0; end < words.size(); end++){
				if (start <= end) {
					String segment = "";
					for (int i = start; i <= end; i++) {
						segment += words.get(i);
						if (i != end)
							segment += " ";
					}
					segments.add(segment);
				}
			}
		}
		return segments;
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
		if (limit >= 0 && sequences.size() >= limit)
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
			HashMap<Tag, List<String>> tagToBolds, Set<Tag> entityToKeep) {
		HashMap<Tag, String[]> res = new HashMap<>();
		for (Tag t : tagToBolds.keySet())
			if (entityToKeep == null || entityToKeep.contains(t))
				res.put(t, tagToBolds.get(t).toArray(new String[]{}));
		return res;
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

	public static <T> List<T> sorted(Collection<T> c, Comparator<T> comp) {
		List<T> list = new ArrayList<T>(c);
		Collections.sort(list, comp);
		return list;
	}
	public static <T extends Comparable<? super T>> List<T> sorted(Collection<T> c) {
		return sorted(c, null);
	}

	public static String removeTrailingParenthetical(String title) {
		return title.replaceAll(WIKITITLE_ENDPAR_REGEX, "");
	}

	public static JSONObject httpQueryJson(String urlAddr) {
		String resultStr = null;
		try {
			URL url = new URL(urlAddr);
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");

			if (conn.getResponseCode() != 200) {
				Scanner s = new Scanner(conn.getErrorStream())
				.useDelimiter("\\A");
				LOG.error("Got HTTP error {}. Message is: {}",
						conn.getResponseCode(), s.next());
				s.close();
			}

			Scanner s = new Scanner(conn.getInputStream())
			.useDelimiter("\\A");
			resultStr = s.hasNext() ? s.next() : "";

			return new JSONObject(resultStr);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static double computeAvgRank(List<Integer> positions, int resultsCount) {
		if (resultsCount==0) return 1;
		float avg = 0;
		for (int pos : positions)
			avg += (float)pos/(float)resultsCount;
		avg += resultsCount - positions.size();

		avg /= resultsCount;
		return avg;
	}

	public static double getFrequency(int occurrences, int resultsCount) {
		return (float) occurrences / (float) resultsCount;
	}

	public static <T1, T2> HashMap<T1, T2> inverseMap(HashMap<T2, T1> map) {
		return (HashMap<T1, T2>) map.entrySet().stream().collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));
	}

	private static void populateBindingsRec(List<Tag> chosenCandidates, List<List<Tag>> candidates, List<List<Tag>> bindings,
			int maxBindings) {
		if (maxBindings > 0 && bindings.size() >= maxBindings)
			return;
		if (chosenCandidates.size() == candidates.size()) {
			bindings.add(new Vector<Tag>(chosenCandidates));
			return;
		}
		List<Tag> candidatesToExpand = candidates.get(chosenCandidates.size());
		for (Tag candidate : candidatesToExpand) {
			List<Tag> nextChosenCandidates = new Vector<>(chosenCandidates);
			nextChosenCandidates.add(candidate);
			populateBindingsRec(nextChosenCandidates, candidates, bindings, maxBindings);
		}
	}

	/**
	 * @param candidates for each segment, the list of candidates it may be linked to
	 * @param maxBindings the maximum number of returned bindings (ignored if less than 1)
	 * @return the possible bindings for a single segmentations
	 */
	public static List<List<Tag>> getBindings(List<List<Tag>> candidates, int maxBindings) {
		List<List<Tag>> bindings = new Vector<List<Tag>>();
		List<Tag> chosenCandidates = new Vector<Tag>();
		populateBindingsRec(chosenCandidates, candidates, bindings, maxBindings);
		return bindings;
	}
	

	public static String getDBPediaURI(String title) {
		return BASE_DBPEDIA_URI + WikipediaApiInterface.normalize(title);
	}

	public static String getWikipediaURI(String title) {
		try {
	        return BASE_WIKIPEDIA_URI + URLEncoder.encode(title, "utf8").replace("+", "%20");
        } catch (UnsupportedEncodingException e) {
	        throw new RuntimeException(e);
        }
	}

	public static void exportToNif(A2WDataset ds, String baseUri, WikipediaApiInterface wikiApi, OutputStream outputStream) {
		List<org.aksw.gerbil.transfer.nif.Document> documents = new Vector<>();

		for (int i = 0; i < ds.getSize(); i++) {
			String text = ds.getTextInstanceList().get(i);
			org.aksw.gerbil.transfer.nif.Document d = new DocumentImpl(text, baseUri + "/doc" + i);

			for (it.unipi.di.acube.batframework.data.Annotation a : ds.getA2WGoldStandardList().get(i)) {
				String title;
				try {
					title = wikiApi.getTitlebyId(a.getConcept());
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
				d.addMarking(new NamedEntity(a.getPosition(), a.getLength(), getDBPediaURI(title)));
			}
			documents.add(d);
		}
		NIFWriter writer = new TurtleNIFWriter();
		writer.writeNIF(documents, outputStream);
	}
}
