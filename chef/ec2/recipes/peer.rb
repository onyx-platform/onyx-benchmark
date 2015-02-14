include_recipe "apt::default"
include_recipe "java::default"
include_recipe "git::default"
include_recipe "lein::default"
include_recipe "collectd::default"

apt_package "ibprotobuf-c0-dev" do
  action :install
end

apt_package "protobuf-c-compiler" do
  action :install
end

template "/etc/collectd/collectd.conf" do
  source "collectd/collectd.conf"
  owner "vagrant"
  group "vagrant"
end
