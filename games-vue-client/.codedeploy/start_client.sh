#!/bin/bash

docker run -d --rm --name games_client -v /home/ec2-user/games-vue-client:/usr/share/nginx/html:ro -p 42637:80 nginx
