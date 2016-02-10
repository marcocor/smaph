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

import it.unipi.di.acube.BingInterface;
import it.unipi.di.acube.batframework.data.MultipleAnnotation;
import it.unipi.di.acube.batframework.problems.CandidatesSpotter;
import it.unipi.di.acube.batframework.problems.Sa2WSystem;
import it.unipi.di.acube.batframework.systemPlugins.WATAnnotator;
import it.unipi.di.acube.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.smaph.EntityToVect;
import it.unipi.di.acube.smaph.SmaphAnnotator;
import it.unipi.di.acube.smaph.SmaphConfig;
import it.unipi.di.acube.smaph.WATRelatednessComputer;
import it.unipi.di.acube.smaph.learn.GenerateModel;
import it.unipi.di.acube.smaph.learn.GenerateTrainingAndTest;
import it.unipi.di.acube.smaph.learn.featurePacks.EntityFeaturePack;
import it.unipi.di.acube.smaph.learn.models.entityfilters.EntityFilter;
import it.unipi.di.acube.smaph.learn.models.entityfilters.LibSvmEntityFilter;
import it.unipi.di.acube.smaph.learn.models.entityfilters.NoEntityFilter;
import it.unipi.di.acube.smaph.learn.normalizer.ZScoreFeatureNormalizer;
import it.unipi.di.acube.smaph.linkback.DummyLinkBack;
import it.unipi.di.acube.smaph.snippetannotationfilters.FrequencyAnnotationFilter;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Vector;

public class Annotator {
	public static final String SMAPH_PARAMS_FORMAT = "BING-auxAnnotator=%s&minLp=%.5f&sortBy=%s&method=%s&relatedness=%s&epsilon=%.5f&spotFilter=%s&spotFilterThreshold=%f&entityFilter=%s&svmEntityFilterModelBase=%s&emptyQueryFilter=%s&svmEmptyQueryFilterModelBase=%s&entitySources=%s";
	private static WikipediaApiInterface wikiApi = null;
	private static WikipediaToFreebase wikiToFreeb = null;
	private static WATAnnotator wat = null;
	private static LibSvmEntityFilter libSvmEntityFilter = null;
	private String bingKey;
	private static SmaphAnnotator collective = null;

