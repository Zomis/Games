#!/bin/bash

docker ps -q --filter name="games_client" | xargs -r docker stop
