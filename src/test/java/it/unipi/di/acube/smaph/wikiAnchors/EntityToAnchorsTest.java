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
		Runtime runtime = Runtime.getRuntime();

		System.out.println("Used Memory (MiB):"
				+ (runtime.totalMemory() - runtime.freeMemory())
				/ (1024 * 1024));

		ObjectInputStream ois = new ObjectInputStream(new FileInputStream(
				DATASET_FILENAME));
		EntityToAnchors e2a = (EntityToAnchors) ois.readObject();
		ois.close();

		System.out.println("Used Memory (MiB):"
				+ (runtime.totalMemory() - runtime.freeMemory())
				/ (1024 * 1024));

		List<Pair<String, Integer>> berlusconiAnchors = e2a
				.getAnchors(BERLUSCONI_ID);
		System.err.printf("%d distinct anchors point to %d.%n",
				berlusconiAnchors.size(), BERLUSCONI_ID);
		assertTrue(berlusconiAnchors.size() > 20);

		{
			int countFound = 0;
			String anchor = "silvio berlusconi";
			for (Pair<String, Integer> p : berlusconiAnchors)
				if (p.first.equals(anchor)) {
					System.err.printf("Anchor %s points to %d for %d times.%n",
							p.first, BERLUSCONI_ID, p.second);
					countFound++;
				}
			assertTrue(countFound == 1);
		}

		{
			int countFound = 0;
			String anchor = "berlusconi";
			for (Pair<String, Integer> p : berlusconiAnchors)
				if (p.first.equals(anchor)) {
					System.err.printf("Anchor %s points to %d for %d times.%n",
							p.first, BERLUSCONI_ID, p.second);
					countFound++;
				}
			assertTrue(countFound == 1);
		}

		{
			int countFound = 0;
			String anchor = "obama";
			for (Pair<String, Integer> p : berlusconiAnchors)
				if (p.first.equals(anchor)) {
					System.err.printf("Anchor %s points to %d for %d times.%n",
							p.first, BERLUSCONI_ID, p.second);
					countFound++;
				}
			assertTrue(countFound == 0);
		}

	}

}
