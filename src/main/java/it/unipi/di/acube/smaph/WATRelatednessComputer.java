package it.unipi.di.acube.smaph;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;

import org.codehaus.jettison.json.JSONObject;

import it.unipi.di.acube.batframework.utils.Pair;
import it.unimi.dsi.fastutil.objects.Object2DoubleOpenHashMap;

public class WATRelatednessComputer {
	private static Object2DoubleOpenHashMap<Pair<Integer,Integer>> cache = new Object2DoubleOpenHashMap<>();
	private static long flushCounter = 0;
	private static final int FLUSH_EVERY = 200;
	private static final String URL_TEMPLATE = "http://wikisense.mkapp.it/rel/id?src=%d&dst=%d&relatedness=jaccard";
	private static String resultsCacheFilename = null;
	
	public static synchronized void increaseFlushCounter()
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
			oos.writeObject(cache);
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
			cache = (Object2DoubleOpenHashMap<Pair<Integer,Integer>>) ois.readObject();
			ois.close();
		}
	}

	private static double queryJson(int wid1, int wid2) {

		String resultStr = null;
		try {
			URL url = new URL(String.format(URL_TEMPLATE,wid1, wid2));
			System.out.print(url);
			
			HttpURLConnection conn = (HttpURLConnection) url.openConnection();
			conn.setRequestMethod("GET");
	         
			if (conn.getResponseCode() != 200) {
				Scanner s = new Scanner(conn.getErrorStream())
						.useDelimiter("\\A");
				System.err.printf("Got HTTP error %d. Message is: %s%n",
						conn.getResponseCode(), s.next());
				s.close();
			}

			Scanner s = new Scanner(conn.getInputStream())
					.useDelimiter("\\A");
			resultStr = s.hasNext() ? s.next() : "";

			JSONObject obj = new JSONObject(resultStr);
			
			increaseFlushCounter();

			double rel = obj.getDouble("value");
			System.out.println(" -> " + rel);

			return rel;

		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static  double getRelatedness(int wid1, int wid2) {
		if (wid2 < wid1) {
			int tmp = wid2;
			wid2 = wid1;
			wid2 = tmp;
		}
		Pair <Integer,Integer> p = new Pair<Integer,Integer>(wid1,wid2);
		if (!cache.containsKey(p))
			cache.put(p, queryJson(wid1, wid2));
			
		return cache.getDouble(p);
		
	}
}
