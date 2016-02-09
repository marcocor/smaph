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

package it.unipi.di.acube.smaph.learn;

import it.unipi.di.acube.batframework.metrics.MetricsResultSet;
import it.unipi.di.acube.smaph.learn.TuneModelLibSvm.OptimizaionProfiles;

import java.io.Serializable;
import java.util.List;

public class ModelConfigurationResult implements Serializable {
	private static final long serialVersionUID = 1L;
	private int[] pickedFtrs;
	private double wPos, wNeg, gamma, c, thr;

	private int totalInstances;
	private int tp;
	private int fp;
	private int fn;
	private int tn;
	private float microf1, fnRate, macrof1, macroPrec, macroRec;

	public ModelConfigurationResult(int[] pickedFtrsI, double wPos,
			double wNeg, double gamma, double c, double thr, int tp, int fp, int fn,
			int tn, float microF1, float macroF1, float macroRec, float macroPrec) {
		this.pickedFtrs = pickedFtrsI.clone();
		this.wPos = wPos;
		this.wNeg = wNeg;
		this.thr = thr;
		this.totalInstances = tp + fp + fn + tn;
		this.tp = tp;
		this.fp = fp;
		this.fn = fn;
		this.tn = tn;
		this.microf1 = microF1;
		this.macrof1 = macroF1;
		this.fnRate = (float) fn / (float) (fn + tp);
		this.macroRec = macroRec;
		this.macroPrec = macroPrec;
		this.c = c;
		this.gamma = gamma;
	}

	public ModelConfigurationResult(int[] pickedFtrsI, double wPos, double wNeg, double gamma, double c, double thr,
			MetricsResultSet metrics, int examplesCount) {
		this.tp = metrics.getGlobalTp();
		this.fp = metrics.getGlobalFp();
		this.fn = metrics.getGlobalFn();
		this.totalInstances = examplesCount;
		this.tn = totalInstances - tp - fp - fn;
		this.microf1 = metrics.getMicroF1();
		this.macrof1 = metrics.getMacroF1();
		this.macroRec = metrics.getMacroRecall();
		this.macroPrec = metrics.getMacroPrecision();
		this.pickedFtrs = pickedFtrsI.clone();
		this.wPos = wPos;
		this.wNeg = wNeg;
		this.thr = thr;
		this.fnRate = (float) fn / (float) (fn + tp);
		this.c = c;
		this.gamma = gamma;
	}

	public String getReadable() {
		String ftrsString = "";
		for (int ftrId : pickedFtrs)
			ftrsString += ftrId + ",";
		ftrsString = pickedFtrs.length == 0 ? "null!" : ftrsString.substring(0,
				ftrsString.length() - 1);
		return String
				.format("Features:%s wPos:%.5f wNeg:%.5f gamma:%.5f C:%.5f Thr:%.5f tot=%d TP=%d FP=%d FN=%d TN=%d -> FN_rate=%.2f%% mic-F1=%.2f%% mac-P/R/F1=%.2f%%/%.2f%%/%.2f%%",
						ftrsString, wPos, wNeg, gamma, c, thr,
						totalInstances, tp, fp, fn, tn, fnRate * 100,
						microf1 * 100, macroPrec*100,macroRec*100, macrof1 * 100);
	}

	public int[] getFeatures() {
		return this.pickedFtrs;
	}

	public double getWPos() {
		return this.wPos;
	}

	public double getWNeg() {
		return this.wNeg;
	}

	public int getTN() {
		return this.tn;
	}

	public float getMicroF1() {
		return this.microf1;
	}

	public float getMacroF1() {
		return this.macrof1;
	}

	public double getFNrate() {
		return this.fnRate;
	}
	public float getMacroPrecision() {
		return this.macroPrec;
	}
	public float getMacroRecall() {
		return this.macroRec;
	}

	public int getTP() {
		return this.tp;
	}

	public int getFP() {
		return this.fp;
	}

	public int getFN() {
		return this.fn;
	}

	public Double getC() {
		return this.c;
	}

	public Double getGamma() {
		return this.gamma;
	}

	public double getThreshold() {
		return this.thr;
	}

	public boolean worseThan(ModelConfigurationResult other,
			OptimizaionProfiles optProfile, double optProfileThreshold) {
		boolean betterResult = false;
		if (other == null)
			return true;

		if (optProfile == OptimizaionProfiles.MAXIMIZE_TN)
			betterResult = (other.getFNrate() < optProfileThreshold
					&& other.getTN() > this.getTN());
		else if (optProfile == OptimizaionProfiles.MAXIMIZE_MICRO_F1)
			betterResult = (other.getMicroF1() > this.getMicroF1());
		else if (optProfile == OptimizaionProfiles.MAXIMIZE_MACRO_F1)
			betterResult = (other.getMacroF1() > this.getMacroF1());

		return betterResult;
	}

	public boolean equalResult(ModelConfigurationResult other,
			OptimizaionProfiles optProfile, double optProfileThreshold) {
		boolean equalResult = false;
		if (other == null)
			return true;

		if (optProfile == OptimizaionProfiles.MAXIMIZE_TN)
			equalResult = other.getFNrate() < optProfileThreshold
			&& other.getTN() == this.getTN();
		else if (optProfile == OptimizaionProfiles.MAXIMIZE_MICRO_F1)
			equalResult = other.getMicroF1() == this.getMicroF1();
		else if (optProfile == OptimizaionProfiles.MAXIMIZE_MACRO_F1)
			equalResult = other.getMacroF1() == this.getMacroF1();

		return equalResult;
	}

	public static ModelConfigurationResult findBest(
			List<ModelConfigurationResult> configurations,
			OptimizaionProfiles optProfile, double optProfileThreshold) {
		ModelConfigurationResult best = null;
		for (ModelConfigurationResult conf : configurations)
			if (best == null
			|| best.worseThan(conf, optProfile, optProfileThreshold)
			|| (best.equalResult(conf, optProfile, optProfileThreshold) && best
					.getFeatures().length > conf.getFeatures().length))
				best = conf;
		return best;
	}
}
