package it.unipi.di.acube.smaph;

import static org.junit.Assert.*;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.smaph.SmaphUtils;

import org.junit.Test;

public class SmaphUtilsTest {
	private static final double DELTA = 1e-4;

	@Test
	public void testGetMinEditDist() {
		{
			assertEquals((0 + 4.0 / 5.0 + 7.0 / 10.0) / 3.0,
					SmaphUtils.getMinEditDist("armstrong moon",
							"Armstrong World Industries"), DELTA);

			List<String> minTokens = new Vector<>();
			double res = SmaphUtils.getMinEditDist("armstrong moon",
					"Armstrong World Industries", minTokens);
			assertEquals((0 + 4.0 / 5.0 + 7.0 / 10.0) / 3.0, res, DELTA);
			assertEquals(3, minTokens.size());
			assertEquals(true, minTokens.get(0).equals("armstrong"));
			assertEquals(true, minTokens.get(1).equals("moon"));
			assertEquals(true, minTokens.get(2).equals("armstrong"));
		}
		{
			assertEquals(0, SmaphUtils.getMinEditDist("armstrong moon",
					"armstrong moon"), 0.0);

			List<String> minTokens = new Vector<>();
			double res = SmaphUtils.getMinEditDist("armstrong moon",
					"armstrong moon", minTokens);
			assertEquals(0, res, 0.0);
			assertEquals(2, minTokens.size());
			assertEquals(true, minTokens.get(0).equals("armstrong"));
			assertEquals(true, minTokens.get(1).equals("moon"));
		}
		{
			assertEquals(1.0 / 4.0,
					SmaphUtils.getMinEditDist("moooon moan", "moon"), DELTA);

			List<String> minTokens = new Vector<>();
			double res = SmaphUtils.getMinEditDist("moooon moan", "moon",
					minTokens);
			assertEquals(1.0 / 4.0, res, DELTA);
			assertEquals(1, minTokens.size());
			assertEquals(true, minTokens.get(0).equals("moan"));
		}

	}

	@Test
	public void testGetNormEditDistance() {
		assertEquals(0.0,
				SmaphUtils.getNormEditDistance("armstrong", "armstrong"), 0.0);
		assertEquals(8.0 / 9.0,
				SmaphUtils.getNormEditDistance("world", "armstrong"), DELTA);
		assertEquals(4.0 / 5.0,
				SmaphUtils.getNormEditDistance("world", "moon"), DELTA);
		assertEquals(7.0 / 10.0,
				SmaphUtils.getNormEditDistance("industries", "armstrong"),
				DELTA);
		assertEquals(1.0, SmaphUtils.getNormEditDistance("industries", "moon"),
				DELTA);
	}

	@Test
	public void testGetBioSequences() {
		{
			List<String> seq1 = SmaphUtils.getBioSequences(1, 100);
			assertEquals(seq1.size(), 2);
			assertEquals(true, seq1.contains("B"));
			assertEquals(true, seq1.contains("O"));
		}
		{
			List<String> seq2 = SmaphUtils.getBioSequences(2, 100);
			assertEquals(seq2.size(), 5);
			assertEquals(true, seq2.contains("BB"));
			assertEquals(true, seq2.contains("BI"));
			assertEquals(true, seq2.contains("BO"));
			assertEquals(true, seq2.contains("OB"));
			assertEquals(true, seq2.contains("OO"));
		}
		{
			List<String> seq3 = SmaphUtils.getBioSequences(3, 100);
			assertEquals(seq3.size(), 13);
			assertEquals(true, seq3.contains("BBB"));
			assertEquals(true, seq3.contains("BBI"));
			assertEquals(true, seq3.contains("BBO"));
			assertEquals(true, seq3.contains("BIB"));
			assertEquals(true, seq3.contains("BII"));
			assertEquals(true, seq3.contains("BIO"));
			assertEquals(true, seq3.contains("BOB"));
			assertEquals(true, seq3.contains("BOO"));
			assertEquals(true, seq3.contains("OBB"));
			assertEquals(true, seq3.contains("OBI"));
			assertEquals(true, seq3.contains("OBO"));
			assertEquals(true, seq3.contains("OOB"));
			assertEquals(true, seq3.contains("OOO"));
		}

	}

