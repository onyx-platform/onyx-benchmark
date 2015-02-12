include_recipe "apt::default"

apt_package "python-dev" do
  action :install
end

include_recipe "graphite::packages"

template "/opt/graphite/conf/carbon.conf" do
  source "carbon/carbon.conf"
  mode '0440'
  owner 'root'
  group 'root'
end

include_recipe "jetty::default"
