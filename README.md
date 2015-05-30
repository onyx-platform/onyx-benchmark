# onyx-benchmark

A benchmarking suite used by the Onyx team.

Start with the aws cli tool after making sure to set your region to us-east-1.

Edit bench.config to set bench settings

Run ./create.sh create-stack to stand up stack
Run ./create.sh update-stack (after editing bench.config again and bumping RUN_ID)
Edit bench.config and set the ZK addr 
Run ./stack_and_start.sh to submit job.

## License

Copyright © 2015 Michael Drogalis

Distributed under the Eclipse Public License either version 1.0 or (at
your option) any later version.
