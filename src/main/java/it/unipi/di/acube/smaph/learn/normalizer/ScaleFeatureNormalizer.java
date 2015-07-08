package it.unipi.di.acube.smaph.learn.normalizer;

import it.unipi.di.acube.smaph.SmaphAnnotatorDebugger;
import it.unipi.di.acube.smaph.learn.ExampleGatherer;
import it.unipi.di.acube.smaph.learn.featurePacks.FeaturePack;

import java.io.*;
import java.util.*;

public class ScaleFeatureNormalizer extends FeatureNormalizer {
	HashMap<String, Double> max = new HashMap<>();
	HashMap<String, Double> min = new HashMap<>();

	public <E extends Serializable> ScaleFeatureNormalizer(String rangeFile,
			FeaturePack<E> fp) {
		loadRanges(rangeFile, fp);
	}

	public <E extends Serializable,G extends Serializable> ScaleFeatureNormalizer(
			ExampleGatherer<E,G> exampleGatherer) {
		for (FeaturePack<E> fp : exampleGatherer.getAllFeaturePacks())
			for (String ftrName : fp.getFeatureNames()) {
				if (!fp.featureIsSet(ftrName))
					continue;
				double ftrVal = fp.getFeature(ftrName);
				if (!max.containsKey(ftrName)) {
					max.put(ftrName, ftrVal);
					min.put(ftrName, ftrVal);
				} else {
					if (max.get(ftrName) < ftrVal)
						max.put(ftrName, ftrVal);
					if (min.get(ftrName) > ftrVal)
						min.put(ftrName, ftrVal);
				}
			}
	}

	@Override
	public double normalizeFeature(FeaturePack<?> fp, String ftrName) {
		if (!fp.featureIsSet(ftrName))
			return 0.0;
		if (!max.containsKey(ftrName))
			return fp.getFeature(ftrName);
		double rangeMax = max.get(ftrName);
		double rangeMin = min.get(ftrName);
		return rangeMax == rangeMin ? 0.0 : (fp.getFeature(ftrName) - rangeMin)
				/ (rangeMax - rangeMin) * 2f - 1;
	}

	private void loadRanges(String rangeFile, FeaturePack<?> fp) {
		Vector<String[]> tokensVect = new Vector<>();
		try {
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					new FileInputStream(rangeFile)));
			String line;
			while ((line = reader.readLine()) != null) {
				String[] tokens = line.split(" ");
				if (tokens.length == 3)
					tokensVect.add(tokens);
			}
			reader.close();
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		for (String[] tokens : tokensVect) {
			int featureId = Integer.parseInt(tokens[0]);
			String ftrName = fp.ftrIdToName(featureId);
			double rangeMin = Double.parseDouble(tokens[1]);
			double rangeMax = Double.parseDouble(tokens[2]);
			min.put(ftrName, rangeMin);
			max.put(ftrName, rangeMax);
			SmaphAnnotatorDebugger.out.printf(
					"Feature %d range: [%.3f, %.3f]%n", featureId, rangeMin,
					rangeMax);
		}
	}

	public void dump(String file, FeaturePack fp) throws IOException {
		BufferedWriter br = new BufferedWriter(new FileWriter(file));
		for (String ftrName : max.keySet()) {
			int ftrId = fp.ftrNameToId(ftrName);
			br.write(String.format("%d %f %f%n", ftrId, min.get(ftrName),
					max.get(ftrName)));
		}

		br.close();
	}
}