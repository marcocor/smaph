#!/bin/bash
mvn install:install-file -Dfile=RankLib-2.1-patched.jar -DgroupId=ranklib.plus -DartifactId=ranklib -Dversion=2.1 -Dpackaging=jar
mvn install:install-file -Dfile=hpc-utils-0.0.5.jar -DgroupId=it.cnr.isti.hpc -DartifactId=hpc-utils -Dversion=0.0.5 -Dpackaging=jar
