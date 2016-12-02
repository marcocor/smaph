#!/bin/python
from __future__ import print_function
import os
import numpy
import os.path
import sys
from ranking import *
import argparse
import shutil

RANKLIB_PATH = "../libs/RankLib-2.5.jar"
RANKER = 6

def ftr_selection_loop(good_ftr, valid_ftrs, qid_cand_to_score, best_f1, update_foo, step, cpus=None):
	while True:
		candidate_ftrs = [f for f in valid_ftrs if f not in good_ftr] if update_foo == increment_features else [f for f in good_ftr if f in valid_ftrs]
		ftr_buckets_left = get_feature_buckets(candidate_ftrs, step)
		print("starting iteration with feature set: {}".format(good_ftr), file=sys.stderr)
		print("all feature buckets to try: {}".format(ftr_buckets_left), file=sys.stderr)
		better_alternatives = []
		for feature_bucket in ftr_buckets_left:
			ftrs_to_try = update_foo(good_ftr, feature_bucket)
			model, f1, _ = generate_and_test_model(ftrs_to_try, qid_cand_to_score, OPT_VALS, RANKER, TRAIN_DATA, VALIDATE_DATA, cpus=cpus)
			print("achieved f1={} with model {} and feature set {} [added/removed features {}]".format(f1, model, ftrs_to_try, feature_bucket), file=sys.stderr)
			if is_improvement(f1, ftrs_to_try, best_f1, good_ftr):
				print("found a better score inside iteration. F1={}".format(f1), file=sys.stderr)
				better_alternatives = sorted([(f1, feature_bucket)] + better_alternatives, reverse=True)
			else:
				print("found no improvement. F1={}".format(f1), file=sys.stderr)
		if not better_alternatives:
			print("no advances in this iteration, stopping iterations.", file=sys.stderr)
			break
		else:
			print("starting vertical pruning. order:{}".format(better_alternatives), file=sys.stderr)
			for feature_bucket in zip(*better_alternatives)[1]:
				print("trying to add/remove {}".format(feature_bucket), file=sys.stderr)
				ftrs_to_try = update_foo(good_ftr, feature_bucket)
				model, f1, employed_ftrs = generate_and_test_model(ftrs_to_try, qid_cand_to_score, OPT_VALS, RANKER, TRAIN_DATA, VALIDATE_DATA, cpus=cpus)
				if is_improvement(f1, ftrs_to_try, best_f1, good_ftr):
					best_f1 = f1
					ftr_buckets_left.remove(feature_bucket)
					good_ftr = employed_ftrs
					print("vertical pruning: found best F1={} selected feature {}".format(best_f1, feature_bucket), file=sys.stderr)
					print("Features actually employed by these models ({} features): {}".format(len(employed_ftrs), ftr_set_string(employed_ftrs)), file=sys.stderr)
				else:
					print("vertical pruning: no new best found with features {} (F1={})".format(feature_bucket, best_f1), file=sys.stderr)
		if not ftr_buckets_left:
			print("no more features to try, stopping interations.", file=sys.stderr)
			break
	return good_ftr, best_f1

def get_feature_buckets(all_ftrs, step):
	ftr_buckets_left = []
	for r in range(0, len(all_ftrs), step):
		bucket = sorted(all_ftrs[r:r+step])
		if bucket:
			ftr_buckets_left.append(bucket)
	return sorted(ftr_buckets_left)

def do_one_phase(update_foo, good_ftr, valid_ftrs, best_f1, step):
	print("Stage: {} - {} by {}".format(update_foo.__name__, step, step), file=sys.stderr)
	good_ftr, best_f1 = ftr_selection_loop(good_ftr, valid_ftrs, qid_cand_to_score, best_f1, update_foo, step)
	print("Overall best features: {}".format(good_ftr), file=sys.stderr)
	print("Overall best score: {}".format(best_f1), file=sys.stderr)
	
	return good_ftr, best_f1

def increment_features(prev_ftrs, ftr_bucket):
	return sorted(prev_ftrs + ftr_bucket)

def decrement_features(prev_ftrs, ftr_bucket):
	return sorted([f for f in prev_ftrs if f not in ftr_bucket])

def is_improvement(new_f1, new_ftrs, old_f1, old_ftrs):
	if len(new_ftrs) < len(old_ftrs):
		return new_f1 >= old_f1 #with a smaller feature set, can we achieve the same (or better) score?
	else:
		return new_f1 > old_f1 #with a bigger feature set, can we achieve a strictly better score?


