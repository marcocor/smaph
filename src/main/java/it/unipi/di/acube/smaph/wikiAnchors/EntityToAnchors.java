package it.unipi.di.acube.smaph.wikiAnchors;

import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.objects.Object2IntArrayMap;
import it.unimi.dsi.fastutil.objects.Object2IntMap;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.List;
import java.util.Map.Entry;
import java.util.NavigableSet;
import java.util.TreeSet;
import java.util.Vector;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.zip.GZIPInputStream;

import org.apache.commons.collections4.bidimap.TreeBidiMap;
import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONWriter;
import org.mapdb.BTreeMap;
import org.mapdb.Bind;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Fun;
import org.mapdb.HTreeMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.trie4j.louds.MapTailLOUDSTrie;
import org.trie4j.patricia.MapPatriciaTrie;

public class EntityToAnchors {
	public static final String DATASET_FILENAME = "./mapdb/e2a_dataset";
	private static EntityToAnchors e2a;

	private DB db;
	/**
	 * entity -> anchor-IDs
	 */
	private BTreeMap<Integer,int[]> entityToAnchorIDs;
	
	/**
	 * entity -> frequencies of anchor-ID for entity
	 */
	private BTreeMap<Integer,int[]> entityToFreqs;
	
	/**
	 * From anchor to anchor-ID
	 */
	private BTreeMap<String, Integer> vocab;
	
	/**
	 * anchor-ID -> how many times the anchor has been seen 
	 */
	private BTreeMap<Integer, Integer> anchorToOccurrences;	
	
	NavigableSet<Fun.Tuple2<Integer, String>> vocabInverse;

	private static Logger logger = LoggerFactory.getLogger(EntityToAnchors.class.getName());

	private static EntityToAnchors fromDB() {
		logger.info("Loading E2A database.");
		return new EntityToAnchors(DBMaker
				.newFileDB(new File(DATASET_FILENAME))
				.transactionDisable()
				.closeOnJvmShutdown()
				.readOnly()
				.cacheLRUEnable()
		        // .compressionEnable()
		        .make());
	}
	
	public EntityToAnchors(DB db) {
		this.db = db;
		entityToAnchorIDs = db.getTreeMap("entityToAnchorIDs");
		entityToFreqs = db.getTreeMap("entityToFreqs");
		vocab = db.getTreeMap("vocab");
		anchorToOccurrences = db.getTreeMap("anchorToOccurrences");
		vocabInverse = new TreeSet<Fun.Tuple2<Integer, String>>();
		Bind.mapInverse(vocab, vocabInverse);
    }

	public static EntityToAnchors e2a() {
		if (e2a == null)
				e2a = fromDB();
		return e2a;
	}
	
	public static void createDB(String file) throws FileNotFoundException,
			IOException {
		
		InputStream gzipStream = new GZIPInputStream(new FileInputStream(file));
		BufferedReader buffered = new BufferedReader(new InputStreamReader(
				gzipStream, "UTF-8"));

		logger.info("Building database...");
		EntityToAnchors mdb = new EntityToAnchors(
				DBMaker.newFileDB(new File(DATASET_FILENAME))
				.transactionDisable()
				.closeOnJvmShutdown()
				.asyncWriteEnable()
		        .asyncWriteFlushDelay(100)
				.cacheLRUEnable()
		        // .compressionEnable()
		        .make());

		String line = null;
		long count = 0;
		long processedGbs = 0;
		Int2ObjectOpenHashMap<Int2IntArrayMap> entityToAnchorIDToFreqBuffer = new Int2ObjectOpenHashMap<Int2IntArrayMap>();
		
		int lastId = 0;
		while ((line = buffered.readLine()) != null) {
			count += line.length();
			if (count > processedGbs * (1024 * 1024 * 1024))
				logger.info(String.format("Read %d GiB.", processedGbs++));
			
			int tabIdx = line.indexOf('\t');
			int idLastIdx = line.indexOf('\t', tabIdx + 1);
			String anchor = line.substring(0, tabIdx);
			Integer aId;
			if (mdb.vocab.containsKey(anchor))
				aId = mdb.vocab.get(anchor);
			else{
				aId = lastId;
				mdb.vocab.put(anchor, lastId++);
			}
			
			int pageId = Integer
					.parseInt(line.substring(tabIdx + 1, idLastIdx));

			Int2IntArrayMap map = entityToAnchorIDToFreqBuffer.get(pageId);
			if (map == null){
				map = new Int2IntArrayMap();
				entityToAnchorIDToFreqBuffer.put(pageId, map);
			}
			if (!map.containsKey(aId))
				map.put(aId.intValue(), 1);
			else
				map.put(aId.intValue(), 1 + map.get(aId)); //increase the occurrence for anchor by +1
			
			if (!mdb.anchorToOccurrences.containsKey(aId))
				mdb.anchorToOccurrences.put(aId, 0);
			mdb.anchorToOccurrences.put(aId, mdb.anchorToOccurrences.get(aId) + 1);
		}
		buffered.close();
		logger.info(String.format("Finished reading %s (%d GBs)", file, processedGbs));
		
		logger.info("Copying entity-to-anchor mappings in db...");
		int c = 0;
		for ( int id : entityToAnchorIDToFreqBuffer.keySet()){
			if (++c % 1000000 == 0)
				logger.info(String.format("Copied %d entity-to-anchor mappings.", c));
			Int2IntArrayMap anchorToFreq = entityToAnchorIDToFreqBuffer.get(id);
			int[] anchors = new int[anchorToFreq.size()];
			int[] frequencies = new int[anchorToFreq.size()];
			int idx=0;
			for (Entry<Integer, Integer> p : anchorToFreq.entrySet()){
				anchors[idx] = p.getKey();
				frequencies[idx] = p.getValue();
				idx++;
			}
			mdb.entityToAnchorIDs.put(id, anchors);
			mdb.entityToFreqs.put(id, frequencies);
		}
		
		logger.info("Committing changes...");
		mdb.db.commit();

		logger.info("Compacting db...");
		mdb.db.compact();
		
		logger.info("Closing db...");
		mdb.db.close();
	}
	
