#!/usr/bin/env bash

set -o errexit
set -o pipefail
set -o nounset
set -o xtrace

source bench.config
## should grab this from a config file as with other things

DEPLOYMENT_ID=$ONYX_REVISION"_"$BENCHMARK_REVISION"_"$RUN_ID

# aws cloudformation $TYPE \
#     --template-body file://aws/benchmark-stack.template \
#     --stack-name onyx-benchmark \
#     --capabilities CAPABILITY_IAM \
#     --parameters \
#         ParameterKey=AccountNumber,ParameterValue="065949834151" \
#         ParameterKey=AccessKeyId,ParameterValue=$AWS_ACCESS_KEY \
#         ParameterKey=SecretAccessKey,ParameterValue=$AWS_SECRET_KEY \
#         ParameterKey=KeyName,ParameterValue=us-east.pem \
# 	ParameterKey=PeerSpotPrice,ParameterValue=2.0 \
# 	ParameterKey=Peers,ParameterValue=1 \
# 	ParameterKey=VirtualPeers,ParameterValue=3 \
# 	ParameterKey=ZooKeeperSpotPrice,ParameterValue=0.2 \
# 	ParameterKey=MetricsSpotPrice,ParameterValue=0.1 \
# 	ParameterKey=RegionAZ,ParameterValue=us-east-1c \
# 	ParameterKey=PeerInstanceType,ParameterValue=m3.large \
# 	ParameterKey=BenchmarkGitCommit,ParameterValue=$BENCHMARK_GIT_COMMIT \
# 	ParameterKey=OnyxGitCommit,ParameterValue=$ONYX_GIT_COMMIT \
# 	ParameterKey=RunId,ParameterValue=$RUN_ID

echo $DEPLOYMENT_ID

#nc -z $ZOOKEEPER 2181

lein run -m onyx-benchmark.submit $ZOOKEEPER_ADDR $DEPLOYMENT_ID 20

# if [ $? -eq 0 ]; then
# 	lein run -m onyx-benchmark.submit $ZOOKEEPER $DEPLOYMENT_ID 20
# else
# 	echo ZooKeeper is not up, thus we are unable to submit job
# fi
