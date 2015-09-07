package it.unipi.di.acube.smaph.learn.models;

import static java.lang.Thread.currentThread;

import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;
import it.unipi.di.acube.smaph.learn.models.RankLibModel;
import it.unipi.di.acube.smaph.learn.normalizer.FeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.NoFeatureNormalizer;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class RankLibModelTest {
	TestFeaturePack f0_1, f0_2, f1_1, f1_2, f2_1, f2_2, f2_3, f2_4;
	List<FeaturePack> l0, l1, l2;
	RankLibModel r;
	
	@Before
	public void setUp() throws Exception {
		// Create feature vectors
		f0_1 = TestFeaturePack.fromValues(0.1, 0.1);
		f0_2 = TestFeaturePack.fromValues(0.6, 0.4);
		f1_1 = TestFeaturePack.fromValues(0.8, 0.7);
		f1_2 = TestFeaturePack.fromValues(0.8, 0.8);
		f2_1 = TestFeaturePack.fromValues(0.8, 0.8);
		f2_2 = TestFeaturePack.fromValues(0.3, 0.8);
		f2_3 = TestFeaturePack.fromValues(0.8, 0.9);
		f2_4 = TestFeaturePack.fromValues(0.8, 0.2);

		// Load LambdaMart model
		r = new RankLibModel(currentThread().getContextClassLoader()
				.getResource("ranklib_lm_model").getFile());

		// Create lists
		l0 = new Vector<>();
		l0.add(f0_1);
		l0.add(f0_2);
		l1 = new Vector<>();
		l1.add(f1_1);
		l1.add(f1_2);
		l2 = new Vector<>();
		l2.add(f2_1);
		l2.add(f2_2);
		l2.add(f2_3);
	}

	@Test
	public void testGetScores() throws Exception {
		FeatureNormalizer fn = new NoFeatureNormalizer();
		
		assertTrue(r.predictScore(f0_1, fn) > r.predictScore(f0_2, fn));

		assertTrue(r.predictScore(f1_1, fn) > r.predictScore(f1_2, fn));

		assertTrue(r.predictScore(f2_1, fn) < r.predictScore(f2_2, fn));
		assertTrue(r.predictScore(f2_1, fn) < r.predictScore(f2_3, fn));
		assertTrue(r.predictScore(f2_2, fn) < r.predictScore(f2_3, fn));
		assertTrue(r.predictScore(f2_2, fn) > r.predictScore(f2_4, fn));
		assertTrue(r.predictScore(f2_3, fn) > r.predictScore(f2_4, fn));

		//Test ranking
		assertArrayEquals(new int[]{0,1}, r.getRanking(l0, fn));
		assertArrayEquals(new int[]{0,1}, r.getRanking(l1, fn));
		assertArrayEquals(new int[]{2,1,0}, r.getRanking(l2, fn));
		
		//Test highest rank.
		assertEquals(0, r.getHighestRank(l0, fn));
		assertEquals(0, r.getHighestRank(l1, fn));
		assertEquals(2, r.getHighestRank(l2, fn));
	}

	public static class TestFeaturePack extends FeaturePack<Object>{
		private static final long serialVersionUID = 1L;

		public static TestFeaturePack fromValues (double fa, double fb) {
			HashMap<String, Double> hm = new HashMap<String, Double>();
			hm.put("f_a", fa);
			hm.put("f_b", fb);
			return new TestFeaturePack(hm);
		}		
		
		public TestFeaturePack(HashMap<String, Double> features) {
			super(features);
		}

		@Override
		public void checkFeatures(HashMap<String, Double> features) {
			if (!features.containsKey("f_a") || !features.containsKey("f_b"))
				throw new RuntimeException();
		}

		@Override
		public String[] getFeatureNames() {
			return new String[]{"f_a", "f_b"};
		}
		
	}

}
