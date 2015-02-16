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

execute "psql -c \"CREATE USER graphite WITH PASSWORD 'password'\"" do
  user "postgres"
end

execute "psql -c \"CREATE DATABASE graphite WITH OWNER graphite\"" do
  user "postgres"
end

template "/etc/graphite/local_settings.py" do
  source "graphite/local_settings.py"
  owner "ubuntu"
  group "ubuntu"
end

execute "graphite-manage syncdb --noinput"
execute "echo \"from django.contrib.auth.models import User; User.objects.create_superuser('admin', 'x@x.com', 'blah')\" | python /usr/lib/python2.7/dist-packages/graphite/manage.py shell"

template "/etc/default/graphite-carbon" do
  source "carbon/graphite-carbon"
  owner "ubuntu"
  group "ubuntu"
end

template "/etc/default/carbon.conf" do
  source "carbon/carbon.conf"
  owner "ubuntu"
  group "ubuntu"
end

template "/etc/default/storage-schemas.conf" do
  source "carbon/storage-schemas.conf"
  owner "ubuntu"
  group "ubuntu"
end

template "/etc/carbon/storage-aggregation.conf" do
  source "carbon/storage-aggregation.conf"
  owner "ubuntu"
  group "ubuntu"
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

include_recipe "grafana::default"
include_recipe "elasticsearch::default"

template "/srv/apps/grafana/app/dashboards/default.json" do
  source "grafana/default.json"
  owner "ubuntu"
  group "ubuntu"
end

remote_file "/home/ubuntu/riemann.tar.bz2" do
  action :create_if_missing
  source "https://aphyr.com/riemann/riemann-0.2.8.tar.bz2"
end

execute "tar -xvf /home/ubuntu/riemann.tar.bz2 -C /home/ubuntu"

template "/home/ubuntu/riemann-0.2.8/etc/riemann.config" do
  source "riemann/riemann.config"
  owner "ubuntu"
  group "ubuntu"
end

execute "/home/ubuntu/riemann-0.2.8/bin/riemann /home/ubuntu/riemann-0.2.8/etc/riemann.config &"

remote_file "/home/ubuntu/aws.tar.bz" do
  action :create_if_missing
  source "https://s3.amazonaws.com/cloudformation-examples/aws-cfn-bootstrap-latest.tar.gz"
end

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

