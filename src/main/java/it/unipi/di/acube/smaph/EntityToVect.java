package it.unipi.di.acube.smaph;

import java.io.IOException;
import java.util.List;

import it.cnr.isti.hpc.CentroidEntityScorer;
import it.cnr.isti.hpc.EntityScorer;
import it.cnr.isti.hpc.EntityScorer.ScorerContext;
import it.cnr.isti.hpc.LREntityScorer;
import it.cnr.isti.hpc.Word2VecCompress;
import it.unimi.dsi.fastutil.io.BinIO;

public class EntityToVect {
	public static final String WORD_MODEL_FILE = "entity2vec/wiki.3.200.hs.e0.100.tr.bin.new";
	public static final String CENTROID_ENTITY_MODEL_FILE = "entity2vec/entity_vectors.centroid.e0.100.bin.new";
	public static final String LR_ENTITY_MODEL_FILE = "entity2vec/entity_vectors.lr.e0.100.bin.new";
	private static LREntityScorer lrScorer;
	private static CentroidEntityScorer centroidScorer;
	private static Word2VecCompress centroidEntityModel, lrEntityModel;

	public static void initialize() throws ClassNotFoundException, IOException {
		initialize(WORD_MODEL_FILE, CENTROID_ENTITY_MODEL_FILE, LR_ENTITY_MODEL_FILE);
	}

	public static void initialize(String wordModelFile, String centroidEntityModelFile, String lrEntityModelFile)
	        throws ClassNotFoundException, IOException {
		if (centroidEntityModel == null) {
			Word2VecCompress wordModel = (Word2VecCompress) BinIO.loadObject(wordModelFile);
			centroidEntityModel = (Word2VecCompress) BinIO.loadObject(centroidEntityModelFile);
			lrEntityModel = (Word2VecCompress) BinIO.loadObject(lrEntityModelFile);
			lrScorer = new LREntityScorer(wordModel, lrEntityModel);
			centroidScorer = new CentroidEntityScorer(wordModel, centroidEntityModel);
		}
	}

	public static Float getScores(String entity, List<String> words, Word2VecCompress entityModel, EntityScorer scorer) {
		Long entityId = entityModel.word_id(entity);
		ScorerContext ctx = scorer.context(words);
		float score = ctx.score(entityId);
		return score == EntityScorer.DEFAULT_SCORE ? null : score;
	}

	public static Float getLrScore(String entity, List<String> words) {
		return getScores(entity, words, lrEntityModel, lrScorer);
	}

	public static Float getCentroidScore(String entity, List<String> words) {
		return getScores(entity, words, centroidEntityModel, centroidScorer);
	}
}
