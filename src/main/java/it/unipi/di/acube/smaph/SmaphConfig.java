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

package it.unipi.di.acube.smaph;

import java.io.FileInputStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;

import org.w3c.dom.Document;

public class SmaphConfig {
	private String defaultStands4UserId;
	private String defaultStands4Cache;
	private String defaultStands4TokenId;
	private String defaultGoogleApiKey;
	private String defaultGoogleCseId;
	private String defaultBingKey;
	private String defaultWebsearchCache;
	private String defaultWikipagesStorage;

	public SmaphConfig(String defaultStands4UserId, String defaultStands4Cache, String defaultStands4TokenId,
	        String defaultGoogleApiKey, String defaultGoogleCseId, String defaultBingKey, String defaultWebsearchCache,
	        String defaultWikipagesStorage) {
		this.defaultStands4UserId = defaultStands4UserId;
		this.defaultStands4Cache = defaultStands4Cache;
		this.defaultStands4TokenId = defaultStands4TokenId;
		this.defaultGoogleApiKey = defaultGoogleApiKey;
		this.defaultGoogleCseId = defaultGoogleCseId;
		this.defaultBingKey = defaultBingKey;
		this.defaultWebsearchCache = defaultWebsearchCache;
		this.defaultWikipagesStorage = defaultWikipagesStorage;
	}

	/**
	 * @return the default Google API key.
	 */
	public String getDefaultGoogleApiKey() {
		if (defaultGoogleApiKey.isEmpty() || defaultGoogleApiKey.equals("GOOGLE_API_KEY"))
			throw new RuntimeException(
			        "Configuration has dummy value 'GOOGLE_API_KEY' or is unset. Please replace with an actual Google API key.");
		return defaultGoogleApiKey;
	}

	/**
	 * @return the default Google Custom Search Engine ID.
	 */
	public String getDefaultGoogleCseId() {
		if (defaultGoogleCseId.isEmpty() || defaultGoogleCseId.equals("GOOGLE_CSE_ID"))
			throw new RuntimeException(
			        "Configuration has dummy value 'GOOGLE_CSE_ID' or is unset. Please replace with an actual google CSE id.");
		return defaultGoogleCseId;
	}

	/**
	 * @return the default Bing key.
	 */
	public String getDefaultBingKey() {
		if (defaultBingKey.isEmpty() || defaultBingKey.equals("BING_KEY"))
			throw new RuntimeException(
			        "Configuration file has dummy value 'BING_KEY' or is unset. Please replace with an actual bing key.");
		return defaultBingKey;
	}

	/**
	 * Load data from file.
	 */
	public static SmaphConfig fromConfigFile(String configFile) {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		Document doc;
		try {
			builder = factory.newDocumentBuilder();
			doc = builder.parse(new FileInputStream(configFile));
			return new SmaphConfig(getConfigValue("stands4", "uid", doc), getConfigValue("stands4", "cache", doc),
			        getConfigValue("stands4", "tokenid", doc), getConfigValue("google-cse", "api-key", doc),
			        getConfigValue("google-cse", "cse-id", doc), getConfigValue("bing", "key", doc),
			        getConfigValue("cache", "websearch-cache", doc), getConfigValue("wikipages", "storage", doc));
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private static String getConfigValue(String setting, String name, Document doc) throws XPathExpressionException {
		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();
		XPathExpression userExpr = xpath.compile("smaph/setting[@name=\"" + setting + "\"]/param[@name=\"" + name + "\"]/@value");
		return userExpr.evaluate(doc);
	}

	public String getDefaultWebsearchCache() {
		return defaultWebsearchCache == null || defaultWebsearchCache.isEmpty() ? null : defaultWebsearchCache;
	}

	public String getDefaultStands4TokenId() {
		return defaultStands4TokenId;
	}

	public String getDefaultStands4UserId() {
		return defaultStands4UserId;
	}

	public String getDefaultStands4Cache() {
		return defaultStands4Cache;
	}

	public String getDefaultWikipagesStorage() {
		return defaultWikipagesStorage;
	}

}
