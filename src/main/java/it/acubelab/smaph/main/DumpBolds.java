package it.acubelab.smaph.main;

import java.io.*;
import java.util.List;
import java.util.Vector;

import it.acubelab.batframework.datasetPlugins.SMAPHDataset;
import it.acubelab.batframework.problems.A2WDataset;
import it.acubelab.batframework.utils.Pair;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.BingInterface;
import it.acubelab.smaph.SmaphAnnotator;
import it.acubelab.smaph.SmaphConfig;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;

public class DumpBolds {

	public static void main(String[] args) throws Exception{
		SmaphConfig.setConfigFile("smaph-config.xml");
		String bingKey = SmaphConfig.getDefaultBingKey();
		String bingCache = SmaphConfig.getDefaultBingCache();
		WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache",
				"redirect.cache");
		
		A2WDataset ds = new SMAPHDataset("datasets/smaph/smaph_trainingA.xml",
				wikiApi);

		BingInterface bingInterface = new BingInterface(bingKey);
		BingInterface.setCache(bingCache);
		
		
		File fout = new File("dump_bolds.txt");
		FileOutputStream fos = new FileOutputStream(fout);
	 
		BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(fos));
	 
		for (String q : ds.getTextInstanceList()) {
			JSONObject bingReply = bingInterface.queryBing(q);
			JSONObject data = (JSONObject) bingReply.get("d");
			JSONObject results = (JSONObject) ((JSONArray) data.get("results"))
					.get(0);
			JSONArray webResults = (JSONArray) results.get("Web");

			List<Pair<String, Integer>> boldsAndRanks = new Vector<Pair<String, Integer>>();
			List<String> urls = new Vector<>();
			List<Pair<String, Vector<Pair<Integer, Integer>>>> snippetsToBolds = new Vector<>();
			SmaphAnnotator.getBoldsAndUrls(webResults, 50, boldsAndRanks, urls,
					snippetsToBolds, false);
			String line = q;
			for (Pair<String, Integer> boldAndRank : boldsAndRanks)
				line += "\t"+boldAndRank.first;
			line += "\n";
			bw.write(line);
		}
		bw.close();

	}

}
