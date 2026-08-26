FROM --platform=linux/amd64 centos:7.9.2009

ARG openssl_version=3.6.1
ARG openssl_sha256=b1bfedcd5b289ff22aee87c9d600f515767ebf45f77168cb6d64f231f518a82e
ENV OPENSSL_VERSION $openssl_version
ENV OPENSSL_SHA256 $openssl_sha256

ENV SOURCE_DIR=/root/source
ENV LIBS_DIR=/root/libs
ENV CMAKE_VERSION_BASE=3.26
ENV CMAKE_VERSION=$CMAKE_VERSION_BASE.4
ENV NINJA_VERSION=1.7.2
ENV GO_VERSION=1.9.3

# Update as we need to use the vault now.
RUN sed -i -e 's/^mirrorlist/#mirrorlist/g' -e 's/^#baseurl=http:\/\/mirror.centos.org\/centos\/$releasever\//baseurl=https:\/\/linuxsoft.cern.ch\/centos-vault\/\/7.9.2009\//g' /etc/yum.repos.d/CentOS-Base.repo

# install dependencies
RUN yum install -y \
 apr-devel \
 autoconf \
 automake \
 bzip2 \
 git \
 glibc-devel \
 gnupg \
 java-1.8.0-openjdk-devel \
 libtool \
 lksctp-tools \
 lsb-core \
 make \
 openssl-devel \
 perl \
 perl-IPC-Cmd \
 perl-Time-Piece \
 perl-parent \
 perl-devel \
 tar \
 unzip \
 wget \
 zip \
 zlib-devel

RUN mkdir $SOURCE_DIR
WORKDIR $SOURCE_DIR

RUN yum install -y centos-release-scl

# Update to use the vault
RUN sed -i -e 's/^mirrorlist/#mirrorlist/g' -e 's/^# baseurl=http:\/\/mirror.centos.org\/centos\/7\/sclo\/$basearch\/sclo\//baseurl=https:\/\/vault.centos.org\/centos\/7\/sclo\/$basearch\/sclo\//g' /etc/yum.repos.d/CentOS-SCLo-scl.repo
RUN sed -i -e 's/^mirrorlist/#mirrorlist/g' -e 's/^#baseurl=http:\/\/mirror.centos.org\/centos\/7\/sclo\/$basearch\/rh\//baseurl=https:\/\/vault.centos.org\/centos\/7\/sclo\/$basearch\/rh\//g' /etc/yum.repos.d/CentOS-SCLo-scl-rh.repo

RUN yum -y install devtoolset-11-gcc devtoolset-11-gcc-c++
RUN echo 'source /opt/rh/devtoolset-11/enable' >> ~/.bashrc

RUN wget -q https://github.com/ninja-build/ninja/releases/download/v$NINJA_VERSION/ninja-linux.zip && unzip ninja-linux.zip && mkdir -p /opt/ninja-$NINJA_VERSION/bin && mv ninja /opt/ninja-$NINJA_VERSION/bin && echo 'PATH=/opt/ninja-$NINJA_VERSION/bin:$PATH' >> ~/.bashrc
RUN wget -q https://go.dev/dl/go$GO_VERSION.linux-amd64.tar.gz && tar zxf go$GO_VERSION.linux-amd64.tar.gz && mv go /opt/ && echo 'PATH=/opt/go/bin:$PATH' >> ~/.bashrc && echo 'export GOROOT=/opt/go/' >> ~/.bashrc
RUN curl -s https://cmake.org/files/v$CMAKE_VERSION_BASE/cmake-$CMAKE_VERSION-linux-x86_64.tar.gz --output cmake-$CMAKE_VERSION-linux-x86_64.tar.gz && tar zxf cmake-$CMAKE_VERSION-linux-x86_64.tar.gz && mv cmake-$CMAKE_VERSION-linux-x86_64 /opt/ && echo 'PATH=/opt/cmake-$CMAKE_VERSION-linux-x86_64/bin:$PATH' >> ~/.bashrc

# Build OpenSSL 3.x from source using devtoolset-11
RUN set -x && \
  source /opt/rh/devtoolset-11/enable && \
  # --no-check-certificate: CentOS 6 ships with outdated CA bundles that can't verify modern GitHub TLS certs
  wget --no-check-certificate https://github.com/openssl/openssl/releases/download/openssl-$OPENSSL_VERSION/openssl-$OPENSSL_VERSION.tar.gz && \
  echo "$OPENSSL_SHA256  openssl-$OPENSSL_VERSION.tar.gz" | sha256sum -c - && \
  tar xvf openssl-$OPENSSL_VERSION.tar.gz && \
  (cd openssl-$OPENSSL_VERSION && \
  # no-asm: devtoolset-11 on CentOS 7 cannot reliably compile OpenSSL's hand-tuned x86_64 assembly
  ./Configure linux-x86_64 --prefix=/opt/openssl-$OPENSSL_VERSION --libdir=lib shared no-asm no-apps && \
  make -j1 install_sw) && \
  rm -rf openssl-$OPENSSL_VERSION openssl-$OPENSSL_VERSION.tar.gz

RUN echo 'export LD_LIBRARY_PATH=/opt/openssl-$OPENSSL_VERSION/lib' >> ~/.bashrc

# Downloading and installing SDKMAN!
RUN curl -s "https://beta.sdkman.io?ci=true" | bash

ARG java_version="11.0.26-zulu"
ENV JAVA_VERSION=$java_version

# Installing Java removing some unnecessary SDKMAN files
RUN bash -c "source $HOME/.sdkman/bin/sdkman-init.sh && \
    yes | sdk install java $JAVA_VERSION && \
    rm -rf $HOME/.sdkman/archives/* && \
    rm -rf $HOME/.sdkman/tmp/*"

RUN echo 'export JAVA_HOME="/root/.sdkman/candidates/java/current"' >> ~/.bashrc
RUN echo 'PATH=$JAVA_HOME/bin:$PATH' >> ~/.bashrc

# install rust and setup PATH
RUN curl https://sh.rustup.rs -sSf | sh -s -- -y
RUN echo 'PATH=$PATH:$HOME/.cargo/bin' >> ~/.bashrc

# Prepare our own build
ENV PATH=/root/.sdkman/candidates/maven/current:$PATH
ENV JAVA_HOME=/root/.sdkman/candidates/java/current
ENV LD_LIBRARY_PATH=/opt/openssl-$OPENSSL_VERSION/lib

# Cleanup
RUN rm -rf $SOURCE_DIR
RUN yum clean all && \
    rm -rf /var/cache/yum

# when the JDK is GraalVM install native-image
RUN if [ -O /root/.sdkman/candidates/java/current/bin/gu ]; then /root/.sdkman/candidates/java/current/bin/gu install native-image; else echo "Not GraalVM, skip installation of native-image" ; fi
