package it.unipi.di.acube.smaph.linkback;

import it.unipi.di.acube.batframework.data.Annotation;
import it.unipi.di.acube.batframework.data.Tag;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.QueryInformation;
import it.unipi.di.acube.smaph.SmaphUtils;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.BindingGenerator;
import it.unipi.di.acube.smaph.linkback.bindingGenerator.DefaultBindingGenerator;

import java.util.*;

import static org.junit.Assert.*;

import org.junit.Test;

public class SvmCollectiveLinkBackTest {

	@Test
	public void testGetAllBindings() throws Exception {

		BindingGenerator bg = new DefaultBindingGenerator();

		String query = "metronome satting of allegro";
		HashMap<String, Tag> entityToTexts = new HashMap<>();

		Tag tag1 = new Tag(88771); // Metronome
		entityToTexts.put("metronome", tag1);
		entityToTexts.put("metronomes", tag1);
		entityToTexts.put("Set", tag1);
		entityToTexts.put("Metronome", tag1);
		entityToTexts.put("allegro", tag1);

		Tag tag2 = new Tag(30967); // Tempo
		entityToTexts.put("allegro", tag2);
		entityToTexts.put("Metronome", tag2);
		entityToTexts.put("metronome", tag2);
		entityToTexts.put("of allegro", tag2);

		Tag tag3 = new Tag(3564116); // Setting (narrativity)
		entityToTexts.put("set",tag3);
		entityToTexts.put("setting",tag3);
		
		QueryInformation qi = new QueryInformation();
		qi.boldToEntityS1 = entityToTexts;
		qi.entityToBoldS2S3 = new HashMap<Tag,String[]>();
		List<HashSet<Annotation>> possibleBindings = bg.getBindings(query,qi,new HashSet<Tag>(entityToTexts.values()), new WikipediaApiInterface(null, null));

		for (HashSet<Annotation> binding : possibleBindings) {
			for (Annotation ann : binding)
				System.out.printf(
						"[%s -> %d ] ",
						query.substring(ann.getPosition(), ann.getPosition()
								+ ann.getLength()), ann.getConcept());
			System.out.println();
		}
		assertEquals(24, possibleBindings.size());
		HashSet<Integer> verified = new HashSet<>();

		for (HashSet<Annotation> binding : possibleBindings) {
			if (binding.isEmpty())
				verified.add(0);
			if (binding.size() == 1
					&& binding.contains(new Annotation(0, 9, 88771)))
				verified.add(1);
			if (binding.size() == 1
					&& binding.contains(new Annotation(0, 9, 30967)))
				verified.add(2);
			if (binding.size() == 1
					&& binding.contains(new Annotation(0, 9, 88771)))
				verified.add(3);
			if (binding.size() == 1
					&& binding.contains(new Annotation(21, 7, 88771)))
				verified.add(4);
			if (binding.size() == 1
					&& binding.contains(new Annotation(21, 7, 30967)))
				verified.add(5);
			if (binding.size() == 2
					&& binding.contains(new Annotation(21, 7, 30967))
					&& binding.contains(new Annotation(10, 7, 3564116)))
				verified.add(6);
			if (binding.size() == 2
					&& binding.contains(new Annotation(21, 7, 88771))
					&& binding.contains(new Annotation(10, 7, 3564116)))
				verified.add(7);
			if (binding.size() == 3
					&& binding.contains(new Annotation(0, 9, 88771))
					&& binding.contains(new Annotation(21, 7, 88771))
					&& binding.contains(new Annotation(10, 7, 3564116)))
				verified.add(8);
			if (binding.size() == 3
					&& binding.contains(new Annotation(0, 9, 30967))
					&& binding.contains(new Annotation(21, 7, 88771))
					&& binding.contains(new Annotation(10, 7, 3564116)))
				verified.add(9);
			if (binding.size() == 3
					&& binding.contains(new Annotation(0, 9, 88771))
					&& binding.contains(new Annotation(21, 7, 30967))
					&& binding.contains(new Annotation(10, 7, 3564116)))
				verified.add(10);
			if (binding.size() == 3
					&& binding.contains(new Annotation(0, 9, 30967))
					&& binding.contains(new Annotation(21, 7, 30967))
					&& binding.contains(new Annotation(10, 7, 3564116)))
				verified.add(11);
		}

		assertEquals(true, verified.contains(0));
		assertEquals(true, verified.contains(1));
		assertEquals(true, verified.contains(2));
		assertEquals(true, verified.contains(3));
		assertEquals(true, verified.contains(4));
		assertEquals(true, verified.contains(5));
		assertEquals(true, verified.contains(6));
		assertEquals(true, verified.contains(7));
		assertEquals(true, verified.contains(8));
		assertEquals(true, verified.contains(9));
		assertEquals(true, verified.contains(10));
		assertEquals(true, verified.contains(11));

	}

}
