#!/usr/bin/env bash
# Bash3 Boilerplate. Copyright (c) 2014, kvz.io

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

BENCHMARK_REV=$1

cd /onyx-benchmark
git clean -f
git checkout master
git fetch --all 
git pull --all 
git checkout $BENCHMARK_REV
