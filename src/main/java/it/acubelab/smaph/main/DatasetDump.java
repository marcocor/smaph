package it.acubelab.smaph.main;

import java.io.FileWriter;
import java.util.HashSet;

import org.codehaus.jettison.json.JSONArray;
import org.codehaus.jettison.json.JSONObject;
import org.codehaus.jettison.json.JSONWriter;

import it.acubelab.batframework.datasetPlugins.SMAPHDataset;
import it.acubelab.batframework.datasetPlugins.YahooWebscopeL24Dataset;
import it.acubelab.batframework.problems.*;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.SmaphConfig;
import it.acubelab.smaph.SmaphUtils;
import it.acubelab.smaph.abbreviations.Stands4AbbreviationExpansion;

public class DatasetDump {

	public static void main(String[] args) throws Exception {
		WikipediaApiInterface wikiApi = new WikipediaApiInterface("wid.cache",
				"redirect.cache");
		
    	SmaphConfig.setConfigFile("smaph-config.xml");
    	String tokenId = SmaphConfig.getDefaultStands4TokenId();
    	String uid = SmaphConfig.getDefaultStands4UserId();
    	String cache = SmaphConfig.getDefaultStands4Cache();
		Stands4AbbreviationExpansion ae = new Stands4AbbreviationExpansion(
				tokenId, uid);
		Stands4AbbreviationExpansion.setCache(cache);

		{
			A2WDataset ds1 = new SMAPHDataset(
					"datasets/smaph/smaph_trainingA.xml", wikiApi);
			A2WDataset ds2 = new SMAPHDataset(
					"datasets/smaph/smaph_trainingB.xml", wikiApi);
			A2WDataset ds3 = new SMAPHDataset(
					"datasets/smaph/smaph_test.xml", wikiApi);
			A2WDataset ds4 = new SMAPHDataset(
					"datasets/smaph/smaph_devel.xml", wikiApi);
			
			HashSet<String> insertedKeys = new HashSet<>();
			FileWriter fw = new FileWriter("abbrev.json");
			JSONWriter wr = new JSONWriter(fw);
			wr.object();
			for (A2WDataset ds : new A2WDataset[] {ds1,ds2,ds3,ds4})
				for (String q : ds.getTextInstanceList())
					for (String t : SmaphUtils.tokenize(q))
					if (!insertedKeys.contains(t)){
						insertedKeys.add(t);
						wr.key(t).array();
						for (String expansion : ae.expand(t)){
							wr.value(expansion);
							System.out.printf("%s -> %s%n", t, expansion);
						}
						wr.endArray();
					}
			wr.endObject();
			fw.close();
			Stands4AbbreviationExpansion.flush();
		}
	}

}