	private String idToAnchor(int aId) {
		String anchor = null;
		for (String a : Fun.filter(vocabInverse, aId))
			if (anchor != null)
				throw new RuntimeException("Double anchor for id " + aId);
			else
				anchor = a;
		return anchor;
	}
	
	public boolean containsId(int id){
		return entityToAnchorIDs.containsKey(id);
	}
	
	public List<Pair<String, Integer>> getAnchors(int id) {
		if (!containsId(id))
			throw new RuntimeException("Anchors for page id=" + id
					+ " not found.");
		int[] anchors = entityToAnchorIDs.get(id);
		int[] freqs = entityToFreqs.get(id);
		
		List<Pair<String, Integer>> res = new Vector<Pair<String,Integer>>();
		for (int i=0; i<anchors.length; i++)
			res.add(new Pair<String, Integer>(idToAnchor(anchors[i]), freqs[i]));
		return res;
	}
	
	public int getAnchorGlobalOccurrences(String anchor){
		Integer aId = vocab.get(anchor);
		if (aId == null)
			throw new RuntimeException("Anchor "+anchor+"not present in vocabulary.");
		return anchorToOccurrences.get(aId);
	}
	
	public double getCommonness(String anchor, int entity){
		return ((double)getFrequency(anchor, entity))/getAnchorGlobalOccurrences(anchor);
	}

	private int getFrequency(String anchor, int entity) {
		if (!containsId(entity))
			throw new RuntimeException("Anchors for page id=" + entity
					+ " not found.");
		if (!vocab.containsKey(anchor))
			throw new RuntimeException("Anchors "+anchor+" is not contained in the vocabulary.");
		int aid = vocab.get(anchor);
		
		int[] anchors = entityToAnchorIDs.get(entity);
		int[] freqs = entityToFreqs.get(entity);
		
		for (int i=0; i<anchors.length; i++)
			if (anchors[i] == aid)
				return freqs[i];
		return 0;
	}

	public void dumpJson(String file) throws IOException, JSONException {
		FileWriter fw = new FileWriter(file);
		JSONWriter wr = new JSONWriter(fw);
		wr.object();
		for (int pageid : entityToAnchorIDs.keySet()) {
			wr.key(Integer.toString(pageid)).array();
			List<Pair<String, Integer>> anchorAndFreqs = getAnchors(pageid);
			for (Pair<String, Integer> p: anchorAndFreqs)
				wr.object().key(p.first).value(p.second).endObject();
			wr.endArray();
		}
		wr.endObject();
		fw.close();
	}

	public static void main(String[] args) throws Exception{
		logger.info("Creating E2A database... ");
		createDB(args[0]);
		logger.info("Done.");
		
/*		EntityToAnchors.e2a();
		
		int id = 36511;
		List<Pair<String, Integer>> a = EntityToAnchors.e2a().getAnchors(id);
		logger.info("Getting commonness.");
		for (Pair<String, Integer> p : a){
			double comm = EntityToAnchors.e2a().getCommonness(p.first, id);
			System.out.printf("%.3f %d %s ->%d%n", comm, p.second, p, id);
		}*/
		
	}
}