if __name__ == '__main__':
	parser = argparse.ArgumentParser(description='Do feature selections')
	parser.add_argument('--method', help="Method used in feature selection iterations", choices=['ablation', 'increment', 'oneshot'], required=True)
	parser.add_argument('--dataset', help="Dataset code (e.g. ERD-S1S2S3)", required=True)
	parser.add_argument('--startset', help="Feature set to start with (e.g. 1,3,5-10,24). Default: use all features (no features for 'increment' method)", required=False, default="")
	parser.add_argument('--candidate_features', help="Feature set to try to add (increment) or remove (ablation) (e.g. 1,3,5-10,24). Default: use all features of the start set (ablation) or all valid features not in startset (increment)", required=False, default="")
	parser.add_argument('--leaves', help="Number of tree leaves (e.g. 5,7,10-20). Default: 10.", required=False, default="10")
	parser.add_argument('--opt_vals', help="Values to optimize NDGC with. (e.g. 5,7,10-20). Default: 1-23.", required=False, default="1-23")
	parser.add_argument('--cpus', help="Number of training processes to start in parallel.", required=False, default="")
	args = parser.parse_args()

	leaves = ftr_string_set(args.leaves)
	OPT_VALS = ftr_string_set(args.opt_vals)
	cpus = None if not args.cpus else int(args.cpus)

	TRAIN_DATA = "../train_coll_{}.dat".format(args.dataset)
	VALIDATE_DATA = "../devel_coll_{}.dat".format(args.dataset)

	if not os.path.exists("models"):
		os.makedirs("models")

	print("Loading solutions F1...", file=sys.stderr)
	qid_cand_to_score = load_f1s(VALIDATE_DATA)

	print("Upper bound on macro-F1: {}".format(get_max_macrof1(qid_cand_to_score)), file=sys.stderr)

	print("Getting valid features...", file=sys.stderr)
	valid_ftrs = get_valid_ftrs(VALIDATE_DATA)

	good_ftr = ftr_string_set(args.startset) if args.startset else valid_ftrs
	
	candidate_ftrs = ftr_string_set(args.candidate_features) if args.candidate_features else valid_ftrs

	if args.method == 'ablation':
		main_method, secondary_method = decrement_features, increment_features
	elif args.method == 'increment':
		main_method, secondary_method = increment_features, decrement_features

	if args.method in['increment', 'ablation']:
		print("Testing initial feature set", file=sys.stderr)
		for leaf in leaves:
			_, best_f1, employed_ftrs = generate_and_test_model(good_ftr, qid_cand_to_score, OPT_VALS, RANKER, TRAIN_DATA, VALIDATE_DATA, args.dataset, leaf=[leaf], cpus=cpus)
		print("Initial best score: {}".format(best_f1), file=sys.stderr)
		print("Initial features actually employed by these models ({} features): {}".format(len(employed_ftrs), ftr_set_string(employed_ftrs)), file=sys.stderr)
		good_ftr = employed_ftrs
	
		if args.method == 'increment' and not good_ftr: #start from scratch
			best_f1 = 0.0

		good_ftr, best_f1 = do_one_phase(main_method, good_ftr, candidate_ftrs, best_f1, 6)
		good_ftr, best_f1 = do_one_phase(main_method, good_ftr, candidate_ftrs, best_f1, 3)
	
		good_ftr, best_f1 = do_one_phase(secondary_method, good_ftr, candidate_ftrs, best_f1, 1)

	print("Parameter tuning", file=sys.stderr)
	overall_best_f1, overall_best_model = -1, None
	for l in leaves:
		model, best_f1, employed_ftrs = generate_and_test_model(good_ftr, qid_cand_to_score, OPT_VALS, RANKER, TRAIN_DATA, VALIDATE_DATA, leaf=[l], cpus=cpus)
		if best_f1 > overall_best_f1:
			overall_best_f1, overall_best_model = best_f1, model
		print("Tuning - Overall best model: {}".format(model), file=sys.stderr)
		print("Tuning - Overall best score: {}".format(best_f1), file=sys.stderr)
		print("Tuning - Features actually employed by these models ({} features): {}".format(len(employed_ftrs), ftr_set_string(employed_ftrs)), file=sys.stderr)

	if overall_best_model:
		bestmodel_name = "../src/main/resources/models/best_{}".format(get_model_base_name(args.dataset))
		print("Exporting best model {} to {}".format(overall_best_model, bestmodel_name, file=sys.stderr))
		shutil.copy(overall_best_model, bestmodel_name)
	
