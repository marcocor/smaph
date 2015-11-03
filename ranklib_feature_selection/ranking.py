#!/bin/python
from __future__ import print_function
import os
import numpy
import os.path
import sys
import hashlib
import re
from collections import Counter

RANKLIB_PATH = "../libs/RankLib-2.5.jar"
JAVA_OPTS_TRAIN="-Xmx3g"
JAVA_OPTS_SCORE="-Xmx512m"

def ftr_string_set(s):
	res = set()
	for ra in s.split(","):
		m1 = re.match("^\d+$", ra)
		m2 = re.match("^(\d+)-(\d+)$", ra)
		if m1: 
			res.add(int(ra))
		elif m2:
			res |= set(range(int(m2.group(1)), int(m2.group(2))+1))
		else:
			raise Exception("Could not parse string: {}".format(ra))
	return sorted(list(res))

assert ftr_string_set("1-2,5-6,8,10-12") == [1,2,5,6,8,10,11,12]

def ftr_set_string(ftrs):
	ftr_list = ""
	i = 0
	last_inserted = -1
	last_block_size = 1
	while (i < len(ftrs)):
		current = ftrs[i]
		if i == 0: # first feature
			ftr_list += str(current)
		elif current == last_inserted + 1: # continuation of a block
			if i == len(ftrs) - 1: # last element, close block
				ftr_list += "-" + str(current)
			last_block_size += 1
		else : # start of a new block
			if last_block_size > 1:
				ftr_list += "-" + str(last_inserted)
			ftr_list += "," + str(current)
			last_block_size = 1
		last_inserted = current
		i += 1
	return ftr_list;

assert ftr_set_string([1,2,5,6,8,10,11,12]) == "1-2,5-6,8,10-12"

def parse_line(l):
	l = l.split()
	assert l[-2] == "#"
	assert l[-1][:3] == "F1="
	assert l[1][:4] == "qid:"
	f1 = float(l[-1][3:])
	return [(int(fv[0:fv.index(":")]), float(fv[fv.index(":")+1:])) for fv in l[2:-2]], f1, int(l[1][4:])

def load_f1s(validate_file):
	qid_cand_to_score = dict()
	i = None
	last_qid = -1
	with open(validate_file) as f:
		for l in f:
			ftrs, f1, qid = parse_line(l)
			if qid != last_qid:
				last_qid = qid
				i = 0
			else:
				i += 1
			qid_cand_to_score[(qid, i)] = f1
	return qid_cand_to_score

def get_ftr_chunks(dataset_file, fake_value=None):
	res = Counter()
	with open(dataset_file) as f:
		for l in f:
			set_features = tuple(sorted([p[0] for p in parse_line(l)[0] if p[1] != fake_value]))
			res[set_features] += 1
	return res

def get_valid_ftrs(train_file):
	diverse_features = set()
	first_line = None
	with open(train_file) as f:
		for l in f:
			if not first_line:
				first_line = dict(parse_line(l)[0])
			else:
				f_dict = dict(parse_line(l)[0])
				diverse_features |= set([f for f in f_dict.keys() if first_line[f] != f_dict[f]])
	return sorted(list(diverse_features))

def build_models(ftrs_to_try, opt_vals, ranker, train_file, validate_file, optimize="NDCG", model_name_prefix="", tree=[1000], leaf=[10]):
	get_name = lambda optimize, t, l, op, model_name_base : '{4}.t{1}.l{2}.{0}@{3}'.format(optimize, t, l, op, model_name_base)
	ftrs_string = hashlib.md5(ftr_set_string(ftrs_to_try)).hexdigest()
	features_file = ftr_filename(ftrs_string)
	if not os.path.isfile(features_file):
		gen_ftr_file(ftrs_to_try)
	model_name_base = "models/" + model_name_prefix + "model_" + os.path.basename(train_file) + "_" + ftrs_string 
	models_param = [(optimize, t, l, o, model_name_base) for t in tree for l in leaf for o in opt_vals]
	to_train_param = [p for p in models_param if not os.path.isfile(get_name(*p))]
	if not to_train_param:
		print("all models already trained, skipping", file=sys.stderr)
	else:
		cmds = ['"java {} -jar {} -ranker {} -feature {} -train {} -validate {}'.format(JAVA_OPTS_TRAIN, RANKLIB_PATH, ranker, features_file, train_file, validate_file) + ' -metric2t {0}@{3} -tree {1} -leaf {2} -save {5}"'.format(*(p + (get_name(*p),))) for p in to_train_param]
		cmd = "parallel --gnu ::: {}".format(" ".join(cmds))
		print(cmd, file=sys.stderr)
		os.system(cmd)
	for p in models_param:
		assert(os.path.isfile(get_name(*p)))
	return [get_name(*p) for p in models_param]

def get_scores(scores_file, qid_cand_to_f1):
	qid_to_score_and_f1 = dict()
	with open(scores_file) as f:
		for l in f:
			t = l.split()
			qid, cand, p_score = int(t[0]), int(t[1]), float(t[2])
			if qid not in qid_to_score_and_f1 or qid_to_score_and_f1[qid][0] < p_score:
				qid_to_score_and_f1[qid] = (p_score, qid_cand_to_f1[(qid, cand)])
	return numpy.mean(zip(*qid_to_score_and_f1.values())[1])

