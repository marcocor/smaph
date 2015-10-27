package it.unipi.di.acube.smaph;

import java.io.*;
import java.net.URLEncoder;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

import it.unipi.di.acube.batframework.utils.Pair;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

public class WATRelatednessComputer implements Serializable {
	private static final long serialVersionUID = 1L;
	private static WATRelatednessComputer instance = new WATRelatednessComputer();
	private Object2DoubleOpenHashMap<Pair<Integer,Integer>> cacheJaccard = new Object2DoubleOpenHashMap<>();
	private Object2DoubleOpenHashMap<Pair<Integer,Integer>> cacheMW = new Object2DoubleOpenHashMap<>();
	private Object2DoubleOpenHashMap<String> cacheLp = new Object2DoubleOpenHashMap<>();
	private static long flushCounter = 0;
	private static final int FLUSH_EVERY = 200;
	private static final String URL_TEMPLATE_JACCARD = "http://wikisense.mkapp.it/rel/id?src=%d&dst=%d&relatedness=jaccard";
	private static final String URL_TEMPLATE_MW = "http://wikisense.mkapp.it/rel/id?src=%d&dst=%d&relatedness=mw";
	private static final String URL_TEMPLATE_SPOT = "http://wikisense.mkapp.it/tag/spot?text=%s";
	private static String resultsCacheFilename = null;
	
	public synchronized void increaseFlushCounter()
			throws FileNotFoundException, IOException {
		flushCounter++;
		if ((flushCounter % FLUSH_EVERY) == 0)
			flush();
	}

	public static synchronized void flush() throws FileNotFoundException,
			IOException {
		if (flushCounter > 0 && resultsCacheFilename != null) {
			SmaphAnnotatorDebugger.out.print("Flushing relatedness cache... ");
			new File(resultsCacheFilename).createNewFile();
			ObjectOutputStream oos = new ObjectOutputStream(
					new FileOutputStream(resultsCacheFilename));
			oos.writeObject(instance);
			oos.close();
			SmaphAnnotatorDebugger.out
					.println("Flushing relatedness cache done.");
		}
	}
	
	public static void setCache(String cacheFilename)
			throws FileNotFoundException, IOException, ClassNotFoundException {
		if (resultsCacheFilename != null
				&& resultsCacheFilename.equals(cacheFilename))
			return;
		System.out.println("Loading relatedness cache...");
		resultsCacheFilename = cacheFilename;
		if (new File(resultsCacheFilename).exists()) {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
					resultsCacheFilename));
			instance = (WATRelatednessComputer) ois.readObject();
			ois.close();
		}
	}

	private double queryJsonRel(int wid1, int wid2, String urlTemplate) {
		String url = String.format(urlTemplate, wid1, wid2);
		System.out.print(url);
		JSONObject obj = SmaphUtils.httpQueryJson(url);
		try {
			increaseFlushCounter();
			double rel = obj.getDouble("value");
			System.out.println(" -> " + rel);
			return rel;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	private double getGenericRelatedness(int wid1, int wid2, Object2DoubleOpenHashMap<Pair<Integer,Integer>> cache, String url){
		if (wid2 < wid1) {
			int tmp = wid2;
			wid2 = wid1;
			wid2 = tmp;
		}
		Pair <Integer,Integer> p = new Pair<Integer,Integer>(wid1,wid2);
		if (!cache.containsKey(p))
			cache.put(p, queryJsonRel(wid1, wid2, url));
			
		return cache.getDouble(p);
	}
	
	public static double getJaccardRelatedness(int wid1, int wid2) {
		return instance.getGenericRelatedness(wid1, wid2, instance.cacheJaccard, URL_TEMPLATE_JACCARD);
	}

	public static double getMwRelatedness(int wid1, int wid2) {
		return instance.getGenericRelatedness(wid1, wid2, instance.cacheMW, URL_TEMPLATE_MW);
	}

	public static double getLp(String anchor) {
		if (!instance.cacheLp.containsKey(anchor))
			instance.cacheLp.put(anchor, queryJsonLp(anchor));
		return instance.cacheLp.get(anchor);
	}

	private static double queryJsonLp(String anchor) {
		try {
			String url = String.format(URL_TEMPLATE_SPOT, URLEncoder.encode(anchor, "utf-8"));
			System.out.println(url);
			JSONObject obj = SmaphUtils.httpQueryJson(url);
			instance.increaseFlushCounter();
			JSONArray spots = obj.getJSONArray("spots");
			for (int i = 0; i < spots.length(); i++) {
				JSONObject objI = spots.getJSONObject(i);
				if (objI.getString("spot").equals(anchor)){
					double lp = objI.getDouble("linkProb");
					System.out.println("anchor: " + anchor + " lp:"+lp);
					return lp;
				}
			}
			return 0.0;
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

}
