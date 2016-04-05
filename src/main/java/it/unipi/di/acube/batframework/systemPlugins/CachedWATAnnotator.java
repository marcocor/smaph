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

package it.unipi.di.acube.batframework.systemPlugins;

import java.io.*;
import java.lang.invoke.MethodHandles;
import java.net.*;
import java.util.*;

import org.codehaus.jettison.json.JSONObject;
import org.slf4j.LoggerFactory;
import org.slf4j.Logger;

import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.smaph.SmaphUtils;

public class CachedWATAnnotator extends WATAnnotator {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static HashMap<String, byte[]> url2jsonCache = new HashMap<>();
	private static long flushCounter = 0;
	private static final int FLUSH_EVERY = 200;
	private static String resultsCacheFilename = null;

	public CachedWATAnnotator(String ip, int port, String method, String sortBy,
			String relatedness, String epsilon, String minLinkProbability) {
		super(ip, port, method, sortBy, relatedness, epsilon,
				minLinkProbability);
	}

	private static synchronized void increaseFlushCounter()
			throws FileNotFoundException, IOException {
		flushCounter++;
		if ((flushCounter % FLUSH_EVERY) == 0)
			flush();
	}

	public static synchronized void flush() throws FileNotFoundException,
			IOException {
		if (flushCounter > 0 && resultsCacheFilename != null) {
			LOG.info("Flushing WAT cache... ");
			new File(resultsCacheFilename).createNewFile();
			ObjectOutputStream oos = new ObjectOutputStream(
					new FileOutputStream(resultsCacheFilename));
			oos.writeObject(url2jsonCache);
			oos.close();
			LOG.info("Flushing WAT cache Done.");
		}
	}

	public static void setCache(String cacheFilename)
			throws FileNotFoundException, IOException, ClassNotFoundException {
		if (resultsCacheFilename != null
				&& resultsCacheFilename.equals(cacheFilename))
			return;
		LOG.info("Loading wikisense cache...");
		resultsCacheFilename = cacheFilename;
		if (new File(resultsCacheFilename).exists()) {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
					resultsCacheFilename));
			url2jsonCache = (HashMap<String, byte[]>) ois.readObject();
			ois.close();
		}
	}

	public static void clearCache() {
		url2jsonCache = new HashMap<>();
		System.gc();
	}

	@Override
	protected JSONObject queryJson(String parameters, String url,
			String getParameters, int retry) throws Exception {

		URL wikiSenseApi = new URL(String.format("%s?%s", url, getParameters));

		String cacheKey = wikiSenseApi.toExternalForm() + parameters.toString();
		byte[] compressed = url2jsonCache.get(cacheKey);
		if (compressed != null) {
			try {
				String jsonString = SmaphUtils.decompress(compressed);
				return new JSONObject(jsonString);
			} catch (IOException e) {
				LOG.warn("Broken Gzip, re-downloading");
			}
		}

		JSONObject obj = super.queryJson(parameters, url, getParameters, retry);
		url2jsonCache.put(cacheKey, SmaphUtils.compress(obj.toString()));
		increaseFlushCounter();

		return obj;
	}
}
