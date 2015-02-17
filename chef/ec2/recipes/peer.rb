include_recipe "apt::default"
include_recipe "java::default"
include_recipe "git::default"
include_recipe "lein::default"

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

template "/etc/collectd/collectd.conf" do
  source "collectd/collectd.conf"
  owner "ubuntu"
  group "ubuntu"
end

execute "apt-get install -y python-pip"
execute "pip install https://s3.amazonaws.com/cloudformation-examples/aws-cfn-bootstrap-latest.tar.gz"

