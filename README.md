# onyx-benchmark

The official benchmarking suite used by the Onyx team.

## Usage

### Infrastructure set up

This benchmark suite is suite up with **Ansible 2**.

Make sure you have Boto installed:

```bash
$ pip install boto
```

Next, start up the machines with:

```text
$ cd ansible
$ ansible-playbook --private-key ~/.ssh/your-aws-key.pem -i "," -e remote_user="ubuntu" -e onyx_cluster_id=your-cluster-id -e aws_access_key="XXXXXX" -e aws_secret_key="YYYYYY" -e aws_key_name="your-aws-key-name" tasks/main.yml
```

Running this will construct a private VPC in AWS with a cluster of machines. Software will be installed to support the entire Onyx cluster, including metrics. Override any variables in `defaults/main.yml` with your own value by specifying `-e key=value` on the command line run. Note that security is **wide** open on these machines.

### Ports

Grafana: Metrics machine, port 3000

## License

Copyright © 2015 Distributed Masonry LLC

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
