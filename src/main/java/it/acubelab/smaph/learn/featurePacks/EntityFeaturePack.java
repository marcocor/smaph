package it.acubelab.smaph.learn.featurePacks;

import it.acubelab.batframework.data.Tag;

import java.util.HashMap;


public class EntityFeaturePack extends FeaturePack<Tag> {
	private static final long serialVersionUID = 1L;

	public EntityFeaturePack(HashMap<String, Double> ftrs) {
		super(ftrs);
	}

	public EntityFeaturePack() {
		super(null);
	}

	public static String[] ftrNames = new String[] {
			"is_s1", // 1
			"is_s2",
			"is_s3",
			"is_s4",
			"is_s5",
			"s1_freq",
			"s1_rhoScore", //
			"s1_localCoherence", //
			"s1_lp",
			"s1_editDistance", // 10
			"s1_commonness", //
			"s1_avgRank",
			"s1_ambiguity",
			"s1_pageRank", //
			"s2_editDistanceTitle",
			"s2_rank",
			"s2_wikiWebTotal",
			"s2_webTotal",
			"s3_rank",
			"s3_wikiWebTotal", // 20
			"s3_editDistanceTitle",
			"s3_editDistanceNoPar",
			"s3_editDistanceBolds",
			"s3_capitalizedBolds",
			"s3_avgBoldsWords",
			"s5_rank",
			"s5_wikiWebTotal",
			"s5_editDistanceTitle",
			"s5_editDistanceNoPar",
			"s5_editDistanceBolds", // 30
			"s5_capitalizedBolds",
			"s5_avgBoldsWords",
			"s3_webTotal",
			"s2_editDistanceNoPar",
			"s2_editDistanceBolds",
			"s2_capitalizedBolds",
			"s2_avgBoldsWords",
			"s1_is_named_entity",
			"s2_is_named_entity",
			"s3_is_named_entity", //40
			"s6_is_named_entity",
			"s1_fragmentation",
			"s1_aggregation",
			"is_s6",
			"s6_webTotal",
			"s6_freq",
			"s6_avgRank",
			"s6_pageRank",
			"s6_min_rho",
			"s6_max_rho",       //50
			"s6_avg_rho",
			"s6_min_lp",
			"s6_max_lp",
			"s6_avg_lp",
			"s6_min_commonness",
			"s6_max_commonness",
			"s6_avg_commonness",
			"s6_min_ambig",
			"s6_max_ambig",
			"s6_avg_ambig",		//60
			"s6_min_min_bold_ed",
			"s6_max_min_bold_ed",
			"s6_avg_min_bold_ed",
			"s6_min_min_mention_ed",
			"s6_max_min_mention_ed",
			"s6_avg_min_mention_ed",
			"s6_min_mention_bold_overlap",
			"s6_max_mention_bold_overlap",
			"s6_avg_mention_bold_overlap", //69
	};

	@Override
	public String[] getFeatureNames() {
		return getFeatureNamesStatic();
	}

	private static String[] getFeatureNamesStatic() {
		return ftrNames;
	}

	@Override
	public void checkFeatures(HashMap<String, Double> features) {
		int sourceCount = 0;
		for (String ftrName : new String[] { "is_s1", "is_s2", "is_s3",
				"is_s4", "is_s5", "is_s6" }) {
			if (!features.containsKey(ftrName))
				throw new RuntimeException(
						"All entity sources must be set (one source to 1.0, all others to 0.0)");
			sourceCount += features.get(ftrName);
		}

		if (sourceCount != 1.0)
			throw new RuntimeException(
					"All sources must be set to 0.0, except from one source that must be set to 1.0");

		boolean found = false;
		for (String sourcePrefix : new String[] { "s1_", "s2_", "s3_", "s5_", "s6_" }) {
			int sourceFtrCount = 0;

			for (String ftrName : features.keySet())
				if (ftrName.startsWith(sourcePrefix))
					sourceFtrCount++;
			int baseFeatures = 6;
			if (sourcePrefix.equals("s1_"))
				found = sourceFtrCount == 12
						&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s2_"))
				found = sourceFtrCount == 9
						&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s3_"))
				found = sourceFtrCount == 9
						&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s4_"))
				found = sourceFtrCount == 0
						&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s5_"))
				found = sourceFtrCount == 8
						&& features.size() == sourceFtrCount + baseFeatures;
			if (sourcePrefix.equals("s6_"))
				found = sourceFtrCount == 26
						&& features.size() == sourceFtrCount + baseFeatures;

			if (found)
				return;
		}
		throw new RuntimeException("Incorrect number of features.");
	}

}
