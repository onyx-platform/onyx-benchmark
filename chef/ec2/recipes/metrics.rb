include_recipe "apt::default"

apt_package "graphite-web" do
  action :install
end

execute "DEBIAN_FRONTEND=noninteractive apt-get -q -y --force-yes install graphite-carbon"
execute "graphite-manage syncdb"

