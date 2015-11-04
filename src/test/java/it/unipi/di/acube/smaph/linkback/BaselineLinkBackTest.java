package it.unipi.di.acube.smaph.linkback;

import static org.junit.Assert.*;
import it.unipi.di.acube.batframework.data.ScoredAnnotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.linkback.BaselineLinkBack;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

import org.junit.Test;

public class BaselineLinkBackTest {

	@Test
	public void testLinkBack() {
		BaselineLinkBack lb = new BaselineLinkBack();
		{
			String query = "armstrong mon   lading";
			
			HashMap<Tag, List<String>> boldsToEntities = new HashMap<>();
			boldsToEntities.put(new Tag(111), Arrays.asList("moon landing", "wikipedia", "moon"));
			boldsToEntities.put(new Tag(222), Arrays.asList("armstrong", "neil armstrong"));
			boldsToEntities.put(new Tag(333), Arrays.asList("armstrang", "neil armstrang"));

			HashSet<Tag> acceptedEntities = new HashSet<Tag>(boldsToEntities.keySet());

			QueryInformation qi = new QueryInformation();
			qi.entityToBoldsS6 = boldsToEntities;
			
			HashSet<ScoredAnnotation> res = lb.linkBack(query, acceptedEntities, qi);
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
			HashMap<Tag, List<String>> boldsToEntities = new HashMap<>();
			boldsToEntities.put(new Tag(111), Arrays.asList("moon landing", "wikipedia", "moon"));
			boldsToEntities.put(new Tag(222), Arrays.asList("armstrong", "neil armstrong"));

			HashSet<Tag> acceptedEntities = new HashSet<Tag>(boldsToEntities.keySet());

			QueryInformation qi = new QueryInformation();
			qi.entityToBoldsS6 = boldsToEntities;

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
