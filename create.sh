#!/usr/bin/env bash

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

CREATE_UPDATE=$1

git push

source bench.config
# --disable-rollback \
aws cloudformation $CREATE_UPDATE \
    --stack-name onyx-benchmark \
    --template-body file://aws/benchmark-stack.template \
    --capabilities CAPABILITY_IAM \
    --parameters \
    ParameterKey=AccountNumber,ParameterValue=$AWS_ACCOUNT_NUMBER \
    ParameterKey=AccessKeyId,ParameterValue=$AWS_ACCESS_KEY \
    ParameterKey=SecretAccessKey,ParameterValue=$AWS_SECRET_KEY \
    ParameterKey=PeersInstanceType,ParameterValue=$PEER_INSTANCE_TYPE \
    ParameterKey=PeersNumberOfInstances,ParameterValue=$NUM_PEERS \
    ParameterKey=VirtualPeers,ParameterValue=$VIRTUAL_PEERS \
    ParameterKey=YellerToken,ParameterValue=$YELLER_TOKEN \
    ParameterKey=KeyPair,ParameterValue=us-east.pem \
    ParameterKey=BenchmarkGitCommit,ParameterValue=$BENCHMARK_REVISION \
    ParameterKey=OnyxGitCommit,ParameterValue=$ONYX_REVISION \
    ParameterKey=OnyxLogLevel,ParameterValue=$ONYX_LOG_LEVEL \
    ParameterKey=RunId,ParameterValue=$RUN_ID