def get_scores_f1_qid_and_maxf1(scores_file, qid_cand_to_f1):
	qid_to_max_f1 = dict()
	score_f1_qids = []
	with open(scores_file) as f:
		for l in f:
			t = l.split()
			qid, cand, p_score = int(t[0]), int(t[1]), float(t[2])
			f1 = qid_cand_to_f1[(qid, cand)]
			if qid not in qid_to_max_f1 or qid_to_max_f1[qid] < f1:
				qid_to_max_f1[qid] = f1
			score_f1_qids.append((p_score, f1, qid))
	max_macro_f1 = numpy.mean(qid_to_max_f1.values())
	return score_f1_qids, max_macro_f1, qid_to_max_f1

def get_max_macrof1(qid_cand_to_f1):
	return numpy.mean([max([qid_cand_to_f1[qid_cand] for qid_cand in qid_cand_to_f1.keys() if qid_cand[0] == qid]) for qid in set(zip(*(qid_cand_to_f1.keys()))[0])])

def get_scores_binary(scores_file, qid_cand_to_f1):
	score_f1_qids, max_macro_f1, qid_to_max_f1 = get_scores_f1_qid_and_maxf1(scores_file, qid_cand_to_f1)
	pos_count = len([score_f1_qid for score_f1_qid in score_f1_qids if score_f1_qid[1] > 0])
	neg_count = len([score_f1_qid for score_f1_qid in score_f1_qids if score_f1_qid[1] == 0])
	print("positive:{} negative:{} max_f1:{}".format(pos_count, neg_count, max_macro_f1), file=sys.stderr)
	all_scores = sorted(zip(*score_f1_qids)[0])
	min_score = all_scores[int(len(all_scores)*0.25)] #do not range in the bottom-10% scores
	max_score = all_scores[int(len(all_scores)*0.97)] #do not range in the top-3% scores
	res = (-1, -1, -1, -1, -1)
	for threshold in numpy.arange(min_score, max_score, (max_score-min_score)/50.0):
		fn, tn, missed_best = 0, 0, 0
		qid_to_new_best_f1 = dict()
		for score_f1_qid in score_f1_qids:
			if score_f1_qid[0] < threshold:
				if score_f1_qid[1] > 0:
					fn += 1
				if score_f1_qid[1] == 0:
					tn += 1
				if score_f1_qid[1] > 0 and score_f1_qid[1] == qid_to_max_f1[score_f1_qid[2]]:
					missed_best += 1
			else:
				if score_f1_qid[2] not in qid_to_new_best_f1 or qid_to_new_best_f1[score_f1_qid[2]] < score_f1_qid[1]:
					qid_to_new_best_f1[score_f1_qid[2]] = score_f1_qid[1]
		new_best_macro_f1 = numpy.mean(qid_to_new_best_f1.values())
		this_res = (threshold, fn/float(neg_count), tn/float(neg_count), missed_best, new_best_macro_f1)
		print("Thr:{} fn:{} tn:{} missed:{} new_best_f1:{}".format(*this_res), file=sys.stderr)

		if (new_best_macro_f1 > max_macro_f1 - 0.03) and this_res[2] > res[2]:
			res = this_res
			print("Found new best.", file=sys.stderr)
		else:
			print("Discarding.", file=sys.stderr)
	return res

def test_models(model_names, qid_cand_to_score, validate_file, scores_computer=get_scores, scores_file_prefix=""):
	cmd = "parallel --gnu java {} -jar {} -load {{}} -rank {} -score {}{{}}.scores ::: {}".format(JAVA_OPTS_SCORE, RANKLIB_PATH, validate_file, scores_file_prefix, " ".join(model_names))
	print(cmd, file=sys.stderr)
	os.system(cmd)
	return [(model_name, scores_computer(scores_file_prefix+model_name+".scores", qid_cand_to_score) if scores_computer else None) for model_name in model_names]

def ftr_filename(ftrs_string):
	return "models/" + ftrs_string + ".features"

def gen_ftr_file(ftrs):
	ftrs_string = hashlib.md5(ftr_set_string(ftrs)).hexdigest()
	filename = ftr_filename(ftrs_string)
	with open(filename, "w") as f:
		for ftr in ftrs:
			f.write(str(ftr))
			f.write("\n")
	return filename

def generate_and_test_model(ftrs_to_try, qid_cand_to_score, opt_vals, ranker, train_file, validate_file, tree=[1000], leaf=[10]):
	print("testing feature set: {}".format(ftrs_to_try), file=sys.stderr)
	gen_ftr_file(ftrs_to_try)
	models = build_models(ftrs_to_try, opt_vals, ranker, train_file, validate_file, tree=tree, leaf=leaf)
	print("generated models: {}".format(" ".join(models)), file=sys.stderr)
	models_and_f1s = test_models(models, qid_cand_to_score, validate_file)
	for p in models_and_f1s:
		print("model {} F1={}".format(*p), file=sys.stderr)
	model, f1 = max(models_and_f1s, key=lambda p: p[1])
	return model, f1
