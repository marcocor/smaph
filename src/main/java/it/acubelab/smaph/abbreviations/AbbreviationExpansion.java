package it.acubelab.smaph.abbreviations;

import java.util.List;

public interface AbbreviationExpansion {

	/**
	 * @param abbrev
	 * @param exp
	 * @return true iff abbrev is an abbreviation of exp. 
	 */
	public boolean isAbbreviationOf(String abbrev, String exp) throws Exception;
	
	/**
	 * @param str
	 * @return All the abbreviations of str, or null in case str cannot be expanded.
	 */
	public List<String> expand(String str) throws Exception;
	
}
