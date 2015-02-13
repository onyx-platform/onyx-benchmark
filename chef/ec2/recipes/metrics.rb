include_recipe "apt::default"

apt_package "graphite-web" do
  action :install
end

apt_package "postgresql" do
  action :install
end

apt_package "libpq-dev" do
  action :install
end

apt_package "python-psycopg2" do
  action :install
end

execute "DEBIAN_FRONTEND=noninteractive apt-get -q -y --force-yes install graphite-carbon"

execute "su -c \"echo 'CREATE USER graphite WITH PASSWORD \"password\";' | psql\" postgres"
execute "su -c \"echo 'CREATE DATABASE graphite WITH OWNER graphite;' | psql\" postgres"

template "/etc/graphite/local_settings.py" do
  source "graphite/local_settings.py"
  owner "vagrant"
  group "vagrant"
end

execute "graphite-manage syncdb --noinput"
execute "echo \"from django.contrib.auth.models import User; User.objects.create_superuser('admin', 'x@x.com', 'blah')\" | python /usr/lib/python2.7/dist-packages/graphite/manage.py shell"

template "/etc/default/graphite-carbon" do
  source "carbon/graphite-carbon"
  owner "vagrant"
  group "vagrant"
end

template "/etc/default/carbon.conf" do
  source "carbon/carbon.conf"
  owner "vagrant"
  group "vagrant"
end

template "/etc/default/storage-schemas.conf" do
  source "carbon/storage-schemas.conf"
  owner "vagrant"
  group "vagrant"
end

template "/etc/carbon/storage-aggregation.conf" do
  source "carbon/storage-aggregation.conf"
  owner "vagrant"
  group "vagrant"
end

execute "service carbon-cache start"

apt_package "apache2" do
  action :install
end

apt_package "libapache2-mod-wsgi" do
  action :install
end

execute "a2dissite 000-default"
execute "cp /usr/share/graphite-web/apache2-graphite.conf /etc/apache2/sites-available"
execute "a2ensite apache2-graphite"
execute "service apache2 reload"