	@Test
	public void testFindTokensPosition() throws Exception {
		{
			List<Pair<Integer, Integer>> res = SmaphUtils
					.findTokensPosition("all your base are belong to us.");
			assertEquals(7, res.size());
			assertEquals(res.get(0).first.intValue(), 0);
			assertEquals(res.get(0).second.intValue(), 3);
			assertEquals(res.get(1).first.intValue(), 4);
			assertEquals(res.get(1).second.intValue(), 8);
			assertEquals(res.get(2).first.intValue(), 9);
			assertEquals(res.get(2).second.intValue(), 13);
			assertEquals(res.get(3).first.intValue(), 14);
			assertEquals(res.get(3).second.intValue(), 17);
			assertEquals(res.get(4).first.intValue(), 18);
			assertEquals(res.get(4).second.intValue(), 24);
			assertEquals(res.get(5).first.intValue(), 25);
			assertEquals(res.get(5).second.intValue(), 27);
			assertEquals(res.get(6).first.intValue(), 28);
			assertEquals(res.get(6).second.intValue(), 30);
		}
		{
			List<Pair<Integer, Integer>> res = SmaphUtils
					.findTokensPosition("  lulz   hahhh");
			assertEquals(2, res.size());
			assertEquals(res.get(0).first.intValue(), 2);
			assertEquals(res.get(0).second.intValue(), 6);
			assertEquals(res.get(1).first.intValue(), 9);
			assertEquals(res.get(1).second.intValue(), 14);
		}
		{
			List<Pair<Integer, Integer>> res = SmaphUtils
					.findTokensPosition("  lulz   hahhh  !! ");
			assertEquals(2, res.size());
			assertEquals(res.get(0).first.intValue(), 2);
			assertEquals(res.get(0).second.intValue(), 6);
			assertEquals(res.get(1).first.intValue(), 9);
			assertEquals(res.get(1).second.intValue(), 14);

		}
	}

	@Test
	public void testGetSegmentations() throws Exception {
		String query = "  all , 0your   base!!  ";
		List<List<Pair<Integer, Integer>>> segmentations = SmaphUtils
				.getSegmentations(query, 1000);
		assertEquals(13, segmentations.size());
		HashSet<Integer> verified = new HashSet<>();

		for (List<Pair<Integer, Integer>> segmentationIdx : segmentations) {
			List<String> segmentationStr = new Vector<>();
			for (Pair<Integer, Integer> p : segmentationIdx)
				segmentationStr.add(query.substring(p.first, p.second));

			if (segmentationStr.size() == 3
					&& segmentationStr.get(0).equals("all")
					&& segmentationStr.get(1).equals("0your")
					&& segmentationStr.get(2).equals("base"))
				verified.add(0);
			else if (segmentationStr.size() == 2
					&& segmentationStr.get(0).equals("all")
					&& segmentationStr.get(1).equals("0your   base"))
				verified.add(1);
			else if (segmentationStr.size() == 2
					&& segmentationStr.get(0).equals("all")
					&& segmentationStr.get(1).equals("0your"))
				verified.add(2);
			else if (segmentationStr.size() == 2
					&& segmentationStr.get(0).equals("all , 0your")
					&& segmentationStr.get(1).equals("base"))
				verified.add(3);
			else if (segmentationStr.size() == 1
					&& segmentationStr.get(0).equals("all , 0your   base"))
				verified.add(4);
			else if (segmentationStr.size() == 1
					&& segmentationStr.get(0).equals("all , 0your"))
				verified.add(5);
			else if (segmentationStr.size() == 2
					&& segmentationStr.get(0).equals("all")
					&& segmentationStr.get(1).equals("base"))
				verified.add(6);
			else if (segmentationStr.size() == 1
					&& segmentationStr.get(0).equals("all"))
				verified.add(7);
			else if (segmentationStr.size() == 2
					&& segmentationStr.get(0).equals("0your")
					&& segmentationStr.get(1).equals("base"))
				verified.add(8);
			else if (segmentationStr.size() == 1
					&& segmentationStr.get(0).equals("0your   base"))
				verified.add(9);
			else if (segmentationStr.size() == 1
					&& segmentationStr.get(0).equals("0your"))
				verified.add(10);
			else if (segmentationStr.size() == 1
					&& segmentationStr.get(0).equals("base"))
				verified.add(11);
			else if (segmentationStr.size() == 0)
				verified.add(12);

		}

		assertEquals(true, verified.contains(0));
		assertEquals(true, verified.contains(1));
		assertEquals(true, verified.contains(2));
		assertEquals(true, verified.contains(3));
		assertEquals(true, verified.contains(4));
		assertEquals(true, verified.contains(5));
		assertEquals(true, verified.contains(6));
		assertEquals(true, verified.contains(7));
		assertEquals(true, verified.contains(8));
		assertEquals(true, verified.contains(9));
		assertEquals(true, verified.contains(10));
		assertEquals(true, verified.contains(11));
		assertEquals(true, verified.contains(12));
	}

