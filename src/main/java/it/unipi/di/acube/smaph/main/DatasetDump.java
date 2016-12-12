package it.unipi.di.acube.smaph.main;

import it.unipi.di.acube.batframework.datasetPlugins.DatasetBuilder;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.batframework.utils.WikipediaLocalInterface;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.abbreviations.Stands4AbbreviationExpansion;

import java.io.FileWriter;
import java.lang.invoke.MethodHandles;
import java.util.HashSet;

import org.codehaus.jettison.json.JSONWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DatasetDump {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());

	public static void main(String[] args) throws Exception {
		SmaphConfig c = SmaphConfig.fromConfigFile("smaph-config.xml");
    	String tokenId = c.getDefaultStands4TokenId();
    	String uid = c.getDefaultStands4UserId();
    	String cache = c.getDefaultStands4Cache();
		Stands4AbbreviationExpansion ae = new Stands4AbbreviationExpansion(
				tokenId, uid);
		Stands4AbbreviationExpansion.setCache(cache);

		{
			WikipediaInterface wikiApi = WikipediaLocalInterface.open(c.getDefaultWikipagesStorage());
			A2WDataset ds1 = DatasetBuilder.getGerdaqTrainA(wikiApi);
			A2WDataset ds2 = DatasetBuilder.getGerdaqTrainB(wikiApi);
			A2WDataset ds3 = DatasetBuilder.getGerdaqTest(wikiApi);
			A2WDataset ds4 = DatasetBuilder.getGerdaqDevel(wikiApi);
			
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
							LOG.info("{} -> {}", t, expansion);
						}
						wr.endArray();
					}
			wr.endObject();
			fw.close();
			Stands4AbbreviationExpansion.flush();
		}
	}

}
