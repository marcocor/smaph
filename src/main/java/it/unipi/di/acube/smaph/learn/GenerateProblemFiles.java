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

package it.unipi.di.acube.smaph.learn;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Vector;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.OptionBuilder;
import org.apache.commons.cli.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.systemPlugins.CachedWATAnnotator;
import it.unipi.di.acube.batframework.utils.WikipediaInterface;
import it.unipi.di.acube.batframework.utils.WikipediaLocalInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphBuilder;
import it.unipi.di.acube.smaph.SmaphBuilder.SmaphVersion;
import it.unipi.di.acube.smaph.datasets.wikiAnchors.EntityToAnchors;
import it.unipi.di.acube.smaph.datasets.wikitofreebase.WikipediaToFreebase;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.learn.GenerateTrainingAndTest.OptDataset;
import it.unipi.di.acube.smaph.learn.normalizer.NoFeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;

public class GenerateProblemFiles {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static WikipediaInterface wikiApi;
	private static WikipediaToFreebase wikiToFreeb;
	private static EntityToAnchors e2a;

	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new GnuParser();
		Options options = new Options();

		options.addOption(OptionBuilder.withLongOpt("dump-entity-filter")
		        .withDescription("Dump feature file for entity filter").create("a"));
		options.addOption(OptionBuilder.withLongOpt("dump-annotation-regressor")
		        .withDescription("Dump feature file for annotation regressor").create("e"));
		options.addOption(OptionBuilder.withLongOpt("dump-collective-rank")
		        .withDescription("Dump feature file for collective ranker").create("c"));
		options.addOption(OptionBuilder.withLongOpt("dump-greedy")
		        .withDescription("Dump feature file for greedy annotator (first step only!)").create("g"));
		options.addOption(OptionBuilder.withLongOpt("websearch-piggyback").hasArg().withArgName("WEBSEARCH")
		        .withDescription("What web search engine to piggyback on. Can be either `bing' or `google'.").create("w"));
		options.addOption("exclude_s1", false, "Exclude entity source 1.");
		options.addOption("exclude_s2", false, "Exclude entity source 2.");
		options.addOption("exclude_s3", false, "Exclude entity source 3.");

		CommandLine line = parser.parse(options, args);

		boolean s1 = !line.hasOption("exclude_s1");
		boolean s2 = !line.hasOption("exclude_s2");
		boolean s3 = !line.hasOption("exclude_s3");

		SmaphBuilder.Websearch ws = SmaphBuilder.websearchFromString(line.getOptionValue("websearch-piggyback"));

		Locale.setDefault(Locale.US);
		SmaphConfig c = SmaphConfig.fromConfigFile("smaph-config.xml");
		wikiApi = WikipediaLocalInterface.open(c.getDefaultWikipagesStorage());
		wikiToFreeb = WikipediaToFreebase.open(c.getDefaultWikipediaToFreebaseStorage());
		e2a = EntityToAnchors.fromDB(c.getDefaultEntityToAnchorsStorage());
		WATRelatednessComputer.setCache("relatedness.cache");
		CachedWATAnnotator.setCache("wikisense.cache");

		if (line.hasOption("dump-entity-filter"))
			generateEFModel(ws, s1, s2, s3, c);
		if (line.hasOption("dump-annotation-regressor"))
			generateAnnotationRegressorModel(ws, s1, s2, s3, c);
		if (line.hasOption("dump-collective-rank"))
			generateCollectiveModel(ws, s1, s2, s3, c);
		if (line.hasOption("dump-greedy"))
			generateGreedyModel(ws, s1, s2, s3, c);

