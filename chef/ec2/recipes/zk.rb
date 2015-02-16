include_recipe "apt::default"
include_recipe "java::default"
include_recipe "zookeeper::default"

execute "/opt/zookeeper/zookeeper-3.4.6/bin/zkServer.sh start"

remote_file "/home/ubuntu/aws.tar.bz" do
  action :create_if_missing
  source "https://s3.amazonaws.com/cloudformation-examples/aws-cfn-bootstrap-latest.tar.gz"
end

execute "mkdir -p /opt/aws/bin/"

execute "apt-get install -y python-pip"
execute "pip install easyinstall"

execute "tar -xvf /home/ubuntu/aws.tar.bz -C /home/ubuntu"
execute "easy_install /home/ubuntu/aws-cfn-bootstrap-1.4"
execute "chmod +x /home/ubuntu/aws-cfn-bootstrap-1.4/bin/cfn-signal"
execute "chmod +x /home/ubuntu/aws-cfn-bootstrap-1.4/bin/cfn-hup"
execute "chmod +x /home/ubuntu/aws-cfn-bootstrap-1.4/bin/cfn-init"
execute "chmod +x /home/ubuntu/aws-cfn-bootstrap-1.4/bin/cfn-get-metadata"
execute "chmod +x /home/ubuntu/aws-cfn-bootstrap-1.4/bin/cfn-elect-cmd-leader"

execute "ln -s /home/ubuntu/aws-cfn-bootstrap-1.4/bin/cfn-signal /opt/aws/bin/cfn-signal"
execute "ln -s /home/ubuntu/aws-cfn-bootstrap-1.4/bin/cfn-hup /opt/aws/bin/cfn-hup"
execute "ln -s /home/ubuntu/aws-cfn-bootstrap-1.4/bin/cfn-init /opt/aws/bin/cfn-init"
execute "ln -s /home/ubuntu/aws-cfn-bootstrap-1.4/bin/cfn-get-metadata /opt/aws/bin/cfn-get-metadata"
execute "ln -s /home/ubuntu/aws-cfn-bootstrap-1.4/bin/cfn-elect-cmd-leader /opt/aws/bin/cfn-elect-cmd-leader"

