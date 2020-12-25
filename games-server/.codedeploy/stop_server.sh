#!/bin/bash

docker ps -q --filter name="games_server" | xargs -r docker stop
