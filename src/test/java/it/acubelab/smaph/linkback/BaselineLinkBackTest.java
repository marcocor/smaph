package it.acubelab.smaph.linkback;

import static org.junit.Assert.*;
import it.acubelab.batframework.data.ScoredAnnotation;
import it.acubelab.batframework.data.Tag;
import it.acubelab.smaph.QueryInformation;

import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Vector;

import org.junit.Test;

public class BaselineLinkBackTest {

	@Test
	public void testLinkBack() {
		BaselineLinkBack lb = new BaselineLinkBack();
		{
			String query = "armstrong mon   lading";
			HashMap<String, Tag> boldsToEntities = new HashMap<>();
			boldsToEntities.put("moon landing", new Tag(111));
			boldsToEntities.put("wikipedia", new Tag(111));
			boldsToEntities.put("moon", new Tag(111));
			boldsToEntities.put("armstrong", new Tag(222));
			boldsToEntities.put("neil armstrong", new Tag(222));
			boldsToEntities.put("armstrang", new Tag(333));
			boldsToEntities.put("neil armstrang", new Tag(333));

			HashSet<Tag> acceptedEntities = new HashSet<Tag>();
			acceptedEntities.add(new Tag(111));
			acceptedEntities.add(new Tag(222));
			acceptedEntities.add(new Tag(333));

			QueryInformation qi = new QueryInformation();
			qi.boldToEntityS1 = boldsToEntities;
			
			HashSet<ScoredAnnotation> res = lb.linkBack(query,
					acceptedEntities, qi);
			Vector<ScoredAnnotation> resVect = new Vector<>(res);
			Collections.sort(resVect);
			assertEquals(2, res.size());

			assertEquals(222, resVect.get(0).getConcept());
			assertEquals(0, resVect.get(0).getPosition());
			assertEquals(9, resVect.get(0).getLength());

			assertEquals(111, resVect.get(1).getConcept());
			assertEquals(10, resVect.get(1).getPosition());
			assertEquals(12, resVect.get(1).getLength());
		}

		{
			String query = "armstrang trumpet";
			HashMap<String, Tag> boldsToEntities = new HashMap<>();
			boldsToEntities.put("moon landing", new Tag(111));
			boldsToEntities.put("wikipedia", new Tag(111));
			boldsToEntities.put("moon", new Tag(111));
			boldsToEntities.put("armstrong", new Tag(222));
			boldsToEntities.put("neil armstrong", new Tag(222));

			HashSet<Tag> acceptedEntities = new HashSet<Tag>();
			acceptedEntities.add(new Tag(111));
			acceptedEntities.add(new Tag(222));

			QueryInformation qi = new QueryInformation();
			qi.boldToEntityS1 = boldsToEntities;

			HashSet<ScoredAnnotation> res = lb.linkBack(query,
					acceptedEntities, qi);
			Vector<ScoredAnnotation> resVect = new Vector<>(res);
			Collections.sort(resVect);
			assertEquals(2, res.size());

			assertEquals(222, resVect.get(0).getConcept());
			assertEquals(0, resVect.get(0).getPosition());
			assertEquals(9, resVect.get(0).getLength());
			
			assertEquals(111, resVect.get(1).getConcept());
			assertEquals(10, resVect.get(1).getPosition());
			assertEquals(7, resVect.get(1).getLength());
		}
	}
}
