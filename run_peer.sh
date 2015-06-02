#!/usr/bin/env bash
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

ONYX_REV=$1
BENCHMARK_REV=$2
RUN_ID=$3
VPEERS=$4
LOG_LEVEL=$5


DEPLOYMENT_ID=$ONYX_REV"_"$BENCHMARK_REV"_"$RUN_ID

killall -9 java || true

export LEIN_ROOT=1

# update repo again since the other task might no be run if some other variables change?
# investigate later
./update_repo.sh $BENCHMARK_REV

cd /
rm -rf onyx
git clone https://github.com/MichaelDrogalis/onyx.git
cd /onyx
git checkout $ONYX_REV
lein install

#cd .. && git clone https://github.com/real-logic/Aeron.git && cd Aeron && git checkout 3fc10054424fbaa0d5613f3baa05d1b43de2939a && ./gradlew && gradle install

cd /onyx-benchmark

ZOOKEEPER_ADDR=$(cat /home/ubuntu/zookeeper.txt)
RIEMANN_ADDR=$(cat /home/ubuntu/metrics.txt)

echo "lein run -m onyx-benchmark.peer $ZOOKEEPER_ADDR $RIEMANN_ADDR $DEPLOYMENT_ID $VPEERS"
./tune-os.sh linux

export TIMBRE_LOG_LEVEL=$LOG_LEVEL

lein run -m onyx-benchmark.peer $ZOOKEEPER_ADDR $RIEMANN_ADDR $DEPLOYMENT_ID $VPEERS &
