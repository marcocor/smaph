#!/usr/bin/env bash

pushd scripts
if [ ! -f entity.tsv ]
then
  echo "File entity.tsv not found, downloading"
  wget http://web-ngram.research.microsoft.com/erd2014/Docs/entity.tsv
fi
popd


echo "now you can run IndexWikipediaLabelToFreebaseIdCLI with options: -input scripts/entity.tsv -dbdir mapdb"
