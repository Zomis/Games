#!/bin/bash

cd /opt/codedeploy-agent/deployment-root/$DEPLOYMENT_GROUP_ID/$DEPLOYMENT_ID/deployment-archive/
pwd
docker images -q --filter reference="server2*" | xargs -r docker rmi
docker build -t server2 -f "./Dockerfile" .
# This assumes there is a server2.conf file that specifies `-wsPort 42638` 
docker run -d --rm --name games_server -p 42638:42638 -v /home/ec2-user/server2:/data/logs -v /etc/letsencrypt:/etc/letsencrypt -w /data/logs server2
