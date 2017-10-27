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

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.lang.invoke.MethodHandles;
import java.net.URI;
import java.util.HashMap;
import java.util.List;

import org.apache.commons.lang3.tuple.Pair;
import org.codehaus.jettison.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unipi.di.acube.smaph.SmaphUtils;

public class CachedWAT2Annotator extends WAT2Annotator {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	private static HashMap<String, byte[]> url2jsonCache = new HashMap<>();
	private static long flushCounter = 0;
	private static final int FLUSH_EVERY = 200;
	private static String resultsCacheFilename = null;

	public static class CachedWAT2AnnotatorBuilder extends WAT2Annotator.WAT2AnnotatorBuilder {
		public static CachedWAT2AnnotatorBuilder builder() {
			return new CachedWAT2AnnotatorBuilder();
		}

		public CachedWAT2Annotator build() {
			return new CachedWAT2Annotator(baseUri, tokenizer, method, debug, gcubeToken);
		}
	}

	protected CachedWAT2Annotator(String uri, String tokenizer, String method, int debug, String gcubeToken) {
		super(uri, tokenizer, method, debug, gcubeToken);
	}

	private static synchronized void increaseFlushCounter() throws FileNotFoundException, IOException {
		flushCounter++;
		if ((flushCounter % FLUSH_EVERY) == 0)
			flush();
	}

	public static synchronized void flush() throws FileNotFoundException, IOException {
		if (flushCounter > 0 && resultsCacheFilename != null) {
			LOG.info("Flushing WAT cache... ");
			new File(resultsCacheFilename).createNewFile();
			ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(resultsCacheFilename));
			oos.writeObject(url2jsonCache);
			oos.close();
			LOG.info("Flushing WAT cache Done.");
		}
	}

	public static void setCache(String cacheFilename) throws FileNotFoundException, IOException, ClassNotFoundException {
		if (resultsCacheFilename != null && resultsCacheFilename.equals(cacheFilename))
			return;
		LOG.info("Loading WAT2 cache...");
		resultsCacheFilename = cacheFilename;
		if (new File(resultsCacheFilename).exists()) {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(resultsCacheFilename));
			url2jsonCache = (HashMap<String, byte[]>) ois.readObject();
			ois.close();
		}
	}

	public static void clearCache() {
		url2jsonCache = new HashMap<>();
		System.gc();
	}

	@Override
	protected JSONObject queryJson(String baseUrl, List<Pair<String, String>> getParameters) throws Exception {

		URI requestUri = getRequestUri(baseUrl, getParameters);

		String cacheKey = requestUri.toString();
		byte[] compressed = url2jsonCache.get(cacheKey);
		if (compressed != null) {
			try {
				String jsonString = SmaphUtils.decompress(compressed);
				return new JSONObject(jsonString);
			} catch (IOException e) {
				LOG.warn("Broken Gzip, re-downloading");
			}
		}

		increaseFlushCounter();
		JSONObject obj = super.queryJson(baseUrl, getParameters);
		url2jsonCache.put(cacheKey, SmaphUtils.compress(obj.toString()));
		return obj;

	}
}
