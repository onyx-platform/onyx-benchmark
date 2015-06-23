#!/usr/bin/env bash

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

CREATE_UPDATE=$1

git push

source bench.config

aws cloudformation $CREATE_UPDATE \
    --stack-name onyx-benchmark \
    --template-body file://aws/benchmark-stack.template \
    --capabilities CAPABILITY_IAM \
    --parameters \
    ParameterKey=AccessKeyId,ParameterValue=$AWS_ACCESS_KEY \
    ParameterKey=SecretAccessKey,ParameterValue=$AWS_SECRET_KEY \
    ParameterKey=PeersInstanceType,ParameterValue=$PEER_INSTANCE_TYPE \
    ParameterKey=VirtualPeers,ParameterValue=$VIRTUAL_PEERS \
    ParameterKey=PeersNumberOfInstances,ParameterValue=$N_MACHINES \
    ParameterKey=KeyPair,ParameterValue=$KEY_PAIR \
    ParameterKey=BenchmarkGitCommit,ParameterValue=$BENCHMARK_REVISION \
    ParameterKey=OnyxGitCommit,ParameterValue=$ONYX_REVISION \
    ParameterKey=OnyxLogLevel,ParameterValue=$ONYX_LOG_LEVEL \
    ParameterKey=RunId,ParameterValue=$RUN_ID \
    ParameterKey=AvailabilityZone,ParameterValue=$AVAILABILITY_ZONE
