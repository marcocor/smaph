package it.unipi.di.acube.smaph.learn;

import it.unipi.di.acube.smaph.learn.GenerateModel;

import org.junit.Test;

import static org.junit.Assert.*;

public class GenerateModelTest {

	@Test
	public void testGetFtrListRepresentation() throws Exception {
		assertEquals("models/model_1-2_LB_5.00000000",
				GenerateModel.getModelFileNameBaseLB(new int[] { 1, 2 },
						5.0));
		assertEquals(
				"models/model_1-2,7-9_LB_5.00000000",
				GenerateModel.getModelFileNameBaseLB(new int[] { 1, 2, 7,
						8, 9 }, 5.0));
		assertEquals("models/model_1,6,9_LB_5.00000000",
				GenerateModel.getModelFileNameBaseLB(new int[] { 1, 6, 9 },
						5.0));
		assertEquals("models/model_1,9-10_LB_5.00000000",
				GenerateModel.getModelFileNameBaseLB(
						new int[] { 1, 9, 10 }, 5.0));
	}

}
