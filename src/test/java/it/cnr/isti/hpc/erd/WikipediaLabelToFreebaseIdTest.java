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

import static org.junit.Assert.assertEquals;

import org.junit.Test;

/**
 * @author Diego Ceccarelli <diego.ceccarelli@isti.cnr.it>
 * 
 *         Created on Mar 15, 2014
 */
public class WikipediaLabelToFreebaseIdTest {

	@Test
	public void test() {
		WikipediaLabelToFreebaseRecord converter = new WikipediaLabelToFreebaseRecord();

		assertEquals(
				"/wikipedia/en_title/101_Dalmatians_(1996_film)",
				converter
						.convert("/wikipedia/en_title/101_Dalmatians_$00281996_film$0029"));
		assertEquals("/wikipedia/en_title/C._Z._Guest",
				converter.convert("/wikipedia/en_title/C$002E_Z$002E_Guest"));
		assertEquals("/wikipedia/en_title/Takashi_Kondō",
				converter.convert("/wikipedia/en_title/Takashi_Kond$014D"));

	}

	@Test
	public void parse() {
		WikipediaLabelToFreebaseRecord record = WikipediaLabelToFreebaseRecord
				.parse("/m/02qlpsg\t\"Ferris Jacobs, Jr.\"@en\t\"/wikipedia/en_title/Ferris_Jacobs$002C_Jr$002E\"");
		assertEquals("/m/02qlpsg", record.getFreebaseId());
		assertEquals("Ferris Jacobs, Jr.", record.getLabel());
		assertEquals("/wikipedia/en_title/Ferris_Jacobs$002C_Jr$002E",
				record.getWikipediaLabel());
		assertEquals("Ferris_Jacobs,_Jr.", record.getCleanWikipediaLabel());
	}
}
