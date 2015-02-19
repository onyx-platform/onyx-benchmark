apt_package "automake" do
  action :install
end

apt_package "libtool" do
  action :install
end

apt_package "libbz2-dev" do
  action :install
end

apt_package "libpq-dev" do
  action :install
end

apt_package "libgeos++-dev" do
  action :install
end

apt_package "libxml2-dev" do
  action :install
end

apt_package "build-essential" do
  action :install
end

apt_package "protobuf-c-compiler" do
  action :install
end

apt_package "libprotobuf-c0-dev" do
  action :install
end

include_recipe "collectd::default"

zk_addr = File.open("/home/ubuntu/zookeeper.txt", "r").read
metrics_addr = File.open("/home/ubuntu/metrics.txt", "r").read

template "/etc/collectd/collectd.conf" do
  source "collectd/collectd.conf.erb"
  owner "ubuntu"
  group "ubuntu"
  variables({
    :zk => zk_addr,
    :metrics => metrics_arr
  })
end

execute "apt-get install -y python-pip"
execute "pip install https://s3.amazonaws.com/cloudformation-examples/aws-cfn-bootstrap-latest.tar.gz"