	@Test
	public void testFindSegments() throws Exception {
		List<Pair<Integer, Integer>> segments = SmaphUtils.findSegments("  aaa bbb   ccc   ");
		assertEquals(6, segments.size());
		
		assertEquals(2, segments.get(0).first.intValue());
		assertEquals(5, segments.get(0).second.intValue());
		assertEquals(6, segments.get(1).first.intValue());
		assertEquals(9, segments.get(1).second.intValue());
		assertEquals(12, segments.get(2).first.intValue());
		assertEquals(15, segments.get(2).second.intValue());
		assertEquals(2, segments.get(3).first.intValue());
		assertEquals(9, segments.get(3).second.intValue());
		assertEquals(6, segments.get(4).first.intValue());
		assertEquals(15, segments.get(4).second.intValue());
		assertEquals(2, segments.get(5).first.intValue());
		assertEquals(15, segments.get(5).second.intValue());
	}

	@Test
	public void testGetNonAlphanumericCharCount() throws Exception {
		assertEquals(0, SmaphUtils.getNonAlphanumericCharCount(" dd    34"));
		assertEquals(1, SmaphUtils.getNonAlphanumericCharCount(" dd;34"));
		assertEquals(8, SmaphUtils.getNonAlphanumericCharCount(" dd;34.)*&*+^"));
	}

