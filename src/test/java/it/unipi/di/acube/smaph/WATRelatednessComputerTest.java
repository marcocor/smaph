package it.unipi.di.acube.smaph;

import org.junit.Test;
import static org.junit.Assert.*;

public class WATRelatednessComputerTest {

	@Test
	public void testGetLp() throws Exception {
		assertTrue(WATRelatednessComputer.getLp("silvio berlusconi") > 0.1);
		assertTrue(WATRelatednessComputer.getLp("berlusconi") > 0.05);
		assertTrue(WATRelatednessComputer.getLp("berlusconi") < 0.5);
		assertTrue(WATRelatednessComputer.getLp("joseph berlusconi") == 0.0);
		assertTrue(WATRelatednessComputer.getLp("sdfliksjgfoisdjgoija") == 0.0);
		assertTrue(WATRelatednessComputer.getLp("marco sdfosdfjoiadasoi") == 0.0);
	}
}
