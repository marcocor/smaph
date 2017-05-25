package it.unipi.di.acube.smaph.main.experiments;

import it.unipi.di.acube.batframework.datasetPlugins.DatasetBuilder;
import it.unipi.di.acube.batframework.datasetPlugins.GERDAQDataset;
import it.unipi.di.acube.batframework.utils.TestDataset;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.batframework.utils.WikipediaLocalInterface;
import it.unipi.di.acube.smaph.SmaphConfig;

public class DatasetInfo {

	public static void main(String[] args) throws Exception {
		SmaphConfig c = SmaphConfig.fromConfigFile("smaph-config.xml");
		WikipediaInterface wikiApi = WikipediaLocalInterface.open(c.getDefaultWikipagesStorage());
		GERDAQDataset trainA = DatasetBuilder.getGerdaqTrainA(wikiApi);
		GERDAQDataset trainB = DatasetBuilder.getGerdaqTrainB(wikiApi);
		GERDAQDataset test = DatasetBuilder.getGerdaqTest(wikiApi);
		GERDAQDataset dev = DatasetBuilder.getGerdaqDevel(wikiApi);
		
		TestDataset.dumpInfo(trainA, wikiApi);
		TestDataset.dumpInfo(trainB, wikiApi);
		TestDataset.dumpInfo(test, wikiApi);
		TestDataset.dumpInfo(dev, wikiApi);
	}
}
