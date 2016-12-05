/**
 *  Copyright 2014 Diego Ceccarelli
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

/**
 *  Copyright 2014 Diego Ceccarelli
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package it.cnr.isti.hpc.erd;

import java.io.File;
import java.util.Map;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.Serializer;

/**
 * @author Diego Ceccarelli <diego.ceccarelli@isti.cnr.it>
 * 
 *         Created on Mar 15, 2014
 */
public class WikipediaToFreebase {
	private static WikipediaToFreebase wikiToFreebase;

	private Map<String, String> map;
	private Map<String, String> labels;

	public static WikipediaToFreebase getDefault() {
		if (wikiToFreebase == null)
			wikiToFreebase = new WikipediaToFreebase("mapdb");
		return wikiToFreebase;
	}
	
	public WikipediaToFreebase(String folder) {
		File dir = new File(folder);
		File mapfile = new File(dir, "freebase.db");
		DB db = DBMaker.fileDB(mapfile).fileMmapEnable().readOnly().closeOnJvmShutdown().make();
		map = db.hashMap("index", Serializer.STRING, Serializer.STRING).createOrOpen();
		labels = db.hashMap("label", Serializer.STRING, Serializer.STRING).createOrOpen();
	}

	public String getLabel(String wikiid) {
		wikiid = wikiid.replaceAll(" ", "_");
		String freebase = map.get(wikiid);
		if (freebase == null)
			return null;

		String label = labels.get(freebase);
		return label;
	}

	public boolean hasEntity(String wikilabel) {
		wikilabel = wikilabel.replaceAll(" ", "_");
		return map.containsKey(wikilabel);
	}

	public String getFreebaseId(String wikilabel) {
		wikilabel = wikilabel.replaceAll(" ", "_");
		String freebase = map.get(wikilabel);
		return freebase;
	}

	public static void main(String[] args) {
		WikipediaToFreebase w2f = new WikipediaToFreebase("mapdb");
		System.out.println(w2f.getFreebaseId("Diego_Maradona"));
		System.out.println(w2f.getLabel("Diego_Maradona"));
		System.out.println(w2f.getFreebaseId("East Ridge High School (Minnesota)"));
		System.out.println(w2f.getLabel("East Ridge High School (Minnesota)"));
	}

}
