#!/bin/bash
TRAIN=$1
TEST=$2
TRAIN_OPTS=$3

~/Downloads/liblinear-1.96/train $TRAIN_OPTS -s 13 $TRAIN /tmp/model
~/Downloads/liblinear-1.96/predict $TEST /tmp/model /tmp/pred

expected=($(cat $TEST | sed 's/ .*//' ))
preds=($(</tmp/pred))

if [ "${#preds[@]}" -ne "${#expected[@]}" ]; then
	echo "this is wrong: ${#preds[@]} -ne ${#expected[@]}"
	exit
fi

echo "Read ${#preds[@]} datapoints."

(
for (( i=0; i<${#preds[@]}; i++ ))
	do
		echo -e "${expected[i]}\t${preds[i]}"
	done
	) | python feature_analysis/prediction_analysis.py
