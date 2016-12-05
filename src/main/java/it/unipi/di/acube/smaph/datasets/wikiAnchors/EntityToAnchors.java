package it.unipi.di.acube.smaph.datasets.wikiAnchors;

import it.unimi.dsi.fastutil.ints.Int2IntArrayMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.smaph.SmaphUtils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Collections;
import java.util.List;
import java.util.Map.Entry;
import java.util.Vector;

import org.codehaus.jettison.json.JSONException;
import org.codehaus.jettison.json.JSONWriter;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class EntityToAnchors {
	public static final String DEFAULT_INPUT = "./data/anchors.tsv";
	public static final String DATASET_FILENAME = "./mapdb/e2a_dataset";
	private static EntityToAnchors e2a;

	private DB db;
	/**
	 * entity -> anchor-IDs
	 */
	private HTreeMap<Integer, int[]> entityToAnchorIDs;
	
	/**
	 * entity -> frequencies of anchor-ID for entity
	 */
	private HTreeMap<Integer,int[]> entityToFreqs;
	
	/**
	 * From anchor to anchor-ID
	 */
	private HTreeMap<String, Integer> anchorToAid;
	
	/**
	 * From anchor-ID to snchor
	 */
	private HTreeMap<Integer, String> aidToAnchor;
	
	/**
	 * anchor-ID -> how many times the anchor has been seen 
	 */
	private HTreeMap<Integer, Integer> anchorToOccurrences;	
	
	private static Logger logger = LoggerFactory.getLogger(EntityToAnchors.class.getName());

	private static EntityToAnchors fromDB() {
		logger.info("Opening E2A database.");
		DB db = DBMaker.fileDB(DATASET_FILENAME).fileMmapEnable().closeOnJvmShutdown().make();
		EntityToAnchors e2a = new EntityToAnchors(db);
		logger.info("Loading E2A database done.");
		return e2a;
	}

	private EntityToAnchors(DB db) {
		this.db = db;
		entityToAnchorIDs = db.hashMap("entityToAnchorIDs", Serializer.INTEGER, Serializer.INT_ARRAY).createOrOpen();
		entityToFreqs = db.hashMap("entityToFreqs", Serializer.INTEGER, Serializer.INT_ARRAY).createOrOpen();
		anchorToAid = db.hashMap("anchorToAid", Serializer.STRING, Serializer.INTEGER).createOrOpen();
		aidToAnchor = db.hashMap("aidToAnchor", Serializer.INTEGER, Serializer.STRING).createOrOpen();
		anchorToOccurrences = db.hashMap("anchorToOccurrences", Serializer.INTEGER, Serializer.INTEGER).createOrOpen();
	}

	public static EntityToAnchors e2a() {
		if (e2a == null)
				e2a = fromDB();
		return e2a;
	}
	
	private static void createDB(String file) throws FileNotFoundException,
			IOException {
		
		InputStream inputStream = new FileInputStream(file);
		BufferedReader buffered = new BufferedReader(new InputStreamReader(
				inputStream, "UTF-8"));

		logger.info("Building database...");
		EntityToAnchors mdb = new EntityToAnchors(DBMaker.fileDB(DATASET_FILENAME).fileMmapEnable().closeOnJvmShutdown().make());

		long count = 0;
		long processedMbs = 0;
		Int2ObjectOpenHashMap<Int2IntArrayMap> entityToAnchorIDToFreqBuffer = new Int2ObjectOpenHashMap<Int2IntArrayMap>();
		
		int lastAnchorId = 0;
		String line = null;
		while ((line = buffered.readLine()) != null) {
			count += line.length();
			if (count > processedMbs * (10 * 1024 * 1024))
				logger.info(String.format("Read %d MiB.", 10*(processedMbs++)));
			
			String[] tokens = line.split("\t");
			if (tokens.length != 3){
				buffered.close();
				throw new RuntimeException("Read line: [" + line + "] should have three tokens.");
			}
			
			String anchor = tokens[0].toLowerCase();
			int freq = Integer.parseInt(tokens[2]);

			if (!mdb.anchorToAid.containsKey(anchor)){
				mdb.anchorToAid.put(anchor, lastAnchorId);
				mdb.aidToAnchor.put(lastAnchorId, anchor);
				lastAnchorId++;
			}
				
			Integer aId = mdb.anchorToAid.get(anchor);
			
			int pageId = Integer.parseInt(tokens[1]);

			Int2IntArrayMap aidToFreqMap = entityToAnchorIDToFreqBuffer.get(pageId);
			if (aidToFreqMap == null){
				aidToFreqMap = new Int2IntArrayMap();
				entityToAnchorIDToFreqBuffer.put(pageId, aidToFreqMap);
			}
			if (!aidToFreqMap.containsKey(aId))
				aidToFreqMap.put(aId.intValue(), freq);
			else
				aidToFreqMap.put(aId.intValue(), freq + aidToFreqMap.get(aId)); //increase the occurrence for anchor by freq (this should only happen for anchors with different case, same content.)
			
			if (!mdb.anchorToOccurrences.containsKey(aId))
				mdb.anchorToOccurrences.put(aId, 0);
			mdb.anchorToOccurrences.put(aId, mdb.anchorToOccurrences.get(aId) + freq);
		}
		buffered.close();
		logger.info(String.format("Finished reading %s (%.1f MBs)", file, count / 1024.0 / 1024.0));
		
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

		logger.info("Closing db...");
		mdb.db.close();
	}
	
	public String idToAnchor(int aId){
		String anchor = aidToAnchor.get(Integer.valueOf(aId));
		if (anchor == null)
			throw new RuntimeException("Anchor-ID "+aId+"not present.");
		return anchor;
	}
	
	public boolean containsId(int id){
		return entityToAnchorIDs.containsKey(id);
	}

	public List<Pair<String, Integer>> getAnchors(int id, double keepFreq) {
		if (!containsId(id))
			throw new RuntimeException("Anchors for page id=" + id
					+ " not found.");
		int[] anchors = entityToAnchorIDs.get(id);
		int[] freqs = entityToFreqs.get(id);

		int totalFreq = 0;
		List<Pair<Integer, Integer>> anchorsAndFreq = new Vector<>();
		for (int i=0; i<anchors.length; i++){
			totalFreq += freqs[i];
			anchorsAndFreq.add(new Pair<Integer, Integer>(anchors[i], freqs[i]));
		}

		Collections.sort(anchorsAndFreq, new SmaphUtils.ComparePairsBySecondElement<Integer, Integer>());

		int gathered = 0;
		List<Pair<String, Integer>> res = new Vector<Pair<String, Integer>>();
		for (int i = anchorsAndFreq.size() - 1; i >= 0; i--)
			if (gathered >= keepFreq * totalFreq)
				break;
			else {
				int aid = anchorsAndFreq.get(i).first;
				int freq = anchorsAndFreq.get(i).second;
				res.add(new Pair<String, Integer>(idToAnchor(aid), freq));
				gathered += freq;
			}
		return res;
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
		Integer aId = anchorToAid.get(anchor);
		if (aId == null)
			throw new RuntimeException("Anchor "+anchor+"not present.");
		return anchorToOccurrences.get(aId);
	}
	
	public double getCommonness(String anchor, int entity){
		if (!anchorToAid.containsKey(anchor))
			return 0.0;
		return ((double)getFrequency(anchor, entity)) / getAnchorGlobalOccurrences(anchor);
	}

	public double getCommonness(String anchor, int entity, int occurrences){
		return ((double) occurrences) / getAnchorGlobalOccurrences(anchor);
	}

	private int getFrequency(String anchor, int entity) {
		if (!containsId(entity))
			throw new RuntimeException("Anchors for page id=" + entity
					+ " not found.");
		if (!anchorToAid.containsKey(anchor))
			throw new RuntimeException("Anchors "+anchor+" not present.");
		int aid = anchorToAid.get(anchor);
		
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
		for (int pageid : entityToAnchorIDs.getKeys()) {
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
		createDB(args.length > 0 ? args[0] : DEFAULT_INPUT);
		logger.info("Done.");
	}
}
