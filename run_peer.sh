#!/usr/bin/env bash
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
# set -o xtrace

ONYX_REV=$1
BENCHMARK_REV=$2
RUN_ID=$3
VPEERS=$4

DEPLOYMENT_ID=$1"_"$2

killall -9 java || true

cd /onyx
git checkout master
git fetch --all 
git pull --all 
git checkout $ONYX_REV
lein install

cd /onyx-benchmark
git checkout master
git fetch --all 
git pull --all 
git checkout $BENCHMARK_REV

LEIN_ROOT=1 lein run -m onyx-benchmark.peer $DEPLOYMENT_ID $VPEERS &
