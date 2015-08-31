package it.unipi.di.acube.smaph.learn.normalizer;

import it.unipi.di.acube.smaph.learn.ExampleGatherer;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;

import java.io.*;
import java.util.*;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;


public class ZScoreFeatureNormalizer extends FeatureNormalizer {

	private HashMap<String, Double> avgs = new HashMap<>();
	private HashMap<String, Double> stdDevs = new HashMap<>();
	private Mean meanComputer = new Mean();
	private StandardDeviation stdDevComputer = new StandardDeviation();

	public ZScoreFeatureNormalizer(String zScoreFile, FeaturePack<?> fp) {
		load(zScoreFile, fp);
	}

	public <E extends Serializable, G extends Serializable> ZScoreFeatureNormalizer(
			ExampleGatherer<E,G> exampleGatherer) {
		List<FeaturePack<E>> ftrPacks = exampleGatherer.getAllFeaturePacks();
		for (String ftrName : ftrPacks.get(0).getFeatureNames()) {
			Vector<Double> ftrValues = new Vector<>();
			for (FeaturePack<E> fp : ftrPacks)
				if (fp.featureIsSet(ftrName))
					ftrValues.add(fp.getFeature(ftrName));
			double[] ftrValArray = ArrayUtils.toPrimitive(ftrValues
					.toArray(new Double[] {}));

			double valMean = meanComputer.evaluate(ftrValArray);
			double stdDev = stdDevComputer.evaluate(ftrValArray, valMean);
			avgs.put(ftrName, valMean);
			stdDevs.put(ftrName, stdDev);
		}
	}

	@Override
	public double normalizeFeature(FeaturePack<?> fp, String ftrName) {
		if (!fp.featureIsSet(ftrName))
			return 0.0;
		if (!avgs.containsKey(ftrName))
			return fp.getFeature(ftrName);
		double avg = avgs.get(ftrName);
		double stdDev = stdDevs.get(ftrName);
		if (Double.isNaN(avg) || Double.isNaN(stdDev))
			throw new RuntimeException("You are trying to normalize feature " + ftrName + " that has never been observed before.");
		if (stdDev == 0.0)
			return fp.getFeature(ftrName) - avg;
		return (fp.getFeature(ftrName) - avg) / stdDev;
	}

	private void load(String zScoreFile, FeaturePack<?> fp) {
		Vector<String[]> tokensVect = new Vector<>();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(zScoreFile)));
			String line;
			while ((line = reader.readLine()) != null) {
				String[] tokens = line.split(" ");
				if (tokens.length != 3) {
					reader.close();
					throw new RuntimeException("Can't read line: [" + line
							+ "]");
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

	public void dump(String file) throws IOException {
		BufferedWriter br = new BufferedWriter(new FileWriter(file));
		for (String ftrName : avgs.keySet()) {
			br.write(String.format("%s %f %f%n", ftrName, avgs.get(ftrName),
					stdDevs.get(ftrName)));
		}

		br.close();
	}
}
