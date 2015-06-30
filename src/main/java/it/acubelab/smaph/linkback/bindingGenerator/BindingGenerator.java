package it.acubelab.smaph.linkback.bindingGenerator;

import it.acubelab.batframework.data.Annotation;
import it.acubelab.batframework.data.Tag;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.acubelab.smaph.QueryInformation;

import java.util.HashSet;
import java.util.Set;
import java.util.List;

public interface BindingGenerator {
	public List<HashSet<Annotation>> getBindings(String query, QueryInformation qi, Set<Tag> acceptedEntities, WikipediaApiInterface wikiApi);
}
