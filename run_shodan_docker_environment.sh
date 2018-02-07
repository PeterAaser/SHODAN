#!/bin/bash

docker run -it --user root -e NB_UID=$(id -u $USER) -e NB_GID=$(id -g $USER) -e GRANT_SUDO=yes -v $(pwd):/SHODAN -p 8888:8888 -p 12340:12340 -p 12341:12341 -p 12345:12345 shodan_docker_environment bash
