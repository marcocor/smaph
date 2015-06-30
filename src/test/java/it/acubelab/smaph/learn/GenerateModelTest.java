package it.acubelab.smaph.learn;

import org.junit.Test;
import static org.junit.Assert.*;

public class GenerateModelTest {

	@Test
	public void testGetFtrListRepresentation() throws Exception {
		assertEquals("models/model_1-2_LB_0.600_5.00000000",
				GenerateModel.getModelFileNameBaseLB(new int[] { 1, 2 },
						0.6, 5.0));
		assertEquals(
				"models/model_1-2,7-9_LB_0.600_5.00000000",
				GenerateModel.getModelFileNameBaseLB(new int[] { 1, 2, 7,
						8, 9 }, 0.6, 5.0));
		assertEquals("models/model_1,6,9_LB_0.600_5.00000000",
				GenerateModel.getModelFileNameBaseLB(new int[] { 1, 6, 9 },
						0.6, 5.0));
		assertEquals("models/model_1,9-10_LB_0.600_5.00000000",
				GenerateModel.getModelFileNameBaseLB(
						new int[] { 1, 9, 10 }, 0.6, 5.0));
	}

}
