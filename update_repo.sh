#!/usr/bin/env bash
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

BENCHMARK_REV=$1

rm -rf /onyx-benchmark
git clone https://github.com/MichaelDrogalis/onyx-benchmark.git
cd /onyx-benchmark
git checkout $BENCHMARK_REV
