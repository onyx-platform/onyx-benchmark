include_recipe "apt::default"
include_recipe "java::default"
include_recipe "zookeeper::default"

execute "/opt/zookeeper/zookeeper-3.4.6/bin/zkServer.sh start"

execute "mkdir -p /opt/aws/bin/"

execute "tar -xvf /home/ubuntu/aws.tar.bz -C /home/ubuntu"
execute "easy_install /home/ubuntu/aws"
execute "chmod +x /home/ubuntu/aws/bin/cfn-signal"
execute "chmod +x /home/ubuntu/aws/bin/cfn-hup"
execute "chmod +x /home/ubuntu/aws/bin/cfn-init"
execute "chmod +x /home/ubuntu/aws/bin/cfn-get-metadata"
execute "chmod +x /home/ubuntu/aws/bin/cfn-elect-cmd-leader"

execute "ln -s /home/ubuntu/aws/bin/cfn-signal /opt/aws/bin/cfn-signal"
execute "ln -s /home/ubuntu/aws/bin/cfn-hup /opt/aws/bin/cfn-hup"
execute "ln -s /home/ubuntu/aws/bin/cfn-init /opt/aws/bin/cfn-init"
execute "ln -s /home/ubuntu/aws/bin/cfn-get-metadata /opt/aws/bin/cfn-get-metadata"
execute "ln -s /home/ubuntu/aws/bin/cfn-elect-cmd-leader /opt/aws/bin/cfn-elect-cmd-leader"

