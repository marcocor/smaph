/**
 *  Copyright 2014 Marco Cornolti
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

package it.unipi.di.acube.smaph.main;

import it.unipi.di.acube.batframework.data.*;
import it.unipi.di.acube.batframework.problems.A2WDataset;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.SmaphAnnotatorDebugger;
import it.cnr.isti.hpc.erd.WikipediaToFreebase;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

public class ERDDatasetFilter implements A2WDataset {
	private List<HashSet<Tag>> ERDTopics;
	private A2WDataset ds;
	private List<HashSet<Mention>> ERDMentions;
	private List<HashSet<Annotation>> ERDAnnotations;

	public ERDDatasetFilter(A2WDataset ds, WikipediaApiInterface wikiApi,
			WikipediaToFreebase wikiToFreebase) throws IOException {
		this.ds = ds;
		FilterERDTopics(ds.getC2WGoldStandardList(), wikiApi, wikiToFreebase);
		FilterERDAnnotations(ds.getA2WGoldStandardList(), wikiApi,
				wikiToFreebase);
	}

	public static boolean EntityIsNE(WikipediaApiInterface wikiApi,
			WikipediaToFreebase wikiToFreebase, int wid) throws IOException {
		String title = wikiApi.getTitlebyId(wid);
		return EntityIsNE(wikiApi, wikiToFreebase, title);
	}
	public static boolean EntityIsNE(WikipediaApiInterface wikiApi,
			WikipediaToFreebase wikiToFreebase, String title) throws IOException {
		return title != null && wikiToFreebase.hasEntity(title);
	}

	private void FilterERDAnnotations(
			List<HashSet<Annotation>> a2wGoldStandardList,
			WikipediaApiInterface wikiApi, WikipediaToFreebase wikiToFreebase)
			throws IOException {
		ERDMentions = new Vector<HashSet<Mention>>();
		ERDAnnotations = new Vector<HashSet<Annotation>>();
		for (HashSet<Annotation> anns : a2wGoldStandardList) {
			HashSet<Annotation> filteredAnns = new HashSet<>();
			ERDAnnotations.add(filteredAnns);
			HashSet<Mention> filteredMentions = new HashSet<>();
			ERDMentions.add(filteredMentions);
			for (Annotation ann : anns) {
				String title = wikiApi.getTitlebyId(ann.getConcept());
				if (!EntityIsNE(wikiApi, wikiToFreebase, ann.getConcept())) {
					SmaphAnnotatorDebugger.out.printf("Discarding title=%s%n", title);
					continue;
				}
				SmaphAnnotatorDebugger.out.printf("Including title=%s%n", title);
				filteredAnns.add(ann);
				filteredMentions.add(new Mention(ann.getPosition(), ann
						.getLength()));
			}

		}

	}

	private void FilterERDTopics(List<HashSet<Tag>> c2wGoldStandardList,
			WikipediaApiInterface wikiApi, WikipediaToFreebase wikiToFreebase)
			throws IOException {
		ERDTopics = new Vector<>();
		for (HashSet<Tag> tags : c2wGoldStandardList) {
			HashSet<Tag> erdTags = new HashSet<>();
			ERDTopics.add(erdTags);
			for (Tag t : tags) {
				String title = wikiApi.getTitlebyId(t.getConcept());
				if (!EntityIsNE(wikiApi, wikiToFreebase, t.getConcept())) {
					SmaphAnnotatorDebugger.out.printf("Discarding title=%s%n", title);
					continue;
				}
				SmaphAnnotatorDebugger.out.printf("Including title=%s%n", title);
				erdTags.add(new Tag(t.getConcept()));
			}

		}
	}

	@Override
	public int getSize() {
		return ds.getSize();
	}

	@Override
	public String getName() {
		return ds.getName() + " (ERD)";
	}

	@Override
	public List<String> getTextInstanceList() {
		return ds.getTextInstanceList();
	}

	@Override
	public int getTagsCount() {
		int count = 0;
		for (HashSet<Annotation> s : ERDAnnotations)
			count += s.size();
		return count;
	}

	@Override
	public List<HashSet<Tag>> getC2WGoldStandardList() {
		return ERDTopics;
	}

	@Override
	public List<HashSet<Mention>> getMentionsInstanceList() {
		return ERDMentions;
	}

	@Override
	public List<HashSet<Annotation>> getD2WGoldStandardList() {
		return getA2WGoldStandardList();
	}

	@Override
	public List<HashSet<Annotation>> getA2WGoldStandardList() {
		return ERDAnnotations;
	}

}
