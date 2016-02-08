package it.unipi.di.acube.smaph.linkback.bindingGenerator;

import it.unipi.di.acube.batframework.data.Annotation;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class AnnotationRegressorBindingGeneratorTest {

	@Test
	public void testGetBindingsAnn() throws Exception {
		Set<Annotation> anns = new HashSet<>();
		anns.add(new Annotation(0, 3, 111));
		anns.add(new Annotation(4, 6, 444));
		anns.add(new Annotation(0, 5, 333));
		anns.add(new Annotation(0, 3, 222));
		
		List<HashSet<Annotation>> res = AnnotationRegressorBindingGenerator.getBindingsAnn(anns);
		
		assertEquals(7, res.size());
		assertTrue(res.contains(new HashSet<Annotation>(Arrays.asList())));
		assertTrue(res.contains(new HashSet<Annotation>(Arrays.asList(new Annotation(0, 3, 111)))));
		assertTrue(res.contains(new HashSet<Annotation>(Arrays.asList(new Annotation(0, 3, 111), new Annotation(4, 6, 444)))));
		assertTrue(res.contains(new HashSet<Annotation>(Arrays.asList(new Annotation(0, 3, 222)))));
		assertTrue(res.contains(new HashSet<Annotation>(Arrays.asList(new Annotation(0, 3, 222), new Annotation(4, 6, 444)))));
		assertTrue(res.contains(new HashSet<Annotation>(Arrays.asList(new Annotation(0, 5, 333)))));
		assertTrue(res.contains(new HashSet<Annotation>(Arrays.asList(new Annotation(4, 6, 444)))));
	}

}
