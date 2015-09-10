#!/bin/python
from __future__ import print_function
import os
import numpy
import os.path
import sys

RANKLIB_PATH = "../libs/RankLib-2.5.jar"
RANKER = 6
TRAIN_DATA = "../train_binding_ranking.dat"
VALIDATE_DATA = "../devel_binding_ranking.dat"
OPT_VALS = [9,11,13,15,17,19,21,23]
JAVA_OPTS="-Xmx6g"

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

def load_validate_f1s():
	qid_cand_to_score = dict()
	i = None
	last_qid = -1
	with open(VALIDATE_DATA) as f:
		for l in f:
			ftrs, f1, qid = parse_line(l)
			if qid != last_qid:
				last_qid = qid
				i = 0
			else:
				i += 1
			qid_cand_to_score[(qid, i)] = f1
	return qid_cand_to_score

def get_valid_ftrs():
	diverse_features = set()
	first_line = None
	with open(TRAIN_DATA) as f:
		for l in f:
			if not first_line:
				first_line = dict(parse_line(l)[0])
			else:
				f_dict = dict(parse_line(l)[0])
				diverse_features |= set([f for f in f_dict.keys() if first_line[f] != f_dict[f]])
	return sorted(list(diverse_features))

def build_models(ftrs_to_try):
	ftrs_string = ftr_set_string(ftrs_to_try)
	model_name_base = "model_" + ftrs_string 
	to_train = [o for o in OPT_VALS if not os.path.isfile("{}.NDCG@{}".format(model_name_base, o))]
	if not to_train:
		print("all models already trained, skipping", file=sys.stderr)
	else:
		cmd = "parallel --gnu java {} -jar {} -ranker {} -feature {}.features -metric2t NDCG@{{}} -train {} -validate {} -save {}.NDCG@{{}} ::: {}".format(JAVA_OPTS, RANKLIB_PATH, RANKER, ftrs_string, TRAIN_DATA, VALIDATE_DATA, model_name_base, " ".join(str(o) for o in to_train))
		print(cmd, file=sys.stderr)
		os.system(cmd)
	res= ["{}.NDCG@{}".format(model_name_base, o) for o in OPT_VALS]
	for filename in res:
		assert(os.path.isfile(filename))
	return res

def get_scores(scores_file, qid_cand_to_f1):
	qid_to_score_and_f1 = dict()
	with open(scores_file) as f:
		for l in f:
			t = l.split()
			qid, cand, p_score = int(t[0]), int(t[1]), float(t[2])
			if qid not in qid_to_score_and_f1 or qid_to_score_and_f1[qid][0] < p_score:
				qid_to_score_and_f1[qid] = (p_score, qid_cand_to_f1[(qid, cand)])
	assert set(p[0] for p in qid_cand_to_score.keys()) == set(qid_to_score_and_f1.keys())
	return numpy.mean(zip(*qid_to_score_and_f1.values())[1])
				

def test_models(model_names, qid_cand_to_score):
	cmd = "parallel --gnu java {} -jar {} -load {{}} -rank {} -score {{}}.scores ::: {}".format(JAVA_OPTS, RANKLIB_PATH, VALIDATE_DATA, " ".join(model_names))
	print(cmd, file=sys.stderr)
	os.system(cmd)
	return [(model_name, get_scores(model_name+".scores", qid_cand_to_score)) for model_name in model_names]

def gen_ftr_file(ftrs):
	filename = ftr_set_string(ftrs_to_try) + ".features"
	with open(filename, "w") as f:
		for ftr in ftrs:
			f.write(str(ftr))
			f.write("\n")

good_ftr = []
ftr_buckets_left = (lambda fl : sorted([sorted(fl[r:r+3]) for r in range(0, len(fl), 3)])) (list(reversed(get_valid_ftrs())))
print("all feature buckets: {}".format(ftr_buckets_left), file=sys.stderr)
qid_cand_to_score = load_validate_f1s()
best_f1 = None

while True:
	best_in_iteration = None
	print("starting iteration with feature set: {}".format(good_ftr), file=sys.stderr)
	for feature_bucket in ftr_buckets_left:
		ftrs_to_try = sorted(good_ftr + feature_bucket)
		print("testing feature set: {}".format(ftrs_to_try), file=sys.stderr)
		gen_ftr_file(ftrs_to_try)
		models = build_models(ftrs_to_try)
		print("generated models: {}".format(" ".join(models)), file=sys.stderr)
		models_and_f1s = test_models(models, qid_cand_to_score)
		for p in models_and_f1s:
			print("model {} F1={}".format(*p), file=sys.stderr)
		model, f1 = max(models_and_f1s, key=lambda p: p[1])
		print("achieved f1={} with model {} and feature set {}".format(f1, model, ftrs_to_try), file=sys.stderr)
	
		if not best_in_iteration or f1 >= best_in_iteration[1]:
			print("found new best score inside iteration. F1={}".format(f1), file=sys.stderr)
			best_in_iteration = (feature_bucket, f1)
	if not best_in_iteration or best_in_iteration[1] < best_f1:
		print("no advances in this iteration, stopping iterations.", file=sys.stderr)
		break
	else:
		best_f1 = best_in_iteration
		good_ftr = sorted(good_ftr + best_in_iteration[0])
		ftr_buckets_left.remove(best_in_iteration[0])
		print("iteration finished: found best F1={} adding feature {}".format(best_in_iteration[1], best_in_iteration[0]), file=sys.stderr)
	if not ftr_buckets_left:
		print("no more features to try, stopping interations.", file=sys.stderr)
		break

print("Overall best features: {}".format(good_ftr), file=sys.stderr)
print("Overall best score: {}".format(best_f1), file=sys.stderr)
