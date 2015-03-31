#!/usr/bin/env bash

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

TYPE=$1

git push

BENCHMARK_GIT_COMMIT=$(git rev-parse HEAD) 
ONYX_GIT_COMMIT=1aefd535b4fec4cfe4ab3331cd6fe1bb3151487d
RUN_ID=$(date +"%d_%m_%Y_%H_%M_%S")

DEPLOYMENT_ID=$ONYX_GIT_COMMIT"_"$BENCHMARK_GIT_COMMIT"_"$RUN_ID

aws cloudformation $TYPE \
    --template-body file://aws/benchmark-stack.template \
    --stack-name onyx-benchmark \
    --capabilities CAPABILITY_IAM \
    --parameters \
        ParameterKey=AccountNumber,ParameterValue="065949834151" \
        ParameterKey=AccessKeyId,ParameterValue=$AWS_ACCESS_KEY \
        ParameterKey=SecretAccessKey,ParameterValue=$AWS_SECRET_KEY \
        ParameterKey=KeyName,ParameterValue=us-east.pem \
	ParameterKey=PeerSpotPrice,ParameterValue=2.0 \
	ParameterKey=Peers,ParameterValue=1 \
	ParameterKey=VirtualPeers,ParameterValue=3 \
	ParameterKey=ZooKeeperSpotPrice,ParameterValue=0.2 \
	ParameterKey=MetricsSpotPrice,ParameterValue=0.1 \
	ParameterKey=RegionAZ,ParameterValue=us-east-1c \
	ParameterKey=PeerInstanceType,ParameterValue=m3.large \
	ParameterKey=BenchmarkGitCommit,ParameterValue=$BENCHMARK_GIT_COMMIT \
	ParameterKey=OnyxGitCommit,ParameterValue=$ONYX_GIT_COMMIT \
	ParameterKey=RunId,ParameterValue=$RUN_ID

echo $DEPLOYMENT_ID

ZOOKEEPER="54.204.205.221"
METRICS="54.163.190.132"
lein run -m onyx-benchmark.submit $ZOOKEEPER $METRICS $DEPLOYMENT_ID 20
