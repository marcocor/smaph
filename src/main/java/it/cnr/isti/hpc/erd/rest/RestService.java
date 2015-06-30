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

package it.cnr.isti.hpc.erd.rest;

import it.acubelab.batframework.data.ScoredAnnotation;
import it.acubelab.batframework.systemPlugins.WATAnnotator;
import it.acubelab.batframework.utils.WikipediaApiInterface;
import it.unipi.di.acube.BingInterface;
import it.acubelab.smaph.SmaphAnnotator;
import it.acubelab.smaph.SmaphAnnotatorDebugger;
import it.acubelab.smaph.SmaphConfig;
import it.acubelab.smaph.boldfilters.FrequencyBoldFilter;
import it.acubelab.smaph.entityfilters.LibSvmEntityFilter;
import it.acubelab.smaph.learn.featurePacks.EntityFeaturePack;
import it.acubelab.smaph.learn.normalizer.ScaleFeatureNormalizer;
import it.acubelab.smaph.linkback.DummyLinkBack;
import it.cnr.isti.hpc.erd.Annotation;
import it.cnr.isti.hpc.erd.Annotator;

import java.io.IOException;
import java.net.URLEncoder;
import java.util.HashSet;
import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import org.codehaus.jettison.json.*;

import com.sun.jersey.multipart.FormDataParam;

/**
 * @author Diego Ceccarelli <diego.ceccarelli@isti.cnr.it> (edited by Marco
 *         Cornolti)
 * 
 *         Created on Mar 15, 2014
 */

@Path("")
public class RestService {

	@POST
	@Path("/shortTrack")
	@Consumes(MediaType.MULTIPART_FORM_DATA)
	@Produces({ MediaType.TEXT_PLAIN })
	public String annotatePost(@FormDataParam("runID") String runId,
			@FormDataParam("TextID") String textId,
			@FormDataParam("Text") String text) {
		Annotator annotator = new Annotator();
		List<Annotation> annotations = annotator.annotate(runId, textId, text);

		return encodeAnnotations(annotations);
	}

	private String encodeAnnotations(List<Annotation> annotations) {
		StringBuilder sb = new StringBuilder();
		for (Annotation a : annotations) {
			sb.append(a.toTsv()).append('\n');

		}
		return sb.toString();
	}

	@GET
	@Path("/debug")
	@Produces({ MediaType.APPLICATION_JSON })
	public String debugSmaph(@QueryParam("Text") String text) {
		WikipediaApiInterface wikiApi = getDefaultWikiInterface();
		SmaphAnnotator ann = getDefaultAnnotator(wikiApi);
		SmaphAnnotatorDebugger debugger = new SmaphAnnotatorDebugger();
		ann.setDebugger(debugger);
		ann.solveSa2W(text);

		try {
			return debugger.toJson(wikiApi).toString();
		} catch (JSONException | IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	@GET
	@Path("/default")
	@Produces({ MediaType.APPLICATION_JSON })
	public String annotateGetFull(@QueryParam("Text") String text) {
		WikipediaApiInterface wikiApi = getDefaultWikiInterface();
		SmaphAnnotator ann = getDefaultAnnotator(wikiApi);
		return encodeJsonResponse(ann.solveSa2W(text), wikiApi);
	}

	private WikipediaApiInterface getDefaultWikiInterface() {
		try {
			return new WikipediaApiInterface("wid.cache", "redirect.cache");
		} catch (ClassNotFoundException | IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}
	}

	private SmaphAnnotator getDefaultAnnotator(WikipediaApiInterface wikiApi) {
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

		String modelBase = "models/model_1-3,6-7,9-25,33-37_EF_3.80000_4.00000_0.060_0.03000000_5.00000000_ANW";
		WATAnnotator auxAnnotatorService = new WATAnnotator(
				"wikisense.mkapp.it", 80, "base", "COMMONNESS", "jaccard", "0.6",
				"0", false, false, false);

		try {
			return new SmaphAnnotator(auxAnnotatorService,
					new FrequencyBoldFilter(0.06f), new LibSvmEntityFilter(
							modelBase), new ScaleFeatureNormalizer(modelBase+".range", new EntityFeaturePack()), new DummyLinkBack(), true, true, true,
					10, false, -1, false, -1, false, 0, false, null, null, wikiApi, bingKey);
		} catch (IOException e) {
			e.printStackTrace();
			throw new RuntimeException(e);
		}

	}

	private String encodeJsonResponse(HashSet<ScoredAnnotation> annotations,
			WikipediaApiInterface wikiApi) {
		JSONObject res = new JSONObject();

		try {
			res.put("response-code", "OK");

			JSONArray annotJson = new JSONArray();
			for (ScoredAnnotation ann : annotations) {
				int wid = ann.getConcept();
				String title = wikiApi.getTitlebyId(ann.getConcept());
				if (wid >= 0 && title != null) {
					JSONObject annJson = new JSONObject();
					annJson.put("wid", wid);
					annJson.put("title", title);
					annJson.put(
							"url",
							"http://en.wikipedia.org/wiki/"
									+ URLEncoder.encode(title, "utf8").replace(
											"+", "%20"));
					annotJson.put(annJson);
				}
			}
			res.put("annotations", annotJson);
		} catch (JSONException | IOException e) {
			throw new RuntimeException(e);
		}

		return res.toString();
	}

}