		CachedWATAnnotator.flush();
		WATRelatednessComputer.flush();
	}

	public static void generateEFModel(SmaphBuilder.Websearch ws, boolean s1, boolean s2, boolean s3, SmaphConfig c) throws Exception {
		OptDataset opt = OptDataset.SMAPH_DATASET;
		SmaphAnnotator smaphGatherer = SmaphBuilder.getSmaphGatherer(wikiApi, wikiToFreeb, e2a, s1, s2, s3, ws, c);

		ExampleGatherer<Tag, HashSet<Tag>> trainEntityFilterGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
		ExampleGatherer<Tag, HashSet<Tag>> develEntityFilterGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(smaphGatherer, trainEntityFilterGatherer,
		        develEntityFilterGatherer, null, null, null, null, null, null, -1, null, null, null, null, wikiApi, wikiToFreeb, opt);

		String label = SmaphBuilder.getDefaultLabel(SmaphVersion.ENTITY_FILTER, ws, s1, s2, s3);
		LOG.info("Building Z-score normalizer over training set...");
		ZScoreFeatureNormalizer fNormEF = ZScoreFeatureNormalizer.fromGatherer(trainEntityFilterGatherer);
		fNormEF.dump(new File(String.format("train_%s.zscore", label)));
		LOG.info("Dumping entity filter training problems (scaled)...");
		trainEntityFilterGatherer.dumpExamplesLibSvm(String.format("train_zscore_%s.dat", label),
		        fNormEF);
		LOG.info("Dumping entity filter training problems (original values)...");
		trainEntityFilterGatherer.dumpExamplesLibSvm(String.format("train_%s.dat", label),
		        new NoFeatureNormalizer());
		LOG.info("Dumping entity filter development problems (scaled)...");
		develEntityFilterGatherer.dumpExamplesLibSvm(String.format("devel_zscore_%s.dat", label),
		        fNormEF);
		LOG.info("Dumping entity filter development problems (original values)...");
		develEntityFilterGatherer.dumpExamplesLibSvm(String.format("devel_%s.dat", label),
		        new NoFeatureNormalizer());
	}

	public static void generateAnnotationRegressorModel(SmaphBuilder.Websearch ws, boolean s1, boolean s2, boolean s3, SmaphConfig c)
	        throws Exception {
		OptDataset opt = OptDataset.SMAPH_DATASET;
		SmaphAnnotator smaphGatherer = SmaphBuilder.getSmaphGatherer(wikiApi, wikiToFreeb, e2a, s1, s2, s3, ws, c);

		ExampleGatherer<Annotation, HashSet<Annotation>> trainAnnotationRegressorGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
		ExampleGatherer<Annotation, HashSet<Annotation>> develAnnotationRegressorGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
		List<String> develInstances = new Vector<>();
		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(smaphGatherer, null, null, trainAnnotationRegressorGatherer,
		        develAnnotationRegressorGatherer, null, null, null, null, -1, null, null, null, develInstances, wikiApi, wikiToFreeb, opt);

		String label = SmaphBuilder.getDefaultLabel(SmaphVersion.ANNOTATION_REGRESSOR, ws, s1, s2, s3);
		LOG.info("Building Z-score normalizer over training set...");
		ZScoreFeatureNormalizer fNormAR = ZScoreFeatureNormalizer.fromGatherer(trainAnnotationRegressorGatherer);
		fNormAR.dump(new File(String.format("train_%s.zscore", label)));
		LOG.info("Dumping annotation regressor training problems (scaled)...");
		trainAnnotationRegressorGatherer.dumpExamplesLibSvm(String.format("train_zscore_%s.dat", label),
		        fNormAR);
		LOG.info("Dumping annotation regressor training problems (original values)...");
		trainAnnotationRegressorGatherer.dumpExamplesLibSvm(String.format("train_%s.dat", label),
		        new NoFeatureNormalizer());
		LOG.info("Dumping annotation regressor development problems (scaled)...");
		develAnnotationRegressorGatherer.dumpExamplesLibSvm(String.format("devel_zscore_%s.dat", label),
		        fNormAR);
		LOG.info("Dumping annotation regressor development problems (original values)...");
		develAnnotationRegressorGatherer.dumpExamplesLibSvm(String.format("devel_%s.dat", label),
		        new NoFeatureNormalizer());
	}

	public static void generateGreedyModel(SmaphBuilder.Websearch ws, boolean s1, boolean s2, boolean s3, SmaphConfig c)
	        throws Exception {
		OptDataset opt = OptDataset.SMAPH_DATASET;
		SmaphAnnotator smaphGatherer = SmaphBuilder.getSmaphGatherer(wikiApi, wikiToFreeb, e2a, s1, s2, s3, ws, c);

		ExampleGatherer<Annotation, HashSet<Annotation>> trainGreedyGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
		ExampleGatherer<Annotation, HashSet<Annotation>> develGreedyGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
		List<String> develInstances = new Vector<>();
		List<HashSet<Annotation>> greedyPartialSolutionsTrain = new Vector<>();
		List<HashSet<Annotation>> greedyPartialSolutionsDevel = new Vector<>();		
		int step = 0;
		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(smaphGatherer, null, null, null, null, null, null,
		        greedyPartialSolutionsTrain, greedyPartialSolutionsDevel, step, trainGreedyGatherer, develGreedyGatherer, null,
		        develInstances, wikiApi, wikiToFreeb, opt);

		String label = String.format("%s.%d", SmaphBuilder.getDefaultLabel(SmaphVersion.GREEDY, ws, s1, s2, s3), step);
		LOG.info("Building Z-score normalizer over training set...");
		ZScoreFeatureNormalizer fNormAR = ZScoreFeatureNormalizer.fromGatherer(trainGreedyGatherer);
		fNormAR.dump(new File(String.format("train_%s.zscore", label)));
		LOG.info("Dumping annotation regressor training problems (scaled)...");
		trainGreedyGatherer.dumpExamplesLibSvm(String.format("train_zscore_%s.dat", label), fNormAR);
		LOG.info("Dumping annotation regressor training problems (original values)...");
		trainGreedyGatherer.dumpExamplesLibSvm(String.format("train_%s.dat", label), new NoFeatureNormalizer());
		LOG.info("Dumping annotation regressor development problems (scaled)...");
		develGreedyGatherer.dumpExamplesLibSvm(String.format("devel_zscore_%s.dat", label), fNormAR);
		LOG.info("Dumping annotation regressor development problems (original values)...");
		develGreedyGatherer.dumpExamplesLibSvm(String.format("devel_%s.dat", label), new NoFeatureNormalizer());
	}

	public static void generateCollectiveModel(SmaphBuilder.Websearch ws, boolean s1, boolean s2, boolean s3, SmaphConfig c) throws Exception {
		OptDataset opt = OptDataset.SMAPH_DATASET;

		SmaphAnnotator smaphGatherer = SmaphBuilder.getSmaphGatherer(wikiApi, wikiToFreeb, e2a, s1, s2, s3, ws, c);
		CachedWATAnnotator.setCache("wikisense.cache");

		ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> trainCollectiveGatherer = new ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>>();
		ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> develCollectiveGatherer = new ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>>();
		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(smaphGatherer, null, null, null, null, trainCollectiveGatherer,
		        develCollectiveGatherer, null, null, -1, null, null, null, null, wikiApi, wikiToFreeb, opt);

		String label = SmaphBuilder.getDefaultLabel(SmaphVersion.COLLECTIVE, ws, s1, s2, s3);
		LOG.info("Dumping annotation regressor training problems (original values)...");
		trainCollectiveGatherer.dumpExamplesRankLib(String.format("train_%s.dat", label),
		        new NoFeatureNormalizer());
		LOG.info("Dumping annotation regressor development problems (original values)...");
		develCollectiveGatherer.dumpExamplesRankLib(String.format("devel_%s.dat", label),
		        new NoFeatureNormalizer());
	}

	public static String getModelFileNameBaseRL(int[] ftrs) {
		return String.format("models/model_%s_RL", getFtrListRepresentation(ftrs));
	}

	public static String getModelFileNameBaseLB(int[] ftrs, double C) {
		return String.format("models/model_%s_LB_%.3f_%.8f", getFtrListRepresentation(ftrs), C);
	}

	public static String getModelFileNameBaseEF(int[] ftrs, double wPos, double wNeg, double gamma, double C) {
		return String.format("models/model_%s_EF_%.5f_%.5f_%.8f_%.8f", getFtrListRepresentation(ftrs), wPos, wNeg, gamma, C);
	}

	public static String getModelFileNameBaseAF(int[] ftrs, double c, double anchorMaxED) {
		return String.format("models/model_%s_AF_%.8f_%.3f", getFtrListRepresentation(ftrs), c, anchorMaxED);
	}

	public static String getModelFileNameBaseAF(int[] ftrs, double c, double gamma, double anchorMaxED) {
		return String.format("models/model_%s_AF_%.8f_%.3f_%.8f", getFtrListRepresentation(ftrs), c, anchorMaxED, gamma);
	}

	public static String generateFeatureListFile(int[] ftrs) throws IOException {
		String filename = "/tmp/feature_list_" + getFtrListRepresentation(ftrs);
		FileWriter fw = new FileWriter(filename);
		for (int f : ftrs)
			fw.write(String.format("%d%n", f));
		fw.close();
		return filename;
	}

	private static String getFtrListRepresentation(int[] ftrs) {
		Arrays.sort(ftrs);
		String ftrList = "";
		int i = 0;
		int lastInserted = -1;
		int lastBlockSize = 1;
		while (i < ftrs.length) {
			int current = ftrs[i];
			if (i == 0) // first feature
				ftrList += current;
			else if (current == lastInserted + 1) { // continuation of a block
				if (i == ftrs.length - 1)// last element, close block
					ftrList += "-" + current;
				lastBlockSize++;
			} else {// start of a new block
				if (lastBlockSize > 1) {
					ftrList += "-" + lastInserted;
				}
				ftrList += "," + current;
				lastBlockSize = 1;
			}
			lastInserted = current;
			i++;
		}
		return ftrList;
	}
}
