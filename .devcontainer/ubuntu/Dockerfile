FROM mcr.microsoft.com/devcontainers/base:ubuntu
RUN apt-get update && apt-get install -y \
    autotools-dev \
    autoconf \
    automake \
    libtool \
    make \
    tar \
    libaio-dev \
    libssl-dev \
    libapr1-dev \
    lksctp-tools \
    gcc \
    htop \
    strace \
    openjdk-8-jdk-headless \
    openjdk-8-source \
    openjdk-11-jdk-headless \
    openjdk-11-source \
    openjdk-17-jdk-headless \
    openjdk-17-source \
    openjdk-21-jdk-headless \
    openjdk-21-source \
    cmake \
    ninja-build \
    perl \
    golang

RUN curl --proto '=https' --tlsv1.2 -sSf https://sh.rustup.rs | sh -s -- -y
RUN . "$HOME/.cargo/env"
