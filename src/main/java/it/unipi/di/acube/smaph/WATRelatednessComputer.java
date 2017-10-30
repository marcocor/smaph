package it.unipi.di.acube.smaph;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.io.UnsupportedEncodingException;
import java.lang.invoke.MethodHandles;
import java.net.URLEncoder;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;
import it.unipi.di.acube.batframework.utils.Pair;

public class WATRelatednessComputer implements Serializable {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static final long serialVersionUID = 1L;
	private static WATRelatednessComputer instance = new WATRelatednessComputer();
	private Object2DoubleOpenHashMap<Pair<Integer,Integer>> cacheJaccard = new Object2DoubleOpenHashMap<>();
	private Object2DoubleOpenHashMap<Pair<Integer,Integer>> cacheMW = new Object2DoubleOpenHashMap<>();
	private Object2DoubleOpenHashMap<String> cacheLp = new Object2DoubleOpenHashMap<>();
	private static long flushCounter = 0;
	private static final int FLUSH_EVERY = 1000;
	private static final String URL_TEMPLATE_JACCARD = "%s/relatedness/graph?gcube-token=%s&ids=%d&ids=%d&relatedness=jaccard";
	private static final String URL_TEMPLATE_MW = "%s/relatedness/graph?gcube-token=%s&ids=%d&ids=%d&relatedness=mw";
	private static final String URL_TEMPLATE_SPOT = "%s/sf/sf?gcube-token=%s&text=%s";
	private static String baseUri = "https://wat.d4science.org/wat";
	private static String gcubeToken = null;
	private static String resultsCacheFilename = null;
	
	public static void setBaseUri(String watBaseUri){
		baseUri = watBaseUri;
	}
	
	public static void setGcubeToken(String watGcubeToken){
		gcubeToken = watGcubeToken;
	}
	
	private synchronized void increaseFlushCounter()
			throws FileNotFoundException, IOException {
		flushCounter++;
		if ((flushCounter % FLUSH_EVERY) == 0)
			flush();
	}

	public static synchronized void flush() throws FileNotFoundException,
			IOException {
		if (flushCounter > 0 && resultsCacheFilename != null) {
			LOG.info("Flushing relatedness cache... ");
			new File(resultsCacheFilename).createNewFile();
			ObjectOutputStream oos = new ObjectOutputStream(
					new FileOutputStream(resultsCacheFilename));
			oos.writeObject(instance);
			oos.close();
			LOG.info("Flushing relatedness cache done.");
		}
	}
	
	public static void setCache(String cacheFilename)
			throws FileNotFoundException, IOException, ClassNotFoundException {
		if (resultsCacheFilename != null
				&& resultsCacheFilename.equals(cacheFilename))
			return;
		LOG.info("Loading relatedness cache...");
		resultsCacheFilename = cacheFilename;
		if (new File(resultsCacheFilename).exists()) {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
					resultsCacheFilename));
			instance = (WATRelatednessComputer) ois.readObject();
			ois.close();
		}
	}

	private double queryJsonRel(int wid1, int wid2, String urlTemplate) {
		String url = String.format(urlTemplate, baseUri, gcubeToken, wid1, wid2);
		LOG.info(url);
		JSONObject obj = SmaphUtils.httpQueryJson(url);
		try {
			increaseFlushCounter();
			double rel = obj.getJSONArray("pairs").getJSONObject(0).getDouble("relatedness");
			LOG.debug(" -> " + rel);
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
		if (wid1 == wid2) return 1.0;
		return instance.getGenericRelatedness(wid1, wid2, instance.cacheJaccard, URL_TEMPLATE_JACCARD);
	}

	public static double getMwRelatedness(int wid1, int wid2) {
		if (wid1 == wid2) return 1.0;
		return instance.getGenericRelatedness(wid1, wid2, instance.cacheMW, URL_TEMPLATE_MW);
	}

	public static double getLp(String anchor) {
		if (!instance.cacheLp.containsKey(anchor))
			instance.cacheLp.put(anchor, queryJsonLp(anchor));
		return instance.cacheLp.get(anchor);
	}

	private static double queryJsonLp(String anchor) {
		String url;
		try {
			url = String.format(URL_TEMPLATE_SPOT, baseUri, gcubeToken, URLEncoder.encode(anchor, "utf-8"));
		} catch (UnsupportedEncodingException e1) {
			e1.printStackTrace();
			throw new RuntimeException(e1);
		}
		
		LOG.debug("Querying {}", url);
		
		JSONObject obj = SmaphUtils.httpQueryJson(url);
		
		try {
			instance.increaseFlushCounter();
			return obj.getDouble("link_probability");
		} catch (Exception e1) {
			e1.printStackTrace();
			throw new RuntimeException(e1);
		}
	}

}
