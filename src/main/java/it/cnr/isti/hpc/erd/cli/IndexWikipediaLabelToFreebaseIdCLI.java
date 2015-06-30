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
package it.cnr.isti.hpc.erd.cli;

import it.cnr.isti.hpc.cli.AbstractCommandLineInterface;
import it.cnr.isti.hpc.erd.WikipediaLabelToFreebaseRecord;
import it.cnr.isti.hpc.io.reader.RecordReader;
import it.cnr.isti.hpc.log.ProgressLogger;
import it.cnr.isti.hpc.mapdb.MapDB;

import java.io.File;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author Diego Ceccarelli <diego.ceccarelli@isti.cnr.it>
 * 
 *         Created on Mar 15, 2014
 */
public class IndexWikipediaLabelToFreebaseIdCLI extends
		AbstractCommandLineInterface {

	private static final Logger logger = LoggerFactory
			.getLogger(IndexWikipediaLabelToFreebaseIdCLI.class);

	private static String[] params = new String[] { "input", "dbdir" };
	private static String usage = "java -cp $jar it.cnr.isti.hpc.erd.cli.IndexWikipediaLabelToFreebaseIdCLI -input entity.tsv -dbdir index directory";

	public IndexWikipediaLabelToFreebaseIdCLI(String[] args) {
		super(args, params, usage);

	}

	public static void main(String[] args) {
		IndexWikipediaLabelToFreebaseIdCLI cli = new IndexWikipediaLabelToFreebaseIdCLI(
				args);
		RecordReader<WikipediaLabelToFreebaseRecord> reader = new RecordReader<WikipediaLabelToFreebaseRecord>(
				cli.getInput(), new WikipediaLabelToFreebaseRecord.Parser());
		File f = new File(cli.getParam("dbdir"));
		if (!f.exists()) {
			f.mkdirs();
		}
		File dbfile = new File(f, "mapdb");
		MapDB db = new MapDB(dbfile, false);

		ProgressLogger pl = new ProgressLogger("indexed {} records", 100000);
		Map<String, String> map = db.getCollection("index");
		Map<String, String> labels = db.getCollection("label");
		for (WikipediaLabelToFreebaseRecord record : reader) {
			map.put(record.getCleanWikipediaLabel(), record.getFreebaseId());
			labels.put(record.getFreebaseId(), record.getLabel());
			pl.up();
		}
		db.commit();
		db.close();

		logger.info("file indexed, index in {}", cli.getParam("dbdir"));

	}

}
