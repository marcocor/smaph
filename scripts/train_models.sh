#!/bin/bash

# This script launches the training of SMAPH-1, SMAPH-S and SMAPH-3 for both Google and Bing flavors, with and without Source 2.

mvn clean compile || exit

FTR_SEL="ablation-rank"

for TYPE in entity-filter annotation-regressor greedy-regressor ; do
	for S2_SRC in 10 0; do
		for SE in google bing; do
			mvn exec:java -Dexec.mainClass="it.unipi.di.acube.smaph.learn.TuneModelLibSvm" -Dexec.args="--opt-$TYPE -w $SE --ftr-sel-method ${FTR_SEL} --topk-S2 $S2_SRC" &> out.train.${FTR_SEL}.$TYPE.S2=${S2_SRC}.${SE};
		 done ;
	done ;
done ;

