package it.unipi.di.acube.smaph.wikiAnchors;

import static org.junit.Assert.*;
import it.unipi.di.acube.batframework.utils.Pair;
import it.unipi.di.acube.smaph.wikiAnchors.EntityToAnchors;

import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.util.List;

import org.junit.Test;

public class EntityToAnchorsTest {
	public static final String DATASET_FILENAME = "e2a.dataset";
	public static final int BERLUSCONI_ID = 26909;

	@Test
	public void testGetAnchors() throws Exception {
		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
				DATASET_FILENAME));
		EntityToAnchors e2a = (EntityToAnchors) ois.readObject();
		ois.close();

		List<Pair<String, Integer>> berlusconiAnchors = e2a
				.getAnchors(BERLUSCONI_ID);
		assertTrue(berlusconiAnchors.size() > 20);

		{
			int countFound = 0;
			String anchor = "silvio berlusconi";
			for (Pair<String, Integer> p : berlusconiAnchors)
				if (p.first.equals(anchor))
					countFound++;
			assertTrue(countFound == 1);
		}

		{
			int countFound = 0;
			String anchor = "berlusconi";
			for (Pair<String, Integer> p : berlusconiAnchors)
				if (p.first.equals(anchor))
					countFound++;
			assertTrue(countFound == 1);
		}

		{
			int countFound = 0;
			String anchor = "obama";
			for (Pair<String, Integer> p : berlusconiAnchors)
				if (p.first.equals(anchor))
					countFound++;
			assertTrue(countFound == 0);
		}

	}

}
