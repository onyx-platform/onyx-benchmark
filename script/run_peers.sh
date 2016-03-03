#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

export BIND_ADDR=$(hostname --ip-address)
export APP_NAME=$(echo "onyx-benchmark" | sed s/"-"/"_"/g)
exec java $PEER_JAVA_OPTS -cp /srv/onyx-benchmark.jar "$APP_NAME.peer" $NPEERS 
