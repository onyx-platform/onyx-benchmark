include_recipe "apt::default"
include_recipe "java::default"
include_recipe "zookeeper::default"

execute "/opt/zookeeper/zookeeper-3.4.6/bin/zkServer.sh start"

execute "apt-get install -y python-pip"
execute "pip install https://s3.amazonaws.com/cloudformation-examples/aws-cfn-bootstrap-latest.tar.gz"

