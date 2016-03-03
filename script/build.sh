#!/usr/bin/env bash
set -o errexit
set -o nounset
set -o xtrace

set -e
lein clean
lein uberjar

docker build --shm-size=1G -t onyxplatform/onyx-benchmark:0.0.1 . 
