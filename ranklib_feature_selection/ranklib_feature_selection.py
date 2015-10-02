#!/bin/python
from __future__ import print_function
import os
import numpy
import os.path
import sys
from ranking import *

RANKLIB_PATH = "../libs/RankLib-2.5.jar"
RANKER = 6
TRAIN_DATA = "../train_binding_ranking.dat"
VALIDATE_DATA = "../devel_binding_ranking.dat"
#TRAIN_DATA = "train_bin_stage2.dat"
#VALIDATE_DATA = "devel_bin_stage2.dat"
OPT_VALS = [10,11,12,13,14,15,16,17,18,19,20,21,22,23,24]
JAVA_OPTS="-Xmx6g"

def ftr_selection_loop(good_ftr, ftr_buckets_left, qid_cand_to_score, best_f1, update_foo):
	print("all feature buckets to try: {}".format(ftr_buckets_left), file=sys.stderr)
	while True:
		best_in_iteration = None
		print("starting iteration with feature set: {}".format(good_ftr), file=sys.stderr)
		for feature_bucket in ftr_buckets_left:
			ftrs_to_try = update_foo(good_ftr, feature_bucket)
			model, f1 = generate_and_test_model(ftrs_to_try, qid_cand_to_score, OPT_VALS, RANKER, TRAIN_DATA, VALIDATE_DATA)
			print("achieved f1={} with model {} and feature set {} [added/removed features {}]".format(f1, model, ftrs_to_try, feature_bucket), file=sys.stderr)
			if not best_in_iteration or f1 >= best_in_iteration[1]:
				print("found new best score inside iteration. F1={} ({}overall best)".format(f1, "no " if f1 <= best_f1 else ""), file=sys.stderr)
				best_in_iteration = (feature_bucket, f1)
		if not best_in_iteration or best_in_iteration[1] < best_f1:
			print("no advances in this iteration, stopping iterations.", file=sys.stderr)
			break
		else:
			best_f1 = best_in_iteration[1]
			good_ftr = update_foo(good_ftr, best_in_iteration[0])
			ftr_buckets_left.remove(best_in_iteration[0])
			print("iteration finished: found best F1={} selected feature {}".format(best_in_iteration[1], best_in_iteration[0]), file=sys.stderr)
		if not ftr_buckets_left:
			print("no more features to try, stopping interations.", file=sys.stderr)
			break
	return good_ftr, best_f1

increment_features = lambda prev_ftrs, ftr_bucket: sorted(prev_ftrs + ftr_bucket)
decrement_features = lambda prev_ftrs, ftr_bucket: sorted([f for f in prev_ftrs if f not in ftr_bucket])

FTR_STEP = 1
good_ftr = [] #[2, 3, 4, 151, 154, 155, 156, 162, 169, 170, 171, 172, 173, 174, 199, 200, 201, 202, 203, 204, 220, 221, 222, 223, 224, 225]

all_ftrs = list(reversed(get_valid_ftrs(VALIDATE_DATA)))
ftr_buckets_left = []
for r in range(0, len(all_ftrs), FTR_STEP):
	bucket = sorted([f for f in all_ftrs[r:r+FTR_STEP] if f not in good_ftr])
	if bucket:
		ftr_buckets_left.append(bucket)
ftr_buckets_left = sorted(ftr_buckets_left)

qid_cand_to_score = load_f1s(VALIDATE_DATA)


if good_ftr:
	print("Initialization - testing initial feature set", file=sys.stderr)
	_, best_f1 = generate_and_test_model(good_ftr, qid_cand_to_score, OPT_VALS, RANKER, TRAIN_DATA, VALIDATE_DATA)
	print("Initial best score: {}".format(best_f1), file=sys.stderr)

print("First phase - feature increment", file=sys.stderr)
good_ftr, best_f1 = ftr_selection_loop(good_ftr, ftr_buckets_left, qid_cand_to_score, best_f1, increment_features)
print("1st phase - Overall best features: {}".format(good_ftr), file=sys.stderr)
print("1st phase - Overall best score: {}".format(best_f1), file=sys.stderr)

print("Second phase - feature ablation", file=sys.stderr)
good_ftr, best_f1 = ftr_selection_loop(good_ftr, [[f] for f in good_ftr], qid_cand_to_score, best_f1, decrement_features)
print("2nd phase - Overall best features: {}".format(good_ftr), file=sys.stderr)
print("2nd phase - Overall best score: {}".format(best_f1), file=sys.stderr)

print("Third phase - Parameter tuning", file=sys.stderr)
for t in [200, 500,1000]:
	for l in [7,8,9,10,11,12]:
		model, best_f1 = generate_and_test_model(good_ftr, qid_cand_to_score, OPT_VALS, RANKER, TRAIN_DATA, VALIDATE_DATA, tree=[t], leaf=[l])
		print("3rd phase - Overall best model: {}".format(model), file=sys.stderr)
		print("3rd phase - Overall best score: {}".format(best_f1), file=sys.stderr)
		

