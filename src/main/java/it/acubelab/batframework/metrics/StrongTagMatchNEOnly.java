package it.acubelab.batframework.metrics;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import it.acubelab.batframework.data.Tag;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.main.ERDDatasetFilter;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

public class StrongTagMatchNEOnly extends StrongTagMatch {
	WikipediaApiInterface api;
	WikipediaToFreebase wikiToFreebase;
	public StrongTagMatchNEOnly(WikipediaApiInterface api, WikipediaToFreebase wikiToFreebase) {
		super(api);
		this.api = api;
		this.wikiToFreebase = wikiToFreebase;
	}

	@Override
	public List<HashSet<Tag>> preProcessOutput(List<HashSet<Tag>> computedOutput) {
		List<HashSet<Tag>> dereferencedEntities = super.preProcessOutput(computedOutput);
		
		List<HashSet<Tag>> filtered = new Vector<HashSet<Tag>>();
		for (HashSet<Tag> s : dereferencedEntities){
			HashSet<Tag> newRes = new HashSet<Tag>();
			filtered.add(newRes);
			for (Tag t: s)
				try {
					if (ERDDatasetFilter.EntityIsNE(api, wikiToFreebase,t.getConcept()))
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
