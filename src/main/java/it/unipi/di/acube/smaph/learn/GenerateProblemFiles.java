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

import it.cnr.isti.hpc.erd.WikipediaToFreebase;
import it.unipi.di.acube.BingInterface;
import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.systemPlugins.CachedWATAnnotator;
import it.unipi.di.acube.batframework.utils.FreebaseApi;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphAnnotatorBuilder;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.learn.GenerateTrainingAndTest.OptDataset;
import it.unipi.di.acube.smaph.learn.normalizer.NoFeatureNormalizer;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;

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

public class GenerateProblemFiles {
	private final static Logger LOG = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
	private static String bingKey, freebKey, freebCache;
	private static WikipediaApiInterface wikiApi;
	private static FreebaseApi freebApi;
	private static WikipediaToFreebase wikiToFreebase;

	public static void main(String[] args) throws Exception {
		CommandLineParser parser = new GnuParser();
		Options options = new Options();

		options.addOption(OptionBuilder.withLongOpt("dump-entity-filter")
		        .withDescription("Dump feature file for annotation regressor").create("a"));
		options.addOption(OptionBuilder.withLongOpt("dump-annotation-regressor")
		        .withDescription("Dump feature file for annotation regressor").create("e"));
		options.addOption(OptionBuilder.withLongOpt("dump-collective-rank")
		        .withDescription("Dump feature file for annotation regressor").create("c"));
		options.addOption(OptionBuilder.withLongOpt("outfile-base").hasArg().withDescription("Prefix of the output file name.")
		        .create("f"));

		CommandLine line = parser.parse(options, args);

		Locale.setDefault(Locale.US);
		SmaphConfig.setConfigFile("smaph-config.xml");
		bingKey = SmaphConfig.getDefaultBingKey();
		freebKey = SmaphConfig.getDefaultFreebaseKey();
		freebCache = SmaphConfig.getDefaultFreebaseCache();
		BingInterface.setCache(SmaphConfig.getDefaultBingCache());
		wikiApi = WikipediaApiInterface.api();
		WATRelatednessComputer.setCache("relatedness.cache");
		freebApi = new FreebaseApi(freebKey, freebCache);
		CachedWATAnnotator.setCache("wikisense.cache");
		wikiToFreebase = new WikipediaToFreebase("mapdb");

		if (line.hasOption("dump-entity-filter"))
			generateEFModel(line.getOptionValue("outfile-base", ""));
		if (line.hasOption("dump-annotation-regressor"))
			generateIndividualAdvancedAnnotationModel(line.getOptionValue("outfile-base", ""));
		if (line.hasOption("dump-collective-rank"))
			generateCollectiveModel(line.getOptionValue("outfile-base", ""));

		CachedWATAnnotator.flush();
		WATRelatednessComputer.flush();
	}

	public static void generateEFModel(String fileNamePrefix) throws Exception {
		OptDataset opt = OptDataset.SMAPH_DATASET;

		SmaphAnnotator bingAnnotator = SmaphAnnotatorBuilder.getDefaultBingAnnotatorGatherer(wikiApi, bingKey, true, true, true);

		ExampleGatherer<Tag, HashSet<Tag>> trainEntityFilterGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
		ExampleGatherer<Tag, HashSet<Tag>> develEntityFilterGatherer = new ExampleGatherer<Tag, HashSet<Tag>>();
		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(bingAnnotator, trainEntityFilterGatherer,
		        develEntityFilterGatherer, null, null, null, null, null, null, wikiApi, wikiToFreebase, freebApi, opt, -1);

		LOG.info("Building Z-score normalizer over training set...");
		ZScoreFeatureNormalizer fNormEF = new ZScoreFeatureNormalizer(trainEntityFilterGatherer);
		fNormEF.dump("train_ef.zscore");
		LOG.info("Dumping entity filter training problems (scaled)...");
		trainEntityFilterGatherer.dumpExamplesLibSvm(fileNamePrefix + "train_ef_zscore.dat", fNormEF);
		LOG.info("Dumping entity filter training problems (original values)...");
		trainEntityFilterGatherer.dumpExamplesLibSvm(fileNamePrefix + "train_ef.dat", new NoFeatureNormalizer());
		LOG.info("Dumping entity filter development problems (scaled)...");
		develEntityFilterGatherer.dumpExamplesLibSvm(fileNamePrefix + "devel_ef_zscore.dat", fNormEF);
		LOG.info("Dumping entity filter development problems (original values)...");
		develEntityFilterGatherer.dumpExamplesLibSvm(fileNamePrefix + "devel_ef.dat", new NoFeatureNormalizer());
	}

