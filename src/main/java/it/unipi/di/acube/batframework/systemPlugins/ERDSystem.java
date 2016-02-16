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

import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.problems.C2WSystem;
import it.unipi.di.acube.batframework.utils.AnnotationException;
import it.unipi.di.acube.batframework.utils.FreebaseApi;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.lang.invoke.MethodHandles;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;
import java.util.HashSet;

import org.apache.http.entity.mime.HttpMultipartMode;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.StringBody;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * An interface to a generic ERD System, as defined in <a
 * href="http://web-ngram.research.microsoft.com/erd2014/Rules.aspx"
 * >http://web-ngram.research.microsoft.com/erd2014/Rules.aspx</a>.
 * 
 * @author Marco Cornolti
 * 
 */
public class ERDSystem implements C2WSystem {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private String url;
	private String name;
	private String run;
	private long lastTime = -1;
	private long calib = -1;
	private FreebaseApi freebApi;
	private WikipediaApiInterface wikiApi;

	/**
	 * @param url
	 *            the URL of the ERD system.
	 * @param run
	 *            the runID of the ERD system.
	 * @param name
	 *            the name of the system.
	 * @param freebApi
	 *            an API to Freebase.
	 * @param wikiApi
	 *            an API to Wikipedia.
	 */
	public ERDSystem(String url, String run, String name, FreebaseApi freebApi,
			WikipediaApiInterface wikiApi) {
		this.url = url;
		this.name = name;
		this.run = run;
		this.freebApi = freebApi;
		this.wikiApi = wikiApi;
	}

	@Override
	public String getName() {
		return this.name;
	}

	@Override
	public long getLastAnnotationTime() {
		return lastTime - calib > 0 ? lastTime - calib : 0;
	}

	@Override
	public HashSet<Tag> solveC2W(String text) throws AnnotationException {
		lastTime = Calendar.getInstance().getTimeInMillis();
		HashSet<Tag> res = new HashSet<Tag>();
		try {
			URL erdApi = new URL(url);

			HttpURLConnection connection = (HttpURLConnection) erdApi
					.openConnection();
			connection.setDoOutput(true);
			connection.setRequestMethod("POST");

			MultipartEntity multipartEntity = new MultipartEntity(
					HttpMultipartMode.STRICT);
			multipartEntity.addPart("runID", new StringBody(this.run));
			multipartEntity.addPart("TextID",
					new StringBody("" + text.hashCode()));
			multipartEntity.addPart("Text", new StringBody(text));

			connection.setRequestProperty("Content-Type", multipartEntity
					.getContentType().getValue());
			OutputStream out = connection.getOutputStream();
			try {
				multipartEntity.writeTo(out);
			} finally {
				out.close();
			}

			int status = ((HttpURLConnection) connection).getResponseCode();
			if (status != 200) {
				BufferedReader br = new BufferedReader(new InputStreamReader(
						connection.getErrorStream()));
				String line = null;
				while ((line = br.readLine()) != null)
					LOG.info(line);
				throw new RuntimeException();
			}

			BufferedReader br = new BufferedReader(new InputStreamReader(
					connection.getInputStream()));
			String line = null;
			while ((line = br.readLine()) != null) {
				String mid = line.split("\t")[2];
				String title = freebApi.midToTitle(mid);
				int wid;
				if (title == null || (wid = wikiApi.getIdByTitle(title)) == -1)
					LOG.info("Discarding mid=" + mid);
				else
					res.add(new Tag(wid));
			}

		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

		lastTime = Calendar.getInstance().getTimeInMillis() - lastTime;
		return res;
	}
}
