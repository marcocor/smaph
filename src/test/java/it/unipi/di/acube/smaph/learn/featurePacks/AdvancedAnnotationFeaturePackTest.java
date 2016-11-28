package it.unipi.di.acube.smaph.learn.featurePacks;

import java.util.List;
import java.util.Vector;

import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.smaph.learn.featurePacks.AnnotationFeaturePack;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AdvancedAnnotationFeaturePackTest {

	@Before
	public void setUp() throws Exception {
	}

	@Test
	public void testExpandedMention() throws Exception {
		assertEquals(null, AnnotationFeaturePack.expandedMention("bbb", 0, 3, false));
		assertEquals(null, AnnotationFeaturePack.expandedMention("bbb", 0, 3, true));
		assertEquals(null, AnnotationFeaturePack.expandedMention("aa;bbb cc", 3, 6, false));
		assertEquals(null, AnnotationFeaturePack.expandedMention("aa;bbb cc", 3, 6, true));
		assertEquals("aaa bbb", AnnotationFeaturePack.expandedMention("aaa;bbb ccc", 4, 7, false));
		assertEquals("bbb ccc", AnnotationFeaturePack.expandedMention("aaa;bbb ccc", 4, 7, true));
		assertEquals("aaaa bbb", AnnotationFeaturePack.expandedMention("aaa aaaa;bbb cccc aaa", 9, 12, false));
		assertEquals("bbb cccc", AnnotationFeaturePack.expandedMention("aaa aaaa;bbb cccc aaa", 9, 12, true));

		assertEquals(null, AnnotationFeaturePack.expandedMention("a a;bbb c c", 4, 7, false));
		assertEquals(null, AnnotationFeaturePack.expandedMention("a a;bbb c c", 4, 7, true));
		assertEquals("a a a bbb", AnnotationFeaturePack.expandedMention("a a a;bbb c*c*c", 6, 9, false));
		assertEquals("bbb c c c", AnnotationFeaturePack.expandedMention("a a a;bbb c*c*c", 6, 9, true));
		assertEquals("a aa bbb", AnnotationFeaturePack.expandedMention("a aa;bbb c*cc", 5, 8, false));
		assertEquals("bbb c cc", AnnotationFeaturePack.expandedMention("a aa;bbb c*cc", 5, 8, true));
		assertEquals("a a a bbb", AnnotationFeaturePack.expandedMention("a a a a;bbb c c c c", 8, 11, false));
		assertEquals("bbb c c c", AnnotationFeaturePack.expandedMention("a a a a;bbb c c c c", 8, 11, true));

		assertEquals(null, AnnotationFeaturePack.expandedMention("**bbb;;", 2, 5, false));
		assertEquals(null, AnnotationFeaturePack.expandedMention("**bbb;;", 2, 5, true));
		assertEquals(null, AnnotationFeaturePack.expandedMention("+++a a;bbb c c+++", 7, 10, false));
		assertEquals(null, AnnotationFeaturePack.expandedMention("+++a a;bbb c c+++", 7, 10, true));
		assertEquals("aaa bbb", AnnotationFeaturePack.expandedMention("*aaa;bbb ccc*", 5, 8, false));
		assertEquals("bbb ccc", AnnotationFeaturePack.expandedMention("*aaa;bbb ccc*", 5, 8, true));
		assertEquals("a a a bbb", AnnotationFeaturePack.expandedMention(" a a a;bbb c*c*c*", 7, 10, false));
		assertEquals("bbb c c c", AnnotationFeaturePack.expandedMention(" a a a;bbb c*c*c*", 7, 10, true));

	}

	@Test
	public void testExpandibility() throws Exception {
		{
			/* There is a much better expansion */
			List<Pair<String, Integer>> anchorAndOccurrencies = new Vector<>();
			anchorAndOccurrencies.add(new Pair<String, Integer>("xxx", 1));
			anchorAndOccurrencies.add(new Pair<String, Integer>("bbb cc", 1));
			anchorAndOccurrencies.add(new Pair<String, Integer>("aaax bbb", 1));

			assertEquals(3.0 / 6.0 - 1.0 / 7.0, // +0.357
			        AnnotationFeaturePack.expandibility("*aaa bbb ccc", 5, 8, anchorAndOccurrencies, true), 0.000001);
			assertEquals(3.0 / 6.0 - 1.0 / 8.0, // +0.375
			        AnnotationFeaturePack.expandibility("*aaa bbb ccc", 5, 8, anchorAndOccurrencies, false), 0.000001);
		}
		{
			/* There is a better expansion */
			List<Pair<String, Integer>> anchorAndOccurrencies = new Vector<>();
			anchorAndOccurrencies.add(new Pair<String, Integer>("bbx", 1));
			anchorAndOccurrencies.add(new Pair<String, Integer>("bbb cc", 1));
			anchorAndOccurrencies.add(new Pair<String, Integer>("aaax bbb", 1));

			assertEquals(1.0 / 3.0 - 1.0 / 7.0, // +0.190
			        AnnotationFeaturePack.expandibility("*aaa bbb ccc", 5, 8, anchorAndOccurrencies, true), 0.000001);
			assertEquals(1.0 / 3.0 - 1.0 / 8.0, // +0.208
			        AnnotationFeaturePack.expandibility("*aaa bbb ccc", 5, 8, anchorAndOccurrencies, false), 0.000001);
		}
		{
			/* There is a fairly good expansion */
			List<Pair<String, Integer>> anchorAndOccurrencies = new Vector<>();
			anchorAndOccurrencies.add(new Pair<String, Integer>("bbb", 1));
			anchorAndOccurrencies.add(new Pair<String, Integer>("bbb cc", 1));
			anchorAndOccurrencies.add(new Pair<String, Integer>("aaax bbb", 1));

			assertEquals(0.0 - 1.0 / 7.0, // +0.143
			        AnnotationFeaturePack.expandibility("*aaa bbb ccc", 5, 8, anchorAndOccurrencies, true), 0.000001);
			assertEquals(0.0 - 1.0 / 8.0, // +0.125
			        AnnotationFeaturePack.expandibility("*aaa bbb ccc", 5, 8, anchorAndOccurrencies, false), 0.000001);
		}
		{
			/* There are no better expansions */
			List<Pair<String, Integer>> anchorAndOccurrencies = new Vector<>();
			anchorAndOccurrencies.add(new Pair<String, Integer>("bbx", 1));
			anchorAndOccurrencies.add(new Pair<String, Integer>("zzz cc", 1));
			anchorAndOccurrencies.add(new Pair<String, Integer>("zzzz bbb", 1));

			assertEquals(1.0 / 3.0 - 4.0 / 7.0, // -0.238
			        AnnotationFeaturePack.expandibility("*aaa bbb ccc", 5, 8, anchorAndOccurrencies, true), 0.000001);
			assertEquals(1.0 / 3.0 - 4.0 / 8.0, // -0.166
			        AnnotationFeaturePack.expandibility("*aaa bbb ccc", 5, 8, anchorAndOccurrencies, false), 0.000001);
		}
	}

}
