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

package it.unipi.di.acube.smaph.datasets.wikitofreebase;

/**
 * @author Diego Ceccarelli <diego.ceccarelli@isti.cnr.it>
 * 
 *         Created on Mar 15, 2014
 */
public class Annotation {
	private String qid;
	private Integer interpretationSet;
	private String primaryId;
	private String mentionText;
	private float score;

	public Annotation() {

	}

	public String toTsv() {
		StringBuilder sb = new StringBuilder();
		sb.append(qid).append('\t');
		sb.append(interpretationSet).append('\t');
		sb.append(primaryId).append('\t');
		sb.append(mentionText).append('\t');
		sb.append(score);
		return sb.toString();

	}

	public String getQid() {
		return qid;
	}

	public void setQid(String qid) {
		this.qid = qid;
	}

	public Integer getInterpretationSet() {
		return interpretationSet;
	}

	public void setInterpretationSet(Integer interpretationSet) {
		this.interpretationSet = interpretationSet;
	}

	public String getPrimaryId() {
		return primaryId;
	}

	public void setPrimaryId(String primaryId) {
		this.primaryId = primaryId;
	}

	public String getMentionText() {
		return mentionText;
	}

	public void setMentionText(String mentionText) {
		this.mentionText = mentionText;
	}

	public float getScore() {
		return score;
	}

	public void setScore(float score) {
		this.score = score;
	}

}
