package it.unipi.di.acube.smaph.linkback;

import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.smaph.QueryInformation;

import java.util.HashSet;

public interface LinkBack {
	public HashSet<ScoredAnnotation> linkBack(String query,
			HashSet<Tag> acceptedEntities, QueryInformation qi);
}
