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

DEPLOYMENT_ID=$1"_"$2

killall -9 java || true

export LEIN_ROOT=1

cd /onyx
git clean -f
git checkout master
git fetch --all 
git pull --all 
git checkout $ONYX_REV
lein clean
lein install

cd /onyx-benchmark
git clean -f
git checkout master
git fetch --all 
git pull --all 
git checkout $BENCHMARK_REV

ZOOKEEPER_ADDR=$(cat /home/ubuntu/zookeeper.txt)

lein run -m onyx-benchmark.peer $ZOOKEEPER_ADDR $DEPLOYMENT_ID $VPEERS &
