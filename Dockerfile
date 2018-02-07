# TODO: Install postgresql

# Use phusion/baseimage as base image. To make your builds

# reproducible, make sure you lock down to a specific version, not

# to `latest`! See

# https://github.com/phusion/baseimage-docker/blob/master/Changelog.md

# for a list of version numbers.

FROM phusion/baseimage:latest

LABEL maintainer="Ivar Thokle Hovden <djloek@gmail.com>"

# Use baseimage-docker's init system.

CMD ["/sbin/my_init"]

# ...put your own build instructions here...

ENV SBT_VERSION 1.1.0

# install dependencies
RUN apt-get update && \
    apt-get install -y openjdk-8-jdk && \
    curl -sL https://deb.nodesource.com/setup_8.x | bash - && \
    apt-get install -y nodejs && \
    curl -L -o sbt-$SBT_VERSION.deb \
    http://dl.bintray.com/sbt/debian/sbt-$SBT_VERSION.deb && \
    dpkg -i sbt-$SBT_VERSION.deb && \
    rm sbt-$SBT_VERSION.deb && \
    apt-get update && \
    apt-get install -y sbt tmux

WORKDIR /SHODAN

# add the SHODAN git project to container working directory WORKDIR
ADD . /SHODAN

EXPOSE 9090
EXPOSE 9091
EXPOSE 9092
EXPOSE 9998

# http
EXPOSE 8888

# tcp ports
EXPOSE 12340
EXPOSE 12341

# fontend
EXPOSE 12345

# Clean up APT when done.

RUN apt-get clean && rm -rf /var/lib/apt/lists/* /tmp/* /var/tmp/*

# compile SHODAN
RUN sbt compile
