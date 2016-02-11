package it.unipi.di.acube.smaph.learn;

import it.unipi.di.acube.smaph.learn.GenerateProblemFiles;

import org.junit.Test;

import static org.junit.Assert.*;

public class GenerateProblemFilesTest {

	@Test
	public void testGetFtrListRepresentation() throws Exception {
		assertEquals("models/model_1-2_LB_5.00000000",
				GenerateProblemFiles.getModelFileNameBaseLB(new int[] { 1, 2 },
						5.0));
		assertEquals(
				"models/model_1-2,7-9_LB_5.00000000",
				GenerateProblemFiles.getModelFileNameBaseLB(new int[] { 1, 2, 7,
						8, 9 }, 5.0));
		assertEquals("models/model_1,6,9_LB_5.00000000",
				GenerateProblemFiles.getModelFileNameBaseLB(new int[] { 1, 6, 9 },
						5.0));
		assertEquals("models/model_1,9-10_LB_5.00000000",
				GenerateProblemFiles.getModelFileNameBaseLB(
						new int[] { 1, 9, 10 }, 5.0));
	}

}
