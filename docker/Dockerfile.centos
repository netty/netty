ARG centos_version=6
FROM centos:$centos_version
# needed to do again after FROM due to docker limitation
ARG centos_version

# install dependencies
RUN yum install -y \
 apr-devel \
 autoconf \
 automake \
 git \
 glibc-devel \
 libtool \
 lksctp-tools \
 lsb-core \
 make \
 openssl-devel \
 tar \
 wget

ARG java_version=1.8
ENV JAVA_VERSION $java_version
# installing java with jabba
RUN curl -sL https://github.com/shyiko/jabba/raw/master/install.sh | JABBA_COMMAND="install $JAVA_VERSION -o /jdk" bash

RUN echo 'export JAVA_HOME="/jdk"' >> ~/.bashrc
RUN echo 'PATH=/jdk/bin:$PATH' >> ~/.bashrc