	@Test
	public void testBoldPairsToListLC() throws Exception {
		List<Pair<String,Integer>> boldAndRanks = new Vector<>();
		boldAndRanks.add(new Pair<String, Integer>("aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 1));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 6));
		boldAndRanks.add(new Pair<String, Integer>("bbb", 5));
		boldAndRanks.add(new Pair<String, Integer>("CCC", 5));
		boldAndRanks.add(new Pair<String, Integer>("ccc", 6));
		List<String> bolds = SmaphUtils.boldPairsToListLC(boldAndRanks);
		assertEquals(bolds.size(), 7);
		assertEquals(bolds.get(0), "aaa");
		assertEquals(bolds.get(1), "aaa");
		assertEquals(bolds.get(2), "aaa");
		assertEquals(bolds.get(3), "aaa");
		assertEquals(bolds.get(4), "bbb");
		assertEquals(bolds.get(5), "ccc");
		assertEquals(bolds.get(6), "ccc");
	}

	@Test
	public void testGetFragmentation() throws Exception {
		List<Pair<String, Integer>> boldAndRanks = new Vector<>();
		boldAndRanks.add(new Pair<String, Integer>("aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("aaa bbb", 1));
		boldAndRanks.add(new Pair<String, Integer>("aaa BBB", 1));
		boldAndRanks.add(new Pair<String, Integer>("aaa bbb", 4));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 6));
		boldAndRanks.add(new Pair<String, Integer>("bbb aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("CCC", 5));
		boldAndRanks.add(new Pair<String, Integer>("ccc", 6));
		boldAndRanks.add(new Pair<String, Integer>("bbb", 6));
		List<String> bolds = SmaphUtils.boldPairsToListLC(boldAndRanks);
		assertEquals(1.0, SmaphUtils.getFragmentation(bolds, "aaa"), DELTA);
		assertEquals(3.0 / 7.0,
				SmaphUtils.getFragmentation(bolds, "aaa ' bbb  "), DELTA);
		assertEquals(1.0 / 5.0, SmaphUtils.getFragmentation(bolds, "BBB aaa"),
				DELTA);
		assertEquals(1.0, SmaphUtils.getFragmentation(bolds, "ccc"), DELTA);
		assertEquals(1.0, SmaphUtils.getFragmentation(bolds, "bbb"), DELTA);
	}

	@Test
	public void testGetAggregation() throws Exception {
		List<Pair<String, Integer>> boldAndRanks = new Vector<>();
		boldAndRanks.add(new Pair<String, Integer>("aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("aaa bbb", 1));
		boldAndRanks.add(new Pair<String, Integer>("aaa BBB", 1));
		boldAndRanks.add(new Pair<String, Integer>("aaa bbb", 4));
		boldAndRanks.add(new Pair<String, Integer>("aaa bbb ccc", 9));
		boldAndRanks.add(new Pair<String, Integer>("aaa", 6));
		boldAndRanks.add(new Pair<String, Integer>("bbb aaa", 5));
		boldAndRanks.add(new Pair<String, Integer>("CCC", 5));
		boldAndRanks.add(new Pair<String, Integer>("ccc", 6));
		List<String> bolds = SmaphUtils.boldPairsToListLC(boldAndRanks);
		assertEquals(3.0 / 8.0, SmaphUtils.getAggregation(bolds, "aaa"),
				DELTA);
		assertEquals(3.0 / 4.0,
				SmaphUtils.getAggregation(bolds, "aaa ' bbb  "), DELTA);
		assertEquals(1.0 , SmaphUtils.getAggregation(bolds, "BBB aaa"),
				DELTA);
		assertEquals(2.0 / 3.0, SmaphUtils.getAggregation(bolds, "ccc"),
				DELTA);
		assertEquals(0.0, SmaphUtils.getAggregation(bolds, "bbb"), DELTA);
		assertEquals(1.0,
				SmaphUtils.getAggregation(bolds, "aaa   &^*# bbb CCC"), DELTA);
	}

	@Test
	public void testIsSubToken() throws Exception {
		List<String> tokens1 = new Vector<>();
		tokens1.add("aaa");
		tokens1.add("bbb");
		tokens1.add("ccc");
		tokens1.add("ddd");
		tokens1.add("eee");
		
		List<String> tokens2 = new Vector<>();
		tokens2.add("bbb");
		tokens2.add("ccc");
		tokens2.add("ddd");
		
		List<String> tokens3 = new Vector<>();
		tokens3.add("ccc");
		tokens3.add("eee");
		
		List<String> tokens4 = new Vector<>();
		tokens4.add("ccc");
		tokens4.add("ddd");
		tokens4.add("eee");

		List<String> tokens5 = new Vector<>();
		tokens5.add("aaa");
		
		assertEquals(false, SmaphUtils.isSubToken(tokens1, tokens1));
		assertEquals(false, SmaphUtils.isSubToken(tokens2, tokens2));
		assertEquals(false, SmaphUtils.isSubToken(tokens3, tokens3));
		assertEquals(false, SmaphUtils.isSubToken(tokens4, tokens4));
		assertEquals(false, SmaphUtils.isSubToken(tokens5, tokens5));
		
		assertEquals(true, SmaphUtils.isSubToken(tokens2, tokens1));
		assertEquals(false, SmaphUtils.isSubToken(tokens1, tokens2));
		
		assertEquals(false, SmaphUtils.isSubToken(tokens3, tokens1));
		assertEquals(false, SmaphUtils.isSubToken(tokens1, tokens3));
		
		assertEquals(true, SmaphUtils.isSubToken(tokens4, tokens1));
		assertEquals(false, SmaphUtils.isSubToken(tokens1, tokens4));

		assertEquals(true, SmaphUtils.isSubToken(tokens5, tokens1));
		assertEquals(false, SmaphUtils.isSubToken(tokens1, tokens5));
		
		assertEquals(false, SmaphUtils.isSubToken(tokens3, tokens2));
		assertEquals(false, SmaphUtils.isSubToken(tokens2, tokens3));
		
	}

	@Test
	public void testCollapseBinding() throws Exception {
		{
			HashSet<ScoredAnnotation> binding1 = new HashSet<>();
			binding1.add(new ScoredAnnotation(2, 4, 111,0.5f));
			binding1.add(new ScoredAnnotation(10, 4, 111,1.0f));
			binding1.add(new ScoredAnnotation(20, 4, 111,0.0f));

			HashSet<ScoredAnnotation> res1 = SmaphUtils.collapseBinding(binding1);
			Vector<ScoredAnnotation> res1Oreded = new Vector<>(res1);
			Collections.sort(res1Oreded);
			assertEquals(1, res1Oreded.size());
			assertEquals(2, res1Oreded.get(0).getPosition());
			assertEquals(22, res1Oreded.get(0).getLength());
			assertEquals(111, res1Oreded.get(0).getConcept());
			assertEquals(0.5f, res1Oreded.get(0).getScore(), DELTA);
		}

		{
			HashSet<ScoredAnnotation> binding1 = new HashSet<>();
			binding1.add(new ScoredAnnotation(2, 4, 111,0.0f));
			binding1.add(new ScoredAnnotation(10, 4, 111,0.0f));
			binding1.add(new ScoredAnnotation(12, 4, 111,1.0f));
			binding1.add(new ScoredAnnotation(20, 4, 111,1.0f));
			binding1.add(new ScoredAnnotation(30, 4, 222,0.7f));
			binding1.add(new ScoredAnnotation(40, 4, 333,0.5f));
			binding1.add(new ScoredAnnotation(50, 4, 333,0.0f));

			HashSet<ScoredAnnotation> res1 = SmaphUtils.collapseBinding(binding1);
			Vector<ScoredAnnotation> res1Oreded = new Vector<>(res1);
			Collections.sort(res1Oreded);
			assertEquals(3, res1Oreded.size());
			assertEquals(2, res1Oreded.get(0).getPosition());
			assertEquals(22, res1Oreded.get(0).getLength());
			assertEquals(111, res1Oreded.get(0).getConcept());
			assertEquals(0.5f, res1Oreded.get(0).getScore(), DELTA);
			assertEquals(30, res1Oreded.get(1).getPosition());
			assertEquals(4, res1Oreded.get(1).getLength());
			assertEquals(222, res1Oreded.get(1).getConcept());
			assertEquals(0.7f, res1Oreded.get(1).getScore(), DELTA);
			assertEquals(40, res1Oreded.get(2).getPosition());
			assertEquals(14, res1Oreded.get(2).getLength());
			assertEquals(333, res1Oreded.get(2).getConcept());
			assertEquals(0.25f, res1Oreded.get(2).getScore(), DELTA);
		}

		{
			HashSet<ScoredAnnotation> binding1 = new HashSet<>();
			binding1.add(new ScoredAnnotation(2, 4, 111, 0.7f));
			binding1.add(new ScoredAnnotation(20, 4, 222, 0.1f));
			binding1.add(new ScoredAnnotation(30, 4, 222, 0.3f));
			binding1.add(new ScoredAnnotation(40, 4, 333, 0.3f));
			binding1.add(new ScoredAnnotation(50, 4, 333, 0.5f));

			HashSet<ScoredAnnotation> res1 = SmaphUtils.collapseBinding(binding1);
			Vector<ScoredAnnotation> res1Oreded = new Vector<>(res1);
			Collections.sort(res1Oreded);
			assertEquals(3, res1Oreded.size());
			assertEquals(2, res1Oreded.get(0).getPosition());
			assertEquals(4, res1Oreded.get(0).getLength());
			assertEquals(111, res1Oreded.get(0).getConcept());
			assertEquals(0.7f, res1Oreded.get(0).getScore(), DELTA);
			assertEquals(20, res1Oreded.get(1).getPosition());
			assertEquals(14, res1Oreded.get(1).getLength());
			assertEquals(222, res1Oreded.get(1).getConcept());
			assertEquals(0.2f, res1Oreded.get(1).getScore(), DELTA);
			assertEquals(40, res1Oreded.get(2).getPosition());
			assertEquals(14, res1Oreded.get(2).getLength());
			assertEquals(333, res1Oreded.get(2).getConcept());
			assertEquals(0.4f, res1Oreded.get(2).getScore(), DELTA);
		}
		{
			HashSet<ScoredAnnotation> binding1 = new HashSet<>();
			binding1.add(new ScoredAnnotation(2, 4, 111, 0.3f));
			binding1.add(new ScoredAnnotation(20, 4, 222, 0.0f));
			binding1.add(new ScoredAnnotation(30, 4, 222, 1.0f));
			binding1.add(new ScoredAnnotation(40, 4, 333, 0.5f));
			binding1.add(new ScoredAnnotation(50, 4, 333, 0.7f));
			binding1.add(new ScoredAnnotation(55, 4, 444, 0.9f));

			HashSet<ScoredAnnotation> res1 = SmaphUtils.collapseBinding(binding1);
			Vector<ScoredAnnotation> res1Oreded = new Vector<>(res1);
			Collections.sort(res1Oreded);
			assertEquals(4, res1Oreded.size());
			assertEquals(2, res1Oreded.get(0).getPosition());
			assertEquals(4, res1Oreded.get(0).getLength());
			assertEquals(111, res1Oreded.get(0).getConcept());
			assertEquals(0.3f, res1Oreded.get(0).getScore(), DELTA);
			assertEquals(20, res1Oreded.get(1).getPosition());
			assertEquals(14, res1Oreded.get(1).getLength());
			assertEquals(222, res1Oreded.get(1).getConcept());
			assertEquals(0.5f, res1Oreded.get(1).getScore(), DELTA);
			assertEquals(40, res1Oreded.get(2).getPosition());
			assertEquals(14, res1Oreded.get(2).getLength());
			assertEquals(333, res1Oreded.get(2).getConcept());
			assertEquals(0.6f, res1Oreded.get(2).getScore(), DELTA);
			assertEquals(55, res1Oreded.get(3).getPosition());
			assertEquals(4, res1Oreded.get(3).getLength());
			assertEquals(444, res1Oreded.get(3).getConcept());
			assertEquals(0.9f, res1Oreded.get(3).getScore(), DELTA);
		}

	}

	@Test
	public void testTokenize() throws Exception {
		List<String> r1 = SmaphUtils.tokenize("aaa   bbb     ccc");
		List<String> r2 = SmaphUtils.tokenize("\taaa   bbb     ccc");
		List<String> r3 = SmaphUtils.tokenize("aaa   bbb     ccc\n");
		List<String> r4 = SmaphUtils.tokenize("aaa   bbb \t\t\nccc\n");
		assertEquals(Arrays.asList("aaa", "bbb", "ccc"), r1);
		assertEquals(Arrays.asList("aaa", "bbb", "ccc"), r2);
		assertEquals(Arrays.asList("aaa", "bbb", "ccc"), r3);
		assertEquals(Arrays.asList("aaa", "bbb", "ccc"), r4);
		
		List<String> r5 = SmaphUtils.tokenize("\t\t\n");
		assertEquals(Arrays.asList(), r5);

		List<String> r6 = SmaphUtils.tokenize("\t\t\naaa");
		assertEquals(Arrays.asList("aaa"), r6);
		List<String> r7 = SmaphUtils.tokenize("aaa\t\t\n");
		assertEquals(Arrays.asList("aaa"), r7);
		List<String> r8 = SmaphUtils.tokenize("aaa");
		assertEquals(Arrays.asList("aaa"), r8);
		
	}

	@Test
	public void testGetAllFtrVectIntIntArray() throws Exception {
		assertArrayEquals(new int[] { 2, 6 }, SmaphUtils.getAllFtrVect(6, new int[] { 1, 3, 4, 5 }));
	}

	@Test
	public void testRemoveTrailingParenthetical() throws Exception {
		assertEquals("Maradona", SmaphUtils.removeTrailingParenthetical("Maradona (Philosophy)"));
		assertEquals("Maradona", SmaphUtils.removeTrailingParenthetical("Maradona"));
		assertEquals("Maradona", SmaphUtils.removeTrailingParenthetical("Maradona  (Player)"));
		assertEquals("Maradona (aaa)", SmaphUtils.removeTrailingParenthetical("Maradona (aaa)  (Player)"));
	}

	@Test
	public void testFindSegmentsStrings() throws Exception {
		Vector<String> segments = new Vector<String>();
		segments.add("aaa");
		segments.add("aaa bbb");
		segments.add("aaa bbb ccc");
		segments.add("bbb");
		segments.add("bbb ccc");
		segments.add("ccc");
		assertEquals(segments,
				SmaphUtils.findSegmentsStrings("  ;;;aaa bbb   ,., ccc"));
	}

	@Test
	public void testAddFtrVect() throws Exception {
		assertArrayEquals(new int[]{5}, SmaphUtils.addFtrVect(null, 5));
		assertArrayEquals(new int[]{5}, SmaphUtils.addFtrVect(new int[]{}, 5));
		assertArrayEquals(new int[]{1,2,3,5}, SmaphUtils.addFtrVect(new int[]{1,2,3}, 5));
		assertArrayEquals(new int[]{1,2,3,5}, SmaphUtils.addFtrVect(new int[]{3,1,2}, 5));
	}

	@Test
	public void testRemoveFtrVect() throws Exception {
		assertArrayEquals(new int[]{}, SmaphUtils.removeFtrVect(new int[]{5}, 5));
		assertArrayEquals(new int[]{1,2}, SmaphUtils.removeFtrVect(new int[]{1,2,3}, 3));
		assertArrayEquals(new int[]{2,3}, SmaphUtils.removeFtrVect(new int[]{3,1,2}, 1));
	}

}
