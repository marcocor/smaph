#!/usr/bin/env bash

VERSION="0.0.1-SNAPSHOT"
XMX="-Xmx2000m"
LOG=INFO
##LOG=DEBUG
LOGAT=1000
E_BADARGS=65
JAVA="java $XMX -Dlogat=$LOGAT -Dlog=$LOG -cp .:scripts/erd-challenge.jar "
CLI=it.cnr.isti.hpc.erd.cli



export LC_ALL=C
