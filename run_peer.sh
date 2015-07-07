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
MESSAGING=$5
LOG_LEVEL=$6

DEPLOYMENT_ID=$ONYX_REV"_"$BENCHMARK_REV"_"$RUN_ID

killall -9 java || true

export LEIN_ROOT=1

# update repo again since the other task might no be run if some other variables change?
# investigate later
./update_repo.sh $BENCHMARK_REV

cd /
rm -rf onyx
git clone https://github.com/onyx-platform/onyx.git
cd /onyx
git checkout $ONYX_REV
lein install
bash -x install-aeron.sh

cd /onyx-benchmark

ZOOKEEPER_ADDR=$(cat /home/ubuntu/zookeeper.txt)
RIEMANN_ADDR=$(cat /home/ubuntu/metrics.txt)

./tune-os.sh linux

echo "lein run -m onyx-benchmark.peer $ZOOKEEPER_ADDR $RIEMANN_ADDR $DEPLOYMENT_ID $VPEERS $MESSAGING"

export TIMBRE_LOG_LEVEL=$LOG_LEVEL

lein deps

#java -Xmx7g -server -Xbootclasspath/a:/home/ubuntu/.lein/self-installs/leiningen-2.5.1-standalone.jar -Dfile.encoding=UTF-8 -Dmaven.wagon.http.ssl.easy=false -Dmaven.wagon.rto=10000 -Dleiningen.original.pwd=/onyx-benchmark -Dleiningen.script=/usr/local/bin/lein -classpath /home/ubuntu/.lein/self-installs/leiningen-2.5.1-standalone.jar clojure.main -m leiningen.core.main run -m onyx-benchmark.peer $ZOOKEEPER_ADDR $RIEMANN_ADDR $DEPLOYMENT_ID $VPEERS $MESSAGING &

lein run -m onyx-benchmark.peer $ZOOKEEPER_ADDR $RIEMANN_ADDR $DEPLOYMENT_ID $VPEERS $MESSAGING &
