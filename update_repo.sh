#!/usr/bin/env bash
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

BENCHMARK_REV=$1

## TODO: STASH ONYX LOGS

cd /
rm -rf onyx-benchmark
git clone https://github.com/onyx-platform/onyx-benchmark.git
cd /onyx-benchmark
git checkout $BENCHMARK_REV
