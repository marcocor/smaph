package it.unipi.di.acube.smaph.linkback.bindingGenerator;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.QueryInformation;

import java.util.HashSet;
import java.util.Set;
import java.util.List;

public interface BindingGenerator {
	public List<HashSet<Annotation>> getBindings(String query, QueryInformation qi, Set<Tag> acceptedEntities, WikipediaApiInterface wikiApi);
}
