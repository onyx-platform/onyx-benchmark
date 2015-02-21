# onyx-benchmark

A benchmarking suite used by the Onyx team.

Start with the aws cli tool e.g.

```shell
aws cloudformation create-stack \
    --template-body file://aws/benchmark-stack.template \
    --stack-name onyx-benchmark \
    --parameters \
        ParameterKey=AccountNumber,ParameterValue=<AWSACCTNUMBER> \
        ParameterKey=AccessKeyId,ParameterValue=<AWSACCESSKEY> \
        ParameterKey=SecretAccessKey,ParameterValue=<AWSSECRETKEY> \
        ParameterKey=KeyName,ParameterValue=<SSHKEYNAME> \
        ParameterKey=PeerSpotPrice,ParameterValue=<PRICEINDOLLARS> \
        ParameterKey=ZooKeeperSpotPrice,ParameterValue=<PRICEINDOLLARS> \
	ParameterKey=MetricsSpotPrice,ParameterValue=<PRICEINDOLLARS> \
	ParameterKey=RegionAZ,ParameterValue=us-east-1c
```

## License

Copyright © 2015 Michael Drogalis

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
