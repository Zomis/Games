#!/bin/bash

# This assumes there is a server2.conf file that specifies `-wsPort 42638`
docker build -t server2 .
docker run -d --rm --name games_server -p 42638:42638 -v /home/ec2-user/server2:/data/logs -w /data/logs server2
