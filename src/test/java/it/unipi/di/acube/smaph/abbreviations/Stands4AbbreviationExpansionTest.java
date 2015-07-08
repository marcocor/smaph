package it.unipi.di.acube.smaph.abbreviations;

import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.abbreviations.Stands4AbbreviationExpansion;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;


public class Stands4AbbreviationExpansionTest {
	Stands4AbbreviationExpansion ae;
    @Before
    public void setUp() {
    	SmaphConfig.setConfigFile("smaph-config.xml");
    	String tokenId = SmaphConfig.getDefaultStands4TokenId();
    	String uid = SmaphConfig.getDefaultStands4UserId();
    	ae = new Stands4AbbreviationExpansion(tokenId, uid);
    }

	@Test
	public void testExpand() throws Exception {
		for (String s : ae.expand("cern."))
			System.out.println("["+s+"] l="+s.length());
		assertEquals(true, ae.expand("dept").contains("Department"));
	}

	@Test
	public void testIsAbbreviationOf() throws Exception {
		assertEquals(true, ae.isAbbreviationOf("dept", "Department"));
	}

}
