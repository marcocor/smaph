package it.acubelab.smaph;

import it.acubelab.batframework.data.Tag;

import java.util.HashMap;
import java.util.List;

public class QueryInformation {
	public HashMap<Tag, String[]> entityToBoldS2S3;
	public HashMap<String, Tag> boldToEntityS1;
	public HashMap<Tag, List<HashMap<String, Double>>> entityToFtrVects;
	public HashMap<Tag, List<String>> tagToBoldsS6;

}
