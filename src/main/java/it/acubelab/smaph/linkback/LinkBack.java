package it.acubelab.smaph.linkback;

import it.acubelab.batframework.data.ScoredAnnotation;
import it.acubelab.batframework.data.Tag;
import it.acubelab.smaph.QueryInformation;

import java.util.HashSet;

public interface LinkBack {
	public HashSet<ScoredAnnotation> linkBack(String query,
			HashSet<Tag> acceptedEntities, QueryInformation qi);
}
