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
	private static String defaultStands4UserId;
	private static String defaultStands4Cache;
	private static String defaultStands4TokenId;
	private static String defaultGoogleApiKey;
	private static String defaultGoogleCseId;
	private static String defaultBingKey;
	private static String configFile;
	private static String defaultWebsearchCache;

	/**
	 * Set the configuration file.
	 * 
	 * @param filename
	 *            the configuration file.
	 */
	public static void setConfigFile(String filename) {
		configFile = filename;
	}

	/**
	 * @return the default Google API key.
	 */
	public static String getDefaultGoogleApiKey() {
		if (defaultGoogleApiKey == null)
			initialize();
		if (defaultGoogleApiKey.isEmpty() || defaultGoogleApiKey.equals("GOOGLE_API_KEY"))
			throw new RuntimeException(
					"Configuration file "
							+ configFile
							+ " has dummy value 'GOOGLE_API_KEY' or is unset. Please replace with an actual Google API key.");
		return defaultGoogleApiKey;
	}

	/**
	 * @return the default Google Custom Search Engine ID.
	 */
	public static String getDefaultGoogleCseId() {
		if (defaultGoogleCseId == null)
			initialize();
		if (defaultGoogleCseId.isEmpty() || defaultGoogleCseId.equals("GOOGLE_CSE_ID"))
			throw new RuntimeException(
					"Configuration file "
							+ configFile
							+ " has dummy value 'GOOGLE_CSE_ID' or is unset. Please replace with an actual google CSE id.");
		return defaultGoogleCseId;
	}

	/**
	 * @return the default Bing key.
	 */
	public static String getDefaultBingKey() {
		if (defaultBingKey == null)
			initialize();
		if (defaultBingKey.isEmpty() || defaultBingKey.equals("BING_KEY"))
			throw new RuntimeException(
					"Configuration file "
							+ configFile
							+ " has dummy value 'BING_KEY' or is unset. Please replace with an actual bing key.");
		return defaultBingKey;
	}

	/**
	 * Load data from file.
	 */
	private static void initialize() {
		DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
		DocumentBuilder builder;
		Document doc;
		try {
			builder = factory.newDocumentBuilder();
			doc = builder.parse(new FileInputStream(configFile));
			defaultBingKey = getConfigValue("bing", "key", doc);
			defaultGoogleApiKey = getConfigValue("google-cse", "api-key", doc);
			defaultGoogleCseId = getConfigValue("google-cse", "cse-id", doc);
			defaultWebsearchCache = getConfigValue("cache", "websearch-cache", doc);
			defaultStands4Cache = getConfigValue("stands4", "cache", doc);
			defaultStands4TokenId = getConfigValue("stands4", "tokenid", doc);
			defaultStands4UserId = getConfigValue("stands4", "uid", doc);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private static String getConfigValue(String setting, String name,
			Document doc) throws XPathExpressionException {
		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();
		XPathExpression userExpr = xpath.compile("smaph/setting[@name=\""
				+ setting + "\"]/param[@name=\"" + name + "\"]/@value");
		return userExpr.evaluate(doc);
	}

	public static String getDefaultWebsearchCache() {
		if (defaultWebsearchCache == null)
			initialize();
		return defaultWebsearchCache.isEmpty() ? null : defaultWebsearchCache;
	}

	public static String getDefaultStands4TokenId() {
		if (defaultStands4TokenId == null)
			initialize();
		return defaultStands4TokenId;
	}
	public static String getDefaultStands4UserId() {
		if (defaultStands4UserId == null)
			initialize();
		return defaultStands4UserId;
	}
	public static String getDefaultStands4Cache() {
		if (defaultStands4Cache == null)
			initialize();
		return defaultStands4Cache;
	}

}
