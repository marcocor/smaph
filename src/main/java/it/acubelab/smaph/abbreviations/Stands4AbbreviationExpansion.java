package it.acubelab.smaph.abbreviations;

import it.acubelab.smaph.SmaphAnnotatorDebugger;

import java.io.*;
import java.net.*;
import java.util.*;

import javax.xml.parsers.*;
import javax.xml.xpath.*;

import org.codehaus.jettison.json.JSONObject;
import org.w3c.dom.*;

public class Stands4AbbreviationExpansion implements AbbreviationExpansion {
	private static final String API_URL="http://www.stands4.com/services/v2/abbr.php";
	private static int flushCounter = 0;
	private String tokenId, uid;
	private static final int MAX_RETRY = 3;
	private static final int FLUSH_EVERY = 50;
	private static HashMap<String, String[]> abbrToExpansion = new HashMap<>();
	private static String resultsCacheFilename;

	public Stands4AbbreviationExpansion(String tokenId, String uid){
		this.tokenId = tokenId;
		this.uid = uid;
	}
	
	private synchronized void increaseFlushCounter()
			throws FileNotFoundException, IOException {
		flushCounter++;
		if (flushCounter % FLUSH_EVERY == 0)
			flush();
	}

	/**Query the API and returns the list of expansions. Update the cache.
	 * @param abbrev lowercase abbreviation.
	 * @throws Exception
	 */
	private synchronized String[] queryApi(String abbrev, int retryLeft)
			throws Exception {
		if (retryLeft < MAX_RETRY)
			Thread.sleep(1000);
		URL url = new URL(String.format("%s?uid=%s&tokenid=%s&term=%s",
				API_URL, uid, tokenId, URLEncoder.encode(abbrev, "utf8")));

		boolean cached = abbrToExpansion.containsKey(abbrev);
		SmaphAnnotatorDebugger.out.printf("%s %s%n",
				cached ? "<cached>" : "Querying", url);
		if (cached) return abbrToExpansion.get(abbrev);


		HttpURLConnection connection = (HttpURLConnection) url
					.openConnection();
		connection.setConnectTimeout(0);
		connection.setRequestProperty("Accept", "*/*");
		connection
		.setRequestProperty("Content-Type", "multipart/form-data");

		connection.setUseCaches(false);

		if (connection.getResponseCode() != 200) {
			Scanner s = new Scanner(connection.getErrorStream())
					.useDelimiter("\\A");
			System.err.printf("Got HTTP error %d. Message is: %s%n",
					connection.getResponseCode(), s.next());
			s.close();
			throw new RuntimeException("Got response code:"
					+ connection.getResponseCode());
		}

		DocumentBuilder builder = DocumentBuilderFactory.newInstance().newDocumentBuilder();
		Document doc;
		try {
			doc = builder.parse(connection.getInputStream());
		} catch (IOException e) {
			System.out.print("Got error while querying: " + url);
			throw e;
		}

		XPathFactory xPathfactory = XPathFactory.newInstance();
		XPath xpath = xPathfactory.newXPath();
		XPathExpression resourceExpr = xpath.compile("//definition/text()");

		NodeList resources = (NodeList) resourceExpr.evaluate(doc,
				XPathConstants.NODESET);

		Vector<String> resVect = new Vector<>();
		for (int i=0; i<resources.getLength(); i++){
			String expansion = resources.item(i).getTextContent().replace(String.valueOf((char) 160), " ").trim();
			if (!resVect.contains(expansion))
				resVect.add(expansion);
		}
		
		String[] res = resVect.toArray(new String[]{});
		abbrToExpansion.put(abbrev, res);
		increaseFlushCounter();
		return res;
	}

	/**
	 * Set the file to which the abbreviation cache is bound.
	 * 
	 * @param cacheFilename
	 *            the cache file name.
	 * @throws FileNotFoundException
	 *             if the file could not be open for reading.
	 * @throws IOException
	 *             if something went wrong while reading the file.
	 * @throws ClassNotFoundException
	 *             is the file contained an object of the wrong class.
	 */
	public static void setCache(String cacheFilename)
			throws FileNotFoundException, IOException, ClassNotFoundException {
		if (resultsCacheFilename != null
				&& resultsCacheFilename.equals(cacheFilename))
			return;
		System.out.println("Loading bing cache...");
		resultsCacheFilename = cacheFilename;
		if (new File(resultsCacheFilename).exists()) {
			ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
					resultsCacheFilename));
			abbrToExpansion = (HashMap<String, String[]>) ois.readObject();
			ois.close();
		}
	}

	public static synchronized void flush() throws FileNotFoundException, IOException {
		if (flushCounter > 0 && resultsCacheFilename != null) {
			SmaphAnnotatorDebugger.out.print("Flushing STANDS4 cache... ");
			new File(resultsCacheFilename).createNewFile();
			ObjectOutputStream oos = new ObjectOutputStream(
					new FileOutputStream(resultsCacheFilename));
			oos.writeObject(abbrToExpansion);
			oos.close();
			SmaphAnnotatorDebugger.out.println("Flushing STANDS4 cache Done.");
		}
	}
	
	private String clean(String input){
		return input.replaceAll("\\W", "").toLowerCase();
	}
	
	@Override
	public boolean isAbbreviationOf(String abbrev, String exp) throws Exception {
		abbrev = clean(abbrev);
		return Arrays.asList(queryApi(abbrev, MAX_RETRY)).contains(exp);
	}

	@Override
	public List<String> expand(String abbrev) throws Exception {
		abbrev = clean(abbrev);
		return Arrays.asList(queryApi(abbrev, MAX_RETRY));
	}

}