	public static void generateIndividualAdvancedAnnotationModel(String fileNamePrefix) throws Exception {
		OptDataset opt = OptDataset.SMAPH_DATASET;
		double anchorMaxED = 0.7;
		SmaphAnnotator bingAnnotator = SmaphAnnotatorBuilder.getDefaultBingAnnotatorGatherer(wikiApi, bingKey, true, true, true);

		ExampleGatherer<Annotation, HashSet<Annotation>> trainAdvancedAnnotationGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
		ExampleGatherer<Annotation, HashSet<Annotation>> develAdvancedAnnotationGatherer = new ExampleGatherer<Annotation, HashSet<Annotation>>();
		List<String> develInstances = new Vector<>();
		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(bingAnnotator, null, null, null, null,
		        trainAdvancedAnnotationGatherer, develAdvancedAnnotationGatherer, null, develInstances, wikiApi, wikiToFreebase,
		        freebApi, opt, anchorMaxED);

		LOG.info("Building Z-score normalizer over training set...");
		ZScoreFeatureNormalizer fNormEF = new ZScoreFeatureNormalizer(trainAdvancedAnnotationGatherer);
		fNormEF.dump("train_ar.zscore");
		LOG.info("Dumping annotation regressor training problems (scaled)...");
		trainAdvancedAnnotationGatherer.dumpExamplesLibSvm(fileNamePrefix + "train_ar_zscore.dat", fNormEF);
		LOG.info("Dumping annotation regressor training problems (original values)...");
		trainAdvancedAnnotationGatherer.dumpExamplesLibSvm(fileNamePrefix + "train_ar.dat", new NoFeatureNormalizer());
		LOG.info("Dumping annotation regressor development problems (scaled)...");
		develAdvancedAnnotationGatherer.dumpExamplesLibSvm(fileNamePrefix + "devel_ar_zscore.dat", fNormEF);
		LOG.info("Dumping annotation regressor development problems (original values)...");
		develAdvancedAnnotationGatherer.dumpExamplesLibSvm(fileNamePrefix + "devel_ar.dat", new NoFeatureNormalizer());
	}

	public static void generateCollectiveModel(String fileNamePrefix) throws Exception {
		OptDataset opt = OptDataset.SMAPH_DATASET;
		boolean useS2 = true, useS3 = true, useS6 = true;

		SmaphAnnotator bingAnnotator = SmaphAnnotatorBuilder.getDefaultBingAnnotatorGatherer(wikiApi, bingKey, useS2, useS3,
		        useS6);
		CachedWATAnnotator.setCache("wikisense.cache");

		ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> trainCollectiveGatherer = new ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>>();
		ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>> develCollectiveGatherer = new ExampleGatherer<HashSet<Annotation>, HashSet<Annotation>>();
		GenerateTrainingAndTest.gatherExamplesTrainingAndDevel(bingAnnotator, null, null, trainCollectiveGatherer,
		        develCollectiveGatherer, null, null, null, null, wikiApi, wikiToFreebase, freebApi, opt, -1);

		LOG.info("Dumping annotation regressor training problems (original values)...");
		trainCollectiveGatherer.dumpExamplesRankLib(fileNamePrefix + "train_coll.dat", new NoFeatureNormalizer());
		LOG.info("Dumping annotation regressor development problems (original values)...");
		develCollectiveGatherer.dumpExamplesRankLib(fileNamePrefix + "devel_coll.dat", new NoFeatureNormalizer());
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