	public Annotator() {
		SmaphConfig.setConfigFile("smaph-config.xml");
		bingKey = SmaphConfig.getDefaultBingKey();
		String bingCache = SmaphConfig.getDefaultBingCache();
		try {
			EntityToVect.initialize();
			WATAnnotator.setCache("wikisense.cache");
			if (wikiApi == null)
				wikiApi = new WikipediaApiInterface("wid.cache",
						"redirect.cache");
			if (bingCache != null)
				BingInterface.setCache(bingCache);
		} catch (Exception e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
		if (wikiToFreeb == null)
			wikiToFreeb = new WikipediaToFreebase("mapdb");
	}

	/**
	 * Annotate a query with an annotator 'as is', picking the candidate with
	 * highest commonness.
	 * 
	 * @param query
	 *            the query
	 * @param textID
	 *            an unique id for the query.
	 * @param spotter
	 *            the annotator that spots candidates.
	 * @return the list of annotations.
	 */
	public List<Annotation> annotateCommonness(String query, String textID,
			CandidatesSpotter spotter) {
		List<Annotation> annotations = new ArrayList<Annotation>();
		HashSet<MultipleAnnotation> mas = spotter.getSpottedCandidates(query);
		mas = deleteOverlappingAnnotations(mas);
		for (MultipleAnnotation ma : mas) {
			Annotation a = new Annotation();
			a.setQid(textID);
			a.setInterpretationSet(0);
			int wid = ma.getCandidates()[0];
			String title = null;
			try {
				title = wikiApi.getTitlebyId(wid);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			String mid = wikiToFreeb.getFreebaseId(title);
			if (mid == null)
				continue;
			a.setPrimaryId(mid);
			a.setMentionText(query.substring(ma.getPosition(), ma.getPosition()
					+ ma.getLength()));
			a.setScore(1.0f);
			annotations.add(a);
		}
		return annotations;
	}

	/**
	 * Annotate a query with an annotator 'as is'.
	 * 
	 * @param query
	 *            the query
	 * @param textID
	 *            an unique id for the query.
	 * @param annotator
	 *            the annotator to tag the query.
	 * @return the list of annotations.
	 */
	public List<Annotation> annotatePure(String query, String textID,
			Sa2WSystem annotator) {
		List<Annotation> annotations = new ArrayList<Annotation>();
		HashSet<it.unipi.di.acube.batframework.data.ScoredAnnotation> res = annotator
				.solveSa2W(query);
		System.out.printf(annotator.getName() + " found %d annotations.%n",
				res.size());
		HashMap<Annotation, String> annToTitle = new HashMap<>();
		for (it.unipi.di.acube.batframework.data.ScoredAnnotation ann : res) {
			Annotation a = new Annotation();
			a.setQid(textID);
			a.setInterpretationSet(0);
			int wid = ann.getConcept();
			String title = null;
			try {
				title = wikiApi.getTitlebyId(wid);
			} catch (Exception e) {
				e.printStackTrace();
				throw new RuntimeException(e);
			}
			String mid = wikiToFreeb.getFreebaseId(title);
			annToTitle.put(a, title);
			System.out.printf("Annotation: wid=%d mid=%s title=%s%n", wid, mid,
					title);
			if (mid == null)
				continue;
			a.setPrimaryId(mid);
			a.setMentionText("null");
			/*
			 * a.setMentionText(query.substring(ann.getPosition(),
			 * ann.getPosition() + ann.getLength()));
			 */
			a.setScore(ann.getScore());
			annotations.add(a);
		}
		return annotations;
	}

	/**
	 * Handler for an annotation call. Depending on the runId, it calls
	 * different methods to annotate a query.
	 * 
	 * @param runId
	 *            the runId from which the configuation is picked.
	 * @param query
	 *            the query.
	 * @param textID
	 *            an unique id for the query.
	 * @return the annotations of the query.
	 */
	public List<Annotation> annotate(String runId, String textID, String query) {
		if (runId.startsWith("miao")) {
			String modelFileEF = GenerateModel.getModelFileNameBaseEF(
					new int[] {1, 2, 3, 6, 7, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 33, 34, 35, 36, 37},
					3.8, 5.2, 0.01, 100.0)
					+ "_" + "ANW-erd";

			runId = String.format(SMAPH_PARAMS_FORMAT, "wikisense", 0.0,
					"COMMONNESS", "base", "jaccard", 0.6f,
					"Frequency", 0.06, "SvmEntityFilter",
					modelFileEF, "NoEmptyQueryFilter", "null",
					"NormalSearch+WikiSearch10+AnnotateSnippet25");
		}
		if (runId.equals("___reset_models")) {
			System.out.println("Invalidating SVM models...");
			libSvmEntityFilter = null;
			return new Vector<>();
		}
		if (runId.equals("___flush_cache")) {
			System.out.println("Flushing cache...");
			try {
				BingInterface.flush();
				wikiApi.flush();
				WATRelatednessComputer.flush();
				WATAnnotator.flush();
			} catch (FileNotFoundException e) {
				e.printStackTrace();
			} catch (IOException e) {
				e.printStackTrace();
			}
			return new Vector<>();
		}

		String auxAnnotator = "";
		String minLp = "";
		String sortBy = "";
		String method = "";
		String relatedness = "";
		String spotFilterName = "";
		String entityFilterName = "";
		String svmEntityFilterModelBase = "";
		float spotFilterThreshold = -1;
		String emptyQueryFilterName = "";
		String svmEmptyQueryFilterModelBase = "";
		Vector<String> entitySources = new Vector<>();
		boolean includeSourceAnnotator = false;
		boolean includeSourceNormalSearch = false;
		boolean includeSourceWikiSearch = false;
		int wikiSearchPages = 0;
		boolean includeSourceAnnotatorCandidates = false;
		int topKannotatorCandidates = 0;
		boolean includeSourceAnnotateSnippet = false;
		int topKSnippetsToAnnotate = 0;

		{
			double[][] paramsToTest = new double[][] {
					{0.010, 100}
					};
			double[][] weightsToTest = new double[][] {
					/*{ 3.8, 5.2 },*/
					{ 3.8, 4.5 },
					{ 3.8, 4.9 },
					{ 3.8, 5.2 },
					{ 3.8, 5.6 },
					{ 3.8, 5.9 },
			};
			int[][] featuresSetsToTest = new int[][] {
					{2,3,15, 16, 17, 18, 19, 20, 21, 22, 23, 24, 25, 33, 34, 35, 36, 37,44,45,46,47,48,49,50,51,52,53,54,55,56,57,58,59,60,61,62,63,64,65,66,67,68,69}
			};
			if (runId.startsWith("ftr_test_")) {
				String sources = runId.substring("ftr_test_".length(),
						"ftr_test_XXXX".length());
				int idftr = Integer.parseInt(runId.substring(
						"ftr_test_XXXX_".length(),
						"ftr_test_XXXX_XXX".length()));
				int idWeight = Integer.parseInt(runId.substring(
						"ftr_test_XXXX_XXX_".length(),
						"ftr_test_XXXX_XXX_XXX".length()));
				int idParam = Integer.parseInt(runId.substring(
						"ftr_test_XXXX_XXX_XXX_XXXX_".length(),
						"ftr_test_XXXX_XXX_XXX_XXXX_XXX".length()));

				String sourcesString = "";
				for (char c : sources.toCharArray())
					if (c == 'W')
						sourcesString += "WikiSearch10+";
					else if (c == 'N')
						sourcesString += "NormalSearch+";
					else if (c == 'S')
						sourcesString += "AnnotateSnippet25+";
				sourcesString = sourcesString.substring(0,
						sourcesString.length() - 1);

				double wPos = weightsToTest[idWeight][0];
				double wNeg = weightsToTest[idWeight][1];
				double gamma = paramsToTest[idParam][0];
				double C = paramsToTest[idParam][1];
				String modelFileEF = GenerateModel.getModelFileNameBaseEF(
						featuresSetsToTest[idftr], wPos, wNeg, gamma, C)
						+ "_" + sources+"-erd";
				runId = String.format(SMAPH_PARAMS_FORMAT, "wikisense", 0.0,
						"COMMONNESS", "base", "jaccard", 0.6f,
						"Frequency", "SvmEntityFilter",
						modelFileEF, "NoEmptyQueryFilter", "null",
						sourcesString);

			}
		}
		if (runId.startsWith("BING-")) {
			for (String paramSet : runId.substring(5).split("&")) {
				if (paramSet.split("=").length == 1)
					continue;
				String paramName = paramSet.split("=")[0];
				String paramValue = paramSet.split("=")[1];
				if (paramName.equals("auxAnnotator"))
					auxAnnotator = paramValue;
				if (paramName.equals("minLp"))
					minLp = paramValue;
				if (paramName.equals("sortBy"))
					sortBy = paramValue;
				if (paramName.equals("method"))
					method = paramValue;
				if (paramName.equals("relatedness"))
					relatedness = paramValue;
				if (paramName.equals("spotFilterThreshold"))
					spotFilterThreshold = Float.parseFloat(paramValue);
				if (paramName.equals("spotFilter"))
					spotFilterName = paramValue;
				if (paramName.equals("entityFilter"))
					entityFilterName = paramValue;
				if (paramName.equals("svmEntityFilterModelBase"))
					svmEntityFilterModelBase = paramValue;
				if (paramName.equals("emptyQueryFilter"))
					emptyQueryFilterName = paramValue;
				if (paramName.equals("svmEmptyQueryFilterModelBase"))
					svmEmptyQueryFilterModelBase = paramValue;
				if (paramName.equals("entitySources"))
					for (String srcName : paramValue.split("\\+"))
						entitySources.add(srcName);

			}
			int sourcesCount = 0;
			for (String src : entitySources) {
				if (src.equals("NormalSearch")) {
					includeSourceNormalSearch = true;
					sourcesCount++;
				}
				if (src.startsWith("WikiSearch")) {
					includeSourceWikiSearch = true;
					wikiSearchPages = Integer.parseInt(src
							.substring("WikiSearch".length()));
					sourcesCount++;
				}
				if (src.startsWith("AnnotateSnippet")) {
					includeSourceAnnotateSnippet = true;
					topKSnippetsToAnnotate = Integer.parseInt(src
							.substring("AnnotateSnippet".length()));
					sourcesCount++;
				}
			}
			if (sourcesCount != entitySources.size())
				throw new RuntimeException("Unrecognized Source.");
			System.out
					.printf("Parameters: annotator=%s, minLp=%s, sortBy=%s, method=%s, relatedness=%s, spotFilter=%s, spotManagerThreshold=%f entityFilter=%s svmEntityFilterModel=%s emptyQueryFilterName=%s svmEmptyQueryFilterModel=%s includeSourceAnnotator=%b includeSourceNormalSearch=%b includeSourceWikiSearch=%b (wikiSearchPages=%d) includeSourceAnnotatorCandidates=%b (topKannotatorCandidates=%d) includeSourceSnippets=%b (topKSnippets=%d)%n",
							auxAnnotator, minLp, sortBy, method, relatedness,
							spotFilterName, spotFilterThreshold,
							entityFilterName, svmEntityFilterModelBase,
							emptyQueryFilterName, svmEmptyQueryFilterModelBase,
							includeSourceAnnotator, includeSourceNormalSearch,
							includeSourceWikiSearch, wikiSearchPages,
							includeSourceAnnotatorCandidates,
							topKannotatorCandidates, includeSourceAnnotateSnippet, topKSnippetsToAnnotate);

			EntityFilter entityFilter = null;
			if (entityFilterName.equals("NoEntityFilter"))
				entityFilter = new NoEntityFilter();
			else if (entityFilterName.equals("SvmEntityFilter")) {
				synchronized (Annotator.class) {
					if (!svmEntityFilterModelBase.equals("")
							&& (libSvmEntityFilter == null || !libSvmEntityFilter
									.getModel()
									.equals(svmEntityFilterModelBase))) {
						try {
							libSvmEntityFilter = new LibSvmEntityFilter(
									svmEntityFilterModelBase+".model");
						} catch (IOException e) {
							e.printStackTrace();
							throw new RuntimeException(e);
						}
					}
					entityFilter = libSvmEntityFilter;
				}
			}
			WATAnnotator watDefault = new WATAnnotator(
					"wikisense.mkapp.it", 80, "base", "COMMONNESS", "mw", "0.2",
					"0.0", false, false, false);
			List<Annotation> res = annotatePure(query, textID,
					new SmaphAnnotator(
							entityFilter, new ZScoreFeatureNormalizer(svmEntityFilterModelBase+".zscore", new EntityFeaturePack()), new DummyLinkBack(),
							includeSourceNormalSearch,
							includeSourceWikiSearch, wikiSearchPages,
							includeSourceAnnotateSnippet, topKSnippetsToAnnotate, false, watDefault
							, new FrequencyAnnotationFilter(0.03), wikiApi, bingKey));
			//TODO: we are not using titles!
			return res;
		}

		else if (runId.equals("wat")) {
			if (wat == null) {
				wat = new WATAnnotator(
						"wikisense.mkapp.it", 80, "base", "COMMONNESS", "mw", "0.2",
						"0.0", false, false, false);
			}
			return annotatePure(query, textID, wat);
		} else if (runId.equals("void")){
			System.out.println("Processing query: " + query);
			return new Vector<>();
		}else if (runId.startsWith("collective")) {
			if (collective == null) {
				SmaphConfig.setConfigFile("smaph-config.xml");
				String bingKey = SmaphConfig.getDefaultBingKey();
				String bingCache = SmaphConfig.getDefaultBingCache();
				if (bingCache != null)
					try {
						BingInterface.setCache(bingCache);
					} catch (ClassNotFoundException | IOException e) {
						e.printStackTrace();
						throw new RuntimeException(e);
					}
				String rankLibBindingModel = "models/model_1-255_RL_0.060.full.6.NDCG@19.ERD-S2S3S6.model"; // "models/model_train_binding_ranking.dat_130-132,139-141,172-174,224-225,229-234.t1000.l10.NDCG@19";
				String bindingNorm = "models/train_binding_ranking_ERD-S2S3S6.zscore"; // "/tmp/train_binding_ranking.zscore";
				try {
					collective = GenerateTrainingAndTest.getDefaultBingAnnotatorCollectiveLBRanklibAllSources(wikiApi, bingKey, rankLibBindingModel, bindingNorm);
					collective.setPredictNEOnly(true);
				} catch (Exception e) {
					e.printStackTrace();
					throw new RuntimeException(e);
				}
			}
			
			List<Annotation> res = annotatePure(query, textID, collective);
			return res;
		}

		throw new RuntimeException("unrecognized runID=" + runId);

	}

	private static HashSet<MultipleAnnotation> deleteOverlappingAnnotations(
			HashSet<MultipleAnnotation> anns) {
		Vector<MultipleAnnotation> annsList = new Vector<MultipleAnnotation>(
				anns);
		HashSet<MultipleAnnotation> res = new HashSet<MultipleAnnotation>();
		Collections.sort(annsList);

		for (int i = 0; i < annsList.size(); i++) {
			MultipleAnnotation bestCandidate = annsList.get(i);
			int j = i + 1;
			while (j < annsList.size()
					&& bestCandidate.overlaps(annsList.get(j))) {
				if (bestCandidate.getLength() < annsList.get(j).getLength())
					bestCandidate = annsList.get(j);
				j++;
			}
			i = j - 1;
			res.add(bestCandidate);
		}
		return res;

	}

}
