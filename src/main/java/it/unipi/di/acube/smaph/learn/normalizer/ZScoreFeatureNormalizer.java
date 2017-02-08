package it.unipi.di.acube.smaph.learn.normalizer;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.Serializable;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Vector;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;

import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.learn.ExampleGatherer;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;

public class ZScoreFeatureNormalizer extends FeatureNormalizer implements Serializable {

	private HashMap<String, Double> avgs = new HashMap<>();
	private HashMap<String, Double> stdDevs = new HashMap<>();
	private static Mean meanComputer = new Mean();
	private static StandardDeviation stdDevComputer = new StandardDeviation();
	private double defaultValue = 0.0; // Default value for missing features. 0.0 is the average value.

	private ZScoreFeatureNormalizer() {
	}

	public static ZScoreFeatureNormalizer fromUrl(URL zscore, FeaturePack<?> fp) {
		ZScoreFeatureNormalizer fn = new ZScoreFeatureNormalizer();
		fn.load(zscore, fp);
		return fn;
	}

	public static <E extends Serializable, G extends Serializable> ZScoreFeatureNormalizer fromGatherer(ExampleGatherer<E, G> exampleGatherer) {
		ZScoreFeatureNormalizer fn = new ZScoreFeatureNormalizer();
		List<FeaturePack<E>> ftrPacks = exampleGatherer.getAllFeaturePacks();
		for (String ftrName : ftrPacks.get(0).getFeatureNames()) {
			Vector<Double> ftrValues = new Vector<>();
			for (FeaturePack<E> fp : ftrPacks)
				if (fp.featureIsSet(ftrName))
					ftrValues.add(fp.getFeature(ftrName));
			double[] ftrValArray = ArrayUtils.toPrimitive(ftrValues.toArray(new Double[] {}));

			double valMean = meanComputer.evaluate(ftrValArray);
			double stdDev = stdDevComputer.evaluate(ftrValArray, valMean);
			fn.avgs.put(ftrName, valMean);
			fn.stdDevs.put(ftrName, stdDev);
		}
		return fn;
	}

	@Override
	public double normalizeFeature(FeaturePack<?> fp, String ftrName) {
		if (!fp.featureIsSet(ftrName))
			return defaultValue;
		if (!avgs.containsKey(ftrName))
			return fp.getFeature(ftrName);
		double avg = avgs.get(ftrName);
		double stdDev = stdDevs.get(ftrName);
		if (Double.isNaN(avg) || Double.isNaN(stdDev))
			throw new RuntimeException(
			        "You are trying to normalize feature " + ftrName + " that has never been observed before.");
		if (stdDev == 0.0)
			return fp.getFeature(ftrName) - avg;
		return (fp.getFeature(ftrName) - avg) / stdDev;
	}

	private void load(URL zScoreFile, FeaturePack<?> fp) {
		Vector<String[]> tokensVect = new Vector<>();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(zScoreFile.openStream()));
			String line;
			while ((line = reader.readLine()) != null) {
				String[] tokens = line.split(" ");
				if (tokens.length != 3) {
					reader.close();
					throw new RuntimeException("Can't read line: [" + line + "]");
				}
				tokensVect.add(tokens);
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		for (String[] tokens : tokensVect) {
			String ftrName = tokens[0];
			if (fp.ftrNameToId(ftrName) < 0)
				throw new RuntimeException("Unrecognized feature " + tokens[0]);
			double avg = Double.parseDouble(tokens[1]);
			double stdDev = Double.parseDouble(tokens[2]);
			avgs.put(ftrName, avg);
			stdDevs.put(ftrName, stdDev);
		}
	}

	public void dump(File normFile) throws IOException {
		BufferedWriter br = new BufferedWriter(new FileWriter(normFile));
		for (String ftrName : SmaphUtils.sorted(avgs.keySet())) {
			br.write(String.format("%s %.16f %.16f%n", ftrName, avgs.get(ftrName), stdDevs.get(ftrName)));
		}

		br.close();
	}
}
