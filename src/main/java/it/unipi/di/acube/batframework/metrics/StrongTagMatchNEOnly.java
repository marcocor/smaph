package it.unipi.di.acube.batframework.metrics;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import it.cnr.isti.hpc.erd.WikipediaToFreebase;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.main.ERDDatasetFilter;

public class StrongTagMatchNEOnly extends StrongTagMatch {
	private WikipediaApiInterface api;
	private WikipediaToFreebase w2f;

	public StrongTagMatchNEOnly(WikipediaApiInterface api, WikipediaToFreebase w2f) {
		super(api);
		this.api = api;
		this.w2f = w2f;
	}

	@Override
	public List<HashSet<Tag>> preProcessOutput(List<HashSet<Tag>> computedOutput) {
		List<HashSet<Tag>> dereferencedEntities = super.preProcessOutput(computedOutput);

		List<HashSet<Tag>> filtered = new Vector<HashSet<Tag>>();
		for (HashSet<Tag> s : dereferencedEntities) {
			HashSet<Tag> newRes = new HashSet<Tag>();
			filtered.add(newRes);
			for (Tag t : s)
				try {
					if (ERDDatasetFilter.entityIsNE(api, w2f, t.getConcept()))
						newRes.add(t);
				} catch (IOException e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
		}
		return filtered;
	}

	@Override
	public List<HashSet<Tag>> preProcessGoldStandard(List<HashSet<Tag>> goldStandard) {
		return preProcessOutput(goldStandard);
	}

	@Override
	public String getName() {
		return "Strong tag match (NE only)";
	}

}
