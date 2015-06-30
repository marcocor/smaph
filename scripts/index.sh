#!/usr/bin/env bash
source ./scripts/config.sh

EXPECTED_ARGS=1

if [ $# -ne $EXPECTED_ARGS ]
then
  echo "Usage: `basename $0` db-folder"
  exit $E_BADARGS
fi

pushd scripts
if [ ! -f entity.tsv ]
then
  echo "File entity.tsv not found, downloading"
  wget http://web-ngram.research.microsoft.com/erd2014/Docs/entity.tsv
fi
popd


echo "index wikipedia-label -> freebase-id mappings in entity.tsv in folder $1"
$JAVA $CLI.IndexWikipediaLabelToFreebaseIdCLI -input scripts/entity.tsv -dbdir $1
echo "done, mappings in $1"
